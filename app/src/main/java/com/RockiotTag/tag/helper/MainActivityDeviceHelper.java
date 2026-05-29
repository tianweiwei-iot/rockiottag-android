package com.RockiotTag.tag.helper;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.DatabaseHelper;

import java.util.List;

/**
 * MainActivity 设备管理助手
 * 职责：封装设备列表管理和刷新逻辑
 */
public class MainActivityDeviceHelper {
    private static final String TAG = "MainDeviceHelper";
    
    public interface DeviceListCallback {
        void onDeviceListLoaded(List<Device> devices);
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
                List<Device> devices = databaseHelper.getAllDevices();
                
                if (devices != null) {
                    // 按最后 seen 时间排序
                    java.util.Collections.sort(devices, (d1, d2) -> Long.compare(d2.getLastSeen(), d1.getLastSeen()));
                    
                    if (callback != null) {
                        callback.onDeviceListLoaded(devices);
                    }
                    
                    Log.d(TAG, "Loaded " + devices.size() + " devices from database");
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
    public void refreshDevice(Device device) {
        if (device == null) {
            return;
        }
        
        new Thread(() -> {
            try {
                Device updatedDevice = databaseHelper.getDevice(device.getDeviceId());
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
                            Toast.makeText(context, "设备已删除", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting device: " + e.getMessage(), e);
                if (context != null) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                List<Device> allDevices = databaseHelper.getAllDevices();
                List<Device> filteredDevices = new java.util.ArrayList<>();
                
                if (allDevices != null && keyword != null && !keyword.isEmpty()) {
                    String lowerKeyword = keyword.toLowerCase();
                    for (Device device : allDevices) {
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
