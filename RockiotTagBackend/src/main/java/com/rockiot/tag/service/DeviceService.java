package com.rockiot.tag.service;

import com.rockiot.tag.model.Device;
import com.rockiot.tag.repository.DeviceRepository;
import com.rockiot.tag.repository.DeviceHistoryRepository;
import com.rockiot.tag.repository.LocationRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class DeviceService {
    @Autowired
    private DeviceRepository deviceRepository;
    
    @Autowired
    private DeviceHistoryRepository deviceHistoryRepository;
    
    @Autowired
    private LocationRecordRepository locationRecordRepository;

    public List<Device> getDevicesByUserId(int userId) {
        return deviceRepository.findByUserId(userId);
    }

    public Device getDeviceByDeviceNum(String deviceNum) {
        return deviceRepository.findByDeviceNum(deviceNum);
    }

    public Device bindDevice(int userId, String deviceNum, String sn, String nickName) {
        Device device = deviceRepository.findByDeviceNum(deviceNum);
        if (device == null) {
            return null;
        }
        if (nickName != null) device.setNickName(nickName);
        if (sn != null) device.setSn(sn);
        return deviceRepository.save(device);
    }

    @Transactional
    public void unbindDevice(int userId, String deviceNum) {
        Device device = deviceRepository.findByUserIdAndDeviceNum(userId, deviceNum);
        if (device != null) {
            deviceHistoryRepository.deleteByUserIdAndDeviceNum(userId, deviceNum);
            locationRecordRepository.deleteByUserIdAndDeviceNum(userId, deviceNum);
            deviceRepository.delete(device);
        }
    }
}
