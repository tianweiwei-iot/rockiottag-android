package com.RockiotTag.tag;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Google Geocoding API 工具类
 * 用于将经纬度转换为具体地址（逆地理编码）
 */
public class GoogleGeocoder {
    private static final String TAG = "GoogleGeocoder";
    private static final String GEOCODING_API_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    
    public interface GeocodeCallback {
        void onSuccess(String address);
        void onError(String error);
    }
    
    /**
     * 根据经纬度获取地址（异步）
     * @param latitude 纬度（WGS84坐标系）
     * @param longitude 经度（WGS84坐标系）
     * @param callback 回调接口
     */
    public static void getAddressFromLocation(final double latitude, final double longitude, 
                                               final GeocodeCallback callback) {
        // 使用默认语言（中文）
        getAddressFromLocation(latitude, longitude, "zh-CN", callback);
    }
    
    /**
     * 根据经纬度获取地址（异步，指定语言）
     * @param latitude 纬度（WGS84坐标系）
     * @param longitude 经度（WGS84坐标系）
     * @param languageCode 语言代码（如：zh-CN, en, pt-BR等）
     * @param callback 回调接口
     */
    public static void getAddressFromLocation(final double latitude, final double longitude,
                                               final String languageCode,
                                               final GeocodeCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Starting Google Geocoding for: " + latitude + ", " + longitude + ", language: " + languageCode);
                    
                    // 构建请求URL
                    String latLng = latitude + "," + longitude;
                    String urlString = GEOCODING_API_URL + "?latlng=" + URLEncoder.encode(latLng, "UTF-8") 
                                     + "&language=" + languageCode
                                     + "&key=" + ApiConfig.GOOGLE_MAPS_API_KEY;
                    
                    Log.d(TAG, "Request URL: " + urlString);
                    
                    // 发送HTTP请求
                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    
                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Response code: " + responseCode);
                    
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        
                        String jsonResponse = response.toString();
                        Log.d(TAG, "Response: " + jsonResponse);
                        
                        // 解析JSON响应
                        JSONObject jsonObject = new JSONObject(jsonResponse);
                        String status = jsonObject.getString("status");
                        
                        if ("OK".equals(status)) {
                            JSONArray results = jsonObject.getJSONArray("results");
                            if (results.length() > 0) {
                                // 获取第一个结果的格式化地址
                                JSONObject firstResult = results.getJSONObject(0);
                                String formattedAddress = firstResult.getString("formatted_address");
                                
                                Log.d(TAG, "✅ Address found: " + formattedAddress);
                                
                                // 在主线程中调用回调
                                if (callback != null) {
                                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.onSuccess(formattedAddress);
                                        }
                                    });
                                }
                            } else {
                                Log.w(TAG, "No results found");
                                if (callback != null) {
                                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.onError("未找到地址信息");
                                        }
                                    });
                                }
                            }
                        } else {
                            Log.e(TAG, "Geocoding failed with status: " + status);
                            if (callback != null) {
                                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onError("地理编码失败: " + status);
                                    }
                                });
                            }
                        }
                    } else {
                        Log.e(TAG, "HTTP error: " + responseCode);
                        if (callback != null) {
                            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onError("网络请求失败: " + responseCode);
                                }
                            });
                        }
                    }
                    
                    connection.disconnect();
                    
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error during geocoding: " + e.getMessage(), e);
                    if (callback != null) {
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError("地理编码异常: " + e.getMessage());
                            }
                        });
                    }
                }
            }
        }).start();
    }
}
