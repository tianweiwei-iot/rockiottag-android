package com.rockiot.tag.repository;

import com.rockiot.tag.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Integer> {
    List<Device> findByUserId(int userId);
    Device findByUserIdAndDeviceNum(int userId, String deviceNum);
    Device findByDeviceNum(String deviceNum);
}
