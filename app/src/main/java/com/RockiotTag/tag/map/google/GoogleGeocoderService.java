package com.RockiotTag.tag.map.google;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 谷歌地理编码服务（国际版专用）
 * 使用Android内置Geocoder，支持多语言
 * 完全独立的实现，不依赖任何高德地图代码
 */
public class GoogleGeocoderService {
    private static final String TAG = "GoogleGeocoderService";
    
    private Context context;
    private GeocodeCallback callback;
    
    public interface GeocodeCallback {
        void onSuccess(String address);
        void onError(String error);
    }
    
    public GoogleGeocoderService(Context context) {
        this.context = context;
        Log.d(TAG, "GoogleGeocoderService initialized for international version");
    }
    
    /**
     * 设置地理编码回调
     */
    public void setGeocodeCallback(GeocodeCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 根据经纬度获取地址（异步）
     * @param latitude 纬度（WGS84坐标系）
     * @param longitude 经度（WGS84坐标系）
     * @param languageCode 语言代码（如：en, zh-CN, pt-BR等）
     */
    public void getAddressFromLocation(final double latitude, final double longitude, 
                                       final String languageCode) {
        Log.d(TAG, "Starting reverse geocoding for: lat=" + latitude + ", lng=" + longitude 
            + ", language=" + languageCode);
        
        // 在后台线程中执行逆地理编码
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 使用Android内置的Geocoder
                    Locale locale = parseLocale(languageCode);
                    Geocoder geocoder = new Geocoder(context, locale);
                    
                    List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                    
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        
                        // 构建完整地址
                        StringBuilder addressStr = new StringBuilder();
                        for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                            if (i > 0) {
                                addressStr.append(", ");
                            }
                            addressStr.append(address.getAddressLine(i));
                        }
                        
                        String finalAddress = addressStr.toString();
                        Log.d(TAG, "Reverse geocoding success: " + finalAddress);
                        
                        if (callback != null) {
                            callback.onSuccess(finalAddress);
                        }
                    } else {
                        Log.w(TAG, "No address found");
                        if (callback != null) {
                            callback.onError("No address found");
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "IOException during reverse geocoding: " + e.getMessage(), e);
                    if (callback != null) {
                        callback.onError("IOException: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception during reverse geocoding: " + e.getMessage(), e);
                    if (callback != null) {
                        callback.onError("Exception: " + e.getMessage());
                    }
                }
            }
        }).start();
    }
    
    /**
     * 根据经纬度获取地址（使用系统默认语言）
     */
    public void getAddressFromLocation(final double latitude, final double longitude) {
        getAddressFromLocation(latitude, longitude, Locale.getDefault().toLanguageTag());
    }
    
    /**
     * 解析语言代码为Locale对象
     */
    private Locale parseLocale(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return Locale.getDefault();
        }
        
        // 处理特殊语言代码
        switch (languageCode) {
            case "zh-CN":
                return Locale.SIMPLIFIED_CHINESE;
            case "zh-TW":
                return Locale.TRADITIONAL_CHINESE;
            case "en-US":
                return Locale.US;
            case "en-GB":
                return Locale.UK;
            case "pt-BR":
                return new Locale("pt", "BR");
            default:
                // 尝试解析标准语言代码
                String[] parts = languageCode.split("-");
                if (parts.length == 2) {
                    return new Locale(parts[0], parts[1]);
                } else if (parts.length == 1) {
                    return new Locale(parts[0]);
                }
                return Locale.getDefault();
        }
    }
    
    /**
     * 释放资源
     */
    public void onDestroy() {
        // Geocoder不需要特别清理
    }
}
