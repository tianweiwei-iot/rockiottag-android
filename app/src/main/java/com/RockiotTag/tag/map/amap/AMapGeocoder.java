package com.RockiotTag.tag.map.amap;

import android.content.Context;
import android.util.Log;

import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;

/**
 * 高德地理编码服务（国内版专用）
 * 完全独立的高德逆地理编码实现，不依赖任何谷歌地图代码
 */
public class AMapGeocoder {
    private static final String TAG = "AMapGeocoder";
    
    private Context context;
    private GeocodeSearch geocodeSearch;
    private GeocodeCallback callback;
    
    public interface GeocodeCallback {
        void onSuccess(String address);
        void onError(String error);
    }
    
    public AMapGeocoder(Context context) {
        this.context = context;
        try {
            geocodeSearch = new GeocodeSearch(context);
            Log.d(TAG, "AMapGeocoder initialized for domestic version");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize GeocodeSearch: " + e.getMessage(), e);
        }
    }
    
    /**
     * 设置地理编码回调
     */
    public void setGeocodeCallback(GeocodeCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 根据经纬度获取地址（同步）
     * @param latitude 纬度（WGS84坐标系）
     * @param longitude 经度（WGS84坐标系）
     * @return 地址字符串，失败返回null
     */
    public String getAddressFromLocationSync(final double latitude, final double longitude) {
        if (geocodeSearch == null) {
            Log.e(TAG, "GeocodeSearch is not initialized");
            return null;
        }
        
        Log.d(TAG, "Starting sync reverse geocoding for: lat=" + latitude + ", lng=" + longitude);
        
        try {
            // 使用 WGS84 坐标系（GPS原始坐标）
            LatLonPoint latLonPoint = new LatLonPoint(latitude, longitude);
            
            // 第三个参数 GeocodeSearch.GPS 表示输入的是 WGS84 坐标系
            RegeocodeQuery query = new RegeocodeQuery(latLonPoint, 200, GeocodeSearch.GPS);
            
            // 使用同步方法获取地址
            RegeocodeAddress regeocodeAddress = geocodeSearch.getFromLocation(query);
            
            if (regeocodeAddress != null) {
                String simpleAddress = buildSimpleAddress(regeocodeAddress);
                Log.d(TAG, "Sync reverse geocoding success: " + simpleAddress);
                return simpleAddress;
            } else {
                Log.e(TAG, "Sync reverse geocoding failed: result is null");
                return null;
            }
        } catch (com.amap.api.services.core.AMapException e) {
            Log.e(TAG, "AMapException during sync reverse geocoding: code=" + e.getErrorCode() 
                + ", message=" + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Exception during sync reverse geocoding: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 根据经纬度获取地址（异步）
     * @param latitude 纬度（WGS84坐标系）
     * @param longitude 经度（WGS84坐标系）
     */
    public void getAddressFromLocation(final double latitude, final double longitude) {
        if (geocodeSearch == null) {
            Log.e(TAG, "GeocodeSearch is not initialized");
            if (callback != null) {
                callback.onError("GeocodeSearch not initialized");
            }
            return;
        }
        
        Log.d(TAG, "Starting reverse geocoding for: lat=" + latitude + ", lng=" + longitude);
        
        // 在后台线程中执行逆地理编码
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 使用 WGS84 坐标系（GPS原始坐标）
                    LatLonPoint latLonPoint = new LatLonPoint(latitude, longitude);
                    
                    // 第三个参数 GeocodeSearch.GPS 表示输入的是 WGS84 坐标系
                    RegeocodeQuery query = new RegeocodeQuery(latLonPoint, 200, GeocodeSearch.GPS);
                    
                    // 使用同步方法获取地址
                    RegeocodeAddress regeocodeAddress = geocodeSearch.getFromLocation(query);
                    
                    if (regeocodeAddress != null) {
                        String formatAddress = regeocodeAddress.getFormatAddress();
                        Log.d(TAG, "Reverse geocoding success: " + formatAddress);
                        
                        // 构建简洁地址
                        String simpleAddress = buildSimpleAddress(regeocodeAddress);
                        
                        if (callback != null) {
                            callback.onSuccess(simpleAddress);
                        }
                    } else {
                        Log.e(TAG, "Reverse geocoding failed: result is null");
                        if (callback != null) {
                            callback.onError("No address found");
                        }
                    }
                } catch (com.amap.api.services.core.AMapException e) {
                    Log.e(TAG, "AMapException during reverse geocoding: code=" + e.getErrorCode() 
                        + ", message=" + e.getMessage(), e);
                    if (callback != null) {
                        callback.onError("AMapException: " + e.getErrorCode());
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
     * 构建简洁地址
     * 优化策略：优先使用 POI/AOI 等精确位置信息，避免不准确的路名/街道信息
     */
    private String buildSimpleAddress(RegeocodeAddress regeocodeAddress) {
        StringBuilder sb = new StringBuilder();
        
        String province = regeocodeAddress.getProvince();
        String city = regeocodeAddress.getCity();
        String district = regeocodeAddress.getDistrict();
        String township = regeocodeAddress.getTownship();
        
        String streetName = null;
        String streetNum = null;
        if (regeocodeAddress.getStreetNumber() != null) {
            streetName = regeocodeAddress.getStreetNumber().getStreet();
            streetNum = regeocodeAddress.getStreetNumber().getNumber();
        }
        
        String neighborhood = regeocodeAddress.getNeighborhood();
        
        String aoiName = null;
        if (regeocodeAddress.getAois() != null && !regeocodeAddress.getAois().isEmpty()) {
            aoiName = regeocodeAddress.getAois().get(0).getAoiName();
        }
        
        String poiName = null;
        java.util.List<com.amap.api.services.core.PoiItem> pois = regeocodeAddress.getPois();
        if (pois != null && !pois.isEmpty()) {
            poiName = pois.get(0).getTitle();
        }
        
        // === 优化后的地址构建策略 ===
        // 优先级：省市区 > POI/AOI/小区 > 街道 > 路名门牌
        // 这样可以让"甲岸科技园"这样的精确位置信息优先显示
        
        // 1. 基础行政区域（省+市+区）
        if (province != null && !province.isEmpty()) {
            sb.append(province);
        }
        if (city != null && !city.isEmpty() && !city.equals(province)) {
            sb.append(city);
        }
        if (district != null && !district.isEmpty()) {
            sb.append(district);
        }
        
        // 2. 优先添加精确位置信息（POI/AOI/小区）
        boolean hasPreciseLocation = false;
        if (poiName != null && !poiName.isEmpty()) {
            sb.append(poiName);
            hasPreciseLocation = true;
        } else if (aoiName != null && !aoiName.isEmpty()) {
            sb.append(aoiName);
            hasPreciseLocation = true;
        } else if (neighborhood != null && !neighborhood.isEmpty()) {
            sb.append(neighborhood);
            hasPreciseLocation = true;
        }
        
        // 3. 如果没有精确位置信息，再添加街道/路名信息
        if (!hasPreciseLocation) {
            if (township != null && !township.isEmpty()) {
                sb.append(township);
            }
            if (streetName != null && !streetName.isEmpty()) {
                sb.append(streetName);
            }
            if (streetNum != null && !streetNum.isEmpty()) {
                sb.append(streetNum);
            }
        }
        
        if (sb.length() > 0) {
            return sb.toString();
        } else {
            return regeocodeAddress.getFormatAddress();
        }
    }
    
    /**
     * 释放资源
     */
    public void onDestroy() {
        geocodeSearch = null;
    }
}
