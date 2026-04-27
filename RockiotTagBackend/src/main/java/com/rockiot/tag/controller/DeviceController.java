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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);
    
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
        System.out.println("=== getDeviceLatest called ===");
        System.out.println("deviceNum: " + deviceNum);
        
        Map<String, Object> result = new HashMap<>();
        
        Device device = deviceService.getDeviceByDeviceNum(deviceNum);
        
        if (device != null) {
            result.put("deviceNum", device.getDeviceNum());
            result.put("nickName", device.getNickName());
            result.put("updatedAt", device.getUpdatedAt());
            
            System.out.println("Getting latest location from DeviceHistory...");
            java.util.Optional<DeviceHistory> latestHistory = deviceHistoryRepository.findLatestByDeviceNum(deviceNum);
            
            if (latestHistory.isPresent()) {
                DeviceHistory history = latestHistory.get();
                System.out.println("Found latest history: " + history.getTimestamp());
                System.out.println("  lat: " + history.getLatitude() + ", lng: " + history.getLongitude());
                result.put("latitude", history.getLatitude());
                result.put("longitude", history.getLongitude());
                result.put("battery", history.getBattery());
                result.put("timestamp", history.getTimestamp());
                result.put("address", history.getAddress());
            } else {
                System.out.println("No history found, using Device table data");
                result.put("latitude", device.getLatitude());
                result.put("longitude", device.getLongitude());
                result.put("battery", device.getBattery());
                result.put("timestamp", device.getTimestamp());
                result.put("address", device.getAddress());
            }
        }
        
        System.out.println("Returning result: " + result);
        return result;
    }
    
    /**
     * 更新设备名称（Android端调用）
     */
    @PostMapping("/update")
    public Map<String, Object> updateDevice(@RequestBody Map<String, String> params) {
        String deviceNum = params.get("deviceNum");
        String nickName = params.get("nickName");
        
        System.out.println("=== updateDevice called ===");
        System.out.println("deviceNum: " + deviceNum);
        System.out.println("nickName: " + nickName);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            Device device = deviceService.getDeviceByDeviceNum(deviceNum);
            
            if (device == null) {
                System.out.println("Device not found: " + deviceNum);
                result.put("success", false);
                result.put("message", "设备不存在");
                return result;
            }
            
            // 更新设备名称（只保存有意义的昵称）
            if (nickName != null && !nickName.isEmpty() && !nickName.matches("^0+\\d+$")) {
                device.setNickName(nickName);
                log.info("Updated nickname for device {}: {}", deviceNum, nickName);
            } else if (nickName != null && nickName.isEmpty()) {
                // 如果用户明确设置为空，则清空昵称
                device.setNickName(null);
                log.info("Cleared nickname for device {}", deviceNum);
            } else {
                log.debug("Skipping auto-generated numeric nickname: {}", nickName);
            }
            deviceRepository.save(device);
            
            System.out.println("✅ Device name updated successfully: " + deviceNum + " -> " + nickName);
            
            result.put("success", true);
            result.put("message", "设备名称已更新");
            result.put("device", device);
            
        } catch (Exception e) {
            System.out.println("❌ Error updating device: " + e.getMessage());
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 公开API：获取所有设备列表（无需认证）
     */
    @GetMapping("/public/all")
    public List<Device> getAllDevicesPublic() {
        System.out.println("=== getAllDevicesPublic called ===");
        return deviceRepository.findAll();
    }
    
    /**
     * 公开API：获取设备最新位置（无需认证）
     */
    @GetMapping("/public/{deviceNum}/latest")
    public Map<String, Object> getDeviceLatestPublic(@PathVariable String deviceNum) {
        System.out.println("=== getDeviceLatestPublic called ===");
        System.out.println("deviceNum: " + deviceNum);
        
        Map<String, Object> result = new HashMap<>();
        
        Device device = deviceService.getDeviceByDeviceNum(deviceNum);
        
        if (device == null) {
            result.put("success", false);
            result.put("message", "设备不存在");
            return result;
        }
        
        result.put("success", true);
        result.put("deviceNum", device.getDeviceNum());
        result.put("nickName", device.getNickName());
        result.put("mac", device.getMac());
        result.put("updatedAt", device.getUpdatedAt());
        
        // 从历史记录获取最新位置
        java.util.Optional<DeviceHistory> latestHistory = deviceHistoryRepository.findLatestByDeviceNum(deviceNum);
        
        if (latestHistory.isPresent()) {
            DeviceHistory history = latestHistory.get();
            result.put("latitude", history.getLatitude());
            result.put("longitude", history.getLongitude());
            result.put("battery", history.getBattery());
            result.put("timestamp", history.getTimestamp());
            result.put("address", history.getAddress());
        } else {
            result.put("latitude", device.getLatitude());
            result.put("longitude", device.getLongitude());
            result.put("battery", device.getBattery());
            result.put("timestamp", device.getTimestamp());
            result.put("address", device.getAddress());
        }
        
        return result;
    }
    
    /**
     * 公开API：获取设备某天历史记录（无需认证）
     */
    @GetMapping("/public/{deviceNum}/history/date")
    public List<DeviceHistory> getDeviceHistoryByDatePublic(
            @PathVariable String deviceNum,
            @RequestParam String date) {
        System.out.println("=== getDeviceHistoryByDatePublic called ===");
        System.out.println("deviceNum: " + deviceNum + ", date: " + date);
        
        try {
            // 解析日期格式：yyyy-MM-dd
            java.time.LocalDate localDate = java.time.LocalDate.parse(date);
            long startTime = localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endTime = startTime + (24 * 60 * 60 * 1000);
            
            return deviceHistoryRepository.findByDeviceNumAndTimeRange(deviceNum, startTime, endTime);
        } catch (Exception e) {
            System.out.println("Error parsing date: " + e.getMessage());
            return new java.util.ArrayList<>();
        }
    }
    
    /**
     * 公开API：获取设备所有历史记录（无需认证）
     */
    @GetMapping("/public/{deviceNum}/history/all")
    public List<DeviceHistory> getAllDeviceHistoryPublic(@PathVariable String deviceNum) {
        System.out.println("=== getAllDeviceHistoryPublic called ===");
        System.out.println("deviceNum: " + deviceNum);
        
        return deviceHistoryRepository.findByDeviceNumOrderByTimestampDesc(deviceNum);
    }
}
