package com.RockiotTag.tag;

import android.content.Context;
import android.content.SharedPreferences;

import com.RockiotTag.tag.model.TagDevice;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UnboundDeviceManager {
    private static final String PREF_NAME = "app_settings";
    private static final String PREF_UNBOUND_DEVICES = "unbound_devices";
    private static UnboundDeviceManager instance;
    private SharedPreferences prefs;
    private Set<String> unboundDevices;
    
    private UnboundDeviceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(PREF_UNBOUND_DEVICES, null);
        unboundDevices = new HashSet<>();
        if (saved != null) {
            unboundDevices.addAll(saved);
        }
    }
    
    public static synchronized UnboundDeviceManager getInstance(Context context) {
        if (instance == null) {
            instance = new UnboundDeviceManager(context.getApplicationContext());
        }
        return instance;
    }
    
    public static synchronized UnboundDeviceManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("UnboundDeviceManager not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }
    
    public void addUnboundDevice(String deviceNum) {
        if (deviceNum != null && !deviceNum.isEmpty()) {
            unboundDevices.add(deviceNum);
            saveUnboundDevices();
        }
    }
    
    public void removeUnboundDevice(String deviceNum) {
        unboundDevices.remove(deviceNum);
        saveUnboundDevices();
    }
    
    public boolean isDeviceUnbound(String deviceNum) {
        return unboundDevices.contains(deviceNum);
    }
    
    public Set<String> getUnboundDevices() {
        return new HashSet<>(unboundDevices);
    }
    
    public void clearAllUnboundDevices() {
        unboundDevices.clear();
        saveUnboundDevices();
    }
    
    public int getUnboundCount() {
        return unboundDevices.size();
    }
    
    /**
     * 更新未绑定设备列表（用于 MVVM 扫描结果同步）
     */
    public void updateUnboundDevices(List<TagDevice> devices) {
        // 这里可以根据需求处理扫描到的新设备
        // 例如：如果发现新设备且不在已绑定列表中，可以提示用户
        if (devices != null) {
            for (TagDevice device : devices) {
                if (!isDeviceUnbound(device.getDeviceId())) {
                    // 可以在这里触发一个回调或者日志记录
                }
            }
        }
    }
    
    private void saveUnboundDevices() {
        prefs.edit().putStringSet(PREF_UNBOUND_DEVICES, unboundDevices).apply();
    }
}
