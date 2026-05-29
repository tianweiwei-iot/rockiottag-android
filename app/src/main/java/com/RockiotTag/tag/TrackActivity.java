package com.RockiotTag.tag;

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
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import com.RockiotTag.tag.viewmodel.TrackViewModel;
import com.RockiotTag.tag.viewmodel.TrackViewModelFactory;
import com.RockiotTag.tag.model.LocationData;
import com.RockiotTag.tag.util.MapMarkerHelper;
import com.RockiotTag.tag.util.GoogleMapMarkerHelper;
import com.RockiotTag.tag.util.GeocodeHelper;
import com.RockiotTag.tag.util.SafeExecutor;

import java.lang.ref.WeakReference;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.GeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TrackActivity extends AppCompatActivity implements AMap.OnMarkerClickListener, OnMapReadyCallback {

    private static final String TAG = "TrackActivity";
    private MapView mapView;
    private AMap aMap;
    private SupportMapFragment googleMapFragment;
    private GoogleMap googleMap;
    private boolean isGoogleMapMode = false; // 标记当前是否使用 Google 地图
    private DatabaseHelper databaseHelper;
    // GeocodeSearch 已迁移到 GeocodeHelper
    private Button dateBtn;
    private Button startTimeBtn;
    private Button endTimeBtn;
    private Button resetTimeBtn;
    private Calendar selectedDate;
    private Calendar startDate;
    private Calendar endDate;
    private Device selectedDevice;
    
    // 高德地图相关变量
    private List<Marker> positionMarkers = new ArrayList<>();
    private List<Marker> arrowMarkers = new ArrayList<>();
    private Polyline trackPolyline;
    
    // Google 地图相关变量
    private List<com.google.android.gms.maps.model.Marker> googlePositionMarkers = new ArrayList<>();
    private List<com.google.android.gms.maps.model.Marker> googleArrowMarkers = new ArrayList<>();
    private com.google.android.gms.maps.model.Polyline googleTrackPolyline;
    
    private TextView trackPointTime;
    private TextView trackPointAddress;
    private TextView totalDistanceText;
    
    private ImageButton playBtn;
    private SeekBar playbackSeekbar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView speedBtn;
    
    private List<LocationData> allLocationRecords = new CopyOnWriteArrayList<>();
    private List<StayPoint> stayPoints = new CopyOnWriteArrayList<>();
    private static final double STAY_DISTANCE_THRESHOLD = 30.0; // 30 米，平衡 GPS 精度和停留点识别
    private static final double MAX_SPEED_KMH = 200.0;
    
    // MVVM - 播放状态由 ViewModel 管理，这里只保留 UI 控制需要的引用
    // isPlaying, currentPlayIndex, playSpeed 都从 viewModel 获取
    // 修复：使用静态 Handler 防止内存泄漏
    private static class PlaybackHandler extends Handler {
        private final WeakReference<TrackActivity> activityRef;
        
        PlaybackHandler(TrackActivity activity) {
            activityRef = new WeakReference<>(activity);
        }
        
        @Override
        public void handleMessage(Message msg) {
            TrackActivity activity = activityRef.get();
            if (activity != null) {
                // MVVM - 从 ViewModel 获取播放状态
                Boolean playing = activity.viewModel.getIsPlaying().getValue();
                if (playing != null && playing) {
                    try {
                        if (activity.isGoogleMapMode) {
                            activity.moveToNextPointOnGoogleMap();
                        } else {
                            activity.moveToNextPoint();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in playback handler: " + e.getMessage(), e);
                        activity.viewModel.pausePlayback();
                    }
                }
            }
        }
    }
    private PlaybackHandler playHandler;
    
    // 高德地图播放相关
    private Marker playMarker = null;
    private Polyline playedPolyline = null;
    private List<LatLng> playedPoints = new ArrayList<>();
    private ValueAnimator moveAnimator = null;
    private LatLng currentPlayPosition = null;
    
    // Google 地图播放相关
    private com.google.android.gms.maps.model.Marker googlePlayMarker = null;
    private com.google.android.gms.maps.model.Polyline googlePlayedPolyline = null;
    private List<com.google.android.gms.maps.model.LatLng> googlePlayedPoints = new ArrayList<>();
    
    private ImageButton toggleMarkersBtn;
    private ImageButton togglePolylineBtn;
    private ImageButton toggleSatelliteBtn;
    private ImageButton statisticsBtn; // 统计按钮
    private boolean showMarkers = true;
    private boolean showPolyline = true;
    private boolean isSatelliteMode = false;
    
    // 精度调节相关变量
    private SeekBar accuracySeekBar;
    private TextView accuracyValueText;
    private int currentAccuracyThreshold = 200; // 默认精度阈值（米），高精度模式
    
    // 加载进度条
    private ProgressBar loadingProgress;
    
    // 加载提示对话框
    private android.app.AlertDialog loadingDialog;
    
    // 自动刷新相关变量
    private Handler autoRefreshHandler;
    private Runnable autoRefreshRunnable;
    private static final long AUTO_REFRESH_INTERVAL = 3 * 60 * 1000; // 3分钟自动刷新（仅当天）
    
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

            // 隐藏系统 ActionBar（使用自定义标题栏）
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }

            databaseHelper = new DatabaseHelper(this);
            selectedDate = Calendar.getInstance();

            String selectedDeviceId = prefs.getString("selected_device_id", "");
            if (!selectedDeviceId.isEmpty()) {
                selectedDevice = databaseHelper.getDevice(selectedDeviceId);
                if (selectedDevice == null) {
                    Log.e(TAG, "Selected device not found in database: " + selectedDeviceId);
                } else {
                    Log.d(TAG, "Selected device loaded: " + selectedDevice.getDeviceId());
                }
            } else {
                Log.w(TAG, "No device selected in preferences");
            }
            
            // 检查当前选择的地图提供商
            String mapProvider = prefs.getString("map_provider", "amap");
            isGoogleMapMode = "google".equals(mapProvider);
            
            // 先初始化MapView和GoogleMapFragment（无论哪种模式都需要）
            mapView = findViewById(R.id.mapView);
            mapView.onCreate(savedInstanceState);
            
            googleMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.googleMapFragment);
            
            if (isGoogleMapMode) {
                // 隐藏高德地图，显示Google地图
                mapView.setVisibility(View.GONE);
                
                if (googleMapFragment != null) {
                    googleMapFragment.getView().setVisibility(View.VISIBLE);
                    googleMapFragment.getView().bringToFront(); // 确保Google地图在最前面
                    googleMapFragment.getMapAsync(this);
                }
            } else {
                // 显示高德地图，隐藏Google地图
                mapView.setVisibility(View.VISIBLE);
                mapView.bringToFront(); // 确保高德地图在最前面
                
                if (googleMapFragment != null && googleMapFragment.getView() != null) {
                    googleMapFragment.getView().setVisibility(View.GONE);
                }
                
                // 初始化高德地图
                initMap();
            }

            ImageButton backBtn = findViewById(R.id.back_btn);
            backBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });

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

            startTimeBtn = findViewById(R.id.start_time_btn);
            startTimeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showStartTimePicker();
                }
            });

            endTimeBtn = findViewById(R.id.end_time_btn);
            endTimeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEndTimePicker();
                }
            });

            resetTimeBtn = findViewById(R.id.reset_time_btn);
            resetTimeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    resetTimeRange();
                }
            });

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
            totalDistanceText = findViewById(R.id.total_distance_text);
            
            if (trackPointTime == null) {
                Log.e(TAG, "trackPointTime is null after findViewById!");
            }
            if (trackPointAddress == null) {
                Log.e(TAG, "trackPointAddress is null after findViewById!");
            }
            if (totalDistanceText == null) {
                Log.e(TAG, "totalDistanceText is null after findViewById!");
            }
            
            // 初始化 Handler
            playHandler = new PlaybackHandler(this);

            initPlaybackControls();
            
            initToolbar();
            
            // 初始化加载进度条
            loadingProgress = findViewById(R.id.loading_progress);
            
            // 初始化自动刷新
            initAutoRefresh();
            
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

            checkAndCleanOldTrackData();
            
            // 关键修复：延迟加载数据，确保地图完全初始化后再加载
            // 高德地图模式：地图已同步初始化，可以直接加载
            // Google 地图模式：等待 onMapReady 回调后再加载
            if (!isGoogleMapMode) {
                // 高德地图模式 - 地图已初始化，可以加载数据
                if (aMap != null) {
                    Log.d(TAG, "AMap ready, loading track data immediately");
                    // 关键修复：增加安全检查，确保有选中的设备
                    if (selectedDevice != null) {
                        loadTrackData();
                    } else {
                        Log.w(TAG, "No device selected, skip initial loadTrackData");
                        // 静默处理，不显示Toast
                    }
                } else {
                    Log.e(TAG, "AMap is null after initMap, retrying...");
                    // 如果地图仍未初始化，延迟重试
                    new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (aMap != null && !isFinishing() && !isDestroyed()) {
                            Log.d(TAG, "Retrying loadTrackData after delay");
                            if (selectedDevice != null) {
                                loadTrackData();
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
            
            // 2. 移除所有 Handler 消息
            if (playHandler != null) {
                playHandler.removeCallbacksAndMessages(null);
                playHandler = null;
            }
            
            // 3. 停止并清理动画
            if (moveAnimator != null) {
                moveAnimator.cancel();
                moveAnimator.removeAllListeners();
                moveAnimator = null;
            }
            
            // 4. 清理地图资源（根据当前模式）
            if (isGoogleMapMode) {
                for (com.google.android.gms.maps.model.Marker marker : googlePositionMarkers) {
                    marker.remove();
                }
                googlePositionMarkers.clear();
                
                for (com.google.android.gms.maps.model.Marker marker : googleArrowMarkers) {
                    marker.remove();
                }
                googleArrowMarkers.clear();
                
                if (googlePlayMarker != null) googlePlayMarker.remove();
                if (googlePlayedPolyline != null) googlePlayedPolyline.remove();
                if (googleTrackPolyline != null) googleTrackPolyline.remove();
                
                googlePlayedPoints.clear();
            } else {
                for (Marker marker : positionMarkers) {
                    marker.remove();
                }
                positionMarkers.clear();
                
                for (Marker marker : arrowMarkers) {
                    marker.remove();
                }
                arrowMarkers.clear();
                
                if (playMarker != null) playMarker.remove();
                if (playedPolyline != null) playedPolyline.remove();
                if (trackPolyline != null) trackPolyline.remove();
                
                playedPoints.clear();
                currentPlayPosition = null;
            }
            
            // 5. 清理数据集合
            allLocationRecords.clear();
            stayPoints.clear();
            
            // 6. 清理 MapView
            if (mapView != null) {
                mapView.onDestroy();
            }
            
            // 7. 关闭数据库
            if (databaseHelper != null) {
                databaseHelper.close();
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
        
        // 移除 Handler 消息
        if (playHandler != null) {
            playHandler.removeCallbacksAndMessages(null);
        }
    }

    private void initPlaybackControls() {
        playBtn = findViewById(R.id.play_btn);
        playbackSeekbar = findViewById(R.id.playback_seekbar);
        currentTimeText = findViewById(R.id.current_time_text);
        totalTimeText = findViewById(R.id.total_time_text);
        speedBtn = findViewById(R.id.speed_btn);

        // 关键修复：添加空指针检查
        if (playBtn != null) {
            playBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isPlaying) {
                        pausePlayback();
                    } else {
                        startPlayback();
                    }
                }
            });
        } else {
            Log.e(TAG, "playBtn is null after findViewById!");
        }

        if (playbackSeekbar != null) {
            playbackSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && !allLocationRecords.isEmpty()) {
                        // MVVM - 通过 ViewModel 跳转播放位置
                        viewModel.seekTo(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // 用户拖动时暂停播放
                    if (viewModel.getIsPlaying().getValue() != null && viewModel.getIsPlaying().getValue()) {
                        viewModel.pausePlayback();
                    }
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // 拖动结束后更新UI位置
                    updatePlayPosition();
                }
            });
        } else {
            Log.e(TAG, "playbackSeekbar is null after findViewById!");
        }

        if (speedBtn != null) {
            speedBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleSpeed();
                }
            });
        } else {
            Log.e(TAG, "speedBtn is null after findViewById!");
        }
    }

    private void initToolbar() {
        toggleMarkersBtn = findViewById(R.id.toggle_markers_btn);
        togglePolylineBtn = findViewById(R.id.toggle_polyline_btn);
        toggleSatelliteBtn = findViewById(R.id.toggle_satellite_btn);
        statisticsBtn = findViewById(R.id.statistics_btn);
        ImageButton refreshBtn = findViewById(R.id.refresh_btn);
        
        // 初始化精度调节控件
        accuracySeekBar = findViewById(R.id.accuracy_seekbar);
        accuracyValueText = findViewById(R.id.accuracy_value_text);

        // 关键修复：添加空指针检查
        if (toggleMarkersBtn != null) {
            toggleMarkersBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleMarkers();
                }
            });
        } else {
            Log.e(TAG, "toggleMarkersBtn is null after findViewById!");
        }

        if (togglePolylineBtn != null) {
            togglePolylineBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    togglePolyline();
                }
            });
        } else {
            Log.e(TAG, "togglePolylineBtn is null after findViewById!");
        }

        if (toggleSatelliteBtn != null) {
            toggleSatelliteBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleSatellite();
                }
            });
        } else {
            Log.e(TAG, "toggleSatelliteBtn is null after findViewById!");
        }
        
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
                    Log.d(TAG, "Refresh button clicked, forcing sync from server");
                    loadTrackData(true);
                }
            });
        } else {
            Log.e(TAG, "refreshBtn is null after findViewById!");
        }
        
        // 初始化精度调节SeekBar
        if (accuracySeekBar != null) {
            // SeekBar范围：20米 - 200米
            // 左侧（progress小）= 高精度 = 200米阈值 → 合并更多点，显示更少的轨迹点
            // 右侧（progress大）= 低精度 = 20米阈值 → 保留更多点，显示更多的轨迹点
            // 默认居中：110米（(200+20)/2）
            accuracySeekBar.setMax(180); // 最大值180 (200-20)
            accuracySeekBar.setProgress(90); // 初始值对应110米（居中）
            currentAccuracyThreshold = 110; // 同步更新当前阈值变量
            
            accuracySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        // 计算实际阈值：progress=0 → 200米，progress=180 → 20米
                        currentAccuracyThreshold = 200 - progress;
                        Log.d(TAG, "=== ACCURACY SWITCH === 阈值: " + currentAccuracyThreshold + "m");
                        // 重新加载轨迹数据以应用新的精度阈值
                        if (!allLocationRecords.isEmpty()) {
                            reloadTrackWithNewAccuracy();
                        }
                    }
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // 不需要处理
                }
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // 不需要处理
                }
            });
            
            // 更新初始显示
            if (accuracyValueText != null) {
                accuracyValueText.setVisibility(View.GONE);
            }
        } else {
            Log.e(TAG, "accuracySeekBar is null after findViewById!");
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
        
        Log.d(TAG, "=== ACCURACY SWITCH === 阈值: " + currentAccuracyThreshold + "m | 生成停留点数量: " + newStayPoints.size());
        
        synchronized (stayPoints) {
            stayPoints.clear();
            stayPoints.addAll(newStayPoints);
        }
        
        // 【诊断日志】确认内存中的数据已更新
        Log.e(TAG, "【诊断】内存中 stayPoints 数量已更新为: " + stayPoints.size() + "，即将开始渲染");
        
        // 重新渲染轨迹（此时 renderTrack 内部会直接读取更新后的 stayPoints 成员变量）
        renderTrack(allLocationRecords);
    }
    
    /**
     * MVVM - 设置 ViewModel 观察者
     */
    private void setupViewModelObservers() {
        // 观察位置记录
        viewModel.getLocationRecords().observe(this, records -> {
            if (records != null) {
                // 关键修复：使用临时列表避免并发修改异常
                List<LocationData> newRecords = new ArrayList<>();
                for (LocationRecord record : records) {
                    LocationData data = new LocationData();
                    data.setId(record.getId());
                    data.setDeviceId(record.getDeviceId());
                    data.setLatitude(record.getLatitude());
                    data.setLongitude(record.getLongitude());
                    data.setTimestamp(record.getTimestamp());
                    newRecords.add(data);
                }
                
                // 在主线程中安全地替换列表
                synchronized (allLocationRecords) {
                    allLocationRecords.clear();
                    allLocationRecords.addAll(newRecords);
                }
                
                // 关键修复：更新播放进度条最大值前检查是否为 null
                if (playbackSeekbar != null && !newRecords.isEmpty()) {
                    playbackSeekbar.setMax(newRecords.size() - 1);
                } else if (playbackSeekbar == null) {
                    Log.w(TAG, "playbackSeekbar is null in location records observer");
                }
            }
        });
        
        // 观察停留点 - 这是触发渲染的关键点
        viewModel.getStayPoints().observe(this, stays -> {
            if (stays != null) {
                // 关键修复：使用当前选定的精度阈值重新生成停留点，而不是直接使用 ViewModel 的默认结果
                List<StayPoint> finalStayPoints;
                if (currentAccuracyThreshold != 200) {
                    // 如果用户调整了精度，则按用户选择的阈值重新计算
                    finalStayPoints = viewModel.generateStayPointsFromRecordsWithAccuracy(
                        new ArrayList<>(allLocationRecords), currentAccuracyThreshold);
                    Log.d(TAG, "Observer: Re-generated stay points with threshold " + currentAccuracyThreshold + "m, count: " + finalStayPoints.size());
                } else {
                    // 否则使用默认（高精度）结果
                    finalStayPoints = new ArrayList<>(stays);
                }
                
                synchronized (stayPoints) {
                    stayPoints.clear();
                    stayPoints.addAll(finalStayPoints);
                }
                
                // 停留点更新后，触发轨迹渲染（此时 locationRecords 和 stayPoints 都已就绪）
                synchronized (allLocationRecords) {
                    if (!allLocationRecords.isEmpty()) {
                        // 关键修复：渲染前检查 Activity 状态
                        if (!isFinishing() && !isDestroyed()) {
                            // 创建副本传递给renderTrack，避免并发修改
                            List<LocationData> recordsCopy = new ArrayList<>(allLocationRecords);
                            renderTrack(recordsCopy);
                        } else {
                            Log.d(TAG, "Activity is finishing/destroyed, skip renderTrack in observer");
                            // 确保在Activity销毁时释放加载锁
                            isLoadingTrackData = false;
                        }
                    } else {
                        // 无数据时，静默清除UI，不显示Toast
                        Log.d(TAG, "No location records for selected date, clearing old track UI");
                        if (!isFinishing() && !isDestroyed()) {
                            clearTrackUI();
                            updatePlaybackInfo(0);
                            hideLoading();
                        }
                        // 释放加载锁
                        isLoadingTrackData = false;
                    }
                }
            } else {
                // 如果stays为null，也要释放加载锁
                isLoadingTrackData = false;
            }
        });
        
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (loadingProgress != null) {
                loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
        });
        
        // 观察统计信息
        viewModel.getStatistics().observe(this, stats -> {
            if (stats != null && totalDistanceText != null) {
                double distanceKm = stats.totalDistanceKm;
                totalDistanceText.setText(String.format("%.2f km", distanceKm));
            } else if (totalDistanceText == null) {
                Log.w(TAG, "totalDistanceText is null in statistics observer");
            }
        });
        
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                // 静默记录错误日志，不显示Toast
                Log.e(TAG, "Error from ViewModel: " + error);
            }
        });
        
        // 监听服务器同步信号，当本地无数据时自动从服务器同步
        viewModel.getNeedsServerSync().observe(this, params -> {
            if (params != null && selectedDevice != null) {
                Log.d(TAG, "Received server sync signal for device: " + params.deviceNum);
                // 释放加载锁，让同步方法可以执行
                isLoadingTrackData = false;
                // 显示加载对话框
                showLoadingDialog();
                String deviceNum = params.deviceNum;
                // 如果deviceNum是deviceId，需要获取deviceNum
                if (selectedDevice.getDeviceNum() != null) {
                    deviceNum = selectedDevice.getDeviceNum();
                }
                syncTrackDataFromServerAndReload(deviceNum, params.startTime, params.endTime);
            }
        });
        
        // 监听从服务器同步状态，静默处理
        viewModel.getIsSyncingFromServer().observe(this, isSyncing -> {
            if (isSyncing != null && isSyncing) {
                Log.d(TAG, "Syncing from server (silent)...");
                // 不显示Toast，加载中动画已提示用户
            }
        });
        
        viewModel.getIsPlaying().observe(this, playing -> {
            isPlaying = playing;
            if (playBtn != null) {
                playBtn.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            } else {
                Log.w(TAG, "playBtn is null in isPlaying observer");
            }
        });
        
        viewModel.getCurrentPlayIndex().observe(this, index -> {
            currentPlayIndex = index;
            if (playbackSeekbar != null) {
                playbackSeekbar.setProgress(index);
            } else {
                Log.w(TAG, "playbackSeekbar is null in currentPlayIndex observer");
            }
        });
        
        viewModel.getPlaySpeed().observe(this, speed -> {
            playSpeed = speed;
            if (speedBtn != null) {
                speedBtn.setText(speed + "x");
            } else {
                Log.w(TAG, "speedBtn is null in playSpeed observer");
            }
        });
    }

    private void toggleMarkers() {
        try {
            showMarkers = !showMarkers;
            for (Marker marker : positionMarkers) {
                marker.setVisible(showMarkers);
            }
            for (Marker marker : arrowMarkers) {
                marker.setVisible(showMarkers);
            }
            
            // 切换标记显示，不显示Toast
            if (showMarkers) {
                toggleMarkersBtn.setAlpha(1.0f);
            } else {
                toggleMarkersBtn.setAlpha(0.5f);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in toggleMarkers: " + e.getMessage(), e);
        }
    }

    private void togglePolyline() {
        try {
            showPolyline = !showPolyline;
            
            if (isGoogleMapMode) {
                // Google 地图模式
                if (googleTrackPolyline != null) {
                    googleTrackPolyline.setVisible(showPolyline);
                }
                // 同时控制箭头标记
                for (com.google.android.gms.maps.model.Marker marker : googleArrowMarkers) {
                    marker.setVisible(showPolyline);
                }
            } else {
                // 高德地图模式
                if (trackPolyline != null) {
                    trackPolyline.setVisible(showPolyline);
                }
                // 同时控制箭头标记
                for (Marker marker : arrowMarkers) {
                    marker.setVisible(showPolyline);
                }
            }
            
            // 切换连线显示，不显示Toast
            if (showPolyline) {
                togglePolylineBtn.setAlpha(1.0f);
            } else {
                togglePolylineBtn.setAlpha(0.5f);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in togglePolyline: " + e.getMessage(), e);
        }
    }

    private void toggleSatellite() {
        try {
            isSatelliteMode = !isSatelliteMode;
            if (aMap != null) {
                aMap.setMapType(isSatelliteMode ? AMap.MAP_TYPE_SATELLITE : AMap.MAP_TYPE_NORMAL);
            }
                
            // 切换卫星模式，不显示Toast
            if (isSatelliteMode) {
                toggleSatelliteBtn.setAlpha(1.0f);
            } else {
                toggleSatelliteBtn.setAlpha(0.5f);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in toggleSatellite: " + e.getMessage(), e);
        }
    }
        
    /**
     * 只清除UI元素，保留数据用于重新渲染
     */
    private void clearTrackUI() {
        
        if (isGoogleMapMode) {
            // 清理 Google 地图 Marker
            for (com.google.android.gms.maps.model.Marker marker : googlePositionMarkers) {
                marker.remove();
            }
            googlePositionMarkers.clear();
            
            for (com.google.android.gms.maps.model.Marker marker : googleArrowMarkers) {
                marker.remove();
            }
            googleArrowMarkers.clear();

            // 清理 Google 地图轨迹线
            if (googleTrackPolyline != null) {
                googleTrackPolyline.remove();
                googleTrackPolyline = null;
            }
            
            // 清理 Google 地图播放相关对象
            if (googlePlayedPolyline != null) {
                googlePlayedPolyline.remove();
                googlePlayedPolyline = null;
            }
            if (googlePlayMarker != null) {
                googlePlayMarker.remove();
                googlePlayMarker = null;
            }
            googlePlayedPoints.clear();
        } else {
            // 清理高德地图 Marker
            for (Marker marker : positionMarkers) {
                marker.remove();
            }
            positionMarkers.clear();
            
            for (Marker marker : arrowMarkers) {
                marker.remove();
            }
            arrowMarkers.clear();

            // 清理高德地图轨迹线
            if (trackPolyline != null) {
                trackPolyline.remove();
                trackPolyline = null;
            }
            
            // 清理高德地图播放相关对象
            if (playedPolyline != null) {
                playedPolyline.remove();
                playedPolyline = null;
            }
            if (playMarker != null) {
                playMarker.remove();
                playMarker = null;
            }
            playedPoints.clear();
            currentPlayPosition = null;
        }
        
        // 停止所有动画
        if (moveAnimator != null && moveAnimator.isRunning()) {
            moveAnimator.cancel();
            moveAnimator = null;
        }
        
        Log.d(TAG, "Track UI cleared, data preserved for re-rendering");
    }

    private void startPlayback() {
        try {
            if (stayPoints.isEmpty()) {
                // 静默处理，不显示Toast
                return;
            }

            // MVVM - 使用 ViewModel 管理播放状态
            viewModel.startPlayback();
            
            // 初始化地图播放标记
            if (isGoogleMapMode) {
                initGoogleMapPlayback();
            } else {
                initAMapPlayback();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in startPlayback: " + e.getMessage(), e);
            // 静默处理，不显示Toast
            viewModel.pausePlayback();
        }
    }
    
    /**
     * 初始化高德地图播放标记
     */
    private void initAMapPlayback() {
        if (playMarker != null) {
            playMarker.remove();
            playMarker = null;
        }
        playedPoints.clear();
        currentPlayPosition = null;
        
        StayPoint stayPoint = stayPoints.get(0);
        LatLng startPos = CoordinateUtils.wgs84ToGcj02(stayPoint.getLatitude(), stayPoint.getLongitude());
        currentPlayPosition = startPos;
        
        MarkerOptions markerOptions = new MarkerOptions()
            .position(startPos)
            .title(getString(R.string.play_position))
            .icon(createPlayMarker())
            .anchor(0.5f, 0.5f);
        playMarker = aMap.addMarker(markerOptions);
        playedPoints.add(startPos);
        
        moveToNextPoint();
    }
    
    /**
     * 初始化Google地图播放标记
     */
    private void initGoogleMapPlayback() {
        if (googlePlayMarker != null) {
            googlePlayMarker.remove();
            googlePlayMarker = null;
        }
        googlePlayedPoints.clear();
        
        StayPoint stayPoint = stayPoints.get(0);
        com.google.android.gms.maps.model.LatLng startPos = 
            new com.google.android.gms.maps.model.LatLng(stayPoint.getLatitude(), stayPoint.getLongitude());
        
        com.google.android.gms.maps.model.MarkerOptions markerOptions = 
            new com.google.android.gms.maps.model.MarkerOptions()
                .position(startPos)
                .title(getString(R.string.play_position))
                .icon(createPlayMarkerForGoogleMap())
                .anchor(0.5f, 0.5f);
        googlePlayMarker = googleMap.addMarker(markerOptions);
        googlePlayedPoints.add(startPos);
        
        moveToNextPointOnGoogleMap();
    }

    private void pausePlayback() {
        try {
            // MVVM - 使用 ViewModel 暂停播放
            viewModel.pausePlayback();
            
            if (moveAnimator != null && moveAnimator.isRunning()) {
                moveAnimator.cancel();
            }
            playHandler.removeCallbacksAndMessages(null);
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
            
            // 移除所有 Handler 消息
            if (playHandler != null) {
                playHandler.removeCallbacksAndMessages(null);
            }
            
            if (isGoogleMapMode) {
                // 清理 Google 地图播放相关对象
                if (googlePlayMarker != null) {
                    googlePlayMarker.remove();
                    googlePlayMarker = null;
                }
                if (googlePlayedPolyline != null) {
                    googlePlayedPolyline.remove();
                    googlePlayedPolyline = null;
                }
                googlePlayedPoints.clear();
            } else {
                // 清理高德地图播放相关对象
                if (playMarker != null) {
                    playMarker.remove();
                    playMarker = null;
                }
                if (playedPolyline != null) {
                    playedPolyline.remove();
                    playedPolyline = null;
                }
                playedPoints.clear();
                currentPlayPosition = null;
            }
            
            playbackSeekbar.setProgress(0);
            currentTimeText.setText("00:00:00");
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
            
            LatLng fromPos = CoordinateUtils.wgs84ToGcj02(fromStayPoint.getLatitude(), fromStayPoint.getLongitude());
            LatLng toPos = CoordinateUtils.wgs84ToGcj02(toStayPoint.getLatitude(), toStayPoint.getLongitude());
            
            if (currentPlayPosition != null) {
                fromPos = currentPlayPosition;
            }
            
            final LatLng finalFromPos = fromPos;
            final LatLng finalToPos = toPos;
            
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
                    
                    double lat = finalFromPos.latitude + (finalToPos.latitude - finalFromPos.latitude) * fraction;
                    double lng = finalFromPos.longitude + (finalToPos.longitude - finalFromPos.longitude) * fraction;
                    LatLng newPos = new LatLng(lat, lng);
                    
                    currentPlayPosition = newPos;
                    
                    if (playMarker != null) {
                        playMarker.setPosition(newPos);
                    }
                    
                    // 只在关键点更新相机
                    Integer currentIndex = viewModel.getCurrentPlayIndex().getValue();
                    int totalPoints = viewModel.getTotalStayPoints();
                    if (currentIndex != null && (currentIndex % 5 == 0 || currentIndex >= totalPoints - 2)) {
                        aMap.animateCamera(com.amap.api.maps.CameraUpdateFactory.newLatLng(newPos));
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
                        currentPlayPosition = finalToPos;
                        
                        playedPoints.add(finalToPos);
                        updatePlayedPolyline();
                        
                        // MVVM - 从 ViewModel 获取时间信息
                        currentTimeText.setText(viewModel.getCurrentPlayTimeString());
                        trackPointTime.setText(viewModel.getCurrentPlayFullTimeString());
                        
                        // MVVM - 根据 ViewModel 判断是否需要更新地址
                        if (viewModel.shouldUpdateAddress()) {
                            getAddressForLocation(finalToPos);
                        }
                        
                        // 继续播放
                        playHandler.sendEmptyMessageDelayed(0, 50);
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

    private void updatePlayedPolyline() {
        // 优化 4: 使用 addPoint 而不是重新创建整个 Polyline
        if (playedPoints.size() > 1) {
            if (playedPolyline == null) {
                // 第一次创建
                PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(playedPoints)
                    .color(0xFFFF5722)
                    .width(12f);
                playedPolyline = aMap.addPolyline(polylineOptions);
            } else {
                // 后续只添加新点（高德地图 SDK 支持动态更新）
                playedPolyline.setPoints(playedPoints);
            }
        }
    }

    private void updatePlayPosition() {
        try {
            // MVVM - 从 ViewModel 获取总停留点数
            int totalPoints = viewModel.getTotalStayPoints();
            if (totalPoints == 0) return;

            if (isGoogleMapMode) {
                // Google 地图模式
                googlePlayedPoints.clear();
                for (int i = 0; i <= currentPlayIndex && i < totalPoints; i++) {
                    double[] coords = viewModel.getStayPointCoordinates(i);
                    if (coords != null) {
                        // Google 地图使用 WGS84 原始坐标，不需要转换
                        com.google.android.gms.maps.model.LatLng latLng = 
                            new com.google.android.gms.maps.model.LatLng(coords[0], coords[1]);
                        googlePlayedPoints.add(latLng);
                    }
                }

                updatePlayedPolylineOnGoogleMap();

                if (!googlePlayedPoints.isEmpty()) {
                    com.google.android.gms.maps.model.LatLng latLng = googlePlayedPoints.get(googlePlayedPoints.size() - 1);
                    
                    if (googlePlayMarker != null) {
                        googlePlayMarker.setPosition(latLng);
                    } else {
                        com.google.android.gms.maps.model.MarkerOptions markerOptions = 
                            new com.google.android.gms.maps.model.MarkerOptions()
                                .position(latLng)
                                .title(getString(R.string.play_position))
                                .icon(createPlayMarkerForGoogleMap())
                                .anchor(0.5f, 0.5f);
                        googlePlayMarker = googleMap.addMarker(markerOptions);
                    }
                    
                    // 完全禁用谷歌地图播放时的相机移动
                    Log.d(TAG, "Google Map - Camera move during playback COMPLETELY DISABLED");
                    
                    // 获取地址
                    if (currentPlayIndex % 10 == 0) {
                        getAddressForLocationOnGoogleMap(latLng);
                    }
                }
            } else {
                // 高德地图模式
                playedPoints.clear();
                for (int i = 0; i <= currentPlayIndex && i < totalPoints; i++) {
                    double[] coords = viewModel.getStayPointCoordinates(i);
                    if (coords != null) {
                        LatLng latLng = CoordinateUtils.wgs84ToGcj02(coords[0], coords[1]);
                        playedPoints.add(latLng);
                    }
                }

                updatePlayedPolyline();

                if (!playedPoints.isEmpty()) {
                    LatLng latLng = playedPoints.get(playedPoints.size() - 1);
                    currentPlayPosition = latLng;
                    
                    if (playMarker != null) {
                        playMarker.setPosition(latLng);
                    } else {
                        MarkerOptions markerOptions = new MarkerOptions()
                            .position(latLng)
                            .title(getString(R.string.play_position))
                            .icon(createPlayMarker())
                            .anchor(0.5f, 0.5f);
                        playMarker = aMap.addMarker(markerOptions);
                    }
                    
                    aMap.animateCamera(com.amap.api.maps.CameraUpdateFactory.newLatLng(latLng));
                    
                    // 获取地址
                    if (currentPlayIndex % 10 == 0 || isStayPointAt(currentPlayIndex)) {
                        getAddressForLocation(latLng);
                    }
                }
            }

            // MVVM - 使用 ViewModel 获取时间信息
            currentTimeText.setText(viewModel.getCurrentPlayTimeString());
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
            Log.d(TAG, "Speed changed to: " + newSpeed + "x");
        } catch (Exception e) {
            Log.e(TAG, "Error in cycleSpeed: " + e.getMessage(), e);
        }
    }

    private com.amap.api.maps.model.BitmapDescriptor createPlayMarker() {
        // 获取设备的 tag
        String deviceTag = "";
        if (selectedDevice != null && selectedDevice.getTag() != null && !selectedDevice.getTag().isEmpty()) {
            deviceTag = selectedDevice.getTag();
        }
        
        // 根据 tag 获取对应的 emoji 图标
        String emoji = MapMarkerHelper.getTagEmoji(deviceTag);
        
        // 使用 emoji 绘制播放图标
        return MapMarkerHelper.createEmojiMarker(emoji);
    }
    
    /**
     * 为 Google 地图创建播放标记图标
     */
    private com.google.android.gms.maps.model.BitmapDescriptor createPlayMarkerForGoogleMap() {
        // 获取设备的 tag
        String deviceTag = "";
        if (selectedDevice != null && selectedDevice.getTag() != null && !selectedDevice.getTag().isEmpty()) {
            deviceTag = selectedDevice.getTag();
        }
        
        // 根据 tag 获取对应的 emoji 图标
        String emoji = MapMarkerHelper.getTagEmoji(deviceTag);
        
        // 使用 emoji 绘制播放图标
        return GoogleMapMarkerHelper.createEmojiMarker(emoji);
    }

    private void initMap() {
        try {
            if (!isGoogleMapMode) {
                // 高德地图模式
                if (aMap == null) {
                    aMap = mapView.getMap();
                    if (aMap != null) {
                        aMap.getUiSettings().setScaleControlsEnabled(true);
                        aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.zoomTo(17));
                        aMap.setOnMarkerClickListener(this);
                        
                        // 添加相机移动监听 - 检测用户手动交互并保存缩放级别
                        aMap.setOnCameraChangeListener(new com.amap.api.maps.AMap.OnCameraChangeListener() {
                            private boolean isProgrammaticMove = false;
                            
                            @Override
                            public void onCameraChange(com.amap.api.maps.model.CameraPosition cameraPosition) {
                                // 相机变化过程中不做处理
                            }
                            
                            @Override
                            public void onCameraChangeFinish(com.amap.api.maps.model.CameraPosition cameraPosition) {
                                // 相机变化完成后，保存缩放级别（包括用户操作和程序操作）
                                if (cameraPosition != null) {
                                    currentZoomLevel = cameraPosition.zoom;
                                    hasSavedZoomLevel = true;
                                    Log.d(TAG, "AMap - Saved zoom level: " + currentZoomLevel);
                                }
                            }
                        });
                        
                        Log.d(TAG, "AMap initialized successfully");
                    } else {
                        Log.e(TAG, "Failed to get AMap instance");
                    }
                }
            }
            // Google 地图在 onMapReady 中初始化
        } catch (Exception e) {
            Log.e(TAG, "Error in initMap: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void onMapReady(GoogleMap googleMap) {
        try {
            Log.d(TAG, "Google Map is ready");
            this.googleMap = googleMap;
            
            if (googleMap != null) {
                googleMap.getUiSettings().setMyLocationButtonEnabled(false);
                googleMap.getUiSettings().setCompassEnabled(true); // 启用指南针
                googleMap.getUiSettings().setRotateGesturesEnabled(true); // 启用旋转手势
                googleMap.getUiSettings().setTiltGesturesEnabled(true); // 启用倾斜手势
                // 完全禁用初始相机移动，让用户完全控制地图位置
                Log.d(TAG, "Google Map - Initial camera move COMPLETELY DISABLED");
                // googleMap.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.zoomTo(17));
                
                // 设置地图点击监听器
                googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(com.google.android.gms.maps.model.LatLng latLng) {
                        Log.d(TAG, "Google Map clicked at: " + latLng.latitude + ", " + latLng.longitude);
                    }
                });
                
                // 添加相机移动监听 - 检测用户手动交互
                googleMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
                    @Override
                    public void onCameraMoveStarted(int reason) {
                        Log.d(TAG, "Camera move started, reason: " + reason);
                        // REASON_GESTURE = 1 (用户手势)
                        if (reason == 1) {
                            googleMapUserInteracted = true;
                            Log.d(TAG, "User manually interacted with Google Map in TrackActivity - auto camera moves disabled");
                        }
                    }
                });
                
                // 添加相机空闲监听 - 在相机移动结束后保存缩放级别
                googleMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
                    @Override
                    public void onCameraIdle() {
                        if (googleMap != null && googleMapUserInteracted) {
                            currentZoomLevel = googleMap.getCameraPosition().zoom;
                            hasSavedZoomLevel = true;
                            Log.d(TAG, "Google Map - Saved zoom level after idle: " + currentZoomLevel);
                        }
                    }
                });
                
                Log.d(TAG, "Google Map initialized successfully");
                
                // 关键修复：加载轨迹数据前检查是否有选中的设备
                if (selectedDevice != null) {
                    loadTrackData();
                } else {
                    Log.w(TAG, "No device selected in onMapReady, skip loadTrackData");
                    // 静默处理，不显示Toast
                }
            } else {
                Log.e(TAG, "Google Map is null in onMapReady!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onMapReady: " + e.getMessage(), e);
            // 静默处理，不显示Toast
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
                
                LatLng latLng = marker.getPosition();
                getAddressForLocation(latLng);
                
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
        try {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // 关键修复：选择日期前先停止播放
                    stopPlayback();
                    
                    // 关键修复：防止快速切换日期导致并发加载
                    if (isLoadingTrackData) {
                        Log.d(TAG, "Already loading track data, ignore date picker selection");
                        return;
                    }
                    
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    
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
                    
                    updateDateBtnText();
                    loadTrackData();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
            );

            Calendar minDate = Calendar.getInstance();
            minDate.add(Calendar.MONTH, -1);
            datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());

            Calendar maxDate = Calendar.getInstance();
            datePickerDialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());

            datePickerDialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error in showDatePicker: " + e.getMessage(), e);
            // 确保在异常情况下也释放加载锁
            isLoadingTrackData = false;
        }
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
            
            startTimeBtn.setText(com.RockiotTag.tag.util.TimeFormatter.formatTimeHM(startDate.getTimeInMillis()));
            endTimeBtn.setText(com.RockiotTag.tag.util.TimeFormatter.formatTimeHM(endDate.getTimeInMillis()));
        } catch (Exception e) {
            Log.e(TAG, "Error in updateDateBtnText: " + e.getMessage(), e);
        }
    }

    private void showStartTimePicker() {
        try {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.select_start_time));

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setGravity(android.view.Gravity.CENTER);
            layout.setPadding(50, 40, 50, 10);

            final android.widget.NumberPicker hourPicker = new android.widget.NumberPicker(this);
            hourPicker.setMinValue(0);
            hourPicker.setMaxValue(23);
            hourPicker.setValue(startDate.get(Calendar.HOUR_OF_DAY));
            hourPicker.setWrapSelectorWheel(true);
            hourPicker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            LinearLayout.LayoutParams hourParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
            hourPicker.setLayoutParams(hourParams);

            TextView colonText = new TextView(this);
            colonText.setText(":");
            colonText.setTextSize(24);
            colonText.setPadding(16, 0, 16, 0);

            final android.widget.NumberPicker minutePicker = new android.widget.NumberPicker(this);
            minutePicker.setMinValue(0);
            minutePicker.setMaxValue(59);
            minutePicker.setValue(startDate.get(Calendar.MINUTE));
            minutePicker.setWrapSelectorWheel(true);
            minutePicker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            LinearLayout.LayoutParams minuteParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
            minutePicker.setLayoutParams(minuteParams);

            layout.addView(hourPicker);
            layout.addView(colonText);
            layout.addView(minutePicker);

            builder.setView(layout);

            builder.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        startDate.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
                        startDate.set(Calendar.MINUTE, minutePicker.getValue());
                        startDate.set(Calendar.SECOND, 0);
                        startDate.set(Calendar.MILLISECOND, 0);
                        updateDateBtnText();
                        loadTrackData();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in start time picker: " + e.getMessage(), e);
                        // 确保在异常情况下也释放加载锁
                        isLoadingTrackData = false;
                    }
                }
            });

            builder.setNegativeButton(getString(R.string.cancel), null);
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "Error in showStartTimePicker: " + e.getMessage(), e);
        }
    }

    private void showEndTimePicker() {
        try {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.select_end_time));

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setGravity(android.view.Gravity.CENTER);
            layout.setPadding(50, 40, 50, 10);

            final android.widget.NumberPicker hourPicker = new android.widget.NumberPicker(this);
            hourPicker.setMinValue(0);
            hourPicker.setMaxValue(23);
            hourPicker.setValue(endDate.get(Calendar.HOUR_OF_DAY));
            hourPicker.setWrapSelectorWheel(true);
            hourPicker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            LinearLayout.LayoutParams hourParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
            hourPicker.setLayoutParams(hourParams);

            TextView colonText = new TextView(this);
            colonText.setText(":");
            colonText.setTextSize(24);
            colonText.setPadding(16, 0, 16, 0);

            final android.widget.NumberPicker minutePicker = new android.widget.NumberPicker(this);
            minutePicker.setMinValue(0);
            minutePicker.setMaxValue(59);
            minutePicker.setValue(endDate.get(Calendar.MINUTE));
            minutePicker.setWrapSelectorWheel(true);
            minutePicker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            LinearLayout.LayoutParams minuteParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
            minutePicker.setLayoutParams(minuteParams);

            layout.addView(hourPicker);
            layout.addView(colonText);
            layout.addView(minutePicker);

            builder.setView(layout);

            builder.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        endDate.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
                        endDate.set(Calendar.MINUTE, minutePicker.getValue());
                        endDate.set(Calendar.SECOND, 59);
                        endDate.set(Calendar.MILLISECOND, 999);
                        updateDateBtnText();
                        loadTrackData();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in end time picker: " + e.getMessage(), e);
                        // 确保在异常情况下也释放加载锁
                        isLoadingTrackData = false;
                    }
                }
            });

            builder.setNegativeButton(getString(R.string.cancel), null);
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "Error in showEndTimePicker: " + e.getMessage(), e);
        }
    }

    private void resetTimeRange() {
        try {
            // 关键修复：重置日期前先停止播放
            stopPlayback();
            
            selectedDate = Calendar.getInstance();
            
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
            
            updateDateBtnText();
            loadTrackData();
        } catch (Exception e) {
            Log.e(TAG, "Error in resetTimeRange: " + e.getMessage(), e);
            // 确保在异常情况下也释放加载锁
            isLoadingTrackData = false;
        }
    }

    private void goToPreviousDay() {
        try {
            // 关键修复：切换日期前先停止播放，避免Handler在错误的数据上运行
            stopPlayback();
            
            // 关键修复：防止快速切换日期导致并发加载
            if (isLoadingTrackData) {
                Log.d(TAG, "Already loading track data, ignore previous day request");
                return;
            }
            
            selectedDate.add(Calendar.DAY_OF_MONTH, -1);
            Calendar minDate = Calendar.getInstance();
            minDate.add(Calendar.MONTH, -1);
            
            if (selectedDate.before(minDate)) {
                selectedDate.add(Calendar.DAY_OF_MONTH, 1);
                // 静默处理，不显示Toast
                return;
            }
            
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
            
            updateDateBtnText();
            loadTrackData();
        } catch (Exception e) {
            Log.e(TAG, "Error in goToPreviousDay: " + e.getMessage(), e);
            // 确保在异常情况下也释放加载锁
            isLoadingTrackData = false;
        }
    }

    private void goToNextDay() {
        try {
            // 关键修复：切换日期前先停止播放，避免Handler在错误的数据上运行
            stopPlayback();
            
            // 关键修复：防止快速切换日期导致并发加载
            if (isLoadingTrackData) {
                Log.d(TAG, "Already loading track data, ignore next day request");
                return;
            }
            
            selectedDate.add(Calendar.DAY_OF_MONTH, 1);
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 23);
            today.set(Calendar.MINUTE, 59);
            today.set(Calendar.SECOND, 59);
            
            if (selectedDate.after(today)) {
                selectedDate.add(Calendar.DAY_OF_MONTH, -1);
                // 静默处理，不显示Toast
                return;
            }
            
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
            
            updateDateBtnText();
            loadTrackData();
        } catch (Exception e) {
            Log.e(TAG, "Error in goToNextDay: " + e.getMessage(), e);
            // 确保在异常情况下也释放加载锁
            isLoadingTrackData = false;
        }
    }

    private void loadTrackData() {
        try {
            // 如果是 Google 地图模式，等待地图就绪后再加载
            if (isGoogleMapMode && googleMap == null) {
                Log.d(TAG, "Google Map not ready yet, skip loadTrackData (will be called in onMapReady)");
                return;
            }
            
            // 高德地图模式也需要检查地图是否就绪
            if (!isGoogleMapMode && aMap == null) {
                Log.d(TAG, "AMap not ready yet, skip loadTrackData");
                return;
            }
            
            // 默认使用智能缓存策略，不强制同步
            loadTrackData(false);
        } catch (Exception e) {
            Log.e(TAG, "Error in loadTrackData: " + e.getMessage(), e);
        }
    }

    private void loadTrackData(final boolean forceSync) {
        try {
            // 安全检查
            if (isFinishing() || isDestroyed()) {
                Log.d(TAG, "Activity is finishing or destroyed, skip loadTrackData");
                return;
            }
            
            // 关键修复：防止并发加载
            if (isLoadingTrackData) {
                Log.d(TAG, "Already loading track data, skip this request");
                return;
            }
            isLoadingTrackData = true;
            
            if (selectedDevice == null) {
                Log.w(TAG, "No device selected, skip loadTrackData");
                isLoadingTrackData = false; // 释放锁
                return;
            }
            
            // 优化1和2：判断是否是今天
            Calendar today = Calendar.getInstance();
            boolean isToday = (selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                               selectedDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                               selectedDate.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH));
            
            // 生成日期缓存键
            String dateKey = com.RockiotTag.tag.util.TimeFormatter.formatDate(selectedDate.getTimeInMillis());
            
            // 决定是否需要从服务器同步
            boolean shouldSyncFromServer = forceSync; // 强制刷新时总是同步
            
            if (!isToday && !forceSync) {
                // 非今天：检查是否已同步过
                Boolean hasSynced = syncedDates.get(dateKey);
                if (hasSynced != null && hasSynced) {
                    // 已同步过，直接使用本地缓存
                    Log.d(TAG, "Date already synced (" + dateKey + "), using local cache");
                    shouldSyncFromServer = false;
                } else {
                    // 首次查看，需要同步
                    Log.d(TAG, "First time viewing date (" + dateKey + "), will sync from server");
                    shouldSyncFromServer = true;
                }
            } else if (isToday && !forceSync) {
                // 今天但非强制刷新：先加载本地缓存，然后检查是否有新数据
                Log.d(TAG, "Today's date, will check for new data from server");
                shouldSyncFromServer = false; // 先不同步，先加载本地数据
                
                // 异步检查服务器是否有新数据
                checkServerForNewDataAsync();
            }

            // 只有需要同步时才清除旧数据并显示对话框
            if (shouldSyncFromServer) {
                try {
                    clearTrack();
                    showLoadingDialog();
                } catch (Exception e) {
                    Log.e(TAG, "Error in clearTrack: " + e.getMessage(), e);
                }
            } else {
                // 使用缓存时，只显示加载进度条，不清除现有数据
                Log.d(TAG, "Using cached data, showing loading progress without clearing");
                showLoading();
            }

            final Calendar startTime = (Calendar) startDate.clone();
            final Calendar endTime = (Calendar) endDate.clone();
            
            Log.d(TAG, "Loading track data via ViewModel. Range: " + startTime.getTime() + 
                  " - " + endTime.getTime() + ", shouldSync: " + shouldSyncFromServer + ", isToday: " + isToday);

            // 如果需要同步，先清除数据库中的旧数据
            if (shouldSyncFromServer) {
                try {
                    Log.d(TAG, "Syncing: clearing old data for device: " + selectedDevice.getDeviceNum());
                    int deleted = databaseHelper.deleteLocationRecordsByDevice(
                        selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId()
                    );
                    Log.d(TAG, "Deleted " + deleted + " old records during sync");
                    // 静默删除，不显示Toast
                } catch (Exception e) {
                    Log.e(TAG, "Error during sync cleanup: " + e.getMessage(), e);
                }
            }

            // MVVM - 使用 ViewModel 加载轨迹数据
            String deviceNum = selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
            
            // 记录同步状态（如果是首次同步）
            if (shouldSyncFromServer) {
                syncedDates.put(dateKey, true);
                lastSyncTimestamps.put(dateKey, System.currentTimeMillis());
                Log.d(TAG, "Recorded sync timestamp for " + dateKey + ": " + lastSyncTimestamps.get(dateKey));
            }
            
            viewModel.loadTrackData(deviceNum, selectedDate);
        } catch (Exception e) {
            Log.e(TAG, "Error in loadTrackData: " + e.getMessage(), e);
            isLoadingTrackData = false; // 释放锁
            // 隐藏加载进度条
            hideLoading();
            try {
                runOnUiThread(() -> {
                    try {
                        // 静默处理错误，不显示Toast
                        Log.e(TAG, "Load track failed: " + e.getMessage());
                    } catch (Exception ex) {
                        Log.w(TAG, "Failed to log error: " + ex.getMessage());
                    }
                });
            } catch (Exception ex) {
                Log.e(TAG, "Failed to log error: " + ex.getMessage());
            }
        } finally {
            // 确保在异常情况下也释放加载锁
        }
    }

    /**
     * 渲染轨迹（从 loadTrackData 中提取）
     */
    private void renderTrack(List<LocationData> locationRecords) {
        try {
            // 关键修复：渲染前再次检查 Activity 状态
            if (isFinishing() || isDestroyed()) {
                Log.d(TAG, "Activity is finishing/destroyed before renderTrack, aborting");
                hideLoading();
                isLoadingTrackData = false; // 释放锁
                return;
            }
            
            // 显示加载进度条
            showLoading();
            
            // 保存当前缩放级别（如果用户已交互过）
            float zoomToPreserve = currentZoomLevel;
            boolean shouldPreserveZoom = hasSavedZoomLevel && googleMapUserInteracted;
            
            if (shouldPreserveZoom) {
                Log.d(TAG, "Will preserve zoom level: " + zoomToPreserve);
            }
            
            if (locationRecords == null || locationRecords.isEmpty()) {
                Log.d(TAG, "NO RECORDS TO RENDER!");
                // 清除旧UI元素
                clearTrackUI();
                // 无数据时静默处理，不显示Toast
                hideLoading();
                updatePlaybackInfo(0);
                isLoadingTrackData = false; // 释放锁
                return;
            }

            Log.d(TAG, "Rendering track with " + locationRecords.size() + " records");
            
            // 关键修复：先清除旧的UI元素，再渲染新数据
            clearTrackUI();
            
            // 【核心优化】无论数据来源如何，始终根据当前选定的精度阈值重新计算停留点
            if (currentAccuracyThreshold != 200 && viewModel != null) {
                Log.d(TAG, "Applying user-selected accuracy threshold: " + currentAccuracyThreshold + "m before rendering");
                List<StayPoint> recalculatedPoints = viewModel.generateStayPointsFromRecordsWithAccuracy(
                    new ArrayList<>(locationRecords), currentAccuracyThreshold);
                synchronized (stayPoints) {
                    stayPoints.clear();
                    stayPoints.addAll(recalculatedPoints);
                }
                Log.d(TAG, "Recalculated stay points count: " + stayPoints.size());
            } else {
                // 如果是默认精度，直接使用 ViewModel 已经生成的 stayPoints
                Log.d(TAG, "Using default accuracy (200m), stay points count: " + stayPoints.size());
            }
            
            Log.d(TAG, "Processed " + stayPoints.size() + " stay points from " + locationRecords.size() + " filtered records");
            
            Log.d(TAG, "After sorting by time, first point: " + 
                new SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new Date(locationRecords.get(0).getTimestamp())) +
                ", last point: " + 
                new SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new Date(locationRecords.get(locationRecords.size()-1).getTimestamp())));

            // 根据地图模式选择绘制方法
            if (isGoogleMapMode) {
                renderTrackOnGoogleMap(locationRecords, locationRecords, shouldPreserveZoom, zoomToPreserve);
            } else {
                // 高德地图模式 - 检查aMap是否为null
                if (aMap == null) {
                    Log.e(TAG, "AMap is null in renderTrack, aborting render");
                    hideLoading();
                    isLoadingTrackData = false; // 释放锁
                    return;
                }
                
                // 关键修复：渲染过程中再次检查Activity是否正在销毁
                if (isFinishing() || isDestroyed()) {
                    Log.d(TAG, "Activity is finishing/destroyed during renderTrack, aborting");
                    hideLoading();
                    isLoadingTrackData = false; // 释放锁
                    return;
                }
                
                try {
                    // 使用 Helper 渲染高德地图轨迹
                    List<LocationRecord> recordList = new ArrayList<>();
                    for (LocationData data : allLocationRecords) {
                        recordList.add(new LocationRecord(
                            data.getDeviceId(),
                            data.getLatitude(),
                            data.getLongitude(),
                            data.getTimestamp()
                        ));
                    }
                    
                    // 直接接收返回的Polyline对象，避免数组引用传递问题
                    trackPolyline = com.RockiotTag.tag.helper.TrackMapRenderer.renderTrackOnAMap(
                        aMap,
                        stayPoints,
                        recordList,
                        showPolyline,
                        showMarkers,
                        false, // isSimpleMode已移除
                        positionMarkers,
                        arrowMarkers
                    );
                    
                    Log.d(TAG, "=== RENDER TRACK === TrackPolyline assigned: " + (trackPolyline != null ? "success" : "null") + ", StayPoints count: " + stayPoints.size());
                    
                    // 如果用户已交互过，恢复缩放级别
                    if (shouldPreserveZoom && aMap != null) {
                        com.amap.api.maps.model.CameraPosition currentPos = aMap.getCameraPosition();
                        if (currentPos != null) {
                            // 只更新缩放级别，保持当前位置
                            aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.zoomTo(zoomToPreserve));
                            Log.d(TAG, "AMap - Restored zoom level: " + zoomToPreserve);
                        }
                    }
                    
                    // 更新播放信息
                    updatePlaybackInfo(allLocationRecords.size());
                    
                    // 关键修复：UI 更新前再次检查 Activity 状态
                    if (!isFinishing() && !isDestroyed()) {
                        runOnUiThread(() -> {
                            try {
                                if (!isFinishing() && !isDestroyed()) {
                                    // 优化：移除频繁的 Toast 提示，保持界面清爽
                                    // Toast.makeText(this, getString(R.string.loaded_stay_points, stayPoints.size()), Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to show toast: " + e.getMessage());
                            }
                        });
                        
                        // 计算总距离
                        double totalDistanceKm = calculateTotalDistance();
                        totalDistanceText.setText(String.format("%.2f km", totalDistanceKm));
                        Log.d(TAG, "Total distance displayed at bottom: " + String.format("%.2f km", totalDistanceKm));
                        
                        // 显示第一个点的信息
                        if (!stayPoints.isEmpty()) {
                            StayPoint firstPoint = stayPoints.get(0);
                            LatLng firstLatLng = CoordinateUtils.wgs84ToGcj02(firstPoint.getLatitude(), firstPoint.getLongitude());
                            java.text.SimpleDateFormat fullTimeSdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                            trackPointTime.setText(fullTimeSdf.format(new Date(firstPoint.getArriveTime())));
                            
                            getAddressForLocation(firstLatLng);
                        }
                        
                        // 优化3：如果是今天，修正轨迹终点为设备最新位置
                        if (isToday(selectedDate) && !stayPoints.isEmpty()) {
                            correctTrackEndpointForToday();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error rendering track on AMap: " + e.getMessage(), e);
                    // 渲染失败时也要隐藏加载条
                    hideLoading();
                } finally {
                    // 关键修复：渲染完成后释放加载锁
                    isLoadingTrackData = false;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in renderTrack: " + e.getMessage(), e);
            hideLoading();
            isLoadingTrackData = false; // 释放锁
        } finally {
            // 确保在所有情况下都释放加载锁
            isLoadingTrackData = false;
        }
    }
    
    /**
     * 计算总距离（公里）
     */
    private double calculateTotalDistance() {
        return com.RockiotTag.tag.util.TrackCalculator.calculateTotalDistance(allLocationRecords);
    }
    
    /**
     * 在 Google 地图上渲染轨迹
     * @param locationRecords 位置记录列表
     * @param filteredRecords 过滤后的位置记录列表
     * @param shouldPreserveZoom 是否应该保持当前缩放级别
     * @param zoomToPreserve 要保持的缩放级别
     */
    private void renderTrackOnGoogleMap(List<LocationData> locationRecords, List<LocationData> filteredRecords, boolean shouldPreserveZoom, float zoomToPreserve) {
        try {
            // 关键修复：检查googleMap是否为null，避免崩溃
            if (googleMap == null) {
                Log.e(TAG, "Google Map is null in renderTrackOnGoogleMap, aborting render");
                hideLoading();
                isLoadingTrackData = false; // 释放锁
                return;
            }
            
            // 检查Activity是否正在销毁
            if (isFinishing() || isDestroyed()) {
                Log.d(TAG, "Activity is finishing/destroyed, skip rendering");
                hideLoading();
                isLoadingTrackData = false; // 释放锁
                return;
            }
            
            Log.d(TAG, "Rendering track on Google Map with " + stayPoints.size() + " stay points");
            
            // 轨迹线使用 stayPoints（经过合并处理的停留点），更清晰
            // Google 地图使用 WGS84 坐标，不需要转换
            // 关键修复：过滤无效坐标（0, 0）
            List<com.google.android.gms.maps.model.LatLng> googleLatLngList = new ArrayList<>();
            int filteredInvalidCount = 0;
            for (StayPoint stayPoint : stayPoints) {
                // 过滤接近(0, 0)的无效坐标
                if (Math.abs(stayPoint.getLatitude()) < 0.0001 && Math.abs(stayPoint.getLongitude()) < 0.0001) {
                    Log.w(TAG, "Filtered invalid coordinate in Google Map render: lat=" + stayPoint.getLatitude() + ", lng=" + stayPoint.getLongitude());
                    filteredInvalidCount++;
                    continue;
                }
                com.google.android.gms.maps.model.LatLng latLng = 
                    new com.google.android.gms.maps.model.LatLng(stayPoint.getLatitude(), stayPoint.getLongitude());
                googleLatLngList.add(latLng);
            }
            
            if (filteredInvalidCount > 0) {
                Log.d(TAG, "Filtered " + filteredInvalidCount + " invalid coordinates from Google Map rendering");
            }

            // 绘制轨迹线（使用 GoogleMapTrackRenderer）
            if (googleLatLngList.size() > 1 && googleMap != null) {
                googleTrackPolyline = com.RockiotTag.tag.util.GoogleMapTrackRenderer.drawTrackPolyline(
                    googleMap, stayPoints);
                if (googleTrackPolyline != null) {
                    googleTrackPolyline.setVisible(showPolyline);
                }
                
                googleArrowMarkers = com.RockiotTag.tag.util.GoogleMapTrackRenderer.addDirectionArrows(
                    googleMap, googleLatLngList);
            }

            // 添加标记点
            for (int i = 0; i < stayPoints.size(); i++) {
                StayPoint stayPoint = stayPoints.get(i);
                
                // 优化3：精简模式下只显示起点、终点和停留点，完全隐藏普通轨迹点
                // isSimpleMode已移除，始终显示所有点
                if (false && !stayPoint.isStayPoint() && i != 0 && i != stayPoints.size() - 1) {
                    Log.d(TAG, "Skipping ordinary point " + i + " (simple mode)");
                    continue;
                }
                
                // 优化：始终显示起点和终点，不管距离多近（Google 地图）
                // 判断是否为今天
                boolean isToday = com.RockiotTag.tag.util.GoogleMapTrackRenderer.isToday(selectedDate);
                
                // 如果只有一个点：今天显示Start，历史日期显示End
                if (stayPoints.size() == 1) {
                    if (isToday) {
                        Log.d(TAG, "Only one point today on Google Map, showing Start marker");
                        com.google.android.gms.maps.model.MarkerOptions markerOptions = 
                            com.RockiotTag.tag.util.GoogleMapTrackRenderer.createStartEndMarkerOption(
                                this, stayPoint, true, false);
                        com.google.android.gms.maps.model.Marker marker = googleMap.addMarker(markerOptions);
                        googlePositionMarkers.add(marker);
                    } else {
                        Log.d(TAG, "Only one point in history on Google Map, showing End marker");
                        com.google.android.gms.maps.model.MarkerOptions markerOptions = 
                            com.RockiotTag.tag.util.GoogleMapTrackRenderer.createStartEndMarkerOption(
                                this, stayPoint, false, true);
                        com.google.android.gms.maps.model.Marker marker = googleMap.addMarker(markerOptions);
                        googlePositionMarkers.add(marker);
                    }
                    break;
                }
                
                // 优化2和4：创建标记选项，起点终点增大尺寸并设置更高层级
                com.google.android.gms.maps.model.MarkerOptions markerOptions;
                if (i == 0) {
                    markerOptions = com.RockiotTag.tag.util.GoogleMapTrackRenderer.createStartEndMarkerOption(
                        this, stayPoint, true, false);
                } else if (i == stayPoints.size() - 1) {
                    markerOptions = com.RockiotTag.tag.util.GoogleMapTrackRenderer.createStartEndMarkerOption(
                        this, stayPoint, false, true);
                } else {
                    // 优化1：普通点使用缩小后的标记
                    markerOptions = com.RockiotTag.tag.util.GoogleMapTrackRenderer.createNormalMarkerOption(
                        this, stayPoint, i);
                }

                // 关键修复：在添加Marker前检查googleMap是否为null
                if (googleMap != null) {
                    com.google.android.gms.maps.model.Marker marker = googleMap.addMarker(markerOptions);
                    googlePositionMarkers.add(marker);
                } else {
                    Log.w(TAG, "Google Map became null during marker rendering, aborting");
                    break;
                }
            }

            // 调整相机视角 - 首次加载时定位到设备位置
            if (!googleLatLngList.isEmpty() && googleMap != null) {
                // 检查是否是用户首次交互（通过 googleMapUserInteracted 标志）
                if (!googleMapUserInteracted) {
                    // 首次加载，自动定位到轨迹的第一个点
                    com.google.android.gms.maps.model.LatLng firstPoint = googleLatLngList.get(0);
                    try {
                        googleMap.animateCamera(
                            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(firstPoint, 17));
                        Log.d(TAG, "Google Map - Auto-located to first track point: " + firstPoint.latitude + ", " + firstPoint.longitude);
                    } catch (Exception e) {
                        Log.e(TAG, "Error animating camera: " + e.getMessage(), e);
                    }
                } else {
                    // 用户已交互过，只移动中心点，保持当前缩放级别
                    com.google.android.gms.maps.model.LatLng firstPoint = googleLatLngList.get(0);
                    try {
                        float zoomToUse = shouldPreserveZoom ? zoomToPreserve : 17.0f;
                        googleMap.animateCamera(
                            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(firstPoint, zoomToUse));
                        Log.d(TAG, "Google Map - User interacted, keeping zoom level: " + zoomToUse);
                    } catch (Exception e) {
                        Log.e(TAG, "Error animating camera: " + e.getMessage(), e);
                    }
                }
            }

            updatePlaybackInfo(allLocationRecords.size());
            
            // 静默完成，不显示Toast（只在高德地图模式显示）
            
            // 计算总距离（使用 TrackCalculator）
            double totalDistanceKm = com.RockiotTag.tag.util.TrackCalculator.calculateTotalDistance(allLocationRecords);
            totalDistanceText.setText(String.format("%.2f km", totalDistanceKm));
            Log.d(TAG, "Total distance displayed at bottom: " + String.format("%.2f km", totalDistanceKm));
            
            if (!googleLatLngList.isEmpty()) {
                LocationData firstRecord = allLocationRecords.get(0);
                java.text.SimpleDateFormat fullTimeSdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                trackPointTime.setText(fullTimeSdf.format(new Date(firstRecord.getTimestamp())));
                
                // Google 地图使用 Geocoder API 获取地址
                getAddressForLocationOnGoogleMap(googleLatLngList.get(0));
            }
            
            // 优化3：如果是今天，修正轨迹终点为设备最新位置
            if (isToday(selectedDate) && !stayPoints.isEmpty()) {
                correctTrackEndpointForToday();
            }
            
            // 隐藏加载进度条
            hideLoading();
            
            // 关键修复：渲染完成后释放加载锁
            isLoadingTrackData = false;
        } catch (Exception e) {
            Log.e(TAG, "Error in renderTrackOnGoogleMap: " + e.getMessage(), e);
            hideLoading();
            isLoadingTrackData = false; // 释放锁
        } finally {
            // 确保在所有情况下都释放加载锁
            isLoadingTrackData = false;
        }
    }

    /**
     * 完整同步轨迹数据（首次加载或强制刷新时使用）
     */
    private void syncTrackDataFromServerAndReload(final String deviceNum, final long startTime, final long endTime) {
        Log.d(TAG, "=== SYNC TRACK DATA START ===");
        Log.d(TAG, "DeviceNum: " + deviceNum);
        Log.d(TAG, "SelectedDevice ID: " + (selectedDevice != null ? selectedDevice.getDeviceId() : "null"));
        Log.d(TAG, "Time range: " + startTime + " - " + endTime);
        
        // 根据设备号长度设置对应的API URL
        NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(deviceNum));
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    NewApiService apiService = NewApiService.getInstance();
                    
                    Log.d(TAG, "Calling getLocations with deviceNum=" + deviceNum + ", startTime=" + startTime + ", endTime=" + endTime);
                    List<NewApiService.LocationInfo> locations = apiService.getLocations(deviceNum, startTime, endTime);
                    Log.d(TAG, "Got " + (locations != null ? locations.size() : 0) + " locations from server");
                    
                    // 添加详细日志：打印服务器返回的前3条数据
                    if (locations != null && !locations.isEmpty()) {
                        for (int i = 0; i < Math.min(3, locations.size()); i++) {
                            NewApiService.LocationInfo loc = locations.get(i);
                            Log.d(TAG, "Server location[" + i + "]: lat=" + loc.latitude + ", lng=" + loc.longitude + ", ts=" + loc.timestamp);
                        }
                    }
                    
                    if (locations != null && !locations.isEmpty()) {
                        int addedCount = 0;
                        int skippedCount = 0;
                        int invalidCount = 0;
                        
                        for (NewApiService.LocationInfo loc : locations) {
                            Log.d(TAG, "Processing location: lat=" + loc.latitude + ", lng=" + loc.longitude + ", ts=" + loc.timestamp);
                            
                            if (loc.latitude != 0 && loc.longitude != 0 && loc.timestamp > 0) {
                                // 关键修复：使用 deviceNum 而不是 deviceId，确保查询时能匹配
                                String deviceNum = selectedDevice.getDeviceNum() != null ? 
                                    selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
                                
                                LocationRecord record = new LocationRecord(
                                    deviceNum,
                                    loc.latitude,
                                    loc.longitude,
                                    loc.timestamp
                                );
                                
                                List<LocationRecord> existingRecords = databaseHelper.getLocationRecords(
                                    deviceNum,
                                    loc.timestamp - 1000,
                                    loc.timestamp + 1000
                                );
                                
                                if (existingRecords == null || existingRecords.isEmpty()) {
                                    databaseHelper.addLocationRecord(record);
                                    addedCount++;
                                    Log.d(TAG, "Added location record: " + loc.latitude + ", " + loc.longitude + " at " + loc.timestamp);
                                } else {
                                    skippedCount++;
                                    Log.d(TAG, "Skipped duplicate record at timestamp " + loc.timestamp);
                                }
                            } else {
                                invalidCount++;
                                Log.d(TAG, "Skipped invalid record: lat=" + loc.latitude + ", lng=" + loc.longitude + ", ts=" + loc.timestamp);
                            }
                        }
                        
                        // 提前声明 deviceNum，供后续代码使用
                        final String finalDeviceNum = selectedDevice.getDeviceNum() != null ? 
                            selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
                        
                        final int finalAddedCount = addedCount;
                        final int finalSkippedCount = skippedCount;
                        final int finalInvalidCount = invalidCount;
                        final int totalFromServer = locations.size();
                        
                        // 在后台线程中先转换数据并生成停留点
                        final List<LocationData> syncedLocationData = new ArrayList<>();
                        if (locations != null) {
                            for (NewApiService.LocationInfo loc : locations) {
                                if (loc.latitude != 0 && loc.longitude != 0 && loc.timestamp > 0) {
                                    // 添加日志：记录有效数据
                                    Log.d(TAG, "Valid location: lat=" + loc.latitude + ", lng=" + loc.longitude + ", ts=" + loc.timestamp);
                                    
                                    LocationData data = new LocationData();
                                    data.setDeviceId(finalDeviceNum); // 使用 finalDeviceNum 保持一致
                                    data.setLatitude(loc.latitude);
                                    data.setLongitude(loc.longitude);
                                    data.setTimestamp(loc.timestamp);
                                    syncedLocationData.add(data);
                                } else {
                                    // 添加日志：记录无效数据
                                    Log.w(TAG, "Invalid location filtered: lat=" + loc.latitude + ", lng=" + loc.longitude + ", ts=" + loc.timestamp);
                                }
                            }
                        }
                        
                        // 生成停留点
                        final List<StayPoint> generatedStayPoints = viewModel.generateStayPointsFromRecords(syncedLocationData);
                        
                        final int finalSyncedCount = syncedLocationData.size();
                        final int finalStayPointsCount = generatedStayPoints.size();
                        
                        Log.d(TAG, "Sync summary: added=" + finalAddedCount + ", skipped=" + finalSkippedCount + ", invalid=" + finalInvalidCount + ", total=" + totalFromServer + ", syncedData=" + finalSyncedCount + ", stayPoints=" + finalStayPointsCount);
                        
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Log.d(TAG, "=== SYNC RESULT DEBUG ===");
                                    Log.d(TAG, "syncedData=" + finalSyncedCount + ", stayPoints=" + finalStayPointsCount);
                                    Log.d(TAG, "addedCount=" + finalAddedCount + ", skippedCount=" + finalSkippedCount + ", invalidCount=" + finalInvalidCount);
                                    Log.d(TAG, "totalFromServer=" + totalFromServer);
                                    
                                    if (finalSyncedCount > 0) {
                                        Log.d(TAG, "Data validation check:");
                                        for (int i = 0; i < Math.min(3, syncedLocationData.size()); i++) {
                                            LocationData data = syncedLocationData.get(i);
                                            Log.d(TAG, "  Data[" + i + "]: lat=" + data.getLatitude() + ", lng=" + data.getLongitude() + ", ts=" + data.getTimestamp());
                                        }
                                        
                                        // 优化：同步成功后，直接渲染服务器返回的数据，避免数据库事务延迟
                                        Log.d(TAG, "Server sync successful, rendering " + finalSyncedCount + " records directly");
                                        
                                        // 关键修复：先更新 Activity 的成员变量，确保渲染时使用正确的数据
                                        allLocationRecords.clear();
                                        allLocationRecords.addAll(syncedLocationData);
                                        
                                        stayPoints.clear();
                                        stayPoints.addAll(generatedStayPoints);
                                        
                                        Log.d(TAG, "Updated allLocationRecords=" + allLocationRecords.size() + ", stayPoints=" + stayPoints.size());
                                        
                                        // 重置同步状态
                                        viewModel.setSyncingCompleted(true);
                                        
                                        // 直接渲染数据，绕过数据库查询
                                        Log.d(TAG, "Calling renderTrack with " + syncedLocationData.size() + " records");
                                        renderTrack(syncedLocationData);
                                    } else {
                                        Log.e(TAG, "NO DATA TO RENDER! finalSyncedCount=0");
                                        Log.e(TAG, "This means either:");
                                        Log.e(TAG, "1. Server returned empty array");
                                        Log.e(TAG, "2. All data was filtered out (lat/lng=0 or timestamp=0)");
                                        Log.e(TAG, "3. API request failed silently");
                                        
                                        // 同步完成，无数据 - 隐藏加载对话框并释放锁
                                        viewModel.setSyncingCompleted(false);
                                        hideLoading();
                                        isLoadingTrackData = false;
                                        Log.d(TAG, "No track data from server sync");
                                        updatePlaybackInfo(0);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error in sync result processing: " + e.getMessage(), e);
                                    // 确保在异常情况下也隐藏加载对话框并释放锁
                                    hideLoading();
                                    isLoadingTrackData = false;
                                }
                            }
                        });
                    } else {
                        Log.d(TAG, "No locations returned from server");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // 同步完成，无数据 - 隐藏加载对话框并释放锁
                                    viewModel.setSyncingCompleted(false);
                                    hideLoading();
                                    isLoadingTrackData = false;
                                    Log.d(TAG, "No locations returned from server");
                                    updatePlaybackInfo(0);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error in no data handling: " + e.getMessage(), e);
                                    // 确保在异常情况下也隐藏加载对话框并释放锁
                                    hideLoading();
                                    isLoadingTrackData = false;
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error syncing track data: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // 同步失败 - 隐藏加载对话框并释放锁
                                viewModel.setSyncingCompleted(false);
                                hideLoading();
                                isLoadingTrackData = false;
                                Log.e(TAG, "Sync track error: " + e.getMessage());
                            } catch (Exception ex) {
                                Log.e(TAG, "Error in sync error handling: " + ex.getMessage(), ex);
                                hideLoading();
                                isLoadingTrackData = false;
                            }
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * 获取位置地址（高德地图）
     */
    private void getAddressForLocation(LatLng latLng) {
        try {
            GeocodeHelper.reverseGeocodeWithAMap(this, latLng.latitude, latLng.longitude, 
                new GeocodeHelper.OnGeocodeResultListener() {
                    @Override
                    public void onGeocodeSuccess(String address) {
                        runOnUiThread(() -> trackPointAddress.setText(address));
                    }
                    
                    @Override
                    public void onGeocodeFailed(String error) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "地址解析失败: " + error);
                            trackPointAddress.setText(getString(R.string.get_address_failed));
                        });
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Error in getAddressForLocation: " + e.getMessage(), e);
        }
    }

    private void updatePlaybackInfo(int count) {
        // 使用 stayPoints 的数量设置播放进度条
        int playCount = stayPoints.size();
        playbackSeekbar.setMax(Math.max(0, playCount - 1));
        
        if (playCount > 0 && !stayPoints.isEmpty()) {
            String timeStr = com.RockiotTag.tag.util.TimeFormatter.formatFullTime(stayPoints.get(playCount - 1).getArriveTime());
            totalTimeText.setText(timeStr.substring(timeStr.indexOf(" ") + 1));
        } else {
            totalTimeText.setText("00:00:00");
        }
        currentTimeText.setText("00:00:00");
        playbackSeekbar.setProgress(0);
    }
    
    // filterAbnormalPoints 和 processStayPoints 已迁移到 TrackViewModel

    private void clearTrack() {
        
        if (isGoogleMapMode) {
            // 清理 Google 地图 Marker
            for (com.google.android.gms.maps.model.Marker marker : googlePositionMarkers) {
                marker.remove();
            }
            googlePositionMarkers.clear();
            
            for (com.google.android.gms.maps.model.Marker marker : googleArrowMarkers) {
                marker.remove();
            }
            googleArrowMarkers.clear();

            // 清理 Google 地图轨迹线
            if (googleTrackPolyline != null) {
                googleTrackPolyline.remove();
                googleTrackPolyline = null;
            }
            
            // 清理 Google 地图播放相关对象
            if (googlePlayedPolyline != null) {
                googlePlayedPolyline.remove();
                googlePlayedPolyline = null;
            }
            if (googlePlayMarker != null) {
                googlePlayMarker.remove();
                googlePlayMarker = null;
            }
            googlePlayedPoints.clear();
        } else {
            // 清理高德地图 Marker
            for (Marker marker : positionMarkers) {
                marker.remove();
            }
            positionMarkers.clear();
            
            for (Marker marker : arrowMarkers) {
                marker.remove();
            }
            arrowMarkers.clear();

            // 清理高德地图轨迹线
            if (trackPolyline != null) {
                trackPolyline.remove();
                trackPolyline = null;
            }
            
            // 清理高德地图播放相关对象
            if (playedPolyline != null) {
                playedPolyline.remove();
                playedPolyline = null;
            }
            if (playMarker != null) {
                playMarker.remove();
                playMarker = null;
            }
            playedPoints.clear();
            currentPlayPosition = null;
        }
        
        // 5. 停止所有动画
        if (moveAnimator != null && moveAnimator.isRunning()) {
            moveAnimator.cancel();
            moveAnimator = null;
        }
        
        // 6. 清理轨迹数据（释放内存）
        allLocationRecords.clear();
        stayPoints.clear();
        
        Log.d(TAG, "Track cleared, memory released");
    }
    
    // processStayPoints 已迁移到 TrackViewModel
    
    /**
     * 显示加载进度条
     */
    private void showLoading() {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 显示加载提示对话框
     */
    private void showLoadingDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        if (loadingDialog != null && loadingDialog.isShowing()) {
            return;
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.loading_track_title));
        builder.setMessage(getString(R.string.loading_track_message));
        builder.setCancelable(false);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(50, 50, 50, 50);
        
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(20, 0, 40, 0);
        progressBar.setLayoutParams(params);
        layout.addView(progressBar);
        
        TextView textView = new TextView(this);
        textView.setText(getString(R.string.loading_track_message));
        textView.setTextSize(14);
        layout.addView(textView);
        
        builder.setView(layout);
        loadingDialog = builder.create();
        loadingDialog.show();
    }
    
    /**
     * 隐藏加载提示对话框
     */
    private void hideLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
        loadingDialog = null;
    }
    
    /**
     * 隐藏加载进度条
     */
    private void hideLoading() {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.GONE);
        }
        hideLoadingDialog();
    }
    
    /**
     * 从服务器同步轨迹数据
     */
    private void syncTrackFromServer(String deviceNum, long startTime, long endTime) {
        Log.d(TAG, "syncTrackFromServer: deviceNum=" + deviceNum + ", startTime=" + startTime + ", endTime=" + endTime);
        
        if (isFinishing() || isDestroyed()) {
            Log.d(TAG, "Activity is finishing/destroyed, skip sync");
            viewModel.setLoading(false);
            hideLoading();
            isLoadingTrackData = false;
            return;
        }
        
        new Thread(() -> {
            try {
                com.RockiotTag.tag.NewApiService apiService = com.RockiotTag.tag.NewApiService.getInstance();
                List<com.RockiotTag.tag.NewApiService.LocationInfo> serverRecords = apiService.getLocations(deviceNum, startTime, endTime);
                
                Log.d(TAG, "Received " + (serverRecords != null ? serverRecords.size() : 0) + " records from server");
                
                if (serverRecords != null && !serverRecords.isEmpty()) {
                    // 保存到本地数据库
                    for (com.RockiotTag.tag.NewApiService.LocationInfo info : serverRecords) {
                        LocationRecord record = new LocationRecord(
                            info.deviceNum,
                            info.latitude,
                            info.longitude,
                            info.timestamp
                        );
                        databaseHelper.addLocationRecord(record);
                    }
                    Log.d(TAG, "Saved " + serverRecords.size() + " records to local database");
                    
                    // 更新同步状态
                    String dateKey = com.RockiotTag.tag.util.TimeFormatter.formatDate(selectedDate.getTimeInMillis());
                    syncedDates.put(dateKey, true);
                    lastSyncTimestamps.put(dateKey, System.currentTimeMillis());
                    
                    // 重新从本地数据库加载
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            viewModel.loadTrackDataFromLocal(deviceNum, selectedDate);
                        }
                    });
                } else {
                    // 服务器没有数据
                    runOnUiThread(() -> {
                        viewModel.setLoading(false);
                        hideLoading();
                        isLoadingTrackData = false;
                        if (!isFinishing() && !isDestroyed()) {
                            clearTrackUI();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error syncing from server: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    viewModel.setLoading(false);
                    hideLoading();
                    isLoadingTrackData = false;
                });
            }
        }).start();
    }
    
    /**
     * 异步检查服务器是否有新数据（仅用于当天轨迹）
     */
    private void checkServerForNewDataAsync() {
        if (selectedDevice == null) {
            Log.w(TAG, "No device selected, skip server check");
            return;
        }
        
        String deviceNum = selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
        
        new Thread(() -> {
            try {
                // 获取本地最新记录的时间戳
                long startTime = getDayStartTime(selectedDate);
                long endTime = getDayEndTime(selectedDate);
                List<LocationRecord> localRecords = databaseHelper.getLocationRecords(deviceNum, startTime, endTime);
                long localLatestTime = 0;
                if (localRecords != null && !localRecords.isEmpty()) {
                    localLatestTime = localRecords.get(localRecords.size() - 1).getTimestamp();
                    Log.d(TAG, "Local latest time: " + localLatestTime);
                }
                
                // 获取服务器最新数据的时间戳
                com.RockiotTag.tag.NewApiService apiService = com.RockiotTag.tag.NewApiService.getInstance();
                com.RockiotTag.tag.NewApiService.DeviceInfo latestInfo = apiService.getDeviceLatest(deviceNum);
                
                if (latestInfo != null && latestInfo.timestamp > 0) {
                    long serverLatestTime = latestInfo.timestamp;
                    Log.d(TAG, "Server latest time: " + serverLatestTime + ", Local latest time: " + localLatestTime);
                    
                    // 如果服务器有新数据（时间戳比本地新）
                    if (serverLatestTime > localLatestTime) {
                        Log.d(TAG, "Server has new data, syncing...");
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                showLoadingDialog();
                                syncTrackFromServer(deviceNum, startTime, endTime);
                            }
                        });
                    } else {
                        Log.d(TAG, "Server has no new data, using local cache");
                    }
                } else {
                    Log.w(TAG, "Failed to get latest data from server or no timestamp");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking server for new data: " + e.getMessage(), e);
            }
        }).start();
    }
    
    /**
     * 获取一天的开始时间（00:00:00）
     */
    private long getDayStartTime(Calendar date) {
        Calendar start = (Calendar) date.clone();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return start.getTimeInMillis();
    }
    
    /**
     * 获取一天的结束时间（23:59:59）
     */
    private long getDayEndTime(Calendar date) {
        Calendar end = (Calendar) date.clone();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
        return end.getTimeInMillis();
    }
    
    /**
     * 显示轨迹统计信息
     */
    private void showTrackStatistics() {
        // 使用 Helper 显示统计信息
        com.RockiotTag.tag.helper.TrackStatisticsHelper.showStatisticsDialog(
            this, stayPoints, totalDistanceText);
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
                            Log.d(TAG, "Added initial track point after cleanup: lat=" + 
                                  selectedDevice.getLatitude() + ", lng=" + selectedDevice.getLongitude() +
                                  ", timestamp=" + deviceTimestamp);
                            
                            Toast.makeText(TrackActivity.this, 
                                getString(R.string.cleaned_track_with_current, deletedCount), 
                                Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(TrackActivity.this, 
                                getString(R.string.cleaned_track_records, deletedCount), 
                                Toast.LENGTH_SHORT).show();
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
            mapView.onResume();
            
            // 启动自动刷新
            startAutoRefresh();
            
            // 每次恢复时重新从数据库读取设备信息，确保获取最新的位置和时间戳
            refreshSelectedDevice();
            
            // 检查地图提供商是否发生变化
            android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
            String mapProvider = prefs.getString("map_provider", "amap");
            boolean shouldBeGoogleMap = "google".equals(mapProvider);
            
            Log.d(TAG, "TrackActivity onResume - Current isGoogleMapMode: " + isGoogleMapMode + ", Should be: " + shouldBeGoogleMap);
            Log.d(TAG, "TrackActivity onResume - Map provider from prefs: " + mapProvider);
            
            // 如果地图提供商发生变化，需要重新启动Activity
            if (isGoogleMapMode != shouldBeGoogleMap) {
                Log.d(TAG, "Map provider changed from " + (isGoogleMapMode ? "google" : "amap") + 
                      " to " + (shouldBeGoogleMap ? "google" : "amap") + ", restarting TrackActivity...");
                // 先停止播放，避免在重启过程中出现问题
                stopPlayback();
                finish();
                startActivity(getIntent());
                overridePendingTransition(0, 0); // 无动画切换
                return;
            }
            
            // 确保正确的地图视图可见性
            if (isGoogleMapMode) {
                if (mapView != null) {
                    mapView.setVisibility(View.GONE);
                }
                if (googleMapFragment != null && googleMapFragment.getView() != null) {
                    googleMapFragment.getView().setVisibility(View.VISIBLE);
                    googleMapFragment.getView().bringToFront();
                }
                Log.d(TAG, "onResume: Showing Google Map, hiding AMap");
                Log.d(TAG, "onResume - MapView visibility: " + (mapView != null ? mapView.getVisibility() : "null"));
                Log.d(TAG, "onResume - GoogleMap visibility: " + (googleMapFragment != null && googleMapFragment.getView() != null ? googleMapFragment.getView().getVisibility() : "null"));
            } else {
                if (mapView != null) {
                    mapView.setVisibility(View.VISIBLE);
                    mapView.bringToFront();
                }
                if (googleMapFragment != null && googleMapFragment.getView() != null) {
                    googleMapFragment.getView().setVisibility(View.GONE);
                }
                Log.d(TAG, "onResume: Showing AMap, hiding Google Map");
                Log.d(TAG, "onResume - MapView visibility: " + (mapView != null ? mapView.getVisibility() : "null"));
                Log.d(TAG, "onResume - GoogleMap visibility: " + (googleMapFragment != null && googleMapFragment.getView() != null ? googleMapFragment.getView().getVisibility() : "null"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume: " + e.getMessage(), e);
        }
    }
    
    private void refreshSelectedDevice() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String selectedDeviceId = prefs.getString("selected_device_id", "");
        if (!selectedDeviceId.isEmpty()) {
            Device device = databaseHelper.getDevice(selectedDeviceId);
            if (device != null) {
                selectedDevice = device;
                Log.d(TAG, "Refreshed device: lat=" + device.getLatitude() + 
                      ", lng=" + device.getLongitude() + 
                      ", lastSeen=" + device.getLastSeen());
            }
        }
    }

    @Override
    protected void onPause() {
        try {
            super.onPause();
            mapView.onPause();
            
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
            mapView.onSaveInstanceState(outState);
        } catch (Exception e) {
            Log.e(TAG, "Error in onSaveInstanceState: " + e.getMessage(), e);
        }
    }
    
    // ==================== Google 地图辅助方法 ====================
    
    /**
     * 在 Google 地图上获取地址（使用 GeocodeHelper）
     */
    private void getAddressForLocationOnGoogleMap(com.google.android.gms.maps.model.LatLng latLng) {
        try {
            String address = GeocodeHelper.reverseGeocodeWithAndroidGeocoder(this, latLng.latitude, latLng.longitude);
            if (address != null && !address.isEmpty()) {
                trackPointAddress.setText(address);
            } else {
                trackPointAddress.setText(getString(R.string.unknown_location));
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoder error: " + e.getMessage());
            trackPointAddress.setText(getString(R.string.get_address_failed));
        }
    }
    
    /**
     * 在 Google 地图上移动到下一个点
     */
    private void moveToNextPointOnGoogleMap() {
        try {
            if (!isPlaying || currentPlayIndex >= stayPoints.size() - 1) {
                if (currentPlayIndex >= stayPoints.size() - 1) {
                    pausePlayback();
                    currentPlayIndex = stayPoints.size() - 1;
                }
                return;
            }

            StayPoint fromStayPoint = stayPoints.get(currentPlayIndex);
            StayPoint toStayPoint = stayPoints.get(currentPlayIndex + 1);
            
            com.google.android.gms.maps.model.LatLng fromPos = 
                new com.google.android.gms.maps.model.LatLng(fromStayPoint.getLatitude(), fromStayPoint.getLongitude());
            com.google.android.gms.maps.model.LatLng toPos = 
                new com.google.android.gms.maps.model.LatLng(toStayPoint.getLatitude(), toStayPoint.getLongitude());
            
            // 直接移动到下一个点
            currentPlayIndex++;
            
            if (googlePlayMarker != null) {
                googlePlayMarker.setPosition(toPos);
            }
            
            googlePlayedPoints.add(toPos);
            updatePlayedPolylineOnGoogleMap();
            
            playbackSeekbar.setProgress(currentPlayIndex);
            String timeStr = com.RockiotTag.tag.util.TimeFormatter.formatFullTime(toStayPoint.getArriveTime());
            currentTimeText.setText(timeStr.substring(timeStr.indexOf(" ") + 1));
            trackPointTime.setText(timeStr);
            
            // 完全禁用谷歌地图的相机移动
            Log.d(TAG, "Google Map - Camera move in moveToNextPointOnGoogleMap COMPLETELY DISABLED");
            
            // 获取地址
            if (currentPlayIndex % 10 == 0) {
                getAddressForLocationOnGoogleMap(toPos);
            }
            
            // 继续播放
            if (isPlaying) {
                playHandler.sendEmptyMessageDelayed(0, 1000 / playSpeed);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in moveToNextPointOnGoogleMap: " + e.getMessage(), e);
            pausePlayback();
        }
    }
    
    /**
     * 更新 Google 地图上的已播放轨迹线
     */
    private void updatePlayedPolylineOnGoogleMap() {
        try {
            if (googlePlayedPoints.size() > 1) {
                if (googlePlayedPolyline == null) {
                    // 第一次创建
                    com.google.android.gms.maps.model.PolylineOptions polylineOptions = 
                        new com.google.android.gms.maps.model.PolylineOptions()
                            .addAll(googlePlayedPoints)
                            .color(0xFFFF5722)
                            .width(12f);
                    googlePlayedPolyline = googleMap.addPolyline(polylineOptions);
                } else {
                    // 后续更新
                    googlePlayedPolyline.setPoints(googlePlayedPoints);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in updatePlayedPolylineOnGoogleMap: " + e.getMessage(), e);
        }
    }

    /**
     * 初始化自动刷新
     */
    private void initAutoRefresh() {
        // 使用主线程的 Looper，确保 Handler 在主线程运行
        autoRefreshHandler = new Handler(android.os.Looper.getMainLooper());
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    // 执行刷新操作
                    performAutoRefresh();
                    // 安排下一次刷新
                    if (autoRefreshHandler != null && !isFinishing() && !isDestroyed()) {
                        autoRefreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in auto refresh runnable: " + e.getMessage(), e);
                    // 发生异常时停止自动刷新
                    stopAutoRefresh();
                }
            }
        };
    }
    
    /**
     * 启动自动刷新
     */
    private void startAutoRefresh() {
        if (autoRefreshHandler != null && autoRefreshRunnable != null) {
            // 先移除之前的回调，避免重复
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
            
            // 检查是否是今天，只有今天才启动自动刷新
            Calendar today = Calendar.getInstance();
            boolean isToday = (selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                               selectedDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                               selectedDate.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH));
            
            if (isToday) {
                // 延迟10秒后开始第一次自动刷新，然后每隔3分钟刷新一次
                autoRefreshHandler.postDelayed(autoRefreshRunnable, 10000);
                Log.d(TAG, "Auto refresh started for TODAY, interval: " + (AUTO_REFRESH_INTERVAL / 1000 / 60) + " minutes");
            } else {
                Log.d(TAG, "Auto refresh skipped: selected date is not today");
            }
        }
    }
    
    /**
     * 停止自动刷新
     */
    private void stopAutoRefresh() {
        if (autoRefreshHandler != null && autoRefreshRunnable != null) {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
            Log.d(TAG, "Auto refresh stopped");
        }
    }
    
    /**
     * 执行自动刷新
     */
    private void performAutoRefresh() {
        try {
            Log.d(TAG, "Performing auto refresh for current date...");
            
            // 安全检查：确认Activity仍然有效
            if (isFinishing() || isDestroyed()) {
                Log.d(TAG, "Activity is finishing or destroyed, skip auto refresh");
                stopAutoRefresh();
                return;
            }
            
            // 检查必要组件是否初始化
            if (viewModel == null || databaseHelper == null || selectedDevice == null) {
                Log.w(TAG, "Required components not initialized, skip auto refresh");
                return;
            }
            
            // 在UI线程中执行刷新操作
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 再次检查Activity状态
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        
                        // 静默刷新，不显示Toast（优化用户体验）
                        Log.d(TAG, "Auto refresh: loading track data silently");
                        
                        // 只刷新当前日期的轨迹数据（非强制同步，优先使用本地缓存）
                        loadTrackData(false);
                        
                        Log.d(TAG, "Auto refresh completed for date: " + selectedDate.getTime());
                    } catch (Exception e) {
                        Log.e(TAG, "Error during auto refresh: " + e.getMessage(), e);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in performAutoRefresh: " + e.getMessage(), e);
            // 发生异常时停止自动刷新，避免持续崩溃
            stopAutoRefresh();
        }
    }

    /**
     * 转换 LocationRecord 列表为 LocationData 列表
     */
    private List<LocationData> convertToLocationDataList(List<LocationRecord> records) {
        List<LocationData> result = new ArrayList<>();
        if (records != null) {
            for (LocationRecord lr : records) {
                LocationData ld = convertToLocationData(lr);
                if (ld != null) result.add(ld);
            }
        }
        return result;
    }

    /**
     * 转换单个 LocationRecord 为 LocationData
     */
    private LocationData convertToLocationData(LocationRecord record) {
        if (record == null) return null;
        LocationData ld = new LocationData();
        ld.setId(record.getId());
        ld.setDeviceId(record.getDeviceId());
        ld.setLatitude(record.getLatitude());
        ld.setLongitude(record.getLongitude());
        ld.setTimestamp(record.getTimestamp());
        return ld;
    }
    
    /**
     * 获取设备的最新位置（用于今天的轨迹终点修正）
     * @return 设备最新位置数据，如果没有则返回null
     */
    private LocationData getLatestDeviceLocation() {
        try {
            if (selectedDevice == null) {
                Log.w(TAG, "No device selected for getting latest location");
                return null;
            }
            
            String deviceId = selectedDevice.getDeviceNum() != null ? 
                selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
            
            // 从数据库查询设备最新的一条位置记录
            // 使用时间范围查询最近24小时的数据
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (24 * 60 * 60 * 1000); // 24小时前
            List<LocationRecord> records = databaseHelper.getLocationRecords(deviceId, startTime, endTime);
            if (records != null && !records.isEmpty()) {
                // 按时间戳降序排序，取第一条
                Collections.sort(records, (r1, r2) -> Long.compare(r2.getTimestamp(), r1.getTimestamp()));
                LocationRecord latest = records.get(0);
                
                LocationData data = new LocationData();
                data.setId(latest.getId());
                data.setDeviceId(latest.getDeviceId());
                data.setLatitude(latest.getLatitude());
                data.setLongitude(latest.getLongitude());
                data.setTimestamp(latest.getTimestamp());
                
                Log.d(TAG, "Latest device location: " + latest.getLatitude() + ", " + 
                    latest.getLongitude() + " at " + latest.getTimestamp());
                
                return data;
            } else {
                Log.d(TAG, "No location records found for device: " + deviceId);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting latest device location: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 判断是否是今天
     * @param calendar 要判断的日期
     * @return true表示是今天
     */
    private boolean isToday(Calendar calendar) {
        Calendar today = Calendar.getInstance();
        return calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
               calendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
               calendar.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH);
    }
    
    /**
     * 修正今天的轨迹终点为设备最新位置
     * 如果设备的最新位置时间戳晚于停留点列表最后一个点，则用设备位置替换/追加终点
     */
    private void correctTrackEndpointForToday() {
        try {
            Log.d(TAG, "Correcting track endpoint for today...");
            
            // 获取设备最新位置
            LocationData latestLocation = getLatestDeviceLocation();
            if (latestLocation == null) {
                Log.d(TAG, "No device location available for endpoint correction");
                return;
            }
            
            // 检查是否有停留点
            if (stayPoints.isEmpty()) {
                Log.d(TAG, "No stay points to correct");
                return;
            }
            
            // 获取最后一个停留点
            StayPoint lastStayPoint = stayPoints.get(stayPoints.size() - 1);
            long lastStayPointTime = lastStayPoint.getLeaveTime() > 0 ? 
                lastStayPoint.getLeaveTime() : lastStayPoint.getArriveTime();
            
            // 如果设备最新位置的时间戳晚于最后一个停留点
            if (latestLocation.getTimestamp() > lastStayPointTime) {
                Log.d(TAG, "Device location is newer than last stay point, correcting endpoint");
                Log.d(TAG, "Last stay point time: " + lastStayPointTime + ", Device location time: " + latestLocation.getTimestamp());
                
                // 创建新的终点停留点（使用4参数构造函数）
                StayPoint newEndPoint = new StayPoint(
                    latestLocation.getLatitude(),
                    latestLocation.getLongitude(),
                    latestLocation.getTimestamp(),
                    latestLocation.getTimestamp()
                );
                
                // 添加到停留点列表
                stayPoints.add(newEndPoint);
                
                // 添加到位置记录列表
                allLocationRecords.add(latestLocation);
                
                // 重新渲染轨迹（增量更新）
                runOnUiThread(() -> {
                    try {
                        Log.d(TAG, "Re-rendering track with corrected endpoint");
                        
                        if (!isGoogleMapMode && aMap != null) {
                            // 高德地图模式：清除旧标记和轨迹线
                            clearTrackUI();
                            
                            // 重新渲染
                            List<LocationRecord> recordList = new ArrayList<>();
                            for (LocationData data : allLocationRecords) {
                                recordList.add(new LocationRecord(
                                    data.getDeviceId(),
                                    data.getLatitude(),
                                    data.getLongitude(),
                                    data.getTimestamp()
                                ));
                            }
                            
                            trackPolyline = com.RockiotTag.tag.helper.TrackMapRenderer.renderTrackOnAMap(
                                aMap,
                                stayPoints,
                                recordList,
                                showPolyline,
                                showMarkers,
                                false, // isSimpleMode已移除
                                positionMarkers,
                                arrowMarkers
                            );
                            
                            updatePlaybackInfo(allLocationRecords.size());
                            
                            // 静默更新，不显示Toast
                            Log.d(TAG, "Track endpoint updated silently");
                        } else if (isGoogleMapMode && googleMap != null) {
                            // Google 地图模式：清除旧标记和轨迹线
                            clearTrackUI();
                            
                            // 重新渲染（保持当前缩放级别）
                            float zoomToPreserve = currentZoomLevel;
                            boolean shouldPreserveZoom = hasSavedZoomLevel && googleMapUserInteracted;
                            renderTrackOnGoogleMap(new ArrayList<>(allLocationRecords), new ArrayList<>(allLocationRecords), shouldPreserveZoom, zoomToPreserve);
                            
                            // 静默更新，不显示Toast
                            Log.d(TAG, "Track endpoint updated silently (Google Map)");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error re-rendering track: " + e.getMessage(), e);
                    }
                });
            } else {
                Log.d(TAG, "Device location is not newer than last stay point, no correction needed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in correctTrackEndpointForToday: " + e.getMessage(), e);
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
}
