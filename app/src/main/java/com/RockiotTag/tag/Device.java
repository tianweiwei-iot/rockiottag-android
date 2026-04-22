package com.RockiotTag.tag;

public class Device {
    private String deviceId;
    private String deviceNum;
    private String name;
    private String address;
    private String tag;
    private double latitude;
    private double longitude;
    private int signalStrength;
    private long lastSeen;

    public Device(String deviceId, String name) {
        this.deviceId = deviceId;
        this.deviceNum = deviceId;
        this.name = name;
        this.address = deviceId;
        this.tag = "";
        this.latitude = 0;
        this.longitude = 0;
        this.signalStrength = 0;
        this.lastSeen = System.currentTimeMillis();
    }

    public Device(String deviceId, String name, String address) {
        this.deviceId = deviceId;
        this.deviceNum = deviceId;
        this.name = name;
        this.address = address;
        this.tag = "";
        this.latitude = 0;
        this.longitude = 0;
        this.signalStrength = 0;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getDeviceNum() {
        return deviceNum;
    }

    public void setDeviceNum(String deviceNum) {
        this.deviceNum = deviceNum;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    @Override
    public String toString() {
        return "Device{" +
                "deviceId='" + deviceId + '\'' +
                ", name='" + name + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", signalStrength=" + signalStrength +
                ", lastSeen=" + lastSeen +
                '}';
    }
}
