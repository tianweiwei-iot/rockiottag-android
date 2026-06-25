package com.RockiotTag.tag.integration;

import android.util.Log;

import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.util.LogUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备 MAC 地址映射管理器
 * 从 LocationOptimizationManager 拆分，负责 deviceNum 与 MAC 地址的双向映射
 */
public class DeviceMacMapper {

    private static final String TAG = "DeviceMacMapper";

    private final DatabaseHelper databaseHelper;
    private final NewApiService apiService;

    // deviceNum → MAC 映射
    private final Map<String, String> deviceNumToMacMap = java.util.Collections.synchronizedMap(new HashMap<>());
    // MAC → deviceNum 映射
    private final Map<String, String> macToDeviceNumMap = java.util.Collections.synchronizedMap(new HashMap<>());

    public DeviceMacMapper(DatabaseHelper databaseHelper, NewApiService apiService) {
        this.databaseHelper = databaseHelper;
        this.apiService = apiService;
    }

    /**
     * 从数据库加载已绑定设备，建立映射表
     */
    public Set<String> loadBoundDevices() {
        if (databaseHelper == null) {
            return java.util.Collections.emptySet();
        }

        Set<String> boundDeviceIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        deviceNumToMacMap.clear();
        macToDeviceNumMap.clear();

        try {
            List<TagDevice> devices = databaseHelper.getAllDevices();
            if (devices != null) {
                for (TagDevice device : devices) {
                    String deviceNum = device.getDeviceNum();
                    String mac = device.getMac();

                    if (deviceNum != null && !deviceNum.isEmpty()) {
                        boundDeviceIds.add(deviceNum);
                        if (mac != null && !mac.isEmpty()) {
                            String normalizedMac = normalizeMacAddress(mac);
                            deviceNumToMacMap.put(deviceNum, normalizedMac);
                            macToDeviceNumMap.put(normalizedMac, deviceNum);
                        }
                    }
                }
            }
            LogUtil.d(TAG, "Loaded " + boundDeviceIds.size() + " bound devices, " + deviceNumToMacMap.size() + " MAC mappings");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load bound devices", e);
        }

        return boundDeviceIds;
    }

    /**
     * 根据 MAC 地址获取设备号
     */
    public String getDeviceNumByMac(String macAddress) {
        if (macAddress == null || macAddress.isEmpty()) {
            return null;
        }

        String normalizedMac = normalizeMacAddress(macAddress);

        // 先查内存映射
        String deviceNum = macToDeviceNumMap.get(normalizedMac);
        if (deviceNum != null) {
            return deviceNum;
        }

        // 内存映射未命中，尝试遍历数据库查找
        if (databaseHelper != null) {
            try {
                List<TagDevice> devices = databaseHelper.getAllDevices();
                if (devices != null) {
                    for (TagDevice device : devices) {
                        String deviceMac = device.getMac();
                        if (deviceMac != null && normalizeMacAddress(deviceMac).equals(normalizedMac)) {
                            deviceNum = device.getDeviceNum();
                            macToDeviceNumMap.put(normalizedMac, deviceNum);
                            deviceNumToMacMap.put(deviceNum, normalizedMac);
                            return deviceNum;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get device by MAC from database", e);
            }
        }

        return null;
    }

    /**
     * 根据设备号获取 MAC 地址
     */
    public String getMacByDeviceNum(String deviceNum) {
        if (deviceNum == null || deviceNum.isEmpty()) {
            return null;
        }
        return deviceNumToMacMap.get(deviceNum);
    }

    /**
     * 添加设备号与 MAC 地址的映射（运行时动态添加，例如从服务器获取到 MAC 后调用）
     * @param deviceNum 设备号
     * @param macAddress MAC 地址（会被自动标准化）
     */
    public void addMapping(String deviceNum, String macAddress) {
        if (deviceNum == null || deviceNum.isEmpty() || macAddress == null || macAddress.isEmpty()) {
            return;
        }
        String normalizedMac = normalizeMacAddress(macAddress);
        deviceNumToMacMap.put(deviceNum, normalizedMac);
        macToDeviceNumMap.put(normalizedMac, deviceNum);
        LogUtil.d(TAG, "Added mapping: " + deviceNum + " <-> " + normalizedMac);
    }

    /**
     * 获取所有已映射的 MAC 地址集合（用于 BLE 扫描匹配）
     * @return MAC 地址集合的副本
     */
    public Set<String> getAllMacAddresses() {
        return new java.util.HashSet<>(macToDeviceNumMap.keySet());
    }

    /**
     * 清空所有映射（重新加载前调用）
     */
    public void clearMappings() {
        deviceNumToMacMap.clear();
        macToDeviceNumMap.clear();
    }

    /**
     * 更新数据库中的设备 MAC 字段
     */
    public void updateDeviceMacInDatabase(String deviceNum, com.RockiotTag.tag.model.DeviceLocation location) {
        if (databaseHelper == null || deviceNum == null || location == null) {
            return;
        }

        // DeviceLocation 的 deviceId 字段存储的是 MAC 地址
        String mac = location.getDeviceId();
        if (mac == null || mac.isEmpty()) {
            return;
        }

        String normalizedMac = normalizeMacAddress(mac);
        try {
            databaseHelper.updateDeviceMac(deviceNum, normalizedMac);
            deviceNumToMacMap.put(deviceNum, normalizedMac);
            macToDeviceNumMap.put(normalizedMac, deviceNum);
            LogUtil.d(TAG, "Updated MAC for device " + deviceNum + ": " + normalizedMac);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update device MAC in database", e);
        }
    }

    /**
     * 标准化 MAC 地址格式为 XX:XX:XX:XX:XX:XX 大写
     */
    public String normalizeMacAddress(String macAddress) {
        if (macAddress == null || macAddress.isEmpty()) {
            return macAddress;
        }

        String mac = macAddress.trim().toUpperCase();
        mac = mac.replace(":", "");
        mac = mac.replace(" ", "");
        mac = mac.replace("-", "");

        if (mac.length() == 12) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mac.length(); i += 2) {
                if (i > 0) sb.append(":");
                sb.append(mac.substring(i, i + 2));
            }
            return sb.toString();
        }

        return macAddress;
    }
}
