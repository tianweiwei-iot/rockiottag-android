package com.RockiotTag.tag.manager;

import android.content.Context;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.util.GoogleMapTrackRenderer;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹地图控制器
 * 职责：统一管理 Google 地图上的轨迹显示和播放
 */
public class TrackMapController {
    private static final String TAG = "TrackMapController";
    
    private GoogleMap googleMap;
    private List<StayPoint> stayPoints;
    
    // 轨迹元素
    private Polyline trackPolyline;
    private List<Marker> positionMarkers = new ArrayList<>();
    private List<Marker> arrowMarkers = new ArrayList<>();
    
    // 播放相关
    private Marker playMarker;
    private Polyline playedPolyline;
    private List<LatLng> playedPoints = new ArrayList<>();
    
    public TrackMapController(GoogleMap googleMap, List<StayPoint> stayPoints) {
        this.googleMap = googleMap;
        this.stayPoints = stayPoints;
    }
    
    /**
     * 渲染完整轨迹
     */
    public void renderTrack() {
        clearTrack();
        
        if (stayPoints == null || stayPoints.isEmpty()) {
            return;
        }
        
        // 绘制轨迹线
        List<LatLng> latLngList = new ArrayList<>();
        for (StayPoint stayPoint : stayPoints) {
            latLngList.add(new LatLng(stayPoint.getLatitude(), stayPoint.getLongitude()));
        }
        
        if (latLngList.size() > 1) {
            trackPolyline = GoogleMapTrackRenderer.drawTrackPolyline(googleMap, stayPoints);
            if (trackPolyline != null) {
                trackPolyline.setVisible(true);
            }
            
            arrowMarkers = GoogleMapTrackRenderer.addDirectionArrows(googleMap, latLngList);
        }
        
        LogUtil.d(TAG, "Track rendered with " + stayPoints.size() + " points");
    }
    
    /**
     * 初始化播放标记
     */
    public void initPlaybackMarker() {
        if (stayPoints == null || stayPoints.isEmpty()) {
            return;
        }
        
        StayPoint firstPoint = stayPoints.get(0);
        LatLng startPos = new LatLng(firstPoint.getLatitude(), firstPoint.getLongitude());
        
        playMarker = googleMap.addMarker(
            new com.google.android.gms.maps.model.MarkerOptions()
                .position(startPos)
                .title("播放位置")
                .icon(com.RockiotTag.tag.util.GoogleMapMarkerHelper.createCustomMarkerWithR())
                .anchor(0.5f, 0.5f)
        );
        
        playedPoints.clear();
        playedPoints.add(startPos);
        
        LogUtil.d(TAG, "Playback marker initialized");
    }
    
    /**
     * 更新播放位置
     * @param currentIndex 当前索引
     * @param currentPoint 当前停留点
     */
    public void updatePlaybackPosition(int currentIndex, StayPoint currentPoint) {
        if (playMarker == null || currentPoint == null) {
            return;
        }
        
        LatLng newPos = new LatLng(currentPoint.getLatitude(), currentPoint.getLongitude());
        playMarker.setPosition(newPos);
        
        playedPoints.add(newPos);
        updatePlayedPolyline();
        
        LogUtil.d(TAG, "Playback position updated to index " + currentIndex);
    }
    
    /**
     * 更新已播放轨迹线
     */
    private void updatePlayedPolyline() {
        if (playedPoints.size() > 1) {
            if (playedPolyline == null) {
                com.google.android.gms.maps.model.PolylineOptions polylineOptions = 
                    new com.google.android.gms.maps.model.PolylineOptions()
                        .addAll(playedPoints)
                        .color(0xFFFF5722)
                        .width(12f);
                playedPolyline = googleMap.addPolyline(polylineOptions);
            } else {
                playedPolyline.setPoints(playedPoints);
            }
        }
    }
    
    /**
     * 清空轨迹
     */
    public void clearTrack() {
        // 清除标记
        for (Marker marker : positionMarkers) {
            marker.remove();
        }
        positionMarkers.clear();
        
        for (Marker marker : arrowMarkers) {
            marker.remove();
        }
        arrowMarkers.clear();
        
        // 清除轨迹线
        if (trackPolyline != null) {
            trackPolyline.remove();
            trackPolyline = null;
        }
        
        // 清除播放相关
        if (playedPolyline != null) {
            playedPolyline.remove();
            playedPolyline = null;
        }
        if (playMarker != null) {
            playMarker.remove();
            playMarker = null;
        }
        playedPoints.clear();
        
        LogUtil.d(TAG, "Track cleared");
    }
    
    /**
     * 释放资源
     */
    public void release() {
        clearTrack();
        googleMap = null;
        stayPoints = null;
    }
}
