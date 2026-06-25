package com.RockiotTag.tag.repository;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import com.RockiotTag.tag.BLEManager;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;

/**
 * BLE数据仓库 - 统一管理蓝牙设备扫描和连接操作
 */
public class BLERepository {
    private static final String TAG = "BLERepository";
    
    private BLEManager bleManager;
    private static BLERepository instance;
    
    public static synchronized BLERepository getInstance(Context context) {
        if (instance == null) {
            instance = new BLERepository(context);
        }
        return instance;
    }
    
    private BLERepository(Context context) {
        this.bleManager = new BLEManager(context);
    }
    
    /**
     * 检查蓝牙是否启用
     */
    public boolean isBluetoothEnabled() {
        return bleManager.isBluetoothEnabled();
    }
    
    /**
     * 扫描结果回调接口（使用新模型）
     */
    public interface ScanCallback {
        void onDeviceFound(TagDevice device);
        void onScanFinished();
    }
    
    /**
     * 开始扫描BLE设备
     */
    public void startScanning(ScanCallback callback) {
        LogUtil.d(TAG, "Starting BLE scan with new model");
        bleManager.startScanning(new BLEManager.DeviceScanCallback() {
            @Override
            public void onDeviceFound(TagDevice device) {
                if (callback != null) {
                    TagDevice td = new TagDevice(device.getDeviceId(), device.getName());
                    td.setMac(device.getMac());
                    td.setSignalStrength(device.getSignalStrength());
                    callback.onDeviceFound(td);
                }
            }

            @Override
            public void onScanComplete() {
                if (callback != null) {
                    callback.onScanFinished();
                }
            }
        });
    }
    
    /**
     * 停止扫描BLE设备
     */
    public void stopScanning() {
        LogUtil.d(TAG, "Stopping BLE scan");
        bleManager.stopScanning();
    }
    
    /**
     * 连接到BLE设备
     */
    public void connectToDevice(BluetoothDevice device, BLEManager.DeviceConnectionCallback callback) {
        LogUtil.d(TAG, "Connecting to BLE device: " + device.getName());
        bleManager.connectToDevice(device, callback);
    }
    
    /**
     * 断开BLE连接
     */
    public void disconnect() {
        LogUtil.d(TAG, "Disconnecting BLE device");
        bleManager.disconnect();
    }
    
    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return bleManager.isConnected();
    }
    
    /**
     * 获取电池电量
     */
    public void getBatteryLevel() {
        if (bleManager.isConnected()) {
            bleManager.getBatteryLevel();
        }
    }
    
    /**
     * 控制蜂鸣器
     */
    public void controlBuzzer(boolean enable) {
        if (bleManager.isConnected()) {
            bleManager.controlBuzzer(enable);
        }
    }
}
