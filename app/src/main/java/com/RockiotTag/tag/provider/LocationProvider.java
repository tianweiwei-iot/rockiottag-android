package com.RockiotTag.tag.provider;

import android.content.Context;
import android.util.Log;

import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.bluetooth.BluetoothScanResult;
import com.RockiotTag.tag.bluetooth.OptimizedBLEScanner;
import com.RockiotTag.tag.location.PhoneLocationService;
import com.RockiotTag.tag.model.DeviceLocation;
import com.RockiotTag.tag.model.PhoneLocation;
import com.RockiotTag.tag.util.AddressCache;

/**
 * 位置提供者 - 核心逻辑层
 * 智能融合手机GPS和服务器数据，提供最优的位置信息
 */
public class LocationProvider {
    
    private static final String TAG = "LocationProvider";
    
    private Context context;
    private OptimizedBLEScanner bluetoothScanner;
    private PhoneLocationService phoneLocationService;
    private NewApiService serverApiService;
    private AddressCache addressCache;
    
    public LocationProvider(
        Context context,
        OptimizedBLEScanner bluetoothScanner,
        PhoneLocationService phoneLocationService,
        NewApiService serverApiService
    ) {
        this.context = context;
        this.bluetoothScanner = bluetoothScanner;
        this.phoneLocationService = phoneLocationService;
        this.serverApiService = serverApiService;
        this.addressCache = new AddressCache();
    }
    
    /**
     * 获取设备位置（对外接口）
     * @param deviceId 设备ID
     * @param deviceNum 设备编号（用于服务器查询）
     * @return DeviceLocation对象
     */
    public synchronized DeviceLocation getDeviceLocation(String deviceId, String deviceNum) {
        Log.d(TAG, "========== LocationProvider.getDeviceLocation START ==========");
        Log.d(TAG, "deviceId: " + deviceId);
        Log.d(TAG, "deviceNum: " + deviceNum);
        
        // 1. 检查蓝牙是否在附近
        Log.d(TAG, "Step 1: Checking Bluetooth proximity...");
        BluetoothScanResult btResult = bluetoothScanner.getRecentScan(deviceId);
        boolean isNearby = bluetoothScanner.isDeviceNearby(deviceId);
        
        Log.d(TAG, "Bluetooth scan result: " + (btResult != null ? "found" : "not found"));
        if (btResult != null) {
            Log.d(TAG, "RSSI: " + btResult.getRssi() + " dBm");
            Log.d(TAG, "Scan time: " + new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(btResult.getScanTime())));
        }
        Log.d(TAG, "Is nearby: " + isNearby);
        
        if (isNearby && btResult != null) {
            Log.d(TAG, String.format(
                "✓ Device nearby detected: RSSI=%d dBm",
                btResult.getRssi()
            ));
            
            // 2. 尝试使用手机GPS定位
            Log.d(TAG, "Step 2: Attempting to get phone GPS location...");
            PhoneLocation phoneLoc = phoneLocationService.getCurrentLocation();
            
            if (phoneLoc != null) {
                Log.d(TAG, "Phone GPS available:");
                Log.d(TAG, "  Latitude: " + phoneLoc.getLatitude());
                Log.d(TAG, "  Longitude: " + phoneLoc.getLongitude());
                Log.d(TAG, "  Accuracy: " + phoneLoc.getAccuracy() + "m");
                Log.d(TAG, "  Is valid: " + phoneLoc.isValid());
            } else {
                Log.w(TAG, "Phone GPS not available (null)");
            }
            
            if (phoneLoc != null && phoneLoc.isValid() && phoneLoc.getAccuracy() < 20) {
                Log.d(TAG, "✓ Using phone GPS for better accuracy (< 20m)");
                
                DeviceLocation location = createFromPhone(phoneLoc);
                location.setActualSource(DeviceLocation.DataSource.PHONE_GPS);
                
                Log.d(TAG, "========== LocationProvider RETURNING PHONE GPS LOCATION ==========");
                return location;
            } else {
                Log.w(TAG, "✗ Phone GPS not suitable (invalid or accuracy >= 20m), fallback to server");
            }
        } else {
            Log.d(TAG, "Device not nearby or no recent scan");
        }
        
        // 3. 使用服务器数据
        Log.d(TAG, "Step 3: Fetching location from server...");
        DeviceLocation serverLocation = fetchFromServer(deviceNum);
        
        if (serverLocation != null && serverLocation.isValid()) {
            Log.d(TAG, "✓ Using server data");
            serverLocation.setActualSource(DeviceLocation.DataSource.SERVER);
            Log.d(TAG, "========== LocationProvider RETURNING SERVER LOCATION ==========");
            return serverLocation;
        }
        
        // 4. 降级到缓存
        Log.w(TAG, "Step 4: Server data not available, trying cache...");
        DeviceLocation cachedLocation = getCachedLocation(deviceId);
        
        if (cachedLocation != null) {
            Log.d(TAG, "✓ Using cached location");
            Log.d(TAG, "========== LocationProvider RETURNING CACHED LOCATION ==========");
            return cachedLocation;
        }
        
        Log.e(TAG, "========== LocationProvider FAILED: No location source available ==========");
        return null;
    }
    
    /**
     * 从手机定位创建DeviceLocation
     */
    private DeviceLocation createFromPhone(PhoneLocation phoneLoc) {
        DeviceLocation loc = new DeviceLocation();
        loc.setLatitude(phoneLoc.getLatitude());
        loc.setLongitude(phoneLoc.getLongitude());
        loc.setAccuracy(phoneLoc.getAccuracy());
        loc.setTimestamp(System.currentTimeMillis());  // 使用当前时间
        
        // 逆地理编码获取地址（带缓存）
        String cachedAddress = addressCache.getAddress(
            phoneLoc.getLatitude(), 
            phoneLoc.getLongitude()
        );
        
        if (cachedAddress != null) {
            loc.setAddress(cachedAddress);
            Log.d(TAG, "Using cached address: " + cachedAddress);
        } else {
            // TODO: 异步调用逆地理编码API
            loc.setAddress("正在获取地址...");
        }
        
        // 使用缓存的电量（蓝牙扫描时不获取电池电量，设置为-1表示未知）
        loc.setBattery(-1);
        
        Log.d(TAG, String.format(
            "Created location from phone GPS: lat=%.6f, lng=%.6f, accuracy=%.1fm",
            loc.getLatitude(), loc.getLongitude(), loc.getAccuracy()
        ));
        
        return loc;
    }
    
    /**
     * 从服务器获取位置
     */
    private DeviceLocation fetchFromServer(String deviceNum) {
        try {
            // 调用服务器API获取最新位置
            NewApiService.DeviceInfo deviceInfo = serverApiService.getDeviceLatest(deviceNum);
            
            if (deviceInfo != null) {
                DeviceLocation loc = new DeviceLocation();
                loc.setLatitude(deviceInfo.latitude);
                loc.setLongitude(deviceInfo.longitude);
                loc.setAccuracy(25.0f); // 服务器数据精度较低，设为25米
                loc.setTimestamp(deviceInfo.timestamp);
                loc.setAddress(deviceInfo.address);
                loc.setBattery(deviceInfo.battery);
                
                // 缓存地址
                if (deviceInfo.address != null && !deviceInfo.address.isEmpty()) {
                    addressCache.putAddress(deviceInfo.latitude, deviceInfo.longitude, deviceInfo.address);
                }
                
                Log.d(TAG, String.format(
                    "Fetched location from server: lat=%.6f, lng=%.6f, time=%s",
                    loc.getLatitude(), loc.getLongitude(), loc.getDisplayTimeForLog()
                ));
                
                return loc;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching from server", e);
        }
        
        return null;
    }
    
    /**
     * 获取缓存位置
     */
    private DeviceLocation getCachedLocation(String deviceId) {
        // TODO: 从数据库或内存缓存获取
        // 这里简化处理，返回null
        Log.w(TAG, "No cached location available for device: " + deviceId);
        return null;
    }
    
    /**
     * 获取最后已知电量
     */
    private int getLastKnownBattery() {
        // TODO: 从数据库或SharedPreferences获取
        // 这里简化处理，返回默认值
        return 85;
    }
}
