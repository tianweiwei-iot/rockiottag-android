package com.RockiotTag.tag;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.AMap.OnMapLoadedListener;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.google.android.gms.maps.SupportMapFragment;

public class MainActivity extends AppCompatActivity implements AMapLocationListener, OnMapLoadedListener, GeocodeSearch.OnGeocodeSearchListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_DEVICE_LIST = 101;

    private MapView mapView;
    private AMap aMap;
    private MapManager mapManager;
    private SupportMapFragment googleMapFragment;
    private GeocodeSearch geocodeSearch;
    private AMapLocationClient locationClient;
    private BLEManager bleManager;
    private CrowdSourcingManager crowdSourcingManager;
    private DatabaseHelper databaseHelper;
    private NewApiService apiService;
    private UnboundDeviceManager unboundDeviceManager;

    private ImageButton menuBtn;
    private ImageButton buzzerBtn;
    private ImageButton locateBtn;
    private ImageButton mapTypeBtn;
    private ImageButton refreshBtn;
    private ImageButton trackBtn;
    private TextView deviceNameText;
    private LinearLayout deviceNameContainer;
    private TextView deviceTagIcon;
    private TextView batteryLevelText;
    private TextView deviceAddressText;
    private TextView updateTimeText;
    private View bottomInfo;
    private boolean isSatelliteMap = false;

    private double currentLatitude = 22.543611;
    private double currentLongitude = 113.881944;
    private String currentDeviceName = "tag";
    private Marker currentLocationMarker;
    private Marker deviceLocationMarker;
    private long lastUserInteractionTime = 0;
    private static final long AUTO_RETURN_DELAY = 10000; // 10秒延迟
    private boolean isFirstLocation = true; // 标记是否是第一次定位
    private Device selectedDevice = null; // 当前选中的设备
    private Handler trackRefreshHandler;
    private Runnable trackRefreshRunnable;
    private double lastRecordedLatitude = 0;
    private double lastRecordedLongitude = 0;
    private long lastRecordedTimestamp = 0;
    private static final long TRACK_REFRESH_INTERVAL = 30 * 1000; // 30秒
    
    private String getTagIcon(String tag) {
        if (tag == null || tag.isEmpty() || tag.equals("无标签") || tag.equals("No Tag")) {
            return "";
        }
        switch (tag) {
            case "家":
            case "Home":
                return "🏠";
            case "公司":
            case "Office":
                return "🏢";
            case "学校":
            case "School":
                return "🏫";
            case "医院":
            case "Hospital":
                return "🏥";
            case "商场":
            case "Mall":
                return "🏬";
            case "公园":
            case "Park":
                return "🌳";
            case "健身房":
            case "Gym":
                return "🏋️";
            case "餐厅":
            case "Restaurant":
                return "🍽️";
            case "宠物":
            case "Pet":
            case "dog":
            case "cat":
            case "bird":
            case "pig":
                return "🐾";
            case "车":
            case "Car":
            case "car":
                return "🚗";
            case "自行车":
            case "Bike":
            case "bike":
                return "🚴";
            case "摩托车":
            case "Motorcycle":
            case "moto":
                return "🏍️";
            case "行李":
            case "Luggage":
                return "🧳";
            case "钥匙":
            case "Key":
            case "key":
                return "🔑";
            case "钱包":
            case "Wallet":
            case "wallet":
                return "👛";
            case "手机":
            case "Phone":
                return "📱";
            case "电脑":
            case "Computer":
                return "💻";
            case "包":
            case "Bag":
            case "bag":
                return "👜";
            case "boy":
                return "👦";
            case "girl":
                return "👧";
            case "bank_card":
                return "💳";
            default:
                return "🏷️";
        }
    }
    
    private void updateDeviceNameWithTag(String name, String tag) {
        deviceNameText.setText(name);
        if (deviceTagIcon != null) {
            String icon = getTagIcon(tag);
            deviceTagIcon.setText(icon);
            deviceTagIcon.setVisibility(icon.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (deviceNameContainer != null) {
            deviceNameContainer.setVisibility(View.VISIBLE);
        }
    }

    
    private void cleanOldTrackData(boolean forceClean) {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean hasCleanedOldTrack = prefs.getBoolean("has_cleaned_old_track", false);
        
        if (!hasCleanedOldTrack || forceClean) {
            Log.d(TAG, "Cleaning old track data (force=" + forceClean + ")...");
            int deletedCount = databaseHelper.deleteAllLocationRecords();
            Log.d(TAG, "Deleted " + deletedCount + " old track records");
            
            // 重置最后记录的位置和时间戳，确保新轨迹从第一个点开始
            lastRecordedLatitude = 0;
            lastRecordedLongitude = 0;
            lastRecordedTimestamp = 0;
            Log.d(TAG, "Reset last recorded location and timestamp");
            
            if (!forceClean) {
                android.content.SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("has_cleaned_old_track", true);
                editor.apply();
            }
        } else {
            Log.d(TAG, "Old track data already cleaned");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 先恢复用户的语言偏好
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String languageCode = prefs.getString("language", "zh");
        LanguageUtils.applyLanguage(this, languageCode);
        
        // 先恢复用户的深色模式偏好
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化高德地图隐私合规设置
        try {
            com.amap.api.maps.MapsInitializer.updatePrivacyShow(this, true, true);
            com.amap.api.maps.MapsInitializer.updatePrivacyAgree(this, true);
            com.amap.api.location.AMapLocationClient.updatePrivacyShow(this, true, true);
            com.amap.api.location.AMapLocationClient.updatePrivacyAgree(this, true);
            Log.d(TAG, "Privacy compliance settings updated successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error updating privacy compliance settings: " + e.getMessage());
        }

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        googleMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.google_map_fragment);

        menuBtn = findViewById(R.id.menu_btn);
        buzzerBtn = findViewById(R.id.buzzer_btn);
        deviceNameContainer = findViewById(R.id.device_name_container);
        deviceNameText = findViewById(R.id.device_name);
        deviceTagIcon = findViewById(R.id.device_tag_icon);
        batteryLevelText = findViewById(R.id.battery_level);
        deviceAddressText = findViewById(R.id.device_address);
        updateTimeText = findViewById(R.id.update_time);
        bottomInfo = findViewById(R.id.bottom_info);
        locateBtn = findViewById(R.id.locate_btn);
        mapTypeBtn = findViewById(R.id.map_type_btn);
        refreshBtn = findViewById(R.id.refresh_btn);
        trackBtn = findViewById(R.id.track_btn);
        bottomInfo.setVisibility(View.GONE);

        initMap();
        initLocation();
        initBLE();
        initCrowdSourcing();
        initDatabase();
        initApiService();
        
        // 清理旧的轨迹数据（使用10米阈值之前的数据）
        cleanOldTrackData(false);

        checkPermissions();

        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 显示菜单选项
                showMenuOptions();
            }
        });

        buzzerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerBuzzer();
            }
        });

        locateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedDevice != null && selectedDevice.getLatitude() != 0 && selectedDevice.getLongitude() != 0) {
                    // 使用原始WGS84坐标，MapManager会自动处理坐标系转换
                    mapManager.moveCamera(selectedDevice.getLatitude(), selectedDevice.getLongitude(), 17);
                    Toast.makeText(MainActivity.this, R.string.back_to_location, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Locate button: moving to device at " + selectedDevice.getLatitude() + ", " + selectedDevice.getLongitude());
                } else if (currentLatitude != 0 && currentLongitude != 0) {
                    mapManager.moveCamera(currentLatitude, currentLongitude, 17);
                    Toast.makeText(MainActivity.this, R.string.back_to_location, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, R.string.device_position_unknown, Toast.LENGTH_SHORT).show();
                }
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
                startRefreshAnimation();
                refreshDeviceLocation();
            }
        });

        trackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, TrackActivity.class);
                startActivity(intent);
            }
        });

        initTrackRefresh();
        databaseHelper.cleanOldLocationRecords();

        // 设置设备名称
        updateDeviceNameWithTag(currentDeviceName, null);
        
        // 初始状态：未连接设备时显示空白
        updateBottomInfo();
        
        // 立即获取设备信息
        restoreSelectedDevice();

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

    private void stopForegroundService() {
        Intent serviceIntent = new Intent(this, BLEForegroundService.class);
        stopService(serviceIntent);
        Log.d(TAG, "Foreground service stopped");
    }
    
    private void updateBottomInfo() {
        if (bleManager != null && bleManager.isConnected()) {
        } else {
            batteryLevelText.setText(R.string.battery_level);
            deviceAddressText.setText(R.string.address);
            updateTimeText.setText(R.string.last_update);
        }
    }

    private void initMap() {
        Log.d(TAG, "Initializing map...");
        
        mapManager = new MapManager(this, mapView, googleMapFragment);
        mapManager.setCallback(new MapManager.MapCallback() {
            @Override
            public void onMapReady() {
                Log.d(TAG, "Map is ready");
            }
            
            @Override
            public void onMapClick(double latitude, double longitude) {
                Log.d(TAG, "Map clicked: " + latitude + ", " + longitude);
            }
        });
        
        Log.d(TAG, "Initializing AMap...");
        mapManager.initAmap();
        Log.d(TAG, "Initializing Google Map...");
        mapManager.initGoogleMap();
        
        if (mapManager.isAmap()) {
            aMap = mapManager.getAmap();
            if (aMap != null) {
                aMap.getUiSettings().setMyLocationButtonEnabled(true);
                aMap.setMyLocationEnabled(false);
                aMap.getUiSettings().setCompassEnabled(true);
                aMap.getUiSettings().setScaleControlsEnabled(true);
                aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.zoomTo(17));
                aMap.setOnMapLoadedListener(this);
                
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
                    }

                    @Override
                    public void onCameraChangeFinish(com.amap.api.maps.model.CameraPosition cameraPosition) {
                        lastUserInteractionTime = System.currentTimeMillis();
                        Log.d(TAG, "Camera change finished at: " + lastUserInteractionTime);
                    }
                });
                
                try {
                    geocodeSearch = new GeocodeSearch(MainActivity.this);
                    geocodeSearch.setOnGeocodeSearchListener(MainActivity.this);
                    Log.d(TAG, "GeocodeSearch initialized successfully");
                } catch (com.amap.api.services.core.AMapException e) {
                    Log.e(TAG, "Error initializing GeocodeSearch: " + e.getMessage() + ", errorCode=" + e.getErrorCode());
                    geocodeSearch = null;
                } catch (Exception e) {
                    Log.e(TAG, "Error initializing GeocodeSearch (general): " + e.getMessage());
                    geocodeSearch = null;
                }
                
                Log.d(TAG, "Map initialized successfully");
            } else {
                Log.e(TAG, "Failed to get AMap instance");
            }
        }
        
        // 延迟切换地图，确保Fragment视图已创建
        Log.d(TAG, "Scheduling map switch...");
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Executing delayed map switch, isAmap: " + mapManager.isAmap());
                if (mapManager.isAmap()) {
                    mapManager.switchToAmap();
                } else {
                    mapManager.switchToGoogleMap();
                }
            }
        }, 300);
    }

    private void initLocation() {
        try {
            locationClient = new AMapLocationClient(getApplicationContext());
            AMapLocationClientOption option = new AMapLocationClientOption();
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            option.setInterval(5000);
            locationClient.setLocationOption(option);
            locationClient.setLocationListener(this);
            locationClient.startLocation();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing location: " + e.getMessage());
            // 设置默认位置
            setDefaultLocation();
        }
    }

    private void setDefaultLocation() {
        // 默认位置：广东省深圳市新安街道高新奇科技园二期
        currentLatitude = 22.543611;
        currentLongitude = 113.881944;
        Log.d(TAG, "Set default location: " + currentLatitude + ", " + currentLongitude);
        // 更新地图显示
        if (mapManager != null) {
            mapManager.setDefaultLocation(currentLatitude, currentLongitude);
        }
        // 更新地址信息
        deviceAddressText.setText(getString(R.string.address) + getString(R.string.default_address));
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

    private void openApiTest() {
        Intent intent = new Intent(this, ApiTestActivity.class);
        startActivity(intent);
    }

    private void showMenuOptions() {
        final List<String> menuItems = new ArrayList<>();
        menuItems.add(getString(R.string.device_list));
        menuItems.add(getString(R.string.geofence_settings));
        menuItems.add(getString(R.string.switch_map));
        menuItems.add(getString(R.string.toggle_dark_mode));
        menuItems.add(getString(R.string.change_language));

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.menu_title)
                .setItems(menuItems.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                openDeviceList();
                                break;
                            case 1:
                                openGeofenceSettings();
                                break;
                            case 2:
                                showMapSwitchOptions();
                                break;
                            case 3:
                                toggleDarkMode();
                                break;
                            case 4:
                                showLanguageOptions();
                                break;
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    private void showMapSwitchOptions() {
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

    private void selectDevice(Device device) {
        Log.d(TAG, "Selecting device: " + device.getName() + " - " + device.getDeviceId());
        selectedDevice = device;
        updateDeviceNameWithTag(device.getName(), device.getTag());
        deviceNameText.setVisibility(View.VISIBLE);
        bottomInfo.setVisibility(View.VISIBLE);
        
        if (device.getLatitude() != 0 && device.getLongitude() != 0 && mapManager != null) {
            float currentZoom = mapManager.getZoomLevel();
            mapManager.moveCamera(device.getLatitude(), device.getLongitude(), currentZoom);
        }
        
        Toast.makeText(this, getString(R.string.selected_device_info, device.getName()), Toast.LENGTH_SHORT).show();
        
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("selected_device_id", device.getDeviceId());
        editor.apply();
        
        String deviceNumToFetch = device.getDeviceNum() != null ? device.getDeviceNum() : device.getDeviceId();
        fetchDeviceInfo(deviceNumToFetch);
    }
    
    private void fetchDeviceInfo(String deviceNum) {
        Log.d(TAG, "Starting fetchDeviceInfo for device: " + deviceNum);
        
        // 根据设备号长度设置对应的API URL
        NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(deviceNum));
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!apiService.isAuthenticated()) {
                        Log.d(TAG, "Not authenticated, starting registration and login");
                        
                        NewApiService.ApiResponse registerResponse = apiService.register(
                            ApiConfig.getCid(),
                            ApiConfig.getCustomerCode(),
                            ApiConfig.getPassword()
                        );
                        Log.d(TAG, "Register response - success: " + (registerResponse != null ? registerResponse.isSuccess() : "null") + 
                              ", status: " + (registerResponse != null ? registerResponse.getStatus() : "null") + 
                              ", code: " + (registerResponse != null ? registerResponse.getCode() : "null") + 
                              ", message: " + (registerResponse != null ? registerResponse.getMessage() : "null"));
                        
                        NewApiService.ApiResponse loginResponse = apiService.login(
                            ApiConfig.getCid(),
                            ApiConfig.getCustomerCode(),
                            ApiConfig.getPassword()
                        );
                        
                        Log.d(TAG, "Login response - success: " + (loginResponse != null ? loginResponse.isSuccess() : "null") + 
                              ", status: " + (loginResponse != null ? loginResponse.getStatus() : "null") + 
                              ", code: " + (loginResponse != null ? loginResponse.getCode() : "null") + 
                              ", message: " + (loginResponse != null ? loginResponse.getMessage() : "null"));
                        
                        if (loginResponse == null || !loginResponse.isSuccess()) {
                            Log.e(TAG, "Login failed for fetching device info");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, R.string.api_login_failed_no_info, Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }
                        
                        Log.d(TAG, "Login success, userId: " + apiService.getUserId());
                    } else {
                        Log.d(TAG, "Already authenticated, userId: " + apiService.getUserId());
                    }
                    
                    NewApiService.DeviceInfo deviceInfo = null;
                    int maxRetries = 3;
                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        Log.d(TAG, "Fetching device info, attempt " + attempt + "/" + maxRetries);
                        deviceInfo = apiService.getDeviceInfo(deviceNum);
                        
                        if (deviceInfo != null) {
                            Log.d(TAG, "Device info retrieved successfully on attempt " + attempt);
                            break;
                        } else {
                            Log.w(TAG, "Device info is null on attempt " + attempt);
                            if (attempt < maxRetries) {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    Log.e(TAG, "Sleep interrupted: " + e.getMessage());
                                }
                            }
                        }
                    }
                    
                    final NewApiService.DeviceInfo finalDeviceInfo = deviceInfo;
                    if (finalDeviceInfo != null) {
                        Log.d(TAG, "Device info retrieved successfully");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateDeviceUI(finalDeviceInfo);
                            }
                        });
                    } else {
                        Log.e(TAG, "Device info is null after " + maxRetries + " attempts for deviceNum: " + deviceNum);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateDeviceUIDefault();
                                Toast.makeText(MainActivity.this, R.string.cannot_get_device_info, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching device info: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, getString(R.string.get_device_info_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
    
    private void updateDeviceUI(NewApiService.DeviceInfo deviceInfo) {
        Log.d(TAG, "Updating device UI with info: " + deviceInfo.deviceNum + 
              ", battery: " + deviceInfo.battery + 
              ", lat: " + deviceInfo.latitude + 
              ", lng: " + deviceInfo.longitude + 
              ", timestamp: " + deviceInfo.timestamp +
              ", nickName: " + deviceInfo.nickName);
        
        bottomInfo.setVisibility(View.VISIBLE);
        
        if (deviceInfo.nickName != null && !deviceInfo.nickName.isEmpty() 
                && !deviceInfo.nickName.equals(deviceInfo.deviceNum)
                && !deviceInfo.nickName.matches("\\d+")) {
            updateDeviceNameWithTag(deviceInfo.nickName, selectedDevice != null ? selectedDevice.getTag() : null);
            if (selectedDevice != null) {
                selectedDevice.setName(deviceInfo.nickName);
                databaseHelper.addDevice(selectedDevice);
            }
        } else if (selectedDevice != null && selectedDevice.getName() != null) {
            updateDeviceNameWithTag(selectedDevice.getName(), selectedDevice.getTag());
        }
        
        if (deviceInfo.battery > 0) {
            batteryLevelText.setText(getString(R.string.battery_level_value, String.valueOf(deviceInfo.battery)));
        } else if (deviceInfo.battery == 0) {
            batteryLevelText.setText(getString(R.string.battery_level_zero));
        } else {
            batteryLevelText.setText(getString(R.string.battery_level_unknown));
        }
        
        if (deviceInfo.latitude != 0 && deviceInfo.longitude != 0) {
            LatLng deviceLatLng = CoordinateUtils.wgs84ToGcj02(deviceInfo.latitude, deviceInfo.longitude);
            
            Log.d(TAG, "WGS84: " + deviceInfo.latitude + ", " + deviceInfo.longitude + " -> GCJ02: " + deviceLatLng.latitude + ", " + deviceLatLng.longitude);
            
            if (deviceLocationMarker != null) {
                deviceLocationMarker.remove();
                deviceLocationMarker = null;
            }
            if (aMap != null) {
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(deviceLatLng)
                        .title(selectedDevice != null ? selectedDevice.getName() : getString(R.string.device))
                        .snippet(getString(R.string.device_location))
                        .icon(createCustomMarkerWithR());
                deviceLocationMarker = aMap.addMarker(markerOptions);
            }
            
            if (mapManager != null) {
                mapManager.moveCamera(deviceInfo.latitude, deviceInfo.longitude, 17);
                Log.d(TAG, "Moving map to device location with zoom 17: " + deviceInfo.latitude + ", " + deviceInfo.longitude);
            }
            
            if (selectedDevice != null) {
                selectedDevice.setLatitude(deviceInfo.latitude);
                selectedDevice.setLongitude(deviceInfo.longitude);
                if (deviceInfo.timestamp > 0) {
                    selectedDevice.setLastSeen(deviceInfo.timestamp);
                }
                databaseHelper.addDevice(selectedDevice);
                Log.d(TAG, "Updated device: lat=" + deviceInfo.latitude + 
                      ", lng=" + deviceInfo.longitude + 
                      ", lastSeen=" + deviceInfo.timestamp);
            }
            
            deviceAddressText.setText(getString(R.string.position_getting_address));
            getAddressFromLocation(deviceInfo.latitude, deviceInfo.longitude);
        } else {
            deviceAddressText.setText(getString(R.string.position_not_reported));
            Log.d(TAG, "Device location is (0,0), device may not have reported location yet");
        }
        
        if (deviceInfo.timestamp > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            updateTimeText.setText(getString(R.string.last_update_with_time, sdf.format(new Date(deviceInfo.timestamp))));
        } else {
            updateTimeText.setText(getString(R.string.last_update_not_reported));
        }
        
    }

    private void updateDeviceUIWithLatest(NewApiService.DeviceInfo deviceInfo) {
        Log.d(TAG, "Updating device UI with latest info: " + deviceInfo.deviceNum + 
              ", battery: " + deviceInfo.battery + 
              ", lat: " + deviceInfo.latitude + 
              ", lng: " + deviceInfo.longitude + 
              ", timestamp: " + deviceInfo.timestamp +
              ", address: " + deviceInfo.address +
              ", updatedAt: " + deviceInfo.updatedAt);
        
        bottomInfo.setVisibility(View.VISIBLE);
        
        if (deviceInfo.nickName != null && !deviceInfo.nickName.isEmpty() 
                && !deviceInfo.nickName.equals(deviceInfo.deviceNum)
                && !deviceInfo.nickName.matches("\\d+")) {
            updateDeviceNameWithTag(deviceInfo.nickName, selectedDevice != null ? selectedDevice.getTag() : null);
            if (selectedDevice != null) {
                selectedDevice.setName(deviceInfo.nickName);
                databaseHelper.addDevice(selectedDevice);
            }
        } else if (selectedDevice != null && selectedDevice.getName() != null) {
            updateDeviceNameWithTag(selectedDevice.getName(), selectedDevice.getTag());
        }
        
        if (deviceInfo.battery > 0) {
            batteryLevelText.setText(getString(R.string.battery_level_value, String.valueOf(deviceInfo.battery)));
        } else if (deviceInfo.battery == 0) {
            batteryLevelText.setText(getString(R.string.battery_level_zero));
        } else {
            batteryLevelText.setText(getString(R.string.battery_level_unknown));
        }
        
        if (deviceInfo.latitude != 0 && deviceInfo.longitude != 0) {
            LatLng deviceLatLng = CoordinateUtils.wgs84ToGcj02(deviceInfo.latitude, deviceInfo.longitude);
            
            Log.d(TAG, "WGS84: " + deviceInfo.latitude + ", " + deviceInfo.longitude + " -> GCJ02: " + deviceLatLng.latitude + ", " + deviceLatLng.longitude);
            
            if (deviceLocationMarker != null) {
                deviceLocationMarker.remove();
                deviceLocationMarker = null;
            }
            if (aMap != null) {
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(deviceLatLng)
                        .title(selectedDevice != null ? selectedDevice.getName() : getString(R.string.device))
                        .snippet(getString(R.string.device_location))
                        .icon(createCustomMarkerWithR());
                deviceLocationMarker = aMap.addMarker(markerOptions);
            }
            
            if (mapManager != null) {
                mapManager.moveCamera(deviceInfo.latitude, deviceInfo.longitude, 17);
                Log.d(TAG, "Moving map to device location with zoom 17: " + deviceInfo.latitude + ", " + deviceInfo.longitude);
            }
            
            if (selectedDevice != null) {
                selectedDevice.setLatitude(deviceInfo.latitude);
                selectedDevice.setLongitude(deviceInfo.longitude);
                if (deviceInfo.timestamp > 0) {
                    selectedDevice.setLastSeen(deviceInfo.timestamp);
                }
                databaseHelper.addDevice(selectedDevice);
                Log.d(TAG, "Updated device: lat=" + deviceInfo.latitude + 
                      ", lng=" + deviceInfo.longitude + 
                      ", lastSeen=" + deviceInfo.timestamp);
            }
            
            if (deviceInfo.address != null && !deviceInfo.address.isEmpty()) {
                deviceAddressText.setText(getString(R.string.position_with_address, deviceInfo.address));
            } else {
                deviceAddressText.setText(getString(R.string.position_getting_address));
                getAddressFromLocation(deviceInfo.latitude, deviceInfo.longitude);
            }
        } else {
            deviceAddressText.setText(getString(R.string.position_not_reported));
            Log.d(TAG, "Device location is (0,0), device may not have reported location yet");
        }
        
        if (deviceInfo.timestamp > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            updateTimeText.setText(getString(R.string.last_update_with_time, sdf.format(new Date(deviceInfo.timestamp))));
        } else if (deviceInfo.updatedAt != null && !deviceInfo.updatedAt.isEmpty()) {
            updateTimeText.setText(getString(R.string.last_update_with_time, deviceInfo.updatedAt));
        } else {
            updateTimeText.setText(getString(R.string.last_update_not_reported));
        }
    }
    
    private void updateDeviceUIDefault() {
        Log.d(TAG, "Updating device UI with default values");
        bottomInfo.setVisibility(View.VISIBLE);
        batteryLevelText.setText(getString(R.string.battery_level_unknown));
        deviceAddressText.setText(getString(R.string.position_not_reported));
        updateTimeText.setText(getString(R.string.last_update_not_reported));
    }
    
    private double lastGeocodeLat = 0;
    private double lastGeocodeLng = 0;
    
    private void getAddressFromLocation(double latitude, double longitude) {
        Log.d(TAG, "=== getAddressFromLocation START ===");
        Log.d(TAG, "WGS84 coords: " + latitude + ", " + longitude);
        Log.d(TAG, "geocodeSearch is null: " + (geocodeSearch == null));
        
        // 保存原始坐标，用于逆地理编码失败时显示
        lastGeocodeLat = latitude;
        lastGeocodeLng = longitude;
        
        if (geocodeSearch == null) {
            Log.e(TAG, "geocodeSearch is null, showing coordinates only");
            deviceAddressText.setText(getString(R.string.position_with_address, String.format("%.6f", latitude) + ", " + String.format("%.6f", longitude)));
            return;
        }
        
        // 在后台线程中执行逆地理编码
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Creating LatLonPoint...");
                    // 使用 WGS84 坐标系（GPS坐标）
                    com.amap.api.services.core.LatLonPoint latLonPoint = new com.amap.api.services.core.LatLonPoint(latitude, longitude);
                    Log.d(TAG, "LatLonPoint created: " + latLonPoint.getLatitude() + ", " + latLonPoint.getLongitude());
                    
                    Log.d(TAG, "Creating RegeocodeQuery...");
                    // 第三个参数设置坐标系类型：GeocodeSearch.GPS 表示 WGS84 坐标系
                    RegeocodeQuery query = new RegeocodeQuery(latLonPoint, 200, GeocodeSearch.GPS);
                    
                    Log.d(TAG, "Calling getFromLocation...");
                    // 使用同步方法
                    RegeocodeAddress regeocodeAddress = geocodeSearch.getFromLocation(query);
                    
                    Log.d(TAG, "getFromLocation returned, regeocodeAddress is null: " + (regeocodeAddress == null));
                    
                    if (regeocodeAddress != null) {
                        String formatAddress = regeocodeAddress.getFormatAddress();
                        Log.d(TAG, "Format address: " + formatAddress);
                        
                        String province = regeocodeAddress.getProvince();
                        String city = regeocodeAddress.getCity();
                        String district = regeocodeAddress.getDistrict();
                        String township = regeocodeAddress.getTownship();
                        String streetName = null;
                        String streetNum = null;
                        if (regeocodeAddress.getStreetNumber() != null) {
                            streetName = regeocodeAddress.getStreetNumber().getStreet();
                            streetNum = regeocodeAddress.getStreetNumber().getNumber();
                        }
                        
                        // 获取社区/小区信息
                        String neighborhood = regeocodeAddress.getNeighborhood();
                        
                        // 获取AOI信息（区域）
                        String aoiName = null;
                        if (regeocodeAddress.getAois() != null && !regeocodeAddress.getAois().isEmpty()) {
                            aoiName = regeocodeAddress.getAois().get(0).getAoiName();
                        }
                        
                        // 获取最近的POI信息
                        String poiName = null;
                        java.util.List<com.amap.api.services.core.PoiItem> pois = regeocodeAddress.getPois();
                        if (pois != null && !pois.isEmpty()) {
                            poiName = pois.get(0).getTitle();
                        }
                        
                        Log.d(TAG, "Province: " + province + ", City: " + city + 
                              ", District: " + district + ", Township: " + township + 
                              ", Street: " + streetName + ", Number: " + streetNum +
                              ", Neighborhood: " + neighborhood + ", AOI: " + aoiName +
                              ", POI: " + poiName);
                        
                        // 构建详细地址
                        StringBuilder sb = new StringBuilder();
                        if (province != null && !province.isEmpty()) {
                            sb.append(province);
                        }
                        if (city != null && !city.isEmpty() && !city.equals(province)) {
                            sb.append(city);
                        }
                        if (district != null && !district.isEmpty()) {
                            sb.append(district);
                        }
                        if (township != null && !township.isEmpty()) {
                            sb.append(township);
                        }
                        if (streetName != null && !streetName.isEmpty()) {
                            sb.append(streetName);
                        }
                        if (streetNum != null && !streetNum.isEmpty()) {
                            sb.append(streetNum);
                        }
                        if (neighborhood != null && !neighborhood.isEmpty()) {
                            sb.append(neighborhood);
                        }
                        if (aoiName != null && !aoiName.isEmpty()) {
                            sb.append(aoiName);
                        }
                        if (poiName != null && !poiName.isEmpty() && !poiName.equals(neighborhood) && !poiName.equals(aoiName)) {
                            sb.append(poiName);
                        }
                        
                        String simpleAddress;
                        if (sb.length() > 0) {
                            simpleAddress = sb.toString();
                        } else {
                            simpleAddress = formatAddress;
                        }
                        
                        Log.d(TAG, "Final simple address: " + simpleAddress);
                        
                        final String finalAddress = simpleAddress;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                deviceAddressText.setText(getString(R.string.position_with_address, finalAddress));
                                Log.d(TAG, "UI updated with address: " + finalAddress);
                            }
                        });
                    } else {
                        Log.e(TAG, "regeocodeAddress is null");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                deviceAddressText.setText(getString(R.string.position_with_address, String.format("%.6f", lastGeocodeLat) + ", " + String.format("%.6f", lastGeocodeLng)));
                            }
                        });
                    }
                } catch (com.amap.api.services.core.AMapException e) {
                    Log.e(TAG, "AMapException getting address: code=" + e.getErrorCode() + ", message=" + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            deviceAddressText.setText(getString(R.string.position_with_address, String.format("%.6f", lastGeocodeLat) + ", " + String.format("%.6f", lastGeocodeLng)));
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Exception getting address: " + e.getClass().getName() + " - " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            deviceAddressText.setText(getString(R.string.position_with_address, String.format("%.6f", lastGeocodeLat) + ", " + String.format("%.6f", lastGeocodeLng)));
                        }
                    });
                }
            }
        }).start();
    }
    
    @Override
    public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int rCode) {
        Log.d(TAG, "onRegeocodeSearched called with rCode: " + rCode);
        
        if (rCode == 1000) {
            if (regeocodeResult != null && regeocodeResult.getRegeocodeAddress() != null) {
                RegeocodeAddress regeocodeAddress = regeocodeResult.getRegeocodeAddress();
                String formatAddress = regeocodeAddress.getFormatAddress();
                Log.d(TAG, "Format address: " + formatAddress);
                
                String simpleAddress = formatAddress;
                
                String province = regeocodeAddress.getProvince();
                String city = regeocodeAddress.getCity();
                String district = regeocodeAddress.getDistrict();
                String township = regeocodeAddress.getTownship();
                String streetNumber = null;
                if (regeocodeAddress.getStreetNumber() != null) {
                    streetNumber = regeocodeAddress.getStreetNumber().getStreet();
                }
                
                Log.d(TAG, "Province: " + province + ", City: " + city + 
                      ", District: " + district + ", Township: " + township + 
                      ", Street: " + streetNumber);
                
                // 构建详细地址
                StringBuilder sb = new StringBuilder();
                if (province != null && !province.isEmpty()) {
                    sb.append(province);
                }
                if (city != null && !city.isEmpty() && !city.equals(province)) {
                    sb.append(city);
                }
                if (district != null && !district.isEmpty()) {
                    sb.append(district);
                }
                if (township != null && !township.isEmpty()) {
                    sb.append(township);
                }
                if (streetNumber != null && !streetNumber.isEmpty()) {
                    sb.append(streetNumber);
                }
                
                if (sb.length() > 0) {
                    sb.append(getString(R.string.nearby));
                    simpleAddress = sb.toString();
                }
                
                Log.d(TAG, "Simple address: " + simpleAddress);
                
                final String finalAddress = simpleAddress;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        deviceAddressText.setText(getString(R.string.position_with_address, finalAddress));
                    }
                });
            } else {
                Log.e(TAG, "regeocodeResult or regeocodeAddress is null");
                // 显示原始坐标
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        deviceAddressText.setText(getString(R.string.position_with_address, String.format("%.6f", lastGeocodeLat) + ", " + String.format("%.6f", lastGeocodeLng)));
                    }
                });
            }
        } else {
            Log.e(TAG, "Regeocode search failed with rCode: " + rCode);
            // 显示原始坐标
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    deviceAddressText.setText(getString(R.string.position_with_address, String.format("%.6f", lastGeocodeLat) + ", " + String.format("%.6f", lastGeocodeLng)));
                }
            });
        }
    }
    
    @Override
    public void onGeocodeSearched(GeocodeResult geocodeResult, int rCode) {
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

    private void showLanguageOptions() {
        final List<LanguageItem> languages = new ArrayList<>();
        languages.add(new LanguageItem("\uD83C\uDDE8\uD83C\uDDF3", getString(R.string.chinese), "zh"));
        languages.add(new LanguageItem("\uD83C\uDDEC\uD83C\uDDE7", "English", "en"));
        languages.add(new LanguageItem("\uD83C\uDDE7\uD83C\uDDF7", "Português", "pt-rBR"));
        languages.add(new LanguageItem("\uD83C\uDDF7\uD83C\uDDFA", "Русский", "ru"));
        languages.add(new LanguageItem("\uD83C\uDDEE\uD83C\uDDF3", "हिंदी", "hi"));

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

    private void changeLanguage(String languageCode) {
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
        if (bleManager.isConnected()) {
            bleManager.controlBuzzer(true);
            Toast.makeText(this, R.string.trigger_buzzer, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.device_not_connected, Toast.LENGTH_SHORT).show();
        }
    }

    private void startRefreshAnimation() {
        android.view.animation.Animation rotateAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rotate_refresh);
        refreshBtn.startAnimation(rotateAnimation);
    }

    private void stopRefreshAnimation() {
        refreshBtn.clearAnimation();
    }

    private void refreshDeviceLocation() {
        if (selectedDevice == null) {
            stopRefreshAnimation();
            Toast.makeText(this, R.string.please_select_device, Toast.LENGTH_SHORT).show();
            return;
        }
        
        final String deviceNum = selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
        
        // 根据设备号长度设置对应的API URL
        NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(deviceNum));
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!apiService.isAuthenticated()) {
                        NewApiService.ApiResponse registerResponse = apiService.register(
                            "6h7lMJOVpVOld5R9CApqH6coCR1W8iqL",
                            "XHD_HSWL_API",
                            "123456"
                        );
                        
                        NewApiService.ApiResponse loginResponse = apiService.login(
                            "6h7lMJOVpVOld5R9CApqH6coCR1W8iqL",
                            "XHD_HSWL_API",
                            "123456"
                        );
                        
                        if (loginResponse == null || !loginResponse.isSuccess()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    stopRefreshAnimation();
                                    Toast.makeText(MainActivity.this, R.string.api_login_failed, Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }
                    }
                    
                    NewApiService.ApiResponse syncResponse = apiService.syncDevice(deviceNum);
                    Log.d(TAG, "Sync device response: " + (syncResponse != null ? syncResponse.isSuccess() : "null"));
                    
                    if (syncResponse != null && syncResponse.isSuccess()) {
                        NewApiService.DeviceInfo latestInfo = apiService.getDeviceLatest(deviceNum);
                        
                        final NewApiService.DeviceInfo finalLatestInfo = latestInfo;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                stopRefreshAnimation();
                                if (finalLatestInfo != null) {
                                    Toast.makeText(MainActivity.this, R.string.data_sync_success, Toast.LENGTH_SHORT).show();
                                    updateDeviceUIWithLatest(finalLatestInfo);
                                } else {
                                    Toast.makeText(MainActivity.this, R.string.data_sync_success_no_info, Toast.LENGTH_SHORT).show();
                                    fetchDeviceInfo(deviceNum);
                                }
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                stopRefreshAnimation();
                                String message = getString(R.string.sync_failed);
                                if (syncResponse != null && syncResponse.getRawResponse() != null) {
                                    try {
                                        com.google.gson.JsonObject json = new com.google.gson.JsonParser().parse(syncResponse.getRawResponse()).getAsJsonObject();
                                        if (json.has("message")) {
                                            message = json.get("message").getAsString();
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error parsing sync response: " + e.getMessage());
                                    }
                                }
                                Toast.makeText(MainActivity.this, getString(R.string.refresh_failed, message), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing device location: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopRefreshAnimation();
                            Toast.makeText(MainActivity.this, getString(R.string.refresh_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
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
                    getString(R.string.last_update_with_time, new Date(device.getLastSeen()).toString()));
        } else {
            // 创建新标记
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title(device.getName())
                    .snippet(getString(R.string.signal_strength, device.getSignalStrength()) + "\n" +
                            getString(R.string.last_update_with_time, new Date(device.getLastSeen()).toString()))
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

    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation != null) {
            if (aMapLocation.getErrorCode() == 0) {
                currentLatitude = aMapLocation.getLatitude();
                currentLongitude = aMapLocation.getLongitude();
                Log.d(TAG, "Phone location: " + currentLatitude + ", " + currentLongitude);

                // 创建GPS位置对象
                android.location.Location gpsLocation = new android.location.Location("gps");
                gpsLocation.setLatitude(currentLatitude);
                gpsLocation.setLongitude(currentLongitude);
                gpsLocation.setAccuracy(aMapLocation.getAccuracy());
                gpsLocation.setTime(aMapLocation.getTime());

                // 不再显示手机定位标记，只保留设备位置标记

                // 只有在没有选中设备时，才自动移动到手机当前位置
                // 如果有选中设备，地图应该显示设备位置，而不是手机位置
                if (selectedDevice == null) {
                    Log.d(TAG, "No device selected, moving map to phone location");
                    // 第一次定位时，立即移动到当前位置，使用固定缩放级别17
                    if (isFirstLocation && mapManager != null) {
                        mapManager.moveCamera(currentLatitude, currentLongitude, 17);
                        isFirstLocation = false;
                        Log.d(TAG, "First location, moving to current position with zoom 17");
                    } else {
                        // 检查是否需要自动回到当前位置
                        long currentTime = System.currentTimeMillis();
                        if (lastUserInteractionTime == 0 || (currentTime - lastUserInteractionTime) > AUTO_RETURN_DELAY) {
                            // 如果用户没有操作过地图，或者距离上次操作超过10秒，则自动回到当前位置
                            if (mapManager != null) {
                                mapManager.moveCamera(currentLatitude, currentLongitude, 17);
                                Log.d(TAG, "Auto returning to current location with zoom 17");
                            }
                        } else {
                            // 用户在10秒内有操作，不自动移动地图
                            Log.d(TAG, "User recently interacted, not returning to location");
                        }
                    }
                } else {
                    Log.d(TAG, "Device selected, not moving map to phone location. Device: " + selectedDevice.getName());
                }

                // 请求附近的设备
                crowdSourcingManager.requestNearbyDevices(currentLatitude, currentLongitude, 5000);
            } else {
                Log.e(TAG, "Location error: " + aMapLocation.getErrorCode() + " - " + aMapLocation.getErrorInfo());
            }
        }
    }
    
    private void updateCurrentLocationMarker() {
        if (aMap == null) {
            return;
        }
        
        LatLng latLng = new LatLng(currentLatitude, currentLongitude);
        
        if (currentLocationMarker == null) {
            // 创建自定义的带R图标的标记
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .icon(createCustomMarkerWithR());
            currentLocationMarker = aMap.addMarker(markerOptions);
        } else {
            // 更新现有标记位置
            currentLocationMarker.setPosition(latLng);
        }
    }
    
    private com.amap.api.maps.model.BitmapDescriptor createCustomMarkerWithR() {
        try {
            // 获取高德地图默认的紫色水滴标记
            android.graphics.Bitmap markerBitmap = com.amap.api.maps.model.BitmapDescriptorFactory.defaultMarker(com.amap.api.maps.model.BitmapDescriptorFactory.HUE_MAGENTA).getBitmap();
            
            // 加大标记尺寸1.5倍
            float scaleFactor = 1.5f;
            int width = (int) (markerBitmap.getWidth() * scaleFactor);
            int height = (int) (markerBitmap.getHeight() * scaleFactor);
            
            // 创建缩放后的Bitmap
            android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(markerBitmap, width, height, true);
            android.graphics.Bitmap resultBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            
            // 创建Canvas并绘制标记
            android.graphics.Canvas canvas = new android.graphics.Canvas(resultBitmap);
            canvas.drawBitmap(scaledBitmap, 0, 0, null);
            
            // 绘制更大的R文字在标记中间
            android.graphics.Paint textPaint = new android.graphics.Paint();
            textPaint.setColor(0xFFFFFFFF); // 白色
            textPaint.setTextSize(width / 1.8f); // 加大R字，与标记尺寸成正比
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            textPaint.setAntiAlias(true);
            
            String text = "R";
            float x = width / 2f;
            float y = height / 2.5f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(text, x, y, textPaint);
            
            return com.amap.api.maps.model.BitmapDescriptorFactory.fromBitmap(resultBitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating custom marker: " + e.getMessage());
            // 如果创建失败，使用默认的紫色标记
            return com.amap.api.maps.model.BitmapDescriptorFactory.defaultMarker(com.amap.api.maps.model.BitmapDescriptorFactory.HUE_MAGENTA);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEVICE_LIST && resultCode == RESULT_OK) {
            Log.d(TAG, "Device list updated, refreshing device info");
            restoreSelectedDevice();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        startTrackRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        stopTrackRefresh();
    }

    private void initTrackRefresh() {
        trackRefreshHandler = new Handler();
        trackRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                refreshAndRecordLocation();
                trackRefreshHandler.postDelayed(this, TRACK_REFRESH_INTERVAL);
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
        
        // 根据设备号长度设置对应的API URL
        NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(deviceNum));
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!apiService.isAuthenticated()) {
                        NewApiService.ApiResponse registerResponse = apiService.register(
                            "6h7lMJOVpVOld5R9CApqH6coCR1W8iqL",
                            "XHD_HSWL_API",
                            "123456"
                        );
                        
                        NewApiService.ApiResponse loginResponse = apiService.login(
                            "6h7lMJOVpVOld5R9CApqH6coCR1W8iqL",
                            "XHD_HSWL_API",
                            "123456"
                        );
                        
                        if (loginResponse == null || !loginResponse.isSuccess()) {
                            return;
                        }
                    }
                    
                    NewApiService.ApiResponse syncResponse = apiService.syncDevice(deviceNum);
                    Log.d(TAG, "Auto refresh - sync device response: " + (syncResponse != null ? syncResponse.isSuccess() : "null"));
                    
                    NewApiService.DeviceInfo deviceInfo = null;
                    if (syncResponse != null && syncResponse.isSuccess()) {
                        deviceInfo = apiService.getDeviceLatest(deviceNum);
                        Log.d(TAG, "Auto refresh - got latest device info: " + (deviceInfo != null ? "yes" : "null"));
                    }
                    
                    if (deviceInfo == null) {
                        deviceInfo = apiService.getDeviceInfo(deviceNum);
                        Log.d(TAG, "Auto refresh - fallback to getDeviceInfo: " + (deviceInfo != null ? "yes" : "null"));
                    }
                    
                    if (deviceInfo != null) {
                        final NewApiService.DeviceInfo finalDeviceInfo = deviceInfo;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateDeviceUIWithLatest(finalDeviceInfo);
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
                    selectedDevice = device;
                    updateDeviceNameWithTag(device.getName(), device.getTag());
                    deviceNameText.setVisibility(View.VISIBLE);
                    bottomInfo.setVisibility(View.VISIBLE);
                    
                    // 先显示默认的设备信息
                    updateDeviceUIDefault();
                    
                    if (device.getLatitude() != 0 && device.getLongitude() != 0 && aMap != null) {
                        LatLng deviceLatLng = CoordinateUtils.wgs84ToGcj02(device.getLatitude(), device.getLongitude());
                        aMap.animateCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(deviceLatLng, 17), 1000, null);
                        Toast.makeText(MainActivity.this, R.string.back_to_location, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Auto-animating to device location on startup: " + deviceLatLng.latitude + ", " + deviceLatLng.longitude);
                    }
                    
                    // 从数据库中读取最后的轨迹记录来初始化状态
                    initLastRecordedStateFromDb(selectedDeviceId);
                    
                    String deviceNumToFetch = device.getDeviceNum() != null ? device.getDeviceNum() : device.getDeviceId();
                    Log.d(TAG, "Calling fetchDeviceInfo for: " + deviceNumToFetch);
                    fetchDeviceInfo(deviceNumToFetch);
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
        deviceNameText.setVisibility(View.VISIBLE);
        bottomInfo.setVisibility(View.VISIBLE);
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
                    
                    if (!apiService.isAuthenticated()) {
                        Log.d(TAG, "Not authenticated, trying to login directly...");
                        
                        // 先尝试直接登录
                        NewApiService.ApiResponse loginResponse = apiService.login(
                            "6h7lMJOVpVOld5R9CApqH6coCR1W8iqL",
                            "XHD_HSWL_API",
                            "123456"
                        );
                        
                        // 如果登录失败，再尝试注册
                        if (loginResponse == null || !loginResponse.isSuccess()) {
                            Log.d(TAG, "Login failed, trying to register first...");
                            
                            NewApiService.ApiResponse registerResponse = apiService.register(
                                "6h7lMJOVpVOld5R9CApqH6coCR1W8iqL",
                                "XHD_HSWL_API",
                                "123456"
                            );
                            Log.d(TAG, "Register response: " + (registerResponse != null ? registerResponse.isSuccess() : "null"));
                            
                            // 注册后再次登录
                            loginResponse = apiService.login(
                                "6h7lMJOVpVOld5R9CApqH6coCR1W8iqL",
                                "XHD_HSWL_API",
                                "123456"
                            );
                        }
                        
                        if (loginResponse == null || !loginResponse.isSuccess()) {
                            Log.e(TAG, "Login failed");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, R.string.api_login_failed, Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }
                        
                        Log.d(TAG, "Login success, userId: " + apiService.getUserId());
                    }
                    
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
                                Toast.makeText(MainActivity.this, R.string.no_bound_devices_found, Toast.LENGTH_SHORT).show();
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
                                if (info.nickName != null && !info.nickName.isEmpty()) {
                                    existingDevice.setName(info.nickName);
                                }
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
                                Toast.makeText(MainActivity.this, R.string.all_devices_unbound, Toast.LENGTH_SHORT).show();
                            } else {
                                clearDeviceInfo();
                                Toast.makeText(MainActivity.this, R.string.no_bound_devices_found, Toast.LENGTH_SHORT).show();
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
        
        selectedDevice = firstDevice;
        updateDeviceNameWithTag(firstDevice.getName(), firstDevice.getTag());
        deviceNameText.setVisibility(View.VISIBLE);
        bottomInfo.setVisibility(View.VISIBLE);
        
        // 先显示默认的设备信息
        updateDeviceUIDefault();
        
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("selected_device_id", firstDevice.getDeviceId());
        editor.apply();
        
        if (firstDevice.getLatitude() != 0 && firstDevice.getLongitude() != 0 && aMap != null) {
            LatLng deviceLatLng = CoordinateUtils.wgs84ToGcj02(firstDevice.getLatitude(), firstDevice.getLongitude());
            aMap.animateCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(deviceLatLng, 17), 1000, null);
            Toast.makeText(MainActivity.this, R.string.back_to_location, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Auto-animating to first device location on startup: " + deviceLatLng.latitude + ", " + deviceLatLng.longitude);
        }
        
        String deviceNumToFetch = firstDevice.getDeviceNum() != null ? firstDevice.getDeviceNum() : firstDevice.getDeviceId();
        Log.d(TAG, "Calling fetchDeviceInfo for: " + deviceNumToFetch);
        fetchDeviceInfo(deviceNumToFetch);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        if (locationClient != null) {
            locationClient.stopLocation();
            locationClient.onDestroy();
        }
        if (bleManager != null) {
            bleManager.stopScanning();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
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

    @Override
    public void onMapLoaded() {
        Log.d(TAG, "Map loaded successfully");
        Toast.makeText(this, R.string.map_loaded, Toast.LENGTH_SHORT).show();
        bottomInfo.setVisibility(View.VISIBLE);
        
        if (selectedDevice != null && selectedDevice.getLatitude() != 0 && selectedDevice.getLongitude() != 0 && aMap != null) {
            LatLng deviceLatLng = CoordinateUtils.wgs84ToGcj02(selectedDevice.getLatitude(), selectedDevice.getLongitude());
            aMap.animateCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(deviceLatLng, 17), 500, null);
            Toast.makeText(MainActivity.this, R.string.back_to_location, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Map loaded, auto moving to device location: " + deviceLatLng.latitude + ", " + deviceLatLng.longitude);
        } else if (isFirstLocation && currentLatitude != 0 && currentLongitude != 0 && aMap != null) {
            LatLng currentLatLng = CoordinateUtils.wgs84ToGcj02(currentLatitude, currentLongitude);
            aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(currentLatLng, 17));
            isFirstLocation = false;
            Log.d(TAG, "Map loaded, moving to current position with zoom 17");
        }
    }
}