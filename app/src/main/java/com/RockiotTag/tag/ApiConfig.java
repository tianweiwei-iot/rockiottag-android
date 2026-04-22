package com.RockiotTag.tag;

public class ApiConfig {
    public static final String API_CID = "6h7lMJOVpVOld5R9CApqH6coCR1W8iqL";
    public static final String API_CUSTOMER_CODE = "XHD_HSWL_API";
    public static final String API_PASSWORD = "123456";
    
    // 12位设备号使用的服务器URL
    public static final String SERVER_URL_12BIT = "http://8.217.22.251:8080/api";
    
    // 16位设备号使用的服务器URL
    public static final String SERVER_URL_16BIT = "http://8.217.22.251:8081/api";
    
    // 兼容旧代码的默认URL
    public static final String MY_SERVER_URL = SERVER_URL_16BIT;
    
    public static final String VENDOR_API_URL = "https://device.vernal.ltd/tagapi";
    
    // 根据设备号长度获取对应的服务器URL
    public static String getMyServerUrl(String deviceNum) {
        if (deviceNum == null) {
            return SERVER_URL_16BIT;
        }
        // MAC地址（包含冒号）或12位 → SpriteTagBackend (8080)
        if (deviceNum.contains(":") || deviceNum.length() == 12) {
            return SERVER_URL_12BIT;
        }
        // 16位 → RockiotTagBackend (8081)
        return SERVER_URL_16BIT;
    }
    
    public static String getCid() {
        return API_CID;
    }
    
    public static String getCustomerCode() {
        return API_CUSTOMER_CODE;
    }
    
    public static String getPassword() {
        return API_PASSWORD;
    }
    
    public static String getMyServerUrl() {
        return SERVER_URL_16BIT;
    }
    
    public static String getVendorApiUrl() {
        return VENDOR_API_URL;
    }
}
