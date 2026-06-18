package com.RockiotTag.tag.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.SharedPreferencesManager;
import com.RockiotTag.tag.UnboundDeviceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备数据管理器 - 统一管理设备数据的同步、缓存和持久化
 * 职责：
 * 1. 从服务器同步设备数据
 * 2. 管理本地数据库的设备信息
 * 3. 处理设备的选中状态
 * 4. 清理未绑定设备
 */
public class DeviceDataManager {
    private static final String TAG = "DeviceDataManager";
    
    public interface DeviceDataCallback {
        void onDevicesSynced(List<Device> devices);
        void onDeviceInfoUpdated(Device device, NewApiService.DeviceInfo info);
        void onError(String error);
    }
    
    private final Context context;
    private final DatabaseHelper databaseHelper;
    private final NewApiService apiService;
    private final UnboundDeviceManager unboundDeviceManager;
    private DeviceDataCallback callback;
    
    public DeviceDataManager(Context context, DatabaseHelper databaseHelper) {
        this.context = context.getApplicationContext();
        this.databaseHelper = databaseHelper;
        this.apiService = NewApiService.getInstance();
        this.unboundDeviceManager = UnboundDeviceManager.getInstance(context);
        
        // 加载认证信息
        SharedPreferencesManager.loadAuth(context);
    }
    
    /**
     * 设置回调
     */
    public void setCallback(DeviceDataCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 从API同步设备列表
     */
    public void syncDevicesFromApi() {
        // 使用默认的服务器URL（12位设备号的服务器）
        NewApiService.setApiBaseUrl(ApiConfig.SERVER_URL_12BIT);
        
        new Thread(() -> {
            try {
                LogUtil.d(TAG, "Starting API sync...");
                
                // 同步数据
                LogUtil.d(TAG, "Syncing data from vendor API...");
                NewApiService.ApiResponse syncResponse = apiService.syncAll();
                LogUtil.d(TAG, "Sync response: " + (syncResponse != null ? syncResponse.isSuccess() : "null"));
                
                LogUtil.d(TAG, "Fetching bound devices from API...");
                List<NewApiService.DeviceInfo> apiDevices = apiService.getBoundDeviceList();
                LogUtil.d(TAG, "Found " + apiDevices.size() + " devices from API");
                
                if (apiDevices.isEmpty()) {
                    Log.w(TAG, "API returned empty device list");
                    notifyError("未找到已绑定的设备");
                    return;
                }
                
                final List<Device> syncedDevices = new ArrayList<>();
                int skippedCount = 0;
                
                for (NewApiService.DeviceInfo info : apiDevices) {
                    if (info.deviceNum == null) continue;
                    
                    String deviceId = info.deviceNum;
                    
                    if (unboundDeviceManager.isDeviceUnbound(deviceId)) {
                        LogUtil.d(TAG, "Skipping unbound device: " + deviceId);
                        skippedCount++;
                        continue;
                    }
                    
                    if (!databaseHelper.isDeviceBound(deviceId)) {
                        Device device = new Device(deviceId, 
                            info.nickName != null ? info.nickName : "设备" + info.deviceNum);
                        device.setDeviceNum(info.deviceNum);
                        device.setLatitude(info.latitude);
                        device.setLongitude(info.longitude);
                        device.setLastSeen(info.timestamp > 0 ? info.timestamp : System.currentTimeMillis());
                        databaseHelper.addDevice(device);
                        syncedDevices.add(device);
                        LogUtil.d(TAG, "Synced new device: " + device.getName());
                    } else {
                        Device existingDevice = databaseHelper.getDevice(deviceId);
                        if (existingDevice != null) {
                            if (existingDevice.getDeviceNum() == null) {
                                existingDevice.setDeviceNum(info.deviceNum);
                            }
                            // 昵称：保留本地昵称，不使用服务器昵称覆盖
                            // 因为本地昵称可能是用户刚修改但尚未同步到服务器的
                            existingDevice.setLatitude(info.latitude);
                            existingDevice.setLongitude(info.longitude);
                            existingDevice.setLastSeen(info.timestamp > 0 ? info.timestamp : System.currentTimeMillis());
                            databaseHelper.addDevice(existingDevice);
                            syncedDevices.add(existingDevice);
                            LogUtil.d(TAG, "Updated device: " + existingDevice.getName());
                        }
                    }
                }
                
                final int finalSkippedCount = skippedCount;
                if (callback != null) {
                    callback.onDevicesSynced(syncedDevices);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error syncing device list: " + e.getMessage(), e);
                notifyError("同步设备失败: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 获取单个设备的最新信息
     * @param deviceNum 设备编号
     */
    public void fetchDeviceInfo(String deviceNum) {
        new Thread(() -> {
            try {
                LogUtil.d(TAG, "Fetching device info for: " + deviceNum);
                
                // 根据设备号长度设置对应的API URL
                NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(deviceNum));
                
                NewApiService.DeviceInfo deviceInfo = apiService.getDeviceLatest(deviceNum);
                
                if (deviceInfo == null) {
                    deviceInfo = apiService.getDeviceInfo(deviceNum);
                }
                
                if (deviceInfo != null) {
                    // 更新本地数据库（只更新位置和电量，不覆盖昵称）
                    Device device = databaseHelper.getDevice(deviceNum);
                    if (device != null) {
                        // 昵称：保留本地昵称，不使用服务器昵称覆盖
                        device.setLatitude(deviceInfo.latitude);
                        device.setLongitude(deviceInfo.longitude);
                        device.setLastSeen(deviceInfo.timestamp > 0 ?
                            deviceInfo.timestamp : System.currentTimeMillis());
                        databaseHelper.addDevice(device);
                    }
                    
                    if (callback != null) {
                        callback.onDeviceInfoUpdated(device, deviceInfo);
                    }
                } else {
                    notifyError("无法获取设备信息");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching device info: " + e.getMessage(), e);
                notifyError("获取设备信息失败: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 保存选中的设备ID
     */
    public void saveSelectedDeviceId(String deviceId) {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("selected_device_id", deviceId);
        editor.apply();
        LogUtil.d(TAG, "Saved selected device ID: " + deviceId);
    }
    
    /**
     * 获取选中的设备ID
     */
    public String getSelectedDeviceId() {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        return prefs.getString("selected_device_id", "");
    }
    
    /**
     * 清除所有设备信息
     */
    public void clearAllDevices() {
        // 注意：DatabaseHelper可能没有deleteAllDevices方法
        // 如果需要删除所有设备，可以遍历删除或添加新方法
        Log.w(TAG, "clearAllDevices called - implementation pending");
        saveSelectedDeviceId("");
    }
    
    /**
     * 通知错误
     */
    private void notifyError(String error) {
        if (callback != null) {
            callback.onError(error);
        }
    }
    
    /**
     * 关闭数据库连接
     */
    public void close() {
        databaseHelper.close();
    }
}
