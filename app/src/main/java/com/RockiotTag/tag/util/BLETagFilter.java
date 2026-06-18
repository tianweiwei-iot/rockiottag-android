package com.RockiotTag.tag.util;

import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import com.RockiotTag.tag.CoordinateUtils;
import com.RockiotTag.tag.LocationRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BLE Tag 专用过滤器
 * 
 * 核心思路：BLE Tag（30-50米精度）与GPS设备（3-10米精度）的漂移特征完全不同
 * - GPS：偶尔大跳变（几公里），需要速度/距离阈值过滤
 * - BLE Tag：频繁小漂移（几十米），需要精度感知的聚合算法
 * 
 * 关键策略：
 * 1. 异常跳变判定：速度 > 120km/h + 距离 > 3倍精度漂移
 * 2. 静止漂移聚合：将精度范围内的漂移点收缩到中心
 * 3. 移动判定：至少超过1倍精度才有意义
 */
public class BLETagFilter {
    private static final String TAG = "BLETagFilter";
    
    // ===== BLE Tag 特有参数 =====
    // 设备定位精度（米）- BLE Tag 典型值
    private static final double DEVICE_ACCURACY = 50;
    
    // 精度倍数：距离在此范围内视为"同一位置"的漂移
    private static final double ACCURACY_MULTIPLIER = 2.0;
    
    // 同一位置判定半径 = ACCURACY_MULTIPLIER * DEVICE_ACCURACY = 100米
    private static final double SAME_LOCATION_RADIUS = ACCURACY_MULTIPLIER * DEVICE_ACCURACY;
    
    // ===== 速度阈值（需考虑设备精度） =====
    // BLE Tag 场景下，合理的最高移动速度
    // Tag 通常挂在物品上，不太可能超过 120km/h（汽车速度）
    private static final double MAX_REALISTIC_SPEED_MS = 33.33; // 120km/h
    
    // ===== 停留点聚合 =====
    // 静止状态：精度范围内漂移，距离阈值 = 2倍精度
    private static final double STATIONARY_DISTANCE = SAME_LOCATION_RADIUS; // 100米
    // 静止状态时间窗口
    private static final long STATIONARY_TIME = 30 * 60 * 1000; // 30分钟
    
    // 移动状态：设备正在移动，需要更小的距离阈值捕捉路径
    private static final double MOVING_DISTANCE = DEVICE_ACCURACY; // 50米
    // 移动状态时间窗口
    private static final long MOVING_TIME = 60 * 1000; // 1分钟
    
    /**
     * 核心过滤方法：判断两个相邻点是否为异常跳变
     * 
     * 关键思路：对于 BLE Tag，"异常"不等于"速度快"，
     * 而是"移动距离超出精度的合理范围"
     * 
     * @param distanceMeters 两点间距离（米）
     * @param timeDiffMs 时间差（毫秒）
     * @param prevAccuracy 前一个点的精度（米），如果未知则传DEVICE_ACCURACY
     * @param currAccuracy 当前点的精度（米），如果未知则传DEVICE_ACCURACY
     * @return true 如果是异常跳变，应该过滤
     */
    public static boolean isAnomalous(double distanceMeters, long timeDiffMs,
                                       float prevAccuracy, float currAccuracy) {
        if (timeDiffMs <= 0) {
            Log.w(TAG, "Invalid time difference: " + timeDiffMs + "ms");
            return true; // 时间倒流，数据异常
        }
        
        // 第1层：绝对速度检查
        double speedMs = distanceMeters / (timeDiffMs / 1000.0);
        if (speedMs > MAX_REALISTIC_SPEED_MS) {
            LogUtil.d(TAG, String.format("Filtered by speed: %.1f m/s (%.1f km/h) > %.1f m/s",
                speedMs, speedMs * 3.6, MAX_REALISTIC_SPEED_MS));
            return true;
        }
        
        // 第2层：精度感知的距离检查
        // 两个点各有可能偏移 accuracy 米，所以合理范围内最大距离 = accuracy1 + accuracy2
        float effectivePrevAccuracy = prevAccuracy > 0 ? prevAccuracy : (float) DEVICE_ACCURACY;
        float effectiveCurrAccuracy = currAccuracy > 0 ? currAccuracy : (float) DEVICE_ACCURACY;
        double expectedDrift = (effectivePrevAccuracy + effectiveCurrAccuracy);
        
        // 如果实际距离 > 精度合理漂移的 3 倍，认为是异常跳变
        // 3倍是统计经验值：正态分布下 3σ 覆盖 99.7%
        if (distanceMeters > expectedDrift * 3.0) {
            // 但要排除一种情况：设备确实在快速移动
            // 如果速度合理（< 120km/h），即使距离大于3倍精度，也可能是真实的
            if (speedMs <= MAX_REALISTIC_SPEED_MS) {
                LogUtil.d(TAG, String.format("Large jump but reasonable speed: %.0fm in %dms (%.1f km/h)",
                    distanceMeters, timeDiffMs, speedMs * 3.6));
                return false; // 速度合理，不是异常
            }
            LogUtil.d(TAG, String.format("Filtered by precision-aware distance: %.0fm > %.0fm (3x accuracy)",
                distanceMeters, expectedDrift * 3.0));
            return true;
        }
        
        return false;
    }
    
    /**
     * 简化版本：不提供精度信息时使用默认值
     */
    public static boolean isAnomalous(double distanceMeters, long timeDiffMs) {
        return isAnomalous(distanceMeters, timeDiffMs, 
                          (float) DEVICE_ACCURACY, (float) DEVICE_ACCURACY);
    }
    
    /**
     * 静止漂移过滤：设备不动时，识别并聚合精度范围内的漂移点
     * 
     * 关键思路：如果连续多个点都在 2*精度 半径内，
     * 它们代表的是同一个"停留位置"，应该取中心点
     * 
     * @param records 原始位置记录列表
     * @return 过滤后的位置记录列表
     */
    public static List<LocationRecord> filterStationaryDrift(List<LocationRecord> records) {
        if (records == null || records.size() < 2) {
            return records != null ? records : new ArrayList<>();
        }
        
        List<LocationRecord> result = new ArrayList<>();
        List<LocationRecord> cluster = new ArrayList<>();  // 当前聚合簇
        double clusterCenterLat = 0, clusterCenterLng = 0;
        
        for (LocationRecord record : records) {
            if (cluster.isEmpty()) {
                cluster.add(record);
                clusterCenterLat = record.getLatitude();
                clusterCenterLng = record.getLongitude();
                continue;
            }
            
            // 计算与当前聚合中心点的距离
            double distToCenter = CoordinateUtils.calculateDistanceMeters(
                clusterCenterLat, clusterCenterLng,
                record.getLatitude(), record.getLongitude()
            );
            
            if (distToCenter <= SAME_LOCATION_RADIUS) {
                // 在精度范围内，加入当前聚合
                cluster.add(record);
                // 重新计算中心点（所有点的平均值）
                clusterCenterLat = cluster.stream()
                    .mapToDouble(LocationRecord::getLatitude).average().orElse(0);
                clusterCenterLng = cluster.stream()
                    .mapToDouble(LocationRecord::getLongitude).average().orElse(0);
            } else {
                // 超出精度范围，输出当前聚合的代表点
                result.add(createRepresentativePoint(cluster, clusterCenterLat, clusterCenterLng));
                // 开始新聚合
                cluster.clear();
                cluster.add(record);
                clusterCenterLat = record.getLatitude();
                clusterCenterLng = record.getLongitude();
            }
        }
        
        // 处理最后一个聚合
        if (!cluster.isEmpty()) {
            result.add(createRepresentativePoint(cluster, clusterCenterLat, clusterCenterLng));
        }
        
        LogUtil.d(TAG, String.format("Stationary drift filter: %d -> %d points (reduced %.1f%%)",
            records.size(), result.size(),
            (1.0 - (double)result.size() / records.size()) * 100));
        
        return result;
    }
    
    /**
     * 从聚合簇创建代表点
     * 使用中心坐标 + 最早时间戳（保持时间连续性）
     */
    private static LocationRecord createRepresentativePoint(
            List<LocationRecord> cluster, double centerLat, double centerLng) {
        LocationRecord first = cluster.get(0);
        // 注意：这里简化处理，不保留accuracy信息
        // 如果需要保留，应该在LocationRecord中添加accuracy字段
        return new LocationRecord(
            first.getDeviceId(), centerLat, centerLng, first.getTimestamp()
        );
    }
    
    /**
     * 获取BLE Tag推荐的停留点距离阈值（移动状态）
     */
    public static double getMovingDistanceThreshold() {
        return MOVING_DISTANCE;
    }
    
    /**
     * 获取BLE Tag推荐的停留点时间阈值（移动状态）
     */
    public static long getMovingTimeThreshold() {
        return MOVING_TIME;
    }
    
    /**
     * 获取BLE Tag推荐的停留点距离阈值（静止状态）
     */
    public static double getStationaryDistanceThreshold() {
        return STATIONARY_DISTANCE;
    }
    
    /**
     * 获取BLE Tag推荐的停留点时间阈值（静止状态）
     */
    public static long getStationaryTimeThreshold() {
        return STATIONARY_TIME;
    }
}
