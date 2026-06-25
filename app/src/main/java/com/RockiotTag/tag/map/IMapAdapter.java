package com.RockiotTag.tag.map;

/**
 * 地图适配器统一接口
 * 抽象高德地图和 Google 地图的共同操作，消除 Activity 中的双份变量
 * <p>
 * AMapManager 和 GoogleMapManager 实现此接口
 * Activity/Fragment 面向此接口编程，通过 MapAdapterFactory 获取实例
 */
public interface IMapAdapter {

    /**
     * 地图回调接口
     */
    interface MapCallback {
        void onMapReady();
        void onMapClick(double latitude, double longitude);
    }

    /**
     * 初始化地图
     */
    void initMap();

    /**
     * 设置回调
     */
    void setCallback(MapCallback callback);

    /**
     * 地图是否就绪
     */
    boolean isMapReady();

    /**
     * 移动相机到指定位置
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @param zoom      缩放级别
     */
    void moveCamera(double latitude, double longitude, float zoom);

    /**
     * 清除地图上的所有标记和覆盖物
     */
    void clearMap();

    /**
     * 设置地图类型
     *
     * @param type 地图类型（GoogleMap.MAP_TYPE_NORMAL 等）
     */
    void setMapType(int type);

    /**
     * 设置深色地图样式
     */
    void setDarkMapStyle(boolean isDarkMode);

    /**
     * 获取当前缩放级别
     */
    float getZoomLevel();

    /**
     * 添加标记
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @param title     标题
     * @param snippet   副标题
     * @return 标记对象（高德 Marker 或谷歌 Marker，调用方按需 cast）
     */
    Object addMarker(double latitude, double longitude, String title, String snippet);

    /**
     * 添加带图标的标记
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @param title     标题
     * @param snippet   副标题
     * @param iconResId 图标资源 ID
     * @return 标记对象
     */
    Object addMarkerWithIcon(double latitude, double longitude, String title, String snippet, int iconResId);

    /**
     * 绘制轨迹线
     *
     * @param points 坐标点列表，每个 double[] 长度为 2，[0]=纬度, [1]=经度
     * @param color  颜色
     * @param width   线宽
     * @return 折线对象（高德 Polyline 或谷歌 Polyline，调用方按需 cast）
     */
    Object drawPolyline(java.util.List<double[]> points, int color, float width);

    /**
     * 更新折线的坐标点（用于轨迹播放动画）
     *
     * @param polyline 折线对象（由 drawPolyline 返回）
     * @param points   新的坐标点列表
     */
    void updatePolylinePoints(Object polyline, java.util.List<double[]> points);

    /**
     * 移除地图上的对象（标记或折线）
     *
     * @param obj 由 addMarker / drawPolyline 返回的对象
     */
    void removeObject(Object obj);

    /**
     * 设置标记位置（用于轨迹播放动画）
     *
     * @param marker    标记对象
     * @param latitude  纬度
     * @param longitude 经度
     */
    void setMarkerPosition(Object marker, double latitude, double longitude);

    /**
     * 设置标记可见性
     *
     * @param marker  标记对象
     * @param visible 是否可见
     */
    void setMarkerVisible(Object marker, boolean visible);

    /**
     * 设置标记旋转角度
     *
     * @param marker     标记对象
     * @param rotation   旋转角度（度）
     */
    void setMarkerRotation(Object marker, float rotation);

    /**
     * 动画移动相机到指定位置
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @param zoom      缩放级别
     */
    void animateCamera(double latitude, double longitude, float zoom);

    /**
     * 显示地图
     */
    void showMap();

    /**
     * 隐藏地图
     */
    void hideMap();

    /**
     * 获取当前地图提供商标识
     *
     * @return "amap" 或 "google"
     */
    String getProvider();

    /**
     * 生命周期回调
     */
    void onResume();
    void onPause();
    void onDestroy();
}
