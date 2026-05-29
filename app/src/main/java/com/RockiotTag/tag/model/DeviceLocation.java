package com.RockiotTag.tag.model;

import com.RockiotTag.tag.util.TimeFormatter;

/**
 * 设备位置信息（统一封装）
 * 用于静默融合手机GPS和服务器数据，对外提供统一的位置接口
 */
public class DeviceLocation {
    
    private double latitude;
    private double longitude;
    private float accuracy;          // 精度（米），内部使用
    private long timestamp;          // 时间戳
    private String address;          // 地址
    private int battery;             // 电量百分比
    private String deviceId;         // 设备ID（MAC地址），用于UI匹配
    
    // 内部标记，用于日志和调试，不对外暴露
    private DataSource actualSource;
    
    public enum DataSource {
        PHONE_GPS,    // 手机GPS定位
        SERVER,       // 服务器上报
        LOCAL_CACHE,  // 本地缓存
        BLUETOOTH_SCAN // 蓝牙扫描
    }
    
    public DeviceLocation() {
    }
    
    public DeviceLocation(double latitude, double longitude, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }
    
    // Getters and Setters
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
    
    public int getBattery() { 
        return battery; 
    }
    
    public void setBattery(int battery) { 
        this.battery = battery; 
    }
    
    public String getDeviceId() { 
        return deviceId; 
    }
    
    public void setDeviceId(String deviceId) { 
        this.deviceId = deviceId; 
    }
    
    public DataSource getActualSource() { 
        return actualSource; 
    }
    
    public void setActualSource(DataSource source) { 
        this.actualSource = source; 
    }
    
    /**
     * 获取显示用的时间字符串（智能格式，需要Context用于国际化）
     * @param context 上下文
     * @return 例如：2026-05-12 14:32:15 今天
     */
    public String getDisplayTime(android.content.Context context) {
        return TimeFormatter.formatSmartTime(context, timestamp);
    }
    
    /**
     * 获取显示用的时间字符串（固定格式，用于日志和调试）
     * @return 例如：2024-01-15 14:32:15
     */
    public String getDisplayTimeForLog() {
        return TimeFormatter.formatFullTime(timestamp);
    }
    
    /**
     * 获取完整时间字符串
     * 例如：2024-01-15 14:32:15
     */
    public String getFullTime() {
        return TimeFormatter.formatFullTime(timestamp);
    }
    
    /**
     * 位置是否有效
     */
    public boolean isValid() {
        return latitude != 0 && longitude != 0 && timestamp > 0;
    }
    
    @Override
    public String toString() {
        return "DeviceLocation{" +
                "lat=" + latitude +
                ", lng=" + longitude +
                ", accuracy=" + accuracy +
                ", time=" + getDisplayTimeForLog() +
                ", source=" + actualSource +
                '}';
    }
}
