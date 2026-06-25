package com.RockiotTag.tag.map.amap;

import android.content.Context;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;
import android.view.View;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * 高德地图管理器（国内版专用）
 * 完全独立的高德地图实现，不依赖任何谷歌地图代码
 */
public class AMapManager implements com.RockiotTag.tag.map.IMapAdapter {
    private static final String TAG = "AMapManager";

    private Context context;
    private MapView mapView;
    private AMap aMap;
    private com.RockiotTag.tag.map.IMapAdapter.MapCallback callback;

    public AMapManager(Context context, MapView mapView) {
        this.context = context;
        this.mapView = mapView;
        LogUtil.d(TAG, "AMapManager initialized for domestic version");
    }

    @Override
    public void setCallback(com.RockiotTag.tag.map.IMapAdapter.MapCallback callback) {
        this.callback = callback;
    }

    /**
     * 初始化高德地图
     */
    @Override
    public void initMap() {
        LogUtil.d(TAG, "Initializing AMap...");
        if (mapView != null) {
            aMap = mapView.getMap();
            if (aMap != null) {
                configureMap();
                LogUtil.d(TAG, "AMap initialized successfully");

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
    @Override
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
    @Override
    public Object addMarker(double latitude, double longitude, String title, String snippet) {
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
     * 添加带图标资源的标记（IMapAdapter 接口实现）
     */
    @Override
    public Object addMarkerWithIcon(double latitude, double longitude, String title, String snippet, int iconResId) {
        if (aMap != null) {
            LatLng gcj02LatLng = com.RockiotTag.tag.CoordinateUtils.wgs84ToGcj02(latitude, longitude);
            com.amap.api.maps.model.BitmapDescriptor icon = com.amap.api.maps.model.BitmapDescriptorFactory.fromResource(iconResId);
            MarkerOptions options = new MarkerOptions()
                .position(gcj02LatLng)
                .title(title)
                .snippet(snippet)
                .icon(icon);
            return aMap.addMarker(options);
        }
        return null;
    }

    /**
     * 更新折线坐标点（用于轨迹播放动画）
     */
    @Override
    public void updatePolylinePoints(Object polyline, List<double[]> points) {
        if (polyline instanceof Polyline && points != null) {
            List<LatLng> amapPoints = new ArrayList<>();
            for (double[] point : points) {
                amapPoints.add(new LatLng(point[0], point[1]));
            }
            ((Polyline) polyline).setPoints(amapPoints);
        }
    }

    /**
     * 移除地图上的对象（标记或折线）
     */
    @Override
    public void removeObject(Object obj) {
        if (obj instanceof Marker) {
            ((Marker) obj).remove();
        } else if (obj instanceof Polyline) {
            ((Polyline) obj).remove();
        }
    }

    /**
     * 设置标记位置
     */
    @Override
    public void setMarkerPosition(Object marker, double latitude, double longitude) {
        if (marker instanceof Marker) {
            LatLng gcj02LatLng = com.RockiotTag.tag.CoordinateUtils.wgs84ToGcj02(latitude, longitude);
            ((Marker) marker).setPosition(gcj02LatLng);
        }
    }

    /**
     * 设置标记可见性
     */
    @Override
    public void setMarkerVisible(Object marker, boolean visible) {
        if (marker instanceof Marker) {
            ((Marker) marker).setVisible(visible);
        }
    }

    /**
     * 设置标记旋转角度
     */
    @Override
    public void setMarkerRotation(Object marker, float rotation) {
        if (marker instanceof Marker) {
            ((Marker) marker).setRotateAngle(rotation);
        }
    }

    /**
     * 动画移动相机
     */
    @Override
    public void animateCamera(double latitude, double longitude, float zoom) {
        if (aMap != null) {
            LatLng gcj02LatLng = com.RockiotTag.tag.CoordinateUtils.wgs84ToGcj02(latitude, longitude);
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(gcj02LatLng, zoom));
        }
    }

    /**
     * 绘制轨迹线
     */
    @Override
    public Object drawPolyline(List<double[]> points, int color, float width) {
        if (aMap != null && points != null && !points.isEmpty()) {
            List<LatLng> amapPoints = new ArrayList<>();
            for (double[] point : points) {
                amapPoints.add(new LatLng(point[0], point[1]));
            }
            PolylineOptions options = new PolylineOptions()
                .addAll(amapPoints)
                .color(color)
                .width(width);
            return aMap.addPolyline(options);
        }
        return null;
    }

    /**
     * 清除地图上的所有标记和线条
     */
    @Override
    public void clearMap() {
        if (aMap != null) {
            aMap.clear();
        }
    }

    /**
     * 设置地图类型
     * @param type AMap.MAP_TYPE_NORMAL 或 AMap.MAP_TYPE_SATELLITE
     */
    @Override
    public void setMapType(int type) {
        if (aMap != null) {
            aMap.setMapType(type);
        }
    }

    /**
     * 设置深色地图样式
     * @param isDarkMode 是否深色模式
     */
    @Override
    public void setDarkMapStyle(boolean isDarkMode) {
        if (aMap != null) {
            if (isDarkMode) {
                // 使用导航地图样式（深色风格，蓝黑配色）
                aMap.setMapType(com.amap.api.maps.AMap.MAP_TYPE_NIGHT);
            } else {
                // 恢复普通地图样式
                aMap.setMapType(com.amap.api.maps.AMap.MAP_TYPE_NORMAL);
            }
        }
    }

    /**
     * 获取当前缩放级别
     */
    @Override
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
     * 地图是否就绪
     */
    @Override
    public boolean isMapReady() {
        return aMap != null;
    }

    /**
     * 获取当前地图提供商标识
     */
    @Override
    public String getProvider() {
        return "amap";
    }

    /**
     * 显示地图视图
     */
    @Override
    public void showMap() {
        if (mapView != null) {
            mapView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 隐藏地图视图
     */
    @Override
    public void hideMap() {
        if (mapView != null) {
            mapView.setVisibility(View.GONE);
        }
    }

    /**
     * 释放资源
     * MapView 生命周期由 MainActivity 统一管理，此处仅释放 AMap 引用。
     */
    @Override
    public void onDestroy() {
        // mapView 不在此销毁，避免与 MainActivity 重复 onDestroy 导致多次切换语言后崩溃
    }

    /**
     * 恢复地图状态
     */
    @Override
    public void onResume() {
        // mapView.onResume 由 MainActivity 调用
    }

    /**
     * 暂停地图
     */
    @Override
    public void onPause() {
        // mapView.onPause 由 MainActivity 调用
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
