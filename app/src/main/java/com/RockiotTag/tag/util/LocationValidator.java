package com.RockiotTag.tag.util;

/**
 * 位置数据验证工具类
 * 职责：统一验证经纬度坐标的有效性
 */
public class LocationValidator {
    
    // BLE Tag 最大可接受精度（米）
    private static final float MAX_ACCEPTABLE_ACCURACY = 200;
    
    // 中国地理范围（如果设备只在国内使用）
    private static final double CHINA_MIN_LAT = 18;
    private static final double CHINA_MAX_LAT = 54;
    private static final double CHINA_MIN_LNG = 73;
    private static final double CHINA_MAX_LNG = 135;
    
    /**
     * 验证坐标是否有效（基础检查）
     * @param latitude 纬度
     * @param longitude 经度
     * @return true 如果坐标有效
     */
    public static boolean isValidLocation(double latitude, double longitude) {
        return latitude != 0 && longitude != 0 &&
               latitude >= -90 && latitude <= 90 &&
               longitude >= -180 && longitude <= 180;
    }
    
    /**
     * 验证坐标是否有效（包含精度检查）
     * @param latitude 纬度
     * @param longitude 经度
     * @param accuracy 精度（米）
     * @return true 如果坐标有效
     */
    public static boolean isValidLocation(double latitude, double longitude, float accuracy) {
        // 基础坐标检查
        if (!isValidLocation(latitude, longitude)) {
            return false;
        }
        
        // 精度检查：BLE Tag 精度差于 200 米的数据无参考价值
        if (accuracy > MAX_ACCEPTABLE_ACCURACY) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证坐标是否在中国范围内
     * @param latitude 纬度
     * @param longitude 经度
     * @return true 如果在中国范围内
     */
    public static boolean isInChina(double latitude, double longitude) {
        return latitude >= CHINA_MIN_LAT && latitude <= CHINA_MAX_LAT &&
               longitude >= CHINA_MIN_LNG && longitude <= CHINA_MAX_LNG;
    }
    
    /**
     * 验证时间戳是否合理
     * @param timestamp 时间戳（毫秒）
     * @return true 如果时间戳合理
     */
    public static boolean isValidTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long oneYearMs = 365L * 24 * 60 * 60 * 1000;
        
        // 不能是未来时间，也不能超过1年前的时间
        return timestamp <= currentTime && timestamp >= (currentTime - oneYearMs);
    }
    
    /**
     * 综合验证位置数据
     * @param latitude 纬度
     * @param longitude 经度
     * @param accuracy 精度（米）
     * @param timestamp 时间戳（毫秒）
     * @param checkChinaRange 是否检查中国范围
     * @return true 如果所有检查都通过
     */
    public static boolean isValidLocation(double latitude, double longitude, 
                                          float accuracy, long timestamp,
                                          boolean checkChinaRange) {
        // 基础坐标检查
        if (!isValidLocation(latitude, longitude, accuracy)) {
            return false;
        }
        
        // 时间戳检查
        if (!isValidTimestamp(timestamp)) {
            return false;
        }
        
        // 中国范围检查（可选）
        if (checkChinaRange && !isInChina(latitude, longitude)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证是否为异常跳变点
     * @param distanceMeters 距离（米）
     * @param timeDiffMs 时间差（毫秒）
     * @param maxDistanceMeters 最大允许距离（默认500米）
     * @param maxTimeMs 最大时间窗口（默认60秒）
     * @return true 如果是异常跳变点
     */
    public static boolean isAbnormalJump(double distanceMeters, long timeDiffMs, 
                                         double maxDistanceMeters, long maxTimeMs) {
        return timeDiffMs > 0 && timeDiffMs < maxTimeMs && distanceMeters > maxDistanceMeters;
    }
    
    /**
     * 验证是否为异常跳变点（使用默认阈值）
     * @param distanceMeters 距离（米）
     * @param timeDiffMs 时间差（毫秒）
     * @return true 如果是异常跳变点
     */
    public static boolean isAbnormalJump(double distanceMeters, long timeDiffMs) {
        return isAbnormalJump(distanceMeters, timeDiffMs, 500, 60000);
    }
}
