package com.RockiotTag.tag;

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
import android.widget.Toast;

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
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.SupportMapFragment;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_DEVICE_LIST = 101;

    public static int pendingTabSwitch = -1;

    private MapView mapView;
    private AMap aMap;
    private MapManager mapManager;
    private SupportMapFragment googleMapFragment;
    
    // 使用专用的地图服务类（完全隔离）
    private com.RockiotTag.tag.map.amap.AMapLocationService amapLocationService;
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
    private long lastTabClickTime = 0; // 上次点击Tab的时间，用于防止快速重复点击
    private static final long TAB_CLICK_INTERVAL = 500; // Tab点击间隔（毫秒）

    // Fragment
    private HomeFragment homeFragment;
    private DeviceListFragment deviceListFragment;
    private TrackFragment trackFragment;
    private ProfileFragment profileFragment;

    // 蓝牙增强扫描强度：0=关闭，1=低（单次），2=高（持续循环）
    private int scanIntensityLevel = 0;

    private double currentLatitude = 22.543611;
    private double currentLongitude = 113.881944;
    private String currentDeviceName = "tag";
    private Marker currentLocationMarker;
    private Marker deviceLocationMarker;
    private com.google.android.gms.maps.model.Marker googleDeviceLocationMarker; // Google地图设备标记
    private long lastUserInteractionTime = 0;
    private static final long AUTO_RETURN_DELAY = 10000; // 10秒延迟
    private boolean isFirstLocation = true; // 标记是否是第一次定位
    private boolean isUserLocated = false; // 标记用户是否手动点击了定位按钮
    private Device selectedDevice = null; // 当前选中的设备
    private com.RockiotTag.tag.util.SafeHandler trackRefreshHandler;
    private Runnable trackRefreshRunnable;
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
    private static final float ADDRESS_UPDATE_THRESHOLD = 200; // 地址更新阈值（米）
    private long lastAddressUpdateTime = 0; // 上次地址更新时间
    private static final long ADDRESS_UPDATE_INTERVAL = 60 * 1000; // 地址更新间隔（60秒）
    
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
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
            boolean isDarkMode = prefs.getBoolean("dark_mode", false);
            
            Log.d(TAG, "setupStatusBar called, isDarkMode: " + isDarkMode);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                android.view.Window window = getWindow();
                if (window == null) {
                    Log.e(TAG, "Window is null!");
                    return;
                }
                
                // 使用 WindowInsetsControllerCompat 兼容库来设置状态栏
                androidx.core.view.WindowInsetsControllerCompat controller = 
                    androidx.core.view.ViewCompat.getWindowInsetsController(window.getDecorView());
                
                if (controller != null) {
                    if (isDarkMode) {
                        // 夜间模式：黑色背景
                        window.setStatusBarColor(Color.parseColor("#000000"));
                        // 设置浅色图标（白色）
                        controller.setAppearanceLightStatusBars(false);
                        Log.d(TAG, "Night mode: black background with white icons");
                    } else {
                        // 日间模式：白色背景
                        window.setStatusBarColor(Color.parseColor("#FFFFFF"));
                        // 设置深色图标（黑色）
                        controller.setAppearanceLightStatusBars(true);
                        Log.d(TAG, "Day mode: white background with dark icons");
                    }
                } else {
                    Log.w(TAG, "WindowInsetsControllerCompat is null, using fallback method");
                    // Fallback: 直接设置颜色
                    if (isDarkMode) {
                        window.setStatusBarColor(Color.parseColor("#000000"));
                    } else {
                        window.setStatusBarColor(Color.parseColor("#FFFFFF"));
                    }
                    // Fallback: 使用 SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    if (!isDarkMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        getWindow().getDecorView().setSystemUiVisibility(
                            getWindow().getDecorView().getSystemUiVisibility() 
                            | android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in setupStatusBar", e);
        }
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
        
        // 设置全局异常处理器，防止崩溃
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
        });
        
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
            
            Log.d(TAG, "=== STEP 1: super.onCreate DONE ===");

            setContentView(R.layout.activity_main);
            
            Log.d(TAG, "=== STEP 2: setContentView DONE ===");
            
            // 隐藏标题栏
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
            
            // 设置状态栏样式（必须在setContentView之后）
            setupStatusBar();

            // 初始化高德地图隐私合规设置
            try {
                com.amap.api.maps.MapsInitializer.updatePrivacyShow(this, true, true);
                com.amap.api.maps.MapsInitializer.updatePrivacyAgree(this, true);
                com.amap.api.location.AMapLocationClient.updatePrivacyShow(this, true, true);
                com.amap.api.location.AMapLocationClient.updatePrivacyAgree(this, true);
            } catch (Exception e) {
            }
            mapView = findViewById(R.id.mapView);
            Log.d(TAG, "=== STEP 3: mapView findViewById ===");
            if (mapView == null) {
                Log.e(TAG, "!!! mapView is NULL !!!");
                throw new RuntimeException("mapView is null - check layout");
            }
            mapView.onCreate(savedInstanceState);
            Log.d(TAG, "=== STEP 4: mapView.onCreate DONE ===");
            // 地图内边距通过 aMap.setPadding() 设置，避免 mapView.setPadding() 裁剪地图显示区域
            googleMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.google_map_fragment);

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

            initBottomNavigation();
            Log.d(TAG, "=== STEP 5: initBottomNavigation DONE ===");
            initFragments();
            Log.d(TAG, "=== STEP 6: initFragments DONE ===");

            bottomInfo.setVisibility(View.GONE);
            Log.d(TAG, "=== STEP 7: bottomInfo.setVisibility DONE ===");

            // 手动应用深色模式（在所有UI组件初始化之后）
            boolean isDarkMode = getSharedPreferences("app_settings", MODE_PRIVATE).getBoolean("dark_mode", false);
            applyDarkMode(isDarkMode);

            initDatabase();  // 先初始化数据库，以便在地图初始化时能够读取设备信息
            Log.d(TAG, "=== STEP 8: initDatabase DONE ===");
            
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
                Log.d(TAG, "LocationOptimizationManager initialized successfully");
                
                // 设置扫描状态图标为旋转动画
                if (scanStatusIcon != null) {
                    android.graphics.drawable.Drawable drawable = scanStatusIcon.getDrawable();
                    if (drawable instanceof android.graphics.drawable.Animatable) {
                        android.graphics.drawable.Animatable animatable = (android.graphics.drawable.Animatable) drawable;
                        animatable.start();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize LocationOptimizationManager", e);
                locationOptimizationManager = null;
            }

            // 设置位置更新回调，当蓝牙扫描到设备时自动刷新UI
            if (locationOptimizationManager != null) {
                locationOptimizationManager.setLocationUpdateCallback(new com.RockiotTag.tag.integration.LocationOptimizationManager.LocationUpdateCallback() {
                    @Override
                    public void onLocationUpdated(com.RockiotTag.tag.model.DeviceLocation location) {
                    Log.d(TAG, "========== UI Callback: Location Updated ==========");
                    Log.d(TAG, "Source: " + location.getActualSource());
                    Log.d(TAG, "Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude());
                    Log.d(TAG, "Timestamp: " + location.getTimestamp());
                    Log.d(TAG, "DeviceId (MAC): " + location.getDeviceId());
                    Log.d(TAG, "Current selectedDevice: " + (selectedDevice != null ? selectedDevice.getName() : "NULL"));
                    if (selectedDevice != null) {
                        Log.d(TAG, "  selectedDevice.deviceId: " + selectedDevice.getDeviceId());
                        Log.d(TAG, "  selectedDevice.lastSeen: " + selectedDevice.getLastSeen());
                    }
                    
                    // 【关键修复】蓝牙扫描回调总是立即更新时间戳，不管是否有GPS坐标
                    Log.d(TAG, "⚡ Bluetooth scan callback - updating timestamp immediately");
                    runOnUiThread(() -> {
                        try {
                            String deviceMac = location.getDeviceId();
                            Log.d(TAG, "Bluetooth scan for MAC: " + deviceMac);
                            
                            // 【严格检查】必须有选中设备
                            if (selectedDevice == null) {
                                Log.d(TAG, "No selected device, skip update");
                                return;
                            }
                            
                            // 【修复】比较MAC地址，忽略大小写并去除空格
                            String selectedMac = selectedDevice.getMac();
                            Log.d(TAG, "Selected device MAC: [" + selectedMac + "], Scanned MAC: [" + deviceMac + "]");
                            
                            if (selectedMac == null || deviceMac == null || 
                                !selectedMac.trim().equalsIgnoreCase(deviceMac.trim())) {
                                Log.d(TAG, "❌ MAC mismatch: selected='" + selectedMac + "', scanned='" + deviceMac + "', skip update");
                                return;
                            }
                            
                            Log.d(TAG, "✓✓✓ MAC matched! Updating timestamp for: " + selectedDevice.getName());
                            
                            // 【最高优先级】立即更新时间戳
                            long newTimestamp = location.getTimestamp();
                            selectedDevice.setLastSeen(newTimestamp);
                            selectedDevice.setBluetoothScanTime(newTimestamp);
                            
                            // 更新时间显示
                            if (updateTimeText != null) {
                                updateTimeText.setText(getString(R.string.last_update_with_time, 
                                    com.RockiotTag.tag.util.TimeFormatter.formatSmartTime(MainActivity.this, newTimestamp)));
                            }
                            
                            Log.d(TAG, "✓ Timestamp updated: " + newTimestamp);
                            
                            // 【新增】更新电池显示
                            int battery = location.getBattery();
                            Log.d(TAG, "Battery from location: " + battery);
                            if (battery > 0) {
                                viewModel.updateBatteryLevel(String.valueOf(battery));
                                Log.d(TAG, "✓ Battery updated: " + battery + "%");
                            } else if (battery == 0) {
                                viewModel.updateBatteryLevel("0");
                                Log.d(TAG, "✓ Battery updated: 0%");
                            }
                            
                            // 【优化】蓝牙扫描只更新时间戳，不更新地址
                            // 地址由 updateDeviceUIWithLatest() 统一管理，避免频繁刷新
                            // 如果有GPS坐标，只更新设备对象，不更新UI地址显示
                            if (location.getLatitude() != 0 && location.getLongitude() != 0) {
                                Log.d(TAG, "Bluetooth scan has GPS coords, but skip address update: lat=" + location.getLatitude() + ", lng=" + location.getLongitude());
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
                            // 扫描成功：显示OK图标1秒后隐藏
                            if (scanningIndicator != null) {
                                scanningIndicator.setImageResource(android.R.drawable.ic_menu_save);
                                scanningIndicator.setVisibility(View.VISIBLE);
                                // 1秒后隐藏
                                new Handler().postDelayed(() -> {
                                    if (scanningIndicator != null) {
                                        scanningIndicator.setVisibility(View.GONE);
                                    }
                                }, 1000);
                            }
                        });
                    }
                });
            }
            
            
            // 升级数据库以支持多语言地址缓存
            databaseHelper.upgradeToMultiLanguageCache();
            
            
            initMap();
            
            
            initLocation();
            
            
            initBLE();
            
            
            // 启动定位优化（新增）
            if (locationOptimizationManager != null && locationOptimizationManager.isOptimizationEnabled()) {
                Log.d(TAG, "=== Starting Location Optimization ===");
                
                // 【关键优化】先自动选择第一个设备
                locationOptimizationManager.autoSelectFirstDevice();
                
                // 如果有自动选中的设备，立即更新UI
                String autoSelectedMac = locationOptimizationManager.getCurrentSelectedDeviceId();
                if (autoSelectedMac != null && !autoSelectedMac.isEmpty()) {
                    Device firstDevice = databaseHelper.getDevice(autoSelectedMac);
                    if (firstDevice != null) {
                        Log.d(TAG, "Auto-selected first device on startup: " + firstDevice.getName());
                        selectDevice(firstDevice);
                    }
                } else {
                    Log.w(TAG, "No device auto-selected");
                    Toast.makeText(this, "⚠️ 未选中设备", Toast.LENGTH_SHORT).show();
                }
                
                // 不自动启动蓝牙扫描，由扫描开关按钮控制
                Log.d(TAG, "Bluetooth scanning is OFF by default, use scan toggle button to start");
            } else {
                Log.e(TAG, "Location optimization manager is NULL or disabled!");
                Toast.makeText(this, "❌ 蓝牙扫描未启动", Toast.LENGTH_LONG).show();
            }
            
            
            initCrowdSourcing();
            
            
            initApiService();
            
            
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
                    if (mapManager.isAmap()) {
                        if (isSatelliteMap) {
                            mapManager.setMapType(com.amap.api.maps.AMap.MAP_TYPE_NORMAL);
                            Toast.makeText(MainActivity.this, R.string.normal_map, Toast.LENGTH_SHORT).show();
                        } else {
                            mapManager.setMapType(com.amap.api.maps.AMap.MAP_TYPE_SATELLITE);
                            Toast.makeText(MainActivity.this, R.string.satellite_map, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        if (isSatelliteMap) {
                            mapManager.setMapType(com.amap.api.maps.AMap.MAP_TYPE_NORMAL);
                            Toast.makeText(MainActivity.this, R.string.normal_map, Toast.LENGTH_SHORT).show();
                        } else {
                            mapManager.setMapType(com.amap.api.maps.AMap.MAP_TYPE_SATELLITE);
                            Toast.makeText(MainActivity.this, R.string.satellite_map, Toast.LENGTH_SHORT).show();
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
            Log.d(TAG, "Selected device restored");
            
            Log.d(TAG, "========== MainActivity.onCreate COMPLETE ==========");
            
        } catch (Exception e) {
            Log.e(TAG, "!!! FATAL ERROR IN onCreate !!!", e);
            Log.e(TAG, "Error type: " + e.getClass().getName());
            Log.e(TAG, "Error message: " + e.getMessage());
            e.printStackTrace();
            throw e; // 重新抛出异常
        }

    }

    /**
     * 启动导航到设备位置
     */
    private void startNavigation() {
        if (selectedDevice == null) {
            Toast.makeText(this, R.string.please_select_device, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedDevice.getLatitude() == 0 || selectedDevice.getLongitude() == 0) {
            Toast.makeText(this, R.string.navi_no_destination, Toast.LENGTH_SHORT).show();
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
        // 高德定位SDK返回的坐标已经是GCJ-02，不需要再次转换
        // Google定位SDK返回的坐标是WGS-84，需要转换
        boolean isStartGcj02 = (amapLocationService != null);
        intent.putExtra("start_is_gcj02", isStartGcj02);
        Log.d(TAG, "Starting navigation: phone=(" + currentLatitude + "," + currentLongitude 
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
        Log.d(TAG, "Foreground service started");
    }
    
    private void updateBottomInfo() {
        if (bleManager != null && bleManager.isConnected()) {
        } else {
            batteryLevelText.setText(R.string.battery_level);
            deviceAddressText.setText(R.string.address);
            updateTimeText.setText(R.string.last_update);
        }
    }
    
    /**
     * 自动定位到设备位置（地图加载完成后调用）
     */
    private void autoLocateToDevice() {
        
        if (selectedDevice != null && selectedDevice.getLatitude() != 0 && selectedDevice.getLongitude() != 0) {
            // 有选中设备，优先移动到设备位置
            if (mapManager != null && mapManager.isGoogleMap()) {
                if (mapManager.getGoogleMap() != null) {
                    com.google.android.gms.maps.model.LatLng latLng = 
                        new com.google.android.gms.maps.model.LatLng(selectedDevice.getLatitude(), selectedDevice.getLongitude());
                    mapManager.getGoogleMap().animateCamera(
                        com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 17));
                }
            } else {
                mapManager.moveCamera(selectedDevice.getLatitude(), selectedDevice.getLongitude(), 17);
            }
        } else if (currentLatitude != 0 && currentLongitude != 0) {
            // 没有选中设备或设备无位置，移动到当前位置
            if (mapManager != null && mapManager.isGoogleMap()) {
                if (mapManager.getGoogleMap() != null) {
                    com.google.android.gms.maps.model.LatLng latLng = 
                        new com.google.android.gms.maps.model.LatLng(currentLatitude, currentLongitude);
                    mapManager.getGoogleMap().animateCamera(
                        com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 17));
                    Log.d(TAG, "Auto-locate (Google Map): moved to current location");
                }
            } else {
                mapManager.moveCamera(currentLatitude, currentLongitude, 17);
                Log.d(TAG, "Auto-locate (AMap): moved to current location");
            }
        } else {
            Log.d(TAG, "Auto-locate: no valid position available");
        }
    }
    
    /**
     * 切换到设备位置（模拟点击"定位到当前位置"按钮）
     * 用于切换设备后自动移动相机到设备位置
     * @param setUserLocated 是否设置用户已定位标志（true=用户手动点击，false=自动调用）
     */
    private void locateToDevicePosition(boolean setUserLocated) {
        Log.d(TAG, "=== locateToDevicePosition called, setUserLocated=" + setUserLocated + " ===");
        Log.d(TAG, "selectedDevice=" + (selectedDevice != null ? selectedDevice.getName() : "null"));
        Log.d(TAG, "aMap=" + (aMap != null ? "valid" : "null"));
        Log.d(TAG, "mapManager=" + (mapManager != null ? "valid" : "null"));
        
        // 关键修复：检查地图是否就绪（高德或谷歌地图）
        boolean isMapReady = false;
        if (mapManager != null) {
            if (mapManager.isGoogleMap()) {
                isMapReady = mapManager.getGoogleMap() != null;
                Log.d(TAG, "Google Map ready: " + isMapReady);
            } else {
                isMapReady = aMap != null;
                Log.d(TAG, "AMap ready: " + isMapReady);
            }
        }
        
        if (!isMapReady) {
            Log.w(TAG, "Map is not ready, cannot move camera. Waiting for map initialization...");
            // 延迟100ms后重试
            new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Retrying locateToDevicePosition after delay");
                    locateToDevicePosition(setUserLocated);
                }
            }, 100);
            return;
        }
        
        // 根据参数决定是否设置用户手动定位标志
        if (setUserLocated) {
            isUserLocated = true;
            Log.d(TAG, "User manually located - auto camera moves disabled");
        } else {
            Log.d(TAG, "Auto locate - will allow subsequent auto camera moves");
        }
        
        if (selectedDevice != null && selectedDevice.getLatitude() != 0 && selectedDevice.getLongitude() != 0) {
            Log.d(TAG, "Device has location: " + selectedDevice.getName() + " at " + selectedDevice.getLatitude() + ", " + selectedDevice.getLongitude());
            
            // 1. 首先更新设备标记（高德地图）
            if (aMap != null) {
                LatLng deviceLatLng = CoordinateUtils.wgs84ToGcj02(selectedDevice.getLatitude(), selectedDevice.getLongitude());
                Log.d(TAG, "Converted coordinates: WGS84(" + selectedDevice.getLatitude() + ", " + selectedDevice.getLongitude() + ") -> GCJ02(" + deviceLatLng.latitude + ", " + deviceLatLng.longitude + ")");
                
                // 移除旧标记
                if (deviceLocationMarker != null) {
                    deviceLocationMarker.remove();
                    deviceLocationMarker = null;
                }
                
                // 添加新标记
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(deviceLatLng)
                        .title(selectedDevice.getName())
                        .snippet(getString(R.string.device_location))
                        .icon(com.RockiotTag.tag.util.MapMarkerHelper.createCustomMarkerWithR());
                deviceLocationMarker = aMap.addMarker(markerOptions);
                Log.d(TAG, "Device marker updated on AMap");
            }
            
            // 2. 更新设备标记（Google地图）
            if (mapManager != null && mapManager.isGoogleMap() && mapManager.getGoogleMap() != null) {
                if (googleDeviceLocationMarker != null) {
                    googleDeviceLocationMarker.remove();
                    googleDeviceLocationMarker = null;
                }
                com.google.android.gms.maps.model.MarkerOptions markerOptions = 
                    new com.google.android.gms.maps.model.MarkerOptions()
                        .position(new com.google.android.gms.maps.model.LatLng(selectedDevice.getLatitude(), selectedDevice.getLongitude()))
                        .title(selectedDevice.getName())
                        .snippet(getString(R.string.device_location));
                googleDeviceLocationMarker = mapManager.getGoogleMap().addMarker(markerOptions);
                Log.d(TAG, "Device marker updated on Google Map");
            }
            
            // 3. 移动地图相机到设备位置（关键：高德和谷歌地图都移动）
            if (mapManager != null && mapManager.isGoogleMap()) {
                if (mapManager.getGoogleMap() != null) {
                    com.google.android.gms.maps.model.LatLng latLng = 
                        new com.google.android.gms.maps.model.LatLng(selectedDevice.getLatitude(), selectedDevice.getLongitude());
                    mapManager.getGoogleMap().animateCamera(
                        com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 17));
                    Log.d(TAG, "Map camera moved to device position (Google Map)");
                }
            } else if (aMap != null) {
                // 高德地图模式：直接使用aMap移动相机，更可靠
                LatLng deviceLatLng = CoordinateUtils.wgs84ToGcj02(selectedDevice.getLatitude(), selectedDevice.getLongitude());
                Log.d(TAG, "Moving AMap camera to: lat=" + deviceLatLng.latitude + ", lng=" + deviceLatLng.longitude + ", zoom=17");
                aMap.animateCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(deviceLatLng, 17));
                Log.d(TAG, "Map camera moved to device position (AMap): lat=" + selectedDevice.getLatitude() + ", lng=" + selectedDevice.getLongitude());
            } else {
                Log.w(TAG, "Cannot move camera: aMap is null");
            }
            
            // 关键修复：更新MapViewModel，防止后续refreshMapWithCurrentDevice移回旧设备
            com.RockiotTag.tag.model.TagDevice tagDevice = new com.RockiotTag.tag.model.TagDevice(
                selectedDevice.getDeviceId(), selectedDevice.getName()
            );
            tagDevice.setMac(selectedDevice.getMac());
            tagDevice.setLatitude(selectedDevice.getLatitude());
            tagDevice.setLongitude(selectedDevice.getLongitude());
            mapViewModel.updateDeviceLocation(tagDevice);
            Log.d(TAG, "MapViewModel updated with new device location: " + selectedDevice.getName());
            
            // 4. 更新底部位置信息
            bottomInfo.setVisibility(View.VISIBLE);
            deviceAddressText.setText(getString(R.string.position_getting_address));
            // 关键修复：定位按钮必须强制刷新地址，不使用可能过时的缓存
            // 同时确保Google地图模式下使用正确的地理编码服务
            Log.d(TAG, "locateToDevicePosition: forcing address refresh for device location");
            getAddressFromLocation(selectedDevice.getLatitude(), selectedDevice.getLongitude(), true);
            
            // 5. 更新时间信息 - 使用 TimeFormatter 智能格式化
            if (selectedDevice.getLastSeen() > 0) {
                updateTimeText.setText(getString(R.string.last_update_with_time, 
                    com.RockiotTag.tag.util.TimeFormatter.formatSmartTime(MainActivity.this, selectedDevice.getLastSeen())));
            } else {
                updateTimeText.setText(getString(R.string.last_update_not_reported));
            }
            
            // 6. 更新电池信息 - 只在从服务器获取数据时更新，蓝牙扫描时不更新
            // batteryLevelText.setText(getString(R.string.battery_level_unknown));
            
            // 注意：不显示Toast提示，实现无感切换
            Log.d(TAG, "=== locateToDevicePosition completed ===");
            
        } else if (currentLatitude != 0 && currentLongitude != 0) {
            // 如果没有选中设备，移动到当前位置
            if (mapManager != null && mapManager.isGoogleMap()) {
                if (mapManager.getGoogleMap() != null) {
                    com.google.android.gms.maps.model.LatLng latLng = 
                        new com.google.android.gms.maps.model.LatLng(currentLatitude, currentLongitude);
                    mapManager.getGoogleMap().animateCamera(
                        com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 17));
                }
            } else if (aMap != null) {
                mapManager.moveCamera(currentLatitude, currentLongitude, 17);
            }
            // 注意：不显示Toast提示，实现无感切换
            Log.d(TAG, "Moved to current location (no device or device has no position)");
        } else {
            Toast.makeText(this, R.string.device_position_unknown, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Cannot locate: no valid position available");
        }
    }

    /**
     * MVVM 核心修复：根据当前选中的设备刷新地图标记和地址
     */
    /**
     * 刷新当前设备的地图显示
     * @param forceRefreshAddress 是否强制刷新地址（忽略缓存）
     */
    private void refreshMapWithCurrentDevice(boolean forceRefreshAddress) {
        Log.d(TAG, "=== refreshMapWithCurrentDevice START ===");
        
        // 优先从 MapViewModel 获取数据，如果没有则从 MainViewModel 获取
        com.RockiotTag.tag.model.TagDevice tagDevice = mapViewModel.getDeviceLocation().getValue();
        Device device = viewModel.getSelectedDevice().getValue();
        
        Log.d(TAG, "refreshMapWithCurrentDevice called: tagDevice=" + (tagDevice != null ? tagDevice.getName() : "null") + 
              ", device=" + (device != null ? device.getName() : "null"));
        
        if (tagDevice == null && device != null && device.getLatitude() != 0 && device.getLongitude() != 0) {
            Log.d(TAG, "Creating new TagDevice from Device: lat=" + device.getLatitude() + ", lng=" + device.getLongitude());
            tagDevice = new com.RockiotTag.tag.model.TagDevice(
                device.getDeviceId(), device.getName()
            );
            tagDevice.setMac(device.getMac());
            tagDevice.setLatitude(device.getLatitude());
            tagDevice.setLongitude(device.getLongitude());
            mapViewModel.updateDeviceLocation(tagDevice);
            Log.d(TAG, "MapViewModel updated with new device location");
        }
        
        Log.d(TAG, "After check: tagDevice=" + (tagDevice != null ? tagDevice.getName() + " at " + tagDevice.getLatitude() + "," + tagDevice.getLongitude() : "null") + 
              ", aMap=" + (aMap != null ? "valid" : "null") + 
              ", mapManager=" + (mapManager != null ? "valid" : "null") +
              ", isGoogleMap=" + (mapManager != null ? mapManager.isGoogleMap() : "N/A"));
        
        // 修复：Google地图模式下也需要刷新地图显示
        // 原条件只检查 aMap != null，导致Google地图模式下不执行刷新逻辑
        if (tagDevice != null) {
            Log.d(TAG, "Refreshing map with device: " + tagDevice.getName() + " at " + tagDevice.getLatitude() + ", " + tagDevice.getLongitude() + ", forceRefresh=" + forceRefreshAddress);
            
            // 1. 渲染 Marker（updateDeviceMarkerOnMap内部会判断地图模式）
            updateDeviceMarkerOnMap(tagDevice);
            
            // 2. 获取地址（根据参数决定是否强制刷新）
            deviceAddressText.setText(getString(R.string.position_getting_address));
            getAddressFromLocation(tagDevice.getLatitude(), tagDevice.getLongitude(), forceRefreshAddress);
            
            // 3. 移动相机
            boolean isGoogleMapMode = mapManager.isGoogleMap();
            Log.d(TAG, "Checking camera move condition: isGoogleMap=" + isGoogleMapMode + ", isUserLocated=" + isUserLocated);
            
            if (!isGoogleMapMode) {
                // 高德地图模式：只有当用户未手动定位时才移动相机
                if (!isUserLocated && aMap != null) {
                    Log.d(TAG, "Moving camera to device position (AMap): lat=" + tagDevice.getLatitude() + ", lng=" + tagDevice.getLongitude());
                    mapManager.moveCamera(tagDevice.getLatitude(), tagDevice.getLongitude(), 17);
                    Log.d(TAG, "Camera move command executed");
                } else {
                    Log.d(TAG, "User manually located or aMap is null, skipping camera move in refreshMapWithCurrentDevice");
                }
            } else {
                // 谷歌地图模式：由用户完全控制
                Log.d(TAG, "Google Map mode, camera movement controlled by user");
            }
        } else {
            Log.w(TAG, "Cannot refresh map: tagDevice is null");
            if (device != null) {
                Log.w(TAG, "Device coordinates: lat=" + device.getLatitude() + ", lng=" + device.getLongitude());
            }
        }
        
        Log.d(TAG, "=== refreshMapWithCurrentDevice END ===");
    }

    private void initMap() {
        Log.d(TAG, "Initializing map...");
        
        mapManager = new MapManager(this, mapView, googleMapFragment);
        mapManager.setCallback(new MapManager.MapCallback() {
            @Override
            public void onMapReady() {
                Log.d(TAG, "Map is ready");
                // 重新获取 aMap 实例，确保非空
                if (mapManager.isAmap()) {
                    aMap = mapManager.getAmap();
                }
                // MVVM 修复：地图就绪后，检查是否有待显示的设备位置
                refreshMapWithCurrentDevice(false);
            }
            
            @Override
            public void onMapClick(double latitude, double longitude) {
                Log.d(TAG, "Map clicked: " + latitude + ", " + longitude);
            }
        });
        
        // 先尝试恢复选中的设备，以便在地图初始化时设置目标位置
        restoreSelectedDeviceForMapInit();
        
        Log.d(TAG, "Initializing AMap...");
        mapManager.initAmap();
        Log.d(TAG, "Initializing Google Map...");
        mapManager.initGoogleMap();
        
        if (mapManager.isAmap()) {
            aMap = mapManager.getAmap();
            if (aMap != null) {
                // 地图全屏显示，不设置padding（让地图瓦片不移动）
                // logo下移到导航栏上方
                int logoMargin = (int) (56 * getResources().getDisplayMetrics().density);
                aMap.getUiSettings().setLogoBottomMargin(logoMargin);
                // 禁用SDK缩放按钮，使用自定义缩放按钮
                aMap.getUiSettings().setZoomControlsEnabled(false);
                // 禁用SDK指南针，使用自定义指南针
                aMap.getUiSettings().setCompassEnabled(false);
                aMap.getUiSettings().setMyLocationButtonEnabled(true);
                aMap.setMyLocationEnabled(false);
                aMap.getUiSettings().setScaleControlsEnabled(true);
                aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.zoomTo(17));
                
                aMap.setOnMapTouchListener(new com.amap.api.maps.AMap.OnMapTouchListener() {
                    @Override
                    public void onTouch(android.view.MotionEvent motionEvent) {
                        lastUserInteractionTime = System.currentTimeMillis();
                        Log.d(TAG, "User touched map at: " + lastUserInteractionTime);
                    }
                });
                
                aMap.setOnCameraChangeListener(new com.amap.api.maps.AMap.OnCameraChangeListener() {
                    @Override
                    public void onCameraChange(com.amap.api.maps.model.CameraPosition cameraPosition) {
                        lastUserInteractionTime = System.currentTimeMillis();
                        // 更新自定义指南针旋转角度（反向旋转）
                        updateCustomCompassRotation(cameraPosition.bearing);
                    }

                    @Override
                    public void onCameraChangeFinish(com.amap.api.maps.model.CameraPosition cameraPosition) {
                        lastUserInteractionTime = System.currentTimeMillis();
                        Log.d(TAG, "Camera change finished at: " + lastUserInteractionTime);
                        // 更新自定义指南针旋转角度
                        updateCustomCompassRotation(cameraPosition.bearing);
                    }
                });
                
                Log.d(TAG, "Map initialized successfully");
                
                // 初始化后立即应用深色地图样式（如果启用了深色模式）
                boolean isDarkMode = getSharedPreferences("app_settings", MODE_PRIVATE).getBoolean("dark_mode", false);
                if (isDarkMode) {
                    aMap.setMapType(com.amap.api.maps.AMap.MAP_TYPE_NAVI);
                }
                
                // MVVM 修复：地图初始化完成后，立即尝试刷新设备位置
                refreshMapWithCurrentDevice(false);
            } else {
                Log.e(TAG, "Failed to get AMap instance");
            }
        }
        
        // 延迟切换地图，确保Fragment视图已创建
        Log.d(TAG, "Scheduling map switch...");
        new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Executing delayed map switch, isAmap: " + mapManager.isAmap());
                if (mapManager.isAmap()) {
                    mapManager.switchToAmap();
                } else {
                    mapManager.switchToGoogleMap();
                }
                        
                // 地图加载成功后，延迟执行一次“回到当前位置”
                new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "Auto-locating to device position after map load");
                        autoLocateToDevice();
                    }
                }, 500); // 等待500ms确保地图完全加载
            }
        }, 300);
    }

    private void initLocation() {
        try {
            // 根据当前选择的地图类型初始化对应的定位服务
            if (mapManager != null && mapManager.isGoogleMap()) {
                // 使用谷歌定位服务
                Log.d(TAG, "=== Initializing Google Location Service ===");
                googleLocationService = new com.RockiotTag.tag.map.google.GoogleLocationService(getApplicationContext());
                googleLocationService.setLocationCallback(new com.RockiotTag.tag.map.google.GoogleLocationService.ServiceCallback() {
                    @Override
                    public void onLocationSuccess(double latitude, double longitude, float accuracy) {
                        currentLatitude = latitude;
                        currentLongitude = longitude;
                        Log.d(TAG, "Google location success: lat=" + latitude + ", lng=" + longitude);
                        
                        // MVVM - 更新 ViewModel 中的当前位置
                        viewModel.setCurrentLocation(latitude, longitude);
                        
                        updateCurrentLocationOnMap();
                    }
                    
                    @Override
                    public void onLocationFailed(String error) {
                        Log.e(TAG, "Google location failed: " + error);
                        setDefaultLocation();
                    }
                });
                googleLocationService.startLocation();
            } else {
                // 使用高德定位服务
                Log.d(TAG, "=== Initializing AMap Location Service ===");
                amapLocationService = new com.RockiotTag.tag.map.amap.AMapLocationService(getApplicationContext());
                amapLocationService.setLocationCallback(new com.RockiotTag.tag.map.amap.AMapLocationService.LocationCallback() {
                    @Override
                    public void onLocationSuccess(double latitude, double longitude, float accuracy, String address) {
                        currentLatitude = latitude;
                        currentLongitude = longitude;
                        Log.d(TAG, "AMap location success: lat=" + latitude + ", lng=" + longitude);
                        
                        // MVVM - 更新 ViewModel 中的当前位置
                        viewModel.setCurrentLocation(latitude, longitude);
                        
                        updateCurrentLocationOnMap();
                    }
                    
                    @Override
                    public void onLocationFailed(int errorCode, String errorInfo) {
                        Log.e(TAG, "AMap location failed: code=" + errorCode + ", info=" + errorInfo);
                        setDefaultLocation();
                    }
                });
                amapLocationService.startLocation();
            }
            
        } catch (SecurityException e) {
            Log.e(TAG, "❌ SecurityException: Missing location permissions", e);
            Toast.makeText(this, R.string.missing_location_permission, Toast.LENGTH_LONG).show();
            setDefaultLocation();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing location: " + e.getClass().getName() + " - " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.location_service_init_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            setDefaultLocation();
        }
    }

    private void setDefaultLocation() {
        // 默认位置：广东省深圳市新安街道高新奇科技园二期
        currentLatitude = 22.543611;
        currentLongitude = 113.881944;
        Log.d(TAG, "Set default location: " + currentLatitude + ", " + currentLongitude);
        // 关键修复：定位失败时不再自动移动地图，避免覆盖设备位置
        // 只更新currentLatitude和currentLongitude，但不移动地图
        // 如果用户点击定位按钮，会使用这个默认位置
        Log.d(TAG, "Location failed - keeping current map position, default location stored for manual locate");
    }

    private void initBLE() {
        bleManager = new BLEManager(this);
    }

    private void initCrowdSourcing() {
        crowdSourcingManager = new CrowdSourcingManager();
        crowdSourcingManager.setCallback(new CrowdSourcingManager.NearbyDevicesCallback() {
            @Override
            public void onNearbyDevicesReceived(List<Device> devices) {
                if (devices != null && !devices.isEmpty()) {
                    Log.d(TAG, "Received " + devices.size() + " nearby devices");
                    // 更新地图上的附近设备标记
                    for (Device device : devices) {
                        updateMapMarker(device);
                    }
                }
            }

            @Override
            public void onDeviceListReceived(List<Device> devices) {
                if (devices != null && !devices.isEmpty()) {
                    Log.d(TAG, "Received " + devices.size() + " devices from API");
                    // 处理从API获取的设备列表
                    for (Device device : devices) {
                        databaseHelper.addDevice(device);
                        updateMapMarker(device);
                    }
                }
            }
        });
    }

    private void initDatabase() {
        databaseHelper = new DatabaseHelper(this);
    }

    private void initApiService() {
        apiService = NewApiService.getInstance();
        unboundDeviceManager = UnboundDeviceManager.getInstance(this);
        // 从SharedPreferences加载认证信息
        SharedPreferencesManager.loadAuth(this);
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
        final List<String> menuItems = new ArrayList<>();
        menuItems.add(getString(R.string.geofence_settings));
        menuItems.add(getString(R.string.bluetooth_enhance));
        menuItems.add(getString(R.string.version_info));

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.menu_title)
                .setItems(menuItems.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                openGeofenceSettings();
                                break;
                            case 1:
                                showBluetoothEnhanceOptions();
                                break;
                            case 2:
                                showVersionInfo();
                                break;
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    public void showMapSwitchOptions() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String currentMap = prefs.getString("map_provider", "amap");
        
        String[] mapOptions = {getString(R.string.amap), getString(R.string.google_map)};
        int checkedItem = 0;
        if (currentMap.equals("google")) {
            checkedItem = 1;
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.select_map)
                .setSingleChoiceItems(mapOptions, checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newMapProvider = "amap";
                        if (which == 1) {
                            newMapProvider = "google";
                        }
                        
                        // 如果选择原生谷歌地图，先检查 Google Play Services
                        if (which == 1 && mapManager != null) {
                            if (!mapManager.isGooglePlayServicesAvailable()) {
                                Toast.makeText(MainActivity.this, 
                                    "Google Play Services 不可用，无法使用谷歌地图", 
                                    Toast.LENGTH_LONG).show();
                                dialog.dismiss();
                                return;
                            }
                        }
                        
                        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
                        android.content.SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("map_provider", newMapProvider);
                        editor.apply();
                        
                        // 关键修复：切换地图时清除目标地图模式的旧缓存，防止地址污染
                        if (databaseHelper != null) {
                            databaseHelper.cleanMapModeAddressCache(newMapProvider);
                            Log.d(TAG, "Cleared address cache for map mode: " + newMapProvider);
                        }
                        
                        int toastMessage = R.string.switched_to_amap;
                        if (which == 1) {
                            toastMessage = R.string.switched_to_google_map;
                        }
                        
                        Toast.makeText(MainActivity.this, toastMessage, Toast.LENGTH_SHORT).show();
                        
                        dialog.dismiss();
                        
                        recreate();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void selectDevice(Device device) {
        Log.d(TAG, "=== Selecting device ===");
        Log.d(TAG, "  Device Name: " + device.getName());
        Log.d(TAG, "  Device Num (16-digit): " + device.getDeviceNum());
        Log.d(TAG, "  Device MAC: " + (device.getMac() != null ? device.getMac() : "NULL/EMPTY"));
        Log.d(TAG, "  Device ID (from DB): " + device.getDeviceId());
        Log.d(TAG, "  Device Timestamp: " + device.getLastSeen() + " (" + 
            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(device.getLastSeen())) + ")");
        
        // 递增序列号，使旧设备的刷新请求失效
        int newSeq = ++deviceRefreshSequence;
        Log.d(TAG, "  Device refresh sequence: " + newSeq);
        
        // 【关键】设置当前选中的设备MAC地址，用于蓝牙扫描匹配
        // currentSelectedDeviceId 存储的是MAC地址（如 D4:DE:42:0F:57:7A）
        // 不是16位设备号（如 1756726632035006）
        if (locationOptimizationManager != null) {
            String macAddress = device.getMac();
            if (macAddress != null && !macAddress.isEmpty()) {
                locationOptimizationManager.setCurrentSelectedDeviceId(macAddress);
                Log.d(TAG, "Set selected device MAC for bluetooth matching: " + macAddress);
            } else {
                Log.w(TAG, "Device MAC is null/empty, cannot set for bluetooth filtering: " + device.getName());
            }
        }
        
        // 关键修复：切换设备时，先清除 MapViewModel 中的旧位置数据
        mapViewModel.clearDeviceLocation();
        Log.d(TAG, "Cleared old device location from MapViewModel");
        
        // MVVM - 通过 ViewModel 选择设备（使用UseCase）
        viewModel.selectDevice(device.getDeviceId());
        selectedDevice = device; // 保持兼容性
        
        // 选择设备时重置手动定位标志，允许地图跟随设备
        isUserLocated = false;
        Log.d(TAG, "Reset isUserLocated flag for new device selection");
        
        // 重置谷歌地图用户交互状态，允许新设备首次显示时移动相机
        if (mapManager != null && mapManager.isGoogleMap()) {
            mapManager.resetUserInteractionState();
            Log.d(TAG, "Reset Google Map user interaction state for new device");
        }
        
        updateDeviceNameWithTag(device.getName(), device.getTag());
        if (bottomInfo != null) bottomInfo.setVisibility(View.VISIBLE);

        // 【关键修复】切换设备时，如果新设备没有有效经纬度，立即将电量、地址、时间显示为 "--"
        if (device.getLatitude() == 0 || device.getLongitude() == 0) {
            Log.d(TAG, "Device has no valid coordinates, clearing battery/address/time UI");
            batteryLevelText.setText(getString(R.string.battery_level_empty));
            deviceAddressText.setText(getString(R.string.position_empty));
            updateTimeText.setText(getString(R.string.last_update_empty));
        }

        // 选择设备时，立即更新地图标记并移动到设备位置（如果本地有坐标）
        if (device.getLatitude() != 0 && device.getLongitude() != 0) {
            Log.d(TAG, "Device has local location: lat=" + device.getLatitude() + ", lng=" + device.getLongitude());
                    
            // 关键修复：切换设备后自动调用"定位到当前位置"功能（不设置isUserLocated标志）
            locateToDevicePosition(false);
        } else {
            Log.d(TAG, "Device has no local location, will wait for server data");
            // 即使没有本地坐标，也要刷新地图显示（会显示"位置未上报"等状态）
            refreshMapWithCurrentDevice(false);
        }
        
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("selected_device_id", device.getDeviceId());
        editor.apply();
        
        String deviceNumToFetch = device.getDeviceNum() != null ? device.getDeviceNum() : device.getDeviceId();
        
        // MVVM - 使用 ViewModel 获取设备信息（这会在服务器返回后触发观察者更新UI和移动相机）
        Log.d(TAG, "Fetching device info from server: " + deviceNumToFetch);
        viewModel.fetchDeviceInfo(deviceNumToFetch);
        
        // 切换设备后自动刷新，获取供应商最新数据
        performDeviceRefresh(true);
    }
    

    /**
     * 在地图上更新设备标记（供 MapViewModel 观察者调用）
     */
    private void updateDeviceMarkerOnMap(com.RockiotTag.tag.model.TagDevice device) {
        if (device == null || aMap == null) return;

        // 坐标转换：WGS84 -> GCJ02
        LatLng deviceLatLng = CoordinateUtils.wgs84ToGcj02(device.getLatitude(), device.getLongitude());
        
        // 移除旧标记
        if (deviceLocationMarker != null) {
            deviceLocationMarker.remove();
        }

        // 添加新标记
        MarkerOptions markerOptions = new MarkerOptions()
                .position(deviceLatLng)
                .title(device.getName())
                .snippet("设备位置")
                .icon(com.RockiotTag.tag.util.MapMarkerHelper.createCustomMarkerWithR());
        deviceLocationMarker = aMap.addMarker(markerOptions);
        
        Log.d(TAG, "Device marker updated on AMap via MapViewModel");
    }

    /**
     * 更新设备UI（统一方法 - 委托给updateDeviceUIWithLatest）
     */
    private void updateDeviceUI(NewApiService.DeviceInfo deviceInfo) {
        updateDeviceUIWithLatest(deviceInfo, true);
    }
    
    /**
     * 更新地图标记（提取为独立方法）
     */
    private void updateMapMarker(NewApiService.DeviceInfo deviceInfo) {
        if (mapManager == null) return;
        
        if (mapManager.isGoogleMap()) {
            // Google地图使用WGS84坐标
            if (googleDeviceLocationMarker != null) {
                googleDeviceLocationMarker.remove();
            }
            if (mapManager.getGoogleMap() != null) {
                com.google.android.gms.maps.model.MarkerOptions markerOptions = 
                    new com.google.android.gms.maps.model.MarkerOptions()
                        .position(new com.google.android.gms.maps.model.LatLng(deviceInfo.latitude, deviceInfo.longitude))
                        .title(selectedDevice != null ? selectedDevice.getName() : getString(R.string.device))
                        .snippet(getString(R.string.device_location));
                googleDeviceLocationMarker = mapManager.getGoogleMap().addMarker(markerOptions);
            }
        } else {
            // 高德地图使用GCJ02坐标
            LatLng deviceLatLng = CoordinateUtils.wgs84ToGcj02(deviceInfo.latitude, deviceInfo.longitude);
            if (deviceLocationMarker != null) {
                deviceLocationMarker.remove();
            }
            if (aMap != null) {
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(deviceLatLng)
                        .title(selectedDevice != null ? selectedDevice.getName() : getString(R.string.device))
                        .snippet(getString(R.string.device_location))
                        .icon(com.RockiotTag.tag.util.MapMarkerHelper.createCustomMarkerWithR());
                deviceLocationMarker = aMap.addMarker(markerOptions);
            }
        }
    }

    /**
     * 更新设备UI（统一方法）
     * @param deviceInfo 设备信息
     * @param moveCamera 是否移动地图相机到设备位置
     */
    private void updateDeviceUIWithLatest(NewApiService.DeviceInfo deviceInfo, boolean moveCamera) {
        Log.d(TAG, "Updating device UI: " + deviceInfo.deviceNum + ", moveCamera=" + moveCamera);
        
        if (bottomInfo != null) bottomInfo.setVisibility(View.VISIBLE);

        // 1. 更新设备名称 - 保留本地昵称，不使用服务器昵称覆盖
        // 因为本地昵称可能是用户刚修改但尚未同步到服务器的
        if (selectedDevice != null && selectedDevice.getName() != null) {
            updateDeviceNameWithTag(selectedDevice.getName(), selectedDevice.getTag());
        }
        
        // 2. 电池电量由 LiveData 观察者统一更新，此处不直接设置
        // com.RockiotTag.tag.util.DeviceInfoUpdater.updateBatteryLevel(batteryLevelText, deviceInfo.battery, this);
        
        // 3. 处理位置信息
        if (deviceInfo.latitude != 0 && deviceInfo.longitude != 0) {
            // 验证坐标有效性
            if (deviceInfo.latitude < -90 || deviceInfo.latitude > 90 || 
                deviceInfo.longitude < -180 || deviceInfo.longitude > 180) {
                Log.w(TAG, "Invalid coordinates: lat=" + deviceInfo.latitude + ", lng=" + deviceInfo.longitude);
                deviceAddressText.setText(getString(R.string.position_not_reported));
                return;
            }
            
            // 更新地图标记（使用统一方法）
            updateMapMarker(deviceInfo);
            
            // 移动相机 - 修复：切换设备后必须移动相机到新位置
            // 高德地图模式：总是移动相机到设备位置（尊重用户的设备选择）
            // 谷歌地图模式：由用户控制
            if (moveCamera && mapManager != null && !mapManager.isGoogleMap()) {
                mapManager.moveCamera(deviceInfo.latitude, deviceInfo.longitude, 17);
                Log.d(TAG, "Camera moved to new device position: lat=" + deviceInfo.latitude + ", lng=" + deviceInfo.longitude);
            } else if (moveCamera && mapManager != null && mapManager.isGoogleMap()) {
                Log.d(TAG, "Google Map mode - camera movement controlled by user (not auto-moving)");
            }
            
            // 关键修复：同时更新 MapViewModel，确保数据一致性
            com.RockiotTag.tag.model.TagDevice tagDevice = new com.RockiotTag.tag.model.TagDevice(
                selectedDevice != null ? selectedDevice.getDeviceId() : deviceInfo.deviceNum,
                selectedDevice != null ? selectedDevice.getName() : deviceInfo.deviceNum
            );
            // 注意：deviceInfo中可能没有MAC地址，如果selectedDevice存在则使用其MAC
            if (selectedDevice != null) {
                tagDevice.setMac(selectedDevice.getMac());
            }
            tagDevice.setLatitude(deviceInfo.latitude);
            tagDevice.setLongitude(deviceInfo.longitude);
            mapViewModel.updateDeviceLocation(tagDevice);
            Log.d(TAG, "MapViewModel updated with server data: lat=" + deviceInfo.latitude + ", lng=" + deviceInfo.longitude);
            
            // 关键修复：在更新本地数据之前，先保存原始坐标用于地址校验
            // 避免服务器返回的旧坐标（如公司地址）覆盖正确地址
            double localLatBeforeUpdate = 0;
            double localLngBeforeUpdate = 0;
            if (selectedDevice != null && selectedDevice.getLatitude() != 0 && selectedDevice.getLongitude() != 0) {
                localLatBeforeUpdate = selectedDevice.getLatitude();
                localLngBeforeUpdate = selectedDevice.getLongitude();
            }
            
            // 更新本地设备数据
            if (selectedDevice != null) {
                selectedDevice.setLatitude(deviceInfo.latitude);
                selectedDevice.setLongitude(deviceInfo.longitude);
                // 【关键修复】不要在这里更新时间戳，让 ViewModel 统一处理时间戳决策
                // 避免服务器的旧时间戳覆盖蓝牙扫描的最新时间
                // if (deviceInfo.timestamp > 0) {
                //     selectedDevice.setLastSeen(deviceInfo.timestamp);
                // }
                databaseHelper.addDevice(selectedDevice);
                Log.d(TAG, "Updated device location in DB (timestamp NOT updated here, handled by ViewModel)");
            }
            
            // 4. 获取地址
            // 【优化】先检查是否需要更新地址，避免频繁显示"正在获取地址..."
            double addressLat = deviceInfo.latitude;
            double addressLng = deviceInfo.longitude;
            
            Log.d(TAG, "Address coordinate check - localLatBeforeUpdate: " + localLatBeforeUpdate + ", serverLat: " + deviceInfo.latitude);
            
            if (localLatBeforeUpdate != 0 && localLngBeforeUpdate != 0) {
                // 检查服务器坐标是否与更新前的本地坐标差异过大
                double distance = CoordinateUtils.calculateDistanceMeters(
                    localLatBeforeUpdate, localLngBeforeUpdate,
                    deviceInfo.latitude, deviceInfo.longitude
                );
                
                Log.d(TAG, "Coordinate distance: " + distance + "m");
                
                // 关键修复：如果坐标差异超过1000公里，说明本地坐标可能是旧数据（如深圳默认坐标）
                // 应该使用服务器返回的最新坐标
                if (distance > 1000000) { // 1000公里
                    Log.d(TAG, "Local coordinates differ by " + (distance/1000) + "km from server - likely stale data, using server coordinates");
                    // 使用服务器坐标（addressLat/AddressLng已经是服务器坐标）
                } else if (distance > 100) {
                    // 坐标差异适中（100米-1000公里），本地坐标可能更准确，使用本地坐标
                    Log.d(TAG, "Server coordinates differ by " + distance + "m from local, using local coordinates for address");
                    addressLat = localLatBeforeUpdate;
                    addressLng = localLngBeforeUpdate;
                } else {
                    Log.d(TAG, "Server coordinates match local (diff=" + distance + "m), using server coordinates");
                }
            } else {
                // 关键修复：第一次加载时，检查服务器坐标是否合理
                // 如果服务器返回的坐标是深圳默认坐标（22.543611, 113.881944），但地图显示在国外
                // 则不使用该坐标进行地理编码
                double serverLat = deviceInfo.latitude;
                double serverLng = deviceInfo.longitude;
                
                // 检测是否为深圳默认坐标（容差0.01度，约1公里）
                boolean isShenzhenDefault = Math.abs(serverLat - 22.543611) < 0.01 && Math.abs(serverLng - 113.881944) < 0.01;
                
                if (isShenzhenDefault) {
                    Log.w(TAG, "Server returned default Shenzhen coordinates - skipping address geocoding");
                    // 不查询地址，显示坐标
                    deviceAddressText.setText(String.format("%.6f, %.6f", serverLat, serverLng));
                    return;
                }
                
                Log.d(TAG, "No local coordinates before update, using server coordinates");
            }
            
            // 【优化】检查距离阈值和时间间隔，决定是否需要重新获取地址
            long currentTime = System.currentTimeMillis();
            long timeSinceLastUpdate = currentTime - lastAddressUpdateTime;
            
            if (lastAddressLatitude != 0 && lastAddressLongitude != 0) {
                double distanceToLastAddress = CoordinateUtils.calculateDistanceMeters(
                    lastAddressLatitude, lastAddressLongitude,
                    addressLat, addressLng
                );
                
                // 检查是否满足更新条件：距离超过200米 或 时间超过60秒
                boolean needUpdateByDistance = distanceToLastAddress >= ADDRESS_UPDATE_THRESHOLD;
                boolean needUpdateByTime = timeSinceLastUpdate >= ADDRESS_UPDATE_INTERVAL;
                
                if (!needUpdateByDistance && !needUpdateByTime) {
                    // 不满足任何更新条件，保持当前地址不变
                    Log.d(TAG, String.format("Skip address update: distance=%.1fm (<%dm), time=%ds (<%ds)",
                        distanceToLastAddress, (int)ADDRESS_UPDATE_THRESHOLD,
                        timeSinceLastUpdate/1000, ADDRESS_UPDATE_INTERVAL/1000));
                    // 不调用 getAddressFromLocation()，也不设置任何文本
                } else {
                    // 满足更新条件
                    if (needUpdateByDistance) {
                        Log.d(TAG, String.format("Address update triggered by distance: %.1fm >= %dm",
                            distanceToLastAddress, (int)ADDRESS_UPDATE_THRESHOLD));
                    } else {
                        Log.d(TAG, String.format("Address update triggered by time: %ds >= %ds",
                            timeSinceLastUpdate/1000, ADDRESS_UPDATE_INTERVAL/1000));
                    }
                    
                    // 【优化】不显示"正在获取地址..."，等新地址获取成功后直接覆盖
                    // deviceAddressText.setText(getString(R.string.position_getting_address)); // 删除这行
                    
                    // 保存新坐标和更新时间
                    lastAddressLatitude = addressLat;
                    lastAddressLongitude = addressLng;
                    lastAddressUpdateTime = currentTime;
                    getAddressFromLocation(addressLat, addressLng, true);
                }
            } else {
                // 第一次获取地址
                Log.d(TAG, "First time fetching address");
                deviceAddressText.setText(getString(R.string.position_getting_address));
                // 保存坐标和更新时间
                lastAddressLatitude = addressLat;
                lastAddressLongitude = addressLng;
                lastAddressUpdateTime = currentTime;
                getAddressFromLocation(addressLat, addressLng, true);
            }
        } else {
            deviceAddressText.setText(getString(R.string.position_not_reported));
        }
        
        // 5. 更新时间信息 - 使用 DeviceInfoUpdater
        if (deviceInfo.timestamp > 0) {
            com.RockiotTag.tag.util.DeviceInfoUpdater.updateTime(updateTimeText, deviceInfo.timestamp, this);
        } else if (deviceInfo.updatedAt != null && !deviceInfo.updatedAt.isEmpty()) {
            updateTimeText.setText(getString(R.string.last_update_with_time, deviceInfo.updatedAt));
        } else {
            updateTimeText.setText(getString(R.string.last_update_not_reported));
        }
    }
    
    /**
     * 更新设备UI（不移动相机，用于后台刷新）
     */
    private void updateDeviceUIWithoutCameraMove(NewApiService.DeviceInfo deviceInfo) {
        updateDeviceUIWithLatest(deviceInfo, false);
    }
    
    /**
     * 更新设备UI为默认值 - 使用 DeviceInfoUpdater
     */
    private void updateDeviceUIDefault() {
        com.RockiotTag.tag.util.DeviceInfoUpdater.resetToDefault(batteryLevelText, deviceAddressText, updateTimeText, this);
    }
    

    /**
     * 根据经纬度进行逆地理编码，获取具体地址
     * @param latitude 纬度（WGS84坐标系）- 已经过校验的正确坐标
     * @param longitude 经度（WGS84坐标系）- 已经过校验的正确坐标
     * @param forceRefresh 是否强制刷新（忽略缓存）
     */
    private void getAddressFromLocation(double latitude, double longitude, boolean forceRefresh) {
        Log.d(TAG, "Getting address from location: lat=" + latitude + ", lng=" + longitude + ", forceRefresh=" + forceRefresh);
        
        // 判断是否使用高德地理编码
        boolean isGoogleMap = mapManager != null && mapManager.isGoogleMap();
        boolean useAMapGeocoder = !isGoogleMap; // 高德地图模式使用高德地理编码
        String mapMode = isGoogleMap ? "google" : "amap"; // 地图模式标识
        
        Log.d(TAG, "Map mode: " + mapMode + ", useAMapGeocoder=" + useAMapGeocoder);
        
        // MVVM - 使用 ViewModel 的 UseCase 进行逆地理编码
        String languageCode = getGeocodingLanguageCode();
        
        // 关键修复：确保Google地图模式下强制刷新，避免使用高德地图的缓存
        if (isGoogleMap && !forceRefresh) {
            Log.d(TAG, "Google Map mode detected, forcing refresh to avoid AMap cache pollution");
            forceRefresh = true;
        }
        
        viewModel.getAddress(latitude, longitude, languageCode, forceRefresh, useAMapGeocoder, mapMode);
    }

    private static class LanguageItem {
        String flag;
        String name;
        String code;

        LanguageItem(String flag, String name, String code) {
            this.flag = flag;
            this.name = name;
            this.code = code;
        }
    }

    public void showLanguageOptions() {
        final List<LanguageItem> languages = new ArrayList<>();
        languages.add(new LanguageItem("\uD83C\uDDE8\uD83C\uDDF3", "中文", "zh"));
        languages.add(new LanguageItem("\uD83C\uDDEC\uD83C\uDDE7", "English", "en"));
        languages.add(new LanguageItem("\uD83C\uDDE7\uD83C\uDDF7", "Português", "pt-rBR"));
        languages.add(new LanguageItem("\uD83C\uDDF7\uD83C\uDDFA", "Русский", "ru"));
        languages.add(new LanguageItem("\uD83C\uDDEE\uD83C\uDDF3", "हिंदी", "hi"));
        languages.add(new LanguageItem("\uD83C\uDDF9\uD83C\uDDF7", "Türkçe", "tr"));

        // 获取当前选中的语言代码
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String currentLanguageCode = prefs.getString("language", LanguageUtils.getSystemLanguage());

        ArrayAdapter<LanguageItem> adapter = new ArrayAdapter<LanguageItem>(this, R.layout.item_language, R.id.language_name, languages) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = LayoutInflater.from(getContext()).inflate(R.layout.item_language, parent, false);
                }
                LanguageItem item = getItem(position);
                TextView flagText = view.findViewById(R.id.language_flag);
                TextView nameText = view.findViewById(R.id.language_name);
                flagText.setText(item.flag);
                nameText.setText(item.name);
                
                // 高亮当前选中的语言
                if (item.code.equals(currentLanguageCode)) {
                    view.setBackgroundColor(getResources().getColor(R.color.purple_500, null));
                    flagText.setTextColor(getResources().getColor(android.R.color.white, null));
                    nameText.setTextColor(getResources().getColor(android.R.color.white, null));
                } else {
                    view.setBackgroundColor(getResources().getColor(android.R.color.transparent, null));
                    // 根据深色模式设置文字颜色
                    int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                    if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                        flagText.setTextColor(getResources().getColor(android.R.color.white, null));
                        nameText.setTextColor(getResources().getColor(android.R.color.white, null));
                    } else {
                        flagText.setTextColor(getResources().getColor(android.R.color.black, null));
                        nameText.setTextColor(getResources().getColor(android.R.color.black, null));
                    }
                }
                
                return view;
            }
        };

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.language_title)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        changeLanguage(languages.get(which).code);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    private void showVersionInfo() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.version_info)
                    .setMessage(getString(R.string.version_format, versionName, versionCode))
                    .setPositiveButton(R.string.confirm, null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error getting version info: " + e.getMessage());
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void changeLanguage(String languageCode) {
        LanguageUtils.saveLanguage(this, languageCode);
        
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("language", languageCode);
        editor.apply();

        applyLanguage(languageCode);

        Toast.makeText(this, R.string.language_changed, Toast.LENGTH_SHORT).show();
        
        Intent intent = getIntent();
        finish();
        startActivity(intent);
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
        
        Log.d(TAG, "App language setting: " + languageCode);
        
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
        int currentMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            editor.putBoolean("dark_mode", false);
            Toast.makeText(this, R.string.light_mode, Toast.LENGTH_SHORT).show();
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
            editor.putBoolean("dark_mode", true);
            Toast.makeText(this, R.string.dark_mode, Toast.LENGTH_SHORT).show();
        }
        editor.apply();
        
        // 重新启动Activity以应用新的主题
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    private void triggerBuzzer() {
        // MVVM - 使用 ViewModel 的 UseCase 触发蜂鸣器
        viewModel.triggerBuzzer();
        Toast.makeText(this, R.string.trigger_buzzer, Toast.LENGTH_SHORT).show();
    }

    /**
     * 初始化底部导航栏
     */
    private void initBottomNavigation() {
        View.OnClickListener tabClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 防止快速重复点击
                long now = System.currentTimeMillis();
                if (now - lastTabClickTime < TAB_CLICK_INTERVAL) {
                    return;
                }
                lastTabClickTime = now;

                int tabIndex;
                if (v.getId() == R.id.tab_home) tabIndex = 0;
                else if (v.getId() == R.id.tab_list) tabIndex = 1;
                else if (v.getId() == R.id.tab_track) tabIndex = 2;
                else if (v.getId() == R.id.tab_profile) tabIndex = 3;
                else return;

                // 禁止重复点击同一Tab
                if (tabIndex == currentTab) return;

                // 轨迹Tab：直接启动TrackActivity，避免Fragment onResume循环启动
                if (tabIndex == 2) {
                    if (selectedDevice == null) {
                        Toast.makeText(MainActivity.this, R.string.please_select_device, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    updateTabSelection(2);
                    currentTab = 2;
                    updateHomeUIVisibility(false);
                    Intent intent = new Intent(MainActivity.this, TrackActivity.class);
                    startActivity(intent);
                    return;
                }

                switchToTab(tabIndex);
            }
        };

        tabHome.setOnClickListener(tabClickListener);
        tabList.setOnClickListener(tabClickListener);
        tabTrack.setOnClickListener(tabClickListener);
        tabProfile.setOnClickListener(tabClickListener);

        // 默认选中首页
        updateTabSelection(0);
    }

    /**
     * 初始化Fragment
     */
    private void initFragments() {
        Log.d(TAG, "=== initFragments START ===");

        homeFragment = new HomeFragment();
        deviceListFragment = new DeviceListFragment();
        trackFragment = new TrackFragment();
        profileFragment = new ProfileFragment();

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.add(R.id.fragment_container, homeFragment, "home");
        ft.add(R.id.fragment_container, deviceListFragment, "list");
        ft.add(R.id.fragment_container, trackFragment, "track");
        ft.add(R.id.fragment_container, profileFragment, "profile");
        ft.hide(deviceListFragment);
        ft.hide(trackFragment);
        ft.hide(profileFragment);
        ft.commitNowAllowingStateLoss();
        
        currentTab = 0;
        updateTabSelection(0);
        updateHomeUIVisibility(true);
        Log.d(TAG, "=== initFragments END ===");
    }

    /**
     * 切换Tab
     * @param tabIndex 0=首页, 1=列表, 2=轨迹, 3=我的
     */
    public void switchToTab(int tabIndex) {
        if (tabIndex == currentTab) return;

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        // 隐藏所有Fragment
        ft.hide(homeFragment);
        ft.hide(deviceListFragment);
        ft.hide(trackFragment);
        ft.hide(profileFragment);

        // 显示目标Fragment
        switch (tabIndex) {
            case 0: ft.show(homeFragment); break;
            case 1: ft.show(deviceListFragment); break;
            case 2: ft.show(trackFragment); break;
            case 3: ft.show(profileFragment); break;
        }
        ft.commitNowAllowingStateLoss();

        currentTab = tabIndex;
        updateTabSelection(tabIndex);
        updateHomeUIVisibility(tabIndex == 0);
    }

    /**
     * 更新Tab选中状态
     */
    private void updateTabSelection(int tabIndex) {
        tabHome.setSelected(tabIndex == 0);
        tabList.setSelected(tabIndex == 1);
        tabTrack.setSelected(tabIndex == 2);
        tabProfile.setSelected(tabIndex == 3);

        // 更新图标和文字颜色（深色模式下未选中颜色不同）
        int selectedColor = getResources().getColor(R.color.purple_500, null);
        boolean isDarkMode = getSharedPreferences("app_settings", MODE_PRIVATE).getBoolean("dark_mode", false);
        int unselectedColor = isDarkMode ? 
            getResources().getColor(R.color.dark_text_secondary, null) : 
            getResources().getColor(R.color.text_secondary, null);

        tabHomeIcon.setColorFilter(tabIndex == 0 ? selectedColor : unselectedColor);
        tabListIcon.setColorFilter(tabIndex == 1 ? selectedColor : unselectedColor);
        tabTrackIcon.setColorFilter(tabIndex == 2 ? selectedColor : unselectedColor);
        tabProfileIcon.setColorFilter(tabIndex == 3 ? selectedColor : unselectedColor);
        
        tabHomeText.setTextColor(tabIndex == 0 ? selectedColor : unselectedColor);
        tabListText.setTextColor(tabIndex == 1 ? selectedColor : unselectedColor);
        tabTrackText.setTextColor(tabIndex == 2 ? selectedColor : unselectedColor);
        tabProfileText.setTextColor(tabIndex == 3 ? selectedColor : unselectedColor);
    }

    /**
     * 控制首页UI元素的可见性（浮动按钮、底部信息卡片、自定义指南针）
     */
    private void updateHomeUIVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (refreshBtn != null) refreshBtn.setVisibility(visibility);
        if (mapTypeBtn != null) mapTypeBtn.setVisibility(visibility);
        if (locateBtn != null) locateBtn.setVisibility(visibility);
        if (customCompass != null) customCompass.setVisibility(visibility);  // 自定义指南针
        if (bottomInfo != null && selectedDevice != null) {
            bottomInfo.setVisibility(visibility);
        }
    }

    /**
     * 更新自定义指南针旋转角度
     * @param bearing 地图旋转角度（度）
     */
    private void updateCustomCompassRotation(float bearing) {
        if (customCompass != null) {
            // 指南针需要反向旋转，以保持指向北方
            customCompass.setRotation(-bearing);
        }
    }

    /**
     * 首页Fragment可见时回调
     */
    public void onHomeFragmentVisible() {
        updateHomeUIVisibility(true);
    }

    /**
     * 判断当前Tab是否是首页
     */
    public boolean isCurrentTabHome() {
        return currentTab == 0;
    }

    /**
     * 获取当前选中的设备（供Fragment调用）
     */
    public Device getSelectedDevice() {
        return selectedDevice;
    }

    /**
     * 打开添加设备页面（供Fragment调用）
     */
    public void openAddDevice() {
        Intent intent = new Intent(this, AddDeviceActivity.class);
        startActivity(intent);
    }

    /**
     * 切换深色模式（不重建Activity，手动应用主题颜色）
     */
    public void toggleDarkMode(boolean isDarkMode) {
        // 保存设置
        getSharedPreferences("app_settings", MODE_PRIVATE)
            .edit().putBoolean("dark_mode", isDarkMode).apply();
        
        // 手动应用深色/浅色模式，不重建Activity
        applyDarkMode(isDarkMode);
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
        int selectedColor = getResources().getColor(R.color.purple_500, null);
        int unselectedColor = isDarkMode ? 
            getResources().getColor(R.color.dark_text_secondary, null) : 
            getResources().getColor(R.color.text_secondary, null);
        
        // 应用到根视图
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) rootView.setBackgroundColor(bgColor);
        
        // 应用到底部导航栏背景
        if (bottomNavigation != null) bottomNavigation.setBackgroundColor(topBarColor);
        
        // 应用到导航栏Tab文字和图标颜色
        updateTabColors(selectedColor, unselectedColor);
        
        // 应用到底部信息卡片
        if (bottomInfo != null) {
            try {
                ((androidx.cardview.widget.CardView) bottomInfo).setCardBackgroundColor(cardColor);
            } catch (Exception e) {}
        }
        
        // 应用到信息卡片内的文字
        if (batteryLevelText != null) batteryLevelText.setTextColor(onSurfaceColor);
        if (deviceAddressText != null) deviceAddressText.setTextColor(onSurfaceColor);
        if (updateTimeText != null) updateTimeText.setTextColor(onSurfaceColor);
        
        // 更新状态栏
        if (isDarkMode) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.dark_surface, null));
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility() 
                    & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } else {
            getWindow().setStatusBarColor(getResources().getColor(R.color.top_bar_background, null));
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility() 
                    | android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
        
        // 深色模式：设置地图样式
        if (mapManager.isAmap()) {
            if (aMap != null) {
                if (isDarkMode) {
                    // 使用导航地图样式（深色风格，蓝黑配色）
                    aMap.setMapType(com.amap.api.maps.AMap.MAP_TYPE_NAVI);
                } else {
                    aMap.setMapType(com.amap.api.maps.AMap.MAP_TYPE_NORMAL);
                }
            }
        } else if (mapManager != null && mapManager.getGoogleMap() != null) {
            com.google.android.gms.maps.GoogleMap gMap = mapManager.getGoogleMap();
            if (isDarkMode) {
                try {
                    gMap.setMapStyle(com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_night));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to apply dark map style: " + e.getMessage());
                }
            } else {
                gMap.setMapStyle(null);
            }
        }
        
        // 通知Fragment更新
        if (deviceListFragment != null) deviceListFragment.applyTheme(isDarkMode);
        if (profileFragment != null) profileFragment.applyTheme(isDarkMode);
    }
    
    /**
     * 更新导航栏Tab文字和图标颜色
     */
    private void updateTabColors(int selectedColor, int unselectedColor) {
        // 更新当前选中的Tab
        int[][] tabPairs = {
            {R.id.tab_home_icon, R.id.tab_home_text},
            {R.id.tab_list_icon, R.id.tab_list_text},
            {R.id.tab_track_icon, R.id.tab_track_text},
            {R.id.tab_profile_icon, R.id.tab_profile_text}
        };
        
        for (int i = 0; i < tabPairs.length; i++) {
            ImageView icon = findViewById(tabPairs[i][0]);
            TextView text = findViewById(tabPairs[i][1]);
            boolean isSelected = (i == currentTab);
            int color = isSelected ? selectedColor : unselectedColor;
            if (icon != null) icon.setColorFilter(color);
            if (text != null) {
                text.setTextColor(color);
                text.setSelected(isSelected);
            }
        }
    }

    /**
     * 显示登录对话框
     */
    public void showLoginDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.login);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText usernameInput = new EditText(this);
        usernameInput.setHint(R.string.username);
        usernameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(usernameInput);

        final EditText passwordInput = new EditText(this);
        passwordInput.setHint(R.string.password);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput);

        builder.setView(layout);

        builder.setPositiveButton(R.string.login, null);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setNeutralButton(R.string.forgot_password, null);

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        // 重写按钮点击，防止自动关闭
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (!UserApiService.isValidUsername(username)) {
                Toast.makeText(this, R.string.username_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!UserApiService.isValidPassword(password)) {
                Toast.makeText(this, R.string.password_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                NewApiService.ApiResponse response = UserApiService.getInstance().login(username, password);
                runOnUiThread(() -> {
                    if (response.isSuccess() && response.getToken() != null) {
                        // 保存登录状态
                        getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                            .putString("auth_token", response.getToken())
                            .putString("user_username", username)
                            .apply();
                        Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        // 更新ProfileFragment
                        if (profileFragment != null) {
                            profileFragment.onResume();
                        }
                    } else {
                        String msg = response.getMessage();
                        if (msg != null && msg.contains("not found")) {
                            Toast.makeText(this, R.string.user_not_found, Toast.LENGTH_SHORT).show();
                        } else if (msg != null && msg.contains("password")) {
                            Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, R.string.login_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }).start();
        });

        // 忘记密码
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            Toast.makeText(this, R.string.reset_password_contact_admin, Toast.LENGTH_LONG).show();
        });
    }

    /**
     * 显示注册对话框
     */
    public void showRegisterDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.register);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText usernameInput = new EditText(this);
        usernameInput.setHint(R.string.username);
        usernameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(usernameInput);

        TextView usernameHint = new TextView(this);
        usernameHint.setText(getString(R.string.username_rule));
        usernameHint.setTextSize(12);
        usernameHint.setTextColor(getResources().getColor(R.color.text_secondary, null));
        layout.addView(usernameHint);

        final EditText passwordInput = new EditText(this);
        passwordInput.setHint(R.string.password);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput);

        TextView passwordHint = new TextView(this);
        passwordHint.setText(getString(R.string.password_rule));
        passwordHint.setTextSize(12);
        passwordHint.setTextColor(getResources().getColor(R.color.text_secondary, null));
        layout.addView(passwordHint);

        final EditText confirmPasswordInput = new EditText(this);
        confirmPasswordInput.setHint(R.string.confirm_password);
        confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(confirmPasswordInput);

        builder.setView(layout);

        builder.setPositiveButton(R.string.register, null);
        builder.setNegativeButton(R.string.cancel, null);

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            String confirmPassword = confirmPasswordInput.getText().toString().trim();

            if (!UserApiService.isValidUsername(username)) {
                Toast.makeText(this, R.string.username_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!UserApiService.isValidPassword(password)) {
                Toast.makeText(this, R.string.password_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, R.string.password_not_match, Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                NewApiService.ApiResponse response = UserApiService.getInstance().register(username, password);
                runOnUiThread(() -> {
                    if (response.isSuccess()) {
                        Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();
                        // 注册成功后自动登录
                        new Thread(() -> {
                            NewApiService.ApiResponse loginResp = UserApiService.getInstance().login(username, password);
                            runOnUiThread(() -> {
                                if (loginResp.isSuccess() && loginResp.getToken() != null) {
                                    getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                                        .putString("auth_token", loginResp.getToken())
                                        .putString("user_username", username)
                                        .apply();
                                    Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
                                    if (profileFragment != null) {
                                        profileFragment.onResume();
                                    }
                                }
                            });
                        }).start();
                        dialog.dismiss();
                    } else {
                        String msg = response.getMessage();
                        if (msg != null && (msg.contains("exist") || msg.contains("already"))) {
                            Toast.makeText(this, R.string.username_exists, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, R.string.register_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }).start();
        });
    }

    private void startRefreshAnimation() {
        android.view.animation.Animation rotateAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rotate_refresh);
        refreshBtn.startAnimation(rotateAnimation);
    }

    private void stopRefreshAnimation() {
        refreshBtn.clearAnimation();
    }

    /**
     * 执行设备刷新操作（从供应商API获取最新数据）
     * @param showToast 是否显示"请选择设备"等提示（手动点击时true，自动刷新时false）
     */
    private void performDeviceRefresh(boolean showToast) {
        Log.d(TAG, "========== performDeviceRefresh (showToast=" + showToast + ") ==========");
        Device currentSelectedDevice = viewModel.getSelectedDevice().getValue();
        if (currentSelectedDevice == null) {
            if (showToast) {
                Toast.makeText(MainActivity.this, R.string.please_select_device, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        
        // 防止重复刷新
        if (isRefreshInProgress) {
            Log.d(TAG, "Refresh already in progress, skipping");
            return;
        }
        isRefreshInProgress = true;
        startRefreshAnimation();
        
        final String deviceNum = currentSelectedDevice.getDeviceNum() != null ? 
            currentSelectedDevice.getDeviceNum() : currentSelectedDevice.getDeviceId();
        final long currentTimestamp = currentSelectedDevice.getLastSeen();
        final String savedCustomerCode = currentSelectedDevice.getCustomerCode();
        
        // 递增序列号，使旧请求的结果失效
        final int currentSeq = ++deviceRefreshSequence;
        Log.d(TAG, "Refreshing device: " + deviceNum + ", seq: " + currentSeq 
            + ", current timestamp: " + currentTimestamp
            + ", savedCustomerCode: " + savedCustomerCode);
        
        // 异步调用API获取最新设备信息
        new Thread(() -> {
            try {
                String apiUrl = ApiConfig.getMyServerUrl(deviceNum);
                NewApiService.setApiBaseUrl(apiUrl);
                
                // 使用与GetDeviceInfoUseCase相同的策略：先尝试保存的customerCode，再遍历所有
                NewApiService.DeviceInfo latestInfo = null;
                String matchedCustomerCode = null;
                
                // 1. 优先使用设备保存的customerCode
                if (savedCustomerCode != null && !savedCustomerCode.isEmpty()) {
                    Log.d(TAG, "Trying with saved customerCode: " + savedCustomerCode);
                    latestInfo = NewApiService.getInstance().getDeviceLatest(deviceNum, savedCustomerCode);
                    if (latestInfo != null && latestInfo.deviceNum != null && !latestInfo.deviceNum.isEmpty()) {
                        matchedCustomerCode = savedCustomerCode;
                        Log.d(TAG, "Success with saved customerCode: " + savedCustomerCode);
                    } else {
                        Log.d(TAG, "Failed with saved customerCode: " + savedCustomerCode + ", trying others...");
                        latestInfo = null;
                    }
                }
                
                // 2. 如果保存的customerCode失败，遍历所有customerCode
                if (latestInfo == null) {
                    java.util.Map<String, ApiConfig.CustomerConfig> configs = ApiConfig.getAllCustomerConfigs();
                    for (java.util.Map.Entry<String, ApiConfig.CustomerConfig> entry : configs.entrySet()) {
                        // 检查序列号，如果已有新请求则提前退出
                        if (currentSeq != deviceRefreshSequence) {
                            break;
                        }
                        String customerCode = entry.getKey();
                        if (customerCode.equals(savedCustomerCode)) {
                            continue; // 已经尝试过
                        }
                        
                        Log.d(TAG, "Trying customerCode: " + customerCode);
                        latestInfo = NewApiService.getInstance().getDeviceLatest(deviceNum, customerCode);
                        if (latestInfo != null && latestInfo.deviceNum != null && !latestInfo.deviceNum.isEmpty()) {
                            matchedCustomerCode = customerCode;
                            Log.d(TAG, "Success with customerCode: " + customerCode);
                            break;
                        }
                        latestInfo = null;
                    }
                }
                
                // 检查序列号，如果已有新请求则丢弃本结果（但仍需停止动画）
                if (currentSeq != deviceRefreshSequence) {
                    Log.d(TAG, "Refresh seq #" + currentSeq + " ignored (stale), current=#" + deviceRefreshSequence);
                    // 不需要停止动画，因为新的刷新请求会接管动画
                    isRefreshInProgress = false;
                    return;
                }
                
                if (latestInfo == null) {
                    Log.w(TAG, "API returned null for device: " + deviceNum + " with all customer codes");
                    runOnUiThread(() -> {
                        stopRefreshAnimation();
                        isRefreshInProgress = false;
                        if (showToast) {
                            Toast.makeText(MainActivity.this, R.string.refresh_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                
                // 保存匹配的customerCode到设备
                if (matchedCustomerCode != null && selectedDevice != null) {
                    if (!matchedCustomerCode.equals(selectedDevice.getCustomerCode())) {
                        selectedDevice.setCustomerCode(matchedCustomerCode);
                        databaseHelper.addDevice(selectedDevice);
                        Log.d(TAG, "Updated device customerCode to: " + matchedCustomerCode);
                    }
                }
                
                // 创建final副本供lambda使用
                final NewApiService.DeviceInfo finalLatestInfo = latestInfo;
                final String finalMatchedCustomerCode = matchedCustomerCode;
                final boolean finalShowToast = showToast;
                long serverTimestamp = latestInfo.timestamp;
                Log.d(TAG, "Server timestamp: " + serverTimestamp + ", current timestamp: " + currentTimestamp);
                
                runOnUiThread(() -> {
                    stopRefreshAnimation();
                    isRefreshInProgress = false;

                    if (serverTimestamp > currentTimestamp) {
                        // 服务器时间更新，更新UI
                        Log.d(TAG, "Server data is newer, updating UI");
                        updateDeviceUIWithoutCameraMove(finalLatestInfo);

                        // 更新本地设备数据
                        if (selectedDevice != null) {
                            selectedDevice.setLatitude(finalLatestInfo.latitude);
                            selectedDevice.setLongitude(finalLatestInfo.longitude);
                            selectedDevice.setLastSeen(serverTimestamp);
                            selectedDevice.setBattery(finalLatestInfo.battery);
                            if (finalMatchedCustomerCode != null) {
                                selectedDevice.setCustomerCode(finalMatchedCustomerCode);
                            }
                            databaseHelper.addDevice(selectedDevice);
                        }

                        // 更新ViewModel中的LiveData
                        viewModel.setSelectedDevice(selectedDevice);
                        if (finalLatestInfo.battery > 0) {
                            viewModel.updateBatteryLevel(String.valueOf(finalLatestInfo.battery));
                        }
                        viewModel.updateUpdateTime(String.valueOf(serverTimestamp));

                        // 显示刷新成功提示
                        if (finalShowToast) {
                            Toast.makeText(MainActivity.this, R.string.refresh_success, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // 当前数据更新或相同，提示数据已是最新
                        Log.d(TAG, "Current data is newer or equal, keeping current display");
                        if (finalShowToast) {
                            Toast.makeText(MainActivity.this, R.string.data_already_latest, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Refresh API call failed: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    stopRefreshAnimation();
                    isRefreshInProgress = false;
                    if (showToast) {
                        Toast.makeText(MainActivity.this, R.string.refresh_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    /**
     * 刷新设备位置（优化版 - 使用ViewModel）
     */
    private void refreshDeviceLocation() {
        if (selectedDevice == null) {
            stopRefreshAnimation();
            Toast.makeText(this, R.string.please_select_device, Toast.LENGTH_SHORT).show();
            return;
        }
            
        final String deviceNum = selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
            
        // MVVM - 使用 ViewModel 获取设备信息
        viewModel.fetchDeviceInfo(deviceNum);
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
        };

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            // 检查是否需要显示权限解释
            boolean shouldShowRationale = false;
            for (String permission : permissionsToRequest) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    shouldShowRationale = true;
                    break;
                }
            }

            if (shouldShowRationale) {
                // 显示权限解释对话框
                showPermissionRationale(permissionsToRequest.toArray(new String[0]));
            } else {
                // 直接请求权限
                ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_PERMISSIONS);
            }
        }
    }

    private void showPermissionRationale(final String[] permissions) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.permission_request)
                .setMessage(R.string.permission_explanation)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_PERMISSIONS);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Toast.makeText(MainActivity.this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /**
     * 显示蓝牙增强扫描强度选择对话框
     */
    private void showBluetoothEnhanceOptions() {
        String[] options = {
            getString(R.string.scan_intensity_off),
            getString(R.string.scan_intensity_low),
            getString(R.string.scan_intensity_high)
        };

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.bluetooth_enhance)
                .setSingleChoiceItems(options, scanIntensityLevel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int oldLevel = scanIntensityLevel;
                        scanIntensityLevel = which;
                        Log.d(TAG, "Scan intensity changed: " + oldLevel + " -> " + which);

                        // 先停止当前扫描
                        if (locationOptimizationManager != null) {
                            locationOptimizationManager.stopBluetoothScanning();
                        }
                        if (scanningIndicator != null) {
                            scanningIndicator.setVisibility(View.GONE);
                        }

                        // 根据新强度启动扫描
                        switch (which) {
                            case 0: // 关闭
                                Log.d(TAG, "Scan intensity: OFF");
                                break;
                            case 1: // 低 - 单次扫描
                                Log.d(TAG, "Scan intensity: LOW (single scan)");
                                startSingleScanWithCheck();
                                break;
                            case 2: // 高 - 持续循环扫描
                                Log.d(TAG, "Scan intensity: HIGH (continuous)");
                                startContinuousScanWithCheck();
                                break;
                        }

                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 启动单次扫描（低强度）：扫描成功更新UI后自动停止
     */
    private void startSingleScanWithCheck() {
        if (locationOptimizationManager == null || !locationOptimizationManager.isOptimizationEnabled()) {
            Log.w(TAG, "Cannot start scanning: LocationOptimizationManager not available");
            scanIntensityLevel = 0;
            Toast.makeText(this, "蓝牙扫描不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        locationOptimizationManager.autoSelectFirstDevice();
        locationOptimizationManager.startSingleBluetoothScan();
        Log.d(TAG, "Single scan started (low intensity)");
    }

    /**
     * 启动持续循环扫描（高强度）：扫描10秒，停止10秒，循环
     */
    private void startContinuousScanWithCheck() {
        if (locationOptimizationManager == null || !locationOptimizationManager.isOptimizationEnabled()) {
            Log.w(TAG, "Cannot start scanning: LocationOptimizationManager not available");
            scanIntensityLevel = 0;
            Toast.makeText(this, "蓝牙扫描不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        locationOptimizationManager.autoSelectFirstDevice();
        locationOptimizationManager.startBluetoothScanning();
        Log.d(TAG, "Continuous scan started (high intensity)");
    }

    /**
     * 根据当前扫描强度级别应用扫描行为
     */
    private void applyScanIntensity() {
        // 先停止当前扫描
        if (locationOptimizationManager != null) {
            locationOptimizationManager.stopBluetoothScanning();
        }
        if (scanningIndicator != null) {
            scanningIndicator.setVisibility(View.GONE);
        }

        switch (scanIntensityLevel) {
            case 0: // 关闭
                Log.d(TAG, "Scan intensity: OFF");
                break;
            case 1: // 低
                startSingleScanWithCheck();
                break;
            case 2: // 高
                startContinuousScanWithCheck();
                break;
        }
    }

    private void startBLEScanning() {
        if (bleManager.isBluetoothEnabled()) {
            bleManager.startScanning(new BLEManager.DeviceScanCallback() {
                @Override
                public void onDeviceFound(Device device) {
                    Log.d(TAG, "Found device: " + device.getName() + " - " + device.getDeviceId());
                    databaseHelper.addDevice(device);
                    crowdSourcingManager.sendDeviceData(device);
                    // 更新地图标记
                    updateMapMarker(device);
                }

                @Override
                public void onScanComplete() {
                    Log.d(TAG, "BLE scanning completed");
                    Toast.makeText(MainActivity.this, R.string.scan_complete, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(this, R.string.bluetooth_not_enabled, Toast.LENGTH_SHORT).show();
        }
    }

    private Map<String, Marker> deviceMarkers = new HashMap<>();
    private Map<String, Polyline> devicePolylines = new HashMap<>();
    private Map<String, List<LatLng>> deviceLocations = new HashMap<>();

    private void updateMapMarker(Device device) {
        if (aMap == null || device == null) {
            return;
        }

        double latitude = device.getLatitude();
        double longitude = device.getLongitude();

        // 检查设备是否有有效的位置
        if (latitude == 0 && longitude == 0) {
            Log.d(TAG, "Device has no valid location: " + device.getName());
            return;
        }

        LatLng latLng = new LatLng(latitude, longitude);
        String deviceId = device.getDeviceId();

        // 更新设备位置历史
        List<LatLng> locations = deviceLocations.get(deviceId);
        if (locations == null) {
            locations = new ArrayList<>();
            deviceLocations.put(deviceId, locations);
        }
        locations.add(latLng);
        // 限制历史记录数量，避免内存占用过多
        if (locations.size() > 100) {
            locations.remove(0);
        }

        // 更新或创建轨迹线
        updateDevicePolyline(deviceId, locations);

        // 更新或创建标记
        Marker marker = deviceMarkers.get(deviceId);
        if (marker != null) {
            // 更新现有标记
            marker.setPosition(latLng);
            marker.setTitle(device.getName());
            marker.setSnippet(getString(R.string.signal_strength, device.getSignalStrength()) + "\n" +
                    getString(R.string.last_update_with_time, com.RockiotTag.tag.util.TimeFormatter.formatSmartTime(MainActivity.this, device.getLastSeen())));
        } else {
            // 创建新标记
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title(device.getName())
                    .snippet(getString(R.string.signal_strength, device.getSignalStrength()) + "\n" +
                            getString(R.string.last_update_with_time, com.RockiotTag.tag.util.TimeFormatter.formatSmartTime(MainActivity.this, device.getLastSeen())))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)); // 使用蓝色标记，避免紫色

            marker = aMap.addMarker(markerOptions);
            deviceMarkers.put(deviceId, marker);
        }

        // 移动相机到设备位置
        // aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18));
    }

    private void updateDevicePolyline(String deviceId, List<LatLng> locations) {
        if (locations.size() < 2) {
            return;
        }

        Polyline polyline = devicePolylines.get(deviceId);
        if (polyline != null) {
            // 更新现有轨迹线
            polyline.setPoints(locations);
        } else {
            // 创建新轨迹线
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(locations)
                    .width(5)
                    .color(Color.BLUE)
                    .setDottedLine(false);

            polyline = aMap.addPolyline(polylineOptions);
            devicePolylines.put(deviceId, polyline);
        }
    }

    private void updateCurrentLocationMarker() {
        if (aMap == null) {
            return;
        }
        
        LatLng latLng = new LatLng(currentLatitude, currentLongitude);
        
        if (currentLocationMarker == null) {
            // 创建自定义的带R图标的标记 - 使用 MapMarkerHelper
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .icon(com.RockiotTag.tag.util.MapMarkerHelper.createCustomMarkerWithR());
            currentLocationMarker = aMap.addMarker(markerOptions);
        } else {
            // 更新现有标记位置
            currentLocationMarker.setPosition(latLng);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEVICE_LIST && resultCode == RESULT_OK) {
            Log.d(TAG, "=== onActivityResult: Device list updated ===");
            
            String selectedDeviceId = "";
            
            // 优先使用 Intent 传递的数据，确保是最新选择的设备
            if (data != null && data.hasExtra("selected_device_id")) {
                selectedDeviceId = data.getStringExtra("selected_device_id");
                Log.d(TAG, "Got device ID from Intent: " + selectedDeviceId);
            } else {
                // 兼容旧版本，从 SharedPreferences 读取
                android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
                selectedDeviceId = prefs.getString("selected_device_id", "");
                Log.d(TAG, "Got device ID from SharedPreferences: " + selectedDeviceId);
            }
            
            if (!selectedDeviceId.isEmpty()) {
                Log.d(TAG, "Reloading selected device from database: " + selectedDeviceId);
                
                // 从数据库重新加载设备信息
                Device updatedDevice = databaseHelper.getDevice(selectedDeviceId);
                if (updatedDevice != null) {
                    Log.d(TAG, "Device loaded: name=" + updatedDevice.getName() + ", tag=" + updatedDevice.getTag() + 
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
                Log.d(TAG, "No selected device ID found, clearing device info");
                clearDeviceInfo();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }

        Log.d(TAG, "=== onResume called, currentTab=" + currentTab + " ===");

        // 从轨迹界面返回时，切换到指定Tab
        if (pendingTabSwitch >= 0) {
            Log.d(TAG, "Returning from TrackActivity, switching to tab " + pendingTabSwitch);
            int targetTab = pendingTabSwitch;
            pendingTabSwitch = -1;
            switchToTab(targetTab);
        } else if (currentTab == 2) {
            // 只有从TrackActivity返回时currentTab才是2（轨迹Tab不使用Fragment显示）
            Log.d(TAG, "Returning from TrackActivity, switching to home tab");
            switchToTab(0);
        }

        // 修复：从轨迹界面返回时，重置用户定位标志，允许地图自动跟随设备
        isUserLocated = false;
        Log.d(TAG, "Reset isUserLocated flag on resume");
        
        // 重置 MapManager 的用户交互状态（针对谷歌地图）
        if (mapManager != null) {
            mapManager.resetUserInteractionState();
            Log.d(TAG, "Reset MapManager user interaction state on resume");
        }
        
        // 【关键修复】在 onResume 中根据扫描强度级别决定是否重新启动蓝牙扫描
        if (scanIntensityLevel > 0 && locationOptimizationManager != null && locationOptimizationManager.isOptimizationEnabled()) {
            Log.d(TAG, "Restarting Bluetooth scanning in onResume (intensity=" + scanIntensityLevel + ")...");
            applyScanIntensity();
            Log.d(TAG, "✓ Bluetooth scanning restarted in onResume");
        } else {
            Log.d(TAG, "Scan intensity is OFF or manager unavailable, skipping Bluetooth scan restart in onResume");
        }
        
        // 从数据库重新加载设备信息，确保立即显示最新的本地数据
        if (selectedDevice != null && databaseHelper != null) {
            String deviceId = selectedDevice.getDeviceId();
            Log.d(TAG, "Refreshing device info for deviceId: " + deviceId);
            
            try {
                Device updatedDevice = databaseHelper.getDevice(deviceId);
                if (updatedDevice != null) {
                    // 如果设备信息发生变化（例如名称修改），则更新内存中的设备
                    if (!updatedDevice.getName().equals(selectedDevice.getName()) || 
                        !updatedDevice.getTag().equals(selectedDevice.getTag())) {
                        selectedDevice = updatedDevice;
                        Log.d(TAG, "Device info changed, updating memory: name=" + selectedDevice.getName() + ", tag=" + selectedDevice.getTag());
                        updateDeviceNameWithTag(selectedDevice.getName(), selectedDevice.getTag());
                    }
                                
                    // 重置标志位，允许新的请求
                    isFetchingDeviceInfo = false;
                    
                    // 修复：如果设备已有有效坐标，立即移动相机到设备位置
                    if (updatedDevice.getLatitude() != 0 && updatedDevice.getLongitude() != 0) {
                        Log.d(TAG, "Device has local coordinates, moving camera to device position on resume");
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
                Log.d(TAG, "No selected device, skipping refresh");
            }
            if (databaseHelper == null) {
                Log.w(TAG, "databaseHelper is null, skipping refresh");
            }
        }
        
        startTrackRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        stopTrackRefresh();
        
        // 【关键】只停止时间刷新，不停止蓝牙扫描（蓝牙扫描应该持续运行）
        if (locationOptimizationManager != null) {
            locationOptimizationManager.stopTimeRefresh();
            Log.d(TAG, "Time refresh stopped in onPause, but Bluetooth scanning continues");
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        
        // 【新增】在后台或退出时停止蓝牙扫描（onResume会根据开关状态决定是否恢复）
        if (locationOptimizationManager != null) {
            locationOptimizationManager.stopBluetoothScanning();
            Log.d(TAG, "Bluetooth scanning stopped in onStop (app going to background)");
        }
    }

    private void initTrackRefresh() {
        trackRefreshHandler = new com.RockiotTag.tag.util.SafeHandler(new com.RockiotTag.tag.util.SafeHandler.Callback() {
            @Override
            public void handleMessage(android.os.Message msg) {
                // 不需要处理消息，使用Runnable方式
            }
        });
        
        trackRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                refreshAndRecordLocation();
                if (trackRefreshHandler != null) {
                    trackRefreshHandler.postDelayed(this, TRACK_REFRESH_INTERVAL);
                }
            }
        };
    }

    private void startTrackRefresh() {
        if (trackRefreshHandler != null && trackRefreshRunnable != null) {
            trackRefreshHandler.removeCallbacks(trackRefreshRunnable);
            trackRefreshHandler.post(trackRefreshRunnable);
        }
    }

    private void stopTrackRefresh() {
        if (trackRefreshHandler != null && trackRefreshRunnable != null) {
            trackRefreshHandler.removeCallbacks(trackRefreshRunnable);
        }
    }

    private void refreshAndRecordLocation() {
        if (selectedDevice == null) {
            return;
        }

        final String deviceNum = selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
        final int currentSeq = ++deviceRefreshSequence;
        
        // 根据设备号长度设置对应的API URL
        NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(deviceNum));
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    NewApiService.ApiResponse syncResponse = apiService.syncDevice(deviceNum);
                    Log.d(TAG, "Auto refresh - sync device response: " + (syncResponse != null ? syncResponse.isSuccess() : "null"));
                    
                    // 检查序列号，如果已有新请求则丢弃本结果
                    if (currentSeq != deviceRefreshSequence) {
                        Log.d(TAG, "Auto refresh seq #" + currentSeq + " ignored (stale)");
                        return;
                    }
                    
                    NewApiService.DeviceInfo deviceInfo = null;
                    if (syncResponse != null && syncResponse.isSuccess()) {
                        deviceInfo = apiService.getDeviceLatest(deviceNum);
                        Log.d(TAG, "Auto refresh - got latest device info: " + (deviceInfo != null ? "yes" : "null"));
                    }
                    
                    // 再次检查序列号
                    if (currentSeq != deviceRefreshSequence) {
                        Log.d(TAG, "Auto refresh seq #" + currentSeq + " ignored (stale) after getDeviceLatest");
                        return;
                    }
                    
                    if (deviceInfo == null) {
                        deviceInfo = apiService.getDeviceInfo(deviceNum);
                        Log.d(TAG, "Auto refresh - fallback to getDeviceInfo: " + (deviceInfo != null ? "yes" : "null"));
                    }
                    
                    if (deviceInfo != null) {
                        // 最终检查序列号
                        if (currentSeq != deviceRefreshSequence) {
                            Log.d(TAG, "Auto refresh seq #" + currentSeq + " ignored (stale) before UI update");
                            return;
                        }
                        
                        final NewApiService.DeviceInfo finalDeviceInfo = deviceInfo;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // 后台自动刷新时不移动地图，只更新UI数据
                                updateDeviceUIWithoutCameraMove(finalDeviceInfo);
                            }
                        });
                        
                        if (deviceInfo.latitude != 0 && deviceInfo.longitude != 0 && deviceInfo.timestamp > 0) {
                            double newLat = deviceInfo.latitude;
                            double newLng = deviceInfo.longitude;
                            long newTimestamp = deviceInfo.timestamp;
                            
                            Log.d(TAG, "Checking location: lat=" + newLat + ", lng=" + newLng + 
                                  ", timestamp=" + newTimestamp + 
                                  ", lastRecordedTimestamp=" + lastRecordedTimestamp);
                            
                            if (newTimestamp <= lastRecordedTimestamp && lastRecordedTimestamp > 0) {
                                Log.d(TAG, "Skipping - timestamp not newer than last recorded");
                                return;
                            }
                            
                            if (newLat < -90 || newLat > 90 || newLng < -180 || newLng > 180) {
                                Log.d(TAG, "Skipping - invalid coordinates");
                                return;
                            }
                            
                            boolean isAbnormalJump = false;
                            if (lastRecordedLatitude != 0 && lastRecordedLongitude != 0) {
                                double distance = CoordinateUtils.calculateDistanceMeters(
                                    lastRecordedLatitude, lastRecordedLongitude,
                                    newLat, newLng
                                );
                                long timeDiff = newTimestamp - lastRecordedTimestamp;
                                if (timeDiff < 60000 && distance > 500) {
                                    Log.d(TAG, "Skipping - abnormal jump: " + distance + " meters in " + timeDiff + " ms");
                                    isAbnormalJump = true;
                                }
                            }
                            
                            if (isAbnormalJump) {
                                return;
                            }
                            
                            boolean shouldRecord = false;
                            if (lastRecordedLatitude == 0 && lastRecordedLongitude == 0) {
                                shouldRecord = true;
                                Log.d(TAG, "First point - recording");
                            } else {
                                double distance = CoordinateUtils.calculateDistanceMeters(
                                    lastRecordedLatitude, lastRecordedLongitude,
                                    newLat, newLng
                                );
                                shouldRecord = distance > 10;
                                Log.d(TAG, "Distance: " + distance + " meters, shouldRecord=" + shouldRecord);
                            }
                            
                            if (shouldRecord) {
                                Log.d(TAG, "Recording location: " + newLat + ", " + newLng + 
                                      ", deviceTimestamp=" + newTimestamp);
                                LocationRecord record = new LocationRecord(
                                    selectedDevice.getDeviceId(),
                                    newLat,
                                    newLng,
                                    newTimestamp
                                );
                                databaseHelper.addLocationRecord(record);
                                lastRecordedLatitude = newLat;
                                lastRecordedLongitude = newLng;
                                lastRecordedTimestamp = newTimestamp;
                                Log.d(TAG, "Location recorded successfully");
                            } else {
                                Log.d(TAG, "Skipping location record - change too small or same timestamp");
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing and recording location: " + e.getMessage(), e);
                }
            }
        }).start();
    }

    /**
     * 在地图初始化前恢复选中设备，用于设置地图初始位置
     */
    private void restoreSelectedDeviceForMapInit() {
        Log.d(TAG, "Restoring selected device for map initialization");
        if (databaseHelper == null) {
            Log.e(TAG, "databaseHelper is null, cannot restore device");
            return;
        }
        
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String selectedDeviceId = prefs.getString("selected_device_id", "");
        Log.d(TAG, "Saved selected device ID: " + selectedDeviceId);
        
        if (!selectedDeviceId.isEmpty()) {
            Device device = databaseHelper.getDevice(selectedDeviceId);
            if (device != null && device.getLatitude() != 0 && device.getLongitude() != 0) {
                Log.d(TAG, "Found device with location for map init: " + device.getName() + 
                      ", lat=" + device.getLatitude() + ", lng=" + device.getLongitude());
                // 设置地图目标位置为设备位置
                if (mapManager != null) {
                    mapManager.setTargetLocation(device.getLatitude(), device.getLongitude(), 17);
                }
            } else {
                Log.d(TAG, "Device has no valid location, will use default location");
            }
        } else {
            Log.d(TAG, "No saved device, will use default location");
        }
    }
    
    private void restoreSelectedDevice() {
        Log.d(TAG, "Restoring selected device");
        if (databaseHelper == null) {
            Log.e(TAG, "databaseHelper is null, cannot restore device");
            return;
        }
        
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String selectedDeviceId = prefs.getString("selected_device_id", "");
        Log.d(TAG, "Saved selected device ID: " + selectedDeviceId);
        
        List<Device> allDevices = databaseHelper.getAllDevices();
        Log.d(TAG, "Found " + (allDevices != null ? allDevices.size() : 0) + " devices in database");
        
        if (!selectedDeviceId.isEmpty() && allDevices != null) {
            for (Device device : allDevices) {
                if (device.getDeviceId().equals(selectedDeviceId)) {
                    Log.d(TAG, "Found saved device: " + device.getName() + ", deviceId: " + device.getDeviceId());
                    
                    // 【新增优化】设置当前选中的设备ID（MAC地址），用于过滤蓝牙扫描的Toast提醒
                    if (locationOptimizationManager != null) {
                        String macAddress = device.getMac();
                        if (macAddress != null && !macAddress.isEmpty()) {
                            locationOptimizationManager.setCurrentSelectedDeviceId(macAddress);
                            Log.d(TAG, "Set current selected device MAC for bluetooth filtering: " + macAddress);
                        } else {
                            Log.w(TAG, "Device MAC is null/empty: " + device.getName());
                        }
                    }
                    
                    // MVVM - 通过 ViewModel 设置选中设备
                    viewModel.setSelectedDevice(device);
                    selectedDevice = device; // 保持兼容性
                    updateDeviceNameWithTag(device.getName(), device.getTag());
                    if (bottomInfo != null) bottomInfo.setVisibility(View.VISIBLE);
                    
                    // 先显示默认的设备信息
                    updateDeviceUIDefault();
                    
                    if (device.getLatitude() != 0 && device.getLongitude() != 0) {
                        Log.d(TAG, "Device has location: lat=" + device.getLatitude() + ", lng=" + device.getLongitude());
                    }
                    
                    // 从数据库中读取最后的轨迹记录来初始化状态
                    initLastRecordedStateFromDb(selectedDeviceId);
                    
                    String deviceNumToFetch = device.getDeviceNum() != null ? device.getDeviceNum() : device.getDeviceId();
                    Log.d(TAG, "Calling fetchDeviceInfo for: " + deviceNumToFetch);
                    // MVVM - 使用 ViewModel 获取设备信息
                    viewModel.fetchDeviceInfo(deviceNumToFetch);
                    return;
                }
            }
            
            Log.d(TAG, "Saved device not found in database, clearing device info");
            clearDeviceInfo();
        }
        
        if (allDevices == null || allDevices.isEmpty()) {
            Log.d(TAG, "No devices in local database, waiting for user to add device...");
            clearDeviceInfo();
        } else {
            Log.d(TAG, "No saved device found, selecting first device from local database");
            selectFirstDevice(allDevices);
        }
    }
    
    private void initLastRecordedStateFromDb(String deviceId) {
        // 获取今天的时间范围
        Calendar startTime = Calendar.getInstance();
        startTime.set(Calendar.HOUR_OF_DAY, 0);
        startTime.set(Calendar.MINUTE, 0);
        startTime.set(Calendar.SECOND, 0);
        startTime.set(Calendar.MILLISECOND, 0);
        
        Calendar endTime = Calendar.getInstance();
        endTime.set(Calendar.HOUR_OF_DAY, 23);
        endTime.set(Calendar.MINUTE, 59);
        endTime.set(Calendar.SECOND, 59);
        endTime.set(Calendar.MILLISECOND, 999);
        
        List<LocationRecord> records = databaseHelper.getLocationRecords(
            deviceId, startTime.getTimeInMillis(), endTime.getTimeInMillis());
        
        if (records != null && !records.isEmpty()) {
            // 获取最后一条记录
            LocationRecord lastRecord = records.get(records.size() - 1);
            lastRecordedLatitude = lastRecord.getLatitude();
            lastRecordedLongitude = lastRecord.getLongitude();
            lastRecordedTimestamp = lastRecord.getTimestamp();
            Log.d(TAG, "Initialized from DB: lat=" + lastRecordedLatitude + 
                  ", lng=" + lastRecordedLongitude + 
                  ", timestamp=" + lastRecordedTimestamp);
        } else {
            // 没有记录，重置状态
            lastRecordedLatitude = 0;
            lastRecordedLongitude = 0;
            lastRecordedTimestamp = 0;
            Log.d(TAG, "No records in DB, reset state");
        }
    }
    
    private void clearDeviceInfo() {
        Log.d(TAG, "Clearing device info");
        selectedDevice = null;
        updateDeviceNameWithTag(getString(R.string.no_device_selected), null);
        if (bottomInfo != null) bottomInfo.setVisibility(View.VISIBLE);
        batteryLevelText.setText(getString(R.string.battery_level_empty));
        deviceAddressText.setText(getString(R.string.position_empty));
        updateTimeText.setText(getString(R.string.last_update_empty));
        
        if (deviceLocationMarker != null) {
            deviceLocationMarker.remove();
            deviceLocationMarker = null;
        }
    }
    
    private void syncDevicesFromApiAndSelectFirst() {
        // 使用默认的服务器URL（12位设备号的服务器）
        NewApiService.setApiBaseUrl(ApiConfig.SERVER_URL_12BIT);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Starting API sync...");
                    
                    // 同步数据
                    Log.d(TAG, "Syncing data from vendor API...");
                    NewApiService.ApiResponse syncResponse = apiService.syncAll();
                    Log.d(TAG, "Sync response: " + (syncResponse != null ? syncResponse.isSuccess() : "null"));
                    
                    Log.d(TAG, "Fetching bound devices from API...");
                    List<NewApiService.DeviceInfo> apiDevices = apiService.getBoundDeviceList();
                    Log.d(TAG, "Found " + apiDevices.size() + " devices from API");
                    
                    if (apiDevices.isEmpty()) {
                        Log.w(TAG, "API returned empty device list, skipping sync");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                clearDeviceInfo();
                            }
                        });
                        return;
                    }
                    
                    final List<Device> syncedDevices = new ArrayList<>();
                    int skippedCount = 0;
                    
                    for (NewApiService.DeviceInfo info : apiDevices) {
                        if (info.deviceNum == null) continue;
                        
                        String deviceId = info.deviceNum;
                        
                        if (unboundDeviceManager.isDeviceUnbound(deviceId)) {
                            Log.d(TAG, "Skipping unbound device: " + deviceId);
                            skippedCount++;
                            continue;
                        }
                        
                        if (!databaseHelper.isDeviceBound(deviceId)) {
                            Device device = new Device(deviceId, info.nickName != null ? info.nickName : getString(R.string.device_default_name, info.deviceNum));
                            device.setDeviceNum(info.deviceNum);
                            device.setLatitude(info.latitude);
                            device.setLongitude(info.longitude);
                            device.setLastSeen(info.timestamp > 0 ? info.timestamp : System.currentTimeMillis());
                            databaseHelper.addDevice(device);
                            syncedDevices.add(device);
                            Log.d(TAG, "Synced new device: " + device.getName());
                        } else {
                            Device existingDevice = databaseHelper.getDevice(deviceId);
                            if (existingDevice != null) {
                                if (existingDevice.getDeviceNum() == null) {
                                    existingDevice.setDeviceNum(info.deviceNum);
                                }
                                // 昵称：保留本地昵称，不使用服务器昵称覆盖
                                existingDevice.setLatitude(info.latitude);
                                existingDevice.setLongitude(info.longitude);
                                existingDevice.setLastSeen(info.timestamp > 0 ? info.timestamp : System.currentTimeMillis());
                                databaseHelper.addDevice(existingDevice);
                                syncedDevices.add(existingDevice);
                                Log.d(TAG, "Updated device: " + existingDevice.getName());
                            }
                        }
                    }
                    
                    final int finalSkippedCount = skippedCount;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!syncedDevices.isEmpty()) {
                                selectFirstDevice(syncedDevices);
                                Toast.makeText(MainActivity.this, getString(R.string.synced_devices, syncedDevices.size()), Toast.LENGTH_SHORT).show();
                            } else if (finalSkippedCount > 0) {
                                clearDeviceInfo();
                            } else {
                                clearDeviceInfo();
                            }
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error syncing device list: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, getString(R.string.sync_device_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
    
    private void selectFirstDevice(List<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            Log.d(TAG, "No devices to select");
            clearDeviceInfo();
            return;
        }
        
        Device firstDevice = devices.get(0);
        Log.d(TAG, "Auto-selecting first device: " + firstDevice.getName() + ", deviceId: " + firstDevice.getDeviceId());
        
        // 【新增优化】设置当前选中的设备ID（MAC地址），用于过滤蓝牙扫描的Toast提醒
        if (locationOptimizationManager != null) {
            String macAddress = firstDevice.getMac();
            if (macAddress != null && !macAddress.isEmpty()) {
                locationOptimizationManager.setCurrentSelectedDeviceId(macAddress);
                Log.d(TAG, "Set current selected device MAC for bluetooth filtering: " + macAddress);
            } else {
                Log.w(TAG, "First device MAC is null/empty: " + firstDevice.getName());
            }
        }
        
        // MVVM - 通过 ViewModel 设置选中设备
        viewModel.setSelectedDevice(firstDevice);
        selectedDevice = firstDevice; // 保持兼容性
        
        updateDeviceNameWithTag(firstDevice.getName(), firstDevice.getTag());
        if (bottomInfo != null) bottomInfo.setVisibility(View.VISIBLE);
        
        // 先显示默认的设备信息
        updateDeviceUIDefault();
        
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("selected_device_id", firstDevice.getDeviceId());
        editor.apply();
        
        if (firstDevice.getLatitude() != 0 && firstDevice.getLongitude() != 0) {
            // 不要在这里移动地图，等待 fetchDeviceInfo 返回后再移动
            // 这样可以确保标记已经添加后再移动地图
            Log.d(TAG, "Device has location: lat=" + firstDevice.getLatitude() + ", lng=" + firstDevice.getLongitude());
        }
        
        String deviceNumToFetch = firstDevice.getDeviceNum() != null ? firstDevice.getDeviceNum() : firstDevice.getDeviceId();
        Log.d(TAG, "Calling fetchDeviceInfo for: " + deviceNumToFetch);
        // MVVM - 使用 ViewModel 获取设备信息
        viewModel.fetchDeviceInfo(deviceNumToFetch);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    /**
     * 更新当前位置到地图（通用方法，适配高德和谷歌地图）
     */
    private void updateCurrentLocationOnMap() {
        if (mapManager != null) {
            if (mapManager.isGoogleMap()) {
                // 谷歌地图模式：完全禁用自动移动相机，由用户完全控制
                Log.d(TAG, "Google Map - Auto camera move disabled, user has full control");
            } else {
                // 高德地图模式：优化后的逻辑
                if (selectedDevice == null) {
                    // 没有选择设备时，只在第一次定位时移动地图
                    if (isFirstLocation) {
                        mapManager.moveCamera(currentLatitude, currentLongitude, 17);
                        isFirstLocation = false;
                        Log.d(TAG, "AMap - First location, moving to current position with zoom 17");
                    } else {
                        // 关键修复：禁用自动返回功能，避免干扰用户查看地图
                        // 用户如果想看自己的位置，可以点击定位按钮
                        Log.d(TAG, "AMap - No device selected, but auto-return disabled to avoid map jumping");
                    }
                } else {
                    Log.d(TAG, "AMap - Device selected, not moving map to phone location. Device: " + selectedDevice.getName());
                }
            }

            // 【禁用】请求附近的设备 - 不需要此功能
            // if (crowdSourcingManager != null) {
            //     crowdSourcingManager.requestNearbyDevices(currentLatitude, currentLongitude, 5000);
            // }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 1. 清理定位优化管理器（新增）
        if (locationOptimizationManager != null) {
            // 【确保】停止蓝牙扫描
            locationOptimizationManager.stopBluetoothScanning();
            locationOptimizationManager.cleanup();
            locationOptimizationManager = null;
        }
        
        // 2. 停止并清理轨迹刷新 Handler
        stopTrackRefresh();
        if (trackRefreshHandler != null) {
            trackRefreshHandler.cleanup();
            trackRefreshHandler = null;
        }
        
        // 3. 清理高德地图服务
        if (amapLocationService != null) {
            amapLocationService.onDestroy();
            amapLocationService = null;
        }
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
        
        // 6. 清理地图资源
        if (mapView != null) {
            mapView.onDestroy();
            mapView = null;
        }
        
        // 7. 清理 MapManager 引用
        if (mapManager != null) {
            mapManager = null;
        }
        
        // 8. 关闭数据库连接
        if (databaseHelper != null) {
            databaseHelper.close();
            databaseHelper = null;
        }
        
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
        googleDeviceLocationMarker = null;
        aMap = null;
        googleMapFragment = null;
        
        Log.d(TAG, "MainActivity resources fully cleaned up");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            List<String> deniedPermissions = new ArrayList<>();
            
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    deniedPermissions.add(permissions[i]);
                }
            }
            
            if (allGranted) {
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                initLocation();
                initBLE();
            } else {
                boolean permanentlyDenied = false;
                for (String permission : deniedPermissions) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        permanentlyDenied = true;
                        break;
                    }
                }
                
                if (permanentlyDenied) {
                    showPermissionSettingsDialog();
                } else {
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void showPermissionSettingsDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.permission_settings)
                .setMessage(R.string.permission_settings_explanation)
                .setPositiveButton(R.string.go_to_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }
    
    /**
     * MVVM - 设置 ViewModel 观察者
     * 核心原则：View 层只负责监听数据变化并渲染 UI，不修改数据
     */
    private void setupViewModelObservers() {
        // 观察选中的设备（包含位置信息）
        viewModel.getSelectedDevice().observe(this, device -> {
            if (device != null) {
                Log.d(TAG, "ViewModel: Selected device updated: " + device.getName() + 
                      ", lat=" + device.getLatitude() + ", lng=" + device.getLongitude());
                        
                String oldDeviceId = selectedDevice != null ? selectedDevice.getDeviceId() : null;
                String newDeviceId = device.getDeviceId();
                boolean isDeviceChanged = !newDeviceId.equals(oldDeviceId);
                        
                // 【关键修复】检查时间戳，防止服务器旧数据覆盖本地新数据
                if (selectedDevice != null && !isDeviceChanged) {
                    long currentTimestamp = selectedDevice.getLastSeen();
                    long newTimestamp = device.getLastSeen();
                    if (newTimestamp > 0 && currentTimestamp > 0 && newTimestamp <= currentTimestamp) {
                        Log.d(TAG, "Server data has older timestamp (" + newTimestamp + " <= " + currentTimestamp + "), ignoring update");
                        return; // 跳过更新，保留本地较新的数据
                    }
                }
                        
                selectedDevice = device;
                updateDeviceNameWithTag(device.getName(), device.getTag());
                        
                // 如果设备有有效坐标，更新地图和地址
                if (device.getLatitude() != 0 && device.getLongitude() != 0) {
                    Log.d(TAG, "Device has valid coordinates, isDeviceChanged=" + isDeviceChanged);
                            
                    // 只有当设备切换时才强制移动相机，后台刷新时不移动
                    if (isDeviceChanged) {
                        Log.d(TAG, "Device changed, calling locateToDevicePosition to move camera");
                        // 关键修复：切换设备后自动调用“定位到当前位置”功能（用户主动切换，允许后续自动刷新）
                        locateToDevicePosition(false);
                    } else {
                        Log.d(TAG, "Background refresh, updating marker and address (no camera move)");
                        // 后台刷新时，更新MapViewModel并获取地址
                        com.RockiotTag.tag.model.TagDevice tagDevice = new com.RockiotTag.tag.model.TagDevice(
                            device.getDeviceId(), device.getName()
                        );
                        tagDevice.setMac(device.getMac());
                        tagDevice.setLatitude(device.getLatitude());
                        tagDevice.setLongitude(device.getLongitude());
                        mapViewModel.updateDeviceLocation(tagDevice);
                                
                        // 关键修复：刷新时也获取地址，确保地址显示正确
                        deviceAddressText.setText(getString(R.string.position_getting_address));
                        getAddressFromLocation(device.getLatitude(), device.getLongitude(), true);
                    }
                } else {
                    Log.d(TAG, "Device has no valid coordinates yet, clearing UI");
                    // 设备没有有效坐标时，将电量、地址、时间显示为 "--"
                    batteryLevelText.setText(getString(R.string.battery_level_empty));
                    deviceAddressText.setText(getString(R.string.position_empty));
                    updateTimeText.setText(getString(R.string.last_update_empty));
                }
            }
        });
        
        // 观察电池电量
        viewModel.getBatteryLevel().observe(this, batteryStr -> {
            if (batteryStr != null && batteryLevelText != null) {
                try {
                    int battery = Integer.parseInt(batteryStr);
                    if (battery > 0) {
                        batteryLevelText.setText(getString(R.string.battery_level_value, String.valueOf(battery)));
                    } else if (battery == 0) {
                        batteryLevelText.setText(getString(R.string.battery_level_zero));
                    } else {
                        batteryLevelText.setText(getString(R.string.battery_level_unknown));
                    }
                } catch (NumberFormatException e) {
                    batteryLevelText.setText(getString(R.string.battery_level_unknown));
                }
            }
        });
        
        // 关键修复：重新启用设备地址的LiveData观察者，让逆地理编码结果更新到UI
        // updateDeviceUIWithLatest() 设置"正在获取地址..."后，LiveData会异步更新为真实地址
        viewModel.getDeviceAddress().observe(this, address -> {
            if (address != null && deviceAddressText != null) {
                // 添加调试日志，确认LiveData传递的值
                Log.d(TAG, "LiveData observer received address: " + address);
                
                if ("not_reported".equals(address)) {
                    deviceAddressText.setText(getString(R.string.position_not_reported));
                } else {
                    deviceAddressText.setText(getString(R.string.position_with_address, address));
                }
            }
        });
        
        // 观察更新时间 - 使用 TimeFormatter 智能格式化
        viewModel.getUpdateTime().observe(this, timeStr -> {
            if (timeStr != null && updateTimeText != null) {
                Log.d(TAG, "========== UPDATE TIME OBSERVER TRIGGERED ==========");
                Log.d(TAG, "Received timeStr: " + timeStr);
                
                if ("not_reported".equals(timeStr)) {
                    Log.d(TAG, "Setting text to: not_reported");
                    updateTimeText.setText(getString(R.string.last_update_not_reported));
                } else {
                    try {
                        long timestamp = Long.parseLong(timeStr);
                        String formattedTime = com.RockiotTag.tag.util.TimeFormatter.formatSmartTime(MainActivity.this, timestamp);
                        Log.d(TAG, "Parsed timestamp: " + timestamp + " (" + 
                            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                .format(new java.util.Date(timestamp)) + ")");
                        Log.d(TAG, "Formatted time: " + formattedTime);
                        
                        updateTimeText.setText(getString(R.string.last_update_with_time, formattedTime));
                        Log.d(TAG, "✓ UI updated with timestamp: " + timestamp);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "✗ Failed to parse timestamp: " + timeStr, e);
                        updateTimeText.setText(getString(R.string.last_update_not_reported));
                    }
                }
                Log.d(TAG, "====================================================");
            }
        });
        
        // 观察加载状态
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                if (isLoading) startRefreshAnimation();
                else stopRefreshAnimation();
            }
        });
        
        // 观察错误信息
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        // --- BleViewModel Observers ---
        bleViewModel.getScanResults().observe(this, devices -> {
            if (devices != null && unboundDeviceManager != null) {
                unboundDeviceManager.updateUnboundDevices(devices);
            }
        });
        
        bleViewModel.getIsConnected().observe(this, connected -> {
            if (connected) Toast.makeText(this, R.string.bluetooth_connected, Toast.LENGTH_SHORT).show();
        });

        // --- MapViewModel Observers ---
        mapViewModel.getDeviceLocation().observe(this, device -> {
            if (device != null && aMap != null) {
                updateDeviceMarkerOnMap(device);
            }
        });
    }
}