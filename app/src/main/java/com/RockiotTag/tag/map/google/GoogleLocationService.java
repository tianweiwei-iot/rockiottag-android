package com.RockiotTag.tag.map.google;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * 谷歌定位服务（国际版专用）
 * 完全独立的谷歌定位实现，不依赖任何高德地图代码
 */
public class GoogleLocationService {
    private static final String TAG = "GoogleLocationService";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    
    private Context context;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private ServiceCallback callback;
    
    public interface ServiceCallback {
        void onLocationSuccess(double latitude, double longitude, float accuracy);
        void onLocationFailed(String error);
    }
    
    public GoogleLocationService(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        Log.d(TAG, "GoogleLocationService initialized for international version");
    }
    
    /**
     * 设置定位回调
     */
    public void setLocationCallback(ServiceCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 初始化并启动定位
     */
    public void startLocation() {
        // 检查权限
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            if (callback != null) {
                callback.onLocationFailed("Location permission not granted");
            }
            return;
        }
        
        try {
            Log.d(TAG, "Starting Google location service...");
            
            // 创建位置请求
            LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000) // 5秒更新一次
                .build();
            
            // 创建位置回调
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();
                        float accuracy = location.getAccuracy();
                        
                        Log.d(TAG, "Location success: lat=" + latitude + ", lng=" + longitude 
                            + ", accuracy=" + accuracy + "m");
                        
                        if (callback != null) {
                            callback.onLocationSuccess(latitude, longitude, accuracy);
                        }
                    } else {
                        Log.w(TAG, "Location is null");
                    }
                }
                
                @Override
                public void onLocationAvailability(
                        @NonNull com.google.android.gms.location.LocationAvailability availability) {
                    if (!availability.isLocationAvailable()) {
                        Log.w(TAG, "Location not available");
                        if (callback != null) {
                            callback.onLocationFailed("Location not available");
                        }
                    }
                }
            };
            
            // 开始位置更新
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            );
            
            Log.d(TAG, "Google location service started");
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception - missing location permissions", e);
            if (callback != null) {
                callback.onLocationFailed("Missing location permissions");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting location service: " + e.getMessage(), e);
            if (callback != null) {
                callback.onLocationFailed(e.getMessage());
            }
        }
    }
    
    /**
     * 停止定位
     */
    public void stopLocation() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
            Log.d(TAG, "Google location service stopped");
        }
    }
    
    /**
     * 获取当前位置（单次定位）
     */
    public void requestSingleLocation() {
        // 检查权限
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            if (callback != null) {
                callback.onLocationFailed("Location permission not granted");
            }
            return;
        }
        
        try {
            Log.d(TAG, "Requesting single location update...");
            
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            if (callback != null) {
                                callback.onLocationSuccess(
                                    location.getLatitude(),
                                    location.getLongitude(),
                                    location.getAccuracy()
                                );
                            }
                        } else {
                            Log.w(TAG, "Single location is null");
                            if (callback != null) {
                                callback.onLocationFailed("Location is null");
                            }
                        }
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Failed to get single location: " + e.getMessage(), e);
                        if (callback != null) {
                            callback.onLocationFailed(e.getMessage());
                        }
                    }
                });
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception: " + e.getMessage(), e);
            if (callback != null) {
                callback.onLocationFailed("Missing location permissions");
            }
        }
    }
    
    /**
     * 释放资源
     */
    public void onDestroy() {
        stopLocation();
    }
}
