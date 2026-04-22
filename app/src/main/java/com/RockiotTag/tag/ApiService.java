package com.RockiotTag.tag;

import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class ApiService {
    private static final String TAG = "ApiService";
    private static String API_BASE_URL = ApiConfig.VENDOR_API_URL;
    private static final String CUSTOMER_CODE = ApiConfig.API_CUSTOMER_CODE;
    private static final String CID = ApiConfig.API_CID;
    
    private static ApiService instance;
    private String token;
    private int userId;
    
    private ApiService() {
    }
    
    public static ApiService getInstance() {
        if (instance == null) {
            instance = new ApiService();
        }
        return instance;
    }
    
    public static String getCustomerCode() {
        return CUSTOMER_CODE;
    }
    
    public static String getCid() {
        return CID;
    }
    
    public void setAuth(int userId, String token) {
        this.userId = userId;
        this.token = token;
    }
    
    public int getUserId() {
        return userId;
    }
    
    public String getToken() {
        return token;
    }
    
    public boolean isAuthenticated() {
        return userId > 0 && token != null;
    }
    
    // 用户注册
    public ApiResponse register(String cid, String name, String pwd) {
        Map<String, Object> params = new HashMap<>();
        params.put("cid", cid);
        params.put("name", name);
        params.put("pwd", pwd);
        
        return postRequest("/user/register", params);
    }
    
    // 用户登录
    public ApiResponse login(String cid, String name, String pwd) {
        Map<String, Object> params = new HashMap<>();
        params.put("cid", cid);
        params.put("name", name);
        params.put("pwd", pwd);
        
        ApiResponse response = postRequest("/user/login", params);
        if (response != null && response.isSuccess() && response.getItems() != null) {
            Log.d(TAG, "Starting to parse login response items");
            try {
                // 使用JsonParser直接解析items
                Gson gson = new Gson();
                String itemsJson = gson.toJson(response.getItems());
                Log.d(TAG, "Items JSON: " + itemsJson);
                
                JsonParser parser = new JsonParser();
                JsonObject itemsObject = parser.parse(itemsJson).getAsJsonObject();
                
                if (itemsObject.has("id")) {
                    userId = itemsObject.get("id").getAsInt();
                    Log.d(TAG, "Parsed userId: " + userId);
                }
                
                if (itemsObject.has("token")) {
                    token = itemsObject.get("token").getAsString();
                    Log.d(TAG, "Parsed token: " + token);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error parsing login response: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "Login response not successful or items is null");
        }
        return response;
    }
    
    // 获取设备列表
    public ApiResponse getDeviceList(int pageNo, int pageSize) {
        Log.d(TAG, "getDeviceList called with pageNo: " + pageNo + ", pageSize: " + pageSize);
        if (!isAuthenticated()) {
            Log.e(TAG, "Not authenticated");
            return null;
        }
        
        Log.d(TAG, "userId: " + userId + ", token: " + (token != null ? token.substring(0, Math.min(10, token.length())) + "..." : "null"));
        
        Map<String, Object> queryFilter = new HashMap<>();
        queryFilter.put("userId", userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("pageNo", pageNo);
        data.put("pageSize", pageSize);
        data.put("queryFilter", queryFilter);
        
        Log.d(TAG, "Data to encrypt: " + data);
        String encryptedData = encryptData(data, token);
        Log.d(TAG, "Encrypted data: " + (encryptedData != null ? encryptedData.substring(0, Math.min(20, encryptedData.length())) + "..." : "null"));
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("data", encryptedData);
        
        Log.d(TAG, "Params: " + params);
        return postRequest("/device/getDeviceList", params);
    }
    
    // 绑定设备
    public ApiResponse bindDevice(String deviceNum, String sn, String nickName) {
        if (!isAuthenticated()) {
            Log.e(TAG, "Not authenticated");
            return null;
        }
        
        Log.d(TAG, "bindDevice called with deviceNum: " + deviceNum + ", sn: " + sn + ", nickName: " + nickName);
        
        Map<String, Object> data = new HashMap<>();
        data.put("cid", CID);
        data.put("userId", userId);
        
        if (deviceNum != null && !deviceNum.isEmpty()) {
            data.put("deviceNum", deviceNum);
        }
        if (sn != null && !sn.isEmpty()) {
            data.put("sn", sn);
        }
        if (nickName != null && !nickName.isEmpty()) {
            data.put("nickName", nickName);
        }
        
        Log.d(TAG, "Data to encrypt for bind: " + data);
        String encryptedData = encryptData(data, token);
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("data", encryptedData);
        
        Log.d(TAG, "Bind params: " + params);
        return postRequest("/device/bind", params);
    }
    
    // 解绑设备
    public ApiResponse unbindDevice(String deviceNum) {
        if (!isAuthenticated()) {
            Log.e(TAG, "Not authenticated");
            return null;
        }
        
        Log.d(TAG, "unbindDevice called for deviceNum: " + deviceNum);
        
        Map<String, Object> data = new HashMap<>();
        data.put("cid", CID);
        data.put("deviceNum", deviceNum);
        data.put("userId", userId);
        
        String encryptedData = encryptData(data, token);
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("data", encryptedData);
        
        return postRequest("/device/unbind", params);
    }
    
    // 刷新设备位置
    public ApiResponse refreshLocation(String deviceNum) {
        if (!isAuthenticated()) {
            Log.e(TAG, "Not authenticated");
            return null;
        }
        
        Log.d(TAG, "refreshLocation called for deviceNum: " + deviceNum);
        
        Map<String, Object> data = new HashMap<>();
        data.put("cid", CID);
        data.put("deviceNum", deviceNum);
        data.put("userId", userId);
        
        String encryptedData = encryptData(data, token);
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("data", encryptedData);
        
        return postRequest("/device/refreshLocation", params);
    }
    
    // 获取设备位置列表
    public ApiResponse getLocationList(String deviceNum, int pageNo, int pageSize) {
        return getLocationList(deviceNum, pageNo, pageSize, 0, 0);
    }
    
    // 获取设备位置列表（带时间范围）
    public ApiResponse getLocationList(String deviceNum, int pageNo, int pageSize, long beginTime, long endTime) {
        if (!isAuthenticated()) {
            Log.e(TAG, "Not authenticated");
            return null;
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("deviceNum", deviceNum);
        data.put("pageNo", pageNo);
        data.put("pageSize", pageSize);
        data.put("userId", userId);
        
        if (beginTime > 0 && endTime > 0) {
            Map<String, Object> queryFilter = new HashMap<>();
            queryFilter.put("deviceNum", deviceNum);
            queryFilter.put("userId", userId);
            queryFilter.put("beginTime", beginTime);
            queryFilter.put("endTime", endTime);
            data.put("queryFilter", queryFilter);
        }
        
        String encryptedData = encryptData(data, token);
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("data", encryptedData);
        
        Log.d(TAG, "Calling getLocationList with deviceNum: " + deviceNum);
        return postRequest("/device/getLocationList", params);
    }
    
    // 获取设备信息（包含位置和电池）
    public DeviceInfo getDeviceInfo(String deviceNum) {
        Log.d(TAG, "getDeviceInfo called for deviceNum: " + deviceNum);
        if (!isAuthenticated()) {
            Log.e(TAG, "Not authenticated");
            return null;
        }
        
        Log.d(TAG, "First trying to refresh location...");
        try {
            ApiResponse refreshResponse = refreshLocation(deviceNum);
            Log.d(TAG, "Refresh location response - success: " + (refreshResponse != null ? refreshResponse.isSuccess() : "null") + 
                  ", message: " + (refreshResponse != null ? refreshResponse.getMessage() : "null"));
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing location, continuing anyway: " + e.getMessage());
        }
        
        Log.d(TAG, "Waiting for location to update...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Log.e(TAG, "Sleep interrupted: " + e.getMessage());
        }
        
        Log.d(TAG, "Calling getDeviceList...");
        ApiResponse response = getDeviceList(1, 100);
        Log.d(TAG, "getDeviceList response - success: " + (response != null ? response.isSuccess() : "null") + 
              ", status: " + (response != null ? response.getStatus() : "null") + 
              ", code: " + (response != null ? response.getCode() : "null") + 
              ", message: " + (response != null ? response.getMessage() : "null") +
              ", has items: " + (response != null && response.getItems() != null));
        
        if (response != null && response.isSuccess() && response.getItems() != null) {
            try {
                Log.d(TAG, "Starting to decrypt data...");
                String decryptedData = decryptData((String) response.getItems(), token);
                Log.d(TAG, "Decrypted device list: " + decryptedData);
                
                if (decryptedData == null) {
                    Log.e(TAG, "Decrypted data is null");
                    return null;
                }
                
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(decryptedData, JsonObject.class);
                
                Log.d(TAG, "Parsed JSON object: " + jsonObject);
                
                if (jsonObject.has("records")) {
                    var recordsArray = jsonObject.getAsJsonArray("records");
                    Log.d(TAG, "Found " + recordsArray.size() + " records");
                    
                    for (int i = 0; i < recordsArray.size(); i++) {
                        JsonObject record = recordsArray.get(i).getAsJsonObject();
                        Log.d(TAG, "Record " + i + ": " + record);
                        
                        if (record.has("deviceNum")) {
                            String recordDeviceNum = record.get("deviceNum").getAsString();
                            Log.d(TAG, "Record deviceNum: " + recordDeviceNum + ", looking for: " + deviceNum);
                            
                            if (deviceNum.equals(recordDeviceNum)) {
                                Log.d(TAG, "Found matching device!");
                                DeviceInfo info = new DeviceInfo();
                                info.deviceNum = deviceNum;
                                
                                if (record.has("nickName")) {
                                    info.nickName = record.get("nickName").getAsString();
                                    Log.d(TAG, "nickName: " + info.nickName);
                                }
                                
                                if (record.has("mac")) {
                                    info.mac = record.get("mac").getAsString();
                                    Log.d(TAG, "mac: " + info.mac);
                                }
                                
                                boolean hasValidLocation = false;
                                if (record.has("location") && !record.get("location").isJsonNull()) {
                                    JsonObject location = record.getAsJsonObject("location");
                                    Log.d(TAG, "Location object: " + location);
                                    
                                    if (location.has("latitude") && !location.get("latitude").isJsonNull()) {
                                        try {
                                            info.latitude = location.get("latitude").getAsDouble();
                                        } catch (Exception e) {
                                            try {
                                                String latitudeStr = location.get("latitude").getAsString();
                                                if (latitudeStr != null && !latitudeStr.isEmpty()) {
                                                    info.latitude = Double.parseDouble(latitudeStr);
                                                }
                                            } catch (Exception ex) {
                                                info.latitude = 0;
                                            }
                                        }
                                        Log.d(TAG, "latitude: " + info.latitude);
                                    }
                                    if (location.has("longitude") && !location.get("longitude").isJsonNull()) {
                                        try {
                                            info.longitude = location.get("longitude").getAsDouble();
                                        } catch (Exception e) {
                                            try {
                                                String longitudeStr = location.get("longitude").getAsString();
                                                if (longitudeStr != null && !longitudeStr.isEmpty()) {
                                                    info.longitude = Double.parseDouble(longitudeStr);
                                                }
                                            } catch (Exception ex) {
                                                info.longitude = 0;
                                            }
                                        }
                                        Log.d(TAG, "longitude: " + info.longitude);
                                    }
                                    if (location.has("battery") && !location.get("battery").isJsonNull()) {
                                        try {
                                            info.battery = location.get("battery").getAsInt();
                                        } catch (Exception e) {
                                            try {
                                                info.battery = Integer.parseInt(location.get("battery").getAsString());
                                            } catch (Exception ex) {
                                                info.battery = 0;
                                            }
                                        }
                                        Log.d(TAG, "battery: " + info.battery);
                                    }
                                    if (location.has("timestamp") && !location.get("timestamp").isJsonNull()) {
                                        try {
                                            info.timestamp = location.get("timestamp").getAsLong();
                                        } catch (Exception e) {
                                            try {
                                                info.timestamp = Long.parseLong(location.get("timestamp").getAsString());
                                            } catch (Exception ex) {
                                                info.timestamp = 0;
                                            }
                                        }
                                        Log.d(TAG, "timestamp: " + info.timestamp);
                                    }
                                    
                                    hasValidLocation = info.latitude != 0 && info.longitude != 0;
                                } else {
                                    Log.d(TAG, "No location data found in record");
                                }
                                
                                if (!hasValidLocation) {
                                    Log.d(TAG, "Trying to get location from getLocationList...");
                                    try {
                                        long endTime = System.currentTimeMillis();
                                        long beginTime = endTime - (30L * 24L * 60L * 60L * 1000L);
                                        
                                        ApiResponse locationListResponse = getLocationList(deviceNum, 1, 10, beginTime, endTime);
                                        Log.d(TAG, "getLocationList response - success: " + (locationListResponse != null ? locationListResponse.isSuccess() : "null"));
                                        
                                        if (locationListResponse != null && locationListResponse.isSuccess() && locationListResponse.getItems() != null) {
                                            String locationDecrypted = decryptData((String) locationListResponse.getItems(), token);
                                            Log.d(TAG, "Decrypted location list: " + locationDecrypted);
                                            
                                            if (locationDecrypted != null) {
                                                JsonObject locationListJson = gson.fromJson(locationDecrypted, JsonObject.class);
                                                
                                                if (locationListJson.has("records")) {
                                                    var locationRecords = locationListJson.getAsJsonArray("records");
                                                    Log.d(TAG, "Found " + locationRecords.size() + " location records");
                                                    
                                                    if (locationRecords.size() > 0) {
                                                        JsonObject latestLocation = locationRecords.get(0).getAsJsonObject();
                                                        Log.d(TAG, "Latest location record: " + latestLocation);
                                                        
                                                        if (latestLocation.has("latitude") && !latestLocation.get("latitude").isJsonNull()) {
                                                            try {
                                                                info.latitude = latestLocation.get("latitude").getAsDouble();
                                                            } catch (Exception e) {
                                                                try {
                                                                    info.latitude = Double.parseDouble(latestLocation.get("latitude").getAsString());
                                                                } catch (Exception ex) {
                                                                    info.latitude = 0;
                                                                }
                                                            }
                                                            Log.d(TAG, "latitude from history: " + info.latitude);
                                                        }
                                                        if (latestLocation.has("longitude") && !latestLocation.get("longitude").isJsonNull()) {
                                                            try {
                                                                info.longitude = latestLocation.get("longitude").getAsDouble();
                                                            } catch (Exception e) {
                                                                try {
                                                                    info.longitude = Double.parseDouble(latestLocation.get("longitude").getAsString());
                                                                } catch (Exception ex) {
                                                                    info.longitude = 0;
                                                                }
                                                            }
                                                            Log.d(TAG, "longitude from history: " + info.longitude);
                                                        }
                                                        if (latestLocation.has("battery") && !latestLocation.get("battery").isJsonNull()) {
                                                            try {
                                                                info.battery = latestLocation.get("battery").getAsInt();
                                                            } catch (Exception e) {
                                                                try {
                                                                    info.battery = Integer.parseInt(latestLocation.get("battery").getAsString());
                                                                } catch (Exception ex) {
                                                                    info.battery = 0;
                                                                }
                                                            }
                                                            Log.d(TAG, "battery from history: " + info.battery);
                                                        }
                                                        if (latestLocation.has("timestamp") && !latestLocation.get("timestamp").isJsonNull()) {
                                                            try {
                                                                info.timestamp = latestLocation.get("timestamp").getAsLong();
                                                            } catch (Exception e) {
                                                                try {
                                                                    info.timestamp = Long.parseLong(latestLocation.get("timestamp").getAsString());
                                                                } catch (Exception ex) {
                                                                    info.timestamp = 0;
                                                                }
                                                            }
                                                            Log.d(TAG, "timestamp from history: " + info.timestamp);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error getting location list: " + e.getMessage(), e);
                                    }
                                }
                                
                                return info;
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "No 'records' field in JSON");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing device info: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "getDeviceList failed or returned no items");
        }
        return null;
    }
    
    // 获取已绑定的设备列表（返回DeviceInfo列表）
    public List<DeviceInfo> getBoundDeviceList() {
        Log.d(TAG, "getBoundDeviceList called");
        List<DeviceInfo> deviceInfoList = new ArrayList<>();
        
        if (!isAuthenticated()) {
            Log.e(TAG, "Not authenticated");
            return deviceInfoList;
        }
        
        ApiResponse response = getDeviceList(1, 100);
        Log.d(TAG, "getDeviceList response - success: " + (response != null ? response.isSuccess() : "null"));
        
        if (response != null && response.isSuccess() && response.getItems() != null) {
            try {
                String decryptedData = decryptData((String) response.getItems(), token);
                Log.d(TAG, "Decrypted device list: " + decryptedData);
                
                if (decryptedData != null) {
                    Gson gson = new Gson();
                    JsonObject jsonObject = gson.fromJson(decryptedData, JsonObject.class);
                    
                    if (jsonObject.has("records")) {
                        var recordsArray = jsonObject.getAsJsonArray("records");
                        Log.d(TAG, "Found " + recordsArray.size() + " records");
                        
                        for (int i = 0; i < recordsArray.size(); i++) {
                            JsonObject record = recordsArray.get(i).getAsJsonObject();
                            DeviceInfo info = new DeviceInfo();
                            
                            if (record.has("deviceNum")) {
                                info.deviceNum = record.get("deviceNum").getAsString();
                            }
                            if (record.has("nickName")) {
                                info.nickName = record.get("nickName").getAsString();
                            }
                            if (record.has("mac")) {
                                info.mac = record.get("mac").getAsString();
                            }
                            
                            if (record.has("location") && !record.get("location").isJsonNull()) {
                                JsonObject location = record.getAsJsonObject("location");
                                if (location.has("latitude")) {
                                    try {
                                        info.latitude = location.get("latitude").getAsDouble();
                                    } catch (Exception e) {
                                        try {
                                            info.latitude = Double.parseDouble(location.get("latitude").getAsString());
                                        } catch (Exception ex) {
                                            info.latitude = 0;
                                        }
                                    }
                                }
                                if (location.has("longitude")) {
                                    try {
                                        info.longitude = location.get("longitude").getAsDouble();
                                    } catch (Exception e) {
                                        try {
                                            info.longitude = Double.parseDouble(location.get("longitude").getAsString());
                                        } catch (Exception ex) {
                                            info.longitude = 0;
                                        }
                                    }
                                }
                                if (location.has("battery")) {
                                    info.battery = location.get("battery").getAsInt();
                                }
                                if (location.has("timestamp")) {
                                    info.timestamp = location.get("timestamp").getAsLong();
                                }
                            }
                            
                            deviceInfoList.add(info);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing bound device list: " + e.getMessage(), e);
            }
        }
        
        return deviceInfoList;
    }
    
    // 设备信息类
    public static class DeviceInfo {
        public String deviceNum;
        public String nickName;
        public String mac;
        public double latitude;
        public double longitude;
        public int battery;
        public long timestamp;
    }
    
    // 发送POST请求
    private ApiResponse postRequest(String endpoint, Map<String, Object> params) {
        try {
            URL url = new URL(API_BASE_URL + endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            // 构建JSON参数
            String jsonParams = buildJson(params);
            Log.d(TAG, "Request: " + jsonParams);
            
            OutputStream os = conn.getOutputStream();
            os.write(jsonParams.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
            
            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                
                String responseString = response.toString();
                Log.d(TAG, "Response: " + responseString);
                
                // 解析响应
                return parseResponse(responseString);
            } else {
                Log.e(TAG, "Request failed with code: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error making API request: " + e.getMessage());
            return null;
        }
    }
    
    // 构建JSON字符串
    private String buildJson(Map<String, Object> params) {
        Gson gson = new Gson();
        return gson.toJson(params);
    }
    
    // 解析API响应
    private ApiResponse parseResponse(String responseString) {
        try {
            Gson gson = new Gson();
            ApiResponse response = gson.fromJson(responseString, ApiResponse.class);
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing response: " + e.getMessage());
            return null;
        }
    }
    
    // 加密数据 - 使用Triple DES (DESede)
    private String encryptData(Map<String, Object> data, String key) {
        try {
            Log.d(TAG, "encryptData called with key: " + (key != null ? key.substring(0, Math.min(10, key.length())) + "..." : "null"));
            
            // 构建数据字符串
            String dataString = buildJson(data);
            Log.d(TAG, "Data to encrypt: " + dataString);
            
            // 使用key作为密钥进行Triple DES加密
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            Log.d(TAG, "Key bytes length: " + keyBytes.length);
            
            // 创建DESede密钥
            javax.crypto.spec.DESedeKeySpec spec = new javax.crypto.spec.DESedeKeySpec(keyBytes);
            javax.crypto.SecretKeyFactory keyFactory = javax.crypto.SecretKeyFactory.getInstance("DESede");
            javax.crypto.SecretKey secretKey = keyFactory.generateSecret(spec);
            
            // 初始化Cipher为加密模式
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("DESede/ECB/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey);
            
            // 加密数据
            byte[] encryptedBytes = cipher.doFinal(dataString.getBytes(StandardCharsets.UTF_8));
            
            // 返回Base64编码的加密结果
            String result = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
            Log.d(TAG, "Encrypted result length: " + result.length());
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error encrypting data: " + e.getMessage(), e);
            return null;
        }
    }
    
    // 解密数据 - 使用Triple DES (DESede)
    public String decryptData(String encryptedData, String key) {
        try {
            Log.d(TAG, "decryptData called with encryptedData length: " + (encryptedData != null ? encryptedData.length() : "null") + 
                  ", key: " + (key != null ? key.substring(0, Math.min(10, key.length())) + "..." : "null"));
            
            // 使用key作为密钥进行Triple DES解密
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            Log.d(TAG, "Key bytes length: " + keyBytes.length);
            
            // 创建DESede密钥
            javax.crypto.spec.DESedeKeySpec spec = new javax.crypto.spec.DESedeKeySpec(keyBytes);
            javax.crypto.SecretKeyFactory keyFactory = javax.crypto.SecretKeyFactory.getInstance("DESede");
            javax.crypto.SecretKey secretKey = keyFactory.generateSecret(spec);
            
            // 初始化Cipher为解密模式
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("DESede/ECB/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey);
            
            // 解码Base64并解密数据
            byte[] encrypted = Base64.decode(encryptedData, Base64.NO_WRAP);
            Log.d(TAG, "Decoded encrypted bytes length: " + encrypted.length);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            String result = new String(decrypted, StandardCharsets.UTF_8);
            Log.d(TAG, "Decrypted result: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting data: " + e.getMessage(), e);
            return null;
        }
    }
    
    // API响应类
    public static class ApiResponse {
        private int status;
        private String code;
        private String message;
        private Object items;
        
        public int getStatus() {
            return status;
        }
        
        public void setStatus(int status) {
            this.status = status;
        }
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public Object getItems() {
            return items;
        }
        
        public void setItems(Object items) {
            this.items = items;
        }
        
        public boolean isSuccess() {
            return status == 200 && "0000".equals(code);
        }
    }
}