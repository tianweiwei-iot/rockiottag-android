package com.RockiotTag.tag;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.amap.api.maps.AMapException;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.navi.AMapNavi;
import com.amap.api.navi.AMapNaviListener;
import com.amap.api.navi.AMapNaviView;
import com.amap.api.navi.AMapNaviViewListener;
import com.amap.api.navi.enums.NaviType;
import com.amap.api.navi.enums.TransportType;
import com.amap.api.navi.model.AMapCalcRouteResult;
import com.amap.api.navi.model.AMapLaneInfo;
import com.amap.api.navi.model.AMapNaviCameraInfo;
import com.amap.api.navi.model.AMapNaviCross;
import com.amap.api.navi.model.AMapNaviLocation;
import com.amap.api.navi.model.AMapNaviPath;
import com.amap.api.navi.model.AMapNaviRouteNotifyData;
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo;
import com.amap.api.navi.model.AimLessModeCongestionInfo;
import com.amap.api.navi.model.AimLessModeStat;
import com.amap.api.navi.model.AMapModelCross;
import com.amap.api.navi.model.NaviLatLng;
import com.amap.api.navi.model.NaviInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 导航Activity - 使用高德导航SDK实现完整的应用内导航
 * 支持驾车/步行/骑行导航，包含语音播报、转向提示、偏航重算
 * 智能推荐导航方式：距离近建议步行，距离远规划驾车
 */
public class NavigationActivity extends AppCompatActivity
        implements AMapNaviListener, AMapNaviViewListener {

    private static final String TAG = "NavigationActivity";

    private static final int NAVI_MODE_DRIVE = 0;
    private static final int NAVI_MODE_WALK = 1;
    private static final int NAVI_MODE_RIDE = 2;

    // 距离阈值：步行/骑行小于50米提示无需导航
    private static final int WALK_RIDE_NO_NAVI_DISTANCE = 50;
    // 距离阈值：驾车小于500米提示无需导航
    private static final int DRIVE_NO_NAVI_DISTANCE = 500;

    private AMapNaviView mAMapNaviView;
    private AMapNavi mAMapNavi;

    private ImageButton btnBack;
    private TextView naviTitle;
    private TextView naviDistance;
    private LinearLayout naviModeBar;
    private LinearLayout btnDrive;
    private LinearLayout btnWalk;
    private LinearLayout btnRide;
    private ProgressBar progressBar;
    private Button btnStartNavi;

    // 起点坐标（手机位置，GCJ-02）
    private double startLatitude;
    private double startLongitude;
    private boolean hasStartLocation = false;

    // 终点坐标（设备位置，GCJ-02）
    private double destLatitude;
    private double destLongitude;

    private int currentNaviMode = NAVI_MODE_DRIVE;
    private boolean isNaviStarted = false;
    private boolean isRouteCalculated = false;
    private int lastRouteDistance = 0;

    // 设备信息（用于品牌定制标记）
    private String deviceId = "";
    private String deviceName = "";

    // 脉冲扩散动画（近距离时模拟蓝牙信号）
    private ValueAnimator pulseAnimator;
    private List<com.amap.api.maps.model.Circle> pulseCircles = new ArrayList<>();

    // 蓝牙信号热力圈
    private com.amap.api.maps.model.Circle heatCircle;
    // 周边资产点位
    private List<com.amap.api.maps.model.Marker> assetMarkers = new ArrayList<>();
    // 距离标注
    private List<com.amap.api.maps.model.Marker> distanceMarkers = new ArrayList<>();
    // 图层开关状态
    private boolean showHeatCircle = true;
    private boolean showAssetPoints = true;
    // 图层开关按钮
    private ImageView btnLayerToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        Intent intent = getIntent();
        destLatitude = intent.getDoubleExtra("dest_latitude", 0);
        destLongitude = intent.getDoubleExtra("dest_longitude", 0);
        deviceId = intent.getStringExtra("device_id");
        deviceName = intent.getStringExtra("device_name");
        if (deviceId == null) deviceId = "";
        if (deviceName == null) deviceName = "";

        if (destLatitude == 0 || destLongitude == 0) {
            Toast.makeText(this, R.string.navi_no_destination, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 读取起点坐标（手机位置）
        startLatitude = intent.getDoubleExtra("start_latitude", 0);
        startLongitude = intent.getDoubleExtra("start_longitude", 0);
        boolean startIsGcj02 = intent.getBooleanExtra("start_is_gcj02", false);

        if (startLatitude != 0 && startLongitude != 0) {
            hasStartLocation = true;
            Log.d(TAG, "Start location from intent: lat=" + startLatitude + ", lng=" + startLongitude
                    + ", isGcj02=" + startIsGcj02);
        } else {
            // 尝试从系统获取当前位置作为备份
            Log.w(TAG, "No start location in intent, trying to get current location...");
            getPhoneLocation();
            startIsGcj02 = false; // LocationManager获取的是WGS-84
        }

        // 坐标系转换（高德地图使用GCJ-02坐标系）
        // 设备位置：BLE GPS返回的是WGS-84，需要转换为GCJ-02
        com.amap.api.maps.model.LatLng destGcj = CoordinateUtils.wgs84ToGcj02(destLatitude, destLongitude);
        destLatitude = destGcj.latitude;
        destLongitude = destGcj.longitude;
        Log.d(TAG, "Navigation destination (GCJ-02): lat=" + destLatitude + ", lng=" + destLongitude);

        // 起点位置：高德定位SDK返回的已经是GCJ-02，不需要转换；Google/系统定位返回的是WGS-84，需要转换
        if (hasStartLocation && !startIsGcj02) {
            com.amap.api.maps.model.LatLng startGcj = CoordinateUtils.wgs84ToGcj02(startLatitude, startLongitude);
            startLatitude = startGcj.latitude;
            startLongitude = startGcj.longitude;
            Log.d(TAG, "Navigation start converted to GCJ-02: lat=" + startLatitude + ", lng=" + startLongitude);
        } else if (hasStartLocation) {
            Log.d(TAG, "Navigation start already in GCJ-02: lat=" + startLatitude + ", lng=" + startLongitude);
        }

        initViews(savedInstanceState);
        initNavi();
    }

    /**
     * 从系统获取手机当前位置作为导航起点（备份方案）
     */
    private void getPhoneLocation() {
        try {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager == null) {
                Log.e(TAG, "LocationManager is null");
                return;
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Location permission not granted");
                return;
            }

            // 先尝试GPS
            Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (gpsLocation != null) {
                startLatitude = gpsLocation.getLatitude();
                startLongitude = gpsLocation.getLongitude();
                hasStartLocation = true;
                Log.d(TAG, "Got GPS location: lat=" + startLatitude + ", lng=" + startLongitude);
                return;
            }

            // 降级到网络定位
            Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (networkLocation != null) {
                startLatitude = networkLocation.getLatitude();
                startLongitude = networkLocation.getLongitude();
                hasStartLocation = true;
                Log.d(TAG, "Got network location: lat=" + startLatitude + ", lng=" + startLongitude);
                return;
            }

            Log.w(TAG, "Could not get any location from LocationManager");
        } catch (Exception e) {
            Log.e(TAG, "Error getting phone location", e);
        }
    }

    private void initViews(Bundle savedInstanceState) {
        mAMapNaviView = findViewById(R.id.navi_view);
        mAMapNaviView.onCreate(savedInstanceState);
        mAMapNaviView.setAMapNaviViewListener(this);
        
        // 配置导航视图选项：禁用SDK自动绘制的路线和标记（旗帜、蓝色点、白线）
        com.amap.api.navi.AMapNaviViewOptions options = new com.amap.api.navi.AMapNaviViewOptions();
        options.setAutoDrawRoute(false);
        android.graphics.Bitmap transparentBitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
        transparentBitmap.setPixel(0, 0, 0);
        options.setStartPointBitmap(transparentBitmap);
        options.setEndPointBitmap(transparentBitmap);
        options.setWayPointBitmap(transparentBitmap);
        options.setCarBitmap(transparentBitmap);
        mAMapNaviView.setViewOptions(options);

        btnBack = findViewById(R.id.btn_back);
        naviTitle = findViewById(R.id.navi_title);
        naviDistance = findViewById(R.id.navi_distance);
        naviModeBar = findViewById(R.id.navi_mode_bar);
        btnDrive = findViewById(R.id.btn_drive);
        btnWalk = findViewById(R.id.btn_walk);
        btnRide = findViewById(R.id.btn_ride);
        progressBar = findViewById(R.id.progress_bar);
        btnStartNavi = findViewById(R.id.btn_start_navi);
        btnLayerToggle = findViewById(R.id.btn_layer_toggle);

        btnBack.setOnClickListener(v -> {
            if (isNaviStarted) {
                mAMapNavi.stopNavi();
                isNaviStarted = false;
            }
            finish();
        });

        // 图层开关：循环切换热力圈和资产点位
        btnLayerToggle.setOnClickListener(v -> {
            if (!showHeatCircle && !showAssetPoints) {
                // 都关了，全部打开
                showHeatCircle = true;
                showAssetPoints = true;
            } else if (showHeatCircle && showAssetPoints) {
                // 都开着，关掉资产点位
                showAssetPoints = false;
            } else if (showHeatCircle) {
                // 只开着热力圈，全关
                showHeatCircle = false;
            } else {
                // 都关了，打开热力圈
                showHeatCircle = true;
            }
            updateLayerVisibility();
            updateLayerToggleIcon();
        });

        btnDrive.setOnClickListener(v -> {
            currentNaviMode = NAVI_MODE_DRIVE;
            updateModeButtons();
            calculateRoute();
        });

        btnWalk.setOnClickListener(v -> {
            currentNaviMode = NAVI_MODE_WALK;
            updateModeButtons();
            calculateRoute();
        });

        btnRide.setOnClickListener(v -> {
            currentNaviMode = NAVI_MODE_RIDE;
            updateModeButtons();
            calculateRoute();
        });

        btnStartNavi.setOnClickListener(v -> {
            if (isRouteCalculated && mAMapNavi != null) {
                mAMapNavi.startNavi(NaviType.GPS);
                isNaviStarted = true;
                naviModeBar.setVisibility(View.GONE);
                btnStartNavi.setVisibility(View.GONE);
                naviTitle.setText(R.string.navi_navigating);
            }
        });

        updateModeButtons();
    }

    private void updateModeButtons() {
        // 选中状态：背景色更亮，alpha=1.0
        // 未选中状态：背景色更暗，alpha=0.6
        btnDrive.setAlpha(currentNaviMode == NAVI_MODE_DRIVE ? 1.0f : 0.6f);
        btnWalk.setAlpha(currentNaviMode == NAVI_MODE_WALK ? 1.0f : 0.6f);
        btnRide.setAlpha(currentNaviMode == NAVI_MODE_RIDE ? 1.0f : 0.6f);
        
        // 选中状态使用选中背景
        btnDrive.setBackgroundResource(currentNaviMode == NAVI_MODE_DRIVE 
                ? R.drawable.navi_mode_btn_selected_bg 
                : R.drawable.navi_mode_btn_bg);
        btnWalk.setBackgroundResource(currentNaviMode == NAVI_MODE_WALK 
                ? R.drawable.navi_mode_btn_selected_bg 
                : R.drawable.navi_mode_btn_bg);
        btnRide.setBackgroundResource(currentNaviMode == NAVI_MODE_RIDE 
                ? R.drawable.navi_mode_btn_selected_bg 
                : R.drawable.navi_mode_btn_bg);
    }

    private void initNavi() {
        try {
            mAMapNavi = AMapNavi.getInstance(getApplicationContext());
            mAMapNavi.addAMapNaviListener(this);
            Log.d(TAG, "AMapNavi instance created, waiting for init callback...");
        } catch (AMapException e) {
            Log.e(TAG, "AMapNavi init failed: " + e.getErrorMessage(), e);
            Toast.makeText(this, "导航初始化失败", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void calculateRoute() {
        if (mAMapNavi == null) return;

        if (!hasStartLocation) {
            Toast.makeText(this, "无法获取您的位置，请确保GPS已开启", Toast.LENGTH_LONG).show();
            return;
        }

        isRouteCalculated = false;
        btnStartNavi.setVisibility(View.GONE);
        naviDistance.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        NaviLatLng startPoint = new NaviLatLng(startLatitude, startLongitude);
        NaviLatLng endPoint = new NaviLatLng(destLatitude, destLongitude);

        Log.d(TAG, "Calculating route: start=(" + startLatitude + "," + startLongitude
                + "), end=(" + destLatitude + "," + destLongitude + "), mode=" + currentNaviMode);

        // 所有模式统一使用 calculateDriveRoute 格式（List参数能正确传递起点）
        // 步行/骑行通过 setTravelInfo 设置出行方式
        switch (currentNaviMode) {
            case NAVI_MODE_WALK:
                mAMapNavi.setTravelInfo(new com.amap.api.navi.model.AMapTravelInfo(TransportType.Walk));
                break;
            case NAVI_MODE_RIDE:
                mAMapNavi.setTravelInfo(new com.amap.api.navi.model.AMapTravelInfo(TransportType.Ride));
                break;
            case NAVI_MODE_DRIVE:
            default:
                mAMapNavi.setTravelInfo(new com.amap.api.navi.model.AMapTravelInfo(TransportType.Drive));
                break;
        }

        int strategy = mAMapNavi.strategyConvert(true, false, false, false, true);
        List<NaviLatLng> startList = new ArrayList<>();
        startList.add(startPoint);
        List<NaviLatLng> endList = new ArrayList<>();
        endList.add(endPoint);
        List<NaviLatLng> wayList = new ArrayList<>();
        boolean result = mAMapNavi.calculateDriveRoute(startList, endList, wayList, strategy);

        switch (currentNaviMode) {
            case NAVI_MODE_DRIVE:
                naviTitle.setText(R.string.navi_calculating_drive);
                break;
            case NAVI_MODE_WALK:
                naviTitle.setText(R.string.navi_calculating_walk);
                break;
            case NAVI_MODE_RIDE:
                naviTitle.setText(R.string.navi_calculating_ride);
                break;
        }

        Log.d(TAG, "Route calculation: mode=" + currentNaviMode + ", strategy=" + strategy + ", result=" + result);

        if (!result) {
            Log.e(TAG, "Route calculation method returned false for mode=" + currentNaviMode);
            progressBar.setVisibility(View.GONE);
            naviTitle.setText(R.string.navi_route_failed);
            Toast.makeText(this, R.string.navi_route_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 智能选择导航模式并规划路线
     * 根据距离自动推荐：距离近建议步行，距离远规划驾车
     */
    private void smartSelectNaviMode() {
        if (!hasStartLocation) {
            Toast.makeText(this, "无法获取您的位置，无法规划路线", Toast.LENGTH_LONG).show();
            return;
        }

        // 默认先用驾车路线规划获取距离信息
        mAMapNavi.setTravelInfo(new com.amap.api.navi.model.AMapTravelInfo(TransportType.Drive));
        int strategy = mAMapNavi.strategyConvert(true, false, false, false, true);
        List<NaviLatLng> startList = new ArrayList<>();
        startList.add(new NaviLatLng(startLatitude, startLongitude));
        List<NaviLatLng> endList = new ArrayList<>();
        endList.add(new NaviLatLng(destLatitude, destLongitude));
        List<NaviLatLng> wayList = new ArrayList<>();

        boolean result = mAMapNavi.calculateDriveRoute(startList, endList, wayList, strategy);
        Log.d(TAG, "Smart mode: calculating drive route first, result=" + result);

        if (!result) {
            // 驾车失败，尝试步行
            currentNaviMode = NAVI_MODE_WALK;
            mAMapNavi.setTravelInfo(new com.amap.api.navi.model.AMapTravelInfo(TransportType.Walk));
            mAMapNavi.calculateDriveRoute(startList, endList, wayList, strategy);
            naviTitle.setText(R.string.navi_calculating_walk);
        }
    }

    /**
     * 处理路线规划成功后的智能推荐
     */
    private void handleRouteSuccessWithSmartRecommend() {
        AMapNaviPath naviPath = mAMapNavi.getNaviPath();
        if (naviPath == null) {
            Log.w(TAG, "NaviPath is null after route success");
            return;
        }

        int distance = naviPath.getAllLength(); // 单位：米
        lastRouteDistance = distance;
        Log.d(TAG, "Route distance: " + distance + " meters");

        // 显示距离信息
        naviDistance.setVisibility(View.VISIBLE);
        naviDistance.setText(formatDistance(distance));

        // 距离阈值判断：距离太近提示无需导航
        boolean isTooClose = false;
        if (currentNaviMode == NAVI_MODE_WALK || currentNaviMode == NAVI_MODE_RIDE) {
            if (distance < WALK_RIDE_NO_NAVI_DISTANCE) {
                isTooClose = true;
            }
        } else if (currentNaviMode == NAVI_MODE_DRIVE) {
            if (distance < DRIVE_NO_NAVI_DISTANCE) {
                isTooClose = true;
            }
        }

        if (isTooClose) {
            naviTitle.setText(R.string.navi_already_nearby);
            Toast.makeText(this, R.string.navi_already_nearby, Toast.LENGTH_LONG).show();
            btnStartNavi.setEnabled(false);
            btnStartNavi.setAlpha(0.5f);
        } else {
            naviTitle.setText(R.string.navi_route_ready);
            btnStartNavi.setEnabled(true);
            btnStartNavi.setAlpha(1.0f);
        }

        // 清除地图上的所有标记和覆盖物
        if (mAMapNaviView != null) {
            com.amap.api.maps.AMap aMap = mAMapNaviView.getMap();
            if (aMap != null) {
                aMap.clear();
                // 清除引用
                pulseCircles.clear();
                assetMarkers.clear();
                distanceMarkers.clear();
                heatCircle = null;

                List<NaviLatLng> coordList = naviPath.getCoordList();
                if (coordList != null && coordList.size() > 0) {
                    List<com.amap.api.maps.model.LatLng> latLngList = new ArrayList<>();
                    for (NaviLatLng naviLatLng : coordList) {
                        latLngList.add(new com.amap.api.maps.model.LatLng(
                                naviLatLng.getLatitude(), naviLatLng.getLongitude()));
                    }
                    Log.d(TAG, "Route polyline drawn with " + latLngList.size() + " points");

                    // 起点终点使用路线连线的首末点（标记在路线上才对齐）
                    com.amap.api.maps.model.LatLng routeStart = latLngList.get(0);
                    com.amap.api.maps.model.LatLng routeEnd = latLngList.get(latLngList.size() - 1);

                    // === 1. 蓝牙信号热力圈（以终点设备为圆心）===
                    addBluetoothHeatCircle(aMap, routeEnd);

                    // === 2. 周边资产点位 ===
                    addNearbyAssetPoints(aMap, routeEnd);

                    // === 3. 导航路线美化 ===
                    // 外层：半透浅蓝描边
                    com.amap.api.maps.model.PolylineOptions outlineLine =
                            new com.amap.api.maps.model.PolylineOptions();
                    outlineLine.addAll(latLngList);
                    outlineLine.color(0x4D64B5F6); // 半透浅蓝
                    outlineLine.width(22);
                    aMap.addPolyline(outlineLine);

                    // 主路线：深蓝色实线
                    com.amap.api.maps.model.PolylineOptions mainLine =
                            new com.amap.api.maps.model.PolylineOptions();
                    mainLine.addAll(latLngList);
                    mainLine.color(0xFF1565C0); // 深蓝
                    mainLine.width(12);
                    aMap.addPolyline(mainLine);

                    // 添加方向箭头（小巧圆角箭头）
                    addRouteArrows(aMap, latLngList);

                    // === 4. 距离分段标注 ===
                    addDistanceLabels(aMap, latLngList, distance);

                    // === 5. 起点标记：蓝色定位罗盘（路线起点）===
                    com.amap.api.maps.model.MarkerOptions startMarker =
                            new com.amap.api.maps.model.MarkerOptions();
                    startMarker.position(routeStart);
                    startMarker.icon(getStartPointBitmap());
                    startMarker.setFlat(false);
                    startMarker.anchor(0.5f, 0.5f);
                    aMap.addMarker(startMarker);

                    // === 6. 终点标记：品牌定制图标（路线终点）===
                    com.amap.api.maps.model.MarkerOptions endMarker =
                            new com.amap.api.maps.model.MarkerOptions();
                    endMarker.position(routeEnd);
                    endMarker.icon(getEndPointBitmap());
                    endMarker.setFlat(false);
                    endMarker.anchor(0.5f, 1.0f); // 锚点在底部（定位针尖端）
                    aMap.addMarker(endMarker);

                    // === 7. 近距离脉冲扩散动画 ===
                    if (distance < 200) {
                        startPulseAnimation(aMap, routeEnd);
                    }
                }
            }
        }

        // 移动相机显示整条路线
        moveCameraToShowRoute(naviPath);
    }

    /**
     * 移动相机显示整条路线
     */
    private void moveCameraToShowRoute(AMapNaviPath naviPath) {
        LatLngBounds bounds = naviPath.getBoundsForPath();
        if (bounds != null && mAMapNaviView != null) {
            com.amap.api.maps.AMap aMap = mAMapNaviView.getMap();
            if (aMap != null) {
                // 移动相机显示整条路线
                aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                Log.d(TAG, "Camera moved to show route bounds");
            }
        }
    }

    /**
     * 格式化距离显示
     */
    private String formatDistance(int meters) {
        if (meters < 1000) {
            return meters + "m";
        } else {
            float km = meters / 1000f;
            return String.format("%.1fkm", km);
        }
    }

    // ===== Activity 生命周期 =====

    @Override
    protected void onResume() {
        super.onResume();
        if (mAMapNaviView != null) mAMapNaviView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAMapNaviView != null) mAMapNaviView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPulseAnimation();
        if (mAMapNaviView != null) mAMapNaviView.onDestroy();
        if (mAMapNavi != null) {
            mAMapNavi.removeAMapNaviListener(this);
            mAMapNavi.destroy();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAMapNaviView != null) mAMapNaviView.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (isNaviStarted) {
            mAMapNavi.stopNavi();
            isNaviStarted = false;
        }
        super.onBackPressed();
    }

    // ===== AMapNaviListener 回调 =====

    @Override
    public void onInitNaviSuccess() {
        Log.d(TAG, "Navigation init success");
        progressBar.setVisibility(View.GONE);
        naviModeBar.setVisibility(View.VISIBLE);
        // 智能选择导航模式
        smartSelectNaviMode();
    }

    @Override
    public void onInitNaviFailure() {
        Log.e(TAG, "Navigation init failure");
        progressBar.setVisibility(View.GONE);
        Toast.makeText(this, R.string.navi_init_failed, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStartNavi(int i) {
        Log.d(TAG, "Navigation started, type=" + i);
    }

    @Override
    public void onTrafficStatusUpdate() {
    }

    @Override
    public void onLocationChange(AMapNaviLocation aMapNaviLocation) {
    }

    @Override
    public void onGetNavigationText(int i, String s) {
    }

    @Override
    public void onGetNavigationText(String s) {
    }

    @Override
    public void onEndEmulatorNavi() {
    }

    @Override
    public void onArriveDestination() {
        Toast.makeText(this, R.string.navi_arrived, Toast.LENGTH_LONG).show();
        isNaviStarted = false;
    }

    @Override
    public void onCalculateRouteFailure(int i) {
        Log.e(TAG, "Route calculation failure (legacy), errorCode=" + i);
        isRouteCalculated = false;
        progressBar.setVisibility(View.GONE);
        naviTitle.setText(R.string.navi_route_failed);
        String errorMsg = getRouteErrorMsg(i);
        Toast.makeText(this, getString(R.string.navi_route_failed) + ": " + errorMsg, Toast.LENGTH_SHORT).show();
    }

    private String getRouteErrorMsg(int code) {
        switch (code) {
            case 1: return "网络连接失败";
            case 2: return "起点距离终点太远";
            case 3: return "找不到可用路线";
            case 4: return "参数错误";
            default: return "错误码 " + code;
        }
    }

    @Override
    public void onReCalculateRouteForYaw() {
        Toast.makeText(this, R.string.navi_reroute_yaw, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onReCalculateRouteForTrafficJam() {
        Toast.makeText(this, R.string.navi_reroute_jam, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onArrivedWayPoint(int i) {
    }

    @Override
    public void onGpsOpenStatus(boolean b) {
    }

    @Override
    public void onNaviInfoUpdate(NaviInfo naviInfo) {
    }

    @Override
    public void updateCameraInfo(AMapNaviCameraInfo[] aMapNaviCameraInfos) {
    }

    @Override
    public void updateIntervalCameraInfo(AMapNaviCameraInfo aMapNaviCameraInfo, AMapNaviCameraInfo aMapNaviCameraInfo1, int i) {
    }

    @Override
    public void onServiceAreaUpdate(com.amap.api.navi.model.AMapServiceAreaInfo[] aMapServiceAreaInfos) {
    }

    @Override
    public void showCross(AMapNaviCross aMapNaviCross) {
    }

    @Override
    public void hideCross() {
    }

    @Override
    public void showModeCross(AMapModelCross aMapModelCross) {
    }

    @Override
    public void hideModeCross() {
    }

    @Override
    public void showLaneInfo(AMapLaneInfo[] aMapLaneInfos, byte[] bytes, byte[] bytes1) {
    }

    @Override
    public void showLaneInfo(AMapLaneInfo aMapLaneInfo) {
    }

    @Override
    public void hideLaneInfo() {
    }

    @Override
    public void onCalculateRouteSuccess(int[] ints) {
        Log.d(TAG, "Route calculation success (legacy), routeIds count=" + (ints != null ? ints.length : 0));
        isRouteCalculated = true;
        progressBar.setVisibility(View.GONE);
        btnStartNavi.setVisibility(View.VISIBLE);
        handleRouteSuccessWithSmartRecommend();
    }

    @Override
    public void notifyParallelRoad(int i) {
    }

    @Override
    public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo[] aMapNaviTrafficFacilityInfos) {
    }

    @Override
    public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo aMapNaviTrafficFacilityInfo) {
    }

    @Override
    public void updateAimlessModeStatistics(AimLessModeStat aimLessModeStat) {
    }

    @Override
    public void updateAimlessModeCongestionInfo(AimLessModeCongestionInfo aimLessModeCongestionInfo) {
    }

    @Override
    public void onPlayRing(int i) {
    }

    @Override
    public void onCalculateRouteSuccess(AMapCalcRouteResult aMapCalcRouteResult) {
        Log.d(TAG, "Route calculation success (new API), route count=" +
                (aMapCalcRouteResult != null && aMapCalcRouteResult.getRouteid() != null ? aMapCalcRouteResult.getRouteid().length : 0));
        isRouteCalculated = true;
        progressBar.setVisibility(View.GONE);
        btnStartNavi.setVisibility(View.VISIBLE);
        handleRouteSuccessWithSmartRecommend();
    }

    @Override
    public void onCalculateRouteFailure(AMapCalcRouteResult aMapCalcRouteResult) {
        int code = aMapCalcRouteResult != null ? aMapCalcRouteResult.getErrorCode() : -1;
        Log.e(TAG, "Route calculation failure (new API), errorCode=" + code);
        isRouteCalculated = false;
        progressBar.setVisibility(View.GONE);
        naviTitle.setText(R.string.navi_route_failed);
        Toast.makeText(this, getString(R.string.navi_route_failed) + " (" + code + ")", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNaviRouteNotify(AMapNaviRouteNotifyData aMapNaviRouteNotifyData) {
    }

    @Override
    public void onGpsSignalWeak(boolean b) {
    }

    // ===== AMapNaviViewListener 回调 =====

    @Override
    public void onNaviSetting() {
    }

    @Override
    public void onNaviCancel() {
        finish();
    }

    @Override
    public boolean onNaviBackClick() {
        return false;
    }

    @Override
    public void onNaviMapMode(int i) {
    }

    @Override
    public void onNaviTurnClick() {
    }

    @Override
    public void onNextRoadClick() {
    }

    @Override
    public void onScanViewButtonClick() {
    }

    @Override
    public void onLockMap(boolean b) {
    }

    @Override
    public void onNaviViewLoaded() {
        Log.d(TAG, "Navigation view loaded");
    }

    @Override
    public void onMapTypeChanged(int i) {
    }

    @Override
    public void onNaviViewShowMode(int i) {
    }

    @Override
    public void onStopSpeaking() {
    }

    @Override
    public void onViewTypeChanged(com.amap.api.navi.AmapPageType amapPageType) {
    }

    @Override
    public void onAMapNaviViewExit() {
        finish();
    }

    @Override
    public void onStrategyChanged(int i) {
    }

    @Override
    public void onBroadcastModeChanged(int i) {
    }

    @Override
    public void onDayAndNightModeChanged(int i) {
    }

    @Override
    public void onScaleAutoChanged(boolean b) {
    }

    @Override
    public void onListenToVoiceDuringCallChanged(boolean b) {
    }

    @Override
    public void onControlMusicVolumeModeChanged(int i) {
    }

    @Override
    public void onEagleChanged(boolean b) {
    }

    @Override
    public void onNaviRouteHighlightChange(long l, int i) {
    }

    /**
     * 将 drawable 资源转换为高德地图的 BitmapDescriptor
     */
    private com.amap.api.maps.model.BitmapDescriptor getMarkerIconFromDrawable(int drawableResId) {
        Context context = getApplicationContext();
        Drawable drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableResId);
        if (drawable == null) {
            Log.w(TAG, "Drawable resource not found: " + drawableResId);
            return com.amap.api.maps.model.BitmapDescriptorFactory.defaultMarker();
        }
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0) width = 120;
        if (height <= 0) height = 160;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return com.amap.api.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * 生成起点图标：蓝色定位罗盘 + 方向箭头
     * 显示手机朝向，方便用户转身找设备
     */
    private com.amap.api.maps.model.BitmapDescriptor getStartPointBitmap() {
        int size = 100;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = size / 2f;
        float cy = size / 2f;

        // 外圈阴影
        paint.setColor(0x33000000);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy + 2, size * 0.44f, paint);

        // 外圈白色边框
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, size * 0.44f, paint);

        // 蓝色罗盘底盘
        paint.setColor(0xFF1565C0);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, size * 0.38f, paint);

        // 罗盘刻度环（浅蓝）
        paint.setColor(0xFF42A5F5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawCircle(cx, cy, size * 0.32f, paint);

        // 十字线（浅蓝细线）
        paint.setColor(0xFF90CAF9);
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(cx, cy - size * 0.28f, cx, cy + size * 0.28f, paint);
        canvas.drawLine(cx - size * 0.28f, cy, cx + size * 0.28f, cy, paint);

        // 方向箭头（北向，白色三角）
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        Path arrowPath = new Path();
        arrowPath.moveTo(cx, cy - size * 0.26f); // 顶点
        arrowPath.lineTo(cx - size * 0.1f, cy - size * 0.05f);
        arrowPath.lineTo(cx + size * 0.1f, cy - size * 0.05f);
        arrowPath.close();
        canvas.drawPath(arrowPath, paint);

        // 南向小三角（半透明白）
        paint.setColor(0x80FFFFFF);
        Path southArrow = new Path();
        southArrow.moveTo(cx, cy + size * 0.22f);
        southArrow.lineTo(cx - size * 0.06f, cy + size * 0.08f);
        southArrow.lineTo(cx + size * 0.06f, cy + size * 0.08f);
        southArrow.close();
        canvas.drawPath(southArrow, paint);

        // 中心小圆点
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, size * 0.05f, paint);

        return com.amap.api.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * 生成终点图标：品牌定制（融合定位标 + 设备编号缩写）
     * 分层设计：底部阴影底座 + 立体定位针 + 圈内设备编号缩写
     */
    private com.amap.api.maps.model.BitmapDescriptor getEndPointBitmap() {
        int width = 120;
        int height = 150;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = width / 2f;
        float pinTop = 15f;
        float circleRadius = 32f;
        float circleCy = pinTop + circleRadius;

        // 1. 底部阴影底座（椭圆阴影）
        paint.setColor(0x33000000);
        paint.setStyle(Paint.Style.FILL);
        RectF shadowOval = new RectF(cx - 20, height - 25, cx + 20, height - 10);
        canvas.drawOval(shadowOval, paint);

        // 2. 定位针尖端（三角形，从圆底部到尖端）
        paint.setColor(0xFFD32F2F);
        paint.setStyle(Paint.Style.FILL);
        Path pinPath = new Path();
        pinPath.moveTo(cx - 14, circleCy + circleRadius - 8);
        pinPath.lineTo(cx + 14, circleCy + circleRadius - 8);
        pinPath.lineTo(cx, height - 18);
        pinPath.close();
        canvas.drawPath(pinPath, paint);

        // 3. 定位针侧面阴影（右侧深色，营造立体感）
        paint.setColor(0xFFB71C1C);
        paint.setStyle(Paint.Style.FILL);
        Path shadowPath = new Path();
        shadowPath.moveTo(cx, circleCy + circleRadius - 8);
        shadowPath.lineTo(cx + 14, circleCy + circleRadius - 8);
        shadowPath.lineTo(cx, height - 18);
        shadowPath.close();
        canvas.drawPath(shadowPath, paint);

        // 4. 圆形主体（红色渐变，营造立体感）
        RadialGradient gradient = new RadialGradient(
                cx - 8, circleCy - 8, circleRadius,
                0xFFEF5350, 0xFFC62828, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, circleCy, circleRadius, paint);
        paint.setShader(null);

        // 5. 白色边框
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawCircle(cx, circleCy, circleRadius - 1, paint);

        // 6. 内圈白色填充区域（显示设备编号）
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, circleCy, circleRadius - 8, paint);

        // 7. 设备编号缩写
        String abbreviation = getDeviceAbbreviation();
        paint.setColor(0xFFC62828);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);

        if (abbreviation.length() <= 3) {
            paint.setTextSize(22f);
        } else {
            paint.setTextSize(16f);
        }

        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = circleCy - (fm.top + fm.bottom) / 2f;
        canvas.drawText(abbreviation, cx, textY, paint);

        // 8. 顶部高光（营造玻璃质感）
        paint.setColor(0x55FFFFFF);
        paint.setStyle(Paint.Style.FILL);
        RectF highlight = new RectF(cx - 12, circleCy - circleRadius + 10, cx + 8, circleCy - 6);
        canvas.drawOval(highlight, paint);

        return com.amap.api.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * 获取设备编号缩写（用于终点标记显示）
     * 优先使用deviceName前几个字符，否则用deviceId
     */
    private String getDeviceAbbreviation() {
        if (deviceName != null && !deviceName.isEmpty()) {
            // 取前4个字符作为缩写
            return deviceName.length() > 4 ? deviceName.substring(0, 4) : deviceName;
        }
        if (deviceId != null && !deviceId.isEmpty()) {
            return deviceId.length() > 4 ? deviceId.substring(0, 4) : deviceId;
        }
        return "R"; // Rockiot默认
    }

    /**
     * 生成小巧圆角箭头图标（用于路线方向指示）
     * 箭头尖端朝下绘制，配合方位角旋转后指向前进方向（手机→设备）
     */
    private com.amap.api.maps.model.BitmapDescriptor getArrowBitmap() {
        int size = 28;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);

        // 箭头尖端朝下（rotateAngle=0时指向南方，配合方位角旋转后指向正确方向）
        Path arrowPath = new Path();
        arrowPath.moveTo(size / 2f, size * 0.95f); // 尖端在下方
        arrowPath.lineTo(size * 0.85f, size * 0.45f);
        arrowPath.lineTo(size * 0.62f, size * 0.45f);
        arrowPath.lineTo(size * 0.62f, size * 0.1f);
        arrowPath.lineTo(size * 0.38f, size * 0.1f);
        arrowPath.lineTo(size * 0.38f, size * 0.45f);
        arrowPath.lineTo(size * 0.15f, size * 0.45f);
        arrowPath.close();
        canvas.drawPath(arrowPath, paint);

        return com.amap.api.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * 在路线上添加方向箭头标记
     */
    private void addRouteArrows(com.amap.api.maps.AMap aMap, List<com.amap.api.maps.model.LatLng> latLngList) {
        if (latLngList == null || latLngList.size() < 3) {
            return;
        }

        com.amap.api.maps.model.BitmapDescriptor arrowIcon = getArrowBitmap();

        // 每隔一定距离添加一个箭头
        int step = Math.max(3, latLngList.size() / 10);
        for (int i = step; i < latLngList.size() - 1; i += step) {
            com.amap.api.maps.model.LatLng from = latLngList.get(i - 1);
            com.amap.api.maps.model.LatLng to = latLngList.get(i);

            // 计算方向角度
            double deltaLat = to.latitude - from.latitude;
            double deltaLon = to.longitude - from.longitude;
            float bearing = (float) Math.toDegrees(Math.atan2(deltaLon, deltaLat));

            com.amap.api.maps.model.MarkerOptions arrowMarker = new com.amap.api.maps.model.MarkerOptions();
            arrowMarker.position(to);
            arrowMarker.icon(arrowIcon);
            arrowMarker.setFlat(true);
            arrowMarker.rotateAngle(bearing);
            arrowMarker.anchor(0.5f, 0.5f);
            aMap.addMarker(arrowMarker);
        }
    }

    /**
     * 添加距离分段标注（长路线每隔一段标注剩余米数）
     */
    private void addDistanceLabels(com.amap.api.maps.AMap aMap,
                                    List<com.amap.api.maps.model.LatLng> latLngList,
                                    int totalDistance) {
        if (latLngList == null || latLngList.size() < 2 || totalDistance < 200) {
            return;
        }

        // 计算路线总长度（用于分段）
        double totalLen = 0;
        List<Double> segLengths = new ArrayList<>();
        for (int i = 1; i < latLngList.size(); i++) {
            float[] results = new float[1];
            Location.distanceBetween(
                    latLngList.get(i - 1).latitude, latLngList.get(i - 1).longitude,
                    latLngList.get(i).latitude, latLngList.get(i).longitude,
                    results);
            segLengths.add((double) results[0]);
            totalLen += results[0];
        }

        // 每隔约200米标注一次（或路线较短时在中间标注一次）
        double labelInterval = 200; // 米
        if (totalLen < 400) {
            labelInterval = totalLen / 2;
        }

        double accumulated = 0;
        double nextLabelAt = labelInterval;
        int remainingMeters = totalDistance;

        for (int i = 0; i < segLengths.size(); i++) {
            accumulated += segLengths.get(i);
            if (accumulated >= nextLabelAt && i + 1 < latLngList.size()) {
                // 计算剩余距离
                double remainingRatio = 1.0 - (accumulated / totalLen);
                remainingMeters = (int) (totalDistance * remainingRatio);
                if (remainingMeters < 10) continue;

                // 生成距离标注Bitmap
                com.amap.api.maps.model.BitmapDescriptor labelIcon = getDistanceLabelBitmap(remainingMeters);

                com.amap.api.maps.model.MarkerOptions labelMarker = new com.amap.api.maps.model.MarkerOptions();
                labelMarker.position(latLngList.get(i + 1));
                labelMarker.icon(labelIcon);
                labelMarker.setFlat(true);
                labelMarker.anchor(0.5f, 0.5f);
                com.amap.api.maps.model.Marker marker = aMap.addMarker(labelMarker);
                distanceMarkers.add(marker);

                nextLabelAt += labelInterval;
            }
        }
    }

    /**
     * 生成距离标注Bitmap（半透明深蓝背景 + 白色文字）
     */
    private com.amap.api.maps.model.BitmapDescriptor getDistanceLabelBitmap(int meters) {
        String text = formatDistance(meters);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // 测量文字宽度
        float textWidth = textPaint.measureText(text);
        int padding = 16;
        int bitmapWidth = (int) (textWidth + padding * 2);
        int bitmapHeight = 44;

        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 圆角背景
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xCC1565C0); // 半透明深蓝
        bgPaint.setStyle(Paint.Style.FILL);
        RectF rect = new RectF(0, 0, bitmapWidth, bitmapHeight);
        canvas.drawRoundRect(rect, 12, 12, bgPaint);

        // 文字
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = bitmapHeight / 2f - (fm.top + fm.bottom) / 2f;
        canvas.drawText(text, bitmapWidth / 2f, textY, textPaint);

        return com.amap.api.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * 添加蓝牙信号热力圈（以终点设备为圆心，淡红色渐变圈）
     * 圈内 = 蓝牙可搜到范围
     */
    private void addBluetoothHeatCircle(com.amap.api.maps.AMap aMap,
                                         com.amap.api.maps.model.LatLng center) {
        // BLE典型范围约15米（城市环境中信号衰减较快）
        double bleRadius = 15;

        heatCircle = aMap.addCircle(new com.amap.api.maps.model.CircleOptions()
                .center(center)
                .radius(bleRadius)
                .fillColor(0x22FF1744) // 淡红色填充
                .strokeColor(0x44FF1744) // 淡红色边框
                .strokeWidth(2)
                .visible(showHeatCircle));

        Log.d(TAG, "Bluetooth heat circle added at destination, radius=" + bleRadius + "m");
    }

    /**
     * 添加周边资产点位（同区域其他Rockiot设备灰色小点）
     */
    private void addNearbyAssetPoints(com.amap.api.maps.AMap aMap,
                                       com.amap.api.maps.model.LatLng destCenter) {
        try {
            DatabaseHelper dbHelper = new DatabaseHelper(this);
            List<Device> allDevices = dbHelper.getAllDevices();

            for (Device device : allDevices) {
                // 跳过当前导航目标设备
                if (device.getDeviceId() != null && device.getDeviceId().equals(deviceId)) {
                    continue;
                }
                // 跳过没有位置的设备
                if (device.getLatitude() == 0 && device.getLongitude() == 0) {
                    continue;
                }

                // 计算与终点的距离，只显示2km范围内的设备
                float[] results = new float[1];
                Location.distanceBetween(
                        destCenter.latitude, destCenter.longitude,
                        device.getLatitude(), device.getLongitude(),
                        results);
                if (results[0] > 2000) continue;

                // 坐标转换：设备位置是WGS-84，需转为GCJ-02
                com.amap.api.maps.model.LatLng deviceGcj = CoordinateUtils.wgs84ToGcj02(
                        device.getLatitude(), device.getLongitude());

                com.amap.api.maps.model.BitmapDescriptor assetIcon = getAssetPointBitmap(device);
                com.amap.api.maps.model.MarkerOptions assetMarker = new com.amap.api.maps.model.MarkerOptions();
                assetMarker.position(deviceGcj);
                assetMarker.icon(assetIcon);
                assetMarker.setFlat(false);
                assetMarker.anchor(0.5f, 0.5f);
                assetMarker.visible(showAssetPoints);

                com.amap.api.maps.model.Marker marker = aMap.addMarker(assetMarker);
                assetMarkers.add(marker);
            }
            Log.d(TAG, "Nearby asset points added: " + assetMarkers.size());
        } catch (Exception e) {
            Log.e(TAG, "Error adding nearby asset points", e);
        }
    }

    /**
     * 生成周边资产点位图标（灰色小圆点 + 设备名首字）
     */
    private com.amap.api.maps.model.BitmapDescriptor getAssetPointBitmap(Device device) {
        int size = 48;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = size / 2f;
        float cy = size / 2f;

        // 外圈阴影
        paint.setColor(0x22000000);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy + 1, size * 0.4f, paint);

        // 灰色圆点
        paint.setColor(0xFF9E9E9E);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, size * 0.38f, paint);

        // 白色边框
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawCircle(cx, cy, size * 0.36f, paint);

        // 设备名首字
        String initial = "";
        if (device.getName() != null && !device.getName().isEmpty()) {
            initial = device.getName().substring(0, 1);
        } else if (device.getDeviceNum() != null && !device.getDeviceNum().isEmpty()) {
            initial = device.getDeviceNum().substring(0, 1);
        } else {
            initial = "R";
        }
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(20f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = cy - (fm.top + fm.bottom) / 2f;
        canvas.drawText(initial, cx, textY, paint);

        return com.amap.api.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * 启动脉冲扩散动画（近距离时模拟蓝牙信号）
     * 一圈圈淡红向外扩散
     */
    private void startPulseAnimation(com.amap.api.maps.AMap aMap,
                                      com.amap.api.maps.model.LatLng center) {
        stopPulseAnimation();

        // 创建3层脉冲圈，依次扩散（BLE范围约15米）
        for (int i = 0; i < 3; i++) {
            com.amap.api.maps.model.Circle pulse = aMap.addCircle(
                    new com.amap.api.maps.model.CircleOptions()
                            .center(center)
                            .radius(3)
                            .fillColor(0x00FF1744) // 初始透明
                            .strokeColor(0x44FF1744)
                            .strokeWidth(2));
            pulseCircles.add(pulse);
        }

        // 动画：0 -> 15m半径，同时透明度从可见到透明
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(3000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            for (int i = 0; i < pulseCircles.size(); i++) {
                if (i >= pulseCircles.size()) break;
                com.amap.api.maps.model.Circle circle = pulseCircles.get(i);
                if (circle == null) continue;

                // 每个圈错开1/3周期
                float offset = i / 3f;
                float phase = (fraction + offset) % 1f;

                // 半径从3到15
                double radius = 3 + phase * 12;
                circle.setRadius(radius);

                // 透明度：中间最亮，两端透明
                int alpha = (int) (Math.sin(phase * Math.PI) * 80);
                circle.setFillColor(Color.argb(alpha, 255, 23, 68));
                circle.setStrokeColor(Color.argb(alpha, 255, 23, 68));
            }
        });
        pulseAnimator.start();
        Log.d(TAG, "Pulse animation started at destination");
    }

    /**
     * 停止脉冲扩散动画
     */
    private void stopPulseAnimation() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        pulseCircles.clear();
    }

    /**
     * 更新图层可见性
     */
    private void updateLayerVisibility() {
        if (heatCircle != null) {
            heatCircle.setVisible(showHeatCircle);
        }
        for (com.amap.api.maps.model.Marker marker : assetMarkers) {
            marker.setVisible(showAssetPoints);
        }
    }

    /**
     * 更新图层开关按钮图标
     */
    private void updateLayerToggleIcon() {
        if (btnLayerToggle == null) return;
        if (showHeatCircle && showAssetPoints) {
            btnLayerToggle.setImageResource(android.R.drawable.ic_menu_mapmode);
            btnLayerToggle.setAlpha(1.0f);
        } else if (showHeatCircle || showAssetPoints) {
            btnLayerToggle.setImageResource(android.R.drawable.ic_menu_mapmode);
            btnLayerToggle.setAlpha(0.6f);
        } else {
            btnLayerToggle.setImageResource(android.R.drawable.ic_menu_mapmode);
            btnLayerToggle.setAlpha(0.3f);
        }
    }
}