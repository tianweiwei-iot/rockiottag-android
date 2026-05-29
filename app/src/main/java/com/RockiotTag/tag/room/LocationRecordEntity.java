package com.RockiotTag.tag.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room位置记录实体类
 */
@Entity(
    tableName = "location_history",
    indices = {
        @Index(value = {"device_id", "timestamp"}, name = "idx_device_timestamp")
    }
)
public class LocationRecordEntity {
    
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "history_id")
    private long historyId;
    
    @ColumnInfo(name = "device_id")
    private String deviceId;
    
    @ColumnInfo(name = "latitude")
    private double latitude;
    
    @ColumnInfo(name = "longitude")
    private double longitude;
    
    @ColumnInfo(name = "accuracy")
    private float accuracy; // 精度（米）
    
    @ColumnInfo(name = "timestamp")
    private long timestamp;
    
    public LocationRecordEntity() {
    }
    
    @Ignore
    public LocationRecordEntity(long historyId, String deviceId, double latitude, 
                               double longitude, float accuracy, long timestamp) {
        this.historyId = historyId;
        this.deviceId = deviceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.timestamp = timestamp;
    }
    
    // Getters and Setters
    public long getHistoryId() {
        return historyId;
    }
    
    public void setHistoryId(long historyId) {
        this.historyId = historyId;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    
    public float getAccuracy() {
        return accuracy;
    }
    
    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
