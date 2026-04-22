package com.RockiotTag.tag;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class WebViewMapActivity extends AppCompatActivity {
    private WebView webView;
    private double currentLat = 22.543611;
    private double currentLng = 113.881944;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        webView = new WebView(this);
        setContentView(webView);
        
        // 获取传递的坐标
        if (getIntent() != null) {
            currentLat = getIntent().getDoubleExtra("lat", currentLat);
            currentLng = getIntent().getDoubleExtra("lng", currentLng);
        }
        
        // 显示调试信息
        Toast.makeText(this, "位置: " + currentLat + ", " + currentLng, Toast.LENGTH_LONG).show();
        
        setupWebView();
        loadGoogleMaps();
    }
    
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // 拦截 intent scheme
                if (url != null && (url.startsWith("intent:") || url.startsWith("tel:") || url.startsWith("mailto:"))) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        intent.addCategory(Intent.CATEGORY_BROWSABLE);
                        intent.setComponent(null);
                        startActivity(intent);
                    } catch (Exception e) {
                        // 如果无法处理，不做任何事
                    }
                    return true;
                }
                return false; // 其他URL在WebView中加载
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Toast.makeText(WebViewMapActivity.this, "地图加载完成", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Toast.makeText(WebViewMapActivity.this, "加载失败: " + description, Toast.LENGTH_SHORT).show();
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
    }
    
    private void loadGoogleMaps() {
        // 使用简单的Google Maps URL，避免复杂的重定向
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n" +
                "    <style>\n" +
                "        body, html { margin: 0; padding: 0; height: 100%; width: 100%; overflow: hidden; }\n" +
                "        #map { height: 100%; width: 100%; border: none; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <iframe\n" +
                "        id=\"map\"\n" +
                "        src=\"https://maps.google.com/maps?q=" + currentLat + "," + currentLng + "&z=15&output=embed\">\n" +
                "    </iframe>\n" +
                "</body>\n" +
                "</html>";
        
        webView.loadDataWithBaseURL("https://maps.google.com", html, "text/html", "UTF-8", null);
    }
    
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
