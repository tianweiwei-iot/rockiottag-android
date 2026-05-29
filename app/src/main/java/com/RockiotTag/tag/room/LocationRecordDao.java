package com.RockiotTag.tag.room;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * 位置记录数据访问对象
 */
@Dao
public interface LocationRecordDao {
    
    /**
     * 插入位置记录
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertLocationRecord(LocationRecordEntity record);
    
    /**
     * 批量插入位置记录
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertLocationRecords(List<LocationRecordEntity> records);
    
    /**
     * 删除位置记录
     */
    @Query("DELETE FROM location_history WHERE history_id = :recordId")
    void deleteLocationRecord(long recordId);
    
    /**
     * 根据设备ID和时间戳删除位置记录
     */
    @Query("DELETE FROM location_history WHERE device_id = :deviceId AND timestamp = :timestamp")
    int deleteLocationRecordByDeviceAndTimestamp(String deviceId, long timestamp);
    
    /**
     * 获取指定设备和时间范围的位置记录（按时间升序）
     */
    @Query("SELECT * FROM location_history WHERE device_id = :deviceId AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    List<LocationRecordEntity> getLocationRecords(String deviceId, long startTime, long endTime);
    
    /**
     * 获取指定设备和时间范围的位置记录（LiveData）
     */
    @Query("SELECT * FROM location_history WHERE device_id = :deviceId AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    LiveData<List<LocationRecordEntity>> getLocationRecordsLive(String deviceId, long startTime, long endTime);
    
    /**
     * 获取设备最新的位置记录时间戳
     */
    @Query("SELECT MAX(timestamp) FROM location_history WHERE device_id = :deviceId")
    Long getLatestRecordTimestamp(String deviceId);
    
    /**
     * 清理超过指定时间的位置记录
     */
    @Query("DELETE FROM location_history WHERE timestamp < :cutoffTime")
    int cleanOldLocationRecords(long cutoffTime);
    
    /**
     * 删除所有位置记录
     */
    @Query("DELETE FROM location_history")
    int deleteAllLocationRecords();
    
    /**
     * 删除指定设备的所有位置记录
     */
    @Query("DELETE FROM location_history WHERE device_id = :deviceId")
    int deleteLocationRecordsByDevice(String deviceId);
    
    /**
     * 获取位置记录数量
     */
    @Query("SELECT COUNT(*) FROM location_history")
    int getLocationRecordCount();
}
