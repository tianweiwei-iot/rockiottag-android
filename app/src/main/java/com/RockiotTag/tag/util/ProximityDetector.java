package com.RockiotTag.tag.util;

/**
 * 距离检测器
 * 根据蓝牙RSSI信号强度判断设备是否在附近
 */
public class ProximityDetector {
    
    // RSSI阈值配置（可根据环境调整）
    private static final int RSSI_THRESHOLD_NEARBY = -70;        // 默认阈值：在附近
    private static final int RSSI_THRESHOLD_VERY_CLOSE = -50;    // 非常近
    private static final int RSSI_THRESHOLD_FAR = -80;           // 较远
    
    /**
     * 判断设备是否在附近
     * @param rssi 蓝牙信号强度（dBm）
     * @return true表示在附近
     */
    public static boolean isNearby(int rssi) {
        return rssi > RSSI_THRESHOLD_NEARBY;
    }
    
    /**
     * 获取距离描述（用于日志和调试）
     * @param rssi 信号强度
     * @return 距离级别描述
     */
    public static String getDistanceLevel(int rssi) {
        if (rssi > RSSI_THRESHOLD_VERY_CLOSE) {
            return "VERY_CLOSE";  // 1-2米
        } else if (rssi > RSSI_THRESHOLD_NEARBY) {
            return "NEARBY";      // 3-5米
        } else if (rssi > RSSI_THRESHOLD_FAR) {
            return "FAR";         // 5-10米
        } else {
            return "VERY_FAR";    // >10米
        }
    }
    
    /**
     * 根据RSSI估算距离（米）
     * 注意：这是一个粗略估算，实际距离受环境影响很大
     * @param rssi 信号强度
     * @return 估算的距离（米）
     */
    public static double estimateDistance(int rssi) {
        // 简化的距离估算公式
        // 假设发射功率为 -59dBm（1米处）
        int txPower = -59;
        
        if (rssi == 0) {
            return -1.0; // 无法判断
        }
        
        double ratio = (txPower * 1.0) / rssi;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        } else {
            return 0.89976 * Math.pow(ratio, 7.7095) + 0.111;
        }
    }
}
