package com.RockiotTag.tag;

import java.util.HashMap;
import java.util.Map;

/**
 * API 配置类 - 多客户支持版
 */
public class ApiConfig {
    // 认证信息（保留以兼容旧代码）
    public static final String API_CID = "6h7lMJOVpVOld5R9CApqH6coCR1W8iqL";
    public static final String API_CUSTOMER_CODE = "XHD_HSWL_API";
    public static final String API_PASSWORD = "123456";
    
    // 多客户 API Key 配置
    public static final String CUSTOMER_HSWL = "hswl";
    public static final String CUSTOMER_DR = "dr";
    public static final String CUSTOMER_DEMO = "hswl_demo";
    public static final String CUSTOMER_HSWL_5GP02 = "hswl_5gp02";
    public static final String CUSTOMER_MEXBT = "mexbt";
    
    private static final Map<String, CustomerConfig> CUSTOMER_CONFIGS = new HashMap<>();
    
    static {
        CUSTOMER_CONFIGS.put(CUSTOMER_HSWL, new CustomerConfig(
            "HSWL_API",
            CUSTOMER_HSWL,
            "rtk_hswl_2f2993ce54ef11f1889100163e06688b"
        ));
        CUSTOMER_CONFIGS.put(CUSTOMER_DR, new CustomerConfig(
            "DR_API",
            CUSTOMER_DR,
            "rtk_dr_2f29354e54ef11f1889100163e06688b"
        ));
        CUSTOMER_CONFIGS.put(CUSTOMER_DEMO, new CustomerConfig(
            "DEMO_API",
            CUSTOMER_DEMO,
            "rtk_demo_2f28d39754ef11f1889100163e06688b"
        ));
        CUSTOMER_CONFIGS.put(CUSTOMER_HSWL_5GP02, new CustomerConfig(
            "HSWL_5GP02_API",
            CUSTOMER_HSWL_5GP02,
            "rtk_hswl_5gp02_7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b"
        ));
        CUSTOMER_CONFIGS.put(CUSTOMER_MEXBT, new CustomerConfig(
            "MEXBT_API",
            CUSTOMER_MEXBT,
            "rtk_mexbt_2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e"
        ));
    }
    
    // 默认客户（兼容旧代码）
    public static final String DEFAULT_CUSTOMER = CUSTOMER_HSWL;
    public static final String API_KEY = getApiKeyForCustomer(DEFAULT_CUSTOMER);
    
    // 服务器 URL（使用HTTPS协议，默认443端口）
    public static final String SERVER_URL_12BIT = "https://5gp.blackrockiot.com/api";
    public static final String SERVER_URL_16BIT = "https://5gp.blackrockiot.com/api";
    public static final String MY_SERVER_URL = SERVER_URL_16BIT;
    public static final String VENDOR_API_URL = "https://device.vernal.ltd/tagapi";
    
    // 谷歌地图 API Key
    public static final String GOOGLE_MAPS_API_KEY = "AIzaSyDrAPLhyAuC-GsRtc5m2eVXDhxkD_AZHUU";
    
    /**
     * 客户配置内部类
     */
    public static class CustomerConfig {
        public final String name;
        public final String customerCode;
        public final String apiKey;
        
        public CustomerConfig(String name, String customerCode, String apiKey) {
            this.name = name;
            this.customerCode = customerCode;
            this.apiKey = apiKey;
        }
    }
    
    /**
     * 根据客户代码获取 API Key
     * @param customerCode 客户代码 (hswl, dr, hswl_demo)
     * @return API Key
     */
    public static String getApiKeyForCustomer(String customerCode) {
        if (customerCode == null || customerCode.isEmpty()) {
            customerCode = DEFAULT_CUSTOMER;
        }
        CustomerConfig config = CUSTOMER_CONFIGS.get(customerCode);
        if (config != null) {
            return config.apiKey;
        }
        return CUSTOMER_CONFIGS.get(DEFAULT_CUSTOMER).apiKey;
    }
    
    /**
     * 根据客户代码获取客户配置
     */
    public static CustomerConfig getCustomerConfig(String customerCode) {
        if (customerCode == null || customerCode.isEmpty()) {
            customerCode = DEFAULT_CUSTOMER;
        }
        CustomerConfig config = CUSTOMER_CONFIGS.get(customerCode);
        return config != null ? config : CUSTOMER_CONFIGS.get(DEFAULT_CUSTOMER);
    }
    
    /**
     * 获取所有客户配置
     */
    public static Map<String, CustomerConfig> getAllCustomerConfigs() {
        return new HashMap<>(CUSTOMER_CONFIGS);
    }
    
    /**
     * 根据设备号长度获取对应的服务器URL
     */
    public static String getMyServerUrl(String deviceNum) {
        if (deviceNum == null || deviceNum.contains(":") || deviceNum.length() == 12) {
            return SERVER_URL_12BIT;
        }
        return SERVER_URL_16BIT;
    }
    
    // 兼容旧代码的 getter 方法
    public static String getCid() {
        return API_CID;
    }
    
    public static String getCustomerCode() {
        return API_CUSTOMER_CODE;
    }
    
    public static String getPassword() {
        return API_PASSWORD;
    }
    
    public static String getApiKey() {
        return API_KEY;
    }
    
    public static String getDefaultServerUrl() {
        return SERVER_URL_16BIT;
    }
}
