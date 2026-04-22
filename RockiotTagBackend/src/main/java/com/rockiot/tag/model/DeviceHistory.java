package com.rockiot.tag.model;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "device_history")
public class DeviceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private int userId;
    private String deviceNum;
    
    private Double latitude;
    private Double longitude;
    private Integer battery;
    private Long timestamp;
    private String address;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", updatable = false)
    private Date createdAt;
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    
    public String getDeviceNum() { return deviceNum; }
    public void setDeviceNum(String deviceNum) { this.deviceNum = deviceNum; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    
    public Integer getBattery() { return battery; }
    public void setBattery(Integer battery) { this.battery = battery; }
    
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
