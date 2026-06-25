package com.RockiotTag.tag.repository;

import android.content.Context;
import android.util.Log;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.model.LocationData;
import com.RockiotTag.tag.util.LogUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 位置数据仓库 - 统一管理位置数据的本地和远程操作
 */
public class LocationRepository {
    private static final String TAG = "LocationRepository";
    
    private DatabaseHelper databaseHelper;
    private NewApiService apiService;
    private static LocationRepository instance;
    
    public static synchronized LocationRepository getInstance(Context context) {
        if (instance == null) {
            instance = new LocationRepository(context);
        }
        return instance;
    }
    
    // 公开构造函数，供ViewModel工厂使用
    public LocationRepository(Context context) {
        this.databaseHelper = DatabaseHelper.getInstance(context);
        this.apiService = NewApiService.getInstance();
    }
    
    /**
     * 保存位置记录到本地数据库
     */
    public void saveLocationRecord(LocationRecord record) {
        LogUtil.d(TAG, "Saving location record for device: " + record.getDeviceId());
        databaseHelper.addLocationRecord(record);
    }
    
    /**
     * 获取指定设备和时间范围的位置记录（转换为新模型）
     */
    public List<LocationData> getLocationRecords(String deviceId, long startTime, long endTime) {
        LogUtil.d(TAG, "Getting location records for device: " + deviceId);
        List<LocationRecord> oldRecords = databaseHelper.getLocationRecords(deviceId, startTime, endTime);
        List<LocationData> newRecords = new ArrayList<>();
        for (LocationRecord lr : oldRecords) {
            LocationData ld = convertToLocationData(lr);
            if (ld != null) newRecords.add(ld);
        }
        return newRecords;
    }
    
    /**
     * 获取轨迹数据（返回LocationRecord，用于UseCase）
     */
    public List<LocationRecord> getTrackData(String deviceNum, long startTime, long endTime) {
        LogUtil.d(TAG, "Getting track data for device: " + deviceNum);
        return databaseHelper.getLocationRecords(deviceNum, startTime, endTime);
    }
    
    /**
     * 转换旧 LocationRecord 为新 LocationData
     */
    private LocationData convertToLocationData(LocationRecord record) {
        if (record == null) return null;
        LocationData ld = new LocationData();
        ld.setId(record.getId());
        ld.setDeviceId(record.getDeviceId());
        ld.setLatitude(record.getLatitude());
        ld.setLongitude(record.getLongitude());
        ld.setTimestamp(record.getTimestamp());
        return ld;
    }
    
    /**
     * 获取指定设备和日期范围的位置记录（按天，转换为新模型）
     */
    public List<LocationData> getLocationRecordsByDateRange(String deviceId, java.util.Date startDate, java.util.Date endDate) {
        LogUtil.d(TAG, "Getting location records by date range for device: " + deviceId);
        long startTime = startDate.getTime();
        long endTime = endDate.getTime();
        List<LocationRecord> oldRecords = databaseHelper.getLocationRecords(deviceId, startTime, endTime);
        List<LocationData> newRecords = new ArrayList<>();
        for (LocationRecord lr : oldRecords) {
            LocationData ld = convertToLocationData(lr);
            if (ld != null) newRecords.add(ld);
        }
        return newRecords;
    }
    
    /**
     * 从服务器获取位置记录
     */
    public List<NewApiService.LocationInfo> getRemoteLocations(String deviceNum, long startTime, long endTime) {
        LogUtil.d(TAG, "Getting remote locations for device: " + deviceNum);
        try {
            return apiService.getLocations(ApiConfig.getMyServerUrl(deviceNum), deviceNum, startTime, endTime);
        } catch (Exception e) {
            Log.e(TAG, "Error getting remote locations: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 同步位置到服务器
     */
    public interface SyncCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public void syncLocationToServer(String deviceNum, double latitude, double longitude, int battery, long timestamp, SyncCallback callback) {
        LogUtil.d(TAG, "Syncing location to server for device: " + deviceNum);
        
        new Thread(() -> {
            try {
                NewApiService.ApiResponse response = apiService.syncLocation(ApiConfig.getMyServerUrl(deviceNum), deviceNum, latitude, longitude, battery, timestamp);
                
                if (response != null && response.isSuccess()) {
                    LogUtil.d(TAG, "Location synced successfully");
                    if (callback != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onSuccess);
                    }
                } else {
                    Log.e(TAG, "Failed to sync location: " + (response != null ? response.getMessage() : "null response"));
                    if (callback != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                            callback.onError("Sync failed"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error syncing location: " + e.getMessage(), e);
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                        callback.onError(e.getMessage()));
                }
            }
        }).start();
    }
    
    /**
     * 清理旧的位置记录（超过一个月）
     */
    public void cleanOldLocationRecords() {
        LogUtil.d(TAG, "Cleaning old location records");
        databaseHelper.cleanOldLocationRecords();
    }
    
    /**
     * 删除所有位置记录
     */
    public int deleteAllLocationRecords() {
        LogUtil.d(TAG, "Deleting all location records");
        return databaseHelper.deleteAllLocationRecords();
    }
    
    /**
     * 删除指定设备的位置记录
     */
    public int deleteLocationRecordsByDevice(String deviceId) {
        LogUtil.d(TAG, "Deleting location records for device: " + deviceId);
        return databaseHelper.deleteLocationRecordsByDevice(deviceId);
    }
    
    /**
     * 获取设备最新的位置记录时间戳
     */
    public long getLatestRecordTimestamp(String deviceId) {
        return databaseHelper.getLatestRecordTimestamp(deviceId);
    }
}
