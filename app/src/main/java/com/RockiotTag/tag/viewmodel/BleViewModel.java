package com.RockiotTag.tag.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.RockiotTag.tag.repository.BLERepository;
import com.RockiotTag.tag.model.TagDevice;

import java.util.List;

/**
 * 蓝牙管理 ViewModel - 负责处理蓝牙扫描、连接和状态监听
 */
public class BleViewModel extends AndroidViewModel {
    private static final String TAG = "BleViewModel";
    
    private BLERepository bleRepository;
    
    // LiveData for UI observation
    private final MutableLiveData<Boolean> isScanning = new MutableLiveData<>(false);
    private final MutableLiveData<List<TagDevice>> scanResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    private final MutableLiveData<String> connectionStatus = new MutableLiveData<>("未连接");
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    public BleViewModel(@NonNull Application application) {
        super(application);
        bleRepository = BLERepository.getInstance(application);
    }
    
    /**
     * 开始扫描蓝牙设备
     */
    public void startScan() {
        if (isScanning.getValue()) return;
        
        isScanning.setValue(true);
        scanResults.setValue(null); // 清空旧结果
        
        bleRepository.startScanning(new BLERepository.ScanCallback() {
            @Override
            public void onDeviceFound(TagDevice device) {
                List<TagDevice> currentList = scanResults.getValue();
                if (currentList == null) {
                    currentList = new java.util.ArrayList<>();
                }
                
                // 简单的去重逻辑：如果已存在相同 ID，则更新信号强度
                boolean found = false;
                for (int i = 0; i < currentList.size(); i++) {
                    if (currentList.get(i).getDeviceId().equals(device.getDeviceId())) {
                        currentList.get(i).setSignalStrength(device.getSignalStrength());
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    currentList.add(device);
                }
                
                scanResults.postValue(currentList);
            }
            
            @Override
            public void onScanFinished() {
                isScanning.postValue(false);
                Log.d(TAG, "BLE scan finished");
            }
        });
    }
    
    /**
     * 停止扫描
     */
    public void stopScan() {
        bleRepository.stopScanning();
        isScanning.setValue(false);
    }
    
    /**
     * 连接到指定设备
     */
    public void connectToDevice(TagDevice device) {
        connectionStatus.setValue("正在连接: " + device.getName());
        // 实际连接逻辑需要 BLEManager 配合，这里先占位
        // bleRepository.connectToDevice(...);
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        bleRepository.disconnect();
        isConnected.setValue(false);
        connectionStatus.setValue("未连接");
    }
    
    /**
     * 检查蓝牙是否已启用
     */
    public boolean isBluetoothEnabled() {
        return bleRepository.isBluetoothEnabled();
    }
    
    /**
     * 触发蜂鸣器
     */
    public void triggerBuzzer() {
        if (isConnected.getValue()) {
            bleRepository.controlBuzzer(true);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                bleRepository.controlBuzzer(false);
            }, 2000);
        } else {
            errorMessage.setValue("请先连接设备");
        }
    }
    
    // Getters
    public LiveData<Boolean> getIsScanning() { return isScanning; }
    public LiveData<List<TagDevice>> getScanResults() { return scanResults; }
    public LiveData<Boolean> getIsConnected() { return isConnected; }
    public LiveData<String> getConnectionStatus() { return connectionStatus; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
}
