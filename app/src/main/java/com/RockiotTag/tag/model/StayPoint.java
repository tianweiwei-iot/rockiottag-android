package com.RockiotTag.tag.model;

import java.io.Serializable;

/**
 * 停留点模型（用于轨迹回放）
 */
public class StayPoint implements Serializable {
    private double latitude;
    private double longitude;
    private long arriveTime;
    private long leaveTime;
    private int duration; // 停留时长（秒）
    private int originalIndex;
    private java.util.List<LocationData> mergedRecords = new java.util.ArrayList<>();

    public StayPoint(double latitude, double longitude, long arriveTime, long leaveTime) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.arriveTime = arriveTime;
        this.leaveTime = leaveTime;
        this.duration = (int) ((leaveTime - arriveTime) / 1000);
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
    }

    public long getLeaveTime() {
        return leaveTime;
    }

    public void setLeaveTime(long leaveTime) {
        this.leaveTime = leaveTime;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getOriginalIndex() {
        return originalIndex;
    }

    public void setOriginalIndex(int originalIndex) {
        this.originalIndex = originalIndex;
    }

    public java.util.List<LocationData> getMergedRecords() {
        return mergedRecords;
    }

    public void addMergedRecord(LocationData record) {
        this.mergedRecords.add(record);
    }

    public int getMergedCount() {
        return mergedRecords.size();
    }

    public boolean isStayPoint() {
        return duration > 60; // 假设超过60秒算停留点
    }

    public String getStayDurationFormatted() {
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format("%d分%d秒", minutes, seconds);
    }
}
