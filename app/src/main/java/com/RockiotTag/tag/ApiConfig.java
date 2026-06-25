package com.RockiotTag.tag;

import com.RockiotTag.tag.BuildConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * API 配置类 - 多客户支持版
 * 敏感信息通过 BuildConfig 从 local.properties 注入，不再硬编码在源码中
 */
public class ApiConfig {
    // 认证信息（从 BuildConfig 读取）
    public static final String API_CID = BuildConfig.API_CID;
    public static final String API_CUSTOMER_CODE = "XHD_HSWL_API";
    public static final String API_PASSWORD = BuildConfig.API_PASSWORD;

    // 多客户 API Key 配置
    public static final String CUSTOMER_HSWL = "hswl";
    public static final String CUSTOMER_DR = "dr";
    public static final String CUSTOMER_DEMO = "hswl_demo";
    public static final String CUSTOMER_HSWL_5GP02 = "hswl_5gp02";
    public static final String CUSTOMER_MEXBT = "mexbt";
    public static final String CUSTOMER_GBRYE = "gbrye";
    public static final String CUSTOMER_ALF = "alf";

    private static final Map<String, CustomerConfig> CUSTOMER_CONFIGS = new HashMap<>();

    static {
        CUSTOMER_CONFIGS.put(CUSTOMER_HSWL, new CustomerConfig(
            "HSWL_API",
            CUSTOMER_HSWL,
            BuildConfig.CUSTOMER_HSWL_KEY
        ));
        CUSTOMER_CONFIGS.put(CUSTOMER_DR, new CustomerConfig(
            "DR_API",
            CUSTOMER_DR,
            BuildConfig.CUSTOMER_DR_KEY
        ));
        CUSTOMER_CONFIGS.put(CUSTOMER_DEMO, new CustomerConfig(
            "DEMO_API",
            CUSTOMER_DEMO,
            BuildConfig.CUSTOMER_DEMO_KEY
        ));
        CUSTOMER_CONFIGS.put(CUSTOMER_HSWL_5GP02, new CustomerConfig(
            "HSWL_5GP02_API",
            CUSTOMER_HSWL_5GP02,
            BuildConfig.CUSTOMER_HSWL_5GP02_KEY
        ));
        CUSTOMER_CONFIGS.put(CUSTOMER_MEXBT, new CustomerConfig(
            "MEXBT_API",
            CUSTOMER_MEXBT,
            BuildConfig.CUSTOMER_MEXBT_KEY
        ));
        CUSTOMER_CONFIGS.put(CUSTOMER_GBRYE, new CustomerConfig(
            "GBRYE_API",
            CUSTOMER_GBRYE,
            BuildConfig.CUSTOMER_GBRYE_KEY
        ));
        CUSTOMER_CONFIGS.put(CUSTOMER_ALF, new CustomerConfig(
            "ALF_API",
            CUSTOMER_ALF,
            BuildConfig.CUSTOMER_ALF_KEY
        ));
    }

    // 默认客户（兼容旧代码）
    public static final String DEFAULT_CUSTOMER = CUSTOMER_HSWL;
    public static final String API_KEY = getApiKeyForCustomer(DEFAULT_CUSTOMER);

    // 服务器 URL（从 BuildConfig 读取，不再硬编码）
    public static final String SERVER_URL_12BIT = BuildConfig.SERVER_URL_12BIT;
    public static final String SERVER_URL_16BIT = BuildConfig.SERVER_URL_16BIT;
    public static final String MY_SERVER_URL = SERVER_URL_16BIT;
    public static final String VENDOR_API_URL = BuildConfig.VENDOR_API_URL;

    // Google Maps 基础 URL
    public static final String GOOGLE_MAPS_BASE_URL = "https://maps.google.com";

    // 谷歌地图 API Key（从 BuildConfig 读取）
    public static final String GOOGLE_MAPS_API_KEY = BuildConfig.GOOGLE_MAPS_API_KEY;

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
