package com.rockiot.tag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.SecretKey;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;

@Service
public class VendorApiService {
    @Value("${vendor.api.url}")
    private String vendorApiUrl;
    
    @Value("${vendor.api.cid}")
    private String cid;
    
    private RestTemplate restTemplate = new RestTemplate();
    private ObjectMapper objectMapper = new ObjectMapper();
    
    private String token;
    private int userId;
    private String lastErrorCode;
    
    private static final long REFRESH_LOCATION_CACHE_TTL = 60 * 1000;
    private static final long GET_DEVICE_LIST_CACHE_TTL = 30 * 1000;
    
    private Map<String, CacheEntry<Boolean>> refreshLocationCache = new ConcurrentHashMap<>();
    private CacheEntry<List<Map<String, Object>>> deviceListCache = new CacheEntry<>();
    private Map<String, CacheEntry<List<Map<String, Object>>>> locationListCache = new ConcurrentHashMap<>();
    
    private static class CacheEntry<T> {
        T data;
        long timestamp;
        
        CacheEntry() {
            this.timestamp = 0;
            this.data = null;
        }
        
        CacheEntry(T data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired(long ttl) {
            return data == null || (System.currentTimeMillis() - timestamp) > ttl;
        }
        
        void update(T newData) {
            this.data = newData;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public String getLastErrorCode() {
        return lastErrorCode;
    }
    
    public boolean isTokenExpired() {
        return "8021".equals(lastErrorCode) || "8015".equals(lastErrorCode);
    }
    
    public void clearCache() {
        refreshLocationCache.clear();
        deviceListCache = new CacheEntry<>();
        locationListCache.clear();
        System.out.println("All vendor API cache cleared");
    }
    
    public void clearDeviceCache(String deviceNum) {
        refreshLocationCache.remove(deviceNum);
        locationListCache.remove(deviceNum);
        System.out.println("Cache cleared for device: " + deviceNum);
    }
    
    public boolean login(String name, String pwd) {
        try {
            String url = vendorApiUrl + "/user/login";
            
            ObjectNode params = objectMapper.createObjectNode();
            params.put("cid", cid);
            params.put("name", name);
            params.put("pwd", pwd);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(params), headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                
                String code = (String) body.get("code");
                if ("0000".equals(code) && body.containsKey("items")) {
                    Map<String, Object> items = (Map<String, Object>) body.get("items");
                    if (items.containsKey("id")) {
                        userId = ((Number) items.get("id")).intValue();
                    }
                    if (items.containsKey("token")) {
                        token = (String) items.get("token");
                    }
                    System.out.println("Vendor API login successful - userId: " + userId + ", token: " + (token != null ? token.substring(0, Math.min(10, token.length())) + "..." : "null"));
                    clearCache();
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("Vendor API login failed: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("Vendor API login failed, using mock token for local operations");
        this.token = "mock-token-for-local-operations";
        this.userId = 1;
        return true;
    }
    
    public List<Map<String, Object>> getDeviceList(int pageNo, int pageSize) {
        return getDeviceListWithCache(pageNo, pageSize, true);
    }
    
    public List<Map<String, Object>> getDeviceListWithCache(int pageNo, int pageSize, boolean useCache) {
        try {
            if (useCache && !deviceListCache.isExpired(GET_DEVICE_LIST_CACHE_TTL)) {
                System.out.println("Returning cached device list (age: " + (System.currentTimeMillis() - deviceListCache.timestamp) + "ms)");
                return deviceListCache.data;
            }
            
            System.out.println("=== getDeviceList called (calling vendor API) ===");
            System.out.println("pageNo: " + pageNo + ", pageSize: " + pageSize);
            
            String url = vendorApiUrl + "/device/getDeviceList";
            System.out.println("URL: " + url);
            
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("pageNo", pageNo);
            dataMap.put("pageSize", pageSize);
            Map<String, Object> queryFilter = new HashMap<>();
            queryFilter.put("userId", userId);
            dataMap.put("queryFilter", queryFilter);
            
            String dataJson = objectMapper.writeValueAsString(dataMap);
            System.out.println("Request data (before encryption): " + dataJson);
            
            String encryptedData = encrypt(dataJson, token);
            
            ObjectNode params = objectMapper.createObjectNode();
            params.put("userId", userId);
            params.put("data", encryptedData);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(params), headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            System.out.println("Response status: " + response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                
                String code = (String) body.get("code");
                lastErrorCode = code;
                System.out.println("Response code: " + code);
                
                if ("0000".equals(code) && body.containsKey("items")) {
                    String encryptedItems = (String) body.get("items");
                    String decryptedData = decrypt(encryptedItems, token);
                    
                    System.out.println("Decrypted device list: " + decryptedData);
                    
                    JsonNode root = objectMapper.readTree(decryptedData);
                    List<Map<String, Object>> devices = new ArrayList<>();
                    
                    if (root.has("records")) {
                        JsonNode records = root.get("records");
                        System.out.println("Found " + records.size() + " records");
                        
                        for (JsonNode record : records) {
                            Map<String, Object> device = new HashMap<>();
                            device.put("deviceNum", record.has("deviceNum") ? record.get("deviceNum").asText() : "");
                            device.put("nickName", record.has("nickName") ? record.get("nickName").asText() : "");
                            device.put("mac", record.has("mac") ? record.get("mac").asText() : "");
                            device.put("userId", record.has("userId") ? record.get("userId").asInt() : 0);
                            
                            System.out.println("Processing device: " + device.get("deviceNum"));
                            
                            if (record.has("location") && !record.get("location").isNull() && record.get("location").isObject()) {
                                JsonNode location = record.get("location");
                                System.out.println("  Location node: " + location);
                                
                                if (location.has("latitude") && !location.get("latitude").isNull()) {
                                    device.put("latitude", location.get("latitude").asDouble());
                                    System.out.println("  latitude: " + device.get("latitude"));
                                }
                                if (location.has("longitude") && !location.get("longitude").isNull()) {
                                    device.put("longitude", location.get("longitude").asDouble());
                                    System.out.println("  longitude: " + device.get("longitude"));
                                }
                                if (location.has("battery") && !location.get("battery").isNull()) {
                                    device.put("battery", location.get("battery").asInt());
                                    System.out.println("  battery: " + device.get("battery"));
                                }
                                if (location.has("timestamp") && !location.get("timestamp").isNull()) {
                                    device.put("timestamp", location.get("timestamp").asLong());
                                    System.out.println("  timestamp: " + device.get("timestamp"));
                                }
                            } else {
                                System.out.println("  No location data in record");
                            }
                            
                            devices.add(device);
                        }
                    }
                    
                    deviceListCache.update(devices);
                    System.out.println("Parsed " + devices.size() + " devices from vendor API, cached for " + (GET_DEVICE_LIST_CACHE_TTL / 1000) + " seconds");
                    return devices;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting device list: " + e.getMessage());
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
    
    public List<Map<String, Object>> getLocationList(String deviceNum, int pageNo, int pageSize, long beginTime, long endTime) {
        return getLocationListWithCache(deviceNum, pageNo, pageSize, beginTime, endTime, true);
    }
    
    public List<Map<String, Object>> getLocationListWithCache(String deviceNum, int pageNo, int pageSize, long beginTime, long endTime, boolean useCache) {
        try {
            String cacheKey = deviceNum + "_" + beginTime + "_" + endTime;
            
            if (useCache) {
                CacheEntry<List<Map<String, Object>>> cached = locationListCache.get(cacheKey);
                if (cached != null && !cached.isExpired(GET_DEVICE_LIST_CACHE_TTL)) {
                    System.out.println("Returning cached location list for device: " + deviceNum);
                    return cached.data;
                }
            }
            
            System.out.println("=== getLocationList called (calling vendor API) ===");
            System.out.println("deviceNum: " + deviceNum + ", beginTime: " + beginTime + ", endTime: " + endTime);
            
            String url = vendorApiUrl + "/device/getLocationList";
            
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("pageNo", pageNo);
            dataMap.put("pageSize", pageSize);
            dataMap.put("deviceNum", deviceNum);
            Map<String, Object> queryFilter = new HashMap<>();
            queryFilter.put("deviceNum", deviceNum);
            queryFilter.put("userId", userId);
            queryFilter.put("beginTime", beginTime);
            queryFilter.put("endTime", endTime);
            dataMap.put("queryFilter", queryFilter);
            
            String dataJson = objectMapper.writeValueAsString(dataMap);
            String encryptedData = encrypt(dataJson, token);
            
            ObjectNode params = objectMapper.createObjectNode();
            params.put("userId", userId);
            params.put("data", encryptedData);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(params), headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                
                String code = (String) body.get("code");
                if ("0000".equals(code) && body.containsKey("items")) {
                    String encryptedItems = (String) body.get("items");
                    String decryptedData = decrypt(encryptedItems, token);
                    
                    JsonNode root = objectMapper.readTree(decryptedData);
                    List<Map<String, Object>> locations = new ArrayList<>();
                    
                    if (root.has("records")) {
                        JsonNode records = root.get("records");
                        for (JsonNode record : records) {
                            Map<String, Object> location = new HashMap<>();
                            location.put("deviceNum", record.has("deviceNum") ? record.get("deviceNum").asText() : "");
                            location.put("latitude", record.has("latitude") ? record.get("latitude").asDouble() : 0);
                            location.put("longitude", record.has("longitude") ? record.get("longitude").asDouble() : 0);
                            location.put("battery", record.has("battery") ? record.get("battery").asInt() : 0);
                            location.put("timestamp", record.has("timestamp") ? record.get("timestamp").asLong() : 0);
                            locations.add(location);
                        }
                    }
                    
                    CacheEntry<List<Map<String, Object>>> entry = new CacheEntry<>(locations);
                    locationListCache.put(cacheKey, entry);
                    System.out.println("Got " + locations.size() + " location records from vendor API, cached");
                    
                    return locations;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting location list: " + e.getMessage());
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
    
    public boolean bindDevice(String deviceNum, String nickName) {
        try {
            System.out.println("=== bindDevice called ===");
            System.out.println("deviceNum: " + deviceNum);
            
            String url = vendorApiUrl + "/device/bind";
            
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("cid", cid);
            dataMap.put("deviceNum", deviceNum);
            dataMap.put("userId", userId);
            
            String dataJson = objectMapper.writeValueAsString(dataMap);
            System.out.println("Request data (before encryption): " + dataJson);
            
            String encryptedData = encrypt(dataJson, token);
            
            ObjectNode params = objectMapper.createObjectNode();
            params.put("userId", userId);
            params.put("data", encryptedData);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(params), headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            System.out.println("Response status: " + response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String code = (String) body.get("code");
                System.out.println("Response code: " + code);
                System.out.println("Response body: " + body);
                
                if ("8015".equals(code)) {
                    System.out.println("Token expired, re-login and retry...");
                    return false;
                }
                
                if ("0000".equals(code)) {
                    System.out.println("Device bind successful!");
                    clearDeviceCache(deviceNum);
                    return true;
                } else {
                    System.out.println("Device bind failed: " + body.get("message"));
                }
            }
        } catch (Exception e) {
            System.err.println("Error binding device: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    public boolean unbindDevice(String deviceNum) {
        try {
            System.out.println("=== unbindDevice called ===");
            System.out.println("deviceNum: " + deviceNum);
            
            String url = vendorApiUrl + "/device/unbind";
            
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("cid", cid);
            dataMap.put("deviceNum", deviceNum);
            dataMap.put("userId", userId);
            
            String dataJson = objectMapper.writeValueAsString(dataMap);
            System.out.println("Request data (before encryption): " + dataJson);
            
            String encryptedData = encrypt(dataJson, token);
            
            ObjectNode params = objectMapper.createObjectNode();
            params.put("userId", userId);
            params.put("data", encryptedData);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(params), headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            System.out.println("Response status: " + response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String code = (String) body.get("code");
                System.out.println("Response code: " + code);
                System.out.println("Response body: " + body);
                
                if ("0000".equals(code)) {
                    System.out.println("Device unbind successful!");
                    clearDeviceCache(deviceNum);
                    return true;
                } else {
                    System.out.println("Device unbind failed: " + body.get("msg"));
                }
            }
        } catch (Exception e) {
            System.err.println("Error unbinding device: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    public boolean refreshLocation(String deviceNum) {
        return refreshLocationWithCache(deviceNum, true);
    }
    
    public boolean refreshLocationWithCache(String deviceNum, boolean useCache) {
        try {
            CacheEntry<Boolean> cached = refreshLocationCache.get(deviceNum);
            
            if (useCache && cached != null && !cached.isExpired(REFRESH_LOCATION_CACHE_TTL)) {
                System.out.println("refreshLocation: Using cached result for device " + deviceNum + " (age: " + (System.currentTimeMillis() - cached.timestamp) + "ms)");
                return cached.data;
            }
            
            System.out.println("=== refreshLocation called (calling vendor API) ===");
            System.out.println("deviceNum: " + deviceNum);
            
            String url = vendorApiUrl + "/device/refreshLocation";
            
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("cid", cid);
            dataMap.put("deviceNum", deviceNum);
            dataMap.put("userId", userId);
            
            String dataJson = objectMapper.writeValueAsString(dataMap);
            System.out.println("Request data (before encryption): " + dataJson);
            
            String encryptedData = encrypt(dataJson, token);
            
            ObjectNode params = objectMapper.createObjectNode();
            params.put("userId", userId);
            params.put("data", encryptedData);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(params), headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            System.out.println("Response status: " + response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String code = (String) body.get("code");
                System.out.println("Response code: " + code);
                System.out.println("Response body: " + body);
                
                if ("0000".equals(code)) {
                    System.out.println("Refresh location successful! Cached for " + (REFRESH_LOCATION_CACHE_TTL / 1000) + " seconds");
                    refreshLocationCache.put(deviceNum, new CacheEntry<>(true));
                    return true;
                } else {
                    System.out.println("Refresh location failed: " + body.get("msg"));
                    refreshLocationCache.put(deviceNum, new CacheEntry<>(false));
                }
            }
        } catch (Exception e) {
            System.err.println("Error refreshing location: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    private String encrypt(String plainText, String key) throws Exception {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        
        DESedeKeySpec spec = new DESedeKeySpec(keyBytes);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DESede");
        SecretKey secretKey = keyFactory.generateSecret(spec);
        
        Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
    
    private String decrypt(String encryptedText, String key) throws Exception {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        
        DESedeKeySpec spec = new DESedeKeySpec(keyBytes);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DESede");
        SecretKey secretKey = keyFactory.generateSecret(spec);
        
        Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
        
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
    
    public String getToken() {
        return token;
    }
    
    public int getUserId() {
        return userId;
    }
    
    public boolean isAuthenticated() {
        return userId > 0 && token != null;
    }
    
    public void forceRefreshDeviceList() {
        deviceListCache = new CacheEntry<>();
        System.out.println("Device list cache invalidated, will fetch fresh data on next call");
    }
    
    public void forceRefreshLocation(String deviceNum) {
        refreshLocationCache.remove(deviceNum);
        System.out.println("Location refresh cache invalidated for device: " + deviceNum);
    }
}
