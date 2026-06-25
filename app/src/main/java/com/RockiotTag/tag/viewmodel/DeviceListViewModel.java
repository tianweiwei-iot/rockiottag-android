package com.RockiotTag.tag.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.RockiotTag.tag.repository.DeviceRepository;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DeviceListActivity的ViewModel - 管理设备列表的数据和业务逻辑
 */
public class DeviceListViewModel extends AndroidViewModel {
    private static final String TAG = "DeviceListViewModel";
    
    private DeviceRepository deviceRepository;
    private ExecutorService executorService;
    private Handler mainHandler;
    
    // LiveData for UI observation
    private final MutableLiveData<List<TagDevice>> deviceList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isEmpty = new MutableLiveData<>(true);
    
    public DeviceListViewModel(@NonNull Application application) {
        super(application);
        deviceRepository = DeviceRepository.getInstance(application);
        // 创建线程池用于后台任务（性能优化）
        executorService = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * 获取设备列表
     */
    public LiveData<List<TagDevice>> getDeviceList() {
        return deviceList;
    }
    
    /**
     * 获取加载状态
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    /**
     * 获取错误信息
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 获取是否为空
     */
    public LiveData<Boolean> getIsEmpty() {
        return isEmpty;
    }
    
    /**
     * 加载所有设备（异步优化版）
     */
    public void loadDevices() {
        LogUtil.d(TAG, "Loading devices");
        isLoading.setValue(true);
        
        // 使用线程池执行后台任务（性能优化）
        executorService.execute(() -> {
            try {
                List<TagDevice> devices = deviceRepository.getAllLocalDevices();
                
                // 切换到主线程更新UI
                mainHandler.post(() -> {
                    deviceList.setValue(devices);
                    isEmpty.setValue(devices == null || devices.isEmpty());
                    isLoading.setValue(false);
                    LogUtil.d(TAG, "Loaded " + (devices != null ? devices.size() : 0) + " devices");
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading devices: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    errorMessage.setValue("加载设备失败: " + e.getMessage());
                    isLoading.setValue(false);
                });
            }
        });
    }
    
    /**
     * 更新设备名称和标签
     */
    public interface UpdateCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public void updateDevice(String deviceId, String deviceNum, String name, String tag, UpdateCallback callback) {
        updateDevice(deviceId, deviceNum, name, tag, null, callback);
    }

    public void updateDevice(String deviceId, String deviceNum, String name, String tag, String customerCode, UpdateCallback callback) {
        LogUtil.d(TAG, "=== updateDevice called ===");
        LogUtil.d(TAG, "deviceId: " + deviceId);
        LogUtil.d(TAG, "deviceNum: " + deviceNum);
        LogUtil.d(TAG, "name: " + name);
        LogUtil.d(TAG, "tag: " + tag);
        LogUtil.d(TAG, "customerCode: " + customerCode);

        deviceRepository.updateDeviceNameAndTag(deviceId, deviceNum, name, tag, customerCode, new DeviceRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                LogUtil.d(TAG, "=== DeviceRepository updateDevice onSuccess ===");
                // Repository已经确保在主线程调用callback
                if (callback != null) {
                    callback.onSuccess();
                }
                
                // 延迟重新加载列表
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        LogUtil.d(TAG, "Reloading devices after update");
                        loadDevices();
                    } catch (Exception e) {
                        Log.e(TAG, "Error reloading devices: " + e.getMessage(), e);
                    }
                }, 300);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "=== DeviceRepository updateDevice onError: " + error + " ===");
                // Repository已经确保在主线程调用callback
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }
    
    /**
     * 批量删除设备（只加载一次列表）
     */
    public void deleteDevices(List<String> deviceIds, DeleteCallback callback) {
        LogUtil.d(TAG, "Deleting " + deviceIds.size() + " devices");
        
        new Thread(() -> {
            try {
                for (String deviceId : deviceIds) {
                    deviceRepository.deleteDevice(deviceId, null);
                    LogUtil.d(TAG, "Device deleted: " + deviceId);
                }
                
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    // 只在所有删除完成后重新加载一次列表
                    loadDevices();
                    LogUtil.d(TAG, "All devices deleted, list reloaded");
                    if (callback != null) {
                        callback.onSuccess();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error deleting devices: " + e.getMessage(), e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    errorMessage.setValue("删除设备失败: " + e.getMessage());
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
            }
        }).start();
    }
    
    /**
     * 删除设备
     */
    public void deleteDevice(String deviceId) {
        deleteDevice(deviceId, null);
    }
    
    /**
     * 删除设备（带回调）
     */
    public void deleteDevice(String deviceId, DeleteCallback callback) {
        LogUtil.d(TAG, "Deleting device: " + deviceId);
        
        deviceRepository.deleteDevice(deviceId, new DeviceRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                LogUtil.d(TAG, "Device deleted successfully, reloading list");
                // Reload device list after deletion - 这会自动触发LiveData更新
                loadDevices();
                // 注意：不在这里调用callback，而是在loadDevices完成后再调用
                // 因为loadDevices会触发LiveData更新，Activity的Observer会收到通知
                // 如果在这里调用callback，可能会导致Activity在列表还没刷新时就收到通知
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error deleting device: " + error);
                errorMessage.setValue("删除设备失败: " + error);
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }
    
    /**
     * 删除回调接口
     */
    public interface DeleteCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * 获取设备数量
     */
    public int getDeviceCount() {
        return deviceRepository.getDeviceCount();
    }
    
    /**
     * 清理资源（防止内存泄漏）
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            LogUtil.d(TAG, "ExecutorService shutdown");
        }
    }
}
