package com.RockiotTag.tag.usecase;

import android.util.Log;

import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.StayPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * 检测停留点的UseCase
 * 
 * 职责：
 * 1. 分析轨迹数据，识别停留点
 * 2. 基于距离和时间阈值判断
 * 3. 返回停留点列表
 */
public class DetectStayPointsUseCase extends BaseUseCase<List<LocationRecord>, List<StayPoint>> {
    
    private static final String TAG = "DetectStayPointsUseCase";
    
    // 停留点检测参数
    private static final double STAY_RADIUS_METERS = 100.0; // 停留半径（米）
    private static final long STAY_DURATION_MS = 5 * 60 * 1000; // 停留时长（5分钟）
    
    @Override
    protected List<StayPoint> executeSync(List<LocationRecord> records) throws Exception {
        Log.d(TAG, "Detecting stay points from " + (records != null ? records.size() : 0) + " records");
        
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<StayPoint> stayPoints = new ArrayList<>();
        int stayPointIndex = 0; // 停留点序号
        
        // 停留点检测算法
        LocationRecord clusterStart = records.get(0);
        double clusterLat = clusterStart.getLatitude();
        double clusterLng = clusterStart.getLongitude();
        long clusterStartTime = clusterStart.getTimestamp();
        
        for (int i = 1; i < records.size(); i++) {
            LocationRecord current = records.get(i);
            
            // 计算距离
            double distance = calculateDistance(
                clusterLat, clusterLng,
                current.getLatitude(), current.getLongitude()
            );
            
            if (distance <= STAY_RADIUS_METERS) {
                // 在停留区域内，继续累积
                long duration = current.getTimestamp() - clusterStartTime;
                
                if (duration >= STAY_DURATION_MS) {
                    // 达到停留时长，创建停留点
                    StayPoint stayPoint = new StayPoint(clusterLat, clusterLng, clusterStartTime, current.getTimestamp());
                    stayPoint.setOriginalIndex(stayPointIndex++); // 设置序号
                    
                    stayPoints.add(stayPoint);
                    Log.d(TAG, "Stay point detected: index=" + stayPoint.getOriginalIndex() + ", duration=" + (duration / 1000) + "s");
                    
                    // 重置聚类
                    if (i + 1 < records.size()) {
                        LocationRecord next = records.get(i + 1);
                        clusterLat = next.getLatitude();
                        clusterLng = next.getLongitude();
                        clusterStartTime = next.getTimestamp();
                    }
                }
            } else {
                // 超出停留区域，重置聚类
                clusterLat = current.getLatitude();
                clusterLng = current.getLongitude();
                clusterStartTime = current.getTimestamp();
            }
        }
        
        Log.d(TAG, "Detected " + stayPoints.size() + " stay points");
        return stayPoints;
    }
    
    /**
     * 计算两点间距离（Haversine公式）
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int EARTH_RADIUS = 6371000; // 地球半径（米）
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c;
    }
}
