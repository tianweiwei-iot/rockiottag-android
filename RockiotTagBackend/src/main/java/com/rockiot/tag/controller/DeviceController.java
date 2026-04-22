package com.rockiot.tag.controller;

import com.rockiot.tag.model.Device;
import com.rockiot.tag.model.DeviceHistory;
import com.rockiot.tag.service.DeviceService;
import com.rockiot.tag.service.VendorApiService;
import com.rockiot.tag.service.DataSyncService;
import com.rockiot.tag.repository.DeviceHistoryRepository;
import com.rockiot.tag.repository.DeviceRepository;
import com.rockiot.tag.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private DeviceHistoryRepository deviceHistoryRepository;
    
    @Autowired
    private DeviceRepository deviceRepository;
    
    @Autowired
    private VendorApiService vendorApiService;
    
    @Autowired
    private DataSyncService dataSyncService;
    
    @Value("${vendor.username:XHD_HSWL_API}")
    private String vendorUsername;
    
    @Value("${vendor.password:123456}")
    private String vendorPassword;

    @GetMapping
    public List<Device> getDevices(@RequestHeader("Authorization") String token) {
        int userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        return deviceService.getDevicesByUserId(userId);
    }

    @PostMapping("/bind")
    public Map<String, Object> bindDevice(@RequestHeader("Authorization") String token, @RequestBody Map<String, String> params) {
        String deviceNum = params.get("deviceNum");
        String sn = params.get("sn");
        String nickName = params.get("nickName");
        
        System.out.println("=== bindDevice called ===");
        System.out.println("deviceNum: " + deviceNum + ", nickName: " + nickName);
        
        Map<String, Object> result = new HashMap<>();
        
        Device device = deviceService.getDeviceByDeviceNum(deviceNum);
        
        if (device == null) {
            System.out.println("Device not found in server database: " + deviceNum);
            result.put("success", false);
            result.put("message", "请确认该设备是否激活");
            return result;
        }
        
        System.out.println("Device found in server database: " + deviceNum);
        System.out.println("  latitude: " + device.getLatitude());
        System.out.println("  longitude: " + device.getLongitude());
        System.out.println("  battery: " + device.getBattery());
        
        if (nickName != null) {
            device.setNickName(nickName);
        }
        if (sn != null) {
            device.setSn(sn);
        }
        deviceRepository.save(device);
        
        result.put("success", true);
        result.put("device", device);
        result.put("message", "设备添加成功");
        
        return result;
    }

    @PostMapping("/unbind")
    public Map<String, Object> unbindDevice(@RequestHeader("Authorization") String token, @RequestBody Map<String, String> params) {
        int userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        String deviceNum = params.get("deviceNum");
        
        System.out.println("=== unbindDevice called ===");
        System.out.println("userId: " + userId + ", deviceNum: " + deviceNum);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            deviceService.unbindDevice(userId, deviceNum);
            result.put("success", true);
            result.put("message", "设备解绑成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "解绑失败: " + e.getMessage());
        }
        
        return result;
    }
    
    @GetMapping("/{deviceNum}/history")
    public List<DeviceHistory> getDeviceHistory(
            @RequestHeader("Authorization") String token,
            @PathVariable String deviceNum) {
        int userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        return deviceHistoryRepository.findByUserIdAndDeviceNumOrderByTimestampDesc(userId, deviceNum);
    }
    
    @GetMapping("/{deviceNum}/latest")
    public Map<String, Object> getDeviceLatest(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String deviceNum) {
        Map<String, Object> result = new HashMap<>();
        
        Device device = deviceService.getDeviceByDeviceNum(deviceNum);
        
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
