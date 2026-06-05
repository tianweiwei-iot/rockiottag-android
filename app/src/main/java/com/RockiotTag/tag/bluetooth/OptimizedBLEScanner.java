package com.RockiotTag.tag.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.RockiotTag.tag.Device;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 优化的BLE扫描器
 * 用于持续扫描附近的蓝牙设备，特别关注已绑定的mTag设备
 */
public class OptimizedBLEScanner {
    
    private static final String TAG = "OptimizedBLEScanner";
    
    // 扫描间隔配置（毫秒）- 扫描10秒，休息10秒
    private static final long SCAN_DURATION = 10000;  // 每次扫描持续10秒
    private static final long SCAN_INTERVAL = 10000;  // 停止间隔10秒
    
    // 扫描状态回调接口
    public interface ScanStateCallback {
        void onScanStarted();      // 扫描开始
        void onScanStopped();      // 扫描停止（休息中）
        void onDeviceFound(Device device);  // 发现设备
    }
    
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private Handler handler;
    
    // 已绑定设备的MAC地址和名称
    private Set<String> boundDeviceMacs;
    private List<String> boundDeviceNames;
    
    // 最近扫描结果缓存：MAC地址 -> 扫描结果
    private Map<String, BluetoothScanResult> recentScans;
    
    // BLE扫描回调
    private android.bluetooth.le.ScanCallback leScanCallback;
    
    // 扫描状态
    private volatile boolean isScanning = false;
    private volatile boolean isContinuousScanning = false; // 标记是否处于持续扫描模式
    private Runnable stopScanRunnable;
    
    // 用户回调接口（已废弃，使用ScanStateCallback代替）
    @Deprecated
    public interface ScanCallback {
        void onDeviceFound(Device device);
        void onScanComplete();
    }
    
    private ScanCallback userCallback;
    private ScanStateCallback scanStateCallback;
    
    public OptimizedBLEScanner(Context context, Set<String> boundDeviceMacs, List<String> boundDeviceNames) {
        this.context = context;
        this.boundDeviceMacs = boundDeviceMacs;
        this.boundDeviceNames = boundDeviceNames;
        this.recentScans = new ConcurrentHashMap<>();
        this.handler = new Handler(Looper.getMainLooper());
        
        // 初始化蓝牙适配器
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        
        Log.d(TAG, "OptimizedBLEScanner initialized with " + boundDeviceMacs.size() + " bound devices");
    }
    
    /**
     * 开始持续扫描（不间断模式）- 扫描10秒，休息10秒
     */
    public void startContinuousScanning(final ScanCallback callback) {
        this.userCallback = callback;
        this.isContinuousScanning = true; // 标记为持续扫描模式
        
        if (!isBluetoothAvailable()) {
            Log.e(TAG, "Bluetooth not available - check permissions and Bluetooth state");
            return;
        }
        
        Log.d(TAG, "Starting continuous BLE scanning (scan 10s, rest 10s)...");
        
        // 立即开始第一次扫描
        performScan();
    }
    
    /**
     * 开始持续扫描（带状态回调）- 推荐使用
     */
    public void startContinuousScanningWithState(ScanStateCallback callback) {
        this.scanStateCallback = callback;
        this.isContinuousScanning = true;

        if (!isBluetoothAvailable()) {
            Log.e(TAG, "Bluetooth not available");
            return;
        }

        Log.d(TAG, "Starting continuous BLE scanning with state callback...");
        performScan();
    }

    /**
     * 开始单次扫描（低强度）：扫描10秒后自动停止，不循环
     */
    public void startSingleScanWithState(ScanStateCallback callback) {
        this.scanStateCallback = callback;
        this.isContinuousScanning = false; // 不循环

        if (!isBluetoothAvailable()) {
            Log.e(TAG, "Bluetooth not available");
            return;
        }

        Log.d(TAG, "Starting single BLE scan (will stop after one cycle)...");
        performScan();
    }
    
    /**
     * 执行单次扫描
     */
    private void performScan() {
        // 【关键修复】每次扫描前重新获取bluetoothLeScanner，防止为null
        if (bluetoothAdapter != null) {
            this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        
        if (!isBluetoothAvailable()) {
            Log.w(TAG, "Bluetooth not available for scanning");
            return;
        }
        
        if (isScanning) {
            Log.d(TAG, "Already scanning, skip this cycle (continuous mode)");
            return;
        }
        
        Log.d(TAG, "=== Starting BLE scan cycle (duration: " + SCAN_DURATION + "ms) ===");
        Log.d(TAG, "Bound devices count: " + (boundDeviceMacs != null ? boundDeviceMacs.size() : 0));
        
        // 通知扫描开始
        if (scanStateCallback != null) {
            scanStateCallback.onScanStarted();
        }
        
        // 【最强扫描模式】不考虑功耗，使用最高强度的扫描设置
        ScanSettings.Builder settingsBuilder = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);  // 最低延迟，最快发现设备
        
        // 仅在 API 23+ 上设置高级选项
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)  // 最积极的匹配模式
                    .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)  // 最大匹配数
                    .setReportDelay(0);  // 无延迟立即报告（关键优化）
        }
        
        ScanSettings settings = settingsBuilder.build();
        
        // 创建扫描过滤器（可选，这里不过滤任何设备）
        List<ScanFilter> filters = new ArrayList<>();
        
        // 创建BLE扫描回调
        leScanCallback = new android.bluetooth.le.ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                // 【调试日志】记录每次扫描结果（包括所有设备）
                if (result != null && result.getDevice() != null) {
                    String mac = result.getDevice().getAddress();
                    int rssi = result.getRssi();
                    Log.v(TAG, "📡 Scan result: MAC=" + mac + ", RSSI=" + rssi);
                }
                processScanResult(result);
            }
            
            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
                Log.d(TAG, "📦 Batch scan: " + results.size() + " devices");
                for (ScanResult result : results) {
                    processScanResult(result);
                }
            }
            
            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e(TAG, "❌ BLE scan failed with error code: " + errorCode);
                isScanning = false;
                // 扫描失败后，等待下一个周期会自动重试
                Log.d(TAG, "Scan failed, will retry in next cycle");
            }
        };
        
        // 开始扫描
        try {
            isScanning = true;
            bluetoothLeScanner.startScan(filters, settings, leScanCallback);
            
            Log.d(TAG, "✅ BLE scan started with MAX POWER mode");
            Log.d(TAG, "   Scan mode: LOW_LATENCY (fastest)");
            Log.d(TAG, "   Match mode: AGGRESSIVE");
            Log.d(TAG, "   Report delay: 0ms (immediate)");
            Log.d(TAG, "   Duration: " + SCAN_DURATION + "ms per cycle");
            
            // 设置停止扫描的延迟任务 - 扫描10秒后停止，然后等待10秒再重新启动
            stopScanRunnable = new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "⏹️ Stopping scan after 10 seconds, entering rest period...");
                    stopScanningInternal();
                    
                    // 通知扫描停止（进入休息期）
                    if (scanStateCallback != null) {
                        scanStateCallback.onScanStopped();
                    }
                    
                    // 等待10秒后重新启动扫描
                    if (isContinuousScanning) {
                        Log.d(TAG, "✓ Rest period started, will restart in " + SCAN_INTERVAL + "ms...");
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "🔄 Restarting BLE scan after rest period...");
                                performScan();
                            }
                        }, SCAN_INTERVAL);
                    }
                }
            };
            handler.postDelayed(stopScanRunnable, SCAN_DURATION);
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when starting scan", e);
            isScanning = false;
        } catch (Exception e) {
            Log.e(TAG, "Error starting BLE scan", e);
            isScanning = false;
        }
    }
    
    /**
     * 处理扫描结果
     */
    private void processScanResult(ScanResult result) {
        if (result == null || result.getDevice() == null) {
            return;
        }
        
        try {
            String macAddress = result.getDevice().getAddress();
            int rssi = result.getRssi();
            long timestamp = System.currentTimeMillis();
            
            // 标准化MAC地址
            String normalizedMac = normalizeMacAddress(macAddress);
            
            // 获取设备名称（需要权限检查）
            String deviceName = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ 需要 BLUETOOTH_CONNECT 权限
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                            == PackageManager.PERMISSION_GRANTED) {
                        deviceName = result.getDevice().getName();
                    }
                } else {
                    // Android 11及以下，直接获取
                    deviceName = result.getDevice().getName();
                }
            } catch (SecurityException e) {
                Log.w(TAG, "Cannot get device name, permission denied: " + e.getMessage());
            }
            
            // 检查是否是已绑定的设备（通过MAC地址匹配）
            if (boundDeviceMacs.contains(normalizedMac)) {
                Log.d(TAG, "✓ Found bound device: MAC=" + normalizedMac + 
                      ", Name=" + deviceName + ", RSSI=" + rssi);
                
                // 保存扫描结果
                BluetoothScanResult scanResult = new BluetoothScanResult(normalizedMac, rssi, timestamp);
                recentScans.put(normalizedMac, scanResult);
                
                // 创建设备对象并通知回调
                Device device = new Device(normalizedMac, deviceName != null ? deviceName : "未知设备");
                device.setSignalStrength(rssi);
                device.setLastSeen(timestamp);
                device.setNearby(true);
                device.setBluetoothScanTime(timestamp);
                device.setMac(normalizedMac);
                
                // 通知用户回调（如果设置了）
                if (userCallback != null) {
                    userCallback.onDeviceFound(device);
                }
                
                // 通知状态回调（主要使用这个）
                if (scanStateCallback != null) {
                    scanStateCallback.onDeviceFound(device);
                }
            } else if (deviceName != null && isBoundDeviceByName(deviceName)) {
                // 也支持通过名称匹配（兼容性考虑）
                Log.d(TAG, "✓ Found bound device by name: Name=" + deviceName + 
                      ", MAC=" + normalizedMac + ", RSSI=" + rssi);
                
                // 保存扫描结果
                BluetoothScanResult scanResult = new BluetoothScanResult(normalizedMac, rssi, timestamp);
                recentScans.put(normalizedMac, scanResult);
                
                // 创建设备对象并通知回调
                Device device = new Device(normalizedMac, deviceName);
                device.setSignalStrength(rssi);
                device.setLastSeen(timestamp);
                device.setNearby(true);
                device.setBluetoothScanTime(timestamp);
                device.setMac(normalizedMac);
                
                // 通知用户回调
                if (userCallback != null) {
                    userCallback.onDeviceFound(device);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing scan result", e);
        }
    }
    
    /**
     * 检查设备名称是否是已绑定设备
     */
    private boolean isBoundDeviceByName(String deviceName) {
        if (deviceName == null || boundDeviceNames == null || boundDeviceNames.isEmpty()) {
            return false;
        }
        
        for (String boundName : boundDeviceNames) {
            if (deviceName.contains(boundName) || boundName.contains(deviceName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 标准化MAC地址格式
     */
    private String normalizeMacAddress(String macAddress) {
        if (macAddress == null) {
            return "";
        }
        
        // 转换为大写并去除空格
        String normalized = macAddress.toUpperCase().trim();
        
        // 统一使用冒号分隔符
        normalized = normalized.replace("-", ":");
        
        return normalized;
    }
    
    /**
     * 停止扫描（内部方法）- 用于扫描周期结束时的临时停止
     */
    private void stopScanningInternal() {
        if (!isScanning) {
            return;
        }
        
        try {
            if (bluetoothLeScanner != null && leScanCallback != null) {
                // 检查权限后再停止扫描
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                            == PackageManager.PERMISSION_GRANTED) {
                        bluetoothLeScanner.stopScan(leScanCallback);
                        Log.d(TAG, "BLE scanning stopped (cycle end)");
                    } else {
                        Log.w(TAG, "Cannot stop scan, missing BLUETOOTH_SCAN permission");
                    }
                } else {
                    bluetoothLeScanner.stopScan(leScanCallback);
                    Log.d(TAG, "BLE scanning stopped (cycle end)");
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when stopping BLE scan", e);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping BLE scan", e);
        } finally {
            isScanning = false;
            
            // 移除停止扫描的延迟任务
            if (stopScanRunnable != null) {
                handler.removeCallbacks(stopScanRunnable);
                stopScanRunnable = null;
            }
        }
    }
    
    /**
     * 停止扫描（公共方法）- 完全停止持续扫描
     */
    public void stopScanning() {
        Log.d(TAG, "Stopping continuous BLE scanning...");
        isContinuousScanning = false; // 退出持续扫描模式
        
        // 移除所有待执行的扫描任务
        handler.removeCallbacksAndMessages(null);
        
        stopScanningInternal();
        
        // 通知回调扫描已完全停止
        if (userCallback != null) {
            userCallback.onScanComplete();
        }
        
        Log.d(TAG, "✓ BLE scanning completely stopped");
    }
    
    /**
     * 检查设备是否在附近（5分钟内有扫描记录）
     */
    public boolean isDeviceNearby(String deviceId) {
        if (deviceId == null) {
            return false;
        }
        
        String normalizedMac = normalizeMacAddress(deviceId);
        BluetoothScanResult result = recentScans.get(normalizedMac);
        
        if (result == null) {
            return false;
        }
        
        // 检查扫描结果是否有效（5分钟内）
        return result.isValid();
    }
    
    /**
     * 获取最近的扫描结果
     */
    public BluetoothScanResult getRecentScan(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        
        String normalizedMac = normalizeMacAddress(deviceId);
        return recentScans.get(normalizedMac);
    }
    
    /**
     * 检查蓝牙是否可用
     */
    private boolean isBluetoothAvailable() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter is null");
            return false;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth is not enabled");
            return false;
        }
        
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null");
            return false;
        }
        
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要 BLUETOOTH_SCAN 权限
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing BLUETOOTH_SCAN permission");
                return false;
            }
        } else {
            // Android 11及以下需要 ACCESS_FINE_LOCATION 权限
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 清理过期的扫描结果
     */
    public void cleanupExpiredScans() {
        long currentTime = System.currentTimeMillis();
        long expireTime = 5 * 60 * 1000; // 5分钟
        
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, BluetoothScanResult> entry : recentScans.entrySet()) {
            if (currentTime - entry.getValue().getScanTime() > expireTime) {
                expiredKeys.add(entry.getKey());
            }
        }
        
        for (String key : expiredKeys) {
            recentScans.remove(key);
        }
        
        if (!expiredKeys.isEmpty()) {
            Log.d(TAG, "Cleaned up " + expiredKeys.size() + " expired scan results");
        }
    }
}
