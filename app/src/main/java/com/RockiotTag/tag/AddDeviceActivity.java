package com.RockiotTag.tag;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
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
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public class AddDeviceActivity extends AppCompatActivity {

    private static final String TAG = "AddDeviceActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 102;
    private static final int MAX_IMAGE_SIZE = 1024;

    private TextView statusText;
    private ImageButton scanQrBtn;
    private Button btnCameraScan;
    private Button btnGalleryScan;
    private EditText deviceNumEdit;
    private Spinner tagSpinner;
    private EditText deviceNicknameEdit;
    private Button bindDeviceBtn;
    private String selectedTag = "";

    private Handler handler;
    private NewApiService apiService;
    private DatabaseHelper databaseHelper;

    // 自定义相机扫码
    private final ActivityResultLauncher<Intent> customScanLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            Log.d(TAG, "=== CUSTOM SCAN CALLBACK ===");
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                String scanResult = result.getData().getStringExtra("SCAN_RESULT");
                if (scanResult != null && !scanResult.isEmpty()) {
                    Log.d(TAG, "Scan result: [" + scanResult + "]");
                    deviceNumEdit.setText(scanResult);
                    deviceNumEdit.setSelection(scanResult.length());
                    Toast.makeText(this, getString(R.string.scan_success, scanResult), Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "Scan result is null or empty");
                    Toast.makeText(this, R.string.scan_no_content, Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "Scan cancelled");
            }
        }
    );

    // 相册选择图片
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            Log.d(TAG, "=== GALLERY CALLBACK ===");
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                if (imageUri != null) {
                    Log.d(TAG, "Selected image: " + imageUri);
                    decodeQRCodeFromUriOptimized(imageUri);
                }
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
        backBtn.setOnClickListener(v -> finish());

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
        
        Log.d(TAG, "AddDeviceActivity created");
    }

    private void initTagSpinner() {
        String[] tags = {"dog", "boy", "car", "bike", "bank_card", "girl", "key", "moto", "pig", "wallet", "bag", "cat", "bird"};
        String[] icons = {"🐕", "👦", "🚗", "🚴", "💳", "👧", "🔑", "🏍️", "🐷", "👛", "👜", "🐱", "🐦"};
        
        java.util.ArrayList<String> tagList = new java.util.ArrayList<>();
        tagList.add(getString(R.string.select_tag));
        tagList.addAll(java.util.Arrays.asList(tags));
        
        java.util.ArrayList<String> iconList = new java.util.ArrayList<>();
        iconList.add("");
        iconList.addAll(java.util.Arrays.asList(icons));
        
        TagAdapter tagAdapter = new TagAdapter(this, tagList, iconList);
        tagSpinner.setAdapter(tagAdapter);

        tagSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTag = position > 0 ? tagList.get(position) : "";
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedTag = "";
            }
        });
    }

    private void setupTextWatchers() {
        deviceNumEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s != null ? s.toString() : "";
                String upper = text.toUpperCase();
                if (!text.equals(upper)) {
                    s.replace(0, s.length(), upper);
                    return;
                }
                checkInputsAndEnableButton();
            }
        });

        deviceNicknameEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                checkInputsAndEnableButton();
            }
        });
    }

    private void checkInputsAndEnableButton() {
        String deviceNum = deviceNumEdit.getText().toString().trim().replace(":", "");
        String nickname = deviceNicknameEdit.getText().toString().trim();
        
        boolean canBind = !deviceNum.isEmpty() && !nickname.isEmpty();
        bindDeviceBtn.setEnabled(canBind);
        bindDeviceBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            canBind ? 0xFF2196F3 : 0xFF888888));
    }

    private void setupButtons() {
        scanQrBtn.setOnClickListener(v -> startCustomScan());
        btnCameraScan.setOnClickListener(v -> startCustomScan());
        btnGalleryScan.setOnClickListener(v -> startGallerySelect());
        bindDeviceBtn.setOnClickListener(v -> bindDevice());
    }

    private void startCustomScan() {
        Log.d(TAG, "Starting custom scan...");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            Intent intent = new Intent(this, CustomCaptureActivity.class);
            customScanLauncher.launch(intent);
        }
    }

    private void startGallerySelect() {
        Log.d(TAG, "Starting gallery select...");
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    // 三、相册图片识别优化
    private void decodeQRCodeFromUriOptimized(Uri imageUri) {
        Log.d(TAG, "=== DECODE FROM GALLERY (OPTIMIZED) ===");
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    runOnUiThread(() -> Toast.makeText(this, R.string.cannot_read_image, Toast.LENGTH_SHORT).show());
                    return;
                }

                // 3.1 处理图片方向（EXIF旋转）
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(inputStream, null, options);
                inputStream.close();

                // 3.3 缩小图片尺寸，避免OOM
                int width = options.outWidth;
                int height = options.outHeight;
                int sampleSize = 1;
                while (width > MAX_IMAGE_SIZE || height > MAX_IMAGE_SIZE) {
                    sampleSize *= 2;
                    width /= 2;
                    height /= 2;
                }
                Log.d(TAG, "Sample size: " + sampleSize + ", target: " + width + "x" + height);

                options.inJustDecodeBounds = false;
                options.inSampleSize = sampleSize;

                inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                inputStream.close();

                if (bitmap == null) {
                    runOnUiThread(() -> Toast.makeText(this, R.string.cannot_decode_image, Toast.LENGTH_SHORT).show());
                    return;
                }

                Log.d(TAG, "Bitmap loaded: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                // 3.1 处理EXIF旋转
                bitmap = rotateImageIfRequired(this, bitmap, imageUri);
                Log.d(TAG, "After rotation: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                // 3.2 图片对比度增强预处理
                bitmap = enhanceContrast(bitmap);
                Log.d(TAG, "After contrast enhancement");

                // 解码
                Result result = decodeBitmapOptimized(bitmap);
                
                if (result != null) {
                    String content = result.getText();
                    Log.d(TAG, "=== GALLERY DECODE SUCCESS ===");
                    Log.d(TAG, "Content: [" + content + "]");
                    
                    runOnUiThread(() -> {
                        deviceNumEdit.setText(content);
                        deviceNumEdit.setSelection(content.length());
                        Toast.makeText(this, getString(R.string.recognize_success, content), Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, R.string.cannot_recognize_qr, Toast.LENGTH_SHORT).show());
                }
                
                bitmap.recycle();
                
            } catch (Exception e) {
                Log.e(TAG, "Gallery decode error", e);
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.recognize_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 3.1 处理EXIF旋转
    private Bitmap rotateImageIfRequired(Context context, Bitmap bitmap, Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return bitmap;
            
            ExifInterface exif = new ExifInterface(inputStream);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            inputStream.close();
            
            int rotation = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: rotation = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: rotation = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: rotation = 270; break;
                default: return bitmap;
            }
            
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return rotated;
            
        } catch (Exception e) {
            Log.e(TAG, "Error rotating image", e);
            return bitmap;
        }
    }

    // 3.2 对比度增强
    private Bitmap enhanceContrast(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        // 增强对比度
        float contrast = 1.5f;
        for (int i = 0; i < pixels.length; i++) {
            int r = Color.red(pixels[i]);
            int g = Color.green(pixels[i]);
            int b = Color.blue(pixels[i]);
            
            // 应用对比度增强
            r = clamp((int)((r - 128) * contrast + 128));
            g = clamp((int)((g - 128) * contrast + 128));
            b = clamp((int)((b - 128) * contrast + 128));
            
            pixels[i] = Color.rgb(r, g, b);
        }

        Bitmap enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        enhanced.setPixels(pixels, 0, width, 0, 0, width, height);
        return enhanced;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // 优化的解码方法
    private Result decodeBitmapOptimized(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);

        // 解码提示
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));

        QRCodeReader reader = new QRCodeReader();

        // 尝试GlobalHistogramBinarizer（更适合低对比度）
        try {
            BinaryBitmap binaryBitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
            Result result = reader.decode(binaryBitmap, hints);
            if (result != null) return result;
        } catch (Exception e) {
            Log.d(TAG, "GlobalHistogramBinarizer failed");
        }

        // 尝试HybridBinarizer
        try {
            reader.reset();
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            return reader.decode(binaryBitmap, hints);
        } catch (Exception e) {
            Log.d(TAG, "HybridBinarizer failed");
        }

        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCustomScan();
        }
    }

    private void bindDevice() {
        String deviceNumInput = deviceNumEdit.getText().toString().trim();
        String nickname = deviceNicknameEdit.getText().toString().trim();

        if (deviceNumInput.isEmpty()) {
            Toast.makeText(this, R.string.please_enter_device_number, Toast.LENGTH_SHORT).show();
            return;
        }

        final String deviceNum = deviceNumInput.contains(":") ? 
            deviceNumInput.replace(":", "").toUpperCase() : deviceNumInput.toUpperCase();

        if (nickname.isEmpty()) {
            Toast.makeText(this, R.string.please_enter_nickname, Toast.LENGTH_SHORT).show();
            return;
        }

        NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(deviceNum));

        if (databaseHelper.isDeviceBound(deviceNum)) {
            Toast.makeText(this, R.string.device_already_added, Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText(R.string.binding_device);
        bindDeviceBtn.setEnabled(false);

        new Thread(() -> {
            try {
                if (!apiService.isAuthenticated()) {
                    NewApiService.ApiResponse loginResponse = apiService.login(
                        ApiConfig.getCid(), ApiConfig.getCustomerCode(), ApiConfig.getPassword());
                    if (loginResponse == null || !loginResponse.isSuccess()) {
                        runOnUiThread(() -> {
                            statusText.setText(R.string.login_failed);
                            bindDeviceBtn.setEnabled(true);
                        });
                        return;
                    }
                }

                NewApiService.DeviceInfo deviceInfo = apiService.getDeviceLatest(deviceNum);
                Log.d(TAG, "Check device on server: " + deviceNum + ", result: " + (deviceInfo != null ? "found" : "not found"));
                
                if (deviceInfo == null || deviceInfo.deviceNum == null || deviceInfo.deviceNum.isEmpty()) {
                    runOnUiThread(() -> {
                        statusText.setText(R.string.device_not_activated);
                        Toast.makeText(this, R.string.device_not_activated, Toast.LENGTH_LONG).show();
                        bindDeviceBtn.setEnabled(true);
                    });
                    return;
                }

                NewApiService.ApiResponse bindResponse = apiService.bindDevice(deviceNum, null, nickname);
                
                if (bindResponse == null || !bindResponse.isSuccess()) {
                    String msg = bindResponse != null ? bindResponse.getMessage() : getString(R.string.bind_failed);
                    runOnUiThread(() -> {
                        statusText.setText(msg);
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        bindDeviceBtn.setEnabled(true);
                    });
                    return;
                }

                runOnUiThread(() -> {
                    Device newDevice = new Device(deviceNum, nickname);
                    newDevice.setTag(selectedTag);
                    if (deviceInfo.latitude != 0 && deviceInfo.longitude != 0) {
                        newDevice.setLatitude(deviceInfo.latitude);
                        newDevice.setLongitude(deviceInfo.longitude);
                    }
                    if (deviceInfo.battery != 0) {
                        newDevice.setSignalStrength(deviceInfo.battery);
                    }
                    if (deviceInfo.timestamp != 0) {
                        newDevice.setLastSeen(deviceInfo.timestamp);
                    }
                    if (deviceInfo.address != null && !deviceInfo.address.isEmpty()) {
                        newDevice.setAddress(deviceInfo.address);
                    }
                    databaseHelper.addDevice(newDevice);
                    
                    getSharedPreferences("app_settings", MODE_PRIVATE)
                        .edit().putString("selected_device_id", newDevice.getDeviceId()).apply();
                    
                    Toast.makeText(this, R.string.device_bind_success, Toast.LENGTH_SHORT).show();
                    handler.postDelayed(() -> finish(), 1500);
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText(getString(R.string.bind_failed_with_error, e.getMessage()));
                    bindDeviceBtn.setEnabled(true);
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }
}
