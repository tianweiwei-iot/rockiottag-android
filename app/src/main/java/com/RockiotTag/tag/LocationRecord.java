package com.RockiotTag.tag;

public class LocationRecord {
    private long id;
    private String deviceId;
    private double latitude;
    private double longitude;
    private float accuracy; // 精度（米）
    private long timestamp;

    public LocationRecord(String deviceId, double latitude, double longitude, long timestamp) {
        this.deviceId = deviceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = 50.0f; // 默认BLE Tag精度
        this.timestamp = timestamp;
    }
    
    public LocationRecord(String deviceId, double latitude, double longitude, float accuracy, long timestamp) {
        this.deviceId = deviceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.timestamp = timestamp;
    }

    public LocationRecord(long id, String deviceId, double latitude, double longitude, long timestamp) {
        this.id = id;
        this.deviceId = deviceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = 50.0f; // 默认BLE Tag精度
        this.timestamp = timestamp;
    }
    
    public LocationRecord(long id, String deviceId, double latitude, double longitude, float accuracy, long timestamp) {
        this.id = id;
        this.deviceId = deviceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
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
}
