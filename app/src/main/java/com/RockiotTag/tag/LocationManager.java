package com.RockiotTag.tag;

import android.location.Location;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class LocationManager {
    private static final String TAG = "LocationManager";
    
    // RSSI到距离的转换参数
    private static final double TX_POWER = -59; // 1米处的信号强度
    private static final double N = 2.0; // 环境衰减因子
    
    // 卡尔曼滤波器参数
    private double x; // 位置估计
    private double p = 1.0; // 估计误差
    private double q = 0.1; // 过程噪声
    private double r = 0.5; // 测量噪声
    
    public LocationManager() {
    }
    
    // 基于RSSI估算距离
    public double estimateDistance(int rssi) {
        if (rssi == 0) {
            return -1.0; // 无法估算
        }
        
        double ratio = (double) rssi / TX_POWER;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10 / N);
        } else {
            double distance = 0.89976 * Math.pow(ratio, 7.7095) + 0.111;
            return distance;
        }
    }
    
    // 融合GPS和BLE定位
    public Location fuseLocations(Location gpsLocation, Location bleLocation) {
        if (gpsLocation == null && bleLocation == null) {
            return null;
        }
        
        if (gpsLocation == null) {
            return bleLocation;
        }
        
        if (bleLocation == null) {
            return gpsLocation;
        }
        
        // 简单的加权融合，根据精度加权
        float gpsAccuracy = gpsLocation.getAccuracy();
        float bleAccuracy = bleLocation.getAccuracy();
        
        double totalWeight = 1.0 / gpsAccuracy + 1.0 / bleAccuracy;
        double gpsWeight = (1.0 / gpsAccuracy) / totalWeight;
        double bleWeight = (1.0 / bleAccuracy) / totalWeight;
        
        double latitude = gpsLocation.getLatitude() * gpsWeight + bleLocation.getLatitude() * bleWeight;
        double longitude = gpsLocation.getLongitude() * gpsWeight + bleLocation.getLongitude() * bleWeight;
        
        Location fusedLocation = new Location("fused");
        fusedLocation.setLatitude(latitude);
        fusedLocation.setLongitude(longitude);
        fusedLocation.setAccuracy((float) (1.0 / totalWeight));
        fusedLocation.setTime(System.currentTimeMillis());
        
        return fusedLocation;
    }
    
    // 卡尔曼滤波平滑位置
    public Location kalmanFilter(Location newLocation) {
        if (newLocation == null) {
            return null;
        }
        
        double measurement = newLocation.getLatitude(); // 这里简化处理，只处理纬度
        // 预测
        double predictX = x;
        double predictP = p + q;
        
        // 更新
        double k = predictP / (predictP + r);
        x = predictX + k * (measurement - predictX);
        p = (1 - k) * predictP;
        
        // 创建过滤后的位置
        Location filteredLocation = new Location(newLocation);
        filteredLocation.setLatitude(x);
        // 同样处理经度
        // 这里需要扩展以处理经度
        
        return filteredLocation;
    }
    
    // 计算两点之间的距离（米）
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 地球半径（公里）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // 转换为米
        return distance;
    }
    
    // 位置平滑算法
    public Location smoothLocation(List<Location> locationList) {
        if (locationList == null || locationList.isEmpty()) {
            return null;
        }
        
        double sumLat = 0, sumLon = 0;
        for (Location location : locationList) {
            sumLat += location.getLatitude();
            sumLon += location.getLongitude();
        }
        
        double avgLat = sumLat / locationList.size();
        double avgLon = sumLon / locationList.size();
        
        Location smoothedLocation = new Location("smoothed");
        smoothedLocation.setLatitude(avgLat);
        smoothedLocation.setLongitude(avgLon);
        smoothedLocation.setTime(System.currentTimeMillis());
        
        return smoothedLocation;
    }
}