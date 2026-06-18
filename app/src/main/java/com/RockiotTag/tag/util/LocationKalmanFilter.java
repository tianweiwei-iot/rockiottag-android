package com.RockiotTag.tag.util;

import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

/**
 * 卡尔曼滤波器 - 双维度完整实现（精度感知版本）
 * 
 * 针对 BLE Tag 设备优化：
 * - 同时处理纬度和经度
 * - 利用精度信息动态调整滤波强度
 * - 过程噪声q设为0.5，适应BLE Tag较大的漂移
 */
public class LocationKalmanFilter {
    private static final String TAG = "LocationKalmanFilter";
    
    // 纬度维度
    private double xLat = Double.NaN;  // 纬度估计值
    private double pLat = 1.0;         // 纬度估计误差协方差
    
    // 经度维度
    private double xLng = Double.NaN;  // 经度估计值
    private double pLng = 1.0;         // 经度估计误差协方差
    
    // 过程噪声（BLE Tag 漂移较大，应设稍高）
    private double q = 0.5;
    
    /**
     * 卡尔曼滤波 — 精度感知版本
     * 
     * @param latitude  观测纬度
     * @param longitude 观测经度
     * @param accuracy  观测精度（米），BLE Tag 通常 30-50
     * @return [filteredLatitude, filteredLongitude]
     */
    public double[] filter(double latitude, double longitude, float accuracy) {
        // 将精度（米）转换为经纬度噪声
        // 1度 ≈ 111km，所以 accuracy 米 ≈ accuracy/111000 度
        double r = accuracy / 111000.0;
        
        if (Double.isNaN(xLat)) {
            // 第一个观测，直接初始化
            xLat = latitude;
            xLng = longitude;
            LogUtil.d(TAG, String.format("Kalman filter initialized: lat=%.6f, lng=%.6f, accuracy=%.1fm",
                latitude, longitude, accuracy));
        } else {
            // ===== 预测步骤 =====
            double predictXLat = xLat;
            double predictPLat = pLat + q;
            double predictXLng = xLng;
            double predictPLng = pLng + q;
            
            // ===== 更新步骤 - 纬度 =====
            double kLat = predictPLat / (predictPLat + r);
            xLat = predictXLat + kLat * (latitude - predictXLat);
            pLat = (1 - kLat) * predictPLat;
            
            // ===== 更新步骤 - 经度 =====
            double kLng = predictPLng / (predictPLng + r);
            xLng = predictXLng + kLng * (longitude - predictXLng);
            pLng = (1 - kLng) * predictPLng;
            
            LogUtil.d(TAG, String.format("Kalman filtered: (%.6f, %.6f) -> (%.6f, %.6f), accuracy=%.1fm, k_lat=%.3f, k_lng=%.3f",
                latitude, longitude, xLat, xLng, accuracy, kLat, kLng));
        }
        
        return new double[]{xLat, xLng};
    }
    
    /**
     * 重置滤波器状态
     */
    public void reset() {
        xLat = Double.NaN;
        xLng = Double.NaN;
        pLat = 1.0;
        pLng = 1.0;
        LogUtil.d(TAG, "Kalman filter reset");
    }
    
    /**
     * 设置过程噪声（可根据设备特性调整）
     * @param q 过程噪声，默认0.5，范围0.01-1.0
     */
    public void setProcessNoise(double q) {
        if (q > 0 && q <= 1.0) {
            this.q = q;
            LogUtil.d(TAG, "Process noise updated: q=" + q);
        } else {
            Log.w(TAG, "Invalid process noise value: " + q + ", keeping current value: " + this.q);
        }
    }
}
