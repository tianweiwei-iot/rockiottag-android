package com.RockiotTag.tag.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 地址解析助手类
 * 封装高德地图和 Google 地图的地址解析逻辑
 */
public class GeocodeHelper {
    
    private static final String TAG = "GeocodeHelper";
    
    /**
     * 高德地图逆地理编码回调接口
     */
    public interface OnGeocodeResultListener {
        void onGeocodeSuccess(String address);
        void onGeocodeFailed(String error);
    }
    
    /**
     * 使用高德地图进行逆地理编码
     * 
     * @param context 上下文
     * @param latitude 纬度（GCJ-02）
     * @param longitude 经度（GCJ-02）
     * @param listener 结果回调
     */
    public static void reverseGeocodeWithAMap(Context context, double latitude, double longitude, 
                                              OnGeocodeResultListener listener) {
        try {
            GeocodeSearch geocodeSearch = new GeocodeSearch(context);
            geocodeSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
                @Override
                public void onRegeocodeSearched(RegeocodeResult result, int rCode) {
                    if (rCode == 1000 && result != null && result.getRegeocodeAddress() != null) {
                        String address = result.getRegeocodeAddress().getFormatAddress();
                        if (address != null && !address.isEmpty()) {
                            listener.onGeocodeSuccess(address);
                        } else {
                            listener.onGeocodeFailed("地址为空");
                        }
                    } else {
                        listener.onGeocodeFailed("错误码: " + rCode);
                    }
                }
                
                @Override
                public void onGeocodeSearched(com.amap.api.services.geocoder.GeocodeResult geocodeResult, int i) {
                    // 不需要实现
                }
            });
            
            LatLonPoint latLonPoint = new LatLonPoint(latitude, longitude);
            RegeocodeQuery query = new RegeocodeQuery(latLonPoint, 200, GeocodeSearch.AMAP);
            geocodeSearch.getFromLocationAsyn(query);
            
        } catch (Exception e) {
            Log.e(TAG, "高德地图逆地理编码失败: " + e.getMessage(), e);
            listener.onGeocodeFailed(e.getMessage());
        }
    }
    
    /**
     * 使用 Android Geocoder 进行逆地理编码（适用于 Google 地图）
     * 
     * @param context 上下文
     * @param latitude 纬度（WGS-84）
     * @param longitude 经度（WGS-84）
     * @return 地址字符串，失败返回 null
     */
    public static String reverseGeocodeWithAndroidGeocoder(Context context, double latitude, double longitude) {
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder addressStr = new StringBuilder();
                
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    if (i > 0) addressStr.append(", ");
                    addressStr.append(address.getAddressLine(i));
                }
                
                return addressStr.toString();
            }
        } catch (IOException e) {
            Log.e(TAG, "Android Geocoder 失败: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Android Geocoder 异常: " + e.getMessage(), e);
        }
        
        return null;
    }
}
