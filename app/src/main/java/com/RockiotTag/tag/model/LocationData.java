package com.RockiotTag.tag.model;

import java.io.Serializable;

/**
 * 位置数据模型
 */
public class LocationData implements Serializable {
    private long id;
    private String deviceId;
    private double latitude;
    private double longitude;
    private float accuracy; // 精度（米）
    private long timestamp;
    private String address; // 逆地理编码地址

    public LocationData() {
    }

    public LocationData(String deviceId, double latitude, double longitude, long timestamp) {
        this.deviceId = deviceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    public LocationData(long id, String deviceId, double latitude, double longitude, long timestamp) {
        this.id = id;
        this.deviceId = deviceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "LocationData{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", lat=" + latitude +
                ", lng=" + longitude +
                ", time=" + timestamp +
                '}';
    }
}
