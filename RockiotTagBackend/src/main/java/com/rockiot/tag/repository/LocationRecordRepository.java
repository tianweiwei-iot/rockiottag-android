package com.rockiot.tag.repository;

import com.rockiot.tag.model.LocationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LocationRecordRepository extends JpaRepository<LocationRecord, Long> {
    List<LocationRecord> findByUserIdAndDeviceNum(int userId, String deviceNum);
    List<LocationRecord> findByUserIdAndDeviceNumAndTimestampBetween(
        int userId, String deviceNum, long startTime, long endTime
    );
    LocationRecord findByUserIdAndDeviceNumAndTimestamp(int userId, String deviceNum, long timestamp);
    
    void deleteByDeviceNum(String deviceNum);
    
    void deleteByUserIdAndDeviceNum(int userId, String deviceNum);
}