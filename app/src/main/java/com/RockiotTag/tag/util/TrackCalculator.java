package com.RockiotTag.tag.util;

import com.RockiotTag.tag.CoordinateUtils;
import com.RockiotTag.tag.model.LocationData;

import java.util.List;

/**
 * 轨迹计算工具类
 * 职责：统一处理轨迹相关的计算逻辑
 */
public class TrackCalculator {
    
    /**
     * 轨迹统计结果
     */
    public static class TrackStatistics {
        public double totalDistanceKm;      // 总距离（公里）
        public int validSegments;           // 有效段数
        public int filteredJumps;           // 过滤的跳变点数
        
        public TrackStatistics(double totalDistanceKm, int validSegments, int filteredJumps) {
            this.totalDistanceKm = totalDistanceKm;
            this.validSegments = validSegments;
            this.filteredJumps = filteredJumps;
        }
    }
    
    /**
     * 计算总距离（公里）
     * @param locationRecords 位置记录列表
     * @return 总距离（公里）
     */
    public static double calculateTotalDistance(List<LocationData> locationRecords) {
        TrackStatistics stats = calculateTrackStatistics(locationRecords);
        return stats.totalDistanceKm;
    }
    
    /**
     * 计算轨迹统计信息
     * @param locationRecords 位置记录列表
     * @return 轨迹统计结果
     */
    public static TrackStatistics calculateTrackStatistics(List<LocationData> locationRecords) {
        if (locationRecords == null || locationRecords.size() < 2) {
            return new TrackStatistics(0, 0, 0);
        }
        
        double totalDistanceMeters = 0;
        int validSegments = 0;
        int filteredJumps = 0;
        
        for (int i = 1; i < locationRecords.size(); i++) {
            LocationData prev = locationRecords.get(i - 1);
            LocationData curr = locationRecords.get(i);
            
            // 验证坐标有效性（使用 LocationValidator）
            if (LocationValidator.isValidLocation(prev.getLatitude(), prev.getLongitude()) &&
                LocationValidator.isValidLocation(curr.getLatitude(), curr.getLongitude())) {
                
                double segmentDistance = CoordinateUtils.calculateDistanceMeters(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
                );
                
                // 过滤异常跳变点（使用 LocationValidator）
                long timeDiff = curr.getTimestamp() - prev.getTimestamp();
                if (LocationValidator.isAbnormalJump(segmentDistance, timeDiff)) {
                    filteredJumps++;
                    continue;
                }
                
                totalDistanceMeters += segmentDistance;
                validSegments++;
            }
        }
        
        return new TrackStatistics(totalDistanceMeters / 1000.0, validSegments, filteredJumps);
    }
}
