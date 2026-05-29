package com.RockiotTag.tag.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地址缓存
 * 用于缓存逆地理编码结果，避免重复调用API
 * 线程安全优化：使用 synchronized 包装或 ConcurrentHashMap
 */
public class AddressCache {
    
    private static final int CACHE_SIZE = 50; // 最多缓存50个地址
    
    private final java.util.Map<String, String> cache = new java.util.concurrent.ConcurrentHashMap<>();
    
    public AddressCache() {
        // 初始化逻辑移至成员变量声明处
    }
    
    /**
     * 从缓存中获取地址
     * @param latitude 纬度
     * @param longitude 经度
     * @return 缓存的地址，如果不存在返回null
     */
    public String getAddress(double latitude, double longitude) {
        String key = generateKey(latitude, longitude);
        return cache.get(key);
    }
    
    /**
     * 缓存地址
     * @param latitude 纬度
     * @param longitude 经度
     * @param address 地址字符串
     */
    public void putAddress(double latitude, double longitude, String address) {
        if (address == null || address.isEmpty()) {
            return;
        }
        
        String key = generateKey(latitude, longitude);
        cache.put(key, address);
    }
    
    /**
     * 清除所有缓存
     */
    public void clear() {
        cache.clear();
    }
    
    /**
     * 生成缓存键
     */
    private String generateKey(double latitude, double longitude) {
        // 保留5位小数，约1米精度
        return String.format(java.util.Locale.US, "%.5f,%.5f", latitude, longitude);
    }
    
    /**
     * 获取缓存大小
     */
    public int getSize() {
        return cache.size();
    }
}
