package com.RockiotTag.tag.helper;

import com.RockiotTag.tag.util.ToastHelper;

import android.content.Context;
import android.util.Log;

import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.util.LogUtil;

import java.util.List;

/**
 * MainActivity 设备管理助手
 * 职责：封装设备列表管理和刷新逻辑
 */
public class MainActivityDeviceHelper {
    private static final String TAG = "MainDeviceHelper";
    
    public interface DeviceListCallback {
        void onDeviceListLoaded(List<TagDevice> devices);
        void onError(String message);
    }
    
    private DatabaseHelper databaseHelper;
    private DeviceListCallback callback;
    
    public MainActivityDeviceHelper(DatabaseHelper dbHelper, DeviceListCallback callback) {
        this.databaseHelper = dbHelper;
        this.callback = callback;
    }
    
    /**
     * 从数据库加载设备列表
     */
    public void loadDevicesFromDatabase() {
        new Thread(() -> {
            try {
                List<TagDevice> devices = databaseHelper.getAllDevices();
                
                if (devices != null) {
                    // 按最后 seen 时间排序
                    java.util.Collections.sort(devices, (d1, d2) -> Long.compare(d2.getLastSeen(), d1.getLastSeen()));
                    
                    if (callback != null) {
                        callback.onDeviceListLoaded(devices);
                    }
                    
                    LogUtil.d(TAG, "Loaded " + devices.size() + " devices from database");
                } else {
                    if (callback != null) {
                        callback.onDeviceListLoaded(new java.util.ArrayList<>());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading devices: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onError("加载设备失败: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * 刷新单个设备信息
     */
    public void refreshDevice(TagDevice device) {
        if (device == null) {
            return;
        }
        
        new Thread(() -> {
            try {
                TagDevice updatedDevice = databaseHelper.getDevice(device.getDeviceId());
                if (updatedDevice != null && callback != null) {
                    callback.onDeviceListLoaded(java.util.Collections.singletonList(updatedDevice));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing device: " + e.getMessage(), e);
            }
        }).start();
    }
    
    /**
     * 删除设备
     */
    public void deleteDevice(String deviceId, Context context) {
        new Thread(() -> {
            try {
                int deleted = databaseHelper.deleteDevice(deviceId);
                
                if (deleted > 0) {
                    // 重新加载设备列表
                    loadDevicesFromDatabase();
                    
                    if (context != null) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            ToastHelper.show(context, "设备已删除");
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting device: " + e.getMessage(), e);
                if (context != null) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        ToastHelper.show(context, "删除失败: " + e.getMessage());
                    });
                }
            }
        }).start();
    }
    
    /**
     * 搜索设备
     */
    public void searchDevices(String keyword, DeviceListCallback searchCallback) {
        new Thread(() -> {
            try {
                List<TagDevice> allDevices = databaseHelper.getAllDevices();
                List<TagDevice> filteredDevices = new java.util.ArrayList<>();
                
                if (allDevices != null && keyword != null && !keyword.isEmpty()) {
                    String lowerKeyword = keyword.toLowerCase();
                    for (TagDevice device : allDevices) {
                        if (device.getDeviceNum() != null && 
                            device.getDeviceNum().toLowerCase().contains(lowerKeyword)) {
                            filteredDevices.add(device);
                        }
                    }
                } else {
                    filteredDevices = allDevices;
                }
                
                if (searchCallback != null) {
                    searchCallback.onDeviceListLoaded(filteredDevices);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error searching devices: " + e.getMessage(), e);
                if (searchCallback != null) {
                    searchCallback.onError("搜索失败: " + e.getMessage());
                }
            }
        }).start();
    }
}
