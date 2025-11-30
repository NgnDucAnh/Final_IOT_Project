package com.example.iot;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.iot.api.RetrofitClient;
import com.example.iot.model.ResultsResponse;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryActivity extends AppCompatActivity {

    private TableLayout tableHistory;

    private Handler handler;
    private Runnable refreshRunnable;
    private static final int REFRESH_INTERVAL = 5000;

    private String currentDeviceId;

    private final List<String> ALERT_ACTIONS = Arrays.asList(
            "using_phone",
            "using_computer",
            "sleeping",
            "no_person"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        SharedPreferences prefs = getSharedPreferences("IOT_PREFS", MODE_PRIVATE);
        currentDeviceId = prefs.getString("SAVED_DEVICE_ID", null);

        if (currentDeviceId == null) {
            Toast.makeText(this, "Lỗi: Chưa chọn thiết bị!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tableHistory = findViewById(R.id.tableHistory);

        handler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                fetchHistoryFromServer();
                handler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
    }

    private void fetchHistoryFromServer() {
        RetrofitClient.getService().getResults(currentDeviceId).enqueue(new Callback<ResultsResponse>() {
            @Override
            public void onResponse(Call<ResultsResponse> call, Response<ResultsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    updateTableUI(response.body());
                } else {
                    Log.e("IOT_HISTORY", "Lỗi Server: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResultsResponse> call, Throwable t) {
                Log.e("IOT_HISTORY", "Lỗi Mạng: " + t.getMessage());
            }
        });
    }

    private void updateTableUI(ResultsResponse data) {
        List<ResultsResponse.LatestAction> historyList = data.history;

        if (historyList == null || historyList.isEmpty()) return;

        // Xóa dữ liệu cũ (giữ dòng tiêu đề)
        int childCount = tableHistory.getChildCount();
        if (childCount > 1) {
            tableHistory.removeViews(1, childCount - 1);
        }

        for (ResultsResponse.LatestAction item : historyList) {
            String rawAction = item.action_code;
            if (rawAction != null && ALERT_ACTIONS.contains(rawAction)) {
                String displayTime = convertUtcToVnTime(item.timestamp);
                String displayMessage = getActionMessage(rawAction);
                addRowToTable(displayTime, displayMessage, item.image_url);
            }
        }
    }
    private String convertUtcToVnTime(String utcTimeStr) {
        if (utcTimeStr == null || utcTimeStr.isEmpty()) return "";

        try {
            String cleanTimeStr = utcTimeStr;
            if (utcTimeStr.contains(".")) {
                cleanTimeStr = utcTimeStr.replaceAll("(\\.\\d{3})\\d+", "$1");
            }
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSX", Locale.ENGLISH);
            Date date = inputFormat.parse(cleanTimeStr);

            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy\nHH:mm:ss", Locale.getDefault());
            outputFormat.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

            return outputFormat.format(date);

        } catch (Exception e) {
             {
                return utcTimeStr.replace(" ", "\n"); // Fallback cuối cùng
            }
        }
    }

    private String getActionMessage(String actionCode) {
        if (actionCode == null) return "Không rõ";
        switch (actionCode) {
            case "using_phone": return "Dùng điện thoại";
            case "sleeping": return "Đang ngủ";
            case "no_person": return "Vắng mặt";
            case "using_computer": return "Dùng máy tính";
            case "writing": return "Đang viết bài";
            default: return actionCode;
        }
    }

    private void addRowToTable(String time, String action, String fullImgUrl) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(Color.parseColor("#1E1E1E"));
        row.setPadding(0, 2, 0, 2);

        TextView tvTime = createTextView(time, "#E0E0E0");
        row.addView(tvTime);
        row.addView(createDivider());
        TextView tvAction = createTextView(action, "#E0E0E0");
        row.addView(tvAction);
        row.addView(createDivider());
        TextView tvImage = createTextView("Xem ảnh", "#4FC3F7");
        tvImage.setOnClickListener(v -> showImageDialog(fullImgUrl));
        row.addView(tvImage);
        tableHistory.addView(row);

        View line = new View(this);
        line.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        line.setBackgroundColor(Color.parseColor("#333333"));
        tableHistory.addView(line);
    }

    private TextView createTextView(String text, String colorHex) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setPadding(20, 20, 20, 20);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    private View createDivider() {
        View v = new View(this);
        v.setLayoutParams(new TableRow.LayoutParams(1, TableRow.LayoutParams.MATCH_PARENT));
        v.setBackgroundColor(Color.parseColor("#333333"));
        return v;
    }

    private void showImageDialog(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            Toast.makeText(this, "Không có ảnh", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_image_preview);

        ImageView imgPreview = dialog.findViewById(R.id.imgPreview);

        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.ic_launcher_background)
                .into(imgPreview);

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
    }
}