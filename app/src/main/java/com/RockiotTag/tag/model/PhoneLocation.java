package com.RockiotTag.tag.model;

/**
 * 手机位置信息
 * 用于封装手机GPS或网络定位的结果
 */
public class PhoneLocation {
    
    private double latitude;
    private double longitude;
    private float accuracy;      // 精度（米）
    private long timestamp;
    private String provider;     // GPS或NETWORK
    
    public PhoneLocation() {
    }
    
    public PhoneLocation(double latitude, double longitude, float accuracy, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.timestamp = timestamp;
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
    
    public String getProvider() { 
        return provider; 
    }
    
    public void setProvider(String provider) { 
        this.provider = provider; 
    }
    
    /**
     * 位置是否有效
     */
    public boolean isValid() {
        return latitude != 0 && longitude != 0 && accuracy > 0;
    }
    
    @Override
    public String toString() {
        return "PhoneLocation{" +
                "lat=" + latitude +
                ", lng=" + longitude +
                ", accuracy=" + accuracy +
                ", provider='" + provider + '\'' +
                '}';
    }
}
