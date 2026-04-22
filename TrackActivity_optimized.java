package com.rockiot.tag.controller;

import com.rockiot.tag.model.Device;
import com.rockiot.tag.model.DeviceHistory;
import com.rockiot.tag.service.DeviceService;
import com.rockiot.tag.repository.DeviceRepository;
import com.rockiot.tag.repository.DeviceHistoryRepository;
import com.rockiot.tag.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    @Autowired
    private DeviceService deviceService;
    
    @Autowired
    private DeviceRepository deviceRepository;
    
    @Autowired
    private DeviceHistoryRepository deviceHistoryRepository;

    @GetMapping
    public List<Device> getDevices(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && !token.isEmpty()) {
            int userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            return deviceService.getDevicesByUserId(userId);
        }
        return List.of();
    }

    @GetMapping("/all")
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    @PostMapping("/bind")
    public Device bindDevice(@RequestHeader(value = "Authorization", required = false) String token, @RequestBody Map<String, String> params) {
        int userId = 0;
        if (token != null && !token.isEmpty()) {
            userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        }
        String deviceNum = params.get("deviceNum");
        String sn = params.get("sn");
        String nickName = params.get("nickName");
        return deviceService.bindDevice(userId, deviceNum, sn, nickName);
    }

    @PostMapping("/unbind")
    public void unbindDevice(@RequestHeader(value = "Authorization", required = false) String token, @RequestBody Map<String, String> params) {
        int userId = 0;
        if (token != null && !token.isEmpty()) {
            userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        }
        String deviceNum = params.get("deviceNum");
        deviceService.unbindDevice(userId, deviceNum);
    }
    
    @GetMapping("/{deviceNum}/history")
    public List<DeviceHistory> getDeviceHistory(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String deviceNum) {
        if (token != null && !token.isEmpty()) {
            int userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            return deviceHistoryRepository.findByUserIdAndDeviceNumOrderByTimestampDesc(userId, deviceNum);
        }
        return deviceHistoryRepository.findByDeviceNumOrderByTimestampDesc(deviceNum);
    }
    
    @GetMapping("/{deviceNum}/latest")
    public Map<String, Object> getDeviceLatest(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String deviceNum) {
        Map<String, Object> result = new HashMap<>();
        
        Device device = deviceRepository.findByDeviceNum(deviceNum);
        
        if (device != null) {
            result.put("deviceNum", device.getDeviceNum());
            result.put("nickName", device.getNickName());
            result.put("latitude", device.getLatitude());
            result.put("longitude", device.getLongitude());
            result.put("battery", device.getBattery());
            result.put("timestamp", device.getTimestamp());
            result.put("address", device.getAddress());
            result.put("updatedAt", device.getUpdatedAt());
        }
        
        return result;
    }
}
