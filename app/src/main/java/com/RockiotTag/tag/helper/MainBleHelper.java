package com.RockiotTag.tag.helper;

import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.RockiotTag.tag.BLEManager;
import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.R;

import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity 蓝牙扫描辅助类
 * 负责封装蓝牙增强扫描强度管理、单次/持续扫描逻辑
 */
public class MainBleHelper {

    private static final String TAG = "MainBleHelper";

    private final AppCompatActivity activity;
    private final BleCallbacks callbacks;

    /**
     * 蓝牙回调接口，由 Activity 实现以提供必要的依赖
     */
    public interface BleCallbacks {
        com.RockiotTag.tag.integration.LocationOptimizationManager getLocationOptimizationManager();
        View getScanningIndicator();
        BLEManager getBleManager();
        com.RockiotTag.tag.DatabaseHelper getDatabaseHelper();
        com.RockiotTag.tag.CrowdSourcingManager getCrowdSourcingManager();
        void onUpdateMapMarker(Device device);
    }

    public MainBleHelper(AppCompatActivity activity, BleCallbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    /**
     * 启动单次扫描（低强度）：扫描成功更新UI后自动停止
     */
    public void startSingleScanWithCheck() {
        if (callbacks.getLocationOptimizationManager() == null || !callbacks.getLocationOptimizationManager().isOptimizationEnabled()) {
            Log.w(TAG, "Cannot start scanning: LocationOptimizationManager not available");
            Toast.makeText(activity, "蓝牙扫描不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        callbacks.getLocationOptimizationManager().autoSelectFirstDevice();
        callbacks.getLocationOptimizationManager().startSingleBluetoothScan();
        Log.d(TAG, "Single scan started (low intensity)");
    }

    /**
     * 启动持续循环扫描（高强度）：扫描10秒，停止10秒，循环
     */
    public void startContinuousScanWithCheck() {
        if (callbacks.getLocationOptimizationManager() == null || !callbacks.getLocationOptimizationManager().isOptimizationEnabled()) {
            Log.w(TAG, "Cannot start scanning: LocationOptimizationManager not available");
            Toast.makeText(activity, "蓝牙扫描不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        callbacks.getLocationOptimizationManager().autoSelectFirstDevice();
        callbacks.getLocationOptimizationManager().startBluetoothScanning();
        Log.d(TAG, "Continuous scan started (high intensity)");
    }

    /**
     * 根据当前扫描强度级别应用扫描行为
     */
    public void applyScanIntensity(int scanIntensityLevel) {
        // 先停止当前扫描
        if (callbacks.getLocationOptimizationManager() != null) {
            callbacks.getLocationOptimizationManager().stopBluetoothScanning();
        }
        if (callbacks.getScanningIndicator() != null) {
            callbacks.getScanningIndicator().setVisibility(View.GONE);
        }

        switch (scanIntensityLevel) {
            case 0: // 关闭
                Log.d(TAG, "Scan intensity: OFF");
                break;
            case 1: // 低
                startSingleScanWithCheck();
                break;
            case 2: // 高
                startContinuousScanWithCheck();
                break;
        }
    }

    /**
     * 启动BLE扫描
     */
    public void startBLEScanning() {
        if (callbacks.getBleManager() == null) {
            Log.w(TAG, "BLEManager is null, cannot start scanning");
            return;
        }
        if (callbacks.getBleManager().isBluetoothEnabled()) {
            callbacks.getBleManager().startScanning(new BLEManager.DeviceScanCallback() {
                @Override
                public void onDeviceFound(Device device) {
                    Log.d(TAG, "Found device: " + device.getName() + " - " + device.getDeviceId());
                    if (callbacks.getDatabaseHelper() != null) {
                        callbacks.getDatabaseHelper().addDevice(device);
                    }
                    if (callbacks.getCrowdSourcingManager() != null) {
                        callbacks.getCrowdSourcingManager().sendDeviceData(device);
                    }
                    // 更新地图标记
                    callbacks.onUpdateMapMarker(device);
                }

                @Override
                public void onScanComplete() {
                    Log.d(TAG, "BLE scanning completed");
                    Toast.makeText(activity, R.string.scan_complete, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(activity, R.string.bluetooth_not_enabled, Toast.LENGTH_SHORT).show();
        }
    }
}
