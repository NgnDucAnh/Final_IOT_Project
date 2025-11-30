package com.example.iot;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class PrivacyActivity extends AppCompatActivity {

    private CheckBox cbAgree;
    private Button btnConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);

        cbAgree = findViewById(R.id.cbAgree);
        btnConfirm = findViewById(R.id.btnConfirmPrivacy);

        // 1. Load trạng thái cũ (nếu đã từng đồng ý thì tick sẵn)
        SharedPreferences prefs = getSharedPreferences("IOT_PREFS", MODE_PRIVATE);
        boolean isAgreed = prefs.getBoolean("PRIVACY_AGREED", false);
        cbAgree.setChecked(isAgreed);

        // 2. Sự kiện bấm nút Xác nhận
        btnConfirm.setOnClickListener(v -> {
            boolean isChecked = cbAgree.isChecked();

            // Lưu lựa chọn vào bộ nhớ máy
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("PRIVACY_AGREED", isChecked);
            editor.apply();

            if (isChecked) {
                Toast.makeText(this, "Đã lưu: Bạn ĐỒNG Ý chính sách.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Đã lưu: Bạn KHÔNG ĐỒNG Ý chính sách.", Toast.LENGTH_SHORT).show();
            }

            // Đóng màn hình này, quay về Main
            finish();
        });
    }
}