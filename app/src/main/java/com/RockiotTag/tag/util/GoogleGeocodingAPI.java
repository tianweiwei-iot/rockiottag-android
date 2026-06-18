package com.RockiotTag.tag.util;

import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Google Geocoding API 服务
 * 用于在Google地图模式下获取准确的地址信息
 * 不受VPN影响，直接根据坐标返回地址
 */
public class GoogleGeocodingAPI {
    private static final String TAG = "GoogleGeocodingAPI";
    private static final String API_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    
    // Google Geocoding API Key（与地图使用相同的Key）
    // 需要在Google Cloud控制台启用"Geocoding API"
    private static final String API_KEY = "AIzaSyDrAPLhyAuC-GsRtc5m2eVXDhxkD_AZHUU";
    
    /**
     * 根据坐标获取地址
     * @param latitude 纬度
     * @param longitude 经度
     * @param languageCode 语言代码（如"zh-CN", "en", "ar"等）
     * @return 地址字符串，如果失败返回null
     */
    public static String getAddress(double latitude, double longitude, String languageCode) {
        try {
            // 构建请求URL
            String latLng = latitude + "," + longitude;
            String lang = languageCode != null ? languageCode : "en";
            
            StringBuilder urlBuilder = new StringBuilder(API_URL);
            urlBuilder.append("?latlng=").append(URLEncoder.encode(latLng, "UTF-8"));
            urlBuilder.append("&language=").append(URLEncoder.encode(lang, "UTF-8"));
            urlBuilder.append("&result_type=street_address|route|political");
            
            if (!API_KEY.isEmpty()) {
                urlBuilder.append("&key=").append(URLEncoder.encode(API_KEY, "UTF-8"));
            }
            
            String urlString = urlBuilder.toString();
            LogUtil.d(TAG, "Requesting Google Geocoding: " + urlString);
            
            // 发送HTTP请求
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 解析JSON响应
                JSONObject json = new JSONObject(response.toString());
                String status = json.getString("status");
                
                if ("OK".equals(status)) {
                    JSONArray results = json.getJSONArray("results");
                    if (results.length() > 0) {
                        JSONObject firstResult = results.getJSONObject(0);
                        String formattedAddress = firstResult.getString("formatted_address");
                        LogUtil.d(TAG, "Geocoding success: " + formattedAddress);
                        return formattedAddress;
                    }
                } else {
                    Log.w(TAG, "Geocoding API returned status: " + status);
                }
            } else {
                Log.e(TAG, "HTTP error: " + responseCode);
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Error calling Google Geocoding API: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * 验证返回的地址是否与坐标匹配
     * @param address 地址字符串
     * @param latitude 纬度
     * @param longitude 经度
     * @return true表示地址可信，false表示可能受VPN影响
     */
    public static boolean isAddressValid(String address, double latitude, double longitude) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        
        // 检查是否返回了明显的错误地址（如深圳）
        boolean containsShenzhen = address.contains("Shenzhen") || address.contains("深圳");
        boolean isNearShenzhen = Math.abs(latitude - 22.54) < 1 && Math.abs(longitude - 113.88) < 1;
        
        if (containsShenzhen && !isNearShenzhen) {
            Log.w(TAG, "Address contains Shenzhen but coordinates are not near Shenzhen");
            return false;
        }
        
        // 检查国家是否匹配
        boolean containsChina = address.contains("China") || address.contains("中国");
        boolean isNearChina = longitude > 73 && longitude < 135 && latitude > 3 && latitude < 54;
        
        if (containsChina && !isNearChina) {
            Log.w(TAG, "Address contains China but coordinates are not in China");
            return false;
        }
        
        return true;
    }
}
