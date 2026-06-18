package com.RockiotTag.tag.map.amap;

import android.content.Context;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;

/**
 * 高德定位服务（国内版专用）
 * 完全独立的高德定位实现，不依赖任何谷歌地图代码
 */
public class AMapLocationService {
    private static final String TAG = "AMapLocationService";
    
    private Context context;
    private AMapLocationClient locationClient;
    private LocationCallback callback;
    
    public interface LocationCallback {
        void onLocationSuccess(double latitude, double longitude, float accuracy, String address);
        void onLocationFailed(int errorCode, String errorInfo);
    }
    
    public AMapLocationService(Context context) {
        this.context = context;
        LogUtil.d(TAG, "AMapLocationService initialized for domestic version");
    }
    
    /**
     * 设置定位回调
     */
    public void setLocationCallback(LocationCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 初始化并启动定位
     */
    public void startLocation() {
        try {
            LogUtil.d(TAG, "Starting AMap location service...");
            
            locationClient = new AMapLocationClient(context.getApplicationContext());
            
            // 配置定位参数
            AMapLocationClientOption option = new AMapLocationClientOption();
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            option.setInterval(5000); // 5秒更新一次
            option.setNeedAddress(true); // 需要返回地址信息
            option.setOnceLocation(false); // 持续定位
            
            locationClient.setLocationOption(option);
            
            // 设置定位监听器
            locationClient.setLocationListener(new AMapLocationListener() {
                @Override
                public void onLocationChanged(AMapLocation aMapLocation) {
                    if (aMapLocation != null) {
                        if (aMapLocation.getErrorCode() == 0) {
                            // 定位成功
                            double latitude = aMapLocation.getLatitude();
                            double longitude = aMapLocation.getLongitude();
                            float accuracy = aMapLocation.getAccuracy();
                            String address = aMapLocation.getAddress();
                            
                            LogUtil.d(TAG, "Location success: lat=" + latitude + ", lng=" + longitude 
                                + ", accuracy=" + accuracy + "m");
                            
                            if (callback != null) {
                                callback.onLocationSuccess(latitude, longitude, accuracy, address);
                            }
                        } else {
                            // 定位失败
                            int errorCode = aMapLocation.getErrorCode();
                            String errorInfo = aMapLocation.getErrorInfo();
                            
                            Log.e(TAG, "Location failed: code=" + errorCode + ", info=" + errorInfo);
                            
                            if (callback != null) {
                                callback.onLocationFailed(errorCode, errorInfo);
                            }
                        }
                    } else {
                        Log.e(TAG, "Location changed but location is null");
                    }
                }
            });
            
            // 启动定位
            locationClient.startLocation();
            LogUtil.d(TAG, "AMap location service started");
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception - missing location permissions", e);
            if (callback != null) {
                callback.onLocationFailed(-1, "Missing location permissions");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting location service: " + e.getMessage(), e);
            if (callback != null) {
                callback.onLocationFailed(-1, e.getMessage());
            }
        }
    }
    
    /**
     * 停止定位
     */
    public void stopLocation() {
        if (locationClient != null) {
            locationClient.stopLocation();
            locationClient.onDestroy();
            locationClient = null;
            LogUtil.d(TAG, "AMap location service stopped");
        }
    }
    
    /**
     * 获取当前位置（单次定位）
     */
    public void requestSingleLocation() {
        try {
            if (locationClient == null) {
                locationClient = new AMapLocationClient(context.getApplicationContext());
                
                AMapLocationClientOption option = new AMapLocationClientOption();
                option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                option.setOnceLocation(true); // 单次定位
                option.setNeedAddress(true);
                
                locationClient.setLocationOption(option);
                locationClient.setLocationListener(new AMapLocationListener() {
                    @Override
                    public void onLocationChanged(AMapLocation aMapLocation) {
                        if (aMapLocation != null && aMapLocation.getErrorCode() == 0) {
                            if (callback != null) {
                                callback.onLocationSuccess(
                                    aMapLocation.getLatitude(),
                                    aMapLocation.getLongitude(),
                                    aMapLocation.getAccuracy(),
                                    aMapLocation.getAddress()
                                );
                            }
                        } else if (callback != null) {
                            callback.onLocationFailed(
                                aMapLocation != null ? aMapLocation.getErrorCode() : -1,
                                aMapLocation != null ? aMapLocation.getErrorInfo() : "Unknown error"
                            );
                        }
                    }
                });
            }
            
            locationClient.startLocation();
            LogUtil.d(TAG, "Single location request sent");
            
        } catch (Exception e) {
            Log.e(TAG, "Error requesting single location: " + e.getMessage(), e);
        }
    }
    
    /**
     * 释放资源
     */
    public void onDestroy() {
        stopLocation();
    }
}
