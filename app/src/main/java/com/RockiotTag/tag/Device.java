package com.RockiotTag.tag;

public class Device {
    private String deviceId;
    private String deviceNum;
    private String name;
    private String address;
    private String tag;
    private String mac;
    private String customerCode;
    private double latitude;
    private double longitude;
    private int signalStrength;
    private long lastSeen;
    private boolean isNearby;
    private Long bluetoothScanTime;
    private int battery;

    public Device(String deviceId, String name) {
        this.deviceId = deviceId;
        this.deviceNum = deviceId;
        this.name = name;
        this.address = deviceId;
        this.tag = "";
        this.customerCode = "";
        this.latitude = 0;
        this.longitude = 0;
        this.signalStrength = 0;
        this.lastSeen = System.currentTimeMillis();
        this.battery = -1;
    }

    public Device(String deviceId, String name, String address) {
        this.deviceId = deviceId;
        this.deviceNum = deviceId;
        this.name = name;
        this.address = address;
        this.tag = "";
        this.customerCode = "";
        this.latitude = 0;
        this.longitude = 0;
        this.signalStrength = 0;
        this.lastSeen = System.currentTimeMillis();
        this.battery = -1;
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

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
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

    public boolean isNearby() {
        return isNearby;
    }

    public void setNearby(boolean nearby) {
        isNearby = nearby;
    }

    public Long getBluetoothScanTime() {
        return bluetoothScanTime;
    }

    public void setBluetoothScanTime(Long bluetoothScanTime) {
        this.bluetoothScanTime = bluetoothScanTime;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public int getBattery() {
        return battery;
    }

    public void setBattery(int battery) {
        this.battery = battery;
    }

    @Override
    public String toString() {
        return "Device{" +
                "deviceId='" + deviceId + '\'' +
                ", name='" + name + '\'' +
                ", customerCode='" + customerCode + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", signalStrength=" + signalStrength +
                ", lastSeen=" + lastSeen +
                ", battery=" + battery +
                '}';
    }
}
