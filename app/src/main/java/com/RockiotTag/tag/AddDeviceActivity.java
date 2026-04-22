package com.RockiotTag.tag;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
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
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class AddDeviceActivity extends AppCompatActivity {

    private static final String TAG = "AddDeviceActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 102;

    private TextView statusText;
    private ImageButton scanQrBtn;
    private Button btnCameraScan;
    private Button btnGalleryScan;
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

    // 相机扫码
    private final ActivityResultLauncher<ScanOptions> scanLauncher = registerForActivityResult(
        new ScanContract(),
        result -> {
            Log.d(TAG, "=== SCAN CALLBACK TRIGGERED ===");
            if (result != null) {
                Log.d(TAG, "Result not null: " + result.toString());
                if (result.getContents() != null) {
                    String rawContents = result.getContents();
                    String contents = rawContents.trim();
                    
                    Log.d(TAG, "=== CAMERA SCAN SUCCESS ===");
                    Log.d(TAG, "Raw: [" + rawContents + "]");
                    Log.d(TAG, "Raw length: " + rawContents.length());
                    Log.d(TAG, "Trimmed: [" + contents + "]");
                    Log.d(TAG, "Trimmed length: " + contents.length());
                    
                    String cleaned = contents.replace(" ", "").replace("\n", "").replace("\r", "");
                    Log.d(TAG, "Cleaned: [" + cleaned + "]");
                    Log.d(TAG, "Cleaned length: " + cleaned.length());
                    
                    deviceNumEdit.setText(contents);
                    deviceNumEdit.setSelection(contents.length());
                    
                    Toast.makeText(this, "扫码成功: " + cleaned, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "=== SCAN FINISHED - text set to edit ===");
                } else {
                    Log.w(TAG, "Scan result.getContents() is NULL!");
                    Toast.makeText(this, "未扫描到二维码内容", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Result is NULL! User cancelled or error.");
            }
        }
    );

    // 相册选择图片
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            Log.d(TAG, "=== GALLERY CALLBACK TRIGGERED ===");
            Log.d(TAG, "Result code: " + result.getResultCode());
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                Log.d(TAG, "Selected image URI: " + imageUri);
                decodeQRCodeFromUri(imageUri);
            } else {
                Log.w(TAG, "Gallery cancelled or no data");
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
        btnCameraScan = findViewById(R.id.btn_camera_scan);
        btnGalleryScan = findViewById(R.id.btn_gallery_scan);
        deviceNumEdit = findViewById(R.id.device_num_edit);
        tagSpinner = findViewById(R.id.tag_spinner);
        deviceNicknameEdit = findViewById(R.id.device_nickname_edit);
        bindDeviceBtn = findViewById(R.id.bind_device_btn);

        initTagSpinner();
        setupTextWatchers();
        setupButtons();
        
        Log.d(TAG, "AddDeviceActivity onCreate completed");
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
                String deviceNum = s != null ? s.toString() : "";
                String upperDeviceNum = deviceNum.toUpperCase();
                
                if (!deviceNum.equals(upperDeviceNum)) {
                    s.replace(0, s.length(), upperDeviceNum);
                    return;
                }
                
                String cleanDeviceNum = upperDeviceNum.trim().replace(":", "");
                Log.d(TAG, "Text changed: [" + cleanDeviceNum + "], len=" + cleanDeviceNum.length());
                
                // 去掉位数限制！只检查是否为空
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

        String cleanDeviceNum = deviceNum.replace(":", "");
        
        // 去掉位数限制！只要不为空就允许绑定
        boolean hasInput = !cleanDeviceNum.isEmpty() && !deviceNickname.isEmpty();
        
        bindDeviceBtn.setEnabled(hasInput);
        if (hasInput) {
            bindDeviceBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2196F3));
        } else {
            bindDeviceBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF888888));
        }
    }

    private void setupButtons() {
        scanQrBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "scanQrBtn clicked");
                startCameraScan();
            }
        });

        btnCameraScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "btnCameraScan clicked");
                startCameraScan();
            }
        });

        btnGalleryScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "btnGalleryScan clicked");
                startGallerySelect();
            }
        });

        bindDeviceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "bindDeviceBtn clicked");
                bindDevice();
            }
        });
    }

    private void startCameraScan() {
        Log.d(TAG, "startCameraScan() called");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            Log.d(TAG, "Launching QR_CODE scan...");
            ScanOptions options = new ScanOptions();
            options.setPrompt("扫描设备二维码");
            options.setBeepEnabled(true);
            options.setOrientationLocked(true);
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            options.setCameraId(0);
            options.setBarcodeImageEnabled(false);
            scanLauncher.launch(options);
            Log.d(TAG, "scanLauncher.launch() called");
        }
    }

    private void startGallerySelect() {
        Log.d(TAG, "startGallerySelect() called");
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
        Log.d(TAG, "galleryLauncher.launch() called");
    }

    private void decodeQRCodeFromUri(Uri imageUri) {
        Log.d(TAG, "decodeQRCodeFromUri() called with URI: " + imageUri);
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            Log.d(TAG, "Bitmap loaded: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            int[] intArray = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(intArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            RGBLuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), intArray);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            QRCodeReader reader = new QRCodeReader();
            Result result = reader.decode(binaryBitmap, hints);
            
            String rawContents = result.getText();
            Log.d(TAG, "=== GALLERY DECODE SUCCESS ===");
            Log.d(TAG, "Raw: [" + rawContents + "]");
            Log.d(TAG, "Length: " + rawContents.length());
            
            String contents = rawContents.trim();
            String cleaned = contents.replace(" ", "").replace("\n", "").replace("\r", "");
            
            Log.d(TAG, "Cleaned: [" + cleaned + "], len=" + cleaned.length());
            
            deviceNumEdit.setText(contents);
            deviceNumEdit.setSelection(contents.length());
            
            Toast.makeText(this, "识别成功: " + cleaned, Toast.LENGTH_SHORT).show();
            
            bitmap.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Decode from gallery FAILED!", e);
            e.printStackTrace();
            Toast.makeText(this, "无法识别: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraScan();
            } else {
                Toast.makeText(this, R.string.need_camera_permission, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void bindDevice() {
        String deviceNumInput = deviceNumEdit.getText() != null ? deviceNumEdit.getText().toString().trim() : "";
        final String deviceNickname = deviceNicknameEdit.getText() != null ? deviceNicknameEdit.getText().toString().trim() : "";

        Log.d(TAG, "bindDevice() called - input: [" + deviceNumInput + "], nickname: [" + deviceNickname + "]");

        if (deviceNumInput.isEmpty()) {
            Toast.makeText(this, R.string.please_enter_device_number, Toast.LENGTH_SHORT).show();
            return;
        }

        final String deviceNum;
        if (deviceNumInput.contains(":")) {
            deviceNum = deviceNumInput.replace(":", "").toUpperCase();
            Log.d(TAG, "MAC converted: " + deviceNumInput + " -> " + deviceNum);
        } else {
            deviceNum = deviceNumInput.toUpperCase();
        }

        Log.d(TAG, "Final deviceNum: [" + deviceNum + "], len=" + deviceNum.length());

        // 去掉位数限制！任何非空字符串都允许绑定
        
        if (deviceNickname.isEmpty()) {
            Toast.makeText(this, R.string.please_enter_nickname, Toast.LENGTH_SHORT).show();
            return;
        }

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
                    Log.d(TAG, "Starting API binding...");
                    
                    if (!apiService.isAuthenticated()) {
                        Log.d(TAG, "Not authenticated, logging in...");
                        
                        NewApiService.ApiResponse loginResponse = apiService.login(
                            ApiConfig.getCid(),
                            ApiConfig.getCustomerCode(),
                            ApiConfig.getPassword()
                        );
                        
                        if (loginResponse == null || !loginResponse.isSuccess()) {
                            Log.e(TAG, "Login failed");
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
                        
                        Log.d(TAG, "Login success, userId: " + apiService.getUserId());
                    }
                    
                    Log.d(TAG, "Binding device to server...");
                    Log.d(TAG, "Device Num: " + deviceNum);
                    
                    NewApiService.ApiResponse bindResponse = apiService.bindDevice(
                        deviceNum,
                        null,
                        deviceNickname
                    );
                    
                    Log.d(TAG, "Bind response: " + (bindResponse != null ? bindResponse.isSuccess() : "null"));
                    
                    if (bindResponse == null) {
                        Log.e(TAG, "Bind response is null");
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
                        Log.e(TAG, "Bind failed - message: " + bindResponse.getMessage());
                        
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
                    
                    Log.d(TAG, "Bind success! Triggering data sync...");
                    
                    NewApiService.ApiResponse syncResponse = apiService.syncDevice(deviceNum);
                    Log.d(TAG, "Sync response: " + (syncResponse != null ? syncResponse.isSuccess() : "null"));
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Device newDevice = new Device(deviceNum, deviceNickname);
                            newDevice.setDeviceNum(deviceNum);
                            newDevice.setTag(selectedTag);
                            databaseHelper.addDevice(newDevice);
                            
                            int deletedCount = databaseHelper.deleteLocationRecordsByDevice(newDevice.getDeviceId());
                            Log.d(TAG, "Deleted " + deletedCount + " track records for new device");
                            
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
                    Log.e(TAG, "Error in bindDevice: " + e.getMessage(), e);
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
