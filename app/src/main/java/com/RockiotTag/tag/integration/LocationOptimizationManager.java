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
import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.bluetooth.OptimizedBLEScanner;
import com.RockiotTag.tag.location.PhoneLocationService;
import com.RockiotTag.tag.model.DeviceLocation;
import com.RockiotTag.tag.model.PhoneLocation;
import com.RockiotTag.tag.provider.LocationProvider;
import com.RockiotTag.tag.ui.TimeRefreshManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定位优化集成管理器
 * 负责将新的定位逻辑集成到 MainActivity
 */
public class LocationOptimizationManager {
    
    private static final String TAG = "LocationOptimization";
    
    private Context context;
    private Context activityContext; // 保存Activity引用用于显示Toast
    private LocationProvider locationProvider;
    private OptimizedBLEScanner bluetoothScanner;
    private PhoneLocationService phoneLocationService;
    private TimeRefreshManager timeRefreshManager;
    private DatabaseHelper databaseHelper;
    private NewApiService apiService;
    private android.os.Handler mainHandler; // 用于在主线程执行回调
    
    // 是否启用优化
    private volatile boolean optimizationEnabled = true;
    
    // 调试模式：是否显示扫描Toast提示
    private volatile boolean debugMode = true;
    
    // 使用同步集合防止并发修改异常
    private final Set<String> boundDeviceIds = java.util.Collections.synchronizedSet(new HashSet<>()); // MAC地址列表
    private final List<String> boundDeviceNames = new ArrayList<>();
    
    // 设备号到MAC地址的映射表
    private final Map<String, String> deviceNumToMacMap = java.util.Collections.synchronizedMap(new HashMap<>());
    // MAC地址到设备号的映射表
    private final Map<String, String> macToDeviceNumMap = java.util.Collections.synchronizedMap(new HashMap<>());
    
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
        this.activityContext = context; // 保存原始Context（可能是Activity）用于显示Toast
        this.databaseHelper = databaseHelper;
        this.timeRefreshManager = new TimeRefreshManager();
        this.mainHandler = new android.os.Handler(android.os.Looper.getMainLooper()); // 初始化主线程Handler
        
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
        Log.d(TAG, "Current selected device ID set to: " + deviceId);
    }
    
    /**
     * 在主线程显示Toast提示
     */
    private void runToast(String message) {
        if (activityContext != null && mainHandler != null) {
            mainHandler.post(() -> {
                try {
                    android.widget.Toast.makeText(activityContext, message, android.widget.Toast.LENGTH_SHORT).show();
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
            Log.d(TAG, "Already have selected device: " + currentSelectedDeviceId);
            return;
        }
        
        if (!boundDeviceIds.isEmpty()) {
            // 选择第一个设备
            String firstMac = boundDeviceIds.iterator().next();
            setCurrentSelectedDeviceId(firstMac);
            Log.d(TAG, "Auto-selected first device: " + firstMac);
            
            // 同时设置对应的deviceNum
            String deviceNum = macToDeviceNumMap.get(firstMac);
            if (deviceNum != null) {
                Log.d(TAG, "  -> deviceNum: " + deviceNum);
            }
        } else {
            Log.w(TAG, "No bound devices available for auto-selection");
        }
    }
    
    /**
     * 初始化所有服务
     */
    private void initializeServices() {
        Log.d(TAG, "=== Initializing Location Optimization Services ===");
        try {
            // 获取已绑定设备列表
            Log.d(TAG, "Step 1: Getting bound device list...");
            getBoundDeviceIds();
            Log.d(TAG, "Found " + boundDeviceIds.size() + " bound devices, " + boundDeviceNames.size() + " device names");
            Log.d(TAG, "boundDeviceIds content: " + boundDeviceIds);
            
            // 初始化蓝牙扫描器
            Log.d(TAG, "Step 2: Initializing BLE scanner...");
            bluetoothScanner = new OptimizedBLEScanner(context, boundDeviceIds, boundDeviceNames);
            Log.d(TAG, "BLE scanner initialized");
            
            // 初始化手机定位服务
            Log.d(TAG, "Step 3: Initializing phone location service...");
            phoneLocationService = new PhoneLocationService(context);
            Log.d(TAG, "Phone location service initialized");
            
            // 初始化服务器API
            Log.d(TAG, "Step 4: Initializing API service...");
            apiService = NewApiService.getInstance();
            Log.d(TAG, "API service initialized");
            
            // 初始化位置提供者
            Log.d(TAG, "Step 5: Initializing location provider...");
            locationProvider = new LocationProvider(
                context,
                bluetoothScanner,
                phoneLocationService,
                apiService
            );
            Log.d(TAG, "Location provider initialized");
            
            Log.d(TAG, "✓ Location optimization services initialized successfully");
            
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
        Log.d(TAG, "=== startBluetoothScanning called ===");
        Log.d(TAG, "optimizationEnabled: " + optimizationEnabled);
        Log.d(TAG, "bluetoothScanner: " + (bluetoothScanner != null ? "initialized" : "NULL"));
        Log.d(TAG, "currentSelectedDeviceId: " + currentSelectedDeviceId);
        Log.d(TAG, "boundDeviceIds: " + boundDeviceIds);
        
        // 自动选择第一个设备（如果当前没有选中设备）
        autoSelectFirstDevice();
        Log.d(TAG, "After autoSelect: currentSelectedDeviceId=" + currentSelectedDeviceId);
        
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
        
        Log.d(TAG, "✓ All checks passed, starting BLE scanning...");
        
        // 【新增】使用带状态回调的扫描方法
        bluetoothScanner.startContinuousScanningWithState(new OptimizedBLEScanner.ScanStateCallback() {
            @Override
            public void onScanStarted() {
                Log.d(TAG, "📡 Scan started (scanning for 10s)");
                if (scanStateCallback != null) {
                    scanStateCallback.onScanStarted();
                }
            }
            
            @Override
            public void onScanStopped() {
                Log.d(TAG, "⏸️ Scan stopped (resting for 10s)");
                if (scanStateCallback != null) {
                    scanStateCallback.onScanStopped();
                }
            }
            
            @Override
            public void onDeviceFound(com.RockiotTag.tag.Device device) {
                Log.d(TAG, "📡 BLE Scanner found device: " + device.getName() + " (MAC: " + device.getDeviceId() + ")");
                
                // 已绑定设备，直接处理（OptimizedBLEScanner已经过滤了已绑定设备）
                // 如果设置了currentSelectedDeviceId，记录但不阻止处理
                if (currentSelectedDeviceId != null && !currentSelectedDeviceId.trim().equalsIgnoreCase(device.getDeviceId().trim())) {
                    Log.d(TAG, "   ℹ️ Device found but not selected: " + device.getDeviceId() + " (selected: " + currentSelectedDeviceId + "), but still processing");
                }
                
                Log.d(TAG, "✓✓✓ Processing bound device: " + device.getDeviceId());
                
                // 【最关键】第一时间更新时间戳到数据库和UI（不等待任何操作）
                Log.d(TAG, "⏰ Step 1: Updating timestamp immediately...");
                updateDeviceTimestampImmediately(device.getDeviceId(), device.getName());
                
                // 通过MAC地址查找对应的16位设备号（deviceNum）
                String deviceNum = getDeviceNumById(device.getDeviceId());
                if (deviceNum == null || deviceNum.isEmpty()) {
                    Log.e(TAG, "Cannot find deviceNum for MAC: " + device.getDeviceId());
                    runToast("❌ 找不到设备号");
                    return;
                }
                
                Log.d(TAG, "📍 Step 2: Found deviceNum: " + deviceNum);
                
                // MAC地址匹配成功，异步更新位置和上传服务器（不阻塞时间戳更新）
                Log.d(TAG, "→ Uploading location for device: " + deviceNum);
                updateDeviceWithPhoneLocation(device.getDeviceId(), deviceNum);
                
                // 【新增】通知扫描成功
                if (scanStateCallback != null) {
                    scanStateCallback.onScanSuccess();
                }
            }
        });
        
        Log.d(TAG, "Bluetooth scanning started");
    }
    
    /**
     * 【最关键】第一时间更新设备时间戳（扫描到设备后立即调用）
     * 不等待GPS位置，直接更新当前时间到数据库和UI
     */
    private void updateDeviceTimestampImmediately(String deviceId, String deviceName) {
        Log.d(TAG, "⚡ IMMEDIATE timestamp update for: " + deviceName + " (" + deviceId + ")");
        
        // 在主线程立即更新UI
        mainHandler.post(() -> {
            try {
                long currentTime = System.currentTimeMillis();
                Log.d(TAG, "🕐 Current time: " + currentTime);
                
                // 【关键修复】先获取手机GPS位置
                Log.d(TAG, "📍 Getting phone GPS location...");
                PhoneLocation phoneLoc = phoneLocationService.getCurrentLocation();
                final double lat;
                final double lng;
                
                if (phoneLoc != null && phoneLoc.isValid()) {
                    lat = phoneLoc.getLatitude();
                    lng = phoneLoc.getLongitude();
                    Log.d(TAG, "✓ Using phone GPS location: " + lat + ", " + lng);
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
                Log.d(TAG, "Battery from database: " + deviceBattery + "% for device: " + deviceNum);
                
                quickUpdate.setAddress(null);
                
                Log.d(TAG, "📱 Notifying UI callback...");
                // 通知外部回调更新UI（显示最新时间和地址）
                if (externalCallback != null) {
                    externalCallback.onLocationUpdated(quickUpdate);
                    Log.d(TAG, "✓ UI updated immediately with timestamp and address");
                } else {
                    Log.w(TAG, "⚠️ externalCallback is NULL!");
                }
                
                // 异步保存到数据库（不阻塞UI）
                Log.d(TAG, "💾 Saving to database...");
                new Thread(() -> {
                    try {
                        saveLocationToDevice(deviceId, quickUpdate);
                        Log.d(TAG, "✓ Database saved: time=" + 
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
            // Android 12+ 需要 BLUETOOTH_SCAN 和 BLUETOOTH_CONNECT
            int scanPerm = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN);
            int connectPerm = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT);
            
            boolean hasScan = scanPerm == PackageManager.PERMISSION_GRANTED;
            boolean hasConnect = connectPerm == PackageManager.PERMISSION_GRANTED;
            
            Log.d(TAG, "Permission check (API " + Build.VERSION.SDK_INT + "):");
            Log.d(TAG, "  BLUETOOTH_SCAN: " + (hasScan ? "✓" : "✗"));
            Log.d(TAG, "  BLUETOOTH_CONNECT: " + (hasConnect ? "✓" : "✗"));
            
            return hasScan && hasConnect;
        } else {
            // Android 11及以下需要 ACCESS_FINE_LOCATION
            int locationPerm = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
            boolean hasLocation = locationPerm == PackageManager.PERMISSION_GRANTED;
            
            Log.d(TAG, "Permission check (API " + Build.VERSION.SDK_INT + "):");
            Log.d(TAG, "  ACCESS_FINE_LOCATION: " + (hasLocation ? "✓" : "✗"));
            
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
            Log.d(TAG, "Bluetooth state: " + (isEnabled ? "ON" : "OFF"));
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
            Log.d(TAG, "Bluetooth scanning stopped");
        }
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
                Log.d(TAG, "Battery from database: " + deviceBattery + "% for device: " + deviceNum);
                
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
                Log.d(TAG, "Fetching reverse geocode for location...");
                
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
     * 注意：此方法在新线程中执行，不会阻塞UI
     * 改进：无限重试机制 + 离线缓存，确保数据可靠上传
     */
    private void syncLocationToServerImmediately(String deviceNum, DeviceLocation location) {
        if (apiService == null || location == null) {
            Log.w(TAG, "Cannot sync location: apiService or location is null");
            return;
        }
        
        new Thread(() -> {
            int retryCount = 0;
            boolean success = false;
            
            // 【关键】无限重试，直到上传成功
            while (!success) {
                try {
                    retryCount++;
                    if (retryCount > 1) {
                        Log.d(TAG, "=== RETRY ATTEMPT #" + retryCount + " (20s interval) ===");
                    } else {
                        Log.d(TAG, "=== FIRST UPLOAD ATTEMPT ===");
                    }
                    
                    // 【关键调试】打印上传的设备信息
                    Log.d(TAG, "🔍 UPLOAD DEVICE INFO:");
                    Log.d(TAG, "  deviceNum (will be uploaded): " + deviceNum);
                    Log.d(TAG, "  Expected deviceNum: check if this matches your selected device");
                    Log.d(TAG, "  lat: " + location.getLatitude() + ", lng: " + location.getLongitude());
                    Log.d(TAG, "  timestamp: " + location.getTimestamp() + " (" + 
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            .format(new java.util.Date(location.getTimestamp())) + ")");
                    Log.d(TAG, "  battery: " + location.getBattery() + "%");
                    
                    // 设置正确的API地址
                    String apiUrl = com.RockiotTag.tag.ApiConfig.getMyServerUrl(deviceNum);
                    NewApiService.setApiBaseUrl(apiUrl);
                    Log.d(TAG, "API URL: " + apiUrl);
                    
                    long startTime = System.currentTimeMillis();
                    
                    // 调用同步接口
                    NewApiService.ApiResponse response = apiService.syncLocation(
                        deviceNum,
                        location.getLatitude(),
                        location.getLongitude(),
                        location.getBattery(),
                        location.getTimestamp()
                    );
                    
                    long endTime = System.currentTimeMillis();
                    Log.d(TAG, "Sync request took: " + (endTime - startTime) + "ms");
                    
                    if (response != null && response.isSuccess()) {
                        Log.d(TAG, "✓ UPLOAD SUCCESS on attempt #" + retryCount);
                        Log.d(TAG, "  Response: " + response.getMessage());
                        success = true;
                        
                        // 上传成功后，删除离线缓存（如果有）
                        removeOfflineCache(deviceNum, location.getTimestamp());
                        
                        // 更新MAC地址
                        updateDeviceMacInDatabase(deviceNum, location);
                        
                    } else {
                        Log.w(TAG, "✗ UPLOAD FAILED on attempt #" + retryCount);
                        Log.w(TAG, "  Response: " + (response != null ? response.getMessage() : "null response"));
                        Log.w(TAG, "  Status Code: " + (response != null ? response.getStatusCode() : "N/A"));
                        
                        // 【关键】上传失败，保存到离线缓存
                        saveToOfflineCache(deviceNum, location);
                        
                        // 等待20秒后重试
                        Log.d(TAG, "Waiting 20 seconds before retry...");
                        Thread.sleep(20000);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "✗ UPLOAD ERROR on attempt #" + retryCount, e);
                    Log.e(TAG, "  Error type: " + e.getClass().getName());
                    Log.e(TAG, "  Error message: " + e.getMessage());
                    
                    // 【关键】异常时 also 保存到离线缓存
                    saveToOfflineCache(deviceNum, location);
                    
                    // 等待20秒后重试
                    try {
                        Log.d(TAG, "Waiting 20 seconds before retry...");
                        Thread.sleep(20000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        Log.e(TAG, "Retry interrupted, stopping upload thread");
                        break;
                    }
                }
            }
            
            Log.d(TAG, "✓✓✓ UPLOAD COMPLETED SUCCESSFULLY after " + retryCount + " attempt(s) ✓✓✓");
        }).start();
    }
    
    /**
     * 同步位置到服务器（备用方法，保留原有逻辑）
     */
    private void syncLocationToServer(String deviceNum, DeviceLocation location) {
        syncLocationToServerImmediately(deviceNum, location);
    }
    
    /**
     * 保存到离线缓存（上传失败时调用）
     * @param deviceNum 设备号
     * @param location 位置信息
     */
    private void saveToOfflineCache(String deviceNum, DeviceLocation location) {
        if (databaseHelper == null || location == null) {
            Log.w(TAG, "Cannot save to offline cache: databaseHelper or location is null");
            return;
        }
        
        try {
            // 创建 LocationRecord 对象
            LocationRecord record = new LocationRecord(
                deviceNum,
                location.getLatitude(),
                location.getLongitude(),
                location.getTimestamp()
            );
            
            // 保存到本地数据库
            databaseHelper.addLocationRecord(record);
            
            Log.d(TAG, "✓ Saved to offline cache: device=" + deviceNum + 
                  ", lat=" + location.getLatitude() + 
                  ", lng=" + location.getLongitude() +
                  ", timestamp=" + location.getTimestamp());
                  
        } catch (Exception e) {
            Log.e(TAG, "✗ Error saving to offline cache", e);
        }
    }
    
    /**
     * 删除已上传的离线缓存
     * @param deviceNum 设备号
     * @param timestamp 时间戳
     */
    private void removeOfflineCache(String deviceNum, long timestamp) {
        if (databaseHelper == null) {
            return;
        }
        
        try {
            // 根据设备号和时间戳删除缓存记录
            databaseHelper.deleteLocationRecord(deviceNum, timestamp);
            
            Log.d(TAG, "✓ Removed offline cache for device: " + deviceNum + 
                  ", timestamp: " + timestamp);
                  
        } catch (Exception e) {
            Log.e(TAG, "✗ Error removing offline cache", e);
        }
    }
    
    /**
     * 更新数据库中设备的MAC地址字段
     * @param deviceNum 设备号
     * @param location 位置信息
     */
    private void updateDeviceMacInDatabase(String deviceNum, DeviceLocation location) {
        if (databaseHelper == null || deviceNum == null) {
            Log.w(TAG, "Cannot update MAC: databaseHelper or deviceNum is null");
            return;
        }
        
        new Thread(() -> {
            try {
                // 从映射表中查找对应的MAC地址
                String macAddress = deviceNumToMacMap.get(deviceNum);
                if (macAddress != null && !macAddress.isEmpty()) {
                    Log.d(TAG, "Updating MAC address in database for device: " + deviceNum + " -> " + macAddress);
                    
                    // 获取设备
                    Device device = databaseHelper.getDeviceByDeviceNum(deviceNum);
                    if (device != null) {
                        // 更新MAC地址
                        device.setMac(macAddress);
                        databaseHelper.addDevice(device);
                        Log.d(TAG, "✓ Updated MAC address in database for device: " + deviceNum);
                    } else {
                        Log.w(TAG, "Device not found in database for deviceNum: " + deviceNum);
                    }
                } else {
                    Log.d(TAG, "No MAC address in map for deviceNum: " + deviceNum + " (this is OK if already saved)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating MAC address in database", e);
                // 不抛出异常，避免影响主流程
            }
        }).start();
    }
    
    /**
     * 根据设备号获取设备名称
     */
    private String getDeviceNameByNum(String deviceNum) {
        if (databaseHelper == null || deviceNum == null) {
            return "未知设备";
        }
        
        try {
            Device device = databaseHelper.getDeviceByDeviceNum(deviceNum);
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
        Log.d(TAG, "=== triggerLocationUpdate START (fallback to server) ===");
        Log.d(TAG, "deviceId: " + deviceId + ", deviceNum: " + deviceNum);
        
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                Log.d(TAG, "Calling locationProvider.getDeviceLocation...");
                
                DeviceLocation location = locationProvider.getDeviceLocation(deviceId, deviceNum);
                
                long endTime = System.currentTimeMillis();
                Log.d(TAG, "Location query took: " + (endTime - startTime) + "ms");
                
                if (location != null && location.isValid()) {
                    Log.d(TAG, "========== Location Update Success ==========");
                    Log.d(TAG, "Latitude: " + location.getLatitude());
                    Log.d(TAG, "Longitude: " + location.getLongitude());
                    Log.d(TAG, "Accuracy: " + location.getAccuracy() + "m");
                    Log.d(TAG, "Source: " + location.getActualSource());
                    Log.d(TAG, "Timestamp: " + location.getDisplayTimeForLog());
                    Log.d(TAG, "Address: " + location.getAddress());
                    Log.d(TAG, "Battery: " + location.getBattery() + "%");
                    
                    // 保存到数据库
                    saveLocationToDevice(deviceId, location);
                    
                    // 添加Toast提示：开始通知UI更新（备用方法）
                    String toastMsg = "🔄 正在更新UI (备用)...";
                    Log.d(TAG, "TOAST: " + toastMsg);
                    runToast(toastMsg);
                    
                    // 通知外部回调（如果有）- 必须在主线程执行
                    if (externalCallback != null) {
                        Log.d(TAG, "Notifying external callback on main thread...");
                        mainHandler.post(() -> {
                            try {
                                externalCallback.onLocationUpdated(location);
                            } catch (Exception e) {
                                Log.e(TAG, "Error in external callback", e);
                            }
                        });
                    }
                    
                    Log.d(TAG, "=== Location Update Complete ===");
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
     * 
     * 关键说明：
     * - deviceId 参数实际上是蓝牙MAC地址（如 D4:DE:42:0F:57:7A）
     * - deviceNum 是16位纯数字设备号（如 1756726632035006）
     * - 两者是一一对应关系，首次从服务器获取后永久缓存在本地
     * 
     * @param macAddress 蓝牙MAC地址
     * @return 16位设备号，如果找不到返回null
     */
    private String getDeviceNumById(String macAddress) {
        if (macAddress == null || macAddress.isEmpty()) {
            Log.w(TAG, "MAC address is null or empty");
            return null;
        }
        
        // 标准化MAC地址（统一格式）
        String normalizedMac = normalizeMacAddress(macAddress);
        Log.d(TAG, "Looking up deviceNum for MAC: " + macAddress + " -> " + normalizedMac);
        
        // 首先尝试从内存映射表中查找（最快）
        String deviceNum = macToDeviceNumMap.get(normalizedMac);
        if (deviceNum != null && !deviceNum.isEmpty()) {
            Log.d(TAG, "✓ Found deviceNum from MAC map: " + deviceNum + " (16-digit)");
            return deviceNum;
        }
        
        // 如果映射表中没有，尝试从数据库查找（备用方案）
        if (databaseHelper != null) {
            try {
                Log.d(TAG, "MAC not in memory map, trying database lookup...");
                com.RockiotTag.tag.Device device = databaseHelper.getDevice(macAddress);
                if (device != null) {
                    deviceNum = device.getDeviceNum();
                    if (deviceNum != null && !deviceNum.isEmpty()) {
                        Log.d(TAG, "✓ Found deviceNum from database: " + deviceNum + " (16-digit)");
                        // 更新内存映射表，下次可以直接使用
                        macToDeviceNumMap.put(normalizedMac, deviceNum);
                        deviceNumToMacMap.put(deviceNum, normalizedMac);
                        return deviceNum;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting deviceNum from database", e);
            }
        }
        
        Log.w(TAG, "✗ Cannot find 16-digit deviceNum for MAC: " + macAddress);
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
                    Log.d(TAG, String.format(
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
            Device device = databaseHelper.getDevice(deviceId);
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
     * 关键改进：从服务器获取设备信息，建立设备号与MAC地址的映射关系
     */
    private Set<String> getBoundDeviceIds() {
        boundDeviceIds.clear();
        boundDeviceNames.clear();
        deviceNumToMacMap.clear();
        macToDeviceNumMap.clear();
        
        Log.d(TAG, "=== getBoundDeviceIds START ===");
        Log.d(TAG, "databaseHelper: " + (databaseHelper != null ? "NOT NULL" : "NULL"));
        
        if (databaseHelper != null) {
            try {
                Log.d(TAG, "Step 1: Loading bound devices from database...");
                java.util.List<Device> devices = databaseHelper.getAllDevices();
                Log.d(TAG, "getAllDevices returned: " + (devices != null ? "list with " + devices.size() + " items" : "NULL"));
                
                if (devices != null && !devices.isEmpty()) {
                    Log.d(TAG, "Found " + devices.size() + " devices in database");
                    
                    int macCount = 0;
                    int noMacCount = 0;
                    
                    for (Device device : devices) {
                        String deviceNum = device.getDeviceNum();
                        String deviceName = device.getName();
                        String deviceMac = device.getMac();
                        
                        if (deviceNum == null || deviceNum.isEmpty()) {
                            Log.w(TAG, "Skipping device with empty deviceNum: " + deviceName);
                            continue;
                        }
                        
                        Log.d(TAG, "Processing device: num=" + deviceNum + ", name=" + deviceName + ", mac=" + deviceMac);
                        
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
                        
                        // 如果数据库中已有MAC地址，直接加载
                        if (deviceMac != null && !deviceMac.isEmpty()) {
                            String normalizedMac = normalizeMacAddress(deviceMac);
                            deviceNumToMacMap.put(deviceNum, normalizedMac);
                            macToDeviceNumMap.put(normalizedMac, deviceNum);
                            boundDeviceIds.add(normalizedMac);
                            macCount++;
                            Log.d(TAG, "  ✓ Loaded MAC from database: " + normalizedMac);
                        } else {
                            noMacCount++;
                            Log.w(TAG, "  ✗ No MAC address for device: " + deviceNum);
                        }
                    }
                    
                    Log.d(TAG, "Summary: " + macCount + " devices with MAC, " + noMacCount + " without MAC");
                    Log.d(TAG, "Loaded " + boundDeviceNames.size() + " device names from database");
                    Log.d(TAG, "Loaded " + boundDeviceIds.size() + " MAC addresses from database");
                    
                    // 【关键优化】即使没有MAC地址，也立即启动蓝牙扫描（通过名称匹配）
                    // 对于没有MAC地址的设备，异步从服务器获取
                    boolean needFetchMac = noMacCount > 0;
                    if (needFetchMac) {
                        Log.d(TAG, "Some devices missing MAC, will fetch from server asynchronously...");
                        Log.d(TAG, "Starting BLE scan NOW with device names (will add MACs when fetched)");
                        // 异步获取MAC，但不阻塞蓝牙扫描启动
                        fetchMacAddressesFromServer(devices);
                    } else {
                        Log.d(TAG, "✓ All devices have MAC addresses, no need to fetch from server");
                    }
                } else {
                    Log.w(TAG, "No devices found in database");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting bound devices", e);
            }
        }
        
        Log.d(TAG, "=== getBoundDeviceIds END ===");
        Log.d(TAG, "Final bound device IDs (MACs): " + boundDeviceIds);
        Log.d(TAG, "Final bound device names: " + boundDeviceNames);
        Log.d(TAG, "DeviceNum to MAC map size: " + deviceNumToMacMap.size());
        return boundDeviceIds;
    }
    
    /**
     * 从服务器获取设备的MAC地址
     * @param devices 设备列表
     */
    private void fetchMacAddressesFromServer(java.util.List<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            Log.w(TAG, "No devices to fetch MAC addresses for");
            return;
        }
        
        new Thread(() -> {
            try {
                Log.d(TAG, "=== Fetching MAC addresses from server ===");
                
                // 使用第一个设备的URL来获取所有设备列表（假设所有设备在同一服务器）
                String firstDeviceNum = devices.get(0).getDeviceNum();
                String apiUrl = ApiConfig.getMyServerUrl(firstDeviceNum);
                NewApiService.setApiBaseUrl(apiUrl);
                Log.d(TAG, "Using API URL: " + apiUrl);
                
                // 只调用一次 /devices 接口获取所有设备列表（包含MAC地址）
                java.util.List<NewApiService.DeviceInfo> allDevices = apiService.getDevices();
                Log.d(TAG, "Got " + (allDevices != null ? allDevices.size() : 0) + " devices from server");
                
                if (allDevices == null || allDevices.isEmpty()) {
                    Log.e(TAG, "✗ Failed to get devices from server or empty list");
                    return;
                }
                
                // 打印服务器返回的所有设备信息（调试用）
                Log.d(TAG, "=== Server returned devices ===");
                boolean foundMyDevice = false;
                for (NewApiService.DeviceInfo info : allDevices) {
                    Log.d(TAG, "  Server device: num=" + info.deviceNum + ", name=" + info.nickName + ", mac=" + info.mac);
                    // 检查是否包含当前用户的设备
                    if (info.deviceNum != null && info.deviceNum.contains("1756726632035006")) {
                        foundMyDevice = true;
                        Log.d(TAG, "  >>> FOUND MY DEVICE: " + info.deviceNum + " with MAC: " + info.mac);
                    }
                }
                Log.d(TAG, "==============================");
                if (!foundMyDevice) {
                    Log.w(TAG, " Device 1756726632035006 (SC-35006) NOT found in server response!");
                    Log.w(TAG, "  This means MAC address cannot be fetched from server!");
                }
                
                // 为每个本地设备查找对应的MAC地址
                for (Device device : devices) {
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
                        
                        Log.d(TAG, "✓ Got MAC for device " + deviceNum + ": " + normalizedMac);
                        
                        // 更新本地设备的MAC地址
                        device.setMac(normalizedMac);
                        
                        // 添加到映射表
                        deviceNumToMacMap.put(deviceNum, normalizedMac);
                        macToDeviceNumMap.put(normalizedMac, deviceNum);
                        
                        // 添加到boundDeviceIds集合（用于蓝牙扫描匹配）
                        boundDeviceIds.add(normalizedMac);
                        
                        // 保存到数据库
                        databaseHelper.addDevice(device);
                        Log.d(TAG, "  Saved MAC to database for device: " + deviceNum);
                    } else {
                        Log.w(TAG, "✗ No MAC address found for device: " + deviceNum);
                    }
                }
                
                Log.d(TAG, "=== MAC address fetch complete ===");
                Log.d(TAG, "Total devices with MAC: " + deviceNumToMacMap.size());
                Log.d(TAG, "Bound device IDs (MACs): " + boundDeviceIds);
                
                // 【关键】如果蓝牙扫描已经在运行，重启它以应用新的MAC列表
                if (bluetoothScanner != null && isContinuousScanningActive()) {
                    Log.d(TAG, "Restarting BLE scanner with updated MAC list...");
                    stopBluetoothScanning();
                    startBluetoothScanning();
                    Log.d(TAG, "✓ BLE scanner restarted with new MAC addresses");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in fetchMacAddressesFromServer", e);
            }
        }).start();
    }
    
    /**
     * 标准化MAC地址格式
     * 统一转换为大写+冒号分隔格式，例如: D4:DE:42:0F:57:7A
     */
    private String normalizeMacAddress(String macAddress) {
        if (macAddress == null || macAddress.isEmpty()) {
            return "";
        }
        
        // 移除所有分隔符并转为大写
        String cleaned = macAddress.toUpperCase()
            .replace(":", "")
            .replace("-", "")
            .replace(" ", "")
            .trim();
        
        // 如果长度不是12，说明格式有问题，返回原始值
        if (cleaned.length() != 12) {
            Log.w(TAG, "Invalid MAC address format: " + macAddress + " (cleaned: " + cleaned + ")");
            return macAddress.toUpperCase();
        }
        
        // 重新格式化为标准格式: XX:XX:XX:XX:XX:XX
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) {
                sb.append(":");
            }
            sb.append(cleaned.substring(i, i + 2));
        }
        
        return sb.toString();
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
        Log.d(TAG, "Optimization " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * 设置调试模式（是否显示扫描Toast）
     * @param enabled true=显示Toast提示，false=不显示
     */
    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
        Log.d(TAG, "Debug mode (Toast) " + (enabled ? "enabled" : "disabled"));
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
            Device device = databaseHelper.getDeviceByDeviceNum(deviceNum);
            if (device != null) {
                int battery = device.getBattery();
                Log.d(TAG, "Got battery from database: " + battery + "% for deviceNum: " + deviceNum);
                return battery;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device battery: " + e.getMessage());
        }
        
        return -1;
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        stopBluetoothScanning();
        stopTimeRefresh();
        
        // 释放引用，防止内存泄漏
        if (locationProvider != null) {
            locationProvider = null;
        }
        if (bluetoothScanner != null) {
            bluetoothScanner = null;
        }
        if (phoneLocationService != null) {
            phoneLocationService = null;
        }
        
        Log.d(TAG, "Location optimization manager cleaned up");
    }
}
