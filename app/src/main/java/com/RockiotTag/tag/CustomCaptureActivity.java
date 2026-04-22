package com.RockiotTag.tag;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.ExifInterface;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_capture);
        
        Log.d(TAG, "ML Kit CustomCaptureActivity onCreate");
        
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
        Log.d(TAG, "Starting camera with ML Kit...");
        
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                
                // 预览 - 使用更高分辨率
                Preview preview = new Preview.Builder()
                    .setTargetResolution(new Size(1920, 1080))
                    .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                
                // 图像分析 - 使用更高分辨率，提高识别率
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(1920, 1080))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
                
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
                
                // 选择后置摄像头
                CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();
                
                // 解绑所有用例
                cameraProvider.unbindAll();
                
                // 绑定用例到生命周期
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                
                // 启用连续自动对焦
                camera.getCameraControl().setLinearZoom(0f);
                
                Log.d(TAG, "Camera started successfully with ML Kit (1920x1080)");
                
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                Toast.makeText(this, "相机启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                                    Log.d(TAG, "=== ML Kit QR CODE FOUND ===");
                                    Log.d(TAG, "Content: " + rawValue);
                                    Log.d(TAG, "Length: " + rawValue.length());
                                    
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
            
            Toast.makeText(this, isFlashOn ? "闪光灯已开启" : "闪光灯已关闭", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Flash: " + (isFlashOn ? "ON" : "OFF"));
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show();
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
