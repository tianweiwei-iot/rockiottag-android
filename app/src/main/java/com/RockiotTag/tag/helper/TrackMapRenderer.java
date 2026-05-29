package com.RockiotTag.tag.helper;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import com.RockiotTag.tag.CoordinateUtils;
import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.util.MapMarkerHelper;
import com.RockiotTag.tag.util.TimeFormatter;
import com.amap.api.maps.AMap;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;

/**
 * 高德地图轨迹渲染助手
 * 职责：封装高德地图上的轨迹渲染逻辑
 */
public class TrackMapRenderer {
    private static final String TAG = "TrackMapRenderer";
    
    /**
     * 在高德地图上渲染完整轨迹
     * @return 轨迹线对象
     */
    public static Polyline renderTrackOnAMap(
        AMap aMap,
        List<StayPoint> stayPoints,
        List<LocationRecord> allLocationRecords,
        boolean showPolyline,
        boolean showMarkers,
        boolean isSimpleMode,
        List<Marker> positionMarkers,
        List<Marker> arrowMarkers) {
        
        Polyline trackPolyline = null;
        
        if (stayPoints == null || stayPoints.isEmpty()) {
            return null;
        }
        
        // 绘制轨迹线 - 使用合并后的stayPoints，而不是所有原始记录
        List<LatLng> latLngList = new ArrayList<>();
        int filteredCount = 0;
        
        // 关键修复：使用 stayPoints（已合并的停留点）而不是 allLocationRecords
        // 这样才能实现精度调节的效果：200米阈值时，距离<200米的点合并成一个
        for (StayPoint stayPoint : stayPoints) {
            // 过滤无效坐标（0, 0）和异常坐标
            if (Math.abs(stayPoint.getLatitude()) < 0.0001 && Math.abs(stayPoint.getLongitude()) < 0.0001) {
                Log.w(TAG, "Filtered invalid coordinate: lat=" + stayPoint.getLatitude() + ", lng=" + stayPoint.getLongitude());
                filteredCount++;
                continue;
            }
            LatLng latLng = CoordinateUtils.wgs84ToGcj02(
                stayPoint.getLatitude(), stayPoint.getLongitude());
            latLngList.add(latLng);
        }
        
        if (filteredCount > 0) {
            Log.d(TAG, "Filtered " + filteredCount + " invalid coordinates from " + stayPoints.size() + " stay points");
        }
        
        if (latLngList.size() > 1) {
            PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(latLngList)
                .color(0xFF0088FF)
                .width(12f)
                .setDottedLine(false);
            trackPolyline = aMap.addPolyline(polylineOptions);
            trackPolyline.setVisible(showPolyline);
            
            addDirectionArrows(aMap, latLngList, arrowMarkers);
        }
        
        // 添加标记点 - 使用合并后的stayPoints
        addMarkersOnAMap(aMap, stayPoints, isSimpleMode, positionMarkers);
        
        // 调整相机视角（默认不保持缩放级别，由调用方决定）
        adjustCameraOnAMap(aMap, latLngList, false, 17.0f);
        
        Log.d(TAG, "Track rendered on AMap with " + stayPoints.size() + " stay points");
        
        return trackPolyline;
    }
    
    /**
     * 将 StayPoint 列表转换为 LocationRecord 列表（兼容旧代码）
     */
    private static List<LocationRecord> convertStayPointsToRecords(List<StayPoint> stayPoints) {
        List<LocationRecord> records = new ArrayList<>();
        for (StayPoint sp : stayPoints) {
            records.add(new LocationRecord(
                "", // deviceId
                sp.getLatitude(),
                sp.getLongitude(),
                sp.getArriveTime()
            ));
        }
        return records;
    }
    
    /**
     * 添加方向箭头
     */
    private static void addDirectionArrows(AMap aMap, List<LatLng> latLngList, List<Marker> arrowMarkers) {
        int arrowInterval = 5;
        
        for (int i = 0; i < latLngList.size() - 1; i += arrowInterval) {
            LatLng p1 = latLngList.get(i);
            LatLng p2 = latLngList.get(Math.min(i + 1, latLngList.size() - 1));
            
            double angle = calculateBearing(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
            
            MarkerOptions arrowMarker = new MarkerOptions()
                .position(p1)
                .icon(MapMarkerHelper.createArrowMarker(angle))
                .anchor(0.5f, 0.5f)
                .zIndex(1);
            
            Marker marker = aMap.addMarker(arrowMarker);
            arrowMarkers.add(marker);
        }
    }
    
    /**
     * 添加标记点（优化：起点终点层级更高，精简模式下隐藏普通点）
     * 关键修复：使用合并后的stayPoints来显示标记，而不是原始记录
     */
    private static void addMarkersOnAMap(
        AMap aMap,
        List<StayPoint> stayPoints,
        boolean isSimpleMode,
        List<Marker> positionMarkers) {
            
        // 优化：单个点的处理 - 今天显示“起点”，历史日期显示“终点”
        boolean isToday = isToday(stayPoints);
            
        for (int i = 0; i < stayPoints.size(); i++) {
            StayPoint stayPoint = stayPoints.get(i);
                
            // 过滤无效坐标（0, 0）
            if (Math.abs(stayPoint.getLatitude()) < 0.0001 && Math.abs(stayPoint.getLongitude()) < 0.0001) {
                Log.w(TAG, "Skipping marker for invalid coordinate at index " + i);
                continue;
            }
                
            // 如果只有一个点：今天显示起点，历史日期显示终点
            if (stayPoints.size() == 1) {
                if (isToday) {
                    Log.d(TAG, "Only one point today on AMap, showing Start marker");
                } else {
                    Log.d(TAG, "Only one point in history on AMap, showing End marker");
                }
            }
                
            LatLng latLng = CoordinateUtils.wgs84ToGcj02(
                stayPoint.getLatitude(), stayPoint.getLongitude());
                
            com.amap.api.maps.model.BitmapDescriptor icon;
            String title;
            float anchorV = 0.5f;
            int zIndex;
                
            if (i == 0) {
                // 优化2：起点标记 - 增大尺寸，增强视觉差异
                icon = MapMarkerHelper.createStartEndMarker("起点", Color.parseColor("#4CAF50"));
                title = "起点";
                anchorV = 0.95f;
                zIndex = 200; // 优化4：起点层级最高
            } else if (i == stayPoints.size() - 1) {
                // 优化2：终点标记 - 增大尺寸，增强视觉差异
                icon = MapMarkerHelper.createStartEndMarker("终点", Color.parseColor("#F44336"));
                title = "终点";
                anchorV = 0.95f;
                zIndex = 200; // 优化4：终点层级最高
            } else {
                // 普通点标记 - 显示连续编号
                icon = MapMarkerHelper.createNumberedMarker(i); // 使用索引i作为编号
                title = String.valueOf(i);
                zIndex = 100; // 普通点层级
            }
                
            StringBuilder snippetBuilder = new StringBuilder();
            snippetBuilder.append(TimeFormatter.formatShortTime(stayPoint.getArriveTime()));
                
            MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(snippetBuilder.toString())
                .icon(icon)
                .anchor(0.5f, anchorV)
                .zIndex(zIndex); // 优化4：设置不同层级
                
            Marker marker = aMap.addMarker(markerOptions);
            marker.setVisible(true);
            positionMarkers.add(marker);
        }
    }
    
    /**
     * 判断是否为今天
     */
    private static boolean isToday(List<StayPoint> stayPoints) {
        if (stayPoints == null || stayPoints.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long dayStart = now - (now % 86400000L);
        return stayPoints.get(0).getArriveTime() >= dayStart;
    }
    
    /**
     * 调整相机视角
     * @param aMap 高德地图实例
     * @param latLngList 轨迹点列表
     * @param preserveZoom 是否保持当前缩放级别
     * @param currentZoom 当前缩放级别（如果preserveZoom为true）
     */
    public static void adjustCameraOnAMap(AMap aMap, List<LatLng> latLngList, boolean preserveZoom, float currentZoom) {
        if (!latLngList.isEmpty()) {
            if (latLngList.size() == 1) {
                float zoomToUse = preserveZoom ? currentZoom : 17.0f;
                aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(
                    latLngList.get(0), zoomToUse));
            } else {
                if (preserveZoom) {
                    // 保持当前缩放级别，只移动中心点
                    com.amap.api.maps.model.LatLngBounds.Builder builder = 
                        new com.amap.api.maps.model.LatLngBounds.Builder();
                    for (LatLng latLng : latLngList) {
                        builder.include(latLng);
                    }
                    com.amap.api.maps.model.LatLngBounds bounds = builder.build();
                    // 使用新位置但保持当前缩放级别
                    com.amap.api.maps.model.CameraPosition cameraPosition = aMap.getCameraPosition();
                    float zoomToUse = currentZoom > 0 ? currentZoom : (cameraPosition != null ? cameraPosition.zoom : 17.0f);
                    
                    // 计算边界中心点
                    double centerLat = (bounds.northeast.latitude + bounds.southwest.latitude) / 2;
                    double centerLng = (bounds.northeast.longitude + bounds.southwest.longitude) / 2;
                    LatLng center = new LatLng(centerLat, centerLng);
                    
                    aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(center, zoomToUse));
                } else {
                    // 自动调整缩放级别以显示所有点
                    com.amap.api.maps.model.LatLngBounds.Builder builder = 
                        new com.amap.api.maps.model.LatLngBounds.Builder();
                    for (LatLng latLng : latLngList) {
                        builder.include(latLng);
                    }
                    com.amap.api.maps.model.LatLngBounds bounds = builder.build();
                    aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngBounds(bounds, 100));
                }
            }
        }
    }
    
    /**
     * 计算方位角
     */
    private static double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLngRad = Math.toRadians(lng2 - lng1);
        
        double y = Math.sin(deltaLngRad) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - 
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLngRad);
        
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }
}
