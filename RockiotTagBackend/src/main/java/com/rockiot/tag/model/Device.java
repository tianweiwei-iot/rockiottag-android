package com.rockiot.tag.model;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "devices", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "device_num"}, name = "uk_user_device_num")
})
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    
    @Column(name = "user_id")
    private int userId;
    
    @Column(name = "device_num")
    private String deviceNum;
    
    private String sn;
    
    @Column(name = "nick_name")
    private String nickName;
    
    private String mac;
    
    private Double latitude;
    private Double longitude;
    private Integer battery;
    private Long timestamp;
    private String address;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", updatable = false)
    private Date createdAt;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at")
    private Date updatedAt;
    
    public int getId() { 
        return id; 
    }
    public void setId(int id) { 
        this.id = id; 
    }
    
    public int getUserId() { 
        return userId; 
    }
    public void setUserId(int userId) { 
        this.userId = userId; 
    }
    
    public String getDeviceNum() { 
        return deviceNum; 
    }
    public void setDeviceNum(String deviceNum) { 
        this.deviceNum = deviceNum; 
    }
    
    public String getSn() { 
        return sn; 
    }
    public void setSn(String sn) { 
        this.sn = sn; 
    }
    
    public String getNickName() { 
        return nickName; 
    }
    public void setNickName(String nickName) { 
        this.nickName = nickName; 
    }
    
    public String getMac() { 
        return mac; 
    }
    public void setMac(String mac) { 
        this.mac = mac; 
    }
    
    public Double getLatitude() { 
        return latitude; 
    }
    public void setLatitude(Double latitude) { 
        this.latitude = latitude; 
    }
    
    public Double getLongitude() { 
        return longitude; 
    }
    public void setLongitude(Double longitude) { 
        this.longitude = longitude; 
    }
    
    public Integer getBattery() { 
        return battery; 
    }
    public void setBattery(Integer battery) { 
        this.battery = battery; 
    }
    
    public Long getTimestamp() { 
        return timestamp; 
    }
    public void setTimestamp(Long timestamp) { 
        this.timestamp = timestamp; 
    }
    
    public String getAddress() { 
        return address; 
    }
    public void setAddress(String address) { 
        this.address = address; 
    }
    
    public Date getCreatedAt() { 
        return createdAt; 
    }
    public void setCreatedAt(Date createdAt) { 
        this.createdAt = createdAt; 
    }
    
    public Date getUpdatedAt() { 
        return updatedAt; 
    }
    public void setUpdatedAt(Date updatedAt) { 
        this.updatedAt = updatedAt; 
    }
}
