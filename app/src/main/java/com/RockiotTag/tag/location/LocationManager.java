package com.RockiotTag.tag.location;

import android.content.Context;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import com.RockiotTag.tag.map.amap.AMapLocationService;
import com.RockiotTag.tag.map.google.GoogleLocationService;

/**
 * 定位管理器 - 统一管理高德和谷歌地图的定位服务
 * 职责：
 * 1. 根据地图类型选择合适的定位服务
 * 2. 处理定位结果回调
 * 3. 管理定位服务的生命周期
 */
public class LocationManager {
    private static final String TAG = "LocationManager";
    
    public interface LocationCallback {
        void onLocationSuccess(double latitude, double longitude, float accuracy);
        void onLocationFailed(String error);
    }
    
    private final Context context;
    private AMapLocationService amapLocationService;
    private GoogleLocationService googleLocationService;
    private LocationCallback callback;
    private boolean isGoogleMapMode = false;
    
    public LocationManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 设置定位回调
     */
    public void setLocationCallback(LocationCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 设置地图模式
     * @param isGoogleMap true为谷歌地图模式，false为高德地图模式
     */
    public void setMapMode(boolean isGoogleMap) {
        this.isGoogleMapMode = isGoogleMap;
    }
    
    /**
     * 启动定位服务
     */
    public void startLocation() {
        try {
            if (isGoogleMapMode) {
                startGoogleLocation();
            } else {
                startAmapLocation();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: Missing location permissions", e);
            if (callback != null) {
                callback.onLocationFailed("缺少位置权限");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing location: " + e.getMessage(), e);
            if (callback != null) {
                callback.onLocationFailed("定位服务初始化失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 启动高德定位服务
     */
    private void startAmapLocation() {
        LogUtil.d(TAG, "Initializing AMap Location Service");
        
        // 清理旧的实例
        if (amapLocationService != null) {
            amapLocationService.onDestroy();
        }
        
        amapLocationService = new AMapLocationService(context);
        amapLocationService.setLocationCallback(new AMapLocationService.LocationCallback() {
            @Override
            public void onLocationSuccess(double latitude, double longitude, float accuracy, String address) {
                LogUtil.d(TAG, "AMap location success: lat=" + latitude + ", lng=" + longitude);
                if (callback != null) {
                    callback.onLocationSuccess(latitude, longitude, accuracy);
                }
            }
            
            @Override
            public void onLocationFailed(int errorCode, String errorInfo) {
                Log.e(TAG, "AMap location failed: code=" + errorCode + ", info=" + errorInfo);
                if (callback != null) {
                    callback.onLocationFailed("高德定位失败: " + errorInfo);
                }
            }
        });
        
        amapLocationService.startLocation();
    }
    
    /**
     * 启动谷歌定位服务
     */
    private void startGoogleLocation() {
        LogUtil.d(TAG, "Initializing Google Location Service");
        
        // 清理旧的实例
        if (googleLocationService != null) {
            googleLocationService.onDestroy();
        }
        
        googleLocationService = new GoogleLocationService(context);
        googleLocationService.setLocationCallback(new GoogleLocationService.ServiceCallback() {
            @Override
            public void onLocationSuccess(double latitude, double longitude, float accuracy) {
                LogUtil.d(TAG, "Google location success: lat=" + latitude + ", lng=" + longitude);
                if (callback != null) {
                    callback.onLocationSuccess(latitude, longitude, accuracy);
                }
            }
            
            @Override
            public void onLocationFailed(String error) {
                Log.e(TAG, "Google location failed: " + error);
                if (callback != null) {
                    callback.onLocationFailed("谷歌定位失败: " + error);
                }
            }
        });
        
        googleLocationService.startLocation();
    }
    
    /**
     * 停止定位服务
     */
    public void stopLocation() {
        if (amapLocationService != null) {
            amapLocationService.stopLocation();
        }
        if (googleLocationService != null) {
            googleLocationService.stopLocation();
        }
    }
    
    /**
     * 销毁定位服务
     */
    public void onDestroy() {
        stopLocation();
        
        if (amapLocationService != null) {
            amapLocationService.onDestroy();
            amapLocationService = null;
        }
        
        if (googleLocationService != null) {
            googleLocationService.onDestroy();
            googleLocationService = null;
        }
        
        callback = null;
    }
}
