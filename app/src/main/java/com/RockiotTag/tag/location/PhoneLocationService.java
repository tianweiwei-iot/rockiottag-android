package com.RockiotTag.tag.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.RockiotTag.tag.model.PhoneLocation;

/**
 * 手机定位服务
 * 提供获取手机当前位置的功能，支持GPS和网络定位
 */
public class PhoneLocationService {
    
    private static final String TAG = "PhoneLocationService";
    private static final long LOCATION_FRESH_THRESHOLD = 5 * 60 * 1000; // 5分钟
    
    private Context context;
    private LocationManager locationManager;
    
    public PhoneLocationService(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }
    
    /**
     * 获取当前位置
     * @return PhoneLocation对象，如果不可用返回null
     */
    public PhoneLocation getCurrentLocation() {
        // 1. 检查权限
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted");
            return null;
        }
        
        // 2. 尝试GPS定位（优先级高）
        Location gpsLocation = getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (gpsLocation != null && isLocationFresh(gpsLocation)) {
            Log.d(TAG, String.format(
                "Using GPS location: lat=%.6f, lng=%.6f, accuracy=%.1fm",
                gpsLocation.getLatitude(),
                gpsLocation.getLongitude(),
                gpsLocation.getAccuracy()
            ));
            return convertToPhoneLocation(gpsLocation);
        }
        
        // 3. 降级到网络定位
        Location networkLocation = getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (networkLocation != null && isLocationFresh(networkLocation)) {
            Log.d(TAG, String.format(
                "Using network location: lat=%.6f, lng=%.6f, accuracy=%.1fm",
                networkLocation.getLatitude(),
                networkLocation.getLongitude(),
                networkLocation.getAccuracy()
            ));
            return convertToPhoneLocation(networkLocation);
        }
        
        Log.w(TAG, "No fresh location available");
        return null;
    }
    
    /**
     * 检查是否有定位权限
     */
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * 获取最后已知位置
     */
    private Location getLastKnownLocation(String provider) {
        if (locationManager == null) {
            return null;
        }
        try {
            return locationManager.getLastKnownLocation(provider);
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting location", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error getting location", e);
            return null;
        }
    }
    
    /**
     * 检查位置是否新鲜（5分钟内）
     */
    private boolean isLocationFresh(Location location) {
        if (location == null) {
            return false;
        }
        
        long age = System.currentTimeMillis() - location.getTime();
        return age < LOCATION_FRESH_THRESHOLD;
    }
    
    /**
     * 转换为PhoneLocation对象
     */
    private PhoneLocation convertToPhoneLocation(Location location) {
        PhoneLocation phoneLoc = new PhoneLocation();
        phoneLoc.setLatitude(location.getLatitude());
        phoneLoc.setLongitude(location.getLongitude());
        phoneLoc.setAccuracy(location.getAccuracy());
        phoneLoc.setTimestamp(location.getTime());
        phoneLoc.setProvider(location.getProvider());
        
        return phoneLoc;
    }
}
