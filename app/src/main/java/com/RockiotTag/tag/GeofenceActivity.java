package com.RockiotTag.tag;

import com.RockiotTag.tag.util.ToastHelper;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.RockiotTag.tag.viewmodel.GeofenceViewModel;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.Circle;
import com.amap.api.maps.model.CircleOptions;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;

import java.util.HashMap;
import java.util.Map;

public class GeofenceActivity extends AppCompatActivity {
    private static final String TAG = "GeofenceActivity";
    private static final String CHANNEL_ID = "geofence_notification_channel";
    
    private MapView mapView;
    private AMap aMap;
    private Circle geofenceCircle;
    private Marker deviceMarker;
    private EditText radiusEditText;
    private Spinner deviceSpinner;
    private Button saveButton;
    
    private double currentLatitude = 22.543611;
    private double currentLongitude = 113.881944;
    private float geofenceRadius = 100;
    private Map<String, TagDevice> deviceMap = new HashMap<>();
    
    // MVVM - ViewModel
    private GeofenceViewModel viewModel;


    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_geofence);
        
        // 隐藏系统 ActionBar（使用自定义标题栏）
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        mapView = findViewById(R.id.mapView);
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
        }
        radiusEditText = findViewById(R.id.radiusEditText);
        deviceSpinner = findViewById(R.id.deviceSpinner);
        saveButton = findViewById(R.id.saveButton);
        Button backButton = findViewById(R.id.back_btn);

        // MVVM - 初始化 ViewModel
        viewModel = new ViewModelProvider(this).get(GeofenceViewModel.class);
        setupViewModelObservers();

        initMap();
        initNotificationChannel();
        initDevices();

        if (saveButton != null) {
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveGeofenceSettings();
                }
            });
        }

        if (backButton != null) {
            backButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }
    
    /**
     * MVVM - 设置 ViewModel 观察者
     */
    private void setupViewModelObservers() {
        viewModel.getStatusMessage().observe(this, message -> {
            if (message != null) {
                ToastHelper.show(this, message);
            }
        });
        
        viewModel.getSaveSuccess().observe(this, success -> {
            if (success) {
                // 保存成功后的操作
            }
        });
    }
    
    private void initMap() {
        if (aMap == null) {
            aMap = mapView.getMap();
            if (aMap != null) {
                // 启用默认的定位按钮和定位标记
                aMap.getUiSettings().setMyLocationButtonEnabled(true);
                aMap.setMyLocationEnabled(true);
                
                // 添加设备标记
                LatLng deviceLatLng = new LatLng(currentLatitude, currentLongitude);
                deviceMarker = aMap.addMarker(new MarkerOptions()
                        .position(deviceLatLng)
                        .title(getString(R.string.device_location))
                        .snippet(getString(R.string.click_to_set_geofence))
                );
                
                // 添加默认安全范围圆圈
                updateGeofenceCircle(deviceLatLng, geofenceRadius);
                
                // 移动相机到设备位置
                aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(deviceLatLng, 18));
            }
        }
    }
    
    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.geofence_notification_channel);
            String description = getString(R.string.geofence_notification_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    private void initDevices() {
        TagDevice device = new TagDevice("test-device-1", getString(R.string.device_default_name, "test-device-1"));
        device.setLatitude(currentLatitude);
        device.setLongitude(currentLongitude);
        deviceMap.put(device.getDeviceId(), device);
    }
    
    private void updateGeofenceCircle(LatLng center, float radius) {
        if (geofenceCircle != null) {
            geofenceCircle.remove();
        }
        
        geofenceCircle = aMap.addCircle(new CircleOptions()
                .center(center)
                .radius(radius)
                .strokeColor(0x440000FF)
                .fillColor(0x220000FF)
                .strokeWidth(2)
        );
    }
    
    private void saveGeofenceSettings() {
        String radiusText = radiusEditText.getText().toString();
        
        // MVVM - 验证半径
        if (!viewModel.validateRadius(radiusText)) {
            ToastHelper.show(this, R.string.enter_valid_radius);
            return;
        }
        
        float radius = viewModel.parseRadius(radiusText);
        
        // MVVM - 使用 ViewModel 保存设置
        viewModel.saveGeofenceSettings(radius, new GeofenceViewModel.SaveCallback() {
            @Override
            public void onSuccess(float savedRadius) {
                geofenceRadius = savedRadius;
                LatLng center = deviceMarker.getPosition();
                updateGeofenceCircle(center, savedRadius);
                simulateGeofenceViolation();
            }
            
            @Override
            public void onError(String error) {
                // 错误已在观察者中处理
            }
        });
    }
    
    private void simulateGeofenceViolation() {
        LogUtil.d(TAG, "模拟设备超出安全范围");
        sendGeofenceNotification(getString(R.string.test_device), getString(R.string.device_out_of_geofence));
    }
    
    public void sendGeofenceNotification(String deviceName, String message) {
        // 检查通知权限（Android 13+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 没有权限，不发送通知
                return;
            }
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(getString(R.string.geofence_reminder))
                .setContentText(deviceName + ": " + message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}