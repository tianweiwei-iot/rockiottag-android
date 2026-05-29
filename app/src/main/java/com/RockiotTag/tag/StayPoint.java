package com.RockiotTag.tag;

import java.util.ArrayList;
import java.util.List;

import com.RockiotTag.tag.model.LocationData;

public class StayPoint {
    private double latitude;
    private double longitude;
    private long arriveTime;
    private long leaveTime;
    private long stayDuration;
    private List<LocationData> mergedRecords;
    private int originalIndex;

    public StayPoint(double latitude, double longitude, long arriveTime, long leaveTime) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.arriveTime = arriveTime;
        this.leaveTime = leaveTime;
        this.stayDuration = leaveTime - arriveTime;
        this.mergedRecords = new ArrayList<>();
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

    public long getArriveTime() {
        return arriveTime;
    }

    public void setArriveTime(long arriveTime) {
        this.arriveTime = arriveTime;
        this.stayDuration = leaveTime - arriveTime;
    }

    public long getLeaveTime() {
        return leaveTime;
    }

    public void setLeaveTime(long leaveTime) {
        this.leaveTime = leaveTime;
        this.stayDuration = leaveTime - arriveTime;
    }

    public long getStayDuration() {
        return stayDuration;
    }

    public String getStayDurationFormatted() {
        long seconds = stayDuration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds = seconds % 60;
        minutes = minutes % 60;
        
        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds);
        } else {
            return String.format("%d秒", seconds);
        }
    }

    public List<LocationData> getMergedRecords() {
        return mergedRecords;
    }

    public void addMergedRecord(LocationData record) {
        this.mergedRecords.add(record);
    }

    public int getMergedCount() {
        return mergedRecords.size();
    }

    public int getOriginalIndex() {
        return originalIndex;
    }

    public void setOriginalIndex(int originalIndex) {
        this.originalIndex = originalIndex;
    }

    public boolean isStayPoint() {
        // 停留点判断条件：
        // 1. 停留时间 >= 2 分钟（减少 GPS 漂移和短暂停留的误判）
        // 2. 至少有 3 个采样点（确保数据可靠性）
        return stayDuration >= 120000 && mergedRecords.size() >= 3;
    }
}
