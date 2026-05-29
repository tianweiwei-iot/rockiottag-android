package com.RockiotTag.tag.room;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 设备数据访问对象
 */
@Dao
public interface DeviceDao {
    
    /**
     * 插入或更新设备
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDevice(DeviceEntity device);
    
    /**
     * 批量插入设备
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDevices(List<DeviceEntity> devices);
    
    /**
     * 更新设备
     */
    @Update
    void updateDevice(DeviceEntity device);
    
    /**
     * 删除设备
     */
    @Delete
    void deleteDevice(DeviceEntity device);
    
    /**
     * 根据设备ID删除设备
     */
    @Query("DELETE FROM devices WHERE device_id = :deviceId")
    int deleteDeviceById(String deviceId);
    
    /**
     * 获取所有设备
     */
    @Query("SELECT * FROM devices ORDER BY last_seen DESC")
    List<DeviceEntity> getAllDevices();
    
    /**
     * 获取所有设备（LiveData）
     */
    @Query("SELECT * FROM devices ORDER BY last_seen DESC")
    LiveData<List<DeviceEntity>> getAllDevicesLive();
    
    /**
     * 根据设备ID获取设备
     */
    @Query("SELECT * FROM devices WHERE device_id = :deviceId LIMIT 1")
    DeviceEntity getDeviceById(String deviceId);
    
    /**
     * 根据设备号获取设备
     */
    @Query("SELECT * FROM devices WHERE device_num = :deviceNum LIMIT 1")
    DeviceEntity getDeviceByNum(String deviceNum);
    
    /**
     * 检查设备是否存在
     */
    @Query("SELECT COUNT(*) FROM devices WHERE device_id = :deviceId")
    int isDeviceExists(String deviceId);
    
    /**
     * 获取设备数量
     */
    @Query("SELECT COUNT(*) FROM devices")
    int getDeviceCount();
    
    /**
     * 更新设备位置和时间
     */
    @Query("UPDATE devices SET latitude = :latitude, longitude = :longitude, last_seen = :timestamp WHERE device_id = :deviceId")
    void updateDeviceLocation(String deviceId, double latitude, double longitude, long timestamp);
    
    /**
     * 更新设备MAC地址
     */
    @Query("UPDATE devices SET device_mac = :mac WHERE device_num = :deviceNum")
    int updateDeviceMac(String deviceNum, String mac);
    
    /**
     * 更新设备名称和标签
     */
    @Query("UPDATE devices SET device_name = :name, tag = :tag WHERE device_num = :deviceNum")
    int updateDeviceNameAndTag(String deviceNum, String name, String tag);
}
