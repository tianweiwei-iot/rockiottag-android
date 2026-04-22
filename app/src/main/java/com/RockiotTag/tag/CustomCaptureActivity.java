package com.RockiotTag.tag;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.journeyapps.barcodescanner.camera.CameraSettings;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class CustomCaptureActivity extends CaptureActivity {
    
    private static final String TAG = "CustomCapture";
    private DecoratedBarcodeView barcodeView;
    private ImageButton flashBtn;
    private boolean isFlashOn = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "CustomCaptureActivity onCreate");
        
        flashBtn = findViewById(R.id.flash_btn);
        if (flashBtn != null) {
            flashBtn.setOnClickListener(v -> toggleFlash());
        }
        
        ImageButton closeBtn = findViewById(R.id.close_btn);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> finish());
        }
    }
    
    @Override
    protected DecoratedBarcodeView initializeContent() {
        Log.d(TAG, "initializeContent called");
        
        setContentView(R.layout.activity_custom_capture);
        barcodeView = findViewById(R.id.zxing_barcode_scanner);
        
        if (barcodeView == null) {
            Log.e(TAG, "barcodeView is null!");
            return null;
        }
        
        Log.d(TAG, "barcodeView found: " + barcodeView);
        
        // 配置相机设置
        CameraSettings cameraSettings = new CameraSettings();
        cameraSettings.setRequestedCameraId(0); // 后置摄像头
        cameraSettings.setAutoTorchEnabled(false);
        cameraSettings.setContinuousFocusEnabled(true); // 连续自动对焦
        cameraSettings.setExposureEnabled(true); // 自动曝光
        barcodeView.getBarcodeView().setCameraSettings(cameraSettings);
        
        // 配置解码提示
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        
        // 设置解码器工厂
        List<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.QR_CODE);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats, hints, null, 3));
        
        Log.d(TAG, "ZXing configured with TRY_HARDER, continuous focus, exposure enabled");
        
        return barcodeView;
    }
    
    private void toggleFlash() {
        if (barcodeView != null) {
            isFlashOn = !isFlashOn;
            if (isFlashOn) {
                barcodeView.setTorchOn();
                Toast.makeText(this, "闪光灯已开启", Toast.LENGTH_SHORT).show();
            } else {
                barcodeView.setTorchOff();
                Toast.makeText(this, "闪光灯已关闭", Toast.LENGTH_SHORT).show();
            }
            Log.d(TAG, "Flash: " + (isFlashOn ? "ON" : "OFF"));
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        
        // 等待视图布局完成后再启动相机
        if (barcodeView != null) {
            barcodeView.post(() -> {
                Log.d(TAG, "barcodeView size after layout: " + barcodeView.getWidth() + "x" + barcodeView.getHeight());
                barcodeView.resume();
                Log.d(TAG, "barcodeView.resume() called");
            });
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (barcodeView != null) {
            barcodeView.pause();
        }
    }
}
