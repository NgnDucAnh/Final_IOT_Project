import os
import base64
import json
import time
import threading
from datetime import datetime
from flask import Flask, request, jsonify
from openai import OpenAI
from dotenv import load_dotenv
import boto3
from Crypto.Cipher import AES
from botocore.exceptions import ClientError
import firebase_admin
from firebase_admin import credentials, firestore

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
load_dotenv(os.path.join(BASE_DIR, '.env'))

cred = credentials.Certificate(os.path.join(BASE_DIR, "creds.json"))
firebase_admin.initialize_app(cred)
db = firestore.client()

S3_BUCKET_NAME = os.getenv("S3_BUCKET_NAME")
AWS_ACCESS_KEY_ID = os.getenv("AWS_ACCESS_KEY_ID")
AWS_SECRET_ACCESS_KEY = os.getenv("AWS_SECRET_ACCESS_KEY")
AWS_REGION = os.getenv("AWS_REGION")
api_key = os.getenv("OPENAI_API_KEY")

S3_IMAGE_FOLDER = "IOT/images" 

s3_client = boto3.client('s3',
    aws_access_key_id=AWS_ACCESS_KEY_ID,
    aws_secret_access_key=AWS_SECRET_ACCESS_KEY,
    region_name=AWS_REGION
)
client = OpenAI(api_key=api_key)

KEY = b"\x1A\x2B\x3C\x4D\x5E\x6F\x70\x81\x92\xA3\xB4\xC5\xD6\xE7\xF8\x09"
IV = b"\x00\x11\x22\x33\x44\x55\x66\x77\x88\x99\xAA\xBB\xCC\xDD\xEE\xFF"

app = Flask(__name__)
PORT = 3000

def create_presigned_url(filename, expiration=3600):
    """Tạo URL tạm thời để App xem được ảnh trên S3"""
    try:
        response = s3_client.generate_presigned_url('get_object',
                                                     Params={'Bucket': S3_BUCKET_NAME,
                                                             'Key': f'{S3_IMAGE_FOLDER}/{filename}'},
                                                     ExpiresIn=expiration)
    except ClientError as e:
        print(f"Lỗi tạo link S3: {e}")
        return None
    return response

def pad(s):
    return s + (16 - len(s) % 16) * chr(16 - len(s) % 16)

def unpad(s):
    return s[:-ord(s[len(s)-1:])]

def encrypt_text(plain_text):
    try:
        raw = pad(plain_text).encode('utf-8')
        cipher = AES.new(KEY, AES.MODE_CBC, IV)
        encrypted = cipher.encrypt(raw)
        return base64.b64encode(encrypted).decode('utf-8')
    except Exception as e:
        print(f"Encrypt Error: {e}")
        return None

def decrypt_text(encrypted_b64):
    try:
        enc = base64.b64decode(encrypted_b64)
        cipher = AES.new(KEY, AES.MODE_CBC, IV)
        decrypted = cipher.decrypt(enc)
        return unpad(decrypted.decode('utf-8'))
    except Exception as e:
        print(f"Decrypt Error: {e}")
        return None

def decrypt_image(encrypted_b64):
    try:
        data = base64.b64decode(encrypted_b64)
        cipher = AES.new(KEY, AES.MODE_CBC, IV)
        decrypted = cipher.decrypt(data)
        pad_len = decrypted[-1]
        if pad_len < 1 or pad_len > 16: pad_len = 0
        return decrypted[:-pad_len] if pad_len else decrypted
    except Exception as e:
        print("Image Decrypt Error:", e)
        return None

ACTION_LABELS = ["writing", "reading", "sleeping", "using_computer", "using_phone", "no_person", "other_action"]

def is_time_in_range(start_str, end_str):
    """Kiểm tra giờ hiện tại có nằm trong khoảng cho phép không"""
    try:
        now = datetime.now().time()
        start = datetime.strptime(start_str, "%H:%M").time()
        end = datetime.strptime(end_str, "%H:%M").time()
        
        if start <= end:
            return start <= now <= end
        else:
            return start <= now or now <= end
    except Exception as e:
        print(f"Lỗi format giờ: {e}")
        return True 

def process_image_ai(filename, device_id):
    """Gửi ảnh cho OpenAI phân tích và lưu log vào Firebase"""
    temp_path = os.path.join(BASE_DIR, 'temp_ai', filename)
    os.makedirs(os.path.dirname(temp_path), exist_ok=True)
    
    try:
        s3_key = f'{S3_IMAGE_FOLDER}/{filename}'
        
        device_ref = db.collection('devices').document(device_id)
        dev_doc = device_ref.get()
        
        if dev_doc.exists:
            full_config = dev_doc.to_dict().get('config', {})
            schedule = full_config.get('schedule', {})
            if schedule.get('enabled', False):
                start_time = schedule.get('start_time', "00:00")
                end_time = schedule.get('end_time', "23:59")
                
                if not is_time_in_range(start_time, end_time):
                    print(f"[{device_id}] Ngoài giờ hoạt động. Tắt báo động.")
                    
                    quiet_log = {
                        "timestamp": firestore.SERVER_TIMESTAMP,
                        "action_code": "schedule_sleep", 
                        "image_filename": filename,
                        "is_alert": False,       
                        "trigger_light": False,  
                        "trigger_buzzer": False, 
                        "message": "Ngoài giờ hoạt động"
                    }
                    
                    device_ref.update({"latest_log": quiet_log})
                    
                    if os.path.exists(temp_path): os.remove(temp_path)
                    return 
        
        s3_client.download_file(S3_BUCKET_NAME, s3_key, temp_path)
        with open(temp_path, "rb") as f:
            b64_img = base64.b64encode(f.read()).decode('utf-8')

        response = client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system", "content": f"Return only one label: {', '.join(ACTION_LABELS)}"},
                {"role": "user", "content": [{"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_img}"}}]}
            ],
            max_tokens=20
        )
        action = response.choices[0].message.content.strip().replace('"','').replace('.','')
        if action not in ACTION_LABELS: action = "unknown_action"

        trigger_light = False
        trigger_buzzer = False
        is_alert = False
        
        if dev_doc.exists:
            general_settings = full_config.get('general', {})
            master_light = general_settings.get('master_light', True)
            master_buzzer = general_settings.get('master_buzzer', True)
            action_settings = full_config.get(action, {})

            if master_light and action_settings.get('enable_light', False):
                trigger_light = True
            if master_buzzer and action_settings.get('enable_buzzer', False):
                trigger_buzzer = True
            if trigger_light or trigger_buzzer:
                is_alert = True

        log_data = {
            "timestamp": firestore.SERVER_TIMESTAMP,
            "action_code": action,
            "image_filename": filename,
            "is_alert": is_alert,
            "trigger_light": trigger_light,
            "trigger_buzzer": trigger_buzzer
        }
        device_ref.collection('logs').add(log_data)    
        device_ref.update({"latest_log": log_data})    
        
        print(f"AI Result [{device_id}]: {action} | Light: {trigger_light} | Buzzer: {trigger_buzzer}")

    except Exception as e:
        print(f"AI Processing Error: {e}")
    finally:
        if os.path.exists(temp_path): os.remove(temp_path)

# 1. API: App gọi để bắt đầu ghép đôi (Pairing)
@app.route('/api/start-pairing', methods=['POST'])
def start_pairing():
    data = request.json
    user_id = data.get('user_id')
    if not user_id: return jsonify({"error": "missing user_id"}), 400
    db.collection('pairing_queue').document(user_id).set({
        'user_id': user_id,
        'timestamp': firestore.SERVER_TIMESTAMP,
        'status': 'waiting'
    })
    return jsonify({"status": "waiting", "message": "Ready to pair device"}), 200

# 2. API: ESP32 gọi để xác thực (Handshake)
@app.route('/device-handshake', methods=['POST'])
def handshake():
    encrypted_data = request.data.decode('utf-8')
    decrypted_json = decrypt_text(encrypted_data)
    
    if not decrypted_json:
        return jsonify({"status": "error"}), 400

    try:
        info = json.loads(decrypted_json)
        mac = info.get('device_id')
    except:
        return jsonify({"status": "error"}), 400

    device_ref = db.collection('devices').document(mac)
    doc = device_ref.get()
    
    response_data = {}

    if doc.exists:
        data = doc.to_dict()
        response_data = {
            "status": "authorized",
            "owner": data.get('user_id'),
            "config": data.get('config', {})
        }
    else:
        queues = db.collection('pairing_queue').where('status', '==', 'waiting').limit(1).stream()
        found_user = None
        for q in queues:
            found_user = q.to_dict().get('user_id')
            db.collection('pairing_queue').document(found_user).delete() 
            break
        
        if found_user:
            default_config = {
                "sleeping":      {"enable_light": True,  "enable_buzzer": True},
                "using_phone":   {"enable_light": False, "enable_buzzer": True},
                "using_computer":{"enable_light": False, "enable_buzzer": True},
                "no_person":     {"enable_light": True,  "enable_buzzer": True},
                "general":       {"master_light": True,  "master_buzzer": True},
                "schedule": {"start_time": "08:00", "end_time": "22:00", "enabled": True}
            }
            device_ref.set({
                'mac_address': mac,
                'user_id': found_user,
                'config': default_config,
                'created_at': firestore.SERVER_TIMESTAMP
            })
            response_data = {"status": "registered_success", "config": default_config}
        else:
            response_data = {"status": "unauthorized"}

    return encrypt_text(json.dumps(response_data))

# 3. API: ESP32 gửi ảnh (Mã hóa)
@app.route('/upload', methods=['POST'])
def upload():
    device_id = request.headers.get('Device-ID') 
    
    encrypted_b64 = request.data.decode('utf-8')
    raw_image = decrypt_image(encrypted_b64)
    
    if not raw_image: return 'Decrypt Failed', 400

    filename = f"img_{datetime.now().strftime('%Y%m%d_%H%M%S')}.jpg"
    s3_client.put_object(Bucket=S3_BUCKET_NAME, Key=f'{S3_IMAGE_FOLDER}/{filename}', Body=raw_image, ContentType='image/jpeg')
    
    if device_id:
        threading.Thread(target=process_image_ai, args=(filename, device_id)).start()
    
    return 'Upload OK', 200

# 4. API: ESP32 lấy kết quả hành động mới nhất (Polling)
@app.route('/results', methods=['GET'])
def results():
    device_id = request.args.get('device_id')
    if not device_id: 
        return jsonify({"error": "missing device_id"}), 400
    
    doc = db.collection('devices').document(device_id).get()
    latest = {}
    config = {}
    if doc.exists:
        data = doc.to_dict()
        latest = data.get('latest_log', {})
        config = data.get('config', {})
        if latest and 'image_filename' in latest:
            latest['image_url'] = create_presigned_url(latest['image_filename'])

    history_list = []
    try:
        logs_ref = db.collection('devices').document(device_id).collection('logs')
        logs_docs = logs_ref.order_by('timestamp', direction=firestore.Query.DESCENDING).limit(20).stream()
        
        for log in logs_docs:
            log_data = log.to_dict()
            if 'timestamp' in log_data and log_data['timestamp']:
                log_data['timestamp'] = str(log_data['timestamp'])
            
            if 'image_filename' in log_data:
                log_data['image_url'] = create_presigned_url(log_data['image_filename'])
                
            history_list.append(log_data)
    except Exception as e:
        print("Lỗi lấy history:", e)

    return jsonify({
        "status": "success",
        "latest_action": latest,
        "current_config": config,
        "history": history_list  
    })
    
    return jsonify({"status": "empty", "latest_action": None})

# API: Lấy danh sách thiết bị của User (Để App quyết định vào Main hay Pairing)
@app.route('/api/get-my-devices', methods=['POST'])
def get_my_devices():
    user_id = request.json.get('user_id')
    
    if not user_id:
        return jsonify({"status": "error", "message": "Missing user_id"}), 400

    try:
        docs = db.collection('devices').where('user_id', '==', user_id).stream()
        
        my_devices = []
        for doc in docs:
            d = doc.to_dict()
            my_devices.append({
                "device_id": doc.id, 
                "config": d.get('config'),
                "created_at": d.get('created_at')
            })
            
        return jsonify({
            "status": "success", 
            "count": len(my_devices),
            "devices": my_devices
        }), 200
        
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    
# API: Lấy cấu hình thiết bị
@app.route('/get-settings', methods=['GET'])
def get_settings():
    device_id = request.args.get('device_id')
    if not device_id: 
        return jsonify({"status": "error", "message": "missing device_id"}), 400

    try:
        doc = db.collection('devices').document(device_id).get()
        if doc.exists:
            data = doc.to_dict()
            config = data.get('config', {})
            return jsonify({"status": "success", "config": config}), 200
        else:
            return jsonify({"status": "error", "message": "Device not found"}), 404
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

# API: Cập nhật cấu hình 
@app.route('/update-settings', methods=['POST'])
def update_settings():
    try:
        data = request.json
        device_id = data.get('device_id')
        action = data.get('action')       
        target = data.get('target')       
        value = data.get('value')
        
        if value is None:
            value = data.get('enabled')

        if not all([device_id, action, target]) or value is None:
            return jsonify({"status": "error", "message": "Thiếu thông tin (device_id, action, target, value)"}), 400

        device_ref = db.collection('devices').document(device_id)
        if not device_ref.get().exists:
            return jsonify({"status": "error", "message": "Device not found"}), 404
        
        update_key = f'config.{action}.{target}'
        
        device_ref.update({
            update_key: value
        })
        
        print(f"Updated: {device_id} | {update_key} = {value}")
        return jsonify({"status": "success", "updated": update_key, "value": value}), 200
        
    except Exception as e:
        print(f"Error updating settings: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500
    

if __name__ == '__main__':
    print("SERVER STARTED - FIREBASE & AES ENABLED")
    app.run(host='0.0.0.0', port=3000, debug=True)