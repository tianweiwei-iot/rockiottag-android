package com.RockiotTag.tag;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

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
    private Map<String, Device> deviceMap = new HashMap<>();


    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String languageCode = prefs.getString("language", "zh");
        LanguageUtils.applyLanguage(this, languageCode);
        
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_geofence);
        
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        radiusEditText = findViewById(R.id.radiusEditText);
        deviceSpinner = findViewById(R.id.deviceSpinner);
        saveButton = findViewById(R.id.saveButton);
        Button backButton = findViewById(R.id.back_btn);
        
        initMap();
        initNotificationChannel();
        initDevices();
        
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveGeofenceSettings();
            }
        });
        
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
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
        Device device = new Device("test-device-1", getString(R.string.device_default_name, "test-device-1"));
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
        try {
            float radius = Float.parseFloat(radiusEditText.getText().toString());
            if (radius <= 0) {
                Toast.makeText(this, R.string.invalid_radius, Toast.LENGTH_SHORT).show();
                return;
            }
            
            geofenceRadius = radius;
            LatLng center = deviceMarker.getPosition();
            updateGeofenceCircle(center, radius);
            
            Toast.makeText(this, R.string.save_geofence, Toast.LENGTH_SHORT).show();
            
            simulateGeofenceViolation();
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.enter_valid_radius, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void simulateGeofenceViolation() {
        Log.d(TAG, "模拟设备超出安全范围");
        sendGeofenceNotification(getString(R.string.test_device), getString(R.string.device_out_of_geofence));
    }
    
    public void sendGeofenceNotification(String deviceName, String message) {
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