package com.RockiotTag.tag;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 启动页 - 直接跳转到MainActivity
 * 注意：根据新架构，Android端不需要登录/注册
 * 后端已经定时从供应商API同步数据，Android直接查询即可
 */
public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
