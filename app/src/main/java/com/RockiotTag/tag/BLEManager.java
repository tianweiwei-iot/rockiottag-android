package com.RockiotTag.tag;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BLEManager {
    private static final String TAG = "BLEManager";
    private static final long SCAN_PERIOD = 5000; // 5 seconds - 优化：缩短扫描周期提高响应速度

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private ScanCallback scanCallback;
    private Handler handler;
    private DeviceScanCallback userCallback;
    private DeviceConnectionCallback connectionCallback;
    
    // 用于停止扫描的 Runnable，方便移除回调
    private Runnable stopScanRunnable;

    public BLEManager(Context context) {
        // 关键修复：使用 ApplicationContext 防止内存泄漏
        this.context = context.getApplicationContext();
        // 关键修复：BluetoothAdapter.getDefaultAdapter() 可能返回 null
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        } else {
            Log.w(TAG, "Bluetooth adapter is not available on this device");
        }
        this.handler = new Handler();
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void startScanning(final DeviceScanCallback callback) {
        Log.d(TAG, "=== BLE Scanning Starting ===");
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter is null - device may not support BLE");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled");
            return;
        }

        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null");
            return;
        }
        
        Log.d(TAG, "Bluetooth adapter: OK");
        Log.d(TAG, "Bluetooth enabled: YES");
        Log.d(TAG, "BLE scanner: OK");

        this.userCallback = callback;

        // 配置扫描设置
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        // 配置扫描过滤器（可选）
        List<ScanFilter> filters = new ArrayList<>();
        // 这里可以添加特定设备的过滤条件

        Log.d(TAG, "Scan settings configured: LOW_LATENCY mode");
        Log.d(TAG, "Starting BLE scan...");

        // 开始扫描
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                try {
                    BluetoothDevice device = result.getDevice();
                    int rssi = result.getRssi();
                    String deviceName = device.getName() != null ? device.getName() : "Unknown";
                    String deviceAddress = device.getAddress();

                    // 创建设备对象
                    Device deviceObj = new Device(deviceAddress, deviceName);
                    deviceObj.setSignalStrength(rssi);

                    // 调用用户回调
                    if (userCallback != null) {
                        userCallback.onDeviceFound(deviceObj);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in onScanResult", e);
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                try {
                    for (ScanResult result : results) {
                        BluetoothDevice device = result.getDevice();
                        int rssi = result.getRssi();
                        String deviceName = device.getName() != null ? device.getName() : "Unknown";
                        String deviceAddress = device.getAddress();

                        Device deviceObj = new Device(deviceAddress, deviceName);
                        deviceObj.setSignalStrength(rssi);

                        if (userCallback != null) {
                            userCallback.onDeviceFound(deviceObj);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in onBatchScanResults", e);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "!!! BLE Scan Failed !!!");
                
                // 这里可以根据错误代码提供更具体的错误信息
                String errorMessage = "Unknown error";
                switch (errorCode) {
                    case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                        errorMessage = "Scan already started";
                        break;
                    case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                        errorMessage = "Application registration failed";
                        break;
                    case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                        errorMessage = "BLE scanning not supported";
                        break;
                    case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                        errorMessage = "Internal error";
                        break;
                }
                
                Log.e(TAG, "Error code: " + errorCode + " - " + errorMessage);
            }
        };

        try {
            bluetoothLeScanner.startScan(filters, settings, scanCallback);

            // 定时停止扫描
            stopScanRunnable = new Runnable() {
                @Override
                public void run() {
                    stopScanning();
                    if (userCallback != null) {
                        userCallback.onScanComplete();
                    }
                }
            };
            handler.postDelayed(stopScanRunnable, SCAN_PERIOD);
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception starting scan", e);
        } catch (Exception e) {
            Log.e(TAG, "Error starting scan", e);
        }
    }

    public void stopScanning() {
        Log.d(TAG, "=== Stopping BLE Scanning ===");
        
        // 移除停止扫描的回调，防止内存泄漏
        if (stopScanRunnable != null && handler != null) {
            handler.removeCallbacks(stopScanRunnable);
            stopScanRunnable = null;
            Log.d(TAG, "Removed stop scan runnable");
        }
        
        if (bluetoothLeScanner != null && scanCallback != null) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                scanCallback = null;
                Log.d(TAG, "✓ BLE scan stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping scan", e);
            }
        } else {
            Log.d(TAG, "Scan already stopped or not started");
        }
        
        // 关键修复：不要清空userCallback，让调用者决定何时清理
        // userCallback = null;  // 注释掉这行，保持回调有效
        Log.d(TAG, "Keep user callback for restart");
    }

    // 连接到设备
    public void connectToDevice(BluetoothDevice device, final DeviceConnectionCallback callback) {
        if (device == null) {

            if (callback != null) {
                // 可以考虑添加错误回调
            }
            return;
        }

        if (bluetoothGatt != null) {

            try {
                bluetoothGatt.close();
            } catch (Exception e) {

            }
            bluetoothGatt = null;
        }
        
        this.connectionCallback = callback;
        
        try {

            bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    try {
                        if (status != BluetoothGatt.GATT_SUCCESS) {

                            if (connectionCallback != null) {
                                // 可以考虑添加错误回调
                            }
                            return;
                        }

                        if (newState == BluetoothProfile.STATE_CONNECTED) {

                            // 连接成功后发现服务
                            boolean success = gatt.discoverServices();

                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                            if (connectionCallback != null) {
                                connectionCallback.onDisconnected();
                            }
                        }
                    } catch (Exception e) {

                    }
                }
                
                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {

                            // 打印发现的服务
                            List<BluetoothGattService> services = gatt.getServices();
                            for (BluetoothGattService service : services) {

                            }
                            if (connectionCallback != null) {
                                connectionCallback.onConnected();
                            }
                        } else {

                            if (connectionCallback != null) {
                                // 可以考虑添加错误回调
                            }
                        }
                    } catch (Exception e) {

                    }
                }
                
                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {

                            // 处理读取到的数据
                            if (connectionCallback != null) {
                                connectionCallback.onDataReceived(characteristic.getValue());
                            }
                        } else {

                            if (connectionCallback != null) {
                                // 可以考虑添加错误回调
                            }
                        }
                    } catch (Exception e) {

                    }
                }
                
                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {

                            if (connectionCallback != null) {
                                connectionCallback.onWriteSuccess();
                            }
                        } else {

                            if (connectionCallback != null) {
                                connectionCallback.onWriteFailed();
                            }
                        }
                    } catch (Exception e) {

                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    try {

                        // 处理特征值变化
                        if (connectionCallback != null) {
                            connectionCallback.onDataReceived(characteristic.getValue());
                        }
                    } catch (Exception e) {

                    }
                }

                @Override
                public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {

                        } else {

                        }
                    } catch (Exception e) {

                    }
                }

                @Override
                public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {

                        } else {

                        }
                    } catch (Exception e) {

                    }
                }

                @Override
                public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {

                        } else {

                        }
                    } catch (Exception e) {

                    }
                }
            });
        } catch (SecurityException e) {

            if (callback != null) {
                // 可以考虑添加错误回调
            }
        } catch (Exception e) {

            if (callback != null) {
                // 可以考虑添加错误回调
            }
        }
    }

    // 断开连接
    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    // 获取设备电量
    public void getBatteryLevel() {
        if (bluetoothGatt != null) {
            BluetoothGattService batteryService = bluetoothGatt.getService(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"));
            if (batteryService != null) {
                BluetoothGattCharacteristic batteryChar = batteryService.getCharacteristic(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"));
                if (batteryChar != null) {
                    bluetoothGatt.readCharacteristic(batteryChar);
                }
            }
        }
    }

    // 控制蜂鸣器
    public void controlBuzzer(boolean enable) {
        if (bluetoothGatt != null) {
            // 这里需要根据实际的设备协议实现蜂鸣器控制
            // 假设设备有一个自定义服务和特征用于控制蜂鸣器
            BluetoothGattService customService = bluetoothGatt.getService(UUID.fromString("YOUR_CUSTOM_SERVICE_UUID"));
            if (customService != null) {
                BluetoothGattCharacteristic buzzerChar = customService.getCharacteristic(UUID.fromString("YOUR_BUZZER_CHARACTERISTIC_UUID"));
                if (buzzerChar != null) {
                    byte[] data = new byte[1];
                    data[0] = (byte) (enable ? 1 : 0);
                    buzzerChar.setValue(data);
                    bluetoothGatt.writeCharacteristic(buzzerChar);
                }
            }
        }
    }

    // 获取GATT连接
    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }

    // 检查是否已连接
    public boolean isConnected() {
        return bluetoothGatt != null;
    }

    // 回调接口
    public interface DeviceScanCallback {
        void onDeviceFound(Device device);
        void onScanComplete();
    }

    // 设备连接回调接口
    public interface DeviceConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onDataReceived(byte[] data);
        void onWriteSuccess();
        void onWriteFailed();
    }
}