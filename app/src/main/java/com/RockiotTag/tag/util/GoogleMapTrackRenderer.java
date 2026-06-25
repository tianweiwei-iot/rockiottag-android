package com.RockiotTag.tag.util;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.StayPoint;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Google 地图轨迹渲染器
 * 职责：统一处理 Google 地图上的轨迹渲染逻辑
 */
public class GoogleMapTrackRenderer {
    private static final String TAG = "GoogleMapTrackRenderer";
    
    /**
     * 在 Google 地图上绘制轨迹线
     * @param googleMap Google 地图实例
     * @param stayPoints 停留点列表
     * @return 轨迹线对象
     */
    public static Polyline drawTrackPolyline(GoogleMap googleMap, List<StayPoint> stayPoints) {
        if (googleMap == null || stayPoints == null || stayPoints.size() < 2) {
            return null;
        }
        
        List<LatLng> latLngList = new ArrayList<>();
        for (StayPoint stayPoint : stayPoints) {
            latLngList.add(new LatLng(stayPoint.getLatitude(), stayPoint.getLongitude()));
        }
        
        PolylineOptions polylineOptions = new PolylineOptions()
            .addAll(latLngList)
            .color(0xFF0088FF)
            .width(12f)
            .clickable(false);
        
        return googleMap.addPolyline(polylineOptions);
    }
    
    /**
     * 在 Google 地图上添加方向箭头
     * @param googleMap Google 地图实例
     * @param latLngList 坐标点列表
     * @return 箭头标记列表
     */
    public static List<Marker> addDirectionArrows(GoogleMap googleMap, List<LatLng> latLngList) {
        if (googleMap == null || latLngList == null || latLngList.size() < 2) {
            return new ArrayList<>();
        }
        
        List<Marker> arrowMarkers = new ArrayList<>();
        int arrowInterval = 5; // 每 5 个点显示一个箭头
        
        for (int i = 0; i < latLngList.size() - 1; i += arrowInterval) {
            LatLng p1 = latLngList.get(i);
            LatLng p2 = latLngList.get(Math.min(i + 1, latLngList.size() - 1));
            
            double angle = calculateBearing(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
            
            MarkerOptions arrowMarker = new MarkerOptions()
                .position(new LatLng(p1.latitude, p1.longitude))
                .icon(GoogleMapMarkerHelper.createArrowMarker(angle))
                .anchor(0.5f, 0.5f)
                .zIndex(1);
            
            Marker marker = googleMap.addMarker(arrowMarker);
            arrowMarkers.add(marker);
        }
        
        LogUtil.d(TAG, "Added direction arrows on Google Map (interval: " + arrowInterval + ")");
        return arrowMarkers;
    }
    
    /**
     * 计算两点之间的方位角
     * @param lat1 起点纬度
     * @param lng1 起点经度
     * @param lat2 终点纬度
     * @param lng2 终点经度
     * @return 方位角（0-360度）
     */
    public static double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLngRad = Math.toRadians(lng2 - lng1);
        
        double y = Math.sin(deltaLngRad) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - 
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLngRad);
        
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }
    
    /**
     * 创建起点/终点标记选项（优化2和4：增大尺寸，增强视觉差异，设置更高层级）
     */
    public static MarkerOptions createStartEndMarkerOption(Context context, StayPoint stayPoint, 
                                                           boolean isStart, boolean isEnd) {
        LatLng latLng = new LatLng(stayPoint.getLatitude(), stayPoint.getLongitude());
        
        String title;
        BitmapDescriptor icon;
        float anchorV = 0.95f;
        int zIndex = 200; // 优化4：起点终点层级最高
        
        if (isStart) {
            title = context.getString(R.string.start_point);
            icon = GoogleMapMarkerHelper.createStartEndMarker(title, Color.parseColor("#4CAF50"));
        } else {
            title = context.getString(R.string.end_point);
            icon = GoogleMapMarkerHelper.createStartEndMarker(title, Color.parseColor("#F44336"));
        }
        
        String snippet = TimeFormatter.formatShortTime(stayPoint.getArriveTime());
        
        return new MarkerOptions()
            .position(latLng)
            .title(title)
            .snippet(snippet)
            .icon(icon)
            .anchor(0.5f, anchorV)
            .zIndex(zIndex); // 优化4：设置高层级
    }
    
    /**
     * 创建普通点标记选项（优化1：缩小尺寸）
     * @param context 上下文
     * @param stayPoint 停留点
     * @param index 索引
     * @return MarkerOptions
     */
    public static MarkerOptions createNormalMarkerOption(Context context, StayPoint stayPoint, int index) {
        LatLng latLng = new LatLng(stayPoint.getLatitude(), stayPoint.getLongitude());
        
        String title = String.valueOf(stayPoint.getOriginalIndex());
        BitmapDescriptor icon;
        int zIndex = 100; // 优化4：普通点层级
        
        if (stayPoint.isStayPoint()) {
            icon = GoogleMapMarkerHelper.createStayPointMarker(
                stayPoint.getOriginalIndex(), 
                stayPoint.getStayDurationFormatted()
            );
        } else {
            icon = GoogleMapMarkerHelper.createNumberedMarker(stayPoint.getOriginalIndex());
        }
        
        StringBuilder snippetBuilder = new StringBuilder();
        snippetBuilder.append(TimeFormatter.formatShortTime(stayPoint.getArriveTime()));
        if (stayPoint.getMergedCount() > 1) {
            snippetBuilder.append(" - ").append(TimeFormatter.formatShortTime(stayPoint.getLeaveTime()));
        }
        if (stayPoint.isStayPoint()) {
            snippetBuilder.append("\n").append(context.getString(R.string.stay_duration, 
                stayPoint.getStayDurationFormatted()));
        }
        if (stayPoint.getMergedCount() > 1) {
            snippetBuilder.append("\n").append(context.getString(R.string.merged_points, 
                stayPoint.getMergedCount()));
        }
        
        return new MarkerOptions()
            .position(latLng)
            .title(title)
            .snippet(snippetBuilder.toString())
            .icon(icon)
            .anchor(0.5f, 0.5f)
            .zIndex(zIndex); // 优化4：设置普通层级
    }
    
    /**
     * 判断是否为今天
     * @param selectedDate 选中的日期
     * @return true 如果是今天
     */
    public static boolean isToday(Calendar selectedDate) {
        Calendar today = Calendar.getInstance();
        return today.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
               today.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH) &&
               today.get(Calendar.DAY_OF_MONTH) == selectedDate.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Google 地图完整轨迹渲染结果
     */
    public static class GoogleTrackRenderResult {
        public Polyline trackPolyline;
        public final List<Marker> positionMarkers = new ArrayList<>();
        public final List<Marker> arrowMarkers = new ArrayList<>();
        public final List<LatLng> validLatLngList = new ArrayList<>();
    }

    /**
     * 在 Google 地图上渲染完整轨迹（线、箭头、起终点标记、相机）
     */
    public static GoogleTrackRenderResult renderTrackOnGoogleMap(
            Context context,
            GoogleMap googleMap,
            List<StayPoint> stayPoints,
            Calendar selectedDate,
            boolean showPolyline,
            int accuracyThreshold,
            float zoomToPreserve,
            boolean shouldPreserveZoom) {

        GoogleTrackRenderResult result = new GoogleTrackRenderResult();
        if (googleMap == null || stayPoints == null || stayPoints.isEmpty()) {
            return result;
        }

        int filteredInvalidCount = 0;
        for (StayPoint stayPoint : stayPoints) {
            if (Math.abs(stayPoint.getLatitude()) < 0.0001 && Math.abs(stayPoint.getLongitude()) < 0.0001) {
                filteredInvalidCount++;
                continue;
            }
            result.validLatLngList.add(
                    new LatLng(stayPoint.getLatitude(), stayPoint.getLongitude()));
        }
        if (filteredInvalidCount > 0) {
            LogUtil.d(TAG, "Filtered " + filteredInvalidCount + " invalid coordinates from Google render");
        }

        if (result.validLatLngList.size() > 1) {
            Polyline polyline = drawTrackPolyline(googleMap, stayPoints);
            if (polyline != null) {
                polyline.setVisible(showPolyline);
                result.trackPolyline = polyline;
            }
            result.arrowMarkers.addAll(addDirectionArrows(googleMap, result.validLatLngList));
        }

        boolean isToday = isToday(selectedDate);
        for (int i = 0; i < stayPoints.size(); i++) {
            StayPoint stayPoint = stayPoints.get(i);
            if (stayPoints.size() == 1) {
                MarkerOptions options = createStartEndMarkerOption(
                        context, stayPoint, isToday, !isToday);
                Marker marker = googleMap.addMarker(options);
                result.positionMarkers.add(marker);
                break;
            }
            MarkerOptions options;
            if (i == 0) {
                options = createStartEndMarkerOption(context, stayPoint, true, false);
            } else if (i == stayPoints.size() - 1) {
                options = createStartEndMarkerOption(context, stayPoint, false, true);
            } else {
                options = createNormalMarkerOption(context, stayPoint, i);
            }
            result.positionMarkers.add(googleMap.addMarker(options));
        }

        if (!result.validLatLngList.isEmpty()) {
            float zoomToUse = shouldPreserveZoom ? zoomToPreserve
                    : calculateZoomForAccuracy(accuracyThreshold);
            LatLng firstPoint = result.validLatLngList.get(0);
            try {
                googleMap.animateCamera(
                        com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(firstPoint, zoomToUse));
            } catch (Exception e) {
                Log.e(TAG, "Error animating Google Map camera: " + e.getMessage(), e);
            }
        }
        return result;
    }

    public static float calculateZoomForAccuracy(int accuracyThreshold) {
        if (accuracyThreshold <= 0) {
            return 17.0f;
        }
        double diameterMeters = accuracyThreshold * 2.0;
        double zoom = 17.0 - Math.log(diameterMeters / 300.0) / Math.log(2.0);
        zoom = Math.max(3.0, Math.min(20.0, zoom));
        return (float) zoom;
    }
}
