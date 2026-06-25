package com.RockiotTag.tag;

import com.RockiotTag.tag.util.ToastHelper;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.util.StatusBarHelper;

/**
 * 修改密码Activity
 */
public class ChangePasswordActivity extends AppCompatActivity {

    private static final String TAG = "ChangePasswordActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LanguageUtils.applyLanguage(this, LanguageUtils.getSavedLanguage(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        StatusBarHelper.setupStatusBar(this);
        StatusBarHelper.applyTitleBarPadding(this);

        View backBtn = findViewById(R.id.back_btn);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        View saveBtn = findViewById(R.id.save_btn);
        if (saveBtn != null) saveBtn.setOnClickListener(v -> changePassword());
    }

    private void changePassword() {
        EditText oldPwdEdit = findViewById(R.id.old_password_edit);
        EditText newPwdEdit = findViewById(R.id.new_password_edit);
        EditText confirmPwdEdit = findViewById(R.id.confirm_password_edit);

        String oldPassword = oldPwdEdit != null ? oldPwdEdit.getText().toString().trim() : "";
        String newPassword = newPwdEdit != null ? newPwdEdit.getText().toString().trim() : "";
        String confirmPassword = confirmPwdEdit != null ? confirmPwdEdit.getText().toString().trim() : "";

        if (oldPassword.isEmpty()) {
            ToastHelper.show(this, R.string.old_password_empty);
            return;
        }
        if (!UserApiService.isValidPassword(newPassword)) {
            ToastHelper.show(this, R.string.password_invalid);
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            ToastHelper.show(this, R.string.password_not_match);
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
            NewApiService.ApiResponse response = UserApiService.getInstance()
                .updatePassword(token, oldPassword, newPassword);
            runOnUiThread(() -> {
                if (saveBtn != null) saveBtn.setEnabled(true);
                if (UserApiService.isOperationSuccess(response)) {
                    ToastHelper.show(this, R.string.password_changed);
                    finish();
                } else {
                    String msg = response.getMessage();
                    ToastHelper.show(this, msg != null ? msg : getString(R.string.password_change_failed));
                }
            });
        }).start();
    }
}
