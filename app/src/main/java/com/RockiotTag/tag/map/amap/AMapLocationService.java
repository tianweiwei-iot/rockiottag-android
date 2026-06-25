package com.RockiotTag.tag.map.amap;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

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

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AMapLocationClient locationClient;
    private LocationCallback callback;
    private AMapLocationListener locationListener;

    public interface LocationCallback {
        void onLocationSuccess(double latitude, double longitude, float accuracy, String address);
        void onLocationFailed(int errorCode, String errorInfo);
    }

    public AMapLocationService(Context context) {
        this.context = context.getApplicationContext();
        LogUtil.d(TAG, "AMapLocationService initialized for domestic version");
    }

    public void setLocationCallback(LocationCallback callback) {
        this.callback = callback;
    }

    /**
     * 初始化并启动定位
     */
    public void startLocation() {
        stopLocation();
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted, skip startLocation");
            if (callback != null) {
                callback.onLocationFailed(-1, "Missing location permissions");
            }
            return;
        }
        try {
            LogUtil.d(TAG, "Starting AMap location service...");
            locationClient = AmapLocationClientHolder.obtain(context);

            AMapLocationClientOption option = new AMapLocationClientOption();
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            option.setInterval(5000);
            option.setNeedAddress(true);
            option.setOnceLocation(false);

            locationClient.setLocationOption(option);

            locationListener = new AMapLocationListener() {
                @Override
                public void onLocationChanged(AMapLocation aMapLocation) {
                    if (aMapLocation == null) {
                        Log.e(TAG, "Location changed but location is null");
                        return;
                    }
                    if (aMapLocation.getErrorCode() == 0) {
                        double latitude = aMapLocation.getLatitude();
                        double longitude = aMapLocation.getLongitude();
                        float accuracy = aMapLocation.getAccuracy();
                        String address = aMapLocation.getAddress();
                        LogUtil.d(TAG, "Location success: lat=" + latitude + ", lng=" + longitude
                                + ", accuracy=" + accuracy + "m");
                        LocationCallback cb = callback;
                        if (cb != null) {
                            mainHandler.post(() -> {
                                LocationCallback active = callback;
                                if (active != null) {
                                    active.onLocationSuccess(latitude, longitude, accuracy, address);
                                }
                            });
                        }
                    } else {
                        Log.e(TAG, "Location failed: code=" + aMapLocation.getErrorCode()
                                + ", info=" + aMapLocation.getErrorInfo());
                        int errorCode = aMapLocation.getErrorCode();
                        String errorInfo = aMapLocation.getErrorInfo();
                        LocationCallback cb = callback;
                        if (cb != null) {
                            mainHandler.post(() -> {
                                LocationCallback active = callback;
                                if (active != null) {
                                    active.onLocationFailed(errorCode, errorInfo);
                                }
                            });
                        }
                    }
                }
            };
            locationClient.setLocationListener(locationListener);
            locationClient.startLocation();
            LogUtil.d(TAG, "AMap location service started");
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception - missing location permissions", e);
            if (callback != null) {
                callback.onLocationFailed(-1, "Missing location permissions");
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error starting location service: " + e.getMessage(), e);
            stopLocation();
            if (callback != null) {
                callback.onLocationFailed(-1, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }
    }

    public void stopLocation() {
        if (locationClient != null) {
            try {
                if (locationListener != null) {
                    locationClient.setLocationListener(null);
                }
                locationClient.stopLocation();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping location client: " + e.getMessage());
            }
            locationClient = null;
            locationListener = null;
            LogUtil.d(TAG, "AMap location service stopped");
        }
    }

    public void requestSingleLocation() {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (callback != null) {
                callback.onLocationFailed(-1, "Missing location permissions");
            }
            return;
        }
        try {
            if (locationClient == null) {
                locationClient = AmapLocationClientHolder.obtain(context);
                AMapLocationClientOption option = new AMapLocationClientOption();
                option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                option.setOnceLocation(true);
                option.setNeedAddress(true);
                locationClient.setLocationOption(option);
                locationClient.setLocationListener(aMapLocation -> {
                    if (aMapLocation != null && aMapLocation.getErrorCode() == 0) {
                        double latitude = aMapLocation.getLatitude();
                        double longitude = aMapLocation.getLongitude();
                        float accuracy = aMapLocation.getAccuracy();
                        String address = aMapLocation.getAddress();
                        mainHandler.post(() -> {
                            LocationCallback active = callback;
                            if (active != null) {
                                active.onLocationSuccess(latitude, longitude, accuracy, address);
                            }
                        });
                    } else {
                        int errorCode = aMapLocation != null ? aMapLocation.getErrorCode() : -1;
                        String errorInfo = aMapLocation != null ? aMapLocation.getErrorInfo() : "Unknown error";
                        mainHandler.post(() -> {
                            LocationCallback active = callback;
                            if (active != null) {
                                active.onLocationFailed(errorCode, errorInfo);
                            }
                        });
                    }
                });
            }
            locationClient.startLocation();
            LogUtil.d(TAG, "Single location request sent");
        } catch (Exception e) {
            Log.e(TAG, "Error requesting single location: " + e.getMessage(), e);
        }
    }

    public void onDestroy() {
        callback = null;
        mainHandler.removeCallbacksAndMessages(null);
        stopLocation();
    }
}
