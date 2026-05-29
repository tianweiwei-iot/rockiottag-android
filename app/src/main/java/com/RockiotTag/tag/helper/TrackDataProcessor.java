package com.RockiotTag.tag.helper;

import android.util.Log;

import com.RockiotTag.tag.CoordinateUtils;
import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.model.LocationData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 轨迹数据处理助手
 * 职责：封装数据过滤、排序和转换逻辑
 */
public class TrackDataProcessor {
    private static final String TAG = "TrackDataProcessor";
    
    /**
     * 过滤无效位置记录
     */
    public static List<LocationRecord> filterInvalidRecords(List<LocationRecord> records) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<LocationRecord> filtered = new ArrayList<>();
        for (LocationRecord record : records) {
            if (record.getLatitude() != 0 && record.getLongitude() != 0 && 
                record.getTimestamp() > 0) {
                filtered.add(record);
            }
        }
        
        Log.d(TAG, "Filtered " + (records.size() - filtered.size()) + " invalid records");
        return filtered;
    }
    
    /**
     * 按时间排序
     */
    public static void sortByTime(List<LocationRecord> records) {
        if (records != null && records.size() > 1) {
            Collections.sort(records, (r1, r2) -> Long.compare(r1.getTimestamp(), r2.getTimestamp()));
        }
    }
    
    /**
     * 过滤异常跳变点（已弃用，请使用 BLETagFilter）
     * @deprecated 使用 {@link com.RockiotTag.tag.util.BLETagFilter#isAnomalous(double, long, float, float)} 替代
     */
    @Deprecated
    public static List<LocationRecord> filterAbnormalJumps(List<LocationRecord> records) {
        if (records == null || records.size() < 2) {
            return records != null ? records : new ArrayList<>();
        }
        
        List<LocationRecord> filtered = new ArrayList<>();
        filtered.add(records.get(0));
        
        int jumpCount = 0;
        for (int i = 1; i < records.size(); i++) {
            LocationRecord prev = records.get(i - 1);
            LocationRecord curr = records.get(i);
            
            double distance = CoordinateUtils.calculateDistanceMeters(
                prev.getLatitude(), prev.getLongitude(),
                curr.getLatitude(), curr.getLongitude()
            );
            
            long timeDiff = curr.getTimestamp() - prev.getTimestamp();
            
            // 使用 BLE Tag 专用过滤器
            if (com.RockiotTag.tag.util.BLETagFilter.isAnomalous(distance, timeDiff,
                                                                 prev.getAccuracy(), curr.getAccuracy())) {
                jumpCount++;
                Log.d(TAG, String.format("Filtered abnormal jump: %.0fm in %dms", distance, timeDiff));
                continue;
            }
            
            filtered.add(curr);
        }
        
        Log.d(TAG, "Filtered " + jumpCount + " abnormal jumps");
        return filtered;
    }
    
    /**
     * 转换 LocationRecord 为 LocationData
     */
    public static List<LocationData> convertToLocationDataList(List<LocationRecord> records) {
        List<LocationData> result = new ArrayList<>();
        if (records != null) {
            for (LocationRecord record : records) {
                LocationData data = new LocationData(
                    record.getDeviceId(),
                    record.getLatitude(),
                    record.getLongitude(),
                    record.getTimestamp()
                );
                result.add(data);
            }
        }
        return result;
    }
    
    /**
     * 生成停留点列表
     */
    public static List<StayPoint> generateStayPoints(List<LocationRecord> records, 
                                                     double distanceThreshold,
                                                     long timeThreshold) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<StayPoint> stayPoints = new ArrayList<>();
        StayPoint currentStayPoint = null;
        int index = 1;
        
        for (LocationRecord record : records) {
            LocationData locationData = new LocationData(
                record.getDeviceId(),
                record.getLatitude(),
                record.getLongitude(),
                record.getTimestamp()
            );
            
            if (currentStayPoint == null) {
                currentStayPoint = new StayPoint(
                    record.getLatitude(),
                    record.getLongitude(),
                    record.getTimestamp(),
                    record.getTimestamp()
                );
                currentStayPoint.setOriginalIndex(index++);
                currentStayPoint.addMergedRecord(locationData);
            } else {
                double distance = CoordinateUtils.calculateDistanceMeters(
                    currentStayPoint.getLatitude(), currentStayPoint.getLongitude(),
                    record.getLatitude(), record.getLongitude()
                );
                long timeDiff = record.getTimestamp() - currentStayPoint.getLeaveTime();
                
                if (distance <= distanceThreshold && timeDiff <= timeThreshold) {
                    currentStayPoint.setLeaveTime(record.getTimestamp());
                    currentStayPoint.addMergedRecord(locationData);
                } else {
                    stayPoints.add(currentStayPoint);
                    currentStayPoint = new StayPoint(
                        record.getLatitude(),
                        record.getLongitude(),
                        record.getTimestamp(),
                        record.getTimestamp()
                    );
                    currentStayPoint.setOriginalIndex(index++);
                    currentStayPoint.addMergedRecord(locationData);
                }
            }
        }
        
        if (currentStayPoint != null) {
            stayPoints.add(currentStayPoint);
        }
        
        Log.d(TAG, "Generated " + stayPoints.size() + " stay points from " + records.size() + " records");
        return stayPoints;
    }
}
