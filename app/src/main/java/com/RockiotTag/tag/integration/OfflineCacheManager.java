package com.RockiotTag.tag.integration;

import android.util.Log;

import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.util.LogUtil;

/**
 * 离线缓存管理器
 * 从 LocationOptimizationManager 拆分，负责上传失败时的本地缓存管理
 */
public class OfflineCacheManager {

    private static final String TAG = "OfflineCacheManager";

    private final DatabaseHelper databaseHelper;

    public OfflineCacheManager(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

    /**
     * 保存位置到离线缓存（上传失败时调用）
     */
    public void saveToOfflineCache(String deviceNum, com.RockiotTag.tag.model.DeviceLocation location) {
        if (databaseHelper == null || location == null) {
            Log.w(TAG, "Cannot save to offline cache: databaseHelper or location is null");
            return;
        }

        try {
            LocationRecord record = new LocationRecord(
                deviceNum,
                location.getLatitude(),
                location.getLongitude(),
                location.getTimestamp()
            );

            databaseHelper.addLocationRecord(record);
            LogUtil.d(TAG, "Location saved to offline cache for device: " + deviceNum);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save to offline cache", e);
        }
    }

    /**
     * 删除离线缓存（上传成功后调用）
     */
    public void removeOfflineCache(String deviceNum, long timestamp) {
        if (databaseHelper == null) {
            return;
        }

        try {
            databaseHelper.deleteLocationRecord(deviceNum, timestamp);
            LogUtil.d(TAG, "Offline cache removed for device: " + deviceNum + ", timestamp: " + timestamp);
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove offline cache", e);
        }
    }
}
