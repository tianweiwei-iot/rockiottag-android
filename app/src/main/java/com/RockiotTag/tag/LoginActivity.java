package com.RockiotTag.tag;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    
    private EditText nameEditText;
    private EditText cidEditText;
    private Button loginButton;
    private Button registerButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        nameEditText = findViewById(R.id.nameEditText);
        cidEditText = findViewById(R.id.cidEditText);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }
        });
        
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                register();
            }
        });
        
        if (SharedPreferencesManager.isAuthenticated(this)) {
            startMainActivity();
        }
    }
    
    private void login() {
        String name = nameEditText.getText().toString().trim();
        String cid = cidEditText.getText().toString().trim();
        
        if (name.isEmpty() || cid.isEmpty()) {
            Toast.makeText(this, "请输入用户名和CID", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    NewApiService apiService = NewApiService.getInstance();
                    NewApiService.ApiResponse response = apiService.login(name, cid);
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response != null && response.isSuccess()) {
                                SharedPreferencesManager.saveAuth(LoginActivity.this, 
                                    response.getId(), 
                                    response.getToken(), 
                                    response.getName(), 
                                    response.getCid());
                                
                                Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                                startMainActivity();
                            } else {
                                String errorMsg = "登录失败";
                                if (response != null) {
                                    errorMsg += ": " + response.getStatusCode();
                                }
                                Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Login error: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(LoginActivity.this, "登录失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
    
    private void register() {
        String name = nameEditText.getText().toString().trim();
        String cid = cidEditText.getText().toString().trim();
        
        if (name.isEmpty() || cid.isEmpty()) {
            Toast.makeText(this, "请输入用户名和CID", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    NewApiService apiService = NewApiService.getInstance();
                    NewApiService.ApiResponse response = apiService.register(name, cid);
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response != null && response.isSuccess()) {
                                Toast.makeText(LoginActivity.this, "注册成功，请登录", Toast.LENGTH_SHORT).show();
                            } else {
                                String errorMsg = "注册失败";
                                if (response != null) {
                                    errorMsg += ": " + response.getStatusCode();
                                }
                                Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Register error: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(LoginActivity.this, "注册失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
    
    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
