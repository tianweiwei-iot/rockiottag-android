package com.RockiotTag.tag;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.journeyapps.barcodescanner.ViewfinderView;

import java.util.Collections;
import java.util.EnumMap;
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
        
        ImageView closeBtn = findViewById(R.id.close_btn);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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
        if (barcodeView != null) {
            isFlashOn = !isFlashOn;
            if (isFlashOn) {
                barcodeView.setTorchOn();
                flashButton.setImageResource(android.R.drawable.ic_menu_camera);
                Log.d(TAG, "Flash turned ON");
            } else {
                barcodeView.setTorchOff();
                flashButton.setImageResource(android.R.drawable.ic_menu_edit);
                Log.d(TAG, "Flash turned OFF");
            }
        }
    }
    
    @Override
    protected DecoratedBarcodeView initializeContent() {
        setContentView(R.layout.custom_scanner_layout);
        barcodeView = findViewById(R.id.zxing_barcode_scanner);
        
        hideLaserLine(barcodeView);
        
        Map<DecodeHintType, Object> decodeHints = new EnumMap<>(DecodeHintType.class);
        decodeHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        decodeHints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        decodeHints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        decodeHints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        
        List<BarcodeFormat> formats = Collections.singletonList(BarcodeFormat.QR_CODE);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats, decodeHints, null, 10));
        
        Log.d(TAG, "Scanner initialized with enhanced decoding");
        
        return barcodeView;
    }
    
    private void hideLaserLine(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewfinderView) {
                child.setVisibility(View.INVISIBLE);
            } else if (child instanceof ViewGroup) {
                hideLaserLine((ViewGroup) child);
            }
        }
    }
}
