package com.RockiotTag.tag;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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
    }
    
    @Override
    protected DecoratedBarcodeView initializeContent() {
        setContentView(R.layout.custom_scanner_layout);
        DecoratedBarcodeView barcodeView = findViewById(R.id.zxing_barcode_scanner);
        
        hideLaserLine(barcodeView);
        
        Map<DecodeHintType, Object> decodeHints = new EnumMap<>(DecodeHintType.class);
        decodeHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        decodeHints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        
        List<BarcodeFormat> formats = Collections.singletonList(BarcodeFormat.QR_CODE);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats, decodeHints, null, 2));
        
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
