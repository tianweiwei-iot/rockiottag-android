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
    private static final long SCAN_PERIOD = 10000; // 10 seconds

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private ScanCallback scanCallback;
    private Handler handler;
    private DeviceScanCallback userCallback;
    private DeviceConnectionCallback connectionCallback;

    public BLEManager(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        this.handler = new Handler();
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void startScanning(final DeviceScanCallback callback) {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter is not available");
            if (callback != null) {
                // 可以考虑添加onScanFailed回调
            }
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled");
            if (callback != null) {
                // 可以考虑添加onScanFailed回调
            }
            return;
        }

        if (bluetoothLeScanner == null) {
            Log.e(TAG, "Bluetooth LE scanner is not available");
            if (callback != null) {
                // 可以考虑添加onScanFailed回调
            }
            return;
        }

        this.userCallback = callback;

        // 配置扫描设置
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        // 配置扫描过滤器（可选）
        List<ScanFilter> filters = new ArrayList<>();
        // 这里可以添加特定设备的过滤条件

        // 开始扫描
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                try {
                    BluetoothDevice device = result.getDevice();
                    int rssi = result.getRssi();

                    // 创建设备对象
                    Device deviceObj = new Device(device.getAddress(), device.getName() != null ? device.getName() : "Unknown Device");
                    deviceObj.setSignalStrength(rssi);

                    // 调用用户回调
                    if (userCallback != null) {
                        userCallback.onDeviceFound(deviceObj);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing scan result: " + e.getMessage());
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                try {
                    for (ScanResult result : results) {
                        BluetoothDevice device = result.getDevice();
                        int rssi = result.getRssi();

                        Device deviceObj = new Device(device.getAddress(), device.getName() != null ? device.getName() : "Unknown Device");
                        deviceObj.setSignalStrength(rssi);

                        if (userCallback != null) {
                            userCallback.onDeviceFound(deviceObj);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing batch scan results: " + e.getMessage());
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "Scan failed with error code: " + errorCode);
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
                Log.e(TAG, "Scan failed: " + errorMessage);
            }
        };

        try {
            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            Log.d(TAG, "Started BLE scanning");

            // 定时停止扫描
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopScanning();
                    if (userCallback != null) {
                        userCallback.onScanComplete();
                    }
                }
            }, SCAN_PERIOD);
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when starting scan: " + e.getMessage());
            // 这里需要处理权限错误
        } catch (Exception e) {
            Log.e(TAG, "Error starting scan: " + e.getMessage());
        }
    }

    public void stopScanning() {
        if (bluetoothLeScanner != null && scanCallback != null) {
            bluetoothLeScanner.stopScan(scanCallback);
            scanCallback = null;
            Log.d(TAG, "Stopped BLE scanning");
        }
    }

    // 连接到设备
    public void connectToDevice(BluetoothDevice device, final DeviceConnectionCallback callback) {
        if (device == null) {
            Log.e(TAG, "Bluetooth device is null");
            if (callback != null) {
                // 可以考虑添加错误回调
            }
            return;
        }

        if (bluetoothGatt != null) {
            Log.d(TAG, "Closing existing GATT connection");
            try {
                bluetoothGatt.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing GATT connection: " + e.getMessage());
            }
            bluetoothGatt = null;
        }
        
        this.connectionCallback = callback;
        
        try {
            Log.d(TAG, "Connecting to device: " + device.getName() + " (" + device.getAddress() + ")");
            bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    try {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.e(TAG, "Connection state change failed with status: " + status);
                            if (connectionCallback != null) {
                                // 可以考虑添加错误回调
                            }
                            return;
                        }

                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d(TAG, "Connected to GATT server");
                            // 连接成功后发现服务
                            boolean success = gatt.discoverServices();
                            Log.d(TAG, "Discover services started: " + success);
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.d(TAG, "Disconnected from GATT server");
                            if (connectionCallback != null) {
                                connectionCallback.onDisconnected();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onConnectionStateChange: " + e.getMessage());
                    }
                }
                
                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "Services discovered successfully");
                            // 打印发现的服务
                            List<BluetoothGattService> services = gatt.getServices();
                            for (BluetoothGattService service : services) {
                                Log.d(TAG, "Found service: " + service.getUuid());
                            }
                            if (connectionCallback != null) {
                                connectionCallback.onConnected();
                            }
                        } else {
                            Log.e(TAG, "onServicesDiscovered failed with status: " + status);
                            if (connectionCallback != null) {
                                // 可以考虑添加错误回调
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onServicesDiscovered: " + e.getMessage());
                    }
                }
                
                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "Characteristic read: " + characteristic.getUuid());
                            // 处理读取到的数据
                            if (connectionCallback != null) {
                                connectionCallback.onDataReceived(characteristic.getValue());
                            }
                        } else {
                            Log.e(TAG, "Characteristic read failed with status: " + status);
                            if (connectionCallback != null) {
                                // 可以考虑添加错误回调
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onCharacteristicRead: " + e.getMessage());
                    }
                }
                
                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "Characteristic written successfully: " + characteristic.getUuid());
                            if (connectionCallback != null) {
                                connectionCallback.onWriteSuccess();
                            }
                        } else {
                            Log.e(TAG, "Characteristic write failed with status: " + status);
                            if (connectionCallback != null) {
                                connectionCallback.onWriteFailed();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onCharacteristicWrite: " + e.getMessage());
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    try {
                        Log.d(TAG, "Characteristic changed: " + characteristic.getUuid());
                        // 处理特征值变化
                        if (connectionCallback != null) {
                            connectionCallback.onDataReceived(characteristic.getValue());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onCharacteristicChanged: " + e.getMessage());
                    }
                }

                @Override
                public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "Reliable write completed successfully");
                        } else {
                            Log.e(TAG, "Reliable write completed with status: " + status);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onReliableWriteCompleted: " + e.getMessage());
                    }
                }

                @Override
                public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "Remote RSSI: " + rssi + " dBm");
                        } else {
                            Log.e(TAG, "Read remote RSSI failed with status: " + status);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onReadRemoteRssi: " + e.getMessage());
                    }
                }

                @Override
                public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "MTU changed to: " + mtu);
                        } else {
                            Log.e(TAG, "MTU change failed with status: " + status);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onMtuChanged: " + e.getMessage());
                    }
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when connecting: " + e.getMessage());
            if (callback != null) {
                // 可以考虑添加错误回调
            }
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to device: " + e.getMessage());
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