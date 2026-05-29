package com.RockiotTag.tag.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.RockiotTag.tag.room.AddressCacheDao;
import com.RockiotTag.tag.room.AddressCacheEntity;
import com.RockiotTag.tag.room.AppDatabase;
import com.RockiotTag.tag.room.DeviceDao;
import com.RockiotTag.tag.room.DeviceEntity;
import com.RockiotTag.tag.room.LocationRecordDao;
import com.RockiotTag.tag.room.LocationRecordEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据仓库 - 统一管理数据访问
 */
public class DataRepository {
    
    private static final String TAG = "DataRepository";
    
    private final DeviceDao deviceDao;
    private final LocationRecordDao locationRecordDao;
    private final AddressCacheDao addressCacheDao;
    private final ExecutorService executorService;
    
    private static volatile DataRepository INSTANCE;
    
    private DataRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        this.deviceDao = database.deviceDao();
        this.locationRecordDao = database.locationRecordDao();
        this.addressCacheDao = database.addressCacheDao();
        this.executorService = Executors.newFixedThreadPool(4);
    }
    
    /**
     * 获取单例实例
     */
    public static DataRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DataRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DataRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }
    
    // ==================== 设备相关操作 ====================
    
    /**
     * 插入或更新设备（异步）
     */
    public void insertDevice(DeviceEntity device) {
        executorService.execute(() -> {
            deviceDao.insertDevice(device);
            Log.d(TAG, "Device inserted: " + device.getDeviceId());
        });
    }
    
    /**
     * 批量插入设备（异步）
     */
    public void insertDevices(List<DeviceEntity> devices) {
        executorService.execute(() -> {
            deviceDao.insertDevices(devices);
            Log.d(TAG, "Devices batch inserted: " + devices.size());
        });
    }
    
    /**
     * 获取所有设备（同步，应在后台线程调用）
     */
    public List<DeviceEntity> getAllDevices() {
        return deviceDao.getAllDevices();
    }
    
    /**
     * 获取所有设备（LiveData，自动观察变化）
     */
    public LiveData<List<DeviceEntity>> getAllDevicesLive() {
        return deviceDao.getAllDevicesLive();
    }
    
    /**
     * 根据设备ID获取设备（同步）
     */
    public DeviceEntity getDeviceById(String deviceId) {
        return deviceDao.getDeviceById(deviceId);
    }
    
    /**
     * 根据设备号获取设备（同步）
     */
    public DeviceEntity getDeviceByNum(String deviceNum) {
        return deviceDao.getDeviceByNum(deviceNum);
    }
    
    /**
     * 删除设备（异步）
     */
    public void deleteDevice(String deviceId) {
        executorService.execute(() -> {
            int deleted = deviceDao.deleteDeviceById(deviceId);
            Log.d(TAG, "Device deleted: " + deviceId + ", rows: " + deleted);
        });
    }
    
    /**
     * 更新设备位置（异步）
     */
    public void updateDeviceLocation(String deviceId, double latitude, double longitude, long timestamp) {
        executorService.execute(() -> {
            deviceDao.updateDeviceLocation(deviceId, latitude, longitude, timestamp);
        });
    }
    
    /**
     * 更新设备MAC地址（异步）
     */
    public void updateDeviceMac(String deviceNum, String mac) {
        executorService.execute(() -> {
            int updated = deviceDao.updateDeviceMac(deviceNum, mac);
            Log.d(TAG, "Device MAC updated: " + deviceNum + ", rows: " + updated);
        });
    }
    
    // ==================== 位置记录相关操作 ====================
    
    /**
     * 插入位置记录（异步）
     */
    public void insertLocationRecord(LocationRecordEntity record) {
        executorService.execute(() -> {
            locationRecordDao.insertLocationRecord(record);
        });
    }
    
    /**
     * 批量插入位置记录（异步）
     */
    public void insertLocationRecords(List<LocationRecordEntity> records) {
        executorService.execute(() -> {
            locationRecordDao.insertLocationRecords(records);
            Log.d(TAG, "Location records batch inserted: " + records.size());
        });
    }
    
    /**
     * 获取位置记录（同步，应在后台线程调用）
     */
    public List<LocationRecordEntity> getLocationRecords(String deviceId, long startTime, long endTime) {
        return locationRecordDao.getLocationRecords(deviceId, startTime, endTime);
    }
    
    /**
     * 获取位置记录（LiveData）
     */
    public LiveData<List<LocationRecordEntity>> getLocationRecordsLive(String deviceId, long startTime, long endTime) {
        return locationRecordDao.getLocationRecordsLive(deviceId, startTime, endTime);
    }
    
    /**
     * 删除位置记录（异步）
     */
    public void deleteLocationRecord(String deviceId, long timestamp) {
        executorService.execute(() -> {
            int deleted = locationRecordDao.deleteLocationRecordByDeviceAndTimestamp(deviceId, timestamp);
            Log.d(TAG, "Location record deleted: " + deviceId + "@" + timestamp + ", rows: " + deleted);
        });
    }
    
    /**
     * 清理旧的位置记录（异步）
     */
    public void cleanOldLocationRecords(long cutoffTime) {
        executorService.execute(() -> {
            int deleted = locationRecordDao.cleanOldLocationRecords(cutoffTime);
            Log.d(TAG, "Old location records cleaned: " + deleted);
        });
    }
    
    /**
     * 删除指定设备的所有位置记录（异步）
     */
    public void deleteLocationRecordsByDevice(String deviceId) {
        executorService.execute(() -> {
            int deleted = locationRecordDao.deleteLocationRecordsByDevice(deviceId);
            Log.d(TAG, "Location records deleted for device: " + deviceId + ", rows: " + deleted);
        });
    }
    
    // ==================== 地址缓存相关操作 ====================
    
    /**
     * 保存地址缓存（异步）
     */
    public void saveAddressCache(AddressCacheEntity cache) {
        executorService.execute(() -> {
            addressCacheDao.insertAddressCache(cache);
            Log.d(TAG, "Address cache saved: " + cache.getCacheKey());
        });
    }
    
    /**
     * 获取地址缓存（同步）
     */
    public AddressCacheEntity getAddressCache(String cacheKey) {
        return addressCacheDao.getAddressCache(cacheKey);
    }
    
    /**
     * 清理过期地址缓存（异步）
     */
    public void cleanExpiredAddressCache(long cutoffTime) {
        executorService.execute(() -> {
            int deleted = addressCacheDao.cleanExpiredAddressCache(cutoffTime);
            Log.d(TAG, "Expired address cache cleaned: " + deleted);
        });
    }
}
