package com.example.iot;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.iot.api.RetrofitClient;
import com.example.iot.model.DeviceResponse;
import com.example.iot.model.UserBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.content.SharedPreferences;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private Button btnLogin;

    // 1. Khai báo FirebaseAuth
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        mAuth = FirebaseAuth.getInstance();
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = etUsername.getText().toString().trim();
                String pass = etPassword.getText().toString().trim();
                if (TextUtils.isEmpty(email)) {
                    Toast.makeText(LoginActivity.this, "Vui lòng nhập Email!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(pass)) {
                    Toast.makeText(LoginActivity.this, "Vui lòng nhập Mật khẩu!", Toast.LENGTH_SHORT).show();
                    return;
                }
                loginWithFirebase(email, pass);
            }
        });
    }

    private void loginWithFirebase(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            String userId = user.getUid();
                            Toast.makeText(LoginActivity.this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                            checkUserDevices(userId);
                        } else {
                            Toast.makeText(LoginActivity.this, "Lỗi: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkUserDevices(String userId) {
        UserBody body = new UserBody(userId);
        RetrofitClient.getService().getMyDevices(body).enqueue(new Callback<DeviceResponse>() {
            @Override
            public void onResponse(Call<DeviceResponse> call, Response<DeviceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    DeviceResponse data = response.body();
                    if (data.count > 0) {
                        String deviceId = data.devices.get(0).device_id;
                        Log.e("deviceId", "deviceId: " + data.devices.get(0).device_id);
                        SharedPreferences prefs = getSharedPreferences("IOT_PREFS", MODE_PRIVATE);
                        prefs.edit().putString("SAVED_DEVICE_ID", deviceId).apply();
                        goToActivity(MainActivity.class, userId);
                    } else {
                        goToActivity(PairingActivity.class, userId);
                    }
                } else {
                    Toast.makeText(LoginActivity.this, "Lỗi Server: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<DeviceResponse> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void goToActivity(Class<?> cls, String userId) {
        Intent intent = new Intent(LoginActivity.this, cls);
        intent.putExtra("USER_ID", userId);
        startActivity(intent);
        finish();
    }
}