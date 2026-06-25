package com.RockiotTag.tag;

import com.RockiotTag.tag.util.ToastHelper;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.ref.WeakReference;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.RockiotTag.tag.viewmodel.MainViewModel;
import com.RockiotTag.tag.viewmodel.BleViewModel;
import com.RockiotTag.tag.viewmodel.MapViewModel;
import com.RockiotTag.tag.model.DeviceTag;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.helper.MainDialogHelper;
import com.RockiotTag.tag.helper.MainThemeHelper;
import com.RockiotTag.tag.helper.MainBleHelper;
import com.RockiotTag.tag.helper.MainAuthHelper;
import com.RockiotTag.tag.helper.MainTabHelper;
import com.RockiotTag.tag.helper.MainDeviceRefreshHelper;
import com.RockiotTag.tag.helper.MainDeviceDisplayHelper;
import com.RockiotTag.tag.helper.MainMapHelper;
import com.RockiotTag.tag.helper.MainLocationHelper;
import com.RockiotTag.tag.helper.MainMapInitHelper;
import com.RockiotTag.tag.helper.MainMapSwitchHelper;
import com.RockiotTag.tag.helper.MainDeviceSelectionHelper;
import com.RockiotTag.tag.helper.MainPermissionHelper;
import com.RockiotTag.tag.helper.MainGeocodingHelper;
import com.RockiotTag.tag.helper.MainCrowdSourceMapHelper;
import com.RockiotTag.tag.helper.MainSelectDeviceHelper;
import com.RockiotTag.tag.helper.MainViewModelObserverHelper;
import com.RockiotTag.tag.helper.MainHelperInitializer;
import com.RockiotTag.tag.integration.LocationOptimizationManager;
import com.RockiotTag.tag.util.BoundDevicesHelper;
import com.RockiotTag.tag.util.LanguageIndicatorHelper;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.map.MapAdapterFactory;
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.gson.Gson;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_DEVICE_LIST = 101;

    public static int pendingTabSwitch = -1;

    private MapView mapView;
    /** 仅在使用高德地图时为 true，用于隔离 MapView 生命周期 */
    private boolean amapLifecycleStarted = false;
    private AMap aMap;
    private MapManager mapManager;
    private IMapAdapter mapAdapter;
    private SupportMapFragment googleMapFragment;
    
    // 使用专用的地图服务类（完全隔离）
    private com.RockiotTag.tag.location.SystemLocationService systemLocationService;
    private com.RockiotTag.tag.map.amap.AMapGeocoder amapGeocoder;
    private com.RockiotTag.tag.map.google.GoogleLocationService googleLocationService;
    private com.RockiotTag.tag.map.google.GoogleGeocoderService googleGeocoderService;
    private BLEManager bleManager;
    private CrowdSourcingManager crowdSourcingManager;
    private DatabaseHelper databaseHelper;
    private NewApiService apiService;
    private UnboundDeviceManager unboundDeviceManager;
    
    // 定位优化管理器（新增）
    private com.RockiotTag.tag.integration.LocationOptimizationManager locationOptimizationManager;
    
    // MVVM - ViewModel
    private MainViewModel viewModel;
    private BleViewModel bleViewModel;
    private MapViewModel mapViewModel;

    // Helper 类（MVVM 重构：将业务逻辑从 Activity 中分离）
    private MainDialogHelper dialogHelper;
    private MainThemeHelper themeHelper;
    private MainBleHelper bleHelper;
    private MainAuthHelper authHelper;
    private MainTabHelper tabHelper;
    private MainDeviceRefreshHelper deviceRefreshHelper;
    private MainDeviceDisplayHelper deviceDisplayHelper;
    private MainMapHelper mapHelper;
    private MainLocationHelper locationHelper;
    private MainMapInitHelper mapInitHelper;
    private MainDeviceSelectionHelper deviceSelectionHelper;
    private MainCrowdSourceMapHelper crowdSourceMapHelper;
    private MainSelectDeviceHelper selectDeviceHelper;

    private ImageButton locateBtn;
    private ImageButton mapTypeBtn;
    private ImageButton refreshBtn;
    private ImageView customCompass;  // 自定义指南针
    private TextView batteryLevelText;
    private TextView deviceAddressText;
    private TextView updateTimeText;
    private ImageView scanStatusIcon;
    private ImageView scanningIndicator;  // 扫描状态指示图标（时间戳后面）
    private View bottomInfo;
    private boolean isSatelliteMap = false;

    // 底部导航栏
    private LinearLayout bottomNavigation;
    private LinearLayout tabHome, tabList, tabTrack, tabProfile;
    private ImageView tabHomeIcon, tabListIcon, tabTrackIcon, tabProfileIcon;
    private TextView tabHomeText, tabListText, tabTrackText, tabProfileText;
    private int currentTab = 0; // 当前选中的Tab索引

    // Fragment（由 MainTabHelper 创建并持有）
    private HomeFragment homeFragment;
    private DeviceListFragment deviceListFragment;
    private TrackFragment trackFragment;
    private ProfileFragment profileFragment;

    // 蓝牙增强扫描强度：0=关闭，1=低（单次），2=高（持续循环）
    private int scanIntensityLevel = 0;
    private boolean locationServicesStarted = false;
    private final Runnable pendingLocationBleInitRunnable = this::doInitLocationAndBleIfPermitted;

    private double currentLatitude = 22.543611;
    private double currentLongitude = 113.881944;
    private String currentDeviceName = "tag";
    private Object currentLocationMarker;
    private Object deviceLocationMarker;
    private long lastUserInteractionTime = 0;
    private static final long AUTO_RETURN_DELAY = 10000; // 10秒延迟
    private boolean isFirstLocation = true; // 标记是否是第一次定位
    private boolean isUserLocated = false; // 标记用户是否手动点击了定位按钮
    private boolean pendingDeviceSelection = false; // 标记是否刚切换了设备，需要等待服务器数据后移动相机
    private TagDevice selectedDevice = null; // 当前选中的设备
    private com.RockiotTag.tag.util.SafeHandler trackRefreshHandler;
    private Runnable trackRefreshRunnable;
    private final Handler mainUiHandler = new Handler(android.os.Looper.getMainLooper());
    private Runnable pendingScanIndicatorHideRunnable;
    private double lastRecordedLatitude = 0;
    private double lastRecordedLongitude = 0;
    private long lastRecordedTimestamp = 0;
    private static final long TRACK_REFRESH_INTERVAL = 30 * 1000; // 30秒
    private boolean isFetchingDeviceInfo = false; // 防止重复请求

    // 设备切换/刷新序列号：确保只处理最新请求的结果
    private volatile int deviceRefreshSequence = 0;
    private volatile boolean isRefreshInProgress = false;
    
    // 地址缓存相关
    private double lastAddressLatitude = 0;
    private double lastAddressLongitude = 0;
    private long lastAddressUpdateTime = 0; // 上次地址更新时间
    
    /**
     * 获取标签图标（已迁移到 DeviceTag 枚举）
     * @deprecated 使用 {@link DeviceTag#getEmoji(String)} 代替
     */
    @Deprecated
    private String getTagIcon(String tag) {
        return DeviceTag.getEmoji(tag);
    }
    
    private void updateDeviceNameWithTag(String name, String tag) {
        // 设备名称显示已移至底部信息卡片区域，此方法保留为空以兼容旧调用
    }

    
    /**
     * 设置状态栏样式
     */
    private void setupStatusBar() {
        com.RockiotTag.tag.util.StatusBarHelper.setupStatusBar(this);
    }
    
    private void cleanOldTrackData(boolean forceClean) {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean hasCleanedOldTrack = prefs.getBoolean("has_cleaned_old_track", false);
        
        if (!hasCleanedOldTrack || forceClean) {
            int deletedCount = databaseHelper.deleteAllLocationRecords();
            
            // 重置最后记录的位置和时间戳，确保新轨迹从第一个点开始
            lastRecordedLatitude = 0;
            lastRecordedLongitude = 0;
            lastRecordedTimestamp = 0;
            
            if (!forceClean) {
                android.content.SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("has_cleaned_old_track", true);
                editor.apply();
            }
        } else {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        
        try {
            // 恢复用户的语言偏好，如果没有设置过则使用系统语言
            android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
            String languageCode;
            
            // 检查用户是否已经选择过语言
            if (LanguageUtils.hasUserSelectedLanguage(this)) {
                // 用户已经选择过语言，使用保存的语言
                languageCode = prefs.getString("language", "zh");
            } else {
                // 首次启动，自动使用系统语言
                languageCode = LanguageUtils.getSystemLanguage();
                // 保存系统语言作为默认语言（但不标记为用户已选择）
                prefs.edit().putString("language", languageCode).apply();
            }
            
            LanguageUtils.applyLanguage(this, languageCode);
            
            // 深色模式不使用setDefaultNightMode，避免Activity重建导致崩溃
            // 深色模式通过手动设置颜色实现（在setContentView之后调用applyDarkMode）
            
            super.onCreate(savedInstanceState);

            if (savedInstanceState != null) {
                currentTab = savedInstanceState.getInt("current_tab", currentTab);
            }
            
            LogUtil.d(TAG, "=== STEP 1: super.onCreate DONE ===");

            setContentView(R.layout.activity_main);
            
            LogUtil.d(TAG, "=== STEP 2: setContentView DONE ===");
            
            // 隐藏标题栏
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
            
            // 设置状态栏样式（必须在setContentView之后）
            setupStatusBar();

            // 高德隐私合规：仅在使用高德地图时初始化
            String mapProvider = com.RockiotTag.tag.map.MapAdapterFactory.getSavedProvider(this);
            if (com.RockiotTag.tag.map.MapLayerController.isAmapProvider(mapProvider)) {
                try {
                    com.amap.api.maps.MapsInitializer.updatePrivacyShow(this, true, true);
                    com.amap.api.maps.MapsInitializer.updatePrivacyAgree(this, true);
                    com.amap.api.location.AMapLocationClient.updatePrivacyShow(this, true, true);
                    com.amap.api.location.AMapLocationClient.updatePrivacyAgree(this, true);
                } catch (Exception e) {
                    // ignore
                }
            }
            mapView = findViewById(R.id.mapView);
            LogUtil.d(TAG, "=== STEP 3: mapView findViewById ===");
            if (mapView == null) {
                Log.e(TAG, "!!! mapView is NULL !!!");
                throw new RuntimeException("mapView is null - check layout");
            }
            googleMapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.google_map_fragment);
            com.RockiotTag.tag.map.MapLayerController.applyLayers(mapProvider, mapView, googleMapFragment);

            amapLifecycleStarted = com.RockiotTag.tag.map.MapLayerController.isAmapProvider(mapProvider);
            boolean mapProviderJustChanged = prefs.getBoolean("map_provider_just_changed", false);
            boolean languageJustChanged = prefs.getBoolean("language_just_changed", false);
            if (mapProviderJustChanged) {
                prefs.edit().putBoolean("map_provider_just_changed", false).apply();
            }
            if (languageJustChanged) {
                prefs.edit().putBoolean("language_just_changed", false).apply();
            }
            if (amapLifecycleStarted) {
                try {
                    if (mapProviderJustChanged || languageJustChanged) {
                        mapView.onCreate(null);
                        LogUtil.d(TAG, "=== STEP 4: mapView.onCreate(null) after config change ===");
                    } else {
                        mapView.onCreate(savedInstanceState);
                        LogUtil.d(TAG, "=== STEP 4: mapView.onCreate DONE ===");
                    }
                } catch (Throwable mapInitError) {
                    Log.e(TAG, "MapView onCreate failed, falling back to Google Maps", mapInitError);
                    amapLifecycleStarted = false;
                    mapProvider = com.RockiotTag.tag.map.MapAdapterFactory.PROVIDER_GOOGLE;
                    prefs.edit().putString("map_provider", mapProvider).apply();
                    com.RockiotTag.tag.map.MapLayerController.applyLayers(mapProvider, mapView, googleMapFragment);
                }
            } else {
                LogUtil.d(TAG, "=== STEP 4: Google map mode, skip MapView lifecycle ===");
            }
            // 地图内边距通过 aMap.setPadding() 设置，避免 mapView.setPadding() 裁剪地图显示区域

            batteryLevelText = findViewById(R.id.battery_level);
            deviceAddressText = findViewById(R.id.device_address);
            updateTimeText = findViewById(R.id.update_time);
            scanStatusIcon = findViewById(R.id.scan_status_icon);
            scanningIndicator = findViewById(R.id.scanning_indicator);  // 新增
            bottomInfo = findViewById(R.id.bottom_info);
            locateBtn = findViewById(R.id.locate_btn);
            mapTypeBtn = findViewById(R.id.map_type_btn);
            refreshBtn = findViewById(R.id.refresh_btn);
            customCompass = findViewById(R.id.custom_compass);  // 自定义指南针

            // 底部导航栏
            bottomNavigation = findViewById(R.id.bottom_navigation);
            tabHome = findViewById(R.id.tab_home);
            tabList = findViewById(R.id.tab_list);
            tabTrack = findViewById(R.id.tab_track);
            tabProfile = findViewById(R.id.tab_profile);
            tabHomeIcon = findViewById(R.id.tab_home_icon);
            tabListIcon = findViewById(R.id.tab_list_icon);
            tabTrackIcon = findViewById(R.id.tab_track_icon);
            tabProfileIcon = findViewById(R.id.tab_profile_icon);
            tabHomeText = findViewById(R.id.tab_home_text);
            tabListText = findViewById(R.id.tab_list_text);
            tabTrackText = findViewById(R.id.tab_track_text);
            tabProfileText = findViewById(R.id.tab_profile_text);

            initTabHelper();
            LogUtil.d(TAG, "=== STEP 5-6: initTabHelper DONE ===");

            if (prefs.getBoolean("show_language_changed_toast", false)) {
                prefs.edit().putBoolean("show_language_changed_toast", false).apply();
                ToastHelper.show(this, R.string.language_changed);
            }
            int pendingMapToast = prefs.getInt("pending_map_switch_toast", 0);
            if (pendingMapToast != 0) {
                prefs.edit().remove("pending_map_switch_toast").apply();
                ToastHelper.show(this, pendingMapToast);
            }

            // 设备信息栏始终保留，未登录或无设备时显示"--"
            showBottomInfo();
            batteryLevelText.setText(getString(R.string.battery_level_empty));
            deviceAddressText.setText(getString(R.string.position_empty));
            updateTimeText.setText(getString(R.string.last_update_empty));
            updateDeviceNameWithTag(getString(R.string.no_device_selected), null);
            LogUtil.d(TAG, "=== STEP 7: bottomInfo initialized with defaults ===");

            // 手动应用深色模式（在所有UI组件初始化之后）
            boolean isDarkMode = getSharedPreferences("app_settings", MODE_PRIVATE).getBoolean("dark_mode", false);
            applyDarkMode(isDarkMode);

            initDatabase();  // 先初始化数据库，以便在地图初始化时能够读取设备信息
            LogUtil.d(TAG, "=== STEP 8: initDatabase DONE ===");
            
            // MVVM - 初始化 ViewModel
            viewModel = new ViewModelProvider(this).get(MainViewModel.class);
            bleViewModel = new ViewModelProvider(this).get(BleViewModel.class);
            mapViewModel = new ViewModelProvider(this).get(MapViewModel.class);
            setupViewModelObservers();

            // 初始化定位优化管理器（新增）
            try {
                locationOptimizationManager = new com.RockiotTag.tag.integration.LocationOptimizationManager(
                    this, databaseHelper
                );
                LogUtil.d(TAG, "LocationOptimizationManager initialized successfully");
                
                // 设置扫描状态图标为旋转动画
                if (scanStatusIcon != null) {
                    android.graphics.drawable.Drawable drawable = scanStatusIcon.getDrawable();
                    if (drawable instanceof android.graphics.drawable.Animatable) {
                        android.graphics.drawable.Animatable animatable = (android.graphics.drawable.Animatable) drawable;
                        animatable.start();
                    }
                }
            } catch (Throwable e) {
                Log.e(TAG, "Failed to initialize LocationOptimizationManager", e);
                locationOptimizationManager = null;
            }

            // 设置位置更新回调，当蓝牙扫描到设备时自动刷新UI
            if (locationOptimizationManager != null) {
                locationOptimizationManager.setLocationUpdateCallback(new com.RockiotTag.tag.integration.LocationOptimizationManager.LocationUpdateCallback() {
                    @Override
                    public void onLocationUpdated(com.RockiotTag.tag.model.DeviceLocation location) {
                    LogUtil.d(TAG, "========== UI Callback: Location Updated ==========");
                    LogUtil.d(TAG, "Source: " + location.getActualSource());
                    LogUtil.d(TAG, "Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude());
                    LogUtil.d(TAG, "Timestamp: " + location.getTimestamp());
                    LogUtil.d(TAG, "DeviceId (MAC): " + location.getDeviceId());
                    LogUtil.d(TAG, "Current selectedDevice: " + (selectedDevice != null ? selectedDevice.getName() : "NULL"));
                    if (selectedDevice != null) {
                        LogUtil.d(TAG, "  selectedDevice.deviceId: " + selectedDevice.getDeviceId());
                        LogUtil.d(TAG, "  selectedDevice.lastSeen: " + selectedDevice.getLastSeen());
                    }
                    
                    // 【关键修复】蓝牙扫描回调总是立即更新时间戳，不管是否有GPS坐标
                    LogUtil.d(TAG, "⚡ Bluetooth scan callback - updating timestamp immediately");
                    runOnUiThread(() -> {
                        try {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            String deviceMac = location.getDeviceId();
                            LogUtil.d(TAG, "Bluetooth scan for MAC: " + deviceMac);
                            
                            // 【严格检查】必须有选中设备
                            if (selectedDevice == null) {
                                LogUtil.d(TAG, "No selected device, skip update");
                                return;
                            }
                            
                            // 【修复】比较MAC地址，忽略大小写并去除空格
                            String selectedMac = selectedDevice.getMac();
                            LogUtil.d(TAG, "Selected device MAC: [" + selectedMac + "], Scanned MAC: [" + deviceMac + "]");
                            
                            if (selectedMac == null || deviceMac == null || 
                                !selectedMac.trim().equalsIgnoreCase(deviceMac.trim())) {
                                LogUtil.d(TAG, "❌ MAC mismatch: selected='" + selectedMac + "', scanned='" + deviceMac + "', skip update");
                                return;
                            }
                            
                            LogUtil.d(TAG, "✓✓✓ MAC matched! Updating timestamp for: " + selectedDevice.getName());
                            
                            // 【最高优先级】立即更新时间戳
                            long newTimestamp = location.getTimestamp();
                            selectedDevice.setLastSeen(newTimestamp);
                            selectedDevice.setBluetoothScanTime(newTimestamp);
                            
                            // 更新时间显示
                            if (updateTimeText != null) {
                                updateTimeText.setText(getString(R.string.last_update_with_time, 
                                    com.RockiotTag.tag.util.TimeFormatter.formatSmartTime(MainActivity.this, newTimestamp)));
                            }
                            
                            LogUtil.d(TAG, "✓ Timestamp updated: " + newTimestamp);
                            
                            // 【新增】更新电池显示
                            int battery = location.getBattery();
                            LogUtil.d(TAG, "Battery from location: " + battery);
                            if (battery > 0) {
                                viewModel.updateBatteryLevel(String.valueOf(battery));
                                LogUtil.d(TAG, "✓ Battery updated: " + battery + "%");
                            } else if (battery == 0) {
                                viewModel.updateBatteryLevel("0");
                                LogUtil.d(TAG, "✓ Battery updated: 0%");
                            }
                            
                            // 【优化】蓝牙扫描只更新时间戳，不更新地址
                            // 地址由 updateDeviceUIWithLatest() 统一管理，避免频繁刷新
                            // 如果有GPS坐标，只更新设备对象，不更新UI地址显示
                            if (location.getLatitude() != 0 && location.getLongitude() != 0) {
                                LogUtil.d(TAG, "Bluetooth scan has GPS coords, but skip address update: lat=" + location.getLatitude() + ", lng=" + location.getLongitude());
                                selectedDevice.setLatitude(location.getLatitude());
                                selectedDevice.setLongitude(location.getLongitude());
                                // 不调用 deviceAddressText.setText()，保持当前地址不变
                            }
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error in bluetooth scan update", e);
                            e.printStackTrace();
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Location update error: " + error);
                }
            });
                
                // 【新增】设置扫描状态回调，用于显示扫描图标
                locationOptimizationManager.setScanStateCallback(new com.RockiotTag.tag.integration.LocationOptimizationManager.ScanStateCallback() {
                    @Override
                    public void onScanStarted() {
                        runOnUiThread(() -> {
                            // 扫描中：在时间戳后面显示旋转动画
                            if (scanningIndicator != null) {
                                scanningIndicator.setImageResource(android.R.drawable.ic_popup_sync);
                                scanningIndicator.setVisibility(View.VISIBLE);
                                android.graphics.drawable.Drawable drawable = scanningIndicator.getDrawable();
                                if (drawable instanceof android.graphics.drawable.Animatable) {
                                    ((android.graphics.drawable.Animatable) drawable).start();
                                }
                            }
                        });
                    }
                    
                    @Override
                    public void onScanStopped() {
                        runOnUiThread(() -> {
                            // 休息中：隐藏扫描指示图标
                            if (scanningIndicator != null) {
                                scanningIndicator.setVisibility(View.GONE);
                            }
                        });
                    }
                    
                    @Override
                    public void onScanSuccess() {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            if (scanningIndicator != null) {
                                scanningIndicator.setImageResource(android.R.drawable.ic_menu_save);
                                scanningIndicator.setVisibility(View.VISIBLE);
                                if (pendingScanIndicatorHideRunnable != null) {
                                    mainUiHandler.removeCallbacks(pendingScanIndicatorHideRunnable);
                                }
                                pendingScanIndicatorHideRunnable = () -> {
                                    if (!isFinishing() && !isDestroyed() && scanningIndicator != null) {
                                        scanningIndicator.setVisibility(View.GONE);
                                    }
                                };
                                mainUiHandler.postDelayed(pendingScanIndicatorHideRunnable, 1000);
                            }
                        });
                    }
                });
            }
            
            
            // 升级数据库以支持多语言地址缓存
            databaseHelper.upgradeToMultiLanguageCache();

            initMapHelper();
            initLocationHelper();
            initDeviceSelectionHelper();
            initMapInitHelper();
            initMap();

            // 定位 / BLE 在权限就绪后初始化（见 initLocationAndBleIfPermitted）
            
            // 启动定位优化（新增）
            if (locationOptimizationManager != null && locationOptimizationManager.isOptimizationEnabled()) {
                LogUtil.d(TAG, "=== Starting Location Optimization ===");
                
                // 检查是否已登录，未登录不自动选择设备
                String authToken = getSharedPreferences("app_settings", MODE_PRIVATE).getString("auth_token", null);
                if (authToken != null && !authToken.isEmpty()) {
                    // 【关键优化】先自动选择第一个设备
                    locationOptimizationManager.autoSelectFirstDevice();
                    
                    // 如果有自动选中的设备，立即更新UI
                    String autoSelectedMac = locationOptimizationManager.getCurrentSelectedDeviceId();
                    if (autoSelectedMac != null && !autoSelectedMac.isEmpty()) {
                        TagDevice firstDevice = databaseHelper.getDevice(autoSelectedMac);
                        if (firstDevice != null) {
                            LogUtil.d(TAG, "Auto-selected first device on startup: " + firstDevice.getName());
                            selectDevice(firstDevice);
                        }
                    } else {
                        Log.w(TAG, "No device auto-selected");
                    }
                } else {
                    LogUtil.d(TAG, "User not logged in, skip auto-selecting device");
                }
                
                // 不自动启动蓝牙扫描，由扫描开关按钮控制
                LogUtil.d(TAG, "Bluetooth scanning is OFF by default, use scan toggle button to start");
            } else {
                Log.e(TAG, "Location optimization manager is NULL or disabled!");
                ToastHelper.showLong(this, "❌ 蓝牙扫描未启动");
            }
            
            
            initCrowdSourcing();
            
            
            initApiService();

            // MVVM 重构 - 初始化 Helper 类（必须在所有依赖变量初始化之后）
            initHelpers();

            // 清理旧的轨迹数据（使用10米阈值之前的数据）
            cleanOldTrackData(false);


            checkPermissions();

            locateBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 用户手动点击定位按钮，设置isUserLocated = true
                    locateToDevicePosition(true);
                }
            });

            mapTypeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mapAdapter != null) {
                        if (isSatelliteMap) {
                            mapAdapter.setMapType(com.amap.api.maps.AMap.MAP_TYPE_NORMAL);
                            ToastHelper.show(MainActivity.this, R.string.normal_map);
                        } else {
                            mapAdapter.setMapType(com.amap.api.maps.AMap.MAP_TYPE_SATELLITE);
                            ToastHelper.show(MainActivity.this, R.string.satellite_map);
                        }
                    }
                    isSatelliteMap = !isSatelliteMap;
                }
            });

            refreshBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performDeviceRefresh(true);
                }
            });


            initTrackRefresh();
            databaseHelper.cleanOldLocationRecords();
            databaseHelper.cleanExpiredAddressCache(); // 清理过期的地址缓存


            // 初始状态：显示"未选择设备"
            updateDeviceNameWithTag(getString(R.string.no_device_selected), null);
            
            // 初始状态：未连接设备时显示空白
            updateBottomInfo();
            
            
            // 立即获取设备信息
            restoreSelectedDevice();
            LogUtil.d(TAG, "Selected device restored");
            
            LogUtil.d(TAG, "========== MainActivity.onCreate COMPLETE ==========");
            
        } catch (Throwable e) {
            Log.e(TAG, "!!! FATAL ERROR IN onCreate !!!", e);
            Log.e(TAG, "Error type: " + e.getClass().getName());
            Log.e(TAG, "Error message: " + e.getMessage());
            ToastHelper.showLong(this, R.string.app_startup_failed);
            throw new RuntimeException("MainActivity startup failed", e);
        }

    }

    /**
     * 启动导航到设备位置
     */
    private void startNavigation() {
        if (selectedDevice == null) {
            ToastHelper.show(this, R.string.please_select_device);
            return;
        }
        if (selectedDevice.getLatitude() == 0 || selectedDevice.getLongitude() == 0) {
            ToastHelper.show(this, R.string.navi_no_destination);
            return;
        }
        Intent intent = new Intent(MainActivity.this, NavigationActivity.class);
        intent.putExtra("dest_latitude", selectedDevice.getLatitude());
        intent.putExtra("dest_longitude", selectedDevice.getLongitude());
        intent.putExtra("device_id", selectedDevice.getDeviceId());
        intent.putExtra("device_name", selectedDevice.getName());
        // 传递手机当前位置作为导航起点
        intent.putExtra("start_latitude", currentLatitude);
        intent.putExtra("start_longitude", currentLongitude);
        // 系统 GPS 为 WGS-84；NavigationActivity 会在需要时自行转换
        boolean isStartGcj02 = false;
        intent.putExtra("start_is_gcj02", isStartGcj02);
        LogUtil.d(TAG, "Starting navigation: phone=(" + currentLatitude + "," + currentLongitude 
                + ", isGcj02=" + isStartGcj02 + "), device=(" + selectedDevice.getLatitude() + "," + selectedDevice.getLongitude() + ")");
        startActivity(intent);
    }

    private void startForegroundService() {
        Intent serviceIntent = new Intent(this, BLEForegroundService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        LogUtil.d(TAG, "Foreground service started");
    }
    
    private void updateBottomInfo() {
        if (bleManager != null && bleManager.isConnected()) {
            // 蓝牙已连接，不覆盖当前显示
        } else {
            batteryLevelText.setText(getString(R.string.battery_level_empty));
            deviceAddressText.setText(getString(R.string.position_empty));
            updateTimeText.setText(getString(R.string.last_update_empty));
        }
    }
    
    /**
     * 自动定位到设备位置（地图加载完成后调用）
     */
    private void autoLocateToDevice() {
        if (mapHelper != null) mapHelper.autoLocateToDevice();
    }
    
    /**
     * 切换到设备位置（模拟点击"定位到当前位置"按钮）
     * 用于切换设备后自动移动相机到设备位置
     * @param setUserLocated 是否设置用户已定位标志（true=用户手动点击，false=自动调用）
     */
    private void locateToDevicePosition(boolean setUserLocated) {
        if (mapHelper != null) mapHelper.locateToDevicePosition(setUserLocated);
    }

    /**
     * 刷新当前设备的地图显示
     */
    private void refreshMapWithCurrentDevice(boolean forceRefreshAddress) {
        if (mapHelper != null) mapHelper.refreshMapWithCurrentDevice(forceRefreshAddress);
    }

    private void initMap() {
        if (mapInitHelper != null) mapInitHelper.initMap();
    }

    private void initLocation() {
        if (locationHelper != null) locationHelper.initLocation();
    }

    private void setDefaultLocation() {
        if (locationHelper != null) locationHelper.setDefaultLocation();
    }

    private void initBLE() {
        if (bleManager == null) {
            bleManager = new BLEManager(this);
        }
    }

    /** 权限就绪后初始化定位与 BLE（延迟到下一帧，避免与权限弹窗/onResume 生命周期冲突） */
    private void initLocationAndBleIfPermitted() {
        if (!MainPermissionHelper.hasAllPermissions(this)) {
            LogUtil.d(TAG, "Runtime permissions not ready, skip location/BLE init");
            return;
        }
        if (locationServicesStarted) {
            LogUtil.d(TAG, "Location/BLE already initialized, skip");
            return;
        }
        mainUiHandler.removeCallbacks(pendingLocationBleInitRunnable);
        mainUiHandler.postDelayed(pendingLocationBleInitRunnable, 500);
    }

    private void doInitLocationAndBleIfPermitted() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (!MainPermissionHelper.hasAllPermissions(this)) {
            return;
        }
        if (locationServicesStarted) {
            return;
        }
        try {
            initLocation();
            initBLE();
            locationServicesStarted = true;
            LogUtil.d(TAG, "Location/BLE initialized after permissions granted");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize location/BLE after permission grant", t);
        }
    }

    private void initCrowdSourcing() {
        crowdSourcingManager = new CrowdSourcingManager();
        crowdSourcingManager.setCallback(new CrowdSourcingManager.NearbyDevicesCallback() {
            @Override
            public void onNearbyDevicesReceived(List<TagDevice> devices) {
                if (devices != null && !devices.isEmpty()) {
                    LogUtil.d(TAG, "Received " + devices.size() + " nearby devices");
                    // 更新地图上的附近设备标记
                    for (TagDevice device : devices) {
                        if (crowdSourceMapHelper != null) crowdSourceMapHelper.updateMapMarker(device);
                    }
                }
            }

            @Override
            public void onDeviceListReceived(List<TagDevice> devices) {
                if (devices != null && !devices.isEmpty()) {
                    LogUtil.d(TAG, "Received " + devices.size() + " devices from API");
                    // 处理从API获取的设备列表
                    for (TagDevice device : devices) {
                        databaseHelper.addDevice(device);
                        if (crowdSourceMapHelper != null) crowdSourceMapHelper.updateMapMarker(device);
                    }
                }
            }
        });
    }

    private void initDatabase() {
        databaseHelper = DatabaseHelper.getInstance(this);
    }

    private void initApiService() {
        apiService = NewApiService.getInstance();
        unboundDeviceManager = UnboundDeviceManager.getInstance(this);
        // 从SharedPreferences加载认证信息
        SharedPreferencesManager.loadAuth(this);
    }

    /**
     * MVVM 重构 - 初始化所有 Helper 类
     * 将业务逻辑委托给专门的 Helper 类，减少 Activity 代码量
     */
    private void initHelpers() {
        MainHelperInitializer.initCoreHelpers(createHelperHost());
    }

    private void initMapHelper() {
        MainHelperInitializer.initMapHelper(createHelperHost());
    }

    private void initLocationHelper() {
        MainHelperInitializer.initLocationHelper(createHelperHost());
    }

    private void initMapInitHelper() {
        MainHelperInitializer.initMapInitHelper(createHelperHost());
    }

    private void initDeviceSelectionHelper() {
        MainHelperInitializer.initDeviceSelectionHelper(createHelperHost());
    }

    private MainHelperInitializer.Host createHelperHost() {
        return new MainHelperInitializer.Host() {
            @Override public AppCompatActivity getActivity() { return MainActivity.this; }
            @Override public AMap getAMap() { return aMap; }
            @Override public void setAMap(AMap map) { aMap = map; }
            @Override public MapView getMapView() { return mapView; }
            @Override public SupportMapFragment getGoogleMapFragment() { return googleMapFragment; }
            @Override public MapManager getMapManager() { return mapManager; }
            @Override public void setMapManager(MapManager manager) { mapManager = manager; }
            @Override public IMapAdapter getMapAdapter() { return mapAdapter; }
            @Override public void setMapAdapter(IMapAdapter adapter) { mapAdapter = adapter; }
            @Override public MainViewModel getViewModel() { return viewModel; }
            @Override public MapViewModel getMapViewModel() { return mapViewModel; }
            @Override public DatabaseHelper getDatabaseHelper() { return databaseHelper; }
            @Override public NewApiService getApiService() { return apiService; }
            @Override public UnboundDeviceManager getUnboundDeviceManager() { return unboundDeviceManager; }
            @Override public LocationOptimizationManager getLocationOptimizationManager() {
                return locationOptimizationManager;
            }
            @Override public BLEManager getBleManager() { return bleManager; }
            @Override public CrowdSourcingManager getCrowdSourcingManager() { return crowdSourcingManager; }
            @Override public MainCrowdSourceMapHelper getCrowdSourceMapHelper() { return crowdSourceMapHelper; }
            @Override public void setCrowdSourceMapHelper(MainCrowdSourceMapHelper helper) {
                crowdSourceMapHelper = helper;
            }
            @Override public MainDialogHelper getDialogHelper() { return dialogHelper; }
            @Override public void setDialogHelper(MainDialogHelper helper) { dialogHelper = helper; }
            @Override public MainThemeHelper getThemeHelper() { return themeHelper; }
            @Override public void setThemeHelper(MainThemeHelper helper) { themeHelper = helper; }
            @Override public MainBleHelper getBleHelper() { return bleHelper; }
            @Override public void setBleHelper(MainBleHelper helper) { bleHelper = helper; }
            @Override public MainAuthHelper getAuthHelper() { return authHelper; }
            @Override public void setAuthHelper(MainAuthHelper helper) { authHelper = helper; }
            @Override public MainMapHelper getMapHelper() { return mapHelper; }
            @Override public void setMapHelper(MainMapHelper helper) { mapHelper = helper; }
            @Override public MainLocationHelper getLocationHelper() { return locationHelper; }
            @Override public void setLocationHelper(MainLocationHelper helper) { locationHelper = helper; }
            @Override public MainMapInitHelper getMapInitHelper() { return mapInitHelper; }
            @Override public void setMapInitHelper(MainMapInitHelper helper) { mapInitHelper = helper; }
            @Override public MainDeviceSelectionHelper getDeviceSelectionHelper() { return deviceSelectionHelper; }
            @Override public void setDeviceSelectionHelper(MainDeviceSelectionHelper helper) {
                deviceSelectionHelper = helper;
            }
            @Override public MainDeviceDisplayHelper getDeviceDisplayHelper() { return deviceDisplayHelper; }
            @Override public void setDeviceDisplayHelper(MainDeviceDisplayHelper helper) {
                deviceDisplayHelper = helper;
            }
            @Override public MainDeviceRefreshHelper getDeviceRefreshHelper() { return deviceRefreshHelper; }
            @Override public void setDeviceRefreshHelper(MainDeviceRefreshHelper helper) {
                deviceRefreshHelper = helper;
            }
            @Override public MainSelectDeviceHelper getSelectDeviceHelper() { return selectDeviceHelper; }
            @Override public void setSelectDeviceHelper(MainSelectDeviceHelper helper) {
                selectDeviceHelper = helper;
            }
            @Override public double getCurrentLatitude() { return currentLatitude; }
            @Override public void setCurrentLatitude(double lat) { currentLatitude = lat; }
            @Override public double getCurrentLongitude() { return currentLongitude; }
            @Override public void setCurrentLongitude(double lng) { currentLongitude = lng; }
            @Override public Object getCurrentLocationMarker() { return currentLocationMarker; }
            @Override public void setCurrentLocationMarker(Object marker) { currentLocationMarker = marker; }
            @Override public Object getDeviceLocationMarker() { return deviceLocationMarker; }
            @Override public void setDeviceLocationMarker(Object marker) { deviceLocationMarker = marker; }
            @Override public boolean isFirstLocation() { return isFirstLocation; }
            @Override public void setFirstLocation(boolean first) { isFirstLocation = first; }
            @Override public boolean isUserLocated() { return isUserLocated; }
            @Override public void setUserLocated(boolean located) { isUserLocated = located; }
            @Override public boolean isPendingDeviceSelection() { return pendingDeviceSelection; }
            @Override public void setPendingDeviceSelection(boolean pending) { pendingDeviceSelection = pending; }
            @Override public long getLastUserInteractionTime() { return lastUserInteractionTime; }
            @Override public void setLastUserInteractionTime(long time) { lastUserInteractionTime = time; }
            @Override public TagDevice getSelectedDevice() { return selectedDevice; }
            @Override public void setSelectedDevice(TagDevice device) { selectedDevice = device; }
            @Override public double getLastRecordedLatitude() { return lastRecordedLatitude; }
            @Override public void setLastRecordedLatitude(double v) { lastRecordedLatitude = v; }
            @Override public double getLastRecordedLongitude() { return lastRecordedLongitude; }
            @Override public void setLastRecordedLongitude(double v) { lastRecordedLongitude = v; }
            @Override public long getLastRecordedTimestamp() { return lastRecordedTimestamp; }
            @Override public void setLastRecordedTimestamp(long v) { lastRecordedTimestamp = v; }
            @Override public double getLastAddressLatitude() { return lastAddressLatitude; }
            @Override public void setLastAddressLatitude(double v) { lastAddressLatitude = v; }
            @Override public double getLastAddressLongitude() { return lastAddressLongitude; }
            @Override public void setLastAddressLongitude(double v) { lastAddressLongitude = v; }
            @Override public long getLastAddressUpdateTime() { return lastAddressUpdateTime; }
            @Override public void setLastAddressUpdateTime(long v) { lastAddressUpdateTime = v; }
            @Override public int getDeviceRefreshSequence() { return deviceRefreshSequence; }
            @Override public int incrementDeviceRefreshSequence() { return ++deviceRefreshSequence; }
            @Override public boolean isRefreshInProgress() { return isRefreshInProgress; }
            @Override public void setRefreshInProgress(boolean inProgress) { isRefreshInProgress = inProgress; }
            @Override public com.RockiotTag.tag.map.google.GoogleLocationService getGoogleLocationService() {
                return googleLocationService;
            }
            @Override public void setGoogleLocationService(
                    com.RockiotTag.tag.map.google.GoogleLocationService service) {
                googleLocationService = service;
            }
            @Override public com.RockiotTag.tag.location.SystemLocationService getSystemLocationService() {
                return systemLocationService;
            }
            @Override public void setSystemLocationService(
                    com.RockiotTag.tag.location.SystemLocationService service) {
                systemLocationService = service;
            }
            @Override public View getScanningIndicator() { return scanningIndicator; }
            @Override public View getBottomNavigation() { return bottomNavigation; }
            @Override public View getBottomInfo() { return bottomInfo; }
            @Override public TextView getBatteryLevelText() { return batteryLevelText; }
            @Override public TextView getDeviceAddressText() { return deviceAddressText; }
            @Override public TextView getUpdateTimeText() { return updateTimeText; }
            @Override public ImageButton getRefreshBtn() { return refreshBtn; }
            @Override public int getCurrentTab() { return currentTab; }
            @Override public int getScanIntensityLevel() { return scanIntensityLevel; }
            @Override public void setScanIntensityLevel(int level) { scanIntensityLevel = level; }
            @Override public DeviceListFragment getDeviceListFragment() { return deviceListFragment; }
            @Override public ProfileFragment getProfileFragment() { return profileFragment; }
            @Override public com.RockiotTag.tag.util.SafeHandler getTrackRefreshHandler() {
                return trackRefreshHandler;
            }
            @Override public void setTrackRefreshHandler(com.RockiotTag.tag.util.SafeHandler handler) {
                trackRefreshHandler = handler;
            }
            @Override public Runnable getTrackRefreshRunnable() { return trackRefreshRunnable; }
            @Override public void setTrackRefreshRunnable(Runnable runnable) { trackRefreshRunnable = runnable; }
            @Override public void changeLanguage(String languageCode) {
                MainActivity.this.changeLanguage(languageCode);
            }
            @Override public void onMapProviderChanged(String newMapProvider, int toastMessageResId) {
                MainActivity.this.onMapProviderChanged(newMapProvider, toastMessageResId);
            }
            @Override public void showBottomInfo() { MainActivity.this.showBottomInfo(); }
            @Override public void selectDevice(TagDevice device) { MainActivity.this.selectDevice(device); }
            @Override public void updateDeviceNameWithTag(String name, String tag) {
                MainActivity.this.updateDeviceNameWithTag(name, tag);
            }
            @Override public void showCoordinatesAndGeocode(double latitude, double longitude, boolean forceRefresh) {
                MainActivity.this.showCoordinatesAndGeocode(latitude, longitude, forceRefresh);
            }
            @Override public void moveCameraToDevicePosition(double latitude, double longitude) {
                MainActivity.this.moveCameraToDevicePosition(latitude, longitude);
            }
            @Override public void resetAddressCache() { MainActivity.this.resetAddressCache(); }
            @Override public void updateDeviceUIDefault() { MainActivity.this.updateDeviceUIDefault(); }
            @Override public void refreshMapWithCurrentDevice(boolean force) {
                MainActivity.this.refreshMapWithCurrentDevice(force);
            }
            @Override public void updateCustomCompassRotation(float bearing) {
                MainActivity.this.updateCustomCompassRotation(bearing);
            }
            @Override public void autoLocateToDevice() { MainActivity.this.autoLocateToDevice(); }
            @Override public void locateToDevicePosition(boolean setUserLocated) {
                MainActivity.this.locateToDevicePosition(setUserLocated);
            }
            @Override public void performDeviceRefresh(boolean showToast) {
                MainActivity.this.performDeviceRefresh(showToast);
            }
            @Override public void updateCurrentLocationOnMap() { MainActivity.this.updateCurrentLocationOnMap(); }
            @Override public void notifyFragmentsThemeChanged(boolean isDarkMode) {
                if (deviceListFragment != null) deviceListFragment.applyTheme(isDarkMode);
                if (profileFragment != null) profileFragment.applyTheme(isDarkMode);
            }
            @Override public void requestPermissions(String[] permissions, int requestCode) {
                ActivityCompat.requestPermissions(MainActivity.this, permissions, requestCode);
            }
            @Override public void refreshProfileFragment() {
                if (profileFragment != null) profileFragment.onResume();
            }
            @Override public void refreshDeviceListFragment() {
                if (deviceListFragment != null) deviceListFragment.onResume();
            }
            @Override public void clearMapMarkers() {
                if (deviceLocationMarker != null) {
                    if (mapAdapter != null) mapAdapter.removeObject(deviceLocationMarker);
                    deviceLocationMarker = null;
                }
            }
            @Override public void resetDeviceUIToDefault() {
                updateDeviceNameWithTag(getString(R.string.no_device_selected), null);
                showBottomInfo();
                batteryLevelText.setText(getString(R.string.battery_level_empty));
                deviceAddressText.setText(getString(R.string.position_empty));
                updateTimeText.setText(getString(R.string.last_update_empty));
            }
            @Override public void selectFirstDeviceAndRefresh() {
                if (databaseHelper != null) {
                    android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
                    String boundDevicesJson = prefs.getString("bound_devices", null);
                    if (boundDevicesJson != null && !boundDevicesJson.isEmpty()) {
                        try {
                            com.google.gson.Gson gson = new com.google.gson.Gson();
                            com.google.gson.reflect.TypeToken<java.util.List<DeviceApiService.BoundDevice>> tokenType =
                                new com.google.gson.reflect.TypeToken<java.util.List<DeviceApiService.BoundDevice>>() {};
                            java.util.List<DeviceApiService.BoundDevice> boundDevices =
                                gson.fromJson(boundDevicesJson, tokenType.getType());
                            if (boundDevices != null && !boundDevices.isEmpty()) {
                                String firstDeviceNum = boundDevices.get(0).getDeviceNum();
                                LogUtil.d(TAG, "First bound device deviceNum: " + firstDeviceNum);
                                TagDevice firstDevice = databaseHelper.getDeviceByDeviceNum(firstDeviceNum);
                                if (firstDevice != null) {
                                    LogUtil.d(TAG, "Auto-selecting first bound device: " + firstDevice.getName());
                                    selectDevice(firstDevice);
                                } else {
                                    Log.w(TAG, "First bound device not found in database: " + firstDeviceNum);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing bound devices: " + e.getMessage(), e);
                        }
                    } else {
                        LogUtil.d(TAG, "No bound devices found in SharedPreferences");
                    }
                }
            }
            @Override public void invalidateRefreshRequests() {
                deviceRefreshSequence++;
                isRefreshInProgress = false;
                LogUtil.d(TAG, "Invalidated all refresh requests, new seq: " + deviceRefreshSequence);
            }
            @Override public void onScanIntensitySelected(int level) {
                scanIntensityLevel = level;
                switch (level) {
                    case 1:
                        if (bleHelper != null) bleHelper.startSingleScanWithCheck();
                        break;
                    case 2:
                        if (bleHelper != null) bleHelper.startContinuousScanWithCheck();
                        break;
                }
            }
            @Override public void onLoginSuccess(String token, String username, String nickname, String email) {
                if (authHelper != null) authHelper.fetchBoundDevicesAfterLogin(token);
            }
            @Override public void onUpdateMapMarker(TagDevice device) {
                if (crowdSourceMapHelper != null) crowdSourceMapHelper.updateMapMarker(device);
            }
            @Override public String getVersionName() {
                try {
                    return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                } catch (Exception e) {
                    return "unknown";
                }
            }
            @Override public int getVersionCode() {
                try {
                    return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                } catch (Exception e) {
                    return 0;
                }
            }
            @Override public String getString(int resId) { return MainActivity.this.getString(resId); }
            @Override public String getString(int resId, Object... args) {
                return MainActivity.this.getString(resId, args);
            }
        };
    }

    private void openDeviceList() {
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(intent, REQUEST_DEVICE_LIST);
    }

    private void openGeofenceSettings() {
        Intent intent = new Intent(this, GeofenceActivity.class);
        startActivity(intent);
    }

    private void showMenuOptions() {
        dialogHelper.showMenuOptions(
            () -> openGeofenceSettings(),
            () -> showBluetoothEnhanceOptions(),
            () -> showVersionInfo()
        );
    }

    public void showMapSwitchOptions() {
        dialogHelper.showMapSwitchOptions();
    }

    public void selectDevice(TagDevice device) {
        if (selectDeviceHelper != null) selectDeviceHelper.selectDevice(device);
    }
    

    /**
     * 在地图上更新设备标记（供 MapViewModel 观察者调用）
     */
    private void updateDeviceMarkerOnMap(TagDevice device) {
        if (mapHelper != null) mapHelper.updateDeviceMarkerOnMap(device);
    }

    /**
     * 更新设备UI（统一方法 - 委托给 MainDeviceDisplayHelper）
     */
    private void updateDeviceUI(NewApiService.DeviceInfo deviceInfo) {
        if (deviceDisplayHelper != null) deviceDisplayHelper.updateDeviceUI(deviceInfo);
    }

    /**
     * 更新设备UI（统一方法）
     */
    private void updateDeviceUIWithLatest(NewApiService.DeviceInfo deviceInfo, boolean moveCamera) {
        if (deviceDisplayHelper != null) {
            deviceDisplayHelper.updateDeviceUIWithLatest(deviceInfo, moveCamera);
        }
    }

    /**
     * 更新设备UI（不移动相机，用于后台刷新）
     */
    private void updateDeviceUIWithoutCameraMove(NewApiService.DeviceInfo deviceInfo) {
        if (deviceDisplayHelper != null) {
            deviceDisplayHelper.updateDeviceUIWithoutCameraMove(deviceInfo);
        }
    }

    /**
     * 更新设备UI为默认值
     */
    private void updateDeviceUIDefault() {
        if (deviceDisplayHelper != null) deviceDisplayHelper.updateDeviceUIDefault();
    }

    /**
     * 切换设备时重置地址缓存
     */
    private void resetAddressCache() {
        if (deviceDisplayHelper != null) deviceDisplayHelper.resetAddressCache();
    }

    /**
     * 立即显示经纬度坐标，并异步启动逆地理编码
     * 如果坐标未变化且已有地址，不覆盖已有地址
     */
    private void showCoordinatesAndGeocode(double latitude, double longitude, boolean forceRefresh) {
        if (latitude == 0 || longitude == 0) {
            return;
        }
        // 坐标未变化且非强制刷新时，不覆盖已有地址
        boolean coordsChanged = Math.abs(latitude - lastAddressLatitude) > 0.000001
                             || Math.abs(longitude - lastAddressLongitude) > 0.000001;
        if (forceRefresh || coordsChanged) {
            if (deviceAddressText != null) {
                deviceAddressText.setText(getString(R.string.position_with_coordinates, latitude, longitude));
            }
        }
        getAddressFromLocation(latitude, longitude, forceRefresh);
    }

    /**
     * 移动地图相机到设备位置（高德/谷歌均支持，尊重 isUserLocated 标志）
     * 默认不强制移动：若用户已手动定位，将跳过移动。
     */
    private void moveCameraToDevicePosition(double latitude, double longitude) {
        if (mapHelper != null) mapHelper.moveCameraToDevicePosition(latitude, longitude);
    }

    private void moveCameraToDevicePosition(double latitude, double longitude, boolean force) {
        if (mapHelper != null) mapHelper.moveCameraToDevicePosition(latitude, longitude, force);
    }

    /**
     * 根据经纬度进行逆地理编码，获取具体地址
     * @param latitude 纬度（WGS84坐标系）- 已经过校验的正确坐标
     * @param longitude 经度（WGS84坐标系）- 已经过校验的正确坐标
     * @param forceRefresh 是否强制刷新（忽略缓存）
     */
    private void getAddressFromLocation(double latitude, double longitude, boolean forceRefresh) {
        MainGeocodingHelper.getAddressFromLocation(
                viewModel, mapAdapter, latitude, longitude, forceRefresh, getGeocodingLanguageCode());
    }

    public void showLanguageOptions() {
        dialogHelper.showLanguageOptions();
    }

    private void showVersionInfo() {
        dialogHelper.showVersionInfo();
    }

    private void changeLanguage(String languageCode) {
        String current = LanguageUtils.getSavedLanguage(this);
        if (languageCode != null && languageCode.equals(current)) {
            return;
        }

        LanguageUtils.saveLanguage(this, languageCode);
        prepareForActivityRecreate();

        getSharedPreferences("app_settings", MODE_PRIVATE)
                .edit()
                .putInt("restore_tab_after_config_change", currentTab)
                .putBoolean("show_language_changed_toast", true)
                .putBoolean("language_just_changed", true)
                .apply();

        recreate();
    }

    /** recreate 前统一释放地图/定位/延迟任务，避免切换语言或地图后旧回调闪退 */
    private void prepareForActivityRecreate() {
        if (mapInitHelper != null) {
            mapInitHelper.cancelPendingTasks();
        }
        if (mapHelper != null) {
            mapHelper.cancelPendingTasks();
        }
        if (pendingScanIndicatorHideRunnable != null) {
            mainUiHandler.removeCallbacks(pendingScanIndicatorHideRunnable);
            pendingScanIndicatorHideRunnable = null;
        }
        com.RockiotTag.tag.map.MapLayerController.cancelPendingRetries();
        releaseLocationServices();
        stopTrackRefresh();
        deviceRefreshSequence++;
        isRefreshInProgress = false;
        if (locationOptimizationManager != null) {
            locationOptimizationManager.prepareForActivityRecreate();
        }
        if (mapManager != null) {
            mapManager.releasePendingCallbacks();
        }
    }

    private void onMapProviderChanged(String newMapProvider, int toastMessageResId) {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String currentProvider = mapAdapter != null
                ? mapAdapter.getProvider()
                : prefs.getString("map_provider", "amap");
        if (newMapProvider.equals(currentProvider)) {
            return;
        }

        if (databaseHelper != null) {
            databaseHelper.cleanMapModeAddressCache(newMapProvider);
        }

        prepareForActivityRecreate();

        prefs.edit()
                .putString("map_provider", newMapProvider)
                .putBoolean("map_provider_just_changed", true)
                .putInt("pending_map_switch_toast", toastMessageResId)
                .putInt("restore_tab_after_config_change", currentTab)
                .apply();

        recreate();
    }

    private void releaseLocationServices() {
        if (systemLocationService != null) {
            systemLocationService.onDestroy();
            systemLocationService = null;
        }
        if (googleLocationService != null) {
            googleLocationService.onDestroy();
            googleLocationService = null;
        }
        locationServicesStarted = false;
    }

    private void applyLanguage(String languageCode) {
        java.util.Locale locale;
        if (languageCode.equals("pt-rBR")) {
            locale = new java.util.Locale("pt", "BR");
        } else {
            locale = new java.util.Locale(languageCode);
        }
        java.util.Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }
    
    /**
     * 获取地理编码服务使用的语言代码
     * @return Google Geocoding API 支持的语言代码
     */
    private String getGeocodingLanguageCode() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String languageCode = prefs.getString("language", "zh");
        
        LogUtil.d(TAG, "App language setting: " + languageCode);
        
        // 将应用的语言代码转换为 Google Geocoding API 支持的语言代码
        switch (languageCode) {
            case "zh":
                return "zh-CN";  // 中文（简体）
            case "en":
                return "en";     // 英语
            case "pt-rBR":
                return "pt-BR";  // 巴西葡萄牙语
            case "ru":
                return "ru";     // 俄语
            case "hi":
                return "hi";     // 印地语
            case "tr":
                return "tr";     // 土耳其语
            default:
                return "en";     // 默认使用英语
        }
    }

    private void toggleDarkMode() {
        themeHelper.toggleDarkMode();
    }

    private void triggerBuzzer() {
        // MVVM - 使用 ViewModel 的 UseCase 触发蜂鸣器
        viewModel.triggerBuzzer();
        ToastHelper.show(this, R.string.trigger_buzzer);
    }

    /**
     * 初始化底部导航 Helper（Fragment 创建与 Tab 切换）
     */
    private void initTabHelper() {
        tabHelper = new MainTabHelper(
                new MainTabHelper.Host() {
                    @Override
                    public AppCompatActivity getActivity() { return MainActivity.this; }
                    @Override
                    public TagDevice getSelectedDevice() { return selectedDevice; }
                    @Override
                    public int getCurrentTab() { return currentTab; }
                    @Override
                    public void setCurrentTab(int tab) { currentTab = tab; }
                    @Override
                    public View getRefreshBtn() { return refreshBtn; }
                    @Override
                    public View getMapTypeBtn() { return mapTypeBtn; }
                    @Override
                    public View getLocateBtn() { return locateBtn; }
                    @Override
                    public View getCustomCompass() { return customCompass; }
                    @Override
                    public View getBottomInfo() { return bottomInfo; }
                    @Override
                    public void onHomeFragmentVisibleInternal() { updateHomeUIVisibility(true); }
                },
                tabHome, tabList, tabTrack, tabProfile,
                tabHomeIcon, tabListIcon, tabTrackIcon, tabProfileIcon,
                tabHomeText, tabListText, tabTrackText, tabProfileText
        );
        tabHelper.initBottomNavigation();
        tabHelper.initFragments();
        tabHelper.restoreTabIfNeeded();
        tabHelper.syncTabToCurrentState();
        homeFragment = tabHelper.getHomeFragment();
        deviceListFragment = tabHelper.getDeviceListFragment();
        trackFragment = tabHelper.getTrackFragment();
        profileFragment = tabHelper.getProfileFragment();
    }

    public void switchToTab(int tabIndex) {
        if (tabHelper != null) {
            tabHelper.switchToTab(tabIndex);
        }
    }

    private void updateTabSelection(int tabIndex) {
        if (tabHelper != null) {
            tabHelper.updateTabSelection(tabIndex);
        }
    }

    private void updateHomeUIVisibility(boolean visible) {
        if (tabHelper != null) {
            tabHelper.updateHomeUIVisibility(visible);
        }
    }

    private void showBottomInfo() {
        if (tabHelper != null) {
            tabHelper.showBottomInfo();
        }
    }

    private void updateCustomCompassRotation(float bearing) {
        if (tabHelper != null) {
            tabHelper.updateCustomCompassRotation(bearing);
        }
    }

    public void onHomeFragmentVisible() {
        if (tabHelper != null) {
            tabHelper.onHomeFragmentVisible();
        }
    }

    public boolean isCurrentTabHome() {
        return tabHelper != null && tabHelper.isCurrentTabHome();
    }

    public int getCurrentTabIndex() {
        return currentTab;
    }

    /**
     * 获取当前选中的设备（供Fragment调用）
     */
    public TagDevice getSelectedDevice() {
        return selectedDevice;
    }

    /**
     * 打开添加设备页面（供Fragment调用）
     */
    public void openAddDevice() {
        if (!BoundDevicesHelper.isLoggedIn(this)) {
            ToastHelper.show(this, R.string.please_login_first);
            return;
        }
        Intent intent = new Intent(this, AddDeviceActivity.class);
        startActivity(intent);
    }

    /**
     * 设备昵称在列表中编辑成功后，同步更新首页显示。
     */
    public void onDeviceNicknameUpdated(String deviceId, String nickname, String tag) {
        if (deviceId == null || nickname == null) {
            return;
        }
        String selectedId = getSharedPreferences("app_settings", MODE_PRIVATE)
                .getString("selected_device_id", "");
        if (!deviceId.equals(selectedId)) {
            return;
        }
        if (selectedDevice != null) {
            selectedDevice.setName(nickname);
            if (tag != null) {
                selectedDevice.setTag(tag);
            }
        }
        updateDeviceNameWithTag(nickname, tag);
    }

    /**
     * 设备在列表中解绑后，同步清除或切换首页当前选中设备。
     */
    public void onDeviceUnbound(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String selectedId = prefs.getString("selected_device_id", "");
        boolean wasSelected = deviceId.equals(selectedId)
                || (selectedDevice != null && deviceId.equals(selectedDevice.getDeviceId()));
        if (!wasSelected) {
            return;
        }

        prefs.edit().remove("selected_device_id").apply();
        selectedDevice = null;
        clearDeviceInfo();

        if (databaseHelper != null) {
            java.util.List<TagDevice> remaining =
                    BoundDevicesHelper.loadDisplayedDevices(this, databaseHelper);
            if (!remaining.isEmpty()) {
                selectDevice(remaining.get(0));
            }
        }
    }

    /**
     * 切换深色模式（不重建Activity，手动应用主题颜色）
     */
    public void toggleDarkMode(boolean isDarkMode) {
        themeHelper.toggleDarkMode(isDarkMode);
    }

    /**
     * 手动应用深色/浅色模式，不重建Activity
     */
    public void applyDarkMode(boolean isDarkMode) {
        int bgColor = isDarkMode ?
            getResources().getColor(R.color.dark_background, null) :
            getResources().getColor(R.color.background, null);
        int surfaceColor = isDarkMode ?
            getResources().getColor(R.color.dark_surface, null) :
            getResources().getColor(R.color.surface, null);
        int onSurfaceColor = isDarkMode ?
            getResources().getColor(R.color.dark_onSurface, null) :
            getResources().getColor(R.color.onSurface, null);
        int topBarColor = isDarkMode ?
            getResources().getColor(R.color.dark_top_bar_background, null) :
            getResources().getColor(R.color.top_bar_background, null);
        int cardColor = isDarkMode ?
            getResources().getColor(R.color.dark_card_background, null) :
            getResources().getColor(R.color.card_background, null);
        int textSecColor = isDarkMode ?
            getResources().getColor(R.color.dark_text_secondary, null) :
            getResources().getColor(R.color.text_secondary, null);

        // 选中/未选中Tab颜色
        int selectedColor = getResources().getColor(R.color.brand_primary, null);
        int unselectedColor = isDarkMode ?
            getResources().getColor(R.color.dark_text_secondary, null) :
            getResources().getColor(R.color.text_secondary, null);

        // 应用到根视图
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) rootView.setBackgroundColor(bgColor);

        // 应用到底部导航栏背景
        if (bottomNavigation != null) {
            bottomNavigation.setBackgroundColor(topBarColor);
        }

        // 应用到导航栏Tab文字和图标颜色
        updateTabColors(selectedColor, unselectedColor);

        // 应用到底部信息卡片
        if (bottomInfo != null) {
            try {
                ((androidx.cardview.widget.CardView) bottomInfo).setCardBackgroundColor(cardColor);
            } catch (Exception e) {
                // 忽略类型转换异常
            }
        }

        // 应用到信息卡片内的文字
        if (batteryLevelText != null) batteryLevelText.setTextColor(onSurfaceColor);
        if (deviceAddressText != null) deviceAddressText.setTextColor(onSurfaceColor);
        if (updateTimeText != null) updateTimeText.setTextColor(onSurfaceColor);

        // 更新状态栏
        com.RockiotTag.tag.util.StatusBarHelper.setupStatusBar(this, isDarkMode);

        // 深色模式：通过适配器设置地图样式
        try {
            if (mapAdapter != null) {
                mapAdapter.setDarkMapStyle(isDarkMode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying dark map style: " + e.getMessage());
        }

        // 通知Fragment更新
        if (tabHelper != null) {
            tabHelper.notifyFragmentsThemeChanged(isDarkMode);
        }

        com.RockiotTag.tag.util.MapFloatingButtonHelper.applyMainScreenButtons(this, isDarkMode);
    }

    /**
     * 更新导航栏Tab文字和图标颜色
     */
    private void updateTabColors(int selectedColor, int unselectedColor) {
        if (tabHelper != null) {
            tabHelper.updateTabColors(selectedColor, unselectedColor);
        }
    }

    /**
     * 显示登录对话框
     */
    public void showLoginDialog() {
        dialogHelper.showLoginDialog();
    }

    /**
     * 显示注册对话框
     */
    public void showRegisterDialog() {
        dialogHelper.showRegisterDialog();
    }

    /**
     * 登录成功后获取用户绑定的设备列表
     * @param token Bearer Token
     */
    private void fetchBoundDevicesAfterLogin(String token) {
        authHelper.fetchBoundDevicesAfterLogin(token);
    }

    /**
     * 同步绑定设备到本地数据库
     * @param boundDevices 从服务器获取的绑定设备列表
     */
    public void syncBoundDevicesToLocalDatabase(List<DeviceApiService.BoundDevice> boundDevices) {
        authHelper.syncBoundDevicesToLocalDatabase(boundDevices);
    }

    /**
     * 退出登录后刷新设备列表
     * 清除选中设备，刷新DeviceListFragment显示空列表
     */
    public void refreshDeviceListAfterLogout() {
        authHelper.refreshDeviceListAfterLogout();
    }

    private void startRefreshAnimation() {
        if (deviceRefreshHelper != null) deviceRefreshHelper.startRefreshAnimation();
    }

    private void stopRefreshAnimation() {
        if (deviceRefreshHelper != null) deviceRefreshHelper.stopRefreshAnimation();
    }

    private void performDeviceRefresh(boolean showToast) {
        if (deviceRefreshHelper != null) deviceRefreshHelper.performDeviceRefresh(showToast);
    }

    private void refreshDeviceLocation() {
        if (deviceRefreshHelper != null) deviceRefreshHelper.refreshDeviceLocation();
    }

    private void checkPermissions() {
        MainPermissionHelper.checkPermissions(new MainPermissionHelper.Host() {
            @Override
            public AppCompatActivity getActivity() { return MainActivity.this; }
            @Override
            public void showPermissionRationale(String[] permissions) {
                MainActivity.this.showPermissionRationale(permissions);
            }
            @Override
            public void requestPermissions(String[] permissions, int requestCode) {
                ActivityCompat.requestPermissions(MainActivity.this, permissions, requestCode);
            }
            @Override
            public void onPermissionsGranted() {
                initLocationAndBleIfPermitted();
            }
        }, REQUEST_PERMISSIONS);
    }

    private void showPermissionRationale(final String[] permissions) {
        dialogHelper.showPermissionRationale(permissions, REQUEST_PERMISSIONS);
    }

    /**
     * 显示蓝牙增强扫描强度选择对话框
     */
    private void showBluetoothEnhanceOptions() {
        dialogHelper.showBluetoothEnhanceOptions(scanIntensityLevel);
    }

    /**
     * 启动单次扫描（低强度）：扫描成功更新UI后自动停止
     */
    private void startSingleScanWithCheck() {
        bleHelper.startSingleScanWithCheck();
    }

    /**
     * 启动持续循环扫描（高强度）：扫描10秒，停止10秒，循环
     */
    private void startContinuousScanWithCheck() {
        bleHelper.startContinuousScanWithCheck();
    }

    /**
     * 根据当前扫描强度级别应用扫描行为
     */
    private void applyScanIntensity() {
        if (bleHelper != null) {
            bleHelper.applyScanIntensity(scanIntensityLevel);
        }
    }

    private void startBLEScanning() {
        bleHelper.startBLEScanning();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEVICE_LIST && resultCode == RESULT_OK) {
            LogUtil.d(TAG, "=== onActivityResult: Device list updated ===");
            
            String selectedDeviceId = "";
            
            // 优先使用 Intent 传递的数据，确保是最新选择的设备
            if (data != null && data.hasExtra("selected_device_id")) {
                selectedDeviceId = data.getStringExtra("selected_device_id");
                LogUtil.d(TAG, "Got device ID from Intent: " + selectedDeviceId);
            } else {
                // 兼容旧版本，从 SharedPreferences 读取
                android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
                selectedDeviceId = prefs.getString("selected_device_id", "");
                LogUtil.d(TAG, "Got device ID from SharedPreferences: " + selectedDeviceId);
            }
            
            if (!selectedDeviceId.isEmpty()) {
                LogUtil.d(TAG, "Reloading selected device from database: " + selectedDeviceId);
                
                // 从数据库重新加载设备信息
                TagDevice updatedDevice = databaseHelper.getDevice(selectedDeviceId);
                if (updatedDevice != null) {
                    LogUtil.d(TAG, "Device loaded: name=" + updatedDevice.getName() + ", tag=" + updatedDevice.getTag() + 
                          ", lat=" + updatedDevice.getLatitude() + ", lng=" + updatedDevice.getLongitude());
                    
                    // 重置用户手动定位标志，允许地图跟随新设备
                    isUserLocated = false;
                    
                    // 调用 selectDevice 方法来完整更新 UI 状态
                    selectDevice(updatedDevice);
                } else {
                    Log.w(TAG, "Device not found in database (may have been deleted): " + selectedDeviceId);
                    // 设备已被删除，清除 UI
                    clearDeviceInfo();
                }
            } else {
                LogUtil.d(TAG, "No selected device ID found, clearing device info");
                clearDeviceInfo();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean useAmap = amapLifecycleStarted;
        if (mapView != null && useAmap) {
            try {
                mapView.onResume();
            } catch (Throwable t) {
                Log.e(TAG, "mapView.onResume failed", t);
            }
        }
        if (mapAdapter != null) {
            try {
                mapAdapter.onResume();
            } catch (Throwable t) {
                Log.e(TAG, "mapAdapter.onResume failed", t);
            }
        }
        if (mapManager != null && mapManager.isGoogleMap()) {
            mapManager.ensureGoogleMapVisible();
        }

        LogUtil.d(TAG, "=== onResume called, currentTab=" + currentTab + " ===");

        if (tabHelper != null) {
            tabHelper.handlePendingTabSwitch();
        }
        isUserLocated = false;
        LogUtil.d(TAG, "Reset isUserLocated flag on resume");
        
        // 重置 MapManager 的用户交互状态（针对谷歌地图）
        if (mapManager != null) {
            mapManager.resetUserInteractionState();
            LogUtil.d(TAG, "Reset MapManager user interaction state on resume");
        }
        
        // 【关键修复】在 onResume 中根据扫描强度级别决定是否重新启动蓝牙扫描
        if (scanIntensityLevel > 0 && locationOptimizationManager != null && locationOptimizationManager.isOptimizationEnabled()) {
            LogUtil.d(TAG, "Restarting Bluetooth scanning in onResume (intensity=" + scanIntensityLevel + ")...");
            applyScanIntensity();
            LogUtil.d(TAG, "✓ Bluetooth scanning restarted in onResume");
        } else {
            LogUtil.d(TAG, "Scan intensity is OFF or manager unavailable, skipping Bluetooth scan restart in onResume");
        }
        
        // 从数据库重新加载设备信息，确保立即显示最新的本地数据
        if (selectedDevice != null && databaseHelper != null) {
            String deviceId = selectedDevice.getDeviceId();
            LogUtil.d(TAG, "Refreshing device info for deviceId: " + deviceId);
            
            try {
                TagDevice updatedDevice = databaseHelper.getDevice(deviceId);
                if (updatedDevice != null) {
                    // 如果设备信息发生变化（例如名称修改），则更新内存中的设备
                    if (!java.util.Objects.equals(updatedDevice.getName(), selectedDevice.getName())
                            || !java.util.Objects.equals(updatedDevice.getTag(), selectedDevice.getTag())) {
                        selectedDevice = updatedDevice;
                        LogUtil.d(TAG, "Device info changed, updating memory: name=" + selectedDevice.getName() + ", tag=" + selectedDevice.getTag());
                        updateDeviceNameWithTag(selectedDevice.getName(), selectedDevice.getTag());
                    }
                                
                    // 重置标志位，允许新的请求
                    isFetchingDeviceInfo = false;
                    
                    // 修复：如果设备已有有效坐标，立即移动相机到设备位置
                    if (updatedDevice.getLatitude() != 0 && updatedDevice.getLongitude() != 0) {
                        LogUtil.d(TAG, "Device has local coordinates, moving camera to device position on resume");
                        refreshMapWithCurrentDevice(false);
                    }
                                
                    // 从服务器获取最新的设备位置、电量等信息
                    String deviceNum = selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : deviceId;
                    viewModel.fetchDeviceInfo(deviceNum);
                } else {
                    Log.e(TAG, "Device not found in database: " + deviceId);
                }
            } catch (IllegalStateException e) {
                // 数据库连接池已关闭，跳过刷新
                Log.w(TAG, "Database connection pool closed, skipping refresh in onResume");
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing device info in onResume: " + e.getMessage(), e);
            }
        } else {
            if (selectedDevice == null) {
                LogUtil.d(TAG, "No selected device, skipping refresh");
            }
            if (databaseHelper == null) {
                Log.w(TAG, "databaseHelper is null, skipping refresh");
            }
        }
        
        LanguageIndicatorHelper.refreshMainActivity(this);
        startTrackRefresh();
    }

    @Override
    protected void onPause() {
        if (mapView != null && amapLifecycleStarted) {
            mapView.onPause();
        }
        if (mapAdapter != null) {
            mapAdapter.onPause();
        }
        super.onPause();
        stopTrackRefresh();
        
        // 【关键】只停止时间刷新，不停止蓝牙扫描（蓝牙扫描应该持续运行）
        if (locationOptimizationManager != null) {
            locationOptimizationManager.stopTimeRefresh();
            LogUtil.d(TAG, "Time refresh stopped in onPause, but Bluetooth scanning continues");
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        
        // 【新增】在后台或退出时停止蓝牙扫描（onResume会根据开关状态决定是否恢复）
        if (locationOptimizationManager != null) {
            locationOptimizationManager.stopBluetoothScanning();
            LogUtil.d(TAG, "Bluetooth scanning stopped in onStop (app going to background)");
        }
    }

    private void initTrackRefresh() {
        if (deviceRefreshHelper != null) deviceRefreshHelper.initTrackRefresh();
    }

    private void startTrackRefresh() {
        if (deviceRefreshHelper != null) deviceRefreshHelper.startTrackRefresh();
    }

    private void stopTrackRefresh() {
        if (deviceRefreshHelper != null) deviceRefreshHelper.stopTrackRefresh();
    }

    private void restoreSelectedDeviceForMapInit() {
        if (deviceSelectionHelper != null) deviceSelectionHelper.restoreSelectedDeviceForMapInit();
    }

    private void restoreSelectedDevice() {
        if (deviceSelectionHelper != null) deviceSelectionHelper.restoreSelectedDevice();
    }

    private void clearDeviceInfo() {
        if (deviceSelectionHelper != null) deviceSelectionHelper.clearDeviceInfo();
    }

    private void syncDevicesFromApiAndSelectFirst() {
        if (deviceSelectionHelper != null) deviceSelectionHelper.syncDevicesFromApiAndSelectFirst();
    }

    private void selectFirstDevice(List<TagDevice> devices) {
        if (deviceSelectionHelper != null) deviceSelectionHelper.selectFirstDevice(devices);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_tab", currentTab);
        if (mapView != null && amapLifecycleStarted
                && mapAdapter != null
                && com.RockiotTag.tag.map.MapAdapterFactory.PROVIDER_AMAP.equals(mapAdapter.getProvider())) {
            mapView.onSaveInstanceState(outState);
        }
    }

    /**
     * 更新当前位置到地图（通用方法，适配高德和谷歌地图）
     */
    private void updateCurrentLocationOnMap() {
        if (locationHelper != null) locationHelper.updateCurrentLocationOnMap();
    }

    @Override
    protected void onDestroy() {
        if (mapInitHelper != null) {
            mapInitHelper.cancelPendingTasks();
        }
        if (mapHelper != null) {
            mapHelper.cancelPendingTasks();
        }

        // 1. 清理定位优化管理器（新增）
        if (locationOptimizationManager != null) {
            locationOptimizationManager.prepareForActivityRecreate();
            if (!isChangingConfigurations()) {
                locationOptimizationManager.cleanup();
            }
            locationOptimizationManager = null;
        }
        
        // 2. 停止并清理轨迹刷新 Handler
        stopTrackRefresh();
        if (trackRefreshHandler != null) {
            trackRefreshHandler.cleanup();
            trackRefreshHandler = null;
        }
        
        // 3. 清理定位服务
        if (systemLocationService != null) {
            systemLocationService.onDestroy();
            systemLocationService = null;
        }
        locationServicesStarted = false;
        if (amapGeocoder != null) {
            amapGeocoder.onDestroy();
            amapGeocoder = null;
        }
        
        // 4. 清理谷歌地图服务
        if (googleLocationService != null) {
            googleLocationService.onDestroy();
            googleLocationService = null;
        }
        if (googleGeocoderService != null) {
            googleGeocoderService.onDestroy();
            googleGeocoderService = null;
        }
        
        // 5. 清理 BLE 资源
        if (bleManager != null) {
            bleManager.stopScanning();
            bleManager.disconnect();
            bleManager = null;
        }
        
        // 6. 清理地图适配器资源
        if (mapAdapter != null) {
            mapAdapter.onDestroy();
            mapAdapter = null;
        }
        if (mapView != null && amapLifecycleStarted) {
            if (!isChangingConfigurations()) {
                mapView.onDestroy();
            }
            mapView = null;
        }

        // 7. 清理 MapManager 引用
        if (mapManager != null) {
            mapManager.releasePendingCallbacks();
            mapManager = null;
        }
        
        // 8. 释放数据库引用（单例不 close，随进程生命周期存在）
        databaseHelper = null;
        
        // 9. 清理 API 服务
        if (apiService != null) {
            apiService = null;
        }
        
        // 10. 清理 CrowdSourcingManager
        if (crowdSourcingManager != null) {
            crowdSourcingManager = null;
        }
        
        // 11. 清理 UnboundDeviceManager
        if (unboundDeviceManager != null) {
            unboundDeviceManager = null;
        }
        
        // 12. 移除所有 LiveData 观察者（防止内存泄漏）
        // 注意：ViewModel 本身不需要移除观察者，Activity 销毁时会自动清理
        // 但为了明确释放，我们可以将 ViewModel 引用置 null
        viewModel = null;
        bleViewModel = null;
        mapViewModel = null;
        
        // 13. 清空引用
        selectedDevice = null;
        currentLocationMarker = null;
        deviceLocationMarker = null;
        aMap = null;
        googleMapFragment = null;
        
        LogUtil.d(TAG, "MainActivity resources fully cleaned up");
        mainUiHandler.removeCallbacks(pendingLocationBleInitRunnable);
        mainUiHandler.removeCallbacksAndMessages(null);
        pendingScanIndicatorHideRunnable = null;
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) {
            return;
        }

        if (MainPermissionHelper.hasAllPermissions(this)) {
            ToastHelper.show(this, R.string.permission_granted);
            initLocationAndBleIfPermitted();
            return;
        }

        String[] stillMissing = MainPermissionHelper.getMissingPermissions(this);
        if (stillMissing.length > 0) {
            boolean permanentlyDenied = false;
            for (String permission : stillMissing) {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    permanentlyDenied = true;
                    break;
                }
            }
            if (permanentlyDenied) {
                showPermissionSettingsDialog();
            } else {
                ToastHelper.show(this, R.string.permission_denied);
                ActivityCompat.requestPermissions(this, stillMissing, REQUEST_PERMISSIONS);
            }
        } else {
            ToastHelper.show(this, R.string.permission_denied);
        }
    }

    private void showPermissionSettingsDialog() {
        dialogHelper.showPermissionSettingsDialog();
    }
    
    /**
     * MVVM - 设置 ViewModel 观察者
     */
    private void setupViewModelObservers() {
        MainViewModelObserverHelper.setupObservers(new MainViewModelObserverHelper.Host() {
            @Override public androidx.lifecycle.LifecycleOwner getLifecycleOwner() { return MainActivity.this; }
            @Override public AppCompatActivity getActivity() { return MainActivity.this; }
            @Override public MainViewModel getViewModel() { return viewModel; }
            @Override public BleViewModel getBleViewModel() { return bleViewModel; }
            @Override public MapViewModel getMapViewModel() { return mapViewModel; }
            @Override public UnboundDeviceManager getUnboundDeviceManager() { return unboundDeviceManager; }
            @Override public AMap getAMap() { return aMap; }
            @Override public TagDevice getSelectedDevice() { return selectedDevice; }
            @Override public void setSelectedDevice(TagDevice device) { selectedDevice = device; }
            @Override public boolean isPendingDeviceSelection() { return pendingDeviceSelection; }
            @Override public void setPendingDeviceSelection(boolean pending) { pendingDeviceSelection = pending; }
            @Override public double getLastAddressLatitude() { return lastAddressLatitude; }
            @Override public double getLastAddressLongitude() { return lastAddressLongitude; }
            @Override public TextView getBatteryLevelText() { return batteryLevelText; }
            @Override public TextView getDeviceAddressText() { return deviceAddressText; }
            @Override public TextView getUpdateTimeText() { return updateTimeText; }
            @Override public void updateDeviceNameWithTag(String name, String tag) {
                MainActivity.this.updateDeviceNameWithTag(name, tag);
            }
            @Override public void locateToDevicePosition(boolean setUserLocated) {
                MainActivity.this.locateToDevicePosition(setUserLocated);
            }
            @Override public void updateDeviceMarkerOnMap(TagDevice device) {
                MainActivity.this.updateDeviceMarkerOnMap(device);
            }
            @Override public void showCoordinatesAndGeocode(double lat, double lng, boolean force) {
                MainActivity.this.showCoordinatesAndGeocode(lat, lng, force);
            }
            @Override public void startRefreshAnimation() { MainActivity.this.startRefreshAnimation(); }
            @Override public void stopRefreshAnimation() { MainActivity.this.stopRefreshAnimation(); }
            @Override public String getString(int resId) { return MainActivity.this.getString(resId); }
            @Override public String getString(int resId, Object... args) {
                return MainActivity.this.getString(resId, args);
            }
        });
    }
}