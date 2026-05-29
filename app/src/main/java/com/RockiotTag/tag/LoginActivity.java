package com.RockiotTag.tag;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 启动页 - 直接跳转到MainActivity
 * 注意：根据新架构，Android端不需要登录/注册
 * 后端已经定时从供应商API同步数据，Android直接查询即可
 */
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 先恢复用户的语言偏好，如果没有设置过则使用系统语言
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String languageCode;
        
        // 检查用户是否已经选择过语言
        if (LanguageUtils.hasUserSelectedLanguage(this)) {
            // 用户已经选择过语言，使用保存的语言
            languageCode = prefs.getString("language", "zh");
        } else {
            // 首次启动，自动使用系统语言
            languageCode = LanguageUtils.getSystemLanguage();
            // 保存系统语言作为默认语言（但不标记为用户已选择）
            prefs.edit().putString("language", languageCode).apply();
        }
        
        LanguageUtils.applyLanguage(this, languageCode);
        
        super.onCreate(savedInstanceState);
        
        // 直接跳转到MainActivity
        startMainActivity();
    }
    
    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
