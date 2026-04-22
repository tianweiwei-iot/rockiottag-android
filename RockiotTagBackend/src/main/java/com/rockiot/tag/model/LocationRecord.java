package com.rockiot.tag.model;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "location_records")
public class LocationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    
    private int userId;
    private String deviceNum;
    private double latitude;
    private double longitude;
    private int battery;
    private long timestamp;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", updatable = false)
    private Date createdAt;
    
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getDeviceNum() { return deviceNum; }
    public void setDeviceNum(String deviceNum) { this.deviceNum = deviceNum; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public int getBattery() { return battery; }
    public void setBattery(int battery) { this.battery = battery; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
