package com.RockiotTag.tag.usecase;

import android.util.Log;

import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.repository.LocationRepository;
import com.RockiotTag.tag.util.BLETagFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * 计算轨迹统计信息的UseCase
 * 
 * 职责：
 * 1. 计算总距离
 * 2. 计算时长
 * 3. 计算平均速度
 */
public class TrackStatisticsUseCase extends BaseUseCase<List<LocationRecord>, TrackStatisticsUseCase.Result> {
    
    private static final String TAG = "TrackStatisticsUseCase";
    
    public static class Result {
        public final double totalDistance; // 米
        public final long totalTime; // 毫秒
        public final double averageSpeed; // 米/秒
        public final int pointCount;
        
        public Result(double totalDistance, long totalTime, double averageSpeed, int pointCount) {
            this.totalDistance = totalDistance;
            this.totalTime = totalTime;
            this.averageSpeed = averageSpeed;
            this.pointCount = pointCount;
        }
    }
    
    @Override
    protected Result executeSync(List<LocationRecord> records) throws Exception {
        Log.d(TAG, "Calculating track statistics from " + (records != null ? records.size() : 0) + " records");
        
        if (records == null || records.size() < 2) {
            return new Result(0, 0, 0, records != null ? records.size() : 0);
        }
        
        double totalDistance = 0;
        long totalTime = 0;
        int filteredCount = 0;
        
        // 计算总距离（使用 BLE Tag 过滤器）
        for (int i = 1; i < records.size(); i++) {
            LocationRecord prev = records.get(i - 1);
            LocationRecord current = records.get(i);
            
            double distance = calculateDistance(
                prev.getLatitude(), prev.getLongitude(),
                current.getLatitude(), current.getLongitude()
            );
            
            long timeDiff = current.getTimestamp() - prev.getTimestamp();
            
            // 使用 BLE Tag 专用过滤器判断是否为异常跳变
            if (BLETagFilter.isAnomalous(distance, timeDiff, 
                                        prev.getAccuracy(), current.getAccuracy())) {
                filteredCount++;
                continue;
            }
            
            totalDistance += distance;
        }
        
        // 计算总时长
        if (records.size() >= 2) {
            totalTime = records.get(records.size() - 1).getTimestamp() - records.get(0).getTimestamp();
        }
        
        // 计算平均速度
        double averageSpeed = totalTime > 0 ? totalDistance / (totalTime / 1000.0) : 0;
        
        Log.d(TAG, String.format("Track statistics: distance=%.2fm, time=%dms, speed=%.2fm/s, filtered=%d",
            totalDistance, totalTime, averageSpeed, filteredCount));
        
        return new Result(totalDistance, totalTime, averageSpeed, records.size());
    }
    
    /**
     * 计算两点间距离（Haversine公式）
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int EARTH_RADIUS = 6371000;
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c;
    }
}
