package com.RockiotTag.tag;

import com.RockiotTag.tag.util.ToastHelper;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.RockiotTag.tag.viewmodel.TrackViewModel;
import com.RockiotTag.tag.viewmodel.TrackViewModelFactory;
import com.RockiotTag.tag.model.LocationData;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.MapMarkerHelper;
import com.RockiotTag.tag.util.GoogleMapMarkerHelper;
import com.RockiotTag.tag.util.GeocodeHelper;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.util.SafeExecutor;
import com.RockiotTag.tag.helper.TrackBottomNavHelper;
import com.RockiotTag.tag.helper.TrackHelperInitializer;
import com.RockiotTag.tag.helper.TrackViewModelObserverHelper;
import com.RockiotTag.tag.helper.TrackDateHelper;
import com.RockiotTag.tag.helper.TrackThemeHelper;
import com.RockiotTag.tag.helper.TrackInfoPanelHelper;
import com.RockiotTag.tag.helper.TrackLoadingHelper;
import com.RockiotTag.tag.helper.TrackSyncHelper;
import com.RockiotTag.tag.helper.TrackAutoRefreshHelper;
import com.RockiotTag.tag.helper.TrackGeocodeHelper;
import com.RockiotTag.tag.helper.TrackMapCleanupHelper;
import com.RockiotTag.tag.helper.TrackRenderHelper;
import com.RockiotTag.tag.helper.TrackDataProcessor;
import com.RockiotTag.tag.helper.TrackEndpointHelper;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.map.MapAdapterFactory;
import com.RockiotTag.tag.map.amap.AMapManager;
import com.RockiotTag.tag.map.google.GoogleMapManager;

import java.lang.ref.WeakReference;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TrackActivity extends AppCompatActivity implements AMap.OnMarkerClickListener {

    private static final String TAG = "TrackActivity";
    private static final java.util.concurrent.ExecutorService threadPool =
        java.util.concurrent.Executors.newFixedThreadPool(4);
    private MapView mapView;
    private SupportMapFragment googleMapFragment;
    private IMapAdapter mapAdapter;
    /** 仅高德模式为 true，用于 MapView 生命周期隔离（与 MainActivity 一致） */
    private boolean amapLifecycleStarted = false;
    private DatabaseHelper databaseHelper;
    // GeocodeSearch 已迁移到 GeocodeHelper
    private Button dateBtn;
    private Calendar selectedDate;
    private Calendar startDate;
    private Calendar endDate;
    private TagDevice selectedDevice;

    // 统一的地图标记和折线变量（通过 IMapAdapter 接口操作）
    private List<Object> positionMarkers = new ArrayList<>();
    private List<Object> arrowMarkers = new ArrayList<>();
    private Object trackPolyline;

    private TextView trackPointTime;
    private TextView trackPointAddress;

    private List<LocationData> allLocationRecords = new CopyOnWriteArrayList<>();
    private List<StayPoint> stayPoints = new CopyOnWriteArrayList<>();
    private static final double STAY_DISTANCE_THRESHOLD = 30.0; // 30 米，平衡 GPS 精度和停留点识别
    private static final double MAX_SPEED_KMH = 200.0;

    // MVVM - 播放状态由 ViewModel 管理，这里只保留 UI 控制需要的引用
    // isPlaying, currentPlayIndex, playSpeed 都从 viewModel 获取

    // 播放相关（统一引用，通过 IMapAdapter 接口操作）
    private Object playMarker = null;
    private Object playedPolyline = null;
    private List<double[]> playedPoints = new ArrayList<>();
    private ValueAnimator moveAnimator = null;
    private double[] currentPlayPosition = null;
    
    // 底部导航栏
    private LinearLayout tabHome, tabList, tabTrack, tabProfile;
    private ImageView tabHomeIcon, tabListIcon, tabTrackIcon, tabProfileIcon;
    private ImageButton statisticsBtn; // 统计按钮
    private boolean showMarkers = true;
    private boolean showPolyline = true;
    
    // 精度调节相关变量
    private SeekBar accuracySeekBar;
    private TextView accuracyValueText;
    private int currentAccuracyThreshold = 140;
    
    // 加载进度条
    private ProgressBar loadingProgress;
    
    // 加载提示对话框
    private android.app.AlertDialog loadingDialog;
    
    // 缓存状态：记录哪些日期已经从服务器同步过数据（key: date string, value: boolean synced）
    private java.util.Map<String, Boolean> syncedDates = new java.util.HashMap<>();
    
    // 增量同步：记录每个日期最后同步的时间戳（key: date string, value: last sync timestamp）
    private java.util.Map<String, Long> lastSyncTimestamps = new java.util.HashMap<>();
    
    // 谷歌地图用户交互标志
    private boolean googleMapUserInteracted = false;
    
    // 保存当前地图缩放级别，刷新时保持标尺不变
    private float currentZoomLevel = 17.0f; // 默认缩放级别
    private boolean hasSavedZoomLevel = false; // 是否已保存过缩放级别
    
    // MVVM - ViewModel
    private TrackViewModel viewModel;

    // Helper 类（MVVM 重构：将业务逻辑从 Activity 中分离）
    private TrackDateHelper dateHelper;
    private TrackThemeHelper themeHelper;
    private TrackBottomNavHelper bottomNavHelper;
    private TrackSyncHelper syncHelper;
    private TrackAutoRefreshHelper autoRefreshHelper;
    private TrackInfoPanelHelper trackInfoPanelHelper;
    private TrackMapCleanupHelper.Host mapCleanupHost;
    private TrackEndpointHelper.Host trackEndpointHost;
    private TrackLoadingHelper loadingHelper;

    // 兼容性字段（从 ViewModel 同步，用于遗留代码）
    private boolean isPlaying = false;
    private int currentPlayIndex = 0;
    private int playSpeed = 1;
    
    // 关键修复：加载锁，防止并发加载导致崩溃
    private volatile boolean isLoadingTrackData = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            // 先恢复用户的语言偏好，如果没有设置过则使用系统语言
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
            
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_track);

            trackInfoPanelHelper = new TrackInfoPanelHelper(this);
            trackInfoPanelHelper.setup();

            // 应用深色模式
            boolean isDarkMode = getSharedPreferences("app_settings", MODE_PRIVATE).getBoolean("dark_mode", false);
            applyDarkMode(isDarkMode);

            // 处理状态栏，让 top_bar 向下偏移状态栏高度
            View topBar = findViewById(R.id.top_bar);
            if (topBar != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    topBar.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                        @Override
                        public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                            int statusBarHeight = insets.getInsets(WindowInsets.Type.statusBars()).top;
                            v.setPadding(0, statusBarHeight, 0, 0);
                            return insets;
                        }
                    });
                    // 请求应用 WindowInsets
                    topBar.requestApplyInsets();
                } else {
                    int statusBarHeight = getStatusBarHeight();
                    topBar.setPadding(0, statusBarHeight, 0, 0);
                }
            }

            // 根据深色模式设置状态栏图标颜色
            com.RockiotTag.tag.util.StatusBarHelper.setupStatusBar(this);

            // 隐藏系统 ActionBar（使用自定义标题栏）
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }

            databaseHelper = DatabaseHelper.getInstance(this);
            selectedDate = Calendar.getInstance();

            // 未登录时不加载设备
            String authToken = prefs.getString("auth_token", null);
            String selectedDeviceId = prefs.getString("selected_device_id", "");
            if (authToken != null && !authToken.isEmpty() && !selectedDeviceId.isEmpty()) {
                selectedDevice = databaseHelper.getDevice(selectedDeviceId);
                if (selectedDevice == null) {
                    Log.e(TAG, "Selected device not found in database: " + selectedDeviceId);
                } else {
                    LogUtil.d(TAG, "Selected device loaded: " + selectedDevice.getDeviceId());
                }
            } else {
                Log.w(TAG, "No device selected in preferences");
            }
            
            // 按当前地图提供商只初始化对应 SDK
            String mapProvider = MapAdapterFactory.getSavedProvider(this);

            mapView = findViewById(R.id.mapView);
            googleMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.googleMapFragment);

            com.RockiotTag.tag.map.MapLayerController.applyLayers(mapProvider, mapView, googleMapFragment);
            ensureOverlaysAboveMap();

            amapLifecycleStarted = com.RockiotTag.tag.map.MapLayerController.isAmapProvider(mapProvider);
            if (amapLifecycleStarted) {
                boolean languageJustChanged = prefs.getBoolean("language_just_changed", false);
                if (languageJustChanged) {
                    mapView.onCreate(null);
                } else {
                    mapView.onCreate(savedInstanceState);
                }
            }

            if (MapAdapterFactory.PROVIDER_AMAP.equals(mapProvider)) {
                mapAdapter = MapAdapterFactory.createAMapAdapter(this, mapView);
            } else {
                mapAdapter = MapAdapterFactory.createGoogleMapAdapter(this, googleMapFragment);
            }

            // 设置地图回调
            mapAdapter.setCallback(new IMapAdapter.MapCallback() {
                @Override
                public void onMapReady() {
                    onMapAdapterReady();
                }

                @Override
                public void onMapClick(double latitude, double longitude) {
                    LogUtil.d(TAG, "Map clicked at: " + latitude + ", " + longitude);
                    if (trackInfoPanelHelper != null) {
                        trackInfoPanelHelper.onMapAreaTap();
                    }
                }
            });

            // 初始化地图（高德同步完成，Google 异步回调 onMapReady）
            mapAdapter.initMap();

            // 返回按钮已移除，通过底部导航栏切换

            dateBtn = findViewById(R.id.date_btn);
            startDate = (Calendar) selectedDate.clone();
            startDate.set(Calendar.HOUR_OF_DAY, 0);
            startDate.set(Calendar.MINUTE, 0);
            startDate.set(Calendar.SECOND, 0);
            startDate.set(Calendar.MILLISECOND, 0);
            
            endDate = (Calendar) selectedDate.clone();
            endDate.set(Calendar.HOUR_OF_DAY, 23);
            endDate.set(Calendar.MINUTE, 59);
            endDate.set(Calendar.SECOND, 59);
            endDate.set(Calendar.MILLISECOND, 999);
            
            // 关键修复：检查 dateBtn 是否为 null
            if (dateBtn != null) {
                dateBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showDatePicker();
                    }
                });
            } else {
                Log.e(TAG, "dateBtn is null after findViewById!");
            }

            updateDateBtnText();

            ImageButton prevDayBtn = findViewById(R.id.prev_day_btn);
            prevDayBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    goToPreviousDay();
                }
            });

            ImageButton nextDayBtn = findViewById(R.id.next_day_btn);
            nextDayBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    goToNextDay();
                }
            });

            // 关键修复：检查所有 UI 组件是否为 null
            trackPointTime = findViewById(R.id.track_point_time);
            trackPointAddress = findViewById(R.id.track_point_address);
            
            if (trackPointTime == null) {
                Log.e(TAG, "trackPointTime is null after findViewById!");
            }
            if (trackPointAddress == null) {
                Log.e(TAG, "trackPointAddress is null after findViewById!");
            }
            
            initToolbar();
            
            // 初始化加载进度条
            loadingProgress = findViewById(R.id.loading_progress);

            // 底部导航栏
            tabHome = findViewById(R.id.tab_home);
            tabList = findViewById(R.id.tab_list);
            tabTrack = findViewById(R.id.tab_track);
            tabProfile = findViewById(R.id.tab_profile);
            tabHomeIcon = findViewById(R.id.tab_home_icon);
            tabListIcon = findViewById(R.id.tab_list_icon);
            tabTrackIcon = findViewById(R.id.tab_track_icon);
            tabProfileIcon = findViewById(R.id.tab_profile_icon);

            bottomNavHelper = new TrackBottomNavHelper(
                    this, tabHome, tabList, tabTrack, tabProfile,
                    tabHomeIcon, tabListIcon, tabTrackIcon, tabProfileIcon);
            bottomNavHelper.initBottomNavigation();
            ensureOverlaysAboveMap();
            
            // MVVM - 初始化 ViewModel（关键修复：确保在设置观察者之前初始化）
            try {
                TrackViewModelFactory factory = new TrackViewModelFactory();
                viewModel = new ViewModelProvider(this, factory).get(TrackViewModel.class);
                if (viewModel != null) {
                    viewModel.init(databaseHelper);
                    setupViewModelObservers();
                } else {
                    Log.e(TAG, "ViewModel is null after initialization!");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing ViewModel: " + e.getMessage(), e);
            }

            // MVVM 重构 - 初始化 Helper 类
            initHelpers();

            checkAndCleanOldTrackData();

            // 关键修复：延迟加载数据，确保地图完全初始化后再加载
            // 高德地图模式：地图已同步初始化，可以直接加载
            // Google 地图模式：等待 onMapReady 回调后再加载
            if (!mapAdapter.getProvider().equals("google")) {
                // 高德地图模式 - 地图已初始化，可以加载数据
                if (mapAdapter.isMapReady()) {
                    LogUtil.d(TAG, "AMap ready, loading track data immediately");
                    // 关键修复：增加安全检查，确保有选中的设备
                    if (selectedDevice != null) {
                        // 首次进入轨迹界面，强制从服务器同步，确保显示最新正确轨迹
                        loadTrackData(true);
                    } else {
                        Log.w(TAG, "No device selected, skip initial loadTrackData");
                        // 静默处理，不显示Toast
                    }
                } else {
                    Log.e(TAG, "AMap is null after initMap, retrying...");
                    // 如果地图仍未初始化，延迟重试
                    new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (mapAdapter.isMapReady() && !isFinishing() && !isDestroyed()) {
                            LogUtil.d(TAG, "Retrying loadTrackData after delay");
                            if (selectedDevice != null) {
                                loadTrackData(true);
                            } else {
                                Log.w(TAG, "No device selected during retry, skip loadTrackData");
                            }
                        }
                    }, 500);
                }
            }
            // Google 地图模式：不在这里加载，等待 onMapReady 回调
        } catch (Exception e) {
            Log.e(TAG, "TrackActivity init failed: " + e.getMessage(), e);
            finish();
        }
    }
    
    @Override
    protected void onDestroy() {
        try {
            super.onDestroy();

            // 停止自动刷新
            stopAutoRefresh();

            // 1. 停止播放并清理状态
            stopPlayback();

            // 2. 停止并清理动画
            if (moveAnimator != null) {
                moveAnimator.cancel();
                moveAnimator.removeAllListeners();
                moveAnimator = null;
            }

            // 3. 清理地图资源（通过适配器统一清理）
            for (Object marker : positionMarkers) {
                mapAdapter.removeObject(marker);
            }
            positionMarkers.clear();

            for (Object marker : arrowMarkers) {
                mapAdapter.removeObject(marker);
            }
            arrowMarkers.clear();

            if (playMarker != null) mapAdapter.removeObject(playMarker);
            if (playedPolyline != null) mapAdapter.removeObject(playedPolyline);
            if (trackPolyline != null) mapAdapter.removeObject(trackPolyline);

            playedPoints.clear();
            currentPlayPosition = null;

            // 4. 清理数据集合
            allLocationRecords.clear();
            stayPoints.clear();

            // 5. 清理地图适配器
            if (mapAdapter != null) {
                mapAdapter.onDestroy();
            }
            if (mapView != null && amapLifecycleStarted) {
                if (!isChangingConfigurations()) {
                    mapView.onDestroy();
                }
            }

            // 6. 释放数据库引用（单例不 close，随进程生命周期存在）
            databaseHelper = null;

            // 7. 清理自动刷新
            if (autoRefreshHelper != null) {
                autoRefreshHelper.cleanup();
            }
        } catch (Exception e) {
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        
        // 停止播放，释放资源
        if (isPlaying) {
            stopPlayback();
        }
    }

    private void initHelpers() {
        TrackHelperInitializer.initHelpers(createTrackHelperHost());
    }

    private TrackHelperInitializer.Host createTrackHelperHost() {
        return new TrackHelperInitializer.Host() {
            @Override public AppCompatActivity getActivity() { return TrackActivity.this; }
            @Override public IMapAdapter getMapAdapter() { return mapAdapter; }
            @Override public DatabaseHelper getDatabaseHelper() { return databaseHelper; }
            @Override public TrackViewModel getViewModel() { return viewModel; }
            @Override public TagDevice getSelectedDevice() { return selectedDevice; }
            @Override public Calendar getSelectedDate() { return selectedDate; }
            @Override public Calendar getStartDate() { return startDate; }
            @Override public Calendar getEndDate() { return endDate; }
            @Override public void setSelectedDate(Calendar date) { selectedDate = date; }
            @Override public void setStartDate(Calendar date) { startDate = date; }
            @Override public void setEndDate(Calendar date) { endDate = date; }
            @Override public List<Object> getPositionMarkers() { return positionMarkers; }
            @Override public List<Object> getArrowMarkers() { return arrowMarkers; }
            @Override public Object getTrackPolyline() { return trackPolyline; }
            @Override public void setTrackPolyline(Object polyline) { trackPolyline = polyline; }
            @Override public Object getPlayedPolyline() { return playedPolyline; }
            @Override public void setPlayedPolyline(Object polyline) { playedPolyline = polyline; }
            @Override public Object getPlayMarker() { return playMarker; }
            @Override public void setPlayMarker(Object marker) { playMarker = marker; }
            @Override public List<double[]> getPlayedPoints() { return playedPoints; }
            @Override public double[] getCurrentPlayPosition() { return currentPlayPosition; }
            @Override public void setCurrentPlayPosition(double[] position) { currentPlayPosition = position; }
            @Override public ValueAnimator getMoveAnimator() { return moveAnimator; }
            @Override public void setMoveAnimator(ValueAnimator animator) { moveAnimator = animator; }
            @Override public List<LocationData> getAllLocationRecords() { return allLocationRecords; }
            @Override public List<StayPoint> getStayPoints() { return stayPoints; }
            @Override public int getCurrentAccuracyThreshold() { return currentAccuracyThreshold; }
            @Override public boolean isShowPolyline() { return showPolyline; }
            @Override public boolean isShowMarkers() { return showMarkers; }
            @Override public boolean isLoadingTrackData() { return isLoadingTrackData; }
            @Override public void setLoadingTrackData(boolean loading) { isLoadingTrackData = loading; }
            @Override public java.util.Map<String, Boolean> getSyncedDates() { return syncedDates; }
            @Override public java.util.Map<String, Long> getLastSyncTimestamps() { return lastSyncTimestamps; }
            @Override public java.util.concurrent.ExecutorService getThreadPool() { return threadPool; }
            @Override public ProgressBar getLoadingProgress() { return loadingProgress; }
            @Override public LinearLayout getTabHome() { return tabHome; }
            @Override public LinearLayout getTabList() { return tabList; }
            @Override public LinearLayout getTabTrack() { return tabTrack; }
            @Override public LinearLayout getTabProfile() { return tabProfile; }
            @Override public ImageView getTabHomeIcon() { return tabHomeIcon; }
            @Override public ImageView getTabListIcon() { return tabListIcon; }
            @Override public ImageView getTabTrackIcon() { return tabTrackIcon; }
            @Override public ImageView getTabProfileIcon() { return tabProfileIcon; }
            @Override public void setMapCleanupHost(TrackMapCleanupHelper.Host host) { mapCleanupHost = host; }
            @Override public void setTrackEndpointHost(TrackEndpointHelper.Host host) { trackEndpointHost = host; }
            @Override public void setLoadingHelper(TrackLoadingHelper helper) { loadingHelper = helper; }
            @Override public void setDateHelper(TrackDateHelper helper) { dateHelper = helper; }
            @Override public void setThemeHelper(TrackThemeHelper helper) { themeHelper = helper; }
            @Override public void setSyncHelper(TrackSyncHelper helper) { syncHelper = helper; }
            @Override public void setAutoRefreshHelper(TrackAutoRefreshHelper helper) { autoRefreshHelper = helper; }
            @Override public TrackMapCleanupHelper.Host getMapCleanupHost() { return mapCleanupHost; }
            @Override public void updateDateBtnText() { TrackActivity.this.updateDateBtnText(); }
            @Override public void loadTrackData() { TrackActivity.this.loadTrackData(); }
            @Override public void loadTrackDataSilently() { TrackActivity.this.loadTrackData(false); }
            @Override public void stopPlayback() { TrackActivity.this.stopPlayback(); }
            @Override public void hideLoading() { TrackActivity.this.hideLoading(); }
            @Override public void hideLoadingDialog() { TrackActivity.this.hideLoadingDialog(); }
            @Override public void showLoadingDialog() { TrackActivity.this.showLoadingDialog(); }
            @Override public void updatePlaybackInfo(int count) { TrackActivity.this.updatePlaybackInfo(count); }
            @Override public void clearTrackUI() { TrackActivity.this.clearTrackUI(); }
            @Override public boolean isFinishing() { return TrackActivity.this.isFinishing(); }
            @Override public boolean isDestroyed() { return TrackActivity.this.isDestroyed(); }
        };
    }

    private void initToolbar() {
        statisticsBtn = findViewById(R.id.statistics_btn);
        ImageButton refreshBtn = findViewById(R.id.refresh_btn);
        
        // 初始化精度调节控件
        accuracySeekBar = findViewById(R.id.accuracy_seekbar);
        accuracyValueText = findViewById(R.id.accuracy_value_text);

        if (statisticsBtn != null) {
            statisticsBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTrackStatistics();
                }
            });
        } else {
            Log.e(TAG, "statisticsBtn is null after findViewById!");
        }
        
        if (refreshBtn != null) {
            refreshBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LogUtil.d(TAG, "Refresh button clicked, forcing sync from server");
                    loadTrackData(true);
                }
            });
        } else {
            Log.e(TAG, "refreshBtn is null after findViewById!");
        }
        
        // 初始化精度调节SeekBar
        // 范围: 50m(高精度,左) ~ 230m(低精度,右), 默认140m
        if (accuracySeekBar != null) {
            accuracySeekBar.setMax(180);
            accuracySeekBar.setProgress(90);
            currentAccuracyThreshold = 140;
            
            accuracySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        currentAccuracyThreshold = 50 + progress;
                        LogUtil.d(TAG, "=== ACCURACY SWITCH === 阈值: " + currentAccuracyThreshold + "m, progress=" + progress);
                        updateAccuracyValueText();
                        if (!allLocationRecords.isEmpty()) {
                            reloadTrackWithNewAccuracy();
                        }
                    }
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            
            if (accuracyValueText != null) {
                accuracyValueText.setVisibility(View.VISIBLE);
                updateAccuracyValueText();
            }
        } else {
            Log.e(TAG, "accuracySeekBar is null after findViewById!");
        }
    }
    
    /**
     * 更新精度值文本显示（低/中/高）
     */
    private void updateAccuracyValueText() {
        if (accuracyValueText != null) {
            String level;
            if (currentAccuracyThreshold <= 90) {
                level = getString(R.string.accuracy_level_high);
            } else if (currentAccuracyThreshold <= 170) {
                level = getString(R.string.accuracy_level_medium);
            } else {
                level = getString(R.string.accuracy_level_low);
            }
            accuracyValueText.setText(getString(R.string.accuracy_current_level, level));
        }
    }
    
    /**
     * 使用新的精度阈值重新加载轨迹
     */
    private void reloadTrackWithNewAccuracy() {
        if (viewModel == null || allLocationRecords.isEmpty()) {
            Log.w(TAG, "reloadTrackWithNewAccuracy: viewModel is null or no records");
            return;
        }
        
        // 清除当前UI
        clearTrackUI();
        
        // 在ViewModel中重新生成停留点（使用新的精度阈值）
        List<StayPoint> newStayPoints = viewModel.generateStayPointsFromRecordsWithAccuracy(
            allLocationRecords, currentAccuracyThreshold);
        
        LogUtil.d(TAG, "=== ACCURACY SWITCH === 阈值: " + currentAccuracyThreshold + "m | 生成停留点数量: " + newStayPoints.size());
        
        synchronized (stayPoints) {
            stayPoints.clear();
            stayPoints.addAll(newStayPoints);
        }
        
        // 【诊断日志】确认内存中的数据已更新
        Log.e(TAG, "【诊断】内存中 stayPoints 数量已更新为: " + stayPoints.size() + "，即将开始渲染");
        
        // 重新渲染轨迹（此时 renderTrack 内部会直接读取更新后的 stayPoints 成员变量）
        renderTrack(allLocationRecords);
    }
    
    private void setupViewModelObservers() {
        TrackViewModelObserverHelper.setupObservers(createTrackObserverHost());
    }

    private TrackViewModelObserverHelper.Host createTrackObserverHost() {
        return new TrackViewModelObserverHelper.Host() {
            @Override public LifecycleOwner getLifecycleOwner() { return TrackActivity.this; }
            @Override public AppCompatActivity getActivity() { return TrackActivity.this; }
            @Override public TrackViewModel getViewModel() { return viewModel; }
            @Override public List<LocationData> getAllLocationRecords() { return allLocationRecords; }
            @Override public List<StayPoint> getStayPoints() { return stayPoints; }
            @Override public int getCurrentAccuracyThreshold() { return currentAccuracyThreshold; }
            @Override public boolean isLoadingTrackData() { return isLoadingTrackData; }
            @Override public void setLoadingTrackData(boolean loading) { isLoadingTrackData = loading; }
            @Override public ProgressBar getLoadingProgress() { return loadingProgress; }
            @Override public TagDevice getSelectedDevice() { return selectedDevice; }
            @Override public void renderTrack(List<LocationData> records) { TrackActivity.this.renderTrack(records); }
            @Override public void clearTrackUI() { TrackActivity.this.clearTrackUI(); }
            @Override public void updatePlaybackInfo(int count) { TrackActivity.this.updatePlaybackInfo(count); }
            @Override public void hideLoading() { TrackActivity.this.hideLoading(); }
            @Override public void showLoadingDialog() { TrackActivity.this.showLoadingDialog(); }
            @Override public void syncTrackDataFromServerAndReload(String deviceNum, long startTime, long endTime) {
                TrackActivity.this.syncTrackDataFromServerAndReload(deviceNum, startTime, endTime);
            }
            @Override public void setIsPlaying(boolean playing) { isPlaying = playing; }
            @Override public void setCurrentPlayIndex(int index) { currentPlayIndex = index; }
            @Override public void setPlaySpeed(int speed) { playSpeed = speed; }
        };
    }
        
    /**
     * 只清除UI元素，保留数据用于重新渲染
     */
    private void clearTrackUI() {
        if (mapCleanupHost != null) TrackMapCleanupHelper.clearTrackUI(mapCleanupHost);
    }

    private void startPlayback() {
        try {
            if (stayPoints.isEmpty()) {
                // 静默处理，不显示Toast
                return;
            }

            // MVVM - 使用 ViewModel 管理播放状态
            viewModel.startPlayback();

            // 初始化地图播放标记（统一方法）
            initPlayback();
        } catch (Exception e) {
            Log.e(TAG, "Error in startPlayback: " + e.getMessage(), e);
            // 静默处理，不显示Toast
            viewModel.pausePlayback();
        }
    }

    /**
     * 初始化播放标记（合并高德和 Google 地图逻辑）
     */
    private void initPlayback() {
        if (playMarker != null) {
            mapAdapter.removeObject(playMarker);
            playMarker = null;
        }
        playedPoints.clear();
        currentPlayPosition = null;

        StayPoint stayPoint = stayPoints.get(0);
        double lat = stayPoint.getLatitude();
        double lng = stayPoint.getLongitude();
        currentPlayPosition = new double[]{lat, lng};

        // 创建播放标记（引擎特定：自定义 emoji 图标，IMapAdapter 接口不支持动态图标）
        Object icon = createPlayMarker();
        if (mapAdapter.getProvider().equals("google")) {
            com.google.android.gms.maps.model.LatLng startPos =
                new com.google.android.gms.maps.model.LatLng(lat, lng);
            com.google.android.gms.maps.model.MarkerOptions markerOptions =
                new com.google.android.gms.maps.model.MarkerOptions()
                    .position(startPos)
                    .title(getString(R.string.play_position))
                    .icon((com.google.android.gms.maps.model.BitmapDescriptor) icon)
                    .anchor(0.5f, 0.5f);
            playMarker = ((GoogleMapManager) mapAdapter).getGoogleMap().addMarker(markerOptions);
        } else {
            LatLng startPos = CoordinateUtils.wgs84ToGcj02(lat, lng);
            MarkerOptions markerOptions = new MarkerOptions()
                .position(startPos)
                .title(getString(R.string.play_position))
                .icon((com.amap.api.maps.model.BitmapDescriptor) icon)
                .anchor(0.5f, 0.5f);
            playMarker = ((AMapManager) mapAdapter).getAMap().addMarker(markerOptions);
        }
        playedPoints.add(new double[]{lat, lng});

        moveToNextPoint();
    }

    private void pausePlayback() {
        try {
            // MVVM - 使用 ViewModel 暂停播放
            viewModel.pausePlayback();

            if (moveAnimator != null && moveAnimator.isRunning()) {
                moveAnimator.cancel();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in pausePlayback: " + e.getMessage(), e);
        }
    }

    private void stopPlayback() {
        try {
            // MVVM - 使用 ViewModel 停止播放
            viewModel.stopPlayback();

            // 关键修复：彻底清理动画，移除所有监听器
            if (moveAnimator != null) {
                if (moveAnimator.isRunning()) {
                    moveAnimator.cancel();
                }
                moveAnimator.removeAllUpdateListeners();
                moveAnimator.removeAllListeners();
                moveAnimator = null;
            }

            // 清理播放相关对象（通过适配器统一清理）
            if (playMarker != null) {
                mapAdapter.removeObject(playMarker);
                playMarker = null;
            }
            if (playedPolyline != null) {
                mapAdapter.removeObject(playedPolyline);
                playedPolyline = null;
            }
            playedPoints.clear();
            currentPlayPosition = null;
        } catch (Exception e) {
            Log.e(TAG, "Error in stopPlayback: " + e.getMessage(), e);
        }
    }

    private void moveToNextPoint() {
        try {
            // MVVM - 使用 ViewModel 判断是否可以继续播放
            if (!viewModel.canMoveToNext()) {
                viewModel.pausePlayback();
                return;
            }

            // MVVM - 从 ViewModel 获取业务逻辑数据
            StayPoint fromStayPoint = viewModel.getCurrentStayPoint();
            StayPoint toStayPoint = viewModel.getNextStayPoint();

            if (fromStayPoint == null || toStayPoint == null) return;

            // 使用 WGS84 坐标进行插值，适配器内部处理坐标转换
            double fromLat = fromStayPoint.getLatitude();
            double fromLng = fromStayPoint.getLongitude();
            double toLat = toStayPoint.getLatitude();
            double toLng = toStayPoint.getLongitude();

            if (currentPlayPosition != null) {
                fromLat = currentPlayPosition[0];
                fromLng = currentPlayPosition[1];
            }

            final double finalFromLat = fromLat;
            final double finalFromLng = fromLng;
            final double finalToLat = toLat;
            final double finalToLng = toLng;

            // MVVM - 使用 ViewModel 计算动画时长
            Integer playSpeed = viewModel.getPlaySpeed().getValue();
            if (playSpeed == null) playSpeed = 1;
            long duration = viewModel.calculateAnimationDuration(playSpeed);

            moveAnimator = ValueAnimator.ofFloat(0f, 1f);
            moveAnimator.setDuration(duration);

            moveAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float fraction = animation.getAnimatedFraction();

                    double lat = finalFromLat + (finalToLat - finalFromLat) * fraction;
                    double lng = finalFromLng + (finalToLng - finalFromLng) * fraction;

                    currentPlayPosition = new double[]{lat, lng};

                    if (playMarker != null) {
                        mapAdapter.setMarkerPosition(playMarker, lat, lng);
                    }

                    // 只在关键点更新相机
                    Integer currentIndex = viewModel.getCurrentPlayIndex().getValue();
                    int totalPoints = viewModel.getTotalStayPoints();
                    if (currentIndex != null && (currentIndex % 5 == 0 || currentIndex >= totalPoints - 2)) {
                        mapAdapter.animateCamera(lat, lng, mapAdapter.getZoomLevel());
                    }
                }
            });

            moveAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {}

                @Override
                public void onAnimationEnd(Animator animation) {
                    Boolean isStillPlaying = viewModel.getIsPlaying().getValue();
                    if (isStillPlaying != null && isStillPlaying) {
                        // MVVM - 推进播放索引
                        viewModel.moveToNextPoint();
                        currentPlayPosition = new double[]{finalToLat, finalToLng};

                        playedPoints.add(new double[]{finalToLat, finalToLng});
                        updatePlayedPolyline();

                        trackPointTime.setText(viewModel.getCurrentPlayFullTimeString());

                        // MVVM - 根据 ViewModel 判断是否需要更新地址
                        if (viewModel.shouldUpdateAddress()) {
                            getAddressForLocation(new double[]{finalToLat, finalToLng});
                        }

                        // 继续播放下一帧
                        moveToNextPoint();
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {}

                @Override
                public void onAnimationRepeat(Animator animation) {}
            });

            moveAnimator.start();
        } catch (Exception e) {
            Log.e(TAG, "Error in moveToNextPoint: " + e.getMessage(), e);
            viewModel.pausePlayback();
        }
    }

    /**
     * 更新已播放轨迹线（合并高德和 Google 地图逻辑）
     */
    private void updatePlayedPolyline() {
        if (playedPoints.size() > 1) {
            if (playedPolyline == null) {
                // 第一次创建
                playedPolyline = mapAdapter.drawPolyline(playedPoints, 0xFFFF5722, 12f);
            } else {
                // 后续更新点
                mapAdapter.updatePolylinePoints(playedPolyline, playedPoints);
            }
        }
    }

    private void updatePlayPosition() {
        try {
            // MVVM - 从 ViewModel 获取总停留点数
            int totalPoints = viewModel.getTotalStayPoints();
            if (totalPoints == 0) return;

            // 统一逻辑：使用 WGS84 坐标的 playedPoints
            playedPoints.clear();
            for (int i = 0; i <= currentPlayIndex && i < totalPoints; i++) {
                double[] coords = viewModel.getStayPointCoordinates(i);
                if (coords != null) {
                    playedPoints.add(coords);
                }
            }

            updatePlayedPolyline();

            if (!playedPoints.isEmpty()) {
                double[] lastCoords = playedPoints.get(playedPoints.size() - 1);
                currentPlayPosition = lastCoords;

                if (playMarker != null) {
                    mapAdapter.setMarkerPosition(playMarker, lastCoords[0], lastCoords[1]);
                } else {
                    // 创建播放标记（引擎特定：自定义 emoji 图标）
                    Object icon = createPlayMarker();
                    if (mapAdapter.getProvider().equals("google")) {
                        com.google.android.gms.maps.model.LatLng latLng =
                            new com.google.android.gms.maps.model.LatLng(lastCoords[0], lastCoords[1]);
                        com.google.android.gms.maps.model.MarkerOptions markerOptions =
                            new com.google.android.gms.maps.model.MarkerOptions()
                                .position(latLng)
                                .title(getString(R.string.play_position))
                                .icon((com.google.android.gms.maps.model.BitmapDescriptor) icon)
                                .anchor(0.5f, 0.5f);
                        playMarker = ((GoogleMapManager) mapAdapter).getGoogleMap().addMarker(markerOptions);
                    } else {
                        LatLng latLng = CoordinateUtils.wgs84ToGcj02(lastCoords[0], lastCoords[1]);
                        MarkerOptions markerOptions = new MarkerOptions()
                            .position(latLng)
                            .title(getString(R.string.play_position))
                            .icon((com.amap.api.maps.model.BitmapDescriptor) icon)
                            .anchor(0.5f, 0.5f);
                        playMarker = ((AMapManager) mapAdapter).getAMap().addMarker(markerOptions);
                    }
                }

                // 相机移动（Google 地图的 animateCamera 在 GoogleMapManager 中已禁用自动移动）
                if (!mapAdapter.getProvider().equals("google")) {
                    mapAdapter.animateCamera(lastCoords[0], lastCoords[1], mapAdapter.getZoomLevel());
                }

                // 获取地址
                if (currentPlayIndex % 10 == 0 || isStayPointAt(currentPlayIndex)) {
                    getAddressForLocation(lastCoords);
                }
            }

            // MVVM - 使用 ViewModel 获取时间信息
            trackPointTime.setText(viewModel.getCurrentPlayFullTimeString());
        } catch (Exception e) {
            Log.e(TAG, "Error in updatePlayPosition: " + e.getMessage(), e);
        }
    }
    
    /**
     * 判断当前索引是否是停留点
     */
    private boolean isStayPointAt(int index) {
        if (stayPoints == null || stayPoints.isEmpty() || index >= allLocationRecords.size()) {
            return false;
        }
        
        long timestamp = allLocationRecords.get(index).getTimestamp();
        
        // 查找是否有停留点包含这个时间戳
        for (StayPoint sp : stayPoints) {
            if (sp.isStayPoint()) {
                List<LocationData> records = sp.getMergedRecords();
                for (LocationData record : records) {
                    if (record.getTimestamp() == timestamp) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    private void cycleSpeed() {
        try {
            // MVVM - 使用 ViewModel 管理播放速度
            Integer currentSpeed = viewModel.getPlaySpeed().getValue();
            if (currentSpeed == null) currentSpeed = 1;
            
            int newSpeed;
            if (currentSpeed == 1) {
                newSpeed = 2;
            } else if (currentSpeed == 2) {
                newSpeed = 4;
            } else if (currentSpeed == 4) {
                newSpeed = 8;
            } else {
                newSpeed = 1;
            }
            viewModel.setPlaySpeed(newSpeed);
            LogUtil.d(TAG, "Speed changed to: " + newSpeed + "x");
        } catch (Exception e) {
            Log.e(TAG, "Error in cycleSpeed: " + e.getMessage(), e);
        }
    }

    /**
     * 创建播放标记图标（合并高德和 Google 地图逻辑）
     * @return 引擎特定的 BitmapDescriptor（调用方按 getProvider() cast）
     */
    private Object createPlayMarker() {
        // 获取设备的 tag
        String deviceTag = "";
        if (selectedDevice != null && selectedDevice.getTag() != null && !selectedDevice.getTag().isEmpty()) {
            deviceTag = selectedDevice.getTag();
        }

        // 根据 tag 获取对应的 emoji 图标
        String emoji = MapMarkerHelper.getTagEmoji(deviceTag);

        // 使用 emoji 绘制播放图标（引擎特定）
        if (mapAdapter.getProvider().equals("google")) {
            return GoogleMapMarkerHelper.createEmojiMarker(emoji);
        } else {
            return MapMarkerHelper.createEmojiMarker(emoji);
        }
    }

    /**
     * 地图适配器就绪回调（合并原 initMap 和 onMapReady 逻辑）
     * 由 MapCallback.onMapReady() 调用，处理引擎特定的额外初始化
     */
    private void onMapAdapterReady() {
        try {
            LogUtil.d(TAG, "Map adapter ready, provider: " + mapAdapter.getProvider());

            String mapProvider = MapAdapterFactory.getSavedProvider(this);
            com.RockiotTag.tag.map.MapLayerController.applyLayers(mapProvider, mapView, googleMapFragment);
            ensureOverlaysAboveMap();

            // 应用深色地图样式
            boolean isDarkMode = getSharedPreferences("app_settings", MODE_PRIVATE).getBoolean("dark_mode", false);
            mapAdapter.setDarkMapStyle(isDarkMode);

            // 引擎特定的额外设置
            if (mapAdapter.getProvider().equals("google")) {
                // Google 地图特定设置
                GoogleMap gm = ((GoogleMapManager) mapAdapter).getGoogleMap();
                if (gm != null) {
                    // 添加相机移动监听 - 检测用户手动交互
                    gm.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
                        @Override
                        public void onCameraMoveStarted(int reason) {
                            LogUtil.d(TAG, "Camera move started, reason: " + reason);
                            if (reason == 1) {
                                googleMapUserInteracted = true;
                                LogUtil.d(TAG, "User manually interacted with Google Map - auto camera moves disabled");
                            }
                        }
                    });

                    // 添加相机空闲监听 - 在相机移动结束后保存缩放级别
                    gm.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
                        @Override
                        public void onCameraIdle() {
                            if (googleMapUserInteracted) {
                                currentZoomLevel = gm.getCameraPosition().zoom;
                                hasSavedZoomLevel = true;
                                LogUtil.d(TAG, "Google Map - Saved zoom level after idle: " + currentZoomLevel);
                            }
                        }
                    });
                }
            } else {
                // 高德地图特定设置
                AMap am = ((AMapManager) mapAdapter).getAMap();
                if (am != null) {
                    am.getUiSettings().setScaleControlsEnabled(true);
                    am.getUiSettings().setZoomControlsEnabled(false);
                    // 设置logo底部margin，让logo不被导航栏遮挡
                    int logoMargin = dpToPx(86);
                    am.getUiSettings().setLogoBottomMargin(logoMargin);
                    am.setOnMarkerClickListener(this);

                    // 添加相机移动监听 - 保存缩放级别
                    am.setOnCameraChangeListener(new com.amap.api.maps.AMap.OnCameraChangeListener() {
                        @Override
                        public void onCameraChange(com.amap.api.maps.model.CameraPosition cameraPosition) {
                        }

                        @Override
                        public void onCameraChangeFinish(com.amap.api.maps.model.CameraPosition cameraPosition) {
                            if (cameraPosition != null) {
                                currentZoomLevel = cameraPosition.zoom;
                                hasSavedZoomLevel = true;
                                LogUtil.d(TAG, "AMap - Saved zoom level: " + currentZoomLevel);
                            }
                        }
                    });
                }
            }

            LogUtil.d(TAG, "Map initialized successfully");

            // 加载轨迹数据
            if (selectedDevice != null) {
                loadTrackData(true);
            } else {
                Log.w(TAG, "No device selected in onMapAdapterReady, skip loadTrackData");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onMapAdapterReady: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean onMarkerClick(Marker marker) {
        try {
            // 关键修复：检查 marker 和 positionMarkers 是否为 null
            if (marker == null) {
                Log.w(TAG, "onMarkerClick: marker is null");
                return false;
            }
            
            if (positionMarkers == null) {
                Log.w(TAG, "onMarkerClick: positionMarkers is null");
                return false;
            }
            
            int markerIndex = positionMarkers.indexOf(marker);
            if (markerIndex >= 0 && stayPoints != null && markerIndex < stayPoints.size()) {
                StayPoint stayPoint = stayPoints.get(markerIndex);
                
                if (stayPoint == null) {
                    Log.w(TAG, "onMarkerClick: stayPoint at index " + markerIndex + " is null");
                    return false;
                }
                
                StringBuilder info = new StringBuilder();
                
                info.append(getString(R.string.track)).append(" #").append(stayPoint.getOriginalIndex()).append("\n");
                info.append(com.RockiotTag.tag.util.TimeFormatter.formatFullTime(stayPoint.getArriveTime()));
                
                if (stayPoint.getMergedCount() > 1) {
                    info.append(" - ").append(com.RockiotTag.tag.util.TimeFormatter.formatFullTime(stayPoint.getLeaveTime()));
                }
                
                if (stayPoint.isStayPoint()) {
                    info.append("\n").append(getString(R.string.stay_duration, stayPoint.getStayDurationFormatted()));
                }
                
                if (stayPoint.getMergedCount() > 1) {
                    info.append("\n").append(getString(R.string.merged_points, stayPoint.getMergedCount()));
                }
                
                // 关键修复：检查 trackPointTime 是否为 null
                if (trackPointTime != null) {
                    trackPointTime.setText(com.RockiotTag.tag.util.TimeFormatter.formatFullTime(stayPoint.getArriveTime()));
                }

                // 使用停留点的 WGS84 坐标获取地址
                getAddressForLocation(new double[]{stayPoint.getLatitude(), stayPoint.getLongitude()});
                
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.track) + " #" + stayPoint.getOriginalIndex());
                builder.setMessage(info.toString());
                builder.setPositiveButton(getString(R.string.confirm), null);
                builder.show();
                
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error in onMarkerClick: " + e.getMessage(), e);
            return false;
        }
    }

    private void showDatePicker() {
        dateHelper.showDatePicker();
    }

    private void updateDateBtnText() {
        try {
            Calendar today = Calendar.getInstance();
            if (selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                selectedDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                selectedDate.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)) {
                dateBtn.setText(getString(R.string.today));
            } else {
                dateBtn.setText(com.RockiotTag.tag.util.TimeFormatter.formatDate(selectedDate.getTimeInMillis()));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in updateDateBtnText: " + e.getMessage(), e);
        }
    }

    private void showStartTimePicker() {
        dateHelper.showStartTimePicker();
    }

    private void showEndTimePicker() {
        dateHelper.showEndTimePicker();
    }

    private void resetTimeRange() {
        dateHelper.resetTimeRange();
    }

    private void goToPreviousDay() {
        dateHelper.goToPreviousDay();
    }

    private void goToNextDay() {
        dateHelper.goToNextDay();
    }

    private void loadTrackData() {
        LogUtil.d(TAG, "=== loadTrackData() called === provider=" + (mapAdapter != null ? mapAdapter.getProvider() : "null"));
        try {
            if (mapAdapter == null || !mapAdapter.isMapReady()) {
                Log.w(TAG, "[MAP_NOT_READY] Map not ready yet, skip loadTrackData");
                return;
            }

            loadTrackData(false);
        } catch (Exception e) {
            Log.e(TAG, "[EXCEPTION] loadTrackData: " + e.getMessage(), e);
        }
    }

    private void loadTrackData(final boolean forceSync) {
        LogUtil.d(TAG, "=== loadTrackData(forceSync=" + forceSync + ") START === isLoadingTrackData=" + isLoadingTrackData);
        try {
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "[ACTIVITY_STATE] Activity is finishing or destroyed, skip loadTrackData");
                return;
            }
            
            if (isLoadingTrackData) {
                Log.w(TAG, "[LOADING_BLOCK] Already loading track data, skip this request");
                return;
            }
            isLoadingTrackData = true;
            LogUtil.d(TAG, "[LOCK_SET] isLoadingTrackData set to TRUE");
            
            if (selectedDevice == null) {
                Log.e(TAG, "[NO_DEVICE] No device selected, skip loadTrackData");
                isLoadingTrackData = false;
                LogUtil.d(TAG, "[LOCK_RELEASED] isLoadingTrackData set to FALSE (no device)");
                return;
            }
            
            Calendar today = Calendar.getInstance();
            boolean isToday = (selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                               selectedDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                               selectedDate.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH));
            
            String dateKey = com.RockiotTag.tag.util.TimeFormatter.formatDate(selectedDate.getTimeInMillis());
            LogUtil.d(TAG, "[DATE_CHECK] dateKey=" + dateKey + ", isToday=" + isToday);
            
            boolean shouldSyncFromServer = forceSync;
            
            if (!isToday && !forceSync) {
                Boolean hasSynced = syncedDates.get(dateKey);
                LogUtil.d(TAG, "[SYNC_CHECK] hasSynced=" + hasSynced + " for dateKey=" + dateKey);
                if (hasSynced != null && hasSynced) {
                    LogUtil.d(TAG, "[CACHE_HIT] Date already synced, using local cache");
                    shouldSyncFromServer = false;
                } else {
                    LogUtil.d(TAG, "[CACHE_MISS] First time viewing date, will sync from server");
                    shouldSyncFromServer = true;
                }
            } else if (isToday && !forceSync) {
                LogUtil.d(TAG, "[TODAY_MODE] Today's date, will check for new data from server");
                shouldSyncFromServer = false;
                checkServerForNewDataAsync();
            }

            if (shouldSyncFromServer) {
                LogUtil.d(TAG, "[SYNC_MODE] Need to sync from server, showing loading dialog");
                try {
                    clearTrack();
                    showLoadingDialog();
                    LogUtil.d(TAG, "[DIALOG_SHOWN] Loading dialog shown");
                } catch (Exception e) {
                    Log.e(TAG, "[EXCEPTION] clearTrack/showLoadingDialog: " + e.getMessage(), e);
                }
            } else {
                LogUtil.d(TAG, "[CACHE_MODE] Using cached data, showing loading progress");
                showLoading();
            }

            final Calendar startTime = (Calendar) startDate.clone();
            final Calendar endTime = (Calendar) endDate.clone();
            
            LogUtil.d(TAG, "[VIEWMODEL_CALL] Calling viewModel.loadTrackData for device=" + 
                  (selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId()) +
                  ", date=" + dateKey + ", shouldSync=" + shouldSyncFromServer);

            if (shouldSyncFromServer) {
                try {
                    LogUtil.d(TAG, "[DB_CLEANUP] Clearing old data for device");
                    int deleted = databaseHelper.deleteLocationRecordsByDevice(
                        selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId()
                    );
                    LogUtil.d(TAG, "[DB_CLEANUP] Deleted " + deleted + " old records");
                } catch (Exception e) {
                    Log.e(TAG, "[EXCEPTION] deleteLocationRecordsByDevice: " + e.getMessage(), e);
                }
            }

            String deviceNum = selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
            
            if (shouldSyncFromServer) {
                syncedDates.put(dateKey, true);
                lastSyncTimestamps.put(dateKey, System.currentTimeMillis());
                LogUtil.d(TAG, "[SYNC_RECORD] Recorded sync timestamp for " + dateKey);
            }
            
            viewModel.loadTrackData(deviceNum, selectedDate);
            LogUtil.d(TAG, "=== loadTrackData(forceSync=" + forceSync + ") END (waiting for ViewModel callback) ===");
        } catch (Exception e) {
            Log.e(TAG, "[EXCEPTION] loadTrackData: " + e.getMessage(), e);
            isLoadingTrackData = false;
            LogUtil.d(TAG, "[LOCK_RELEASED] isLoadingTrackData set to FALSE (exception)");
            hideLoading();
        }
    }

    private void renderTrack(List<LocationData> locationRecords) {
        TrackRenderHelper.renderTrack(new TrackRenderHelper.Host() {
            @Override public AppCompatActivity getActivity() { return TrackActivity.this; }
            @Override public boolean isFinishing() { return TrackActivity.this.isFinishing(); }
            @Override public boolean isDestroyed() {
                return android.os.Build.VERSION.SDK_INT >= 17 && TrackActivity.this.isDestroyed();
            }
            @Override public IMapAdapter getMapAdapter() { return mapAdapter; }
            @Override public TrackViewModel getViewModel() { return viewModel; }
            @Override public List<LocationData> getAllLocationRecords() { return allLocationRecords; }
            @Override public List<StayPoint> getStayPoints() { return stayPoints; }
            @Override public List<Object> getPositionMarkers() { return positionMarkers; }
            @Override public List<Object> getArrowMarkers() { return arrowMarkers; }
            @Override public Object getTrackPolyline() { return trackPolyline; }
            @Override public void setTrackPolyline(Object polyline) { trackPolyline = polyline; }
            @Override public int getCurrentAccuracyThreshold() { return currentAccuracyThreshold; }
            @Override public boolean isShowPolyline() { return showPolyline; }
            @Override public boolean isShowMarkers() { return showMarkers; }
            @Override public Calendar getSelectedDate() { return selectedDate; }
            @Override public float getCurrentZoomLevel() { return currentZoomLevel; }
            @Override public boolean hasSavedZoomLevel() { return hasSavedZoomLevel; }
            @Override public boolean isGoogleMapUserInteracted() { return googleMapUserInteracted; }
            @Override public TextView getTrackPointTime() { return trackPointTime; }
            @Override public void showLoading() { TrackActivity.this.showLoading(); }
            @Override public void hideLoading() { TrackActivity.this.hideLoading(); }
            @Override public void setLoadingTrackData(boolean loading) { isLoadingTrackData = loading; }
            @Override public void clearTrackUI() { TrackActivity.this.clearTrackUI(); }
            @Override public void updatePlaybackInfo(int count) { TrackActivity.this.updatePlaybackInfo(count); }
            @Override public void getAddressForLocation(double[] wgs84LatLng) {
                TrackActivity.this.getAddressForLocation(wgs84LatLng);
            }
            @Override public void correctTrackEndpointForToday() {
                TrackActivity.this.correctTrackEndpointForToday();
            }
        }, locationRecords);
    }

        /**
     * 计算总距离（公里）
     */
    private double calculateTotalDistance() {
        return com.RockiotTag.tag.util.TrackCalculator.calculateTotalDistance(allLocationRecords);
    }
    
    /**
     * 完整同步轨迹数据（首次加载或强制刷新时使用）
     */
    private void syncTrackDataFromServerAndReload(final String deviceNum, final long startTime, final long endTime) {
        if (syncHelper != null) {
            syncHelper.syncTrackDataFromServerAndReload(deviceNum, startTime, endTime);
        }
    }

    /**
     * 获取地址（合并高德和 Google 地图逻辑）
     * 接受 WGS84 坐标，内部根据引擎处理坐标转换
     * @param wgs84LatLng WGS84 坐标 [latitude, longitude]
     */
    private void getAddressForLocation(double[] wgs84LatLng) {
        TrackGeocodeHelper.getAddressForLocation(new TrackGeocodeHelper.Host() {
            @Override
            public AppCompatActivity getActivity() { return TrackActivity.this; }
            @Override
            public IMapAdapter getMapAdapter() { return mapAdapter; }
            @Override
            public TextView getTrackPointAddress() { return trackPointAddress; }
            @Override
            public void runOnUiThread(Runnable runnable) { TrackActivity.this.runOnUiThread(runnable); }
        }, wgs84LatLng);
    }

    private void updatePlaybackInfo(int count) {
        // 播放控件已移除，此方法保留为空以兼容现有调用
    }
    
    // filterAbnormalPoints 和 processStayPoints 已迁移到 TrackViewModel

    private void clearTrack() {
        if (mapCleanupHost != null) TrackMapCleanupHelper.clearTrack(mapCleanupHost);
    }
    
    // processStayPoints 已迁移到 TrackViewModel
    
    /**
     * 显示加载进度条
     */
    private void showLoading() {
        loadingHelper.showLoading();
    }

    /**
     * 显示加载提示对话框
     */
    private void showLoadingDialog() {
        loadingHelper.showLoadingDialog();
    }

    private void hideLoadingDialog() {
        loadingHelper.hideLoadingDialog();
    }

    private void hideLoading() {
        loadingHelper.hideLoading();
    }
    
    /**
     * 从服务器同步轨迹数据
     */
    private void syncTrackFromServer(String deviceNum, long startTime, long endTime) {
        if (syncHelper != null) {
            syncHelper.syncTrackFromServer(deviceNum, startTime, endTime);
        }
    }

    /**
     * 异步检查服务器是否有新数据（仅用于当天轨迹）
     */
    private void checkServerForNewDataAsync() {
        if (syncHelper != null) {
            syncHelper.checkServerForNewDataAsync();
        }
    }

    /**
     * 显示轨迹统计信息
     */
    private void showTrackStatistics() {
        String deviceNum = selectedDevice != null ? 
            (selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId()) : "";
        String deviceName = selectedDevice != null ? selectedDevice.getName() : "";
        long dateMillis = selectedDate != null ? selectedDate.getTimeInMillis() : System.currentTimeMillis();
        
        com.RockiotTag.tag.helper.TrackStatisticsHelper.showStatisticsDialog(
            this, stayPoints, null, deviceNum, deviceName, dateMillis, allLocationRecords);
    }
    
    private com.amap.api.maps.model.BitmapDescriptor createArrowMarker(double angle) {
        return MapMarkerHelper.createArrowMarker(angle);
    }
    
    private void checkAndCleanOldTrackData() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean hasCleanedTrackForNewVersion = prefs.getBoolean("has_cleaned_track_new_v2", false);
        
        if (!hasCleanedTrackForNewVersion) {
            com.RockiotTag.tag.util.DialogHelper.showConfirmDialog(
                this,
                getString(R.string.clean_old_track_data),
                getString(R.string.clean_old_track_message),
                getString(R.string.clean),
                getString(R.string.skip),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int deletedCount = databaseHelper.deleteAllLocationRecords();
                        android.content.SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("has_cleaned_track_new_v2", true);
                        editor.apply();
                        
                        if (selectedDevice != null && 
                            selectedDevice.getLatitude() != 0 && 
                            selectedDevice.getLongitude() != 0) {
                            
                            long deviceTimestamp = selectedDevice.getLastSeen();
                            LocationRecord firstRecord = new LocationRecord(
                                selectedDevice.getDeviceId(),
                                selectedDevice.getLatitude(),
                                selectedDevice.getLongitude(),
                                deviceTimestamp
                            );
                            databaseHelper.addLocationRecord(firstRecord);
                            LogUtil.d(TAG, "Added initial track point after cleanup: lat=" + 
                                  selectedDevice.getLatitude() + ", lng=" + selectedDevice.getLongitude() +
                                  ", timestamp=" + deviceTimestamp);
                            
                            ToastHelper.show(TrackActivity.this, 
                                getString(R.string.cleaned_track_with_current, deletedCount));
                        } else {
                            ToastHelper.show(TrackActivity.this, 
                                getString(R.string.cleaned_track_records, deletedCount));
                        }
                        
                        loadTrackData();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        android.content.SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("has_cleaned_track_new_v2", true);
                        editor.apply();
                    }
                }
            );
        }
    }


    @Override
    protected void onResume() {
        try {
            super.onResume();
            if (mapView != null && amapLifecycleStarted) {
                mapView.onResume();
            }
            if (mapAdapter != null) {
                mapAdapter.onResume();
            }

            // 启动自动刷新
            startAutoRefresh();

            // 每次恢复时重新从数据库读取设备信息，确保获取最新的位置和时间戳
            refreshSelectedDevice();

            // 检查地图提供商是否发生变化
            android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
            String mapProvider = MapAdapterFactory.getSavedProvider(this);
            boolean shouldBeGoogleMap = MapAdapterFactory.PROVIDER_GOOGLE.equals(mapProvider);
            boolean currentIsGoogle = mapAdapter != null && mapAdapter.getProvider().equals("google");

            LogUtil.d(TAG, "TrackActivity onResume - Current provider: " + (mapAdapter != null ? mapAdapter.getProvider() : "null") + ", Should be: " + mapProvider);

            // 如果地图提供商发生变化，需要重新启动Activity
            if (currentIsGoogle != shouldBeGoogleMap) {
                LogUtil.d(TAG, "Map provider changed from " + (currentIsGoogle ? "google" : "amap") +
                      " to " + (shouldBeGoogleMap ? "google" : "amap") + ", restarting TrackActivity...");
                stopPlayback();
                finish();
                startActivity(getIntent());
                overridePendingTransition(0, 0);
                return;
            }

            com.RockiotTag.tag.map.MapLayerController.applyLayers(mapProvider, mapView, googleMapFragment);
            ensureOverlaysAboveMap();
            if (shouldBeGoogleMap) {
                com.RockiotTag.tag.map.MapLayerController.showGoogleHideAmap(mapView, googleMapFragment);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume: " + e.getMessage(), e);
        }
    }
    
    private void refreshSelectedDevice() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String selectedDeviceId = prefs.getString("selected_device_id", "");
        if (!selectedDeviceId.isEmpty()) {
            TagDevice device = databaseHelper.getDevice(selectedDeviceId);
            if (device != null) {
                selectedDevice = device;
                LogUtil.d(TAG, "Refreshed device: lat=" + device.getLatitude() + 
                      ", lng=" + device.getLongitude() + 
                      ", lastSeen=" + device.getLastSeen());
            }
        }
    }

    @Override
    protected void onPause() {
        try {
            super.onPause();
            if (mapView != null && amapLifecycleStarted) {
                mapView.onPause();
            }
            if (mapAdapter != null) {
                mapAdapter.onPause();
            }

            // 停止自动刷新
            stopAutoRefresh();
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
            if (mapView != null && amapLifecycleStarted) {
                mapView.onSaveInstanceState(outState);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onSaveInstanceState: " + e.getMessage(), e);
        }
    }
    
    // ==================== Google 地图辅助方法 ====================
    // moveToNextPointOnGoogleMap 和 updatePlayedPolylineOnGoogleMap 已合并到统一方法

    /**
     * 初始化自动刷新
     */
    private void initAutoRefresh() {
        if (autoRefreshHelper != null) autoRefreshHelper.initAutoRefresh();
    }

    /**
     * 启动自动刷新
     */
    private void startAutoRefresh() {
        if (autoRefreshHelper != null) autoRefreshHelper.startAutoRefresh();
    }

    /**
     * 停止自动刷新
     */
    private void stopAutoRefresh() {
        if (autoRefreshHelper != null) autoRefreshHelper.stopAutoRefresh();
    }

    /**
     * 修正今天的轨迹终点为设备最新位置
     */
    private void correctTrackEndpointForToday() {
        if (trackEndpointHost != null) {
            TrackEndpointHelper.correctTrackEndpointForToday(trackEndpointHost);
        }
    }
    
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * 将浮层 UI 置于地图容器之上，避免 MapLayerController.bringToFront 遮挡 Tab 栏。
     */
    private void ensureOverlaysAboveMap() {
        ViewGroup root = findViewById(R.id.track_root);
        if (root == null) {
            return;
        }
        int[] overlayIds = {
            R.id.top_bar,
            R.id.loading_progress,
            R.id.toolbar_container,
            R.id.track_info_card,
            R.id.track_info_panel_indicator,
            R.id.bottom_navigation
        };
        for (int id : overlayIds) {
            View overlay = findViewById(id);
            if (overlay != null) {
                overlay.bringToFront();
            }
        }
        root.invalidate();
    }

    /**
     * 手动应用深色/浅色模式
     */
    private void applyDarkMode(boolean isDarkMode) {
        int bgColor = getResources().getColor(isDarkMode ? R.color.dark_background : R.color.background, null);
        int topBarColor = getResources().getColor(isDarkMode ? R.color.dark_top_bar_background : R.color.top_bar_background, null);
        int onSurfaceColor = getResources().getColor(isDarkMode ? R.color.dark_onSurface : R.color.onSurface, null);
        int textSecColor = getResources().getColor(isDarkMode ? R.color.dark_text_secondary : R.color.text_secondary, null);
        int cardColor = getResources().getColor(isDarkMode ? R.color.dark_card_background : R.color.card_background, null);
        
        // 根视图
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) rootView.setBackgroundColor(bgColor);
        
        // 顶部栏
        View topBar = findViewById(R.id.top_bar);
        if (topBar != null) topBar.setBackgroundColor(topBarColor);
        
        // 标题文字
        TextView titleText = findViewById(R.id.title_text);
        if (titleText != null) titleText.setTextColor(onSurfaceColor);
        
        // 轨迹页浮动按钮（统计 / 刷新）
        com.RockiotTag.tag.util.MapFloatingButtonHelper.applyTrackScreenButtons(this, isDarkMode);

        if (trackInfoPanelHelper != null) {
            trackInfoPanelHelper.applyTheme(isDarkMode);
        }
        
        // 轨迹信息卡片
        androidx.cardview.widget.CardView trackInfoCard = findViewById(R.id.track_info_card);
        if (trackInfoCard != null) {
            trackInfoCard.setCardBackgroundColor(cardColor);
            if (trackInfoCard instanceof ViewGroup) {
                updateChildViewsColor((ViewGroup) trackInfoCard, onSurfaceColor, textSecColor);
            }
        }
        
        // 底部导航栏
        View bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) bottomNav.setBackgroundColor(topBarColor);
        
        // 更新导航栏Tab文字和图标颜色
        int selectedColor = getResources().getColor(R.color.brand_primary, null);
        int unselectedColor = isDarkMode ? 
            getResources().getColor(R.color.dark_text_secondary, null) : 
            getResources().getColor(R.color.text_secondary, null);
        int[][] tabPairs = {
            {R.id.tab_home_icon, R.id.tab_home_text},
            {R.id.tab_list_icon, R.id.tab_list_text},
            {R.id.tab_track_icon, R.id.tab_track_text},
            {R.id.tab_profile_icon, R.id.tab_profile_text}
        };
        for (int i = 0; i < tabPairs.length; i++) {
            ImageView icon = findViewById(tabPairs[i][0]);
            TextView text = findViewById(tabPairs[i][1]);
            if (icon != null) icon.setColorFilter(unselectedColor);
            if (text != null) text.setTextColor(unselectedColor);
        }
        
        // 状态栏
        com.RockiotTag.tag.util.StatusBarHelper.setupStatusBar(this, isDarkMode);
        
        // 深色模式：通过适配器统一设置地图样式
        try {
            if (mapAdapter != null && mapAdapter.isMapReady()) {
                mapAdapter.setDarkMapStyle(isDarkMode);
            }
        } catch (Exception e) {
            Log.e("TrackActivity", "Error applying dark map style: " + e.getMessage());
        }
    }
    
    private void updateChildViewsColor(ViewGroup parent, int onSurfaceColor, int textSecColor) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                // 按钮文字保持原色（白色），只改普通文字
                if (!(child instanceof Button)) {
                    tv.setTextColor(onSurfaceColor);
                }
            } else if (child instanceof ImageView) {
                ((ImageView) child).setColorFilter(textSecColor);
            } else if (child instanceof ViewGroup) {
                updateChildViewsColor((ViewGroup) child, onSurfaceColor, textSecColor);
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void updateTabSelection(int tabIndex) {
        if (bottomNavHelper != null) {
            bottomNavHelper.updateTabSelection(tabIndex);
        }
    }
}
