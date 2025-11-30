package com.example.iot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.iot.api.RetrofitClient;
import com.example.iot.model.DeviceResponse;
import com.example.iot.model.UserBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PairingActivity extends AppCompatActivity {

    private String userId;
    private Handler handler = new Handler();
    private Runnable checkDeviceRunnable;
    private boolean isPaired = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pairing);
        userId = getIntent().getStringExtra("USER_ID");
        startPairingMode();
        startPolling();
    }

    private void startPairingMode() {
        RetrofitClient.getService().startPairing(new UserBody(userId)).enqueue(new Callback<Object>() {
            @Override
            public void onResponse(Call<Object> call, Response<Object> response) {

            }
            @Override
            public void onFailure(Call<Object> call, Throwable t) {
                Toast.makeText(PairingActivity.this, "Lỗi mạng!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startPolling() {
        checkDeviceRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPaired) return;

                checkIfDeviceConnected();

                handler.postDelayed(this, 3000);
            }
        };
        handler.post(checkDeviceRunnable);
    }

    private void checkIfDeviceConnected() {
        RetrofitClient.getService().getMyDevices(new UserBody(userId)).enqueue(new Callback<DeviceResponse>() {
            @Override
            public void onResponse(Call<DeviceResponse> call, Response<DeviceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().count > 0) {
                        isPaired = true;
                        String deviceId = response.body().devices.get(0).device_id;
                        SharedPreferences prefs = getSharedPreferences("IOT_PREFS", MODE_PRIVATE);
                        prefs.edit().putString("SAVED_DEVICE_ID", deviceId).apply();

                        Toast.makeText(PairingActivity.this, "Ghép đôi thành công!", Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(PairingActivity.this, MainActivity.class);
                        intent.putExtra("USER_ID", userId);
                        startActivity(intent);
                        finish();
                    }
                }
            }

            @Override
            public void onFailure(Call<DeviceResponse> call, Throwable t) {
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(checkDeviceRunnable);
    }
}