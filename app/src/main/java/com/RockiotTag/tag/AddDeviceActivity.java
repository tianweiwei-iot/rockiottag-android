package com.RockiotTag.tag;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class AddDeviceActivity extends AppCompatActivity {

    private static final String TAG = "AddDeviceActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 102;

    private TextView statusText;
    private ImageButton scanQrBtn;
    private EditText deviceNumEdit;
    private Spinner tagSpinner;
    private EditText deviceNicknameEdit;
    private Button bindDeviceBtn;
    private List<String> tagList;
    private List<String> iconList;
    private TagAdapter tagAdapter;
    private String selectedTag = "";

    private Handler handler;
    private NewApiService apiService;
    private DatabaseHelper databaseHelper;

    private final ActivityResultLauncher<ScanOptions> scanLauncher = registerForActivityResult(
        new ScanContract(),
        result -> {
            if (result.getContents() != null) {
                String contents = result.getContents().trim();
                
                // 清理扫码结果：去掉可能的空格、换行符、冒号等
                contents = contents.replace(" ", "").replace("\n", "").replace("\r", "").replace(":", "");
                
                Log.d(TAG, "Scan result: [" + result.getContents() + "] -> cleaned: [" + contents + "]");
                
                deviceNumEdit.setText(contents);
                deviceNumEdit.setSelection(contents.length());
                Toast.makeText(this, R.string.got_device_number, Toast.LENGTH_SHORT).show();
            }
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_device);

        handler = new Handler();
        apiService = NewApiService.getInstance();
        SharedPreferencesManager.loadAuth(this);
        databaseHelper = new DatabaseHelper(this);

        ImageButton backBtn = findViewById(R.id.back_btn);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        statusText = findViewById(R.id.status_text);
        scanQrBtn = findViewById(R.id.scan_qr_btn);
        deviceNumEdit = findViewById(R.id.device_num_edit);
        tagSpinner = findViewById(R.id.tag_spinner);
        deviceNicknameEdit = findViewById(R.id.device_nickname_edit);
        bindDeviceBtn = findViewById(R.id.bind_device_btn);

        initTagSpinner();
        setupTextWatchers();
        setupButtons();
    }

    private void initTagSpinner() {
        tagList = new ArrayList<>(Arrays.asList(
            getString(R.string.select_tag),
            "dog",
            "boy", 
            "car",
            "bike",
            "bank_card",
            "girl",
            "key",
            "moto",
            "pig",
            "wallet",
            "bag",
            "cat",
            "bird"
        ));

        iconList = new ArrayList<>(Arrays.asList(
            "",
            "🐕",
            "👦",
            "🚗",
            "🚴",
            "💳",
            "👧",
            "🔑",
            "🏍️",
            "🐷",
            "👛",
            "👜",
            "🐱",
            "🐦"
        ));

        tagAdapter = new TagAdapter(this, tagList, iconList);
        tagSpinner.setAdapter(tagAdapter);

        tagSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    selectedTag = tagList.get(position);
                } else {
                    selectedTag = "";
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedTag = "";
            }
        });
    }

    private void setupTextWatchers() {
        TextWatcher deviceNumWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String deviceNum = s != null ? s.toString().trim() : "";
                // 去掉冒号后验证长度
                String cleanDeviceNum = deviceNum.replace(":", "");
                if (!cleanDeviceNum.isEmpty() && cleanDeviceNum.length() != 12 && cleanDeviceNum.length() != 16) {
                    deviceNumEdit.setError(getString(R.string.device_number_length_error));
                } else {
                    deviceNumEdit.setError(null);
                }
                checkInputsAndEnableButton();
            }
        };

        TextWatcher nicknameWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String nickname = s != null ? s.toString().trim() : "";
                if (nickname.isEmpty()) {
                    deviceNicknameEdit.setError(getString(R.string.please_enter_nickname));
                } else {
                    deviceNicknameEdit.setError(null);
                }
                checkInputsAndEnableButton();
            }
        };

        deviceNumEdit.addTextChangedListener(deviceNumWatcher);
        deviceNicknameEdit.addTextChangedListener(nicknameWatcher);
    }

    private void checkInputsAndEnableButton() {
        String deviceNum = deviceNumEdit.getText() != null ? deviceNumEdit.getText().toString().trim() : "";
        String deviceNickname = deviceNicknameEdit.getText() != null ? deviceNicknameEdit.getText().toString().trim() : "";

        // 去掉冒号后验证长度
        String cleanDeviceNum = deviceNum.replace(":", "");
        
        boolean hasInput = !cleanDeviceNum.isEmpty() && !deviceNickname.isEmpty();
        boolean isLengthValid = cleanDeviceNum.length() == 12 || cleanDeviceNum.length() == 16;
        
        boolean canBind = hasInput && isLengthValid;
        bindDeviceBtn.setEnabled(canBind);
        if (canBind) {
            bindDeviceBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2196F3));
        } else {
            bindDeviceBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF888888));
        }
    }

    private void setupButtons() {
        scanQrBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startQrScan();
            }
        });

        bindDeviceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bindDevice();
            }
        });
    }

    private void startQrScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            ScanOptions options = new ScanOptions();
            options.setPrompt("");
            options.setBeepEnabled(false);
            options.setOrientationLocked(true);
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            options.setCameraId(0);
            options.setBarcodeImageEnabled(false);
            options.setCaptureActivity(CustomCaptureActivity.class);
            scanLauncher.launch(options);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startQrScan();
            } else {
                Toast.makeText(this, R.string.need_camera_permission, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void bindDevice() {
        String deviceNumInput = deviceNumEdit.getText() != null ? deviceNumEdit.getText().toString().trim() : "";
        final String deviceNickname = deviceNicknameEdit.getText() != null ? deviceNicknameEdit.getText().toString().trim() : "";

        if (deviceNumInput.isEmpty()) {
            Toast.makeText(this, R.string.please_enter_device_number, Toast.LENGTH_SHORT).show();
            return;
        }

        // 如果是MAC地址格式（包含冒号），自动去掉冒号
        final String deviceNum;
        if (deviceNumInput.contains(":")) {
            deviceNum = deviceNumInput.replace(":", "").toUpperCase();
            Log.d("AddDeviceActivity", "MAC address format detected, converted: " + deviceNumInput + " -> " + deviceNum);
        } else {
            deviceNum = deviceNumInput.toUpperCase();
        }

        // 验证设备号长度（去掉冒号后应该是12位或16位）
        if (deviceNum.length() != 12 && deviceNum.length() != 16) {
            Toast.makeText(this, R.string.device_number_length_error, Toast.LENGTH_SHORT).show();
            return;
        }

        if (deviceNickname.isEmpty()) {
            Toast.makeText(this, R.string.please_enter_nickname, Toast.LENGTH_SHORT).show();
            return;
        }

        // 根据设备号长度设置对应的API URL
        NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(deviceNum));

        if (databaseHelper.isDeviceBound(deviceNum)) {
            Toast.makeText(this, R.string.device_already_added, Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText(getString(R.string.binding_device));
        bindDeviceBtn.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d("AddDeviceActivity", "Starting API binding...");
                    
                    if (!apiService.isAuthenticated()) {
                        Log.d("AddDeviceActivity", "Not authenticated, logging in...");
                        
                        NewApiService.ApiResponse loginResponse = apiService.login(
                            ApiConfig.getCid(),
                            ApiConfig.getCustomerCode(),
                            ApiConfig.getPassword()
                        );
                        
                        if (loginResponse == null || !loginResponse.isSuccess()) {
                            Log.e("AddDeviceActivity", "Login failed");
                            final String errorMsg = loginResponse != null ? 
                                (loginResponse.getMessage() != null ? loginResponse.getMessage() : getString(R.string.login_failed)) : 
                                getString(R.string.cannot_connect_server);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    statusText.setText(getString(R.string.login_failed_with_error, errorMsg));
                                    Toast.makeText(AddDeviceActivity.this, getString(R.string.login_failed_with_error, errorMsg), Toast.LENGTH_SHORT).show();
                                    bindDeviceBtn.setEnabled(true);
                                }
                            });
                            return;
                        }
                        
                        Log.d("AddDeviceActivity", "Login success, userId: " + apiService.getUserId());
                    }
                    
                    Log.d("AddDeviceActivity", "Binding device to server (server will handle vendor API)...");
                    Log.d("AddDeviceActivity", "Device Num: " + deviceNum);
                    Log.d("AddDeviceActivity", "Device nickname: " + deviceNickname);
                    
                    NewApiService.ApiResponse bindResponse = apiService.bindDevice(
                        deviceNum,
                        null,
                        deviceNickname
                    );
                    
                    Log.d("AddDeviceActivity", "Bind response: " + (bindResponse != null ? bindResponse.isSuccess() : "null"));
                    
                    if (bindResponse == null) {
                        Log.e("AddDeviceActivity", "Bind response is null");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusText.setText(getString(R.string.api_bind_failed));
                                Toast.makeText(AddDeviceActivity.this, R.string.api_bind_failed_network, Toast.LENGTH_SHORT).show();
                                bindDeviceBtn.setEnabled(true);
                            }
                        });
                        return;
                    }
                    
                    if (!bindResponse.isSuccess()) {
                        Log.e("AddDeviceActivity", "Bind failed - message: " + bindResponse.getMessage());
                        
                        String message = bindResponse.getMessage();
                        final String errorMsg;
                        if (message != null && message.contains("尚未在服务器上注册")) {
                            errorMsg = getString(R.string.device_not_activated);
                        } else if (message != null && message.contains("设备已绑定")) {
                            errorMsg = getString(R.string.device_already_bound);
                        } else {
                            errorMsg = message != null ? message : getString(R.string.bind_failed);
                        }
                        
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusText.setText(errorMsg);
                                Toast.makeText(AddDeviceActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                                bindDeviceBtn.setEnabled(true);
                            }
                        });
                        return;
                    }
                    
                    Log.d("AddDeviceActivity", "Bind success! Triggering data sync...");
                    
                    NewApiService.ApiResponse syncResponse = apiService.syncDevice(deviceNum);
                    Log.d("AddDeviceActivity", "Sync response: " + (syncResponse != null ? syncResponse.isSuccess() : "null"));
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Device newDevice = new Device(deviceNum, deviceNickname);
                            newDevice.setDeviceNum(deviceNum);
                            newDevice.setTag(selectedTag);
                            databaseHelper.addDevice(newDevice);
                            
                            int deletedCount = databaseHelper.deleteLocationRecordsByDevice(newDevice.getDeviceId());
                            Log.d("AddDeviceActivity", "Deleted " + deletedCount + " track records for new device");
                            
                            android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
                            android.content.SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("selected_device_id", newDevice.getDeviceId());
                            editor.apply();
                            
                            if (syncResponse != null && syncResponse.isSuccess()) {
                                statusText.setText(getString(R.string.device_bind_success_synced));
                                Toast.makeText(AddDeviceActivity.this, R.string.device_bind_success_synced, Toast.LENGTH_SHORT).show();
                            } else {
                                statusText.setText(getString(R.string.device_bind_success));
                                Toast.makeText(AddDeviceActivity.this, R.string.device_bind_success, Toast.LENGTH_SHORT).show();
                            }
                            
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    finish();
                                }
                            }, 1500);
                        }
                    });
                } catch (Exception e) {
                    Log.e("AddDeviceActivity", "Error in bindDevice: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(getString(R.string.bind_failed_with_error, e.getMessage()));
                            Toast.makeText(AddDeviceActivity.this, getString(R.string.bind_failed_with_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                            bindDeviceBtn.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}
