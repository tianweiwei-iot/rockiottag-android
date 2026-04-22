package com.RockiotTag.tag;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.multi.MultipleBarcodeReader;
import com.google.zxing.qrcode.QRCodeReader;
import com.journeyapps.barcodescanner.camera.CameraSettings;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomCaptureActivity extends AppCompatActivity {

    private static final String TAG = "CustomCapture";
    
    private SurfaceView surfaceView;
    private ImageButton closeBtn;
    private ImageButton flashBtn;
    
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    
    private CameraManager cameraManager;
    private String cameraId;
    private Size previewSize;
    
    private boolean isFlashOn = false;
    private boolean isScanning = false;
    
    private QRCodeReader qrCodeReader;
    private Map<DecodeHintType, Object> decodeHints;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_custom_capture);
        
        Log.d(TAG, "CustomCaptureActivity onCreate");
        
        surfaceView = findViewById(R.id.preview_surface);
        closeBtn = findViewById(R.id.close_btn);
        flashBtn = findViewById(R.id.flash_btn);
        
        Log.d(TAG, "SurfaceView found: " + surfaceView);
        Log.d(TAG, "SurfaceHolder: " + surfaceView.getHolder());
        
        closeBtn.setOnClickListener(v -> finish());
        flashBtn.setOnClickListener(v -> toggleFlash());
        
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        
        initZXing();
        
        SurfaceHolder holder = surfaceView.getHolder();
        holder.setKeepScreenOn(true);
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "=== SURFACE CREATED ===");
                openCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "Surface changed: " + width + "x" + height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "Surface destroyed");
                closeCamera();
            }
        });
        Log.d(TAG, "SurfaceHolder callback added");
    }
    
    private void initZXing() {
        qrCodeReader = new QRCodeReader();
        decodeHints = new EnumMap<>(DecodeHintType.class);
        
        // 1. 开启TRY_HARDER模式
        decodeHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        
        // 2. 只识别QR_CODE
        decodeHints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));
        
        // 3. 设置字符集
        decodeHints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        
        // 4. 纯净条码模式（提高识别率）
        decodeHints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        
        Log.d(TAG, "ZXing initialized with TRY_HARDER mode");
    }
    
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finish();
            return;
        }
        
        startBackgroundThread();
        
        try {
            // 选择后置摄像头
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    
                    // 获取预览尺寸
                    Size[] sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            .getOutputSizes(ImageFormat.YUV_420_888);
                    if (sizes != null && sizes.length > 0) {
                        previewSize = sizes[0];
                        for (Size size : sizes) {
                            if (size.getWidth() >= 640 && size.getWidth() <= 1920) {
                                previewSize = size;
                                break;
                            }
                        }
                    }
                    break;
                }
            }
            
            if (cameraId == null) {
                Toast.makeText(this, "未找到后置摄像头", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            Log.d(TAG, "Opening camera: " + cameraId + ", previewSize: " + previewSize);
            
            // 创建ImageReader
            if (previewSize != null) {
                imageReader = ImageReader.newInstance(
                    previewSize.getWidth(), 
                    previewSize.getHeight(), 
                    ImageFormat.YUV_420_888, 
                    2
                );
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
            }
            
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access error", e);
            Toast.makeText(this, "无法访问相机: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera opened");
            cameraDevice = camera;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera disconnected");
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Camera error: " + error);
            camera.close();
            cameraDevice = null;
            finish();
        }
    };
    
    private void createCaptureSession() {
        if (cameraDevice == null || surfaceView.getHolder().getSurface() == null) {
            return;
        }
        
        try {
            List<Surface> surfaces = Arrays.asList(
                surfaceView.getHolder().getSurface(),
                imageReader.getSurface()
            );
            
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "Capture session configured");
                    captureSession = session;
                    startPreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Capture session configure failed");
                    finish();
                }
            }, backgroundHandler);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create capture session", e);
        }
    }
    
    private void startPreview() {
        if (cameraDevice == null || captureSession == null) {
            return;
        }
        
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surfaceView.getHolder().getSurface());
            builder.addTarget(imageReader.getSurface());
            
            // 2.1 强制开启连续自动对焦
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            
            // 2.2 调整曝光补偿，避免过曝
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -1);
            
            // 自动曝光
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            
            // 自动白平衡
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            
            // 如果闪光灯开启
            if (isFlashOn) {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            }
            
            captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
            Log.d(TAG, "Preview started with CONTINUOUS_AF and exposure compensation");
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to start preview", e);
        }
    }
    
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = reader -> {
        if (isScanning) {
            return;
        }
        
        Image image = reader.acquireLatestImage();
        if (image == null) {
            return;
        }
        
        isScanning = true;
        
        try {
            // 获取YUV数据
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();
            
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            
            byte[] nv21 = new byte[ySize + uSize + vSize];
            
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);
            
            int width = image.getWidth();
            int height = image.getHeight();
            
            // 2.3 裁剪预览区域，只解码中心区域
            int cropSize = Math.min(width, height) * 3 / 4;
            int left = (width - cropSize) / 2;
            int top = (height - cropSize) / 2;
            
            // 解码
            Result result = decodeFromYUV(nv21, width, height, left, top, cropSize, cropSize);
            
            if (result != null) {
                Log.d(TAG, "=== QR CODE FOUND ===");
                Log.d(TAG, "Content: " + result.getText());
                
                runOnUiThread(() -> {
                    Intent intent = new Intent();
                    intent.putExtra("SCAN_RESULT", result.getText());
                    setResult(RESULT_OK, intent);
                    finish();
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
        } finally {
            image.close();
            isScanning = false;
        }
    };
    
    private Result decodeFromYUV(byte[] nv21, int width, int height, int left, int top, int cropWidth, int cropHeight) {
        try {
            // 创建LuminanceSource
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                nv21, width, height, left, top, cropWidth, cropHeight, false
            );
            
            // 1.2 更换更鲁棒的二值化算法 - 使用GlobalHistogramBinarizer（更适合低对比度）
            BinaryBitmap binaryBitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
            
            // 尝试解码
            try {
                Result result = qrCodeReader.decode(binaryBitmap, decodeHints);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                Log.d(TAG, "GlobalHistogramBinarizer failed, trying HybridBinarizer");
            }
            
            // 如果GlobalHistogramBinarizer失败，尝试HybridBinarizer
            binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            try {
                return qrCodeReader.decode(binaryBitmap, decodeHints);
            } catch (Exception e) {
                Log.d(TAG, "HybridBinarizer also failed");
            }
            
            // 重置reader
            qrCodeReader.reset();
            
        } catch (Exception e) {
            Log.e(TAG, "decodeFromYUV error", e);
        }
        
        return null;
    }
    
    private void toggleFlash() {
        isFlashOn = !isFlashOn;
        
        if (flashBtn != null) {
            flashBtn.setImageResource(isFlashOn ? 
                android.R.drawable.ic_menu_camera : 
                android.R.drawable.ic_menu_edit);
        }
        
        // 重新启动预览以应用闪光灯设置
        if (captureSession != null) {
            startPreview();
        }
        
        Toast.makeText(this, isFlashOn ? "闪光灯已开启" : "闪光灯已关闭", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Flash: " + (isFlashOn ? "ON" : "OFF"));
    }
    
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread", e);
            }
        }
    }
    
    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        stopBackgroundThread();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }
    
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        closeCamera();
        super.onPause();
    }
    
    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        closeCamera();
        super.onDestroy();
    }
}
