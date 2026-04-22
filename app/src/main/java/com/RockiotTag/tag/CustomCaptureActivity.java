package com.RockiotTag.tag;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomCaptureActivity extends CaptureActivity {
    
    private static final String TAG = "CustomCapture";
    private DecoratedBarcodeView barcodeView;
    private ImageButton flashButton;
    private boolean isFlashOn = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "CustomCaptureActivity.onCreate()");
        
        ImageButton closeBtn = findViewById(R.id.close_btn);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Close button clicked");
                    finish();
                }
            });
        }
        
        flashButton = findViewById(R.id.flash_btn);
        if (flashButton != null) {
            flashButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleFlash();
                }
            });
        }
    }
    
    private void toggleFlash() {
        Log.d(TAG, "toggleFlash() called");
        if (barcodeView != null) {
            isFlashOn = !isFlashOn;
            if (isFlashOn) {
                barcodeView.setTorchOn();
                Log.d(TAG, "Flash turned ON");
                Toast.makeText(this, "闪光灯已开启", Toast.LENGTH_SHORT).show();
            } else {
                barcodeView.setTorchOff();
                Log.d(TAG, "Flash turned OFF");
                Toast.makeText(this, "闪光灯已关闭", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected DecoratedBarcodeView initializeContent() {
        setContentView(R.layout.custom_scanner_layout);
        Log.d(TAG, "ContentView set");
        
        barcodeView = findViewById(R.id.zxing_barcode_scanner);
        Log.d(TAG, "BarcodeView found");
        
        // 支持所有二维码/条形码格式，不要限制太多！
        List<BarcodeFormat> allFormats = new ArrayList<>();
        allFormats.add(BarcodeFormat.QR_CODE);
        allFormats.add(BarcodeFormat.CODE_128);
        allFormats.add(BarcodeFormat.CODE_39);
        allFormats.add(BarcodeFormat.EAN_13);
        allFormats.add(BarcodeFormat.EAN_8);
        allFormats.add(BarcodeFormat.UPC_A);
        allFormats.add(BarcodeFormat.DATA_MATRIX);
        allFormats.add(BarcodeFormat.AZTEC);
        allFormats.add(BarcodeFormat.PDF_417);
        Log.d(TAG, "Formats to scan: " + allFormats);
        
        // 疯狂的解码提示，尝试一切可能性！
        Map<DecodeHintType, Object> decodeHints = new EnumMap<>(DecodeHintType.class);
        decodeHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        decodeHints.put(DecodeHintType.POSSIBLE_FORMATS, allFormats);
        decodeHints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        decodeHints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        Log.d(TAG, "Decode hints: " + decodeHints);
        
        // 设置解码器工厂，疯狂尝试解码
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(allFormats, decodeHints, null, 20));
        Log.d(TAG, "Decoder factory set with 20 attempts");
        
        return barcodeView;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        if (barcodeView != null) {
            barcodeView.resume();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        if (barcodeView != null) {
            barcodeView.pause();
        }
    }
}
