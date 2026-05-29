package com.RockiotTag.tag;

import android.util.Log;

import com.RockiotTag.tag.network.HttpHelper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewApiService {
    private static final String TAG = "NewApiService";
    private static String API_BASE_URL = ApiConfig.MY_SERVER_URL;
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;
    
    private static NewApiService instance;
    
    public static void setApiBaseUrl(String url) {
        API_BASE_URL = url;
        Log.d(TAG, "API Base URL set to: " + url);
    }
    
    private NewApiService() {
    }
    
    public static NewApiService getInstance() {
        if (instance == null) {
            instance = new NewApiService();
        }
        return instance;
    }
    
    /**
     * 检查是否已认证（API Key 模式下始终返回 true）
     */
    public boolean isAuthenticated() {
        // API Key 认证模式，始终认为已认证
        return true;
    }
    
    /**
     * 登录方法（API Key 模式下不需要登录，直接返回成功）
     */
    public ApiResponse login(String cid, String customerCode, String password) {
        Log.d(TAG, "Login called (API Key mode - no actual login needed)");
        ApiResponse response = new ApiResponse();
        response.setStatusCode(200);
        response.setStatus(200);
        response.setCode("0000");
        response.setMessage("Login successful (API Key authenticated)");
        return response;
    }
    
    public List<DeviceInfo> getDevices() {
        Log.d(TAG, "getDevices called");
        List<DeviceInfo> deviceInfoList = new ArrayList<>();
        
        // 使用公开API获取所有设备（包含MAC地址）
        ApiResponse response = getRequest("/devices", false);
        Log.d(TAG, "getDevices response - success: " + (response != null ? response.isSuccess() : "null"));
        
        if (response != null && response.isSuccess() && response.getRawResponse() != null) {
            Log.d(TAG, "Raw response: " + response.getRawResponse());
            try {
                Gson gson = new Gson();
                JsonParser parser = new JsonParser();
                var jsonElement = parser.parse(response.getRawResponse());
                
                if (jsonElement.isJsonArray()) {
                    var devicesArray = jsonElement.getAsJsonArray();
                    Log.d(TAG, "Parsing " + devicesArray.size() + " devices from array");
                    for (int i = 0; i < devicesArray.size(); i++) {
                        JsonObject deviceObj = devicesArray.get(i).getAsJsonObject();
                        DeviceInfo info = new DeviceInfo();
                        info.deviceNum = getStringValue(deviceObj, "deviceNum");
                        info.nickName = getStringValue(deviceObj, "nickName");
                        info.mac = getStringValue(deviceObj, "mac");
                        deviceInfoList.add(info);
                        Log.d(TAG, "  Device[" + i + "]: num=" + info.deviceNum + ", name=" + info.nickName + ", mac=" + info.mac);
                    }
                } else if (jsonElement.isJsonObject()) {
                    JsonObject jsonObject = jsonElement.getAsJsonObject();
                    if (jsonObject.has("id")) {
                        DeviceInfo info = new DeviceInfo();
                        info.deviceNum = getStringValue(jsonObject, "deviceNum");
                        info.nickName = getStringValue(jsonObject, "nickName");
                        info.mac = getStringValue(jsonObject, "mac");
                        deviceInfoList.add(info);
                        Log.d(TAG, "  Single device: num=" + info.deviceNum + ", name=" + info.nickName + ", mac=" + info.mac);
                    }
                }
                
                Log.d(TAG, "Parsed " + deviceInfoList.size() + " devices");
            } catch (Exception e) {
                Log.e(TAG, "Error parsing devices: " + e.getMessage(), e);
            }
        }
        
        return deviceInfoList;
    }
    
    public List<DeviceInfo> getBoundDeviceList() {
        return getDevices();
    }
    
    public ApiResponse getDeviceList(int pageNo, int pageSize) {
        Log.d(TAG, "getDeviceList called with pageNo: " + pageNo + ", pageSize: " + pageSize);
        
        ApiResponse response = getRequest("/devices", false);
        Log.d(TAG, "getDeviceList response - success: " + (response != null ? response.isSuccess() : "null"));
        
        return response;
    }
    
    public ApiResponse bindDevice(String deviceNum, String sn, String nickName) {
        return bindDevice(deviceNum, sn, nickName, null);
    }
    
    public ApiResponse bindDevice(String deviceNum, String sn, String nickName, String customerCode) {
        Log.d(TAG, "bindDevice called with deviceNum: " + deviceNum + ", sn: " + sn + ", nickName: " + nickName + ", customerCode: " + customerCode);
        
        Map<String, String> params = new HashMap<>();
        params.put("deviceNum", deviceNum);
        if (sn != null && !sn.isEmpty()) {
            params.put("sn", sn);
        }
        if (nickName != null && !nickName.isEmpty()) {
            params.put("nickName", nickName);
        }
        
        return postRequest("/devices/bind", params, false, customerCode);
    }
    
    public ApiResponse unbindDevice(String deviceNum) {
        return unbindDevice(deviceNum, null);
    }
    
    public ApiResponse unbindDevice(String deviceNum, String customerCode) {
        Log.d(TAG, "unbindDevice called for deviceNum: " + deviceNum + ", customerCode: " + customerCode);
        
        Map<String, String> params = new HashMap<>();
        params.put("deviceNum", deviceNum);
        
        return postRequest("/devices/unbind", params, false, customerCode);
    }
    
    public ApiResponse refreshLocation(String deviceNum) {
        return refreshLocation(deviceNum, null);
    }
    
    public ApiResponse refreshLocation(String deviceNum, String customerCode) {
        Log.d(TAG, "refreshLocation called for deviceNum: " + deviceNum + ", customerCode: " + customerCode);
        
        Map<String, String> params = new HashMap<>();
        params.put("deviceNum", deviceNum);
        
        return postRequest("/devices/refresh", params, false, customerCode);
    }
    
    public ApiResponse syncLocation(String deviceNum, double latitude, double longitude, int battery, long timestamp) {
        Log.d(TAG, "syncLocation called - deviceNum: " + deviceNum + ", lat: " + latitude + ", lng: " + longitude + ", battery: " + battery);
        
        double roundedLat = roundTo8Decimals(latitude);
        double roundedLng = roundTo8Decimals(longitude);
        Log.d(TAG, "Coordinates rounded: lat=" + latitude + " -> " + roundedLat + ", lng=" + longitude + " -> " + roundedLng);
        
        Map<String, Object> params = new HashMap<>();
        params.put("deviceNum", deviceNum);
        params.put("latitude", roundedLat);
        params.put("longitude", roundedLng);
        params.put("battery", battery);
        params.put("timestamp", timestamp);
        
        return postRequestWithObject("/locations/sync", params, false);
    }
    
    private double roundTo8Decimals(double value) {
        return Math.round(value * 100000000.0) / 100000000.0;
    }
    
    public List<LocationInfo> getLocations(String deviceNum, long startTime, long endTime) {
        return getLocations(deviceNum, startTime, endTime, null);
    }
    
    public List<LocationInfo> getLocations(String deviceNum, long startTime, long endTime, String customerCode) {
        Log.d(TAG, "=== getLocations START ===");
        Log.d(TAG, "deviceNum: " + deviceNum + ", startTime: " + startTime + ", endTime: " + endTime + ", customerCode: " + customerCode);
        List<LocationInfo> locationInfoList = new ArrayList<>();
        
        String endpoint = "/locations?deviceNum=" + deviceNum;
        if (startTime > 0 && endTime > 0) {
            endpoint += "&startTime=" + startTime + "&endTime=" + endTime;
        }
        Log.d(TAG, "Request endpoint: " + endpoint);
        
        ApiResponse response = getRequest(endpoint, false, customerCode);
        Log.d(TAG, "Response received - success: " + (response != null ? response.isSuccess() : "null"));
        if (response != null) {
            Log.d(TAG, "Response code: " + response.getStatusCode());
            Log.d(TAG, "Raw response length: " + (response.getRawResponse() != null ? response.getRawResponse().length() : "null"));
        }
        
        if (response != null && response.isSuccess() && response.getRawResponse() != null) {
            try {
                Gson gson = new Gson();
                JsonParser parser = new JsonParser();
                var jsonElement = parser.parse(response.getRawResponse());
                
                if (jsonElement.isJsonArray()) {
                    var locationsArray = jsonElement.getAsJsonArray();
                    Log.d(TAG, "Response is JSON array with " + locationsArray.size() + " elements");
                    for (int i = 0; i < locationsArray.size(); i++) {
                        JsonObject locationObj = locationsArray.get(i).getAsJsonObject();
                        LocationInfo info = new LocationInfo();
                        info.deviceNum = getStringValue(locationObj, "deviceNum");
                        info.latitude = getDoubleValue(locationObj, "latitude", 0);
                        info.longitude = getDoubleValue(locationObj, "longitude", 0);
                        info.battery = getIntValue(locationObj, "battery", 0);
                        info.timestamp = getLongValue(locationObj, "timestamp", 0);
                        locationInfoList.add(info);
                        if (i < 3 || i >= locationsArray.size() - 3) {
                            Log.d(TAG, "Location[" + i + "]: lat=" + info.latitude + ", lng=" + info.longitude + ", ts=" + info.timestamp);
                        }
                    }
                } else if (jsonElement.isJsonObject()) {
                    Log.d(TAG, "Response is JSON object");
                    JsonObject jsonObject = jsonElement.getAsJsonObject();
                    if (jsonObject.has("id")) {
                        LocationInfo info = new LocationInfo();
                        info.deviceNum = getStringValue(jsonObject, "deviceNum");
                        info.latitude = getDoubleValue(jsonObject, "latitude", 0);
                        info.longitude = getDoubleValue(jsonObject, "longitude", 0);
                        info.battery = getIntValue(jsonObject, "battery", 0);
                        info.timestamp = getLongValue(jsonObject, "timestamp", 0);
                        locationInfoList.add(info);
                        Log.d(TAG, "Single location: lat=" + info.latitude + ", lng=" + info.longitude + ", ts=" + info.timestamp);
                    }
                }
                
                Log.d(TAG, "=== getLocations END: Parsed " + locationInfoList.size() + " locations ===");
            } catch (Exception e) {
                Log.e(TAG, "Error parsing locations: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "Failed to get locations: response=" + response + ", success=" + (response != null ? response.isSuccess() : "null"));
        }
        
        return locationInfoList;
    }
    
    public DeviceInfo getDeviceInfo(String deviceNum) {
        Log.d(TAG, "getDeviceInfo called for deviceNum: " + deviceNum);
        
        List<DeviceInfo> devices = getDevices();
        for (DeviceInfo info : devices) {
            if (deviceNum.equals(info.deviceNum)) {
                Log.d(TAG, "Found device: " + deviceNum + ", trying to get locations...");
                try {
                    List<LocationInfo> locations = getLocations(deviceNum, 0, 0);
                    Log.d(TAG, "Got " + (locations != null ? locations.size() : 0) + " locations");
                    if (locations != null && !locations.isEmpty()) {
                        LocationInfo latestLocation = locations.get(0);
                        info.latitude = latestLocation.latitude;
                        info.longitude = latestLocation.longitude;
                        info.battery = latestLocation.battery;
                        info.timestamp = latestLocation.timestamp;
                        Log.d(TAG, "Set location info: lat=" + info.latitude + ", lng=" + info.longitude + ", battery=" + info.battery);
                    } else {
                        Log.d(TAG, "No locations found for device: " + deviceNum);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error getting locations: " + e.getMessage(), e);
                }
                return info;
            }
        }
        
        Log.e(TAG, "Device not found: " + deviceNum);
        return null;
    }
    
    public ApiResponse syncAll() {
        Log.d(TAG, "syncAll called");
        ApiResponse response = postRequest("/sync/all", new HashMap<>(), false);
        Log.d(TAG, "syncAll response - success: " + (response != null ? response.isSuccess() : "null"));
        return response;
    }
    
    public ApiResponse syncDevice(String deviceNum) {
        Log.d(TAG, "syncDevice called for deviceNum: " + deviceNum);
        ApiResponse response = postRequest("/sync/device/" + deviceNum, new HashMap<>(), false);
        Log.d(TAG, "syncDevice response - success: " + (response != null ? response.isSuccess() : "null"));
        return response;
    }
    
    public ApiResponse bindVendorDevice(String deviceNum, String nickName) {
        Log.d(TAG, "bindVendorDevice called for deviceNum: " + deviceNum + ", nickName: " + nickName);
        
        Map<String, String> params = new HashMap<>();
        params.put("deviceNum", deviceNum);
        if (nickName != null && !nickName.isEmpty()) {
            params.put("nickName", nickName);
        }
        
        ApiResponse response = postRequest("/sync/bindVendorDevice", params, false);
        Log.d(TAG, "bindVendorDevice response - success: " + (response != null ? response.isSuccess() : "null"));
        return response;
    }
    
    public ApiResponse updateDevice(String deviceNum, String nickName) {
        Log.d(TAG, "updateDevice called for deviceNum: " + deviceNum + ", nickName: " + nickName);
        
        Map<String, String> params = new HashMap<>();
        params.put("deviceNum", deviceNum);
        if (nickName != null && !nickName.isEmpty()) {
            params.put("nickName", nickName);
        }
        
        // 调用正确的更新端点
        ApiResponse response = postRequest("/devices/update", params, false);
        Log.d(TAG, "updateDevice response - success: " + (response != null ? response.isSuccess() : "null"));
        return response;
    }
    
    public DeviceInfo getDeviceLatest(String deviceNum) {
        return getDeviceLatest(deviceNum, null);
    }
    
    public DeviceInfo getDeviceLatest(String deviceNum, String customerCode) {
        if (deviceNum != null) {
            deviceNum = deviceNum.trim().replaceAll("\\s+", "").toUpperCase();
        }
        
        Log.d(TAG, "getDeviceLatest called for deviceNum: [" + deviceNum + "], customerCode: " + customerCode);
        Log.d(TAG, "Using API Key: " + ApiConfig.getApiKeyForCustomer(customerCode));
        
        ApiResponse response = getRequest("/devices/" + deviceNum + "/latest", false, customerCode);
        Log.d(TAG, "getDeviceLatest response - statusCode: " + (response != null ? response.getStatusCode() : "null response"));
        Log.d(TAG, "getDeviceLatest response - success: " + (response != null ? response.isSuccess() : "null"));
        
        if (response != null) {
            Log.d(TAG, "=== Raw Response ===");
            Log.d(TAG, response.getRawResponse() != null ? response.getRawResponse() : "null body");
            Log.d(TAG, "=== End Raw Response ===");
            
            if (response.getRawResponse() != null) {
                try {
                    Gson gson = new Gson();
                    JsonParser parser = new JsonParser();
                    var jsonElement = parser.parse(response.getRawResponse());
                    
                    if (jsonElement.isJsonObject()) {
                        JsonObject json = jsonElement.getAsJsonObject();
                        
                        if (json.has("success") && !json.get("success").getAsBoolean()) {
                            String message = json.has("message") ? json.get("message").getAsString() : "Unknown error";
                            Log.e(TAG, "Device not found or not activated: " + message);
                            return null;
                        }
                        
                        DeviceInfo info = new DeviceInfo();
                        info.deviceNum = getStringValue(json, "deviceNum");
                        info.nickName = getStringValue(json, "nickName");
                        info.mac = getStringValue(json, "mac");
                        info.latitude = getDoubleValue(json, "latitude", 0);
                        info.longitude = getDoubleValue(json, "longitude", 0);
                        info.battery = getIntValue(json, "battery", 0);
                        info.timestamp = getLongValue(json, "timestamp", 0);
                        info.address = getStringValue(json, "address");
                        info.updatedAt = getStringValue(json, "updatedAt");
                        
                        Log.d(TAG, "Got device latest info: deviceNum=" + info.deviceNum + ", lat=" + info.latitude + ", lng=" + info.longitude + 
                              ", battery=" + info.battery + ", mac=" + info.mac + ", address=" + info.address);
                        
                        if (info.deviceNum != null && !info.deviceNum.isEmpty()) {
                            return info;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing device latest info: " + e.getMessage(), e);
                }
            }
        }
        
        return null;
    }
    
    public static class DeviceInfo {
        public String deviceNum;
        public String nickName;
        public String mac;
        public double latitude;
        public double longitude;
        public int battery;
        public long timestamp;
        public String address;
        public String updatedAt;
    }
    
    public static class LocationInfo {
        public String deviceNum;
        public double latitude;
        public double longitude;
        public int battery;
        public long timestamp;
    }
    
    private ApiResponse postRequest(String endpoint, Map<String, String> params, boolean requireAuth) {
        return postRequest(endpoint, params, requireAuth, null);
    }
    
    private ApiResponse postRequest(String endpoint, Map<String, String> params, boolean requireAuth, String customerCode) {
        try {
            String url = API_BASE_URL + endpoint;
            String jsonParams = buildJson(params);
            Log.d(TAG, "Request: " + jsonParams + ", customerCode: " + customerCode);
            
            HttpHelper.HttpResponse response = HttpHelper.post(url, jsonParams, customerCode);
            
            Log.d(TAG, "Response code: " + response.statusCode);
            if (response.isSuccess()) {
                Log.d(TAG, "Response: " + response.body);
            } else {
                Log.e(TAG, "Error response: " + (response.error != null ? response.error : response.body));
            }
            
            return parseResponse(response.body, response.statusCode);
        } catch (Exception e) {
            Log.e(TAG, "Error making API request: " + e.getMessage(), e);
            return parseResponse("Internal Error: " + e.getMessage(), -1);
        }
    }
    
    private ApiResponse postRequestWithObject(String endpoint, Map<String, Object> params, boolean requireAuth) {
        return postRequestWithObject(endpoint, params, requireAuth, null);
    }
    
    private ApiResponse postRequestWithObject(String endpoint, Map<String, Object> params, boolean requireAuth, String customerCode) {
        try {
            String url = API_BASE_URL + endpoint;
            String jsonParams = buildJsonFromObject(params);
            Log.d(TAG, "Request: " + jsonParams + ", customerCode: " + customerCode);
            
            HttpHelper.HttpResponse response = HttpHelper.post(url, jsonParams, customerCode);
            
            Log.d(TAG, "Response code: " + response.statusCode);
            if (response.isSuccess()) {
                Log.d(TAG, "Response: " + response.body);
            } else {
                Log.e(TAG, "Error response: " + (response.error != null ? response.error : response.body));
            }
            
            return parseResponse(response.body, response.statusCode);
        } catch (Exception e) {
            Log.e(TAG, "Error making API request: " + e.getMessage(), e);
            return parseResponse("Internal Error: " + e.getMessage(), -1);
        }
    }
    
    private ApiResponse getRequest(String endpoint, boolean requireAuth) {
        return getRequest(endpoint, requireAuth, null);
    }
    
    private ApiResponse getRequest(String endpoint, boolean requireAuth, String customerCode) {
        try {
            String url = API_BASE_URL + endpoint;
            Log.d(TAG, "GET request: " + url + ", customerCode: " + customerCode);
            HttpHelper.HttpResponse response = HttpHelper.get(url, customerCode);
            
            Log.d(TAG, "Response code: " + response.statusCode);
            if (response.isSuccess()) {
                Log.d(TAG, "Response: " + response.body);
            } else {
                Log.e(TAG, "Error response: " + (response.error != null ? response.error : response.body));
            }
            
            return parseResponse(response.body, response.statusCode);
        } catch (Exception e) {
            Log.e(TAG, "Error making API request: " + e.getMessage(), e);
            return parseResponse("Internal Error: " + e.getMessage(), -1);
        }
    }
    
    private String buildJson(Map<String, String> params) {
        Gson gson = new Gson();
        return gson.toJson(params);
    }
    
    private String buildJsonFromObject(Map<String, Object> params) {
        Gson gson = new Gson();
        return gson.toJson(params);
    }
    
    private ApiResponse parseResponse(String responseString, int statusCode) {
        try {
            ApiResponse response = new ApiResponse();
            response.setStatusCode(statusCode);
            response.setRawResponse(responseString);
            
            if (responseString == null || responseString.isEmpty()) {
                return response;
            }
            
            Gson gson = new Gson();
            JsonParser parser = new JsonParser();
            var jsonElement = parser.parse(responseString);
            
            if (jsonElement.isJsonArray()) {
                return response;
            }
            
            if (!jsonElement.isJsonObject()) {
                return response;
            }
            
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            
            if (jsonObject.has("id")) {
                response.setId(jsonObject.get("id").getAsInt());
            }
            if (jsonObject.has("name")) {
                response.setName(jsonObject.get("name").getAsString());
            }
            if (jsonObject.has("cid")) {
                response.setCid(jsonObject.get("cid").getAsString());
            }
            if (jsonObject.has("token")) {
                response.setToken(jsonObject.get("token").getAsString());
            }
            if (jsonObject.has("deviceNum")) {
                response.setDeviceNum(jsonObject.get("deviceNum").getAsString());
            }
            if (jsonObject.has("nickName")) {
                response.setNickName(jsonObject.get("nickName").getAsString());
            }
            if (jsonObject.has("sn")) {
                response.setSn(jsonObject.get("sn").getAsString());
            }
            if (jsonObject.has("userId")) {
                response.setUserId(jsonObject.get("userId").getAsInt());
            }
            if (jsonObject.has("status")) {
                response.setStatus(jsonObject.get("status").getAsInt());
            }
            if (jsonObject.has("code")) {
                response.setCode(jsonObject.get("code").getAsString());
            }
            if (jsonObject.has("message")) {
                response.setMessage(jsonObject.get("message").getAsString());
            }
            if (jsonObject.has("items")) {
                response.setItems(jsonObject.get("items"));
            }
            
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing response: " + e.getMessage(), e);
            ApiResponse response = new ApiResponse();
            response.setStatusCode(statusCode);
            response.setRawResponse(responseString);
            return response;
        }
    }
    
    public static class ApiResponse {
        private int statusCode;
        private String rawResponse;
        private int id;
        private String name;
        private String cid;
        private String token;
        private String deviceNum;
        private String nickName;
        private String sn;
        private int userId;
        private int status;
        private String code;
        private String message;
        private Object items;
        
        public int getStatusCode() {
            return statusCode;
        }
        
        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }
        
        public String getRawResponse() {
            return rawResponse;
        }
        
        public void setRawResponse(String rawResponse) {
            this.rawResponse = rawResponse;
        }
        
        public int getId() {
            return id;
        }
        
        public void setId(int id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getCid() {
            return cid;
        }
        
        public void setCid(String cid) {
            this.cid = cid;
        }
        
        public String getToken() {
            return token;
        }
        
        public void setToken(String token) {
            this.token = token;
        }
        
        public String getDeviceNum() {
            return deviceNum;
        }
        
        public void setDeviceNum(String deviceNum) {
            this.deviceNum = deviceNum;
        }
        
        public String getNickName() {
            return nickName;
        }
        
        public void setNickName(String nickName) {
            this.nickName = nickName;
        }
        
        public String getSn() {
            return sn;
        }
        
        public void setSn(String sn) {
            this.sn = sn;
        }
        
        public int getUserId() {
            return userId;
        }
        
        public void setUserId(int userId) {
            this.userId = userId;
        }
        
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
            return statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_CREATED || (status == 200 && "0000".equals(code));
        }
    }
    
    private String getStringValue(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsString();
            } catch (Exception e) {
                Log.e(TAG, "Error getting string value for key: " + key, e);
                return "";
            }
        }
        return "";
    }
    
    private int getIntValue(JsonObject obj, String key, int defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsInt();
            } catch (Exception e) {
                Log.e(TAG, "Error getting int value for key: " + key, e);
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private long getLongValue(JsonObject obj, String key, long defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsLong();
            } catch (Exception e) {
                Log.e(TAG, "Error getting long value for key: " + key, e);
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private double getDoubleValue(JsonObject obj, String key, double defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsDouble();
            } catch (Exception e) {
                Log.e(TAG, "Error getting double value for key: " + key, e);
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
