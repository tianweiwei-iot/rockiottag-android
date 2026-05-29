package com.RockiotTag.tag.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Room设备实体类
 */
@Entity(tableName = "devices")
public class DeviceEntity {
    
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "device_id")
    private String deviceId;
    
    @ColumnInfo(name = "device_num")
    private String deviceNum;
    
    @ColumnInfo(name = "device_name")
    private String deviceName;
    
    @ColumnInfo(name = "device_mac")
    private String deviceMac;
    
    @ColumnInfo(name = "customer_code")
    private String customerCode;
    
    @ColumnInfo(name = "tag")
    private String tag;
    
    @ColumnInfo(name = "latitude")
    private double latitude;
    
    @ColumnInfo(name = "longitude")
    private double longitude;
    
    @ColumnInfo(name = "signal_strength")
    private int signalStrength;
    
    @ColumnInfo(name = "last_seen")
    private long lastSeen;
    
    public DeviceEntity() {
    }
    
    @Ignore
    public DeviceEntity(@NonNull String deviceId, String deviceNum, String deviceName, 
                       String deviceMac, String customerCode, String tag, double latitude, double longitude, 
                       int signalStrength, long lastSeen) {
        this.deviceId = deviceId;
        this.deviceNum = deviceNum;
        this.deviceName = deviceName;
        this.deviceMac = deviceMac;
        this.customerCode = customerCode;
        this.tag = tag;
        this.latitude = latitude;
        this.longitude = longitude;
        this.signalStrength = signalStrength;
        this.lastSeen = lastSeen;
    }
    
    // Getters and Setters
    @NonNull
    public String getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(@NonNull String deviceId) {
        this.deviceId = deviceId;
    }
    
    public String getDeviceNum() {
        return deviceNum;
    }
    
    public void setDeviceNum(String deviceNum) {
        this.deviceNum = deviceNum;
    }
    
    public String getDeviceName() {
        return deviceName;
    }
    
    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }
    
    public String getDeviceMac() {
        return deviceMac;
    }
    
    public void setDeviceMac(String deviceMac) {
        this.deviceMac = deviceMac;
    }
    
    public String getCustomerCode() {
        return customerCode;
    }
    
    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
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
    
    public int getSignalStrength() {
        return signalStrength;
    }
    
    public void setSignalStrength(int signalStrength) {
        this.signalStrength = signalStrength;
    }
    
    public long getLastSeen() {
        return lastSeen;
    }
    
    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }
}
