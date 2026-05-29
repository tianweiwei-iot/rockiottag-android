package com.RockiotTag.tag.bluetooth;

/**
 * 蓝牙扫描结果
 * 用于记录设备扫描时的信号强度和时间
 */
public class BluetoothScanResult {
    
    private String deviceId;
    private int rssi;              // 信号强度（dBm）
    private long scanTime;         // 扫描时间戳
    
    public BluetoothScanResult() {
    }
    
    public BluetoothScanResult(String deviceId, int rssi, long scanTime) {
        this.deviceId = deviceId;
        this.rssi = rssi;
        this.scanTime = scanTime;
    }
    
    public String getDeviceId() { 
        return deviceId; 
    }
    
    public void setDeviceId(String deviceId) { 
        this.deviceId = deviceId; 
    }
    
    public int getRssi() { 
        return rssi; 
    }
    
    public void setRssi(int rssi) { 
        this.rssi = rssi; 
    }
    
    public long getScanTime() { 
        return scanTime; 
    }
    
    public void setScanTime(long scanTime) { 
        this.scanTime = scanTime; 
    }
    
    /**
     * 判断扫描结果是否有效（5分钟内）
     */
    public boolean isValid() {
        long age = System.currentTimeMillis() - scanTime;
        return age < 5 * 60 * 1000; // 5分钟
    }
    
    @Override
    public String toString() {
        return "BluetoothScanResult{" +
                "deviceId='" + deviceId + '\'' +
                ", rssi=" + rssi +
                ", scanTime=" + scanTime +
                '}';
    }
}
