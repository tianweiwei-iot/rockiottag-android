package com.RockiotTag.tag.integration;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.bluetooth.OptimizedBLEScanner;
import com.RockiotTag.tag.location.PhoneLocationService;
import com.RockiotTag.tag.model.DeviceLocation;
import com.RockiotTag.tag.model.PhoneLocation;
import com.RockiotTag.tag.provider.LocationProvider;
import com.RockiotTag.tag.ui.TimeRefreshManager;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.util.ToastHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 定位优化集成管理器
 * 负责将新的定位逻辑集成到 MainActivity
 */
public class LocationOptimizationManager {
    
    private static final String TAG = "LocationOptimization";
    
    private Context context;
    private WeakReference<Context> activityContextRef; // 使用 WeakReference 防止 Activity 泄漏
    private LocationProvider locationProvider;
    private OptimizedBLEScanner bluetoothScanner;
    private PhoneLocationService phoneLocationService;
    private TimeRefreshManager timeRefreshManager;
    private DatabaseHelper databaseHelper;
    private NewApiService apiService;
    private android.os.Handler mainHandler; // 用于在主线程执行回调

    // 拆分出的职责委托对象
    private LocationSyncManager locationSyncManager;
    private OfflineCacheManager offlineCacheManager;
    private DeviceMacMapper deviceMacMapper;

    // 是否启用优化
    private volatile boolean optimizationEnabled = true;

    // 调试模式：是否显示扫描Toast提示
    private volatile boolean debugMode = true;

    // 使用同步集合防止并发修改异常
    private final Set<String> boundDeviceIds = java.util.Collections.synchronizedSet(new HashSet<>()); // MAC地址列表
    private final List<String> boundDeviceNames = java.util.Collections.synchronizedList(new ArrayList<>());
    
    // 位置更新回调（可选，用于通知UI更新）
    private LocationUpdateCallback externalCallback;
    
    // 扫描状态回调（可选，用于通知UI扫描状态）
    private ScanStateCallback scanStateCallback;
    
    // 当前选中的设备ID（用于过滤Toast提醒）
    private String currentSelectedDeviceId = null;
    
    public interface LocationUpdateCallback {
        void onLocationUpdated(DeviceLocation location);
        void onError(String error);
    }
    
    /**
     * 扫描状态回调接口
     */
    public interface ScanStateCallback {
        void onScanStarted();      // 扫描开始
        void onScanStopped();      // 扫描停止（休息中）
        void onScanSuccess();      // 扫描成功（发现匹配设备并更新UI）
    }
    
    public LocationOptimizationManager(Context context, DatabaseHelper databaseHelper) {
        this.context = context.getApplicationContext(); // 使用 ApplicationContext 防止泄漏
        this.activityContextRef = new WeakReference<>(context); // WeakReference 防止 Activity 泄漏
        this.databaseHelper = databaseHelper;
        this.timeRefreshManager = new TimeRefreshManager();
        this.mainHandler = new android.os.Handler(android.os.Looper.getMainLooper()); // 初始化主线程Handler

        // 初始化拆分出的职责对象
        this.deviceMacMapper = new DeviceMacMapper(databaseHelper, null);
        this.offlineCacheManager = new OfflineCacheManager(databaseHelper);
        this.locationSyncManager = null; // 在 initializeServices() 中 apiService 就绪后创建

        // 初始化服务
        initializeServices();
    }
    
    /**
     * 设置外部回调（用于通知UI更新）
     */
    public void setLocationUpdateCallback(LocationUpdateCallback callback) {
        this.externalCallback = callback;
    }
    
    /**
     * 设置扫描状态回调（用于显示扫描图标）
     */
    public void setScanStateCallback(ScanStateCallback callback) {
        this.scanStateCallback = callback;
    }
    
    /**
     * 设置当前选中的设备ID（用于过滤Toast提醒）
     * @param deviceId 当前选中的设备ID（MAC地址）
     */
    public void setCurrentSelectedDeviceId(String deviceId) {
        this.currentSelectedDeviceId = deviceId;
        LogUtil.d(TAG, "Current selected device ID set to: " + deviceId);
    }
    
    /**
     * 在主线程显示Toast提示
     */
    private void runToast(String message) {
        Context actCtx = activityContextRef != null ? activityContextRef.get() : null;
        if (actCtx != null && mainHandler != null) {
            mainHandler.post(() -> {
                try {
                    ToastHelper.show(actCtx, message);
                } catch (Exception e) {
                    Log.e(TAG, "Error showing toast", e);
                }
            });
        }
    }
    
    /**
     * 获取当前选中的设备ID
     */
    public String getCurrentSelectedDeviceId() {
        return currentSelectedDeviceId;
    }
    
    /**
     * 自动选择第一个已绑定设备（如果当前没有选中设备）
     */
    public void autoSelectFirstDevice() {
        if (currentSelectedDeviceId != null && !currentSelectedDeviceId.isEmpty()) {
            LogUtil.d(TAG, "Already have selected device: " + currentSelectedDeviceId);
            return;
        }
        
        if (!boundDeviceIds.isEmpty()) {
            // 选择第一个设备
            String firstMac = boundDeviceIds.iterator().next();
            setCurrentSelectedDeviceId(firstMac);
            LogUtil.d(TAG, "Auto-selected first device: " + firstMac);
            
            // 同时设置对应的deviceNum（委托给 DeviceMacMapper）
            if (deviceMacMapper != null) {
                String deviceNum = deviceMacMapper.getDeviceNumByMac(firstMac);
                if (deviceNum != null) {
                    LogUtil.d(TAG, "  -> deviceNum: " + deviceNum);
                }
            }
        } else {
            Log.w(TAG, "No bound devices available for auto-selection");
        }
    }
    
    /**
     * 初始化所有服务
     */
    private void initializeServices() {
        LogUtil.d(TAG, "=== Initializing Location Optimization Services ===");
        try {
            // 获取已绑定设备列表
            LogUtil.d(TAG, "Step 1: Getting bound device list...");
            getBoundDeviceIds();
            LogUtil.d(TAG, "Found " + boundDeviceIds.size() + " bound devices, " + boundDeviceNames.size() + " device names");
            LogUtil.d(TAG, "boundDeviceIds content: " + boundDeviceIds);
            
            // 初始化蓝牙扫描器
            LogUtil.d(TAG, "Step 2: Initializing BLE scanner...");
            bluetoothScanner = new OptimizedBLEScanner(context, boundDeviceIds, boundDeviceNames);
            LogUtil.d(TAG, "BLE scanner initialized");
            
            // 初始化手机定位服务
            LogUtil.d(TAG, "Step 3: Initializing phone location service...");
            phoneLocationService = new PhoneLocationService(context);
            LogUtil.d(TAG, "Phone location service initialized");
            
            // 初始化服务器API
            LogUtil.d(TAG, "Step 4: Initializing API service...");
            apiService = NewApiService.getInstance();
            LogUtil.d(TAG, "API service initialized");

            // 创建服务器同步管理器（依赖 apiService）
            this.locationSyncManager = new LocationSyncManager(apiService, offlineCacheManager, deviceMacMapper);
            
            // 初始化位置提供者
            LogUtil.d(TAG, "Step 5: Initializing location provider...");
            locationProvider = new LocationProvider(
                context,
                bluetoothScanner,
                phoneLocationService,
                apiService
            );
            LogUtil.d(TAG, "Location provider initialized");
            
            LogUtil.d(TAG, "✓ Location optimization services initialized successfully");
            
        } catch (SecurityException e) {
            Log.e(TAG, "✗ Security exception during initialization", e);
            Log.e(TAG, "This may be due to missing permissions: " + e.getMessage());
            optimizationEnabled = false;
        } catch (Exception e) {
            Log.e(TAG, "✗ Error initializing services", e);
            Log.e(TAG, "Error type: " + e.getClass().getName());
            Log.e(TAG, "Error message: " + e.getMessage());
            e.printStackTrace();
            optimizationEnabled = false;
        }
    }
    
    /**
     * 启动持续蓝牙扫描
     */
    public void startBluetoothScanning() {
        LogUtil.d(TAG, "=== startBluetoothScanning called ===");
        LogUtil.d(TAG, "optimizationEnabled: " + optimizationEnabled);
        LogUtil.d(TAG, "bluetoothScanner: " + (bluetoothScanner != null ? "initialized" : "NULL"));
        LogUtil.d(TAG, "currentSelectedDeviceId: " + currentSelectedDeviceId);
        LogUtil.d(TAG, "boundDeviceIds: " + boundDeviceIds);
        
        // 自动选择第一个设备（如果当前没有选中设备）
        autoSelectFirstDevice();
        LogUtil.d(TAG, "After autoSelect: currentSelectedDeviceId=" + currentSelectedDeviceId);
        
        if (!optimizationEnabled) {
            Log.w(TAG, "Optimization disabled during initialization");
            runToast("❌ 优化器已禁用");
            return;
        }
        
        if (bluetoothScanner == null) {
            Log.w(TAG, "Bluetooth scanner not initialized");
            runToast("❌ 扫描器未初始化");
            return;
        }
        
        // 【诊断】检查权限状态
        boolean hasPermission = checkBluetoothPermissions();
        if (!hasPermission) {
            Log.e(TAG, "✗ Missing Bluetooth permissions - cannot start scanning");
            runToast("❌ 缺少蓝牙权限");
            return;
        }
        
        // 【诊断】检查蓝牙状态
        boolean isBluetoothOn = isBluetoothEnabled();
        if (!isBluetoothOn) {
            Log.e(TAG, "✗ Bluetooth is OFF - cannot start scanning");
            runToast("❌ 蓝牙未开启");
            return;
        }
        
        // 【诊断】检查已绑定设备
        if (boundDeviceIds.isEmpty() && boundDeviceNames.isEmpty()) {
            Log.w(TAG, "⚠ No bound devices - scanning will find nothing");
            runToast("⚠️ 无绑定设备");
            return;
        }
        
        // 【优化】如果没有MAC地址但有设备名称，仍然可以启动扫描（通过名称匹配）
        if (boundDeviceIds.isEmpty() && !boundDeviceNames.isEmpty()) {
            Log.w(TAG, "⚠ No MAC addresses available, but have device names - will scan by name matching");
        }
        
        LogUtil.d(TAG, "✓ All checks passed, starting BLE scanning...");
        
        // 【新增】使用带状态回调的扫描方法
        bluetoothScanner.startContinuousScanningWithState(new OptimizedBLEScanner.ScanStateCallback() {
            @Override
            public void onScanStarted() {
                LogUtil.d(TAG, "📡 Scan started (scanning for 10s)");
                if (scanStateCallback != null) {
                    scanStateCallback.onScanStarted();
                }
            }
            
            @Override
            public void onScanStopped() {
                LogUtil.d(TAG, "⏸️ Scan stopped (resting for 10s)");
                if (scanStateCallback != null) {
                    scanStateCallback.onScanStopped();
                }
            }
            
            @Override
            public void onDeviceFound(TagDevice device) {
                LogUtil.d(TAG, "📡 BLE Scanner found device: " + device.getName() + " (MAC: " + device.getDeviceId() + ")");
                
                // 已绑定设备，直接处理（OptimizedBLEScanner已经过滤了已绑定设备）
                // 如果设置了currentSelectedDeviceId，记录但不阻止处理
                if (currentSelectedDeviceId != null && !currentSelectedDeviceId.trim().equalsIgnoreCase(device.getDeviceId().trim())) {
                    LogUtil.d(TAG, "   ℹ️ Device found but not selected: " + device.getDeviceId() + " (selected: " + currentSelectedDeviceId + "), but still processing");
                }
                
                LogUtil.d(TAG, "✓✓✓ Processing bound device: " + device.getDeviceId());
                
                // 【最关键】第一时间更新时间戳到数据库和UI（不等待任何操作）
                LogUtil.d(TAG, "⏰ Step 1: Updating timestamp immediately...");
                updateDeviceTimestampImmediately(device.getDeviceId(), device.getName());
                
                // 通过MAC地址查找对应的16位设备号（deviceNum）
                String deviceNum = getDeviceNumById(device.getDeviceId());
                if (deviceNum == null || deviceNum.isEmpty()) {
                    Log.e(TAG, "Cannot find deviceNum for MAC: " + device.getDeviceId());
                    runToast("❌ 找不到设备号");
                    return;
                }
                
                LogUtil.d(TAG, "📍 Step 2: Found deviceNum: " + deviceNum);
                
                // MAC地址匹配成功，异步更新位置和上传服务器（不阻塞时间戳更新）
                LogUtil.d(TAG, "→ Uploading location for device: " + deviceNum);
                updateDeviceWithPhoneLocation(device.getDeviceId(), deviceNum);
                
                // 【新增】通知扫描成功
                if (scanStateCallback != null) {
                    scanStateCallback.onScanSuccess();
                }
            }
        });
        
        LogUtil.d(TAG, "Bluetooth scanning started");
    }
    
    /**
     * 【最关键】第一时间更新设备时间戳（扫描到设备后立即调用）
     * 不等待GPS位置，直接更新当前时间到数据库和UI
     */
    private void updateDeviceTimestampImmediately(String deviceId, String deviceName) {
        LogUtil.d(TAG, "⚡ IMMEDIATE timestamp update for: " + deviceName + " (" + deviceId + ")");
        
        // 在主线程立即更新UI
        mainHandler.post(() -> {
            try {
                long currentTime = System.currentTimeMillis();
                LogUtil.d(TAG, "🕐 Current time: " + currentTime);
                
                // 【关键修复】先获取手机GPS位置
                LogUtil.d(TAG, "📍 Getting phone GPS location...");
                PhoneLocation phoneLoc = phoneLocationService.getCurrentLocation();
                final double lat;
                final double lng;
                
                if (phoneLoc != null && phoneLoc.isValid()) {
                    lat = phoneLoc.getLatitude();
                    lng = phoneLoc.getLongitude();
                    LogUtil.d(TAG, "✓ Using phone GPS location: " + lat + ", " + lng);
                } else {
                    lat = 0;
                    lng = 0;
                    Log.w(TAG, "Phone GPS not available, using zero coordinates");
                }
                
                // 创建包含GPS位置的DeviceLocation对象
                DeviceLocation quickUpdate = new DeviceLocation();
                quickUpdate.setDeviceId(deviceId);
                quickUpdate.setTimestamp(currentTime);
                quickUpdate.setActualSource(DeviceLocation.DataSource.BLUETOOTH_SCAN);
                quickUpdate.setLatitude(lat);
                quickUpdate.setLongitude(lng);
                
                String deviceNum = getDeviceNumById(deviceId);
                int deviceBattery = getDeviceBattery(deviceNum);
                quickUpdate.setBattery(deviceBattery);
                LogUtil.d(TAG, "Battery from database: " + deviceBattery + "% for device: " + deviceNum);
                
                quickUpdate.setAddress(null);
                
                LogUtil.d(TAG, "📱 Notifying UI callback...");
                // 通知外部回调更新UI（显示最新时间和地址）
                if (externalCallback != null) {
                    externalCallback.onLocationUpdated(quickUpdate);
                    LogUtil.d(TAG, "✓ UI updated immediately with timestamp and address");
                } else {
                    Log.w(TAG, "⚠️ externalCallback is NULL!");
                }
                
                // 异步保存到数据库（不阻塞UI）
                LogUtil.d(TAG, "💾 Saving to database...");
                new Thread(() -> {
                    try {
                        saveLocationToDevice(deviceId, quickUpdate);
                        LogUtil.d(TAG, "✓ Database saved: time=" + 
                            new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                .format(new java.util.Date(currentTime)) +
                            ", lat=" + lat + ", lng=" + lng);
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving to database", e);
                    }
                }).start();
                
            } catch (Exception e) {
                Log.e(TAG, "Error in immediate timestamp update", e);
            }
        });
    }
    
    /**
     * 检查蓝牙权限
     */
    private boolean checkBluetoothPermissions() {
        if (context == null) {
            Log.e(TAG, "Context is null");
            return false;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要 BLUETOOTH_SCAN/CONNECT，且 BLE 扫描仍依赖位置权限
            int scanPerm = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN);
            int connectPerm = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT);
            int locationPerm = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
            
            boolean hasScan = scanPerm == PackageManager.PERMISSION_GRANTED;
            boolean hasConnect = connectPerm == PackageManager.PERMISSION_GRANTED;
            boolean hasLocation = locationPerm == PackageManager.PERMISSION_GRANTED;
            
            LogUtil.d(TAG, "Permission check (API " + Build.VERSION.SDK_INT + "):");
            LogUtil.d(TAG, "  BLUETOOTH_SCAN: " + (hasScan ? "✓" : "✗"));
            LogUtil.d(TAG, "  BLUETOOTH_CONNECT: " + (hasConnect ? "✓" : "✗"));
            LogUtil.d(TAG, "  ACCESS_FINE_LOCATION: " + (hasLocation ? "✓" : "✗"));
            
            return hasScan && hasConnect && hasLocation;
        } else {
            // Android 11及以下需要 ACCESS_FINE_LOCATION
            int locationPerm = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
            boolean hasLocation = locationPerm == PackageManager.PERMISSION_GRANTED;
            
            LogUtil.d(TAG, "Permission check (API " + Build.VERSION.SDK_INT + "):");
            LogUtil.d(TAG, "  ACCESS_FINE_LOCATION: " + (hasLocation ? "✓" : "✗"));
            
            return hasLocation;
        }
    }
    
    /**
     * 检查蓝牙是否开启
     */
    private boolean isBluetoothEnabled() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                Log.e(TAG, "BluetoothAdapter is null - device may not support Bluetooth");
                return false;
            }
            
            boolean isEnabled = adapter.isEnabled();
            LogUtil.d(TAG, "Bluetooth state: " + (isEnabled ? "ON" : "OFF"));
            return isEnabled;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Bluetooth state", e);
            return false;
        }
    }
    
    /**
     * 停止蓝牙扫描
     */
    public void stopBluetoothScanning() {
        if (bluetoothScanner != null) {
            bluetoothScanner.stopScanning();
            LogUtil.d(TAG, "Bluetooth scanning stopped");
        }
    }

    /**
     * 启动单次蓝牙扫描（低强度）
     * 扫描10秒后自动停止，不循环。扫描到设备后更新UI。
     */
    public void startSingleBluetoothScan() {
        LogUtil.d(TAG, "=== startSingleBluetoothScan called ===");

        if (!optimizationEnabled) {
            Log.w(TAG, "Optimization disabled");
            runToast("❌ 优化器已禁用");
            return;
        }

        if (bluetoothScanner == null) {
            Log.w(TAG, "Bluetooth scanner not initialized");
            runToast("❌ 扫描器未初始化");
            return;
        }

        boolean hasPermission = checkBluetoothPermissions();
        if (!hasPermission) {
            Log.e(TAG, "Missing Bluetooth permissions");
            runToast("❌ 缺少蓝牙权限");
            return;
        }

        boolean isBluetoothOn = isBluetoothEnabled();
        if (!isBluetoothOn) {
            Log.e(TAG, "Bluetooth is OFF");
            runToast("❌ 蓝牙未开启");
            return;
        }

        if (boundDeviceIds.isEmpty() && boundDeviceNames.isEmpty()) {
            Log.w(TAG, "No bound devices");
            runToast("⚠️ 无绑定设备");
            return;
        }

        LogUtil.d(TAG, "Starting single BLE scan (will stop after one cycle)...");

        bluetoothScanner.startSingleScanWithState(new OptimizedBLEScanner.ScanStateCallback() {
            @Override
            public void onScanStarted() {
                LogUtil.d(TAG, "📡 Single scan started");
                if (scanStateCallback != null) {
                    scanStateCallback.onScanStarted();
                }
            }

            @Override
            public void onScanStopped() {
                LogUtil.d(TAG, "⏹️ Single scan stopped (completed)");
                if (scanStateCallback != null) {
                    scanStateCallback.onScanStopped();
                }
            }

            @Override
            public void onDeviceFound(TagDevice device) {
                LogUtil.d(TAG, "📡 Single scan found device: " + device.getName() + " (MAC: " + device.getDeviceId() + ")");

                updateDeviceTimestampImmediately(device.getDeviceId(), device.getName());

                String deviceNum = getDeviceNumById(device.getDeviceId());
                if (deviceNum == null || deviceNum.isEmpty()) {
                    Log.e(TAG, "Cannot find deviceNum for MAC: " + device.getDeviceId());
                    return;
                }

                updateDeviceWithPhoneLocation(device.getDeviceId(), deviceNum);

                if (scanStateCallback != null) {
                    scanStateCallback.onScanSuccess();
                }
            }
        });

        LogUtil.d(TAG, "Single Bluetooth scan started");
    }
    
    /**
     * 检查蓝牙扫描是否正在运行（内部状态）
     */
    private boolean isContinuousScanningActive() {
        // 简单判断：如果bluetoothScanner不为null，则认为扫描可能正在运行
        // 更精确的判断需要OptimizedBLEScanner暴露扫描状态
        return bluetoothScanner != null;
    }
    
    /**
     * 使用手机位置更新设备位置和时间
     * 当蓝牙扫描到已绑定设备时调用
     */
    private void updateDeviceWithPhoneLocation(String deviceId, String deviceNum) {
        new Thread(() -> {
            try {
                // 1. 获取手机当前位置
                PhoneLocation phoneLoc = phoneLocationService.getCurrentLocation();
                
                if (phoneLoc == null || !phoneLoc.isValid()) {
                    // 降级：使用服务器数据
                    triggerLocationUpdate(deviceId, deviceNum);
                    return;
                }
                
                // 创建DeviceLocation对象 - 使用当前系统时间作为时间戳
                long currentTime = System.currentTimeMillis();
                DeviceLocation location = new DeviceLocation();
                location.setLatitude(phoneLoc.getLatitude());
                location.setLongitude(phoneLoc.getLongitude());
                location.setAccuracy(phoneLoc.getAccuracy());
                location.setTimestamp(currentTime);  // 使用当前时间确保时间戳最新
                
                int deviceBattery = getDeviceBattery(deviceNum);
                location.setBattery(deviceBattery);
                LogUtil.d(TAG, "Battery from database: " + deviceBattery + "% for device: " + deviceNum);
                
                location.setActualSource(DeviceLocation.DataSource.PHONE_GPS);
                location.setAddress("正在获取地址...");  // 临时地址，稍后异步获取
                location.setDeviceId(deviceId);  // 设置设备ID，用于UI匹配
                
                // 立即保存到数据库（在主线程之前完成）
                saveLocationToDevice(deviceId, location);
                
                // 通知外部回调（在主线程执行UI更新）- 必须在保存数据库之后
                if (externalCallback != null) {
                    mainHandler.post(() -> {
                        try {
                            externalCallback.onLocationUpdated(location);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in external callback", e);
                        }
                    });
                }
                
                // 立即同步到服务器（不等待，确保数据及时上传）
                syncLocationToServerImmediately(deviceNum, location);
                
                // 异步获取逆地理编码地址（不阻塞UI更新）
                fetchReverseGeocodeAsync(deviceId, location);
                
            } catch (Exception e) {
                Log.e(TAG, "✗ Error updating device with phone location", e);
                // 出错时降级到服务器
                triggerLocationUpdate(deviceId, deviceNum);
            }
        }).start();
    }
    
    /**
     * 仅更新本地数据库和UI，不上传服务器（用于非选中设备）
     */
    private void updateLocalOnly(String deviceId, String deviceNum) {
        new Thread(() -> {
            try {
                // 1. 获取手机当前位置
                PhoneLocation phoneLoc = phoneLocationService.getCurrentLocation();
                
                if (phoneLoc == null || !phoneLoc.isValid()) {
                    return;
                }
                
                // 创建DeviceLocation对象
                long currentTime = System.currentTimeMillis();
                DeviceLocation location = new DeviceLocation();
                location.setLatitude(phoneLoc.getLatitude());
                location.setLongitude(phoneLoc.getLongitude());
                location.setAccuracy(phoneLoc.getAccuracy());
                location.setTimestamp(currentTime);
                
                int deviceBattery = getDeviceBattery(deviceNum);
                location.setBattery(deviceBattery);
                
                location.setActualSource(DeviceLocation.DataSource.PHONE_GPS);
                location.setAddress("正在获取地址...");
                location.setDeviceId(deviceId);  // 设置设备ID，用于UI匹配
                
                // 保存到数据库
                saveLocationToDevice(deviceId, location);
                
            } catch (Exception e) {
                Log.e(TAG, " Error in local-only update", e);
            }
        }).start();
    }
    
    /**
     * 异步获取逆地理编码地址
     */
    private void fetchReverseGeocodeAsync(String deviceId, DeviceLocation location) {
        new Thread(() -> {
            try {
                LogUtil.d(TAG, "Fetching reverse geocode for location...");
                
                // 这里可以调用高德地图或谷歌地图的逆地理编码API
                // 由于这是异步操作，不会阻塞UI更新
                // TODO: 实现逆地理编码逻辑
                
                // 暂时保持"正在获取地址..."状态
                // 实际项目中应该调用地图SDK的逆地理编码接口
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching reverse geocode", e);
            }
        }).start();
    }
    
    /**
     * 【立即同步】同步位置到服务器（蓝牙扫描到设备后立即调用）
     * 委托给 LocationSyncManager，支持最大重试 10 次，失败后保存到离线缓存
     */
    private void syncLocationToServerImmediately(String deviceNum, DeviceLocation location) {
        if (locationSyncManager != null) {
            locationSyncManager.syncLocationToServer(deviceNum, location);
        } else {
            Log.w(TAG, "LocationSyncManager not initialized, cannot sync location");
        }
    }

    /**
     * 同步位置到服务器（备用方法，委托给 LocationSyncManager）
     */
    private void syncLocationToServer(String deviceNum, DeviceLocation location) {
        syncLocationToServerImmediately(deviceNum, location);
    }
    
    /**
     * 保存到离线缓存（委托给 OfflineCacheManager）
     * @param deviceNum 设备号
     * @param location 位置信息
     */
    private void saveToOfflineCache(String deviceNum, DeviceLocation location) {
        if (offlineCacheManager != null) {
            offlineCacheManager.saveToOfflineCache(deviceNum, location);
        }
    }

    /**
     * 删除已上传的离线缓存（委托给 OfflineCacheManager）
     * @param deviceNum 设备号
     * @param timestamp 时间戳
     */
    private void removeOfflineCache(String deviceNum, long timestamp) {
        if (offlineCacheManager != null) {
            offlineCacheManager.removeOfflineCache(deviceNum, timestamp);
        }
    }
    
    /**
     * 更新数据库中设备的MAC地址字段（委托给 DeviceMacMapper）
     * @param deviceNum 设备号
     * @param location 位置信息
     */
    private void updateDeviceMacInDatabase(String deviceNum, DeviceLocation location) {
        if (deviceMacMapper != null) {
            deviceMacMapper.updateDeviceMacInDatabase(deviceNum, location);
        }
    }
    
    /**
     * 根据设备号获取设备名称
     */
    private String getDeviceNameByNum(String deviceNum) {
        if (databaseHelper == null || deviceNum == null) {
            return "未知设备";
        }
        
        try {
            TagDevice device = databaseHelper.getDeviceByDeviceNum(deviceNum);
            if (device != null && device.getName() != null) {
                return device.getName();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device name", e);
        }
        
        return "设备";
    }
    
    /**
     * 触发位置更新（备用：当手机GPS不可用时调用服务器）
     */
    private void triggerLocationUpdate(String deviceId, String deviceNum) {
        LogUtil.d(TAG, "=== triggerLocationUpdate START (fallback to server) ===");
        LogUtil.d(TAG, "deviceId: " + deviceId + ", deviceNum: " + deviceNum);
        
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                LogUtil.d(TAG, "Calling locationProvider.getDeviceLocation...");
                
                DeviceLocation location = locationProvider.getDeviceLocation(deviceId, deviceNum);
                
                long endTime = System.currentTimeMillis();
                LogUtil.d(TAG, "Location query took: " + (endTime - startTime) + "ms");
                
                if (location != null && location.isValid()) {
                    LogUtil.d(TAG, "========== Location Update Success ==========");
                    LogUtil.d(TAG, "Latitude: " + location.getLatitude());
                    LogUtil.d(TAG, "Longitude: " + location.getLongitude());
                    LogUtil.d(TAG, "Accuracy: " + location.getAccuracy() + "m");
                    LogUtil.d(TAG, "Source: " + location.getActualSource());
                    LogUtil.d(TAG, "Timestamp: " + location.getDisplayTimeForLog());
                    LogUtil.d(TAG, "Address: " + location.getAddress());
                    LogUtil.d(TAG, "Battery: " + location.getBattery() + "%");
                    
                    // 保存到数据库
                    saveLocationToDevice(deviceId, location);
                    
                    // 添加Toast提示：开始通知UI更新（备用方法）
                    String toastMsg = "🔄 正在更新UI (备用)...";
                    LogUtil.d(TAG, "TOAST: " + toastMsg);
                    runToast(toastMsg);
                    
                    // 通知外部回调（如果有）- 必须在主线程执行
                    if (externalCallback != null) {
                        LogUtil.d(TAG, "Notifying external callback on main thread...");
                        mainHandler.post(() -> {
                            try {
                                externalCallback.onLocationUpdated(location);
                            } catch (Exception e) {
                                Log.e(TAG, "Error in external callback", e);
                            }
                        });
                    }
                    
                    LogUtil.d(TAG, "=== Location Update Complete ===");
                } else {
                    Log.w(TAG, "========== Location Update Failed ==========");
                    Log.w(TAG, "Invalid or null location returned");
                    
                    if (externalCallback != null) {
                        final String errorMsg = "Invalid location";
                        mainHandler.post(() -> externalCallback.onError(errorMsg));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "========== Location Update Error ==========", e);
                Log.e(TAG, "Error message: " + e.getMessage());
                
                if (externalCallback != null) {
                    final String errorMsg = e.getMessage();
                    mainHandler.post(() -> externalCallback.onError(errorMsg));
                }
            }
        }).start();
    }
    
    /**
     * 根据MAC地址获取16位设备号（deviceNum）
     * 委托给 DeviceMacMapper，内部先查内存映射，未命中再查数据库
     *
     * @param macAddress 蓝牙MAC地址
     * @return 16位设备号，如果找不到返回null
     */
    private String getDeviceNumById(String macAddress) {
        if (deviceMacMapper != null) {
            String deviceNum = deviceMacMapper.getDeviceNumByMac(macAddress);
            if (deviceNum == null) {
                Log.w(TAG, "✗ Cannot find 16-digit deviceNum for MAC: " + macAddress);
            }
            return deviceNum;
        }
        return null;
    }
    
    /**
     * 获取设备位置（使用新的优化逻辑）
     * @param deviceId 设备ID
     * @param deviceNum 设备编号
     * @param callback 回调
     */
    public void getDeviceLocation(String deviceId, String deviceNum, LocationUpdateCallback callback) {
        if (!optimizationEnabled || locationProvider == null) {
            Log.w(TAG, "Optimization not enabled, using fallback");
            if (callback != null) {
                callback.onError("Optimization not enabled");
            }
            return;
        }
        
        new Thread(() -> {
            try {
                DeviceLocation location = locationProvider.getDeviceLocation(deviceId, deviceNum);
                
                if (location != null && location.isValid()) {
                    LogUtil.d(TAG, String.format(
                        "Location obtained: lat=%.6f, lng=%.6f, source=%s",
                        location.getLatitude(),
                        location.getLongitude(),
                        location.getActualSource()
                    ));
                    
                    if (callback != null) {
                        callback.onLocationUpdated(location);
                    }
                } else {
                    Log.w(TAG, "Invalid location returned");
                    if (callback != null) {
                        callback.onError("Invalid location");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting location", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * 更新时间显示
     */
    public void updateTimeDisplay(TextView textView, long timestamp) {
        if (timeRefreshManager != null) {
            timeRefreshManager.stopTimeAutoRefresh();
            timeRefreshManager.startTimeAutoRefresh(textView, timestamp);
        }
    }
    
    /**
     * 停止时间刷新
     */
    public void stopTimeRefresh() {
        if (timeRefreshManager != null) {
            timeRefreshManager.stopTimeAutoRefresh();
        }
    }
    
    /**
     * 保存位置到数据库
     * 重要：同时更新 devices 表（最新位置）和 location_records 表（历史轨迹）
     */
    public void saveLocationToDevice(String deviceId, DeviceLocation location) {
        if (databaseHelper == null || location == null) {
            return;
        }
        
        try {
            // 1. 更新 devices 表（最新位置，用于UI显示）
            TagDevice device = databaseHelper.getDevice(deviceId);
            if (device != null) {
                device.setLatitude(location.getLatitude());
                device.setLongitude(location.getLongitude());
                device.setLastSeen(location.getTimestamp());
                databaseHelper.addDevice(device);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating devices table", e);
        }
        
        // 2. 【关键】追加到 location_records 表（历史轨迹，不覆盖）
        try {
            // 获取设备号（用于轨迹查询）
            String deviceNum = getDeviceNumById(deviceId);
            if (deviceNum != null && !deviceNum.isEmpty()) {
                LocationRecord record = new LocationRecord(
                    deviceNum,  // 使用 deviceNum 而不是 deviceId
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getTimestamp()
                );
                
                databaseHelper.addLocationRecord(record);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving to location_records", e);
        }
    }
    
    /**
     * 获取已绑定设备ID列表和名称列表
     * MAC 映射委托给 DeviceMacMapper，本方法只负责收集设备名称和触发服务器获取
     */
    private Set<String> getBoundDeviceIds() {
        boundDeviceIds.clear();
        boundDeviceNames.clear();
        if (deviceMacMapper != null) {
            deviceMacMapper.clearMappings();
        }

        LogUtil.d(TAG, "=== getBoundDeviceIds START ===");
        LogUtil.d(TAG, "databaseHelper: " + (databaseHelper != null ? "NOT NULL" : "NULL"));

        if (databaseHelper != null) {
            try {
                LogUtil.d(TAG, "Step 1: Loading bound devices from database...");
                java.util.List<TagDevice> devices = databaseHelper.getAllDevices();
                LogUtil.d(TAG, "getAllDevices returned: " + (devices != null ? "list with " + devices.size() + " items" : "NULL"));

                if (devices != null && !devices.isEmpty()) {
                    LogUtil.d(TAG, "Found " + devices.size() + " devices in database");

                    int macCount = 0;
                    int noMacCount = 0;

                    for (TagDevice device : devices) {
                        String deviceNum = device.getDeviceNum();
                        String deviceName = device.getName();
                        String deviceMac = device.getMac();

                        if (deviceNum == null || deviceNum.isEmpty()) {
                            Log.w(TAG, "Skipping device with empty deviceNum: " + deviceName);
                            continue;
                        }

                        LogUtil.d(TAG, "Processing device: num=" + deviceNum + ", name=" + deviceName + ", mac=" + deviceMac);

                        // 收集设备名称
                        if (deviceName != null && !deviceName.isEmpty()) {
                            // 如果名称包含 " (Find My)" 后缀，也添加基础名称
                            if (deviceName.contains(" (Find My)")) {
                                String baseName = deviceName.replace(" (Find My)", "");
                                if (!baseName.isEmpty() && !boundDeviceNames.contains(baseName)) {
                                    boundDeviceNames.add(baseName);
                                }
                            }
                            if (!boundDeviceNames.contains(deviceName)) {
                                boundDeviceNames.add(deviceName);
                            }
                        }

                        // 如果数据库中已有MAC地址，通过 DeviceMacMapper 建立映射
                        if (deviceMac != null && !deviceMac.isEmpty()) {
                            String normalizedMac = normalizeMacAddress(deviceMac);
                            if (deviceMacMapper != null) {
                                deviceMacMapper.addMapping(deviceNum, normalizedMac);
                            }
                            boundDeviceIds.add(normalizedMac);
                            macCount++;
                            LogUtil.d(TAG, "  ✓ Loaded MAC from database: " + normalizedMac);
                        } else {
                            noMacCount++;
                            Log.w(TAG, "  ✗ No MAC address for device: " + deviceNum);
                        }
                    }

                    LogUtil.d(TAG, "Summary: " + macCount + " devices with MAC, " + noMacCount + " without MAC");
                    LogUtil.d(TAG, "Loaded " + boundDeviceNames.size() + " device names from database");
                    LogUtil.d(TAG, "Loaded " + boundDeviceIds.size() + " MAC addresses from database");

                    // 【关键优化】即使没有MAC地址，也立即启动蓝牙扫描（通过名称匹配）
                    // 对于没有MAC地址的设备，异步从服务器获取
                    boolean needFetchMac = noMacCount > 0;
                    if (needFetchMac) {
                        LogUtil.d(TAG, "Some devices missing MAC, will fetch from server asynchronously...");
                        LogUtil.d(TAG, "Starting BLE scan NOW with device names (will add MACs when fetched)");
                        // 异步获取MAC，但不阻塞蓝牙扫描启动
                        fetchMacAddressesFromServer(devices);
                    } else {
                        LogUtil.d(TAG, "✓ All devices have MAC addresses, no need to fetch from server");
                    }
                } else {
                    Log.w(TAG, "No devices found in database");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting bound devices", e);
            }
        }

        LogUtil.d(TAG, "=== getBoundDeviceIds END ===");
        LogUtil.d(TAG, "Final bound device IDs (MACs): " + boundDeviceIds);
        LogUtil.d(TAG, "Final bound device names: " + boundDeviceNames);
        if (deviceMacMapper != null) {
            LogUtil.d(TAG, "DeviceMacMapper MAC count: " + deviceMacMapper.getAllMacAddresses().size());
        }
        return boundDeviceIds;
    }
    
    /**
     * 从服务器获取设备的MAC地址
     * @param devices 设备列表
     */
    private void fetchMacAddressesFromServer(java.util.List<TagDevice> devices) {
        if (devices == null || devices.isEmpty()) {
            Log.w(TAG, "No devices to fetch MAC addresses for");
            return;
        }
        
        new Thread(() -> {
            try {
                LogUtil.d(TAG, "=== Fetching MAC addresses from server ===");
                
                // 使用第一个设备的URL来获取所有设备列表（假设所有设备在同一服务器）
                String firstDeviceNum = devices.get(0).getDeviceNum();
                String apiUrl = ApiConfig.getMyServerUrl(firstDeviceNum);
                LogUtil.d(TAG, "Using API URL: " + apiUrl);
                
                // 只调用一次 /devices 接口获取所有设备列表（包含MAC地址）
                java.util.List<NewApiService.DeviceInfo> allDevices = apiService.getDevices(apiUrl);
                LogUtil.d(TAG, "Got " + (allDevices != null ? allDevices.size() : 0) + " devices from server");
                
                if (allDevices == null || allDevices.isEmpty()) {
                    Log.e(TAG, "✗ Failed to get devices from server or empty list");
                    return;
                }
                
                // 打印服务器返回的所有设备信息（调试用）
                LogUtil.d(TAG, "=== Server returned devices ===");
                boolean foundMyDevice = false;
                for (NewApiService.DeviceInfo info : allDevices) {
                    LogUtil.d(TAG, "  Server device: num=" + info.deviceNum + ", name=" + info.nickName + ", mac=" + info.mac);
                    // 检查是否包含当前用户的设备
                    if (info.deviceNum != null && info.deviceNum.contains("1756726632035006")) {
                        foundMyDevice = true;
                        LogUtil.d(TAG, "  >>> FOUND MY DEVICE: " + info.deviceNum + " with MAC: " + info.mac);
                    }
                }
                LogUtil.d(TAG, "==============================");
                if (!foundMyDevice) {
                    Log.w(TAG, " Device 1756726632035006 (SC-35006) NOT found in server response!");
                    Log.w(TAG, "  This means MAC address cannot be fetched from server!");
                }
                
                // 为每个本地设备查找对应的MAC地址
                for (TagDevice device : devices) {
                    String deviceNum = device.getDeviceNum();
                    if (deviceNum == null || deviceNum.isEmpty()) {
                        continue;
                    }
                    
                    // 在服务器返回的设备列表中查找匹配的设备号
                    String macAddress = null;
                    for (NewApiService.DeviceInfo info : allDevices) {
                        if (deviceNum.equals(info.deviceNum)) {
                            macAddress = info.mac;
                            break;
                        }
                    }
                    
                    if (macAddress != null && !macAddress.isEmpty()) {
                        String normalizedMac = normalizeMacAddress(macAddress);

                        LogUtil.d(TAG, "✓ Got MAC for device " + deviceNum + ": " + normalizedMac);

                        // 更新本地设备的MAC地址
                        device.setMac(normalizedMac);

                        // 通过 DeviceMacMapper 建立映射
                        if (deviceMacMapper != null) {
                            deviceMacMapper.addMapping(deviceNum, normalizedMac);
                        }

                        // 添加到boundDeviceIds集合（用于蓝牙扫描匹配）
                        boundDeviceIds.add(normalizedMac);

                        // 保存到数据库
                        databaseHelper.addDevice(device);
                        LogUtil.d(TAG, "  Saved MAC to database for device: " + deviceNum);
                    } else {
                        Log.w(TAG, "✗ No MAC address found for device: " + deviceNum);
                    }
                }

                LogUtil.d(TAG, "=== MAC address fetch complete ===");
                if (deviceMacMapper != null) {
                    LogUtil.d(TAG, "Total devices with MAC: " + deviceMacMapper.getAllMacAddresses().size());
                }
                LogUtil.d(TAG, "Bound device IDs (MACs): " + boundDeviceIds);
                
                // 【关键】如果蓝牙扫描已经在运行，重启它以应用新的MAC列表
                if (bluetoothScanner != null && isContinuousScanningActive()) {
                    LogUtil.d(TAG, "Restarting BLE scanner with updated MAC list...");
                    stopBluetoothScanning();
                    startBluetoothScanning();
                    LogUtil.d(TAG, "✓ BLE scanner restarted with new MAC addresses");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in fetchMacAddressesFromServer", e);
            }
        }).start();
    }
    
    /**
     * 标准化MAC地址格式（委托给 DeviceMacMapper）
     * 统一转换为大写+冒号分隔格式，例如: D4:DE:42:0F:57:7A
     */
    private String normalizeMacAddress(String macAddress) {
        if (deviceMacMapper != null) {
            return deviceMacMapper.normalizeMacAddress(macAddress);
        }
        return macAddress != null ? macAddress.toUpperCase() : "";
    }
    
    /**
     * 检查优化是否可用
     */
    public boolean isOptimizationEnabled() {
        return optimizationEnabled;
    }
    
    /**
     * 启用/禁用优化
     */
    public void setOptimizationEnabled(boolean enabled) {
        this.optimizationEnabled = enabled;
        LogUtil.d(TAG, "Optimization " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * 设置调试模式（是否显示扫描Toast）
     * @param enabled true=显示Toast提示，false=不显示
     */
    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
        LogUtil.d(TAG, "Debug mode (Toast) " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * 获取调试模式状态
     */
    public boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * 获取最后已知电量
     */
    private int getLastKnownBattery() {
        return 85;
    }
    
    /**
     * 从数据库获取设备电池信息
     * @param deviceNum 设备号
     * @return 电池电量，如果找不到返回-1
     */
    private int getDeviceBattery(String deviceNum) {
        if (databaseHelper == null || deviceNum == null || deviceNum.isEmpty()) {
            return -1;
        }
        
        try {
            TagDevice device = databaseHelper.getDeviceByDeviceNum(deviceNum);
            if (device != null) {
                int battery = device.getBattery();
                LogUtil.d(TAG, "Got battery from database: " + battery + "% for deviceNum: " + deviceNum);
                return battery;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device battery: " + e.getMessage());
        }
        
        return -1;
    }
    
    /**
     * Activity recreate（语言/地图切换）前调用：断开 UI 回调并取消主线程延迟任务，避免旧实例几秒后仍收到蓝牙/定位回调导致闪退。
     */
    public void prepareForActivityRecreate() {
        stopBluetoothScanning();
        stopTimeRefresh();
        externalCallback = null;
        scanStateCallback = null;
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        if (activityContextRef != null) {
            activityContextRef.clear();
        }
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        prepareForActivityRecreate();
        if (activityContextRef != null) {
            activityContextRef.clear();
        }
        if (locationProvider != null) {
            locationProvider = null;
        }
        if (bluetoothScanner != null) {
            bluetoothScanner = null;
        }
        if (phoneLocationService != null) {
            phoneLocationService = null;
        }
        
        LogUtil.d(TAG, "Location optimization manager cleaned up");
    }
}
