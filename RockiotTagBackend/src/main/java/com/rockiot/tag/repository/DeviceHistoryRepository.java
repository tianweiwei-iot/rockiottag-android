package com.rockiot.tag.repository;

import com.rockiot.tag.model.DeviceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DeviceHistoryRepository extends JpaRepository<DeviceHistory, Long> {
    List<DeviceHistory> findByUserIdAndDeviceNumOrderByTimestampDesc(int userId, String deviceNum);
    
    Optional<DeviceHistory> findFirstByUserIdAndDeviceNumOrderByTimestampDesc(int userId, String deviceNum);
    
    @Query("SELECT dh FROM DeviceHistory dh WHERE dh.userId = :userId AND dh.deviceNum = :deviceNum ORDER BY dh.timestamp DESC LIMIT 1")
    Optional<DeviceHistory> findLatestByUserIdAndDeviceNum(@Param("userId") int userId, @Param("deviceNum") String deviceNum);
    
    List<DeviceHistory> findByDeviceNumOrderByTimestampDesc(String deviceNum);
    
    @Query("SELECT dh FROM DeviceHistory dh WHERE dh.deviceNum = :deviceNum ORDER BY dh.timestamp DESC LIMIT 1")
    Optional<DeviceHistory> findLatestByDeviceNum(@Param("deviceNum") String deviceNum);
    
    void deleteByDeviceNum(String deviceNum);
    
    void deleteByUserIdAndDeviceNum(int userId, String deviceNum);
    
    @Query("SELECT dh FROM DeviceHistory dh WHERE dh.deviceNum = :deviceNum AND dh.timestamp >= :startTime AND dh.timestamp <= :endTime ORDER BY dh.timestamp ASC")
    List<DeviceHistory> findByDeviceNumAndTimeRange(@Param("deviceNum") String deviceNum, @Param("startTime") long startTime, @Param("endTime") long endTime);
    
    List<DeviceHistory> findByDeviceNumAndTimestampBetweenOrderByTimestampAsc(String deviceNum, long startTime, long endTime);
}
