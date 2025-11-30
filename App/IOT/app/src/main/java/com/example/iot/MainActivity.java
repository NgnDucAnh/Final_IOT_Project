package com.example.iot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.iot.api.RetrofitClient;
import com.example.iot.model.ResultsResponse;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private TextView txtDateTime, txtStatus;
    private ImageView imgCameraSnapshot;

    private Handler handler;
    private Runnable refreshRunnable;
    private static final int REFRESH_INTERVAL = 15000;

    private String currentDeviceId;
    private String lastImageUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences prefs = getSharedPreferences("IOT_PREFS", MODE_PRIVATE);
        currentDeviceId = prefs.getString("SAVED_DEVICE_ID", null);

        if (currentDeviceId == null) {
            Toast.makeText(this, "Lỗi: Chưa chọn thiết bị!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        txtDateTime = findViewById(R.id.dateTimeText);
        txtStatus = findViewById(R.id.statusText);
        imgCameraSnapshot = findViewById(R.id.imgCameraSnapshot);
        Button btnHistory = findViewById(R.id.historyButton);
        Button btnSettings = findViewById(R.id.settingsButton);
        Button btnPrivacy = findViewById(R.id.privacyButton);
        Button btnLogout = findViewById(R.id.logoutButton);


        btnSettings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        btnHistory.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        btnPrivacy.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, PrivacyActivity.class)));
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("SAVED_DEVICE_ID");
            editor.apply();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        handler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                fetchDataFromServer();
                handler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
    }

    private void fetchDataFromServer() {
        RetrofitClient.getService().getResults(currentDeviceId).enqueue(new Callback<ResultsResponse>() {
            @Override
            public void onResponse(Call<ResultsResponse> call, Response<ResultsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    updateUI(response.body());
                } else {
                    Log.e("IOT_APP", "Lỗi Server: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResultsResponse> call, Throwable t) {
                Log.e("IOT_APP", "Lỗi Mạng: " + t.getMessage());
            }
        });
    }

    private void updateUI(ResultsResponse data) {
        if (data.latest_action == null) {
            txtStatus.setText("Chưa có dữ liệu mới");
            return;
        }
        ResultsResponse.LatestAction action = data.latest_action;

        txtStatus.setText("Hành động: " + action.action_code);
        if (action.is_alert) {
            txtStatus.setTextColor(Color.RED);
            txtStatus.setText("CẢNH BÁO: " + action.action_code);
        } else {
            txtStatus.setTextColor(Color.WHITE);
        }

        String rawTime = action.timestamp;
        String timeDisplay = convertUtcToVnTime(rawTime);
        txtDateTime.setText(timeDisplay);

        String newImageUrl = action.image_url;
        if (newImageUrl != null && !newImageUrl.equals(lastImageUrl)) {
            lastImageUrl = newImageUrl;

            Glide.with(this)
                    .load(newImageUrl)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(android.R.drawable.ic_delete)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(imgCameraSnapshot);
        }
    }

    private String convertUtcToVnTime(String utcTimeStr) {
        if (utcTimeStr == null || utcTimeStr.isEmpty()) return "Vừa xong";

        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
            inputFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

            Date date = inputFormat.parse(utcTimeStr);

            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
            outputFormat.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

            return outputFormat.format(date);

        } catch (Exception e) {
            e.printStackTrace();
            return utcTimeStr;
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