package com.rockiot.tag.service;

import com.rockiot.tag.model.LocationRecord;
import com.rockiot.tag.repository.LocationRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class LocationService {
    @Autowired
    private LocationRecordRepository locationRecordRepository;

    public void saveLocation(int userId, String deviceNum, double latitude, double longitude, int battery, long timestamp) {
        LocationRecord record = new LocationRecord();
        record.setUserId(userId);
        record.setDeviceNum(deviceNum);
        record.setLatitude(latitude);
        record.setLongitude(longitude);
        record.setBattery(battery);
        record.setTimestamp(timestamp);
        locationRecordRepository.save(record);
    }

    public List<LocationRecord> getLocations(int userId, String deviceNum, long startTime, long endTime) {
        if (startTime > 0 && endTime > 0) {
            return locationRecordRepository.findByUserIdAndDeviceNumAndTimestampBetweenOrderByTimestampDesc(userId, deviceNum, startTime, endTime);
        }
        return locationRecordRepository.findByUserIdAndDeviceNumOrderByTimestampDesc(userId, deviceNum);
    }
}