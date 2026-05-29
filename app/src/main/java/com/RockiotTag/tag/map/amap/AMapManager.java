package com.RockiotTag.tag.map.amap;

import android.content.Context;
import android.util.Log;
import android.view.View;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;

/**
 * 高德地图管理器（国内版专用）
 * 完全独立的高德地图实现，不依赖任何谷歌地图代码
 */
public class AMapManager {
    private static final String TAG = "AMapManager";
    
    private Context context;
    private MapView mapView;
    private AMap aMap;
    private MapCallback callback;
    
    public interface MapCallback {
        void onMapReady();
        void onMapClick(double latitude, double longitude);
    }
    
    public AMapManager(Context context, MapView mapView) {
        this.context = context;
        this.mapView = mapView;
        Log.d(TAG, "AMapManager initialized for domestic version");
    }
    
    public void setCallback(MapCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 初始化高德地图
     */
    public void initMap() {
        Log.d(TAG, "Initializing AMap...");
        if (mapView != null) {
            aMap = mapView.getMap();
            if (aMap != null) {
                configureMap();
                Log.d(TAG, "AMap initialized successfully");
                
                if (callback != null) {
                    callback.onMapReady();
                }
            } else {
                Log.e(TAG, "Failed to get AMap instance");
            }
        }
    }
    
    /**
     * 配置地图UI设置
     */
    private void configureMap() {
        aMap.getUiSettings().setMyLocationButtonEnabled(false);
        aMap.setMyLocationEnabled(false);
        aMap.getUiSettings().setCompassEnabled(true);
        aMap.getUiSettings().setScaleControlsEnabled(true);
        aMap.getUiSettings().setZoomControlsEnabled(true);
        aMap.moveCamera(CameraUpdateFactory.zoomTo(17));
        
        // 设置地图点击监听
        aMap.setOnMapClickListener(new AMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                if (callback != null) {
                    callback.onMapClick(latLng.latitude, latLng.longitude);
                }
            }
        });
    }
    
    /**
     * 移动相机到指定位置
     * @param latitude 纬度（WGS84）
     * @param longitude 经度（WGS84）
     * @param zoom 缩放级别
     */
    public void moveCamera(double latitude, double longitude, float zoom) {
        if (aMap != null) {
            // 将WGS84坐标转换为GCJ02坐标（高德使用）
            LatLng gcj02LatLng = com.RockiotTag.tag.CoordinateUtils.wgs84ToGcj02(latitude, longitude);
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(gcj02LatLng, zoom));
        }
    }
    
    /**
     * 添加标记点
     */
    public Marker addMarker(double latitude, double longitude, String title, String snippet) {
        if (aMap != null) {
            LatLng gcj02LatLng = com.RockiotTag.tag.CoordinateUtils.wgs84ToGcj02(latitude, longitude);
            MarkerOptions options = new MarkerOptions()
                .position(gcj02LatLng)
                .title(title)
                .snippet(snippet);
            return aMap.addMarker(options);
        }
        return null;
    }
    
    /**
     * 添加自定义图标标记
     */
    public Marker addMarkerWithIcon(double latitude, double longitude, String title, 
                                    com.amap.api.maps.model.BitmapDescriptor icon) {
        if (aMap != null) {
            LatLng gcj02LatLng = com.RockiotTag.tag.CoordinateUtils.wgs84ToGcj02(latitude, longitude);
            MarkerOptions options = new MarkerOptions()
                .position(gcj02LatLng)
                .title(title)
                .icon(icon);
            return aMap.addMarker(options);
        }
        return null;
    }
    
    /**
     * 绘制轨迹线
     */
    public Polyline drawPolyline(java.util.List<LatLng> points, int color, float width) {
        if (aMap != null && points != null && !points.isEmpty()) {
            PolylineOptions options = new PolylineOptions()
                .addAll(points)
                .color(color)
                .width(width);
            return aMap.addPolyline(options);
        }
        return null;
    }
    
    /**
     * 清除地图上的所有标记和线条
     */
    public void clearMap() {
        if (aMap != null) {
            aMap.clear();
        }
    }
    
    /**
     * 设置地图类型
     * @param type AMap.MAP_TYPE_NORMAL 或 AMap.MAP_TYPE_SATELLITE
     */
    public void setMapType(int type) {
        if (aMap != null) {
            aMap.setMapType(type);
        }
    }
    
    /**
     * 获取当前缩放级别
     */
    public float getZoomLevel() {
        if (aMap != null) {
            return aMap.getCameraPosition().zoom;
        }
        return 17;
    }
    
    /**
     * 获取AMap实例
     */
    public AMap getAMap() {
        return aMap;
    }
    
    /**
     * 显示地图视图
     */
    public void showMap() {
        if (mapView != null) {
            mapView.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 隐藏地图视图
     */
    public void hideMap() {
        if (mapView != null) {
            mapView.setVisibility(View.GONE);
        }
    }
    
    /**
     * 释放资源
     */
    public void onDestroy() {
        if (mapView != null) {
            mapView.onDestroy();
        }
    }
    
    /**
     * 恢复地图状态
     */
    public void onResume() {
        if (mapView != null) {
            mapView.onResume();
        }
    }
    
    /**
     * 暂停地图
     */
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
    }
    
    /**
     * 保存地图状态
     */
    public void onSaveInstanceState(android.os.Bundle outState) {
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }
}
