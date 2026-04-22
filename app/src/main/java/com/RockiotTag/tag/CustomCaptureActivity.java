package com.RockiotTag.tag;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.ViewfinderView;

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
