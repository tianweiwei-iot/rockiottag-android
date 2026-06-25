package com.RockiotTag.tag.map.google;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.RockiotTag.tag.util.LogUtil;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * 谷歌地图管理器（国际版专用）
 * 完全独立的谷歌地图实现，不依赖任何高德地图代码
 */
public class GoogleMapManager implements OnMapReadyCallback, com.RockiotTag.tag.map.IMapAdapter {
    private static final String TAG = "GoogleMapManager";
    
    private Context context;
    private SupportMapFragment mapFragment;
    private GoogleMap googleMap;
    private com.RockiotTag.tag.map.IMapAdapter.MapCallback callback;
    private boolean isMapReady = false;
    private boolean mapAsyncSubmitted = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int LAYOUT_WAIT_MAX_RETRIES = 30;
    private static final long LAYOUT_WAIT_MS = 100L;

    public GoogleMapManager(Context context, SupportMapFragment mapFragment) {
        this.context = context;
        this.mapFragment = mapFragment;
        LogUtil.d(TAG, "GoogleMapManager initialized for international version");
    }
    
    @Override
    public void setCallback(com.RockiotTag.tag.map.IMapAdapter.MapCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 初始化谷歌地图
     */
    @Override
    public void initMap() {
        LogUtil.d(TAG, "Initializing Google Map...");
        mapAsyncSubmitted = false;
        if (mapFragment == null) {
            Log.e(TAG, "MapFragment is null");
            return;
        }
        waitForFragmentLayoutThenGetMap(0);
    }

    /**
     * getMapAsync 必须在 Fragment View 完成 layout（宽高 &gt; 0）后调用，
     * 否则切换高德→Google 时 View 仍为 0x0，地图会一直灰屏。
     */
    private void waitForFragmentLayoutThenGetMap(int attempt) {
        if (mapFragment == null) {
            return;
        }
        View fragmentView = mapFragment.getView();
        if (fragmentView != null && fragmentView.getWidth() > 0 && fragmentView.getHeight() > 0) {
            submitGetMapAsync("layout-ready");
            return;
        }
        if (attempt >= LAYOUT_WAIT_MAX_RETRIES) {
            Log.w(TAG, "Fragment layout wait timeout, submitting getMapAsync anyway");
            submitGetMapAsync("layout-timeout");
            return;
        }
        if (fragmentView == null) {
            LogUtil.d(TAG, "Fragment view null, retry " + attempt);
            mainHandler.postDelayed(() -> waitForFragmentLayoutThenGetMap(attempt + 1), LAYOUT_WAIT_MS);
            return;
        }
        LogUtil.d(TAG, "Fragment view size " + fragmentView.getWidth() + "x" + fragmentView.getHeight()
                + ", waiting for layout (attempt " + attempt + ")");
        fragmentView.requestLayout();
        ViewParent parent = fragmentView.getParent();
        if (parent instanceof View) {
            ((View) parent).requestLayout();
        }
        mainHandler.postDelayed(() -> waitForFragmentLayoutThenGetMap(attempt + 1), LAYOUT_WAIT_MS);
    }

    private void submitGetMapAsync(String reason) {
        if (mapAsyncSubmitted || mapFragment == null) {
            return;
        }
        mapAsyncSubmitted = true;
        mapFragment.getMapAsync(this);
        LogUtil.d(TAG, "getMapAsync submitted (" + reason + ")");
    }
    
    @Override
    public void onMapReady(GoogleMap googleMap) {
        LogUtil.d(TAG, "Google Map is ready");
        this.googleMap = googleMap;
        this.isMapReady = true;
        
        if (googleMap != null) {
            configureMap();
            
            if (callback != null) {
                callback.onMapReady();
            }
        } else {
            Log.e(TAG, "onMapReady received null GoogleMap");
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
        
        // 设置地图内边距，让标尺、logo、缩放按钮上移22dp，指南针额外下移10dp
        int mapPaddingTop = (int) (42 * context.getResources().getDisplayMetrics().density);
        googleMap.setPadding(0, mapPaddingTop, 0, 0);
        
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // 首次就绪时移动到默认位置，触发瓦片请求（zoom=2 时 SDK 常不加载瓦片 → 灰屏）
        if (googleMap.getCameraPosition().zoom < 10f) {
            LatLng defaultLocation = new LatLng(22.543611, 113.881944);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 17f));
        }

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
    @Override
    public void moveCamera(double latitude, double longitude, float zoom) {
        // 完全禁用谷歌地图的自动相机移动
        LogUtil.d(TAG, "Google Map - moveCamera COMPLETELY DISABLED in GoogleMapManager. Requested: " + latitude + ", " + longitude);
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
    @Override
    public Object addMarker(double latitude, double longitude, String title, String snippet) {
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
     * 添加带图标资源的标记（IMapAdapter 接口实现）
     */
    @Override
    public Object addMarkerWithIcon(double latitude, double longitude, String title, String snippet, int iconResId) {
        if (googleMap != null) {
            LatLng latLng = new LatLng(latitude, longitude);
            com.google.android.gms.maps.model.BitmapDescriptor icon =
                com.google.android.gms.maps.model.BitmapDescriptorFactory.fromResource(iconResId);
            MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(snippet)
                .icon(icon);
            return googleMap.addMarker(options);
        }
        return null;
    }

    /**
     * 更新折线坐标点（用于轨迹播放动画）
     */
    @Override
    public void updatePolylinePoints(Object polyline, List<double[]> points) {
        if (polyline instanceof Polyline && points != null) {
            List<com.google.android.gms.maps.model.LatLng> googlePoints = new ArrayList<>();
            for (double[] point : points) {
                googlePoints.add(new com.google.android.gms.maps.model.LatLng(point[0], point[1]));
            }
            ((Polyline) polyline).setPoints(googlePoints);
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
            ((Marker) marker).setPosition(new LatLng(latitude, longitude));
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
            ((Marker) marker).setRotation(rotation);
        }
    }

    /**
     * 动画移动相机
     */
    @Override
    public void animateCamera(double latitude, double longitude, float zoom) {
        if (googleMap != null) {
            LatLng latLng = new LatLng(latitude, longitude);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        }
    }
    
    /**
     * 绘制轨迹线
     */
    @Override
    public Object drawPolyline(List<double[]> points, int color, float width) {
        if (googleMap != null && points != null && !points.isEmpty()) {
            List<com.google.android.gms.maps.model.LatLng> googlePoints = new ArrayList<>();
            for (double[] point : points) {
                googlePoints.add(new com.google.android.gms.maps.model.LatLng(point[0], point[1]));
            }
            PolylineOptions options = new PolylineOptions()
                .addAll(googlePoints)
                .color(color)
                .width(width);
            return googleMap.addPolyline(options);
        }
        return null;
    }
    
    /**
     * 清除地图上的所有标记和线条
     */
    @Override
    public void clearMap() {
        if (googleMap != null) {
            googleMap.clear();
        }
    }
    
    /**
     * 设置地图类型
     * @param type GoogleMap.MAP_TYPE_NORMAL 或 GoogleMap.MAP_TYPE_SATELLITE
     */
    @Override
    public void setMapType(int type) {
        if (googleMap != null) {
            googleMap.setMapType(type);
        }
    }

    /**
     * 设置深色地图样式
     * @param isDarkMode 是否深色模式
     */
    @Override
    public void setDarkMapStyle(boolean isDarkMode) {
        if (googleMap != null) {
            if (isDarkMode) {
                try {
                    int resId = context.getResources().getIdentifier("map_style_night", "raw", context.getPackageName());
                    if (resId != 0) {
                        boolean styleOk = googleMap.setMapStyle(
                                com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(context, resId));
                        LogUtil.d(TAG, "Dark map style applied: " + styleOk);
                        if (!styleOk) {
                            Log.w(TAG, "Dark map style rejected by Maps SDK (may show gray if JSON invalid)");
                        }
                    } else {
                        Log.w(TAG, "map_style_night raw resource not found");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to apply dark map style: " + e.getMessage());
                }
            } else {
                googleMap.setMapStyle(null);
            }
        }
    }
    
    /**
     * 获取当前缩放级别
     */
    @Override
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
    @Override
    public boolean isMapReady() {
        return isMapReady && googleMap != null;
    }
    
    /**
     * 显示地图视图
     */
    @Override
    public void showMap() {
        if (mapFragment != null && mapFragment.getView() != null) {
            View view = mapFragment.getView();
            view.setVisibility(View.VISIBLE);
            ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).bringChildToFront(view);
            }
        } else {
            Log.w(TAG, "showMap: fragment or view not ready");
        }
    }
    
    /**
     * 隐藏地图视图
     */
    @Override
    public void hideMap() {
        if (mapFragment != null && mapFragment.getView() != null) {
            mapFragment.getView().setVisibility(View.GONE);
        }
    }

    @Override
    public String getProvider() {
        return "google";
    }

    @Override
    public void onResume() {
        // GoogleMap 由 SupportMapFragment 管理，无需手动调用
    }

    @Override
    public void onPause() {
        // GoogleMap 由 SupportMapFragment 管理，无需手动调用
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        hideMap();
        googleMap = null;
        isMapReady = false;
        mapAsyncSubmitted = false;
    }
}
