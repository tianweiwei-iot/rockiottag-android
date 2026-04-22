package com.rockiot.tag.controller;

import com.rockiot.tag.model.LocationRecord;
import com.rockiot.tag.model.DeviceHistory;
import com.rockiot.tag.service.LocationService;
import com.rockiot.tag.repository.DeviceHistoryRepository;
import com.rockiot.tag.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/locations")
public class LocationController {
    @Autowired
    private LocationService locationService;
    
    @Autowired
    private DeviceHistoryRepository deviceHistoryRepository;

    @PostMapping("/sync")
    public void syncLocation(@RequestHeader("Authorization") String token, @RequestBody Map<String, Object> params) {
        int userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        String deviceNum = (String) params.get("deviceNum");
        double latitude = Double.parseDouble(params.get("latitude").toString());
        double longitude = Double.parseDouble(params.get("longitude").toString());
        int battery = Integer.parseInt(params.get("battery").toString());
        long timestamp = Long.parseLong(params.get("timestamp").toString());
        locationService.saveLocation(userId, deviceNum, latitude, longitude, battery, timestamp);
    }

    @GetMapping
    public List<?> getLocations(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam("deviceNum") String deviceNum,
            @RequestParam(value = "startTime", defaultValue = "0") long startTime,
            @RequestParam(value = "endTime", defaultValue = "0") long endTime) {
        
        System.out.println("=== getLocations called ===");
        System.out.println("deviceNum: " + deviceNum + ", startTime: " + startTime + ", endTime: " + endTime);
        
        int userId = 0;
        if (token != null && !token.isEmpty()) {
            try {
                userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            } catch (Exception e) {
                System.out.println("Failed to parse token, using userId 0: " + e.getMessage());
            }
        }
        
        List<LocationRecord> locationRecords = locationService.getLocations(userId, deviceNum, startTime, endTime);
        System.out.println("Found " + locationRecords.size() + " records in location_records table");
        
        if (!locationRecords.isEmpty()) {
            return locationRecords;
        }
        
        System.out.println("No data in location_records, checking device_history table...");
        List<DeviceHistory> historyRecords;
        if (startTime > 0 && endTime > 0) {
            historyRecords = deviceHistoryRepository.findByDeviceNumAndTimeRange(deviceNum, startTime, endTime);
        } else {
            historyRecords = deviceHistoryRepository.findByDeviceNumOrderByTimestampDesc(deviceNum);
        }
        
        System.out.println("Found " + historyRecords.size() + " records in device_history table");
        
        return historyRecords;
    }
}
