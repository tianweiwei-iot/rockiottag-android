package com.RockiotTag.tag;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.RockiotTag.tag.viewmodel.WebViewMapViewModel;

public class WebViewMapActivity extends AppCompatActivity {
    private static final String TAG = "WebViewMapActivity";
    private WebView webView;
    private double currentLat = 22.543611;
    private double currentLng = 113.881944;
    
    // MVVM - ViewModel
    private WebViewMapViewModel viewModel;
    
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
        
        webView = new WebView(this);
        setContentView(webView);
        
        // 获取传递的坐标
        if (getIntent() != null) {
            currentLat = getIntent().getDoubleExtra("lat", currentLat);
            currentLng = getIntent().getDoubleExtra("lng", currentLng);
        }
        
        // 显示调试信息
        Toast.makeText(this, "位置: " + currentLat + ", " + currentLng, Toast.LENGTH_LONG).show();
        
        // MVVM - 初始化 ViewModel
        viewModel = new ViewModelProvider(this).get(WebViewMapViewModel.class);
        setupViewModelObservers();
        
        setupWebView();
        loadGoogleMaps();
    }
    
    /**
     * MVVM - 设置 ViewModel 观察者
     */
    private void setupViewModelObservers() {
        viewModel.getPageUrl().observe(this, url -> {
            // URL 变化时可以在这里处理
        });
        
        viewModel.getIsLoading().observe(this, isLoading -> {
            // 可以根据加载状态显示/隐藏进度条
        });
        
        viewModel.getProgress().observe(this, progress -> {
            // 可以更新进度条
        });
        
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });
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
                // MVVM - 通知 ViewModel 页面加载完成
                viewModel.onPageFinished();
                Toast.makeText(WebViewMapActivity.this, "地图加载完成", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                // MVVM - 通知 ViewModel 页面加载失败
                viewModel.onPageError(description);
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
        // 获取用户选择的语言
        String languageCode = getMapLanguageCode();
        String regionCode = getRegionCode();
        
        // MVVM - 使用 ViewModel 加载地图页面
        String mapUrl = "https://maps.google.com/maps?q=" + currentLat + "," + currentLng + 
                        "&z=15&output=embed&hl=" + languageCode + "&region=" + regionCode;
        viewModel.loadMapPage(mapUrl);
        
        // 使用简单的Google Maps URL，添加语言和地区参数
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
                "        src=\"" + mapUrl + "\">\n" +
                "    </iframe>\n" +
                "</body>\n" +
                "</html>";
        
        webView.loadDataWithBaseURL("https://maps.google.com", html, "text/html", "UTF-8", null);
    }
    
    /**
     * 获取地图显示使用的语言代码
     * @return Google Maps 支持的语言代码
     */
    private String getMapLanguageCode() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String languageCode = prefs.getString("language", "zh");
        
        // 将应用的语言代码转换为 Google Maps 支持的语言代码
        switch (languageCode) {
            case "zh":
                return "zh-CN";  // 中文（简体）
            case "en":
                return "en";     // 英语
            case "pt-rBR":
                return "pt-BR";  // 巴西葡萄牙语
            case "ru":
                return "ru";     // 俄语
            case "hi":
                return "hi";     // 印地语
            case "tr":
                return "tr";     // 土耳其语
            default:
                return "en";     // 默认使用英语
        }
    }
    
    /**
     * 获取地区代码，影响地图POI和边界显示
     * @return ISO 3166-1 alpha-2 地区代码
     */
    private String getRegionCode() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String languageCode = prefs.getString("language", "zh");
        
        // 根据语言返回对应的地区代码
        switch (languageCode) {
            case "zh":
                return "CN";     // 中国
            case "en":
                return "US";     // 美国
            case "pt-rBR":
                return "BR";     // 巴西
            case "ru":
                return "RU";     // 俄罗斯
            case "hi":
                return "IN";     // 印度
            case "tr":
                return "TR";     // 土耳其
            default:
                return "US";     // 默认使用美国
        }
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
