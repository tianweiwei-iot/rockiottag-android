package com.RockiotTag.tag.model;

import java.io.Serializable;

/**
 * 智能标签设备模型
 */
public class TagDevice implements Serializable {
    private String deviceId;
    private String deviceNum;
    private String name;
    private String address;
    private String tag;
    private String mac; // MAC地址
    private double latitude;
    private double longitude;
    private int batteryLevel; // 电量
    private int signalStrength; // 信号强度
    private long lastSeen; // 最后可见时间
    private long updatedAt; // 数据库更新时间戳

    public TagDevice() {
    }

    public TagDevice(String deviceId, String name) {
        this.deviceId = deviceId;
        this.deviceNum = deviceId;
        this.name = name;
        this.address = deviceId;
        this.tag = "";
        this.latitude = 0;
        this.longitude = 0;
        this.batteryLevel = -1;
        this.signalStrength = 0;
        this.lastSeen = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceNum() {
        return deviceNum;
    }

    public void setDeviceNum(String deviceNum) {
        this.deviceNum = deviceNum;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
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

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
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

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "TagDevice{" +
                "deviceId='" + deviceId + '\'' +
                ", deviceNum='" + deviceNum + '\'' +
                ", name='" + name + '\'' +
                ", lat=" + latitude +
                ", lng=" + longitude +
                ", battery=" + batteryLevel +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        TagDevice that = (TagDevice) obj;
        
        return Double.compare(that.latitude, latitude) == 0 &&
               Double.compare(that.longitude, longitude) == 0 &&
               batteryLevel == that.batteryLevel &&
               signalStrength == that.signalStrength &&
               lastSeen == that.lastSeen &&
               updatedAt == that.updatedAt &&
               deviceId.equals(that.deviceId) &&
               (deviceNum != null ? deviceNum.equals(that.deviceNum) : that.deviceNum == null) &&
               (name != null ? name.equals(that.name) : that.name == null) &&
               (address != null ? address.equals(that.address) : that.address == null) &&
               (tag != null ? tag.equals(that.tag) : that.tag == null) &&
               (mac != null ? mac.equals(that.mac) : that.mac == null);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        
        result = deviceId.hashCode();
        result = 31 * result + (deviceNum != null ? deviceNum.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (address != null ? address.hashCode() : 0);
        result = 31 * result + (tag != null ? tag.hashCode() : 0);
        result = 31 * result + (mac != null ? mac.hashCode() : 0);
        temp = Double.doubleToLongBits(latitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(longitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + batteryLevel;
        result = 31 * result + signalStrength;
        result = 31 * result + (int) (lastSeen ^ (lastSeen >>> 32));
        result = 31 * result + (int) (updatedAt ^ (updatedAt >>> 32));
        
        return result;
    }
}
