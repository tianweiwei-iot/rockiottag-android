package com.RockiotTag.tag.repository;

import android.content.Context;
import android.util.Log;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备数据仓库 - 统一管理设备数据的本地和远程操作
 */
public class DeviceRepository {
    private static final String TAG = "DeviceRepository";
    
    private DatabaseHelper databaseHelper;
    private NewApiService apiService;
    private static volatile DeviceRepository instance;
    
    public static synchronized DeviceRepository getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceRepository(context);
        }
        return instance;
    }
    
    private DeviceRepository(Context context) {
        this.databaseHelper = DatabaseHelper.getInstance(context);
        this.apiService = NewApiService.getInstance();
    }
    
    /**
     * 从本地数据库获取所有设备
     */
    public List<TagDevice> getAllLocalDevices() {
        LogUtil.d(TAG, "Getting all local devices");
        List<TagDevice> devices = databaseHelper.getAllDevices();
        List<TagDevice> newDevices = new ArrayList<>();
        for (TagDevice d : devices) {
            if (d != null) newDevices.add(d);
        }
        return newDevices;
    }

    /**
     * 从服务器获取设备列表
     */
    public List<NewApiService.DeviceInfo> getRemoteDevices() {
        LogUtil.d(TAG, "Getting remote devices from server");
        try {
            return apiService.getDevices(ApiConfig.getDefaultServerUrl());
        } catch (Exception e) {
            Log.e(TAG, "Error getting remote devices: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 保存或更新设备到本地数据库
     */
    public synchronized void saveDevice(TagDevice device) {
        LogUtil.d(TAG, "Saving device: " + device.getName());
        databaseHelper.addDevice(device);
    }
    
    public TagDevice getDeviceById(String deviceId) {
        LogUtil.d(TAG, "Getting device by ID: " + deviceId);
        return databaseHelper.getDevice(deviceId);
    }

    public TagDevice getDeviceByNum(String deviceNum) {
        LogUtil.d(TAG, "Getting device by deviceNum: " + deviceNum);
        return databaseHelper.getDeviceByDeviceNum(deviceNum);
    }
    
    /**
     * 删除回调接口
     */
    public interface DeleteCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * 删除设备
     */
    public void deleteDevice(String deviceId, DeleteCallback callback) {
        LogUtil.d(TAG, "Deleting device: " + deviceId);
        
        new Thread(() -> {
            try {
                int deletedCount = databaseHelper.deleteDevice(deviceId);
                
                if (deletedCount > 0) {
                    LogUtil.d(TAG, "Device deleted successfully: " + deviceId);
                    if (callback != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onSuccess);
                    }
                } else {
                    Log.w(TAG, "Device not found in database: " + deviceId);
                    if (callback != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                            callback.onError("设备不存在"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting device: " + e.getMessage(), e);
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                        callback.onError(e.getMessage()));
                }
            }
        }).start();
    }
    
    /**
     * 更新设备名称和标签（本地+服务器）
     */
    public interface UpdateCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public void updateDeviceNameAndTag(String deviceId, String deviceNum, String name, String tag, UpdateCallback callback) {
        updateDeviceNameAndTag(deviceId, deviceNum, name, tag, null, callback);
    }

    public void updateDeviceNameAndTag(String deviceId, String deviceNum, String name, String tag, String customerCode, UpdateCallback callback) {
        LogUtil.d(TAG, "=== updateDeviceNameAndTag called ===");
        LogUtil.d(TAG, "deviceId: " + deviceId + ", deviceNum: " + deviceNum);
        LogUtil.d(TAG, "name: " + name + ", tag: " + tag + ", customerCode: " + customerCode);
        LogUtil.d(TAG, "callback: " + (callback != null ? "not null" : "NULL"));

        new Thread(() -> {
            try {
                // 更新服务器（传递customerCode以确保使用正确的API Key）
                if (deviceNum != null && !deviceNum.isEmpty()) {
                    try {
                        LogUtil.d(TAG, "[Thread] Updating server for device: " + deviceNum);
                        NewApiService.ApiResponse response = apiService.updateDevice(com.RockiotTag.tag.ApiConfig.getMyServerUrl(deviceNum), deviceNum, name, customerCode);
                        LogUtil.d(TAG, "[Thread] Server update response: success=" + (response != null ? response.isSuccess() : "null"));
                        
                        // 检查服务器响应
                        if (response != null && !response.isSuccess()) {
                            Log.e(TAG, "[Thread] Server update failed: " + response.getMessage());
                            if (callback != null) {
                                LogUtil.d(TAG, "[Thread] Posting onError to main thread (server failure)");
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    LogUtil.d(TAG, "[Main Thread] Executing callback.onError");
                                    callback.onError("服务器更新失败: " + response.getMessage());
                                });
                            }
                            return;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "[Thread] Error updating device on server: " + e.getMessage(), e);
                        // 服务器更新失败不阻止本地更新，继续执行
                    }
                }
                
                // 更新本地数据库
                LogUtil.d(TAG, "[Thread] Updating local database...");
                boolean updated = databaseHelper.updateDeviceNameAndTag(deviceId, deviceNum, name, tag);
                LogUtil.d(TAG, "[Thread] Local database update result: " + updated);
                
                if (updated) {
                    LogUtil.d(TAG, "[Thread] Device updated successfully");
                    // 关键修复：必须在主线程调用callback，因为UI操作需要在主线程
                    if (callback != null) {
                        LogUtil.d(TAG, "[Thread] Posting onSuccess to main thread");
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            LogUtil.d(TAG, "[Main Thread] === Executing callback.onSuccess ===");
                            try {
                                callback.onSuccess();
                                LogUtil.d(TAG, "[Main Thread] callback.onSuccess completed");
                            } catch (Exception e) {
                                Log.e(TAG, "[Main Thread] Error in callback.onSuccess: " + e.getMessage(), e);
                                e.printStackTrace();
                            }
                        });
                    } else {
                        Log.e(TAG, "[Thread] callback is NULL, cannot notify success!");
                    }
                } else {
                    Log.e(TAG, "[Thread] Failed to update device in database");
                    if (callback != null) {
                        LogUtil.d(TAG, "[Thread] Posting onError to main thread (database failure)");
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            LogUtil.d(TAG, "[Main Thread] Executing callback.onError");
                            callback.onError("数据库更新失败");
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "[Thread] Error updating device: " + e.getMessage(), e);
                e.printStackTrace();
                if (callback != null) {
                    LogUtil.d(TAG, "[Thread] Posting onError to main thread (exception)");
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        LogUtil.d(TAG, "[Main Thread] Executing callback.onError");
                        callback.onError("更新异常: " + e.getMessage());
                    });
                }
            }
        }).start();
        
        LogUtil.d(TAG, "=== Background thread started ===");
    }
    
    /**
     * 更新本地设备信息（从服务器同步后）
     */
    public void updateLocalDeviceInfo(String deviceId, NewApiService.DeviceInfo deviceInfo) {
        LogUtil.d(TAG, "Updating local device info for: " + deviceId);
        databaseHelper.updateDeviceLocationAndBattery(
            deviceId,
            deviceInfo.latitude,
            deviceInfo.longitude,
            deviceInfo.battery,
            deviceInfo.timestamp
        );
    }

    /**
     * 检查设备是否已绑定
     */
    public boolean isDeviceBound(String deviceId) {
        return databaseHelper.isDeviceBound(deviceId);
    }
    
    /**
     * 获取设备数量
     */
    public int getDeviceCount() {
        return databaseHelper.getDeviceCount();
    }
}
