package com.RockiotTag.tag;

import com.RockiotTag.tag.util.ToastHelper;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;
import android.util.Size;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import android.media.Image;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomCaptureActivity extends AppCompatActivity {

    private static final String TAG = "CustomCapture";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;

    private PreviewView previewView;
    private ImageButton flashBtn;
    private ImageButton closeBtn;
    
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private BarcodeScanner barcodeScanner;
    private ExecutorService cameraExecutor;
    private boolean isFlashOn = false;
    private boolean isScanning = true;

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
        setContentView(R.layout.activity_custom_capture);
        
        // 隐藏系统 ActionBar（使用自定义标题栏）
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        LogUtil.d(TAG, "ML Kit CustomCaptureActivity onCreate");
        
        previewView = findViewById(R.id.preview_view);
        flashBtn = findViewById(R.id.flash_btn);
        closeBtn = findViewById(R.id.close_btn);
        
        flashBtn.setOnClickListener(v -> toggleFlash());
        closeBtn.setOnClickListener(v -> finish());
        
        // 初始化ML Kit条码扫描器
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        // 检查相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }
    
    private void startCamera() {
        LogUtil.d(TAG, "Starting camera with ML Kit and AUTO FOCUS...");
        
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                
                // 选择后置摄像头
                CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();
                
                // 预览 - 配置自动对焦
                Preview.Builder previewBuilder = new Preview.Builder()
                    .setTargetResolution(new Size(1920, 1080));
                
                // 使用Camera2Interop启用连续自动对焦
                Camera2Interop.Extender<Preview> previewExtender = new Camera2Interop.Extender<>(previewBuilder);
                previewExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, 
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                previewExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);
                
                Preview preview = previewBuilder.build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                
                // 图像分析 - 配置自动对焦
                ImageAnalysis.Builder analysisBuilder = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(1920, 1080))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
                
                // 图像分析也启用自动对焦
                Camera2Interop.Extender<ImageAnalysis> analysisExtender = new Camera2Interop.Extender<>(analysisBuilder);
                analysisExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                
                ImageAnalysis imageAnalysis = analysisBuilder.build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
                
                // 解绑所有用例
                cameraProvider.unbindAll();
                
                // 绑定用例到生命周期
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                
                LogUtil.d(TAG, "Camera started with CONTINUOUS_AUTO_FOCUS enabled");
                
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                ToastHelper.show(this, getString(R.string.camera_start_failed, e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }
    
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (!isScanning) {
            imageProxy.close();
            return;
        }
        
        try {
            Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage inputImage = InputImage.fromMediaImage(
                    mediaImage, 
                    imageProxy.getImageInfo().getRotationDegrees()
                );
                
                Task<List<Barcode>> result = barcodeScanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty() && isScanning) {
                            for (Barcode barcode : barcodes) {
                                String rawValue = barcode.getRawValue();
                                if (rawValue != null && !rawValue.isEmpty()) {
                                    LogUtil.d(TAG, "=== ML Kit QR CODE FOUND ===");
                                    LogUtil.d(TAG, "Content: " + rawValue);
                                    LogUtil.d(TAG, "Length: " + rawValue.length());
                                    
                                    isScanning = false;
                                    
                                    runOnUiThread(() -> {
                                        Intent intent = new Intent();
                                        intent.putExtra("SCAN_RESULT", rawValue);
                                        setResult(RESULT_OK, intent);
                                        finish();
                                    });
                                    break;
                                }
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Barcode scan failed", e))
                    .addOnCompleteListener(task -> imageProxy.close());
                
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing image", e);
        }
        
        imageProxy.close();
    }
    
    private void toggleFlash() {
        if (camera != null) {
            isFlashOn = !isFlashOn;
            camera.getCameraControl().enableTorch(isFlashOn);
            
            ToastHelper.show(this, isFlashOn ? R.string.flash_on : R.string.flash_off);
            LogUtil.d(TAG, "Flash: " + (isFlashOn ? "ON" : "OFF"));
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                ToastHelper.show(this, R.string.camera_permission_needed);
                finish();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
    }
}
