package com.example.iot;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.iot.api.RetrofitClient;
import com.example.iot.model.ConfigBody;
import com.example.iot.model.ConfigResponse;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {
    private Switch swMasterLight, swMasterBuzzer;
    private Switch swLightSleep, swLightPhone, swLightLaptop, swLightNoPerson;
    private Switch swAlarmSleep, swAlarmPhone, swAlarmLaptop, swAlarmNoPerson;

    private TextView tvStartTime, tvEndTime;

    private String currentDeviceId;
    private boolean isUpdatingUI = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        SharedPreferences prefs = getSharedPreferences("IOT_PREFS", MODE_PRIVATE);
        currentDeviceId = prefs.getString("SAVED_DEVICE_ID", null);

        if (currentDeviceId == null) {
            Toast.makeText(this, "Lỗi: Không tìm thấy thiết bị!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mapViews();
        setupEventListeners();
        loadSettingsFromAPI();
    }

    private void mapViews() {
        tvStartTime = findViewById(R.id.tvStartTime);
        tvEndTime = findViewById(R.id.tvEndTime);

        swMasterLight = findViewById(R.id.light);
        swMasterBuzzer = findViewById(R.id.alarm);

        swLightSleep = findViewById(R.id.lightSleep);
        swLightPhone = findViewById(R.id.lightPhone);
        swLightLaptop = findViewById(R.id.lightLaptop);
        swLightNoPerson = findViewById(R.id.lightNoPerson);

        swAlarmSleep = findViewById(R.id.alarmSleep);
        swAlarmPhone = findViewById(R.id.alarmPhone);
        swAlarmLaptop = findViewById(R.id.alarmLaptop);
        swAlarmNoPerson = findViewById(R.id.alarmNoPerson);
    }

    private void loadSettingsFromAPI() {
        RetrofitClient.getService().getSettings(currentDeviceId).enqueue(new Callback<ConfigResponse>() {
            @Override
            public void onResponse(Call<ConfigResponse> call, Response<ConfigResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().config != null) {
                        applySettingsToUI(response.body().config);
                    }
                }
            }
            @Override
            public void onFailure(Call<ConfigResponse> call, Throwable t) {
                Toast.makeText(SettingsActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applySettingsToUI(ConfigResponse.ConfigMap config) {
        isUpdatingUI = true;

        if (config.schedule != null) {
            tvStartTime.setText(config.schedule.start_time);
            tvEndTime.setText(config.schedule.end_time);
        }

        if (config.general != null) {
            swMasterLight.setChecked(config.general.master_light);
            swMasterBuzzer.setChecked(config.general.master_buzzer);
            updateSubSwitchesState(true, config.general.master_light);
            updateSubSwitchesState(false, config.general.master_buzzer);
        }

        if (config.sleeping != null) swLightSleep.setChecked(config.sleeping.enable_light);
        if (config.using_phone != null) swLightPhone.setChecked(config.using_phone.enable_light);
        if (config.using_computer != null) swLightLaptop.setChecked(config.using_computer.enable_light);
        if (config.no_person != null) swLightNoPerson.setChecked(config.no_person.enable_light);

        if (config.sleeping != null) swAlarmSleep.setChecked(config.sleeping.enable_buzzer);
        if (config.using_phone != null) swAlarmPhone.setChecked(config.using_phone.enable_buzzer);
        if (config.using_computer != null) swAlarmLaptop.setChecked(config.using_computer.enable_buzzer);
        if (config.no_person != null) swAlarmNoPerson.setChecked(config.no_person.enable_buzzer);

        isUpdatingUI = false;
    }

    private void updateSubSwitchesState(boolean isLightGroup, boolean isEnabled) {
        if (isLightGroup) {
            swLightSleep.setEnabled(isEnabled);
            swLightPhone.setEnabled(isEnabled);
            swLightLaptop.setEnabled(isEnabled);
            swLightNoPerson.setEnabled(isEnabled);
        } else {
            swAlarmSleep.setEnabled(isEnabled);
            swAlarmPhone.setEnabled(isEnabled);
            swAlarmLaptop.setEnabled(isEnabled);
            swAlarmNoPerson.setEnabled(isEnabled);
        }
    }

    private void setupEventListeners() {
        tvStartTime.setOnClickListener(v -> showCustomTimePicker(tvStartTime, "start_time"));
        tvEndTime.setOnClickListener(v -> showCustomTimePicker(tvEndTime, "end_time"));

        swMasterLight.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateSubSwitchesState(true, isChecked);
            if (!isUpdatingUI) sendConfigToServer("general", "master_light", isChecked);
        });

        swMasterBuzzer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateSubSwitchesState(false, isChecked);
            if (!isUpdatingUI) sendConfigToServer("general", "master_buzzer", isChecked);
        });

        setupSwitch(swLightSleep, "sleeping", "enable_light");
        setupSwitch(swLightPhone, "using_phone", "enable_light");
        setupSwitch(swLightLaptop, "using_computer", "enable_light");
        setupSwitch(swLightNoPerson, "no_person", "enable_light");

        setupSwitch(swAlarmSleep, "sleeping", "enable_buzzer");
        setupSwitch(swAlarmPhone, "using_phone", "enable_buzzer");
        setupSwitch(swAlarmLaptop, "using_computer", "enable_buzzer");
        setupSwitch(swAlarmNoPerson, "no_person", "enable_buzzer");
    }

    private void showCustomTimePicker(TextView targetView, String targetKey) {
        int hour = 8;
        int minute = 0;
        try {
            String[] parts = targetView.getText().toString().split(":");
            if (parts.length == 2) {
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {}

        new TimePickerDialog(this,
                (TimePicker view, int hourOfDay, int minuteOfHour) -> {
                    String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour);
                    targetView.setText(timeFormatted);
                    sendConfigTimeToServer("schedule", targetKey, timeFormatted);
                },
                hour,
                minute,
                true
        ).show();
    }

    private void setupSwitch(Switch sw, String actionKey, String targetKey) {
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            if (isUpdatingUI) return;
            sendConfigToServer(actionKey, targetKey, isChecked);
        });
    }
    private void sendConfigToServer(String action, String target, boolean enabled) {
        ConfigBody body = new ConfigBody(currentDeviceId, action, target, enabled);
        callUpdateAPI(body);
    }

    private void sendConfigTimeToServer(String action, String target, String timeValue) {
        ConfigBody body = new ConfigBody(currentDeviceId, action, target, true);
        body.value = timeValue;
        callUpdateAPI(body);
    }

    private void callUpdateAPI(ConfigBody body) {
        RetrofitClient.getService().updateSettings(body).enqueue(new Callback<Object>() {
            @Override
            public void onResponse(Call<Object> call, Response<Object> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(SettingsActivity.this, "Lỗi server: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Object> call, Throwable t) {
                Toast.makeText(SettingsActivity.this, "Lỗi mạng!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}