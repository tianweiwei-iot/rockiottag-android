package com.RockiotTag.tag.map.google;

import android.content.Context;
import android.util.Log;
import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

/**
 * 谷歌地图管理器（国际版专用）
 * 完全独立的谷歌地图实现，不依赖任何高德地图代码
 */
public class GoogleMapManager implements OnMapReadyCallback {
    private static final String TAG = "GoogleMapManager";
    
    private Context context;
    private SupportMapFragment mapFragment;
    private GoogleMap googleMap;
    private MapCallback callback;
    private boolean isMapReady = false;
    
    public interface MapCallback {
        void onMapReady();
        void onMapClick(double latitude, double longitude);
    }
    
    public GoogleMapManager(Context context, SupportMapFragment mapFragment) {
        this.context = context;
        this.mapFragment = mapFragment;
        Log.d(TAG, "GoogleMapManager initialized for international version");
    }
    
    public void setCallback(MapCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 初始化谷歌地图
     */
    public void initMap() {
        Log.d(TAG, "Initializing Google Map...");
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.e(TAG, "MapFragment is null");
        }
    }
    
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "Google Map is ready");
        this.googleMap = googleMap;
        this.isMapReady = true;
        
        if (googleMap != null) {
            configureMap();
            
            if (callback != null) {
                callback.onMapReady();
            }
        }
    }
    
    /**
     * 配置地图UI设置
     */
    private void configureMap() {
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setRotateGesturesEnabled(true);
        googleMap.getUiSettings().setTiltGesturesEnabled(true);
        
        // 完全禁用初始相机移动，让用户完全控制地图位置
        Log.d(TAG, "Google Map - Initial camera move COMPLETELY DISABLED in GoogleMapManager");
        // LatLng defaultLocation = new LatLng(22.543611, 113.881944);
        // googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 17));
        
        // 设置地图点击监听
        googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
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
        // 完全禁用谷歌地图的自动相机移动
        Log.d(TAG, "Google Map - moveCamera COMPLETELY DISABLED in GoogleMapManager. Requested: " + latitude + ", " + longitude);
        // 不再移动相机，让用户完全控制地图位置
        /*
        if (googleMap != null) {
            // 谷歌地图直接使用WGS84坐标，无需转换
            LatLng latLng = new LatLng(latitude, longitude);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        }
        */
    }
    
    /**
     * 添加标记点
     */
    public Marker addMarker(double latitude, double longitude, String title, String snippet) {
        if (googleMap != null) {
            LatLng latLng = new LatLng(latitude, longitude);
            MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(snippet);
            return googleMap.addMarker(options);
        }
        return null;
    }
    
    /**
     * 添加自定义图标标记
     */
    public Marker addMarkerWithIcon(double latitude, double longitude, String title, 
                                    com.google.android.gms.maps.model.BitmapDescriptor icon) {
        if (googleMap != null) {
            LatLng latLng = new LatLng(latitude, longitude);
            MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .title(title)
                .icon(icon);
            return googleMap.addMarker(options);
        }
        return null;
    }
    
    /**
     * 绘制轨迹线
     */
    public Polyline drawPolyline(java.util.List<LatLng> points, int color, float width) {
        if (googleMap != null && points != null && !points.isEmpty()) {
            PolylineOptions options = new PolylineOptions()
                .addAll(points)
                .color(color)
                .width(width);
            return googleMap.addPolyline(options);
        }
        return null;
    }
    
    /**
     * 清除地图上的所有标记和线条
     */
    public void clearMap() {
        if (googleMap != null) {
            googleMap.clear();
        }
    }
    
    /**
     * 设置地图类型
     * @param type GoogleMap.MAP_TYPE_NORMAL 或 GoogleMap.MAP_TYPE_SATELLITE
     */
    public void setMapType(int type) {
        if (googleMap != null) {
            googleMap.setMapType(type);
        }
    }
    
    /**
     * 获取当前缩放级别
     */
    public float getZoomLevel() {
        if (googleMap != null) {
            return googleMap.getCameraPosition().zoom;
        }
        return 17;
    }
    
    /**
     * 获取GoogleMap实例
     */
    public GoogleMap getGoogleMap() {
        return googleMap;
    }
    
    /**
     * 检查地图是否已准备好
     */
    public boolean isMapReady() {
        return isMapReady && googleMap != null;
    }
    
    /**
     * 显示地图视图
     */
    public void showMap() {
        if (mapFragment != null && mapFragment.getView() != null) {
            mapFragment.getView().setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 隐藏地图视图
     */
    public void hideMap() {
        if (mapFragment != null && mapFragment.getView() != null) {
            mapFragment.getView().setVisibility(View.GONE);
        }
    }
}
