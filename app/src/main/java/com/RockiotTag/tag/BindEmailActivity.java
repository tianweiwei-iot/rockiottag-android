package com.RockiotTag.tag;

import com.RockiotTag.tag.util.ToastHelper;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.util.StatusBarHelper;

import java.util.regex.Pattern;

/**
 * 绑定邮箱Activity
 */
public class BindEmailActivity extends AppCompatActivity {

    private static final String TAG = "BindEmailActivity";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LanguageUtils.applyLanguage(this, LanguageUtils.getSavedLanguage(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bind_email);

        StatusBarHelper.setupStatusBar(this);
        StatusBarHelper.applyTitleBarPadding(this);

        View backBtn = findViewById(R.id.back_btn);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        // 显示当前已绑定邮箱
        String currentEmail = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getString("user_email", "");
        EditText emailEdit = findViewById(R.id.email_edit);
        if (emailEdit != null && !currentEmail.isEmpty()) {
            emailEdit.setText(currentEmail);
        }

        View saveBtn = findViewById(R.id.save_btn);
        if (saveBtn != null) saveBtn.setOnClickListener(v -> bindEmail());
    }

    private void bindEmail() {
        EditText emailEdit = findViewById(R.id.email_edit);
        String email = emailEdit != null ? emailEdit.getText().toString().trim() : "";

        if (email.isEmpty()) {
            ToastHelper.show(this, R.string.email_empty);
            return;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            ToastHelper.show(this, R.string.email_invalid);
            return;
        }

        String token = getSharedPreferences("app_settings", MODE_PRIVATE).getString("auth_token", null);
        if (token == null || token.isEmpty()) {
            ToastHelper.show(this, R.string.not_logged_in);
            return;
        }

        View saveBtn = findViewById(R.id.save_btn);
        if (saveBtn != null) saveBtn.setEnabled(false);

        new Thread(() -> {
            NewApiService.ApiResponse response = UserApiService.getInstance().updateEmail(token, email);
            runOnUiThread(() -> {
                if (saveBtn != null) saveBtn.setEnabled(true);
                if (UserApiService.isOperationSuccess(response)) {
                    getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                        .putString("user_email", email).apply();
                    ToastHelper.show(this, R.string.email_bound);
                    setResult(RESULT_OK);
                    finish();
                } else {
                    String msg = response.getMessage();
                    ToastHelper.show(this, msg != null ? msg : getString(R.string.email_bind_failed));
                }
            });
        }).start();
    }
}
