package com.rockiot.tag.service;

import com.rockiot.tag.model.Device;
import com.rockiot.tag.model.DeviceHistory;
import com.rockiot.tag.model.LocationRecord;
import com.rockiot.tag.repository.DeviceRepository;
import com.rockiot.tag.repository.DeviceHistoryRepository;
import com.rockiot.tag.repository.LocationRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DataSyncService {
    @Autowired
    private VendorApiService vendorApiService;
    
    @Autowired
    private DeviceRepository deviceRepository;
    
    @Autowired
    private DeviceHistoryRepository deviceHistoryRepository;
    
    @Autowired
    private LocationRecordRepository locationRecordRepository;
    
    @Value("${sync.enabled:true}")
    private boolean syncEnabled;
    
    @Value("${vendor.username:XHD_HSWL_API}")
    private String vendorUsername;
    
    @Value("${vendor.password:123456}")
    private String vendorPassword;
    
    public boolean syncAllData(int userId) {
        return syncAllDataWithRetry(userId, 0);
    }
    
    private boolean syncAllDataWithRetry(int userId, int retryCount) {
        System.out.println("=== DataSyncService.syncAllData called for userId: " + userId + ", retry: " + retryCount + " ===");
        
        if (!syncEnabled) {
            System.out.println("Sync is disabled");
            return false;
        }
        
        try {
            System.out.println("Checking vendor API authentication...");
            if (!vendorApiService.isAuthenticated()) {
                System.out.println("Not authenticated, logging in to vendor API...");
                if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                    System.err.println("Vendor API login failed!");
                    return false;
                }
                System.out.println("Vendor API login successful");
            }
            
            System.out.println("Fetching device list from vendor API...");
            List<Map<String, Object>> devices = vendorApiService.getDeviceList(1, 100);
            System.out.println("Got " + devices.size() + " devices from vendor API");
            
            if (devices.isEmpty() && vendorApiService.isTokenExpired() && retryCount < 2) {
                System.out.println("Token expired (code: " + vendorApiService.getLastErrorCode() + "), re-logging in and retrying...");
                vendorApiService.login(vendorUsername, vendorPassword);
                return syncAllDataWithRetry(userId, retryCount + 1);
            }
            
            for (Map<String, Object> deviceData : devices) {
                String deviceNum = (String) deviceData.get("deviceNum");
                System.out.println("Syncing device: " + deviceNum);
                syncDeviceData(userId, deviceNum, deviceData);
            }
            
            System.out.println("=== Data sync completed successfully ===");
            return true;
        } catch (Exception e) {
            System.err.println("Data sync failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean syncDeviceData(int userId, String deviceNum, Map<String, Object> deviceData) {
        try {
            System.out.println("syncDeviceData called - userId: " + userId + ", deviceNum: " + deviceNum);
            
            Device device = deviceRepository.findByUserIdAndDeviceNum(userId, deviceNum);
            
            if (device == null) {
                System.out.println("Creating new device: " + deviceNum);
                device = new Device();
                device.setUserId(userId);
                device.setDeviceNum(deviceNum);
                if (deviceData != null && deviceData.containsKey("nickName")) {
                    device.setNickName((String) deviceData.get("nickName"));
                }
                if (deviceData != null && deviceData.containsKey("mac")) {
                    device.setMac((String) deviceData.get("mac"));
                }
            }
            
            boolean hasChanges = false;
            Double newLatitude = null;
            Double newLongitude = null;
            Integer newBattery = null;
            Long newTimestamp = null;
            
            if (deviceData != null) {
                if (deviceData.containsKey("latitude")) {
                    newLatitude = ((Number) deviceData.get("latitude")).doubleValue();
                    if (device.getLatitude() == null || !device.getLatitude().equals(newLatitude)) {
                        hasChanges = true;
                    }
                }
                if (deviceData.containsKey("longitude")) {
                    newLongitude = ((Number) deviceData.get("longitude")).doubleValue();
                    if (device.getLongitude() == null || !device.getLongitude().equals(newLongitude)) {
                        hasChanges = true;
                    }
                }
                if (deviceData.containsKey("battery")) {
                    newBattery = ((Number) deviceData.get("battery")).intValue();
                    if (device.getBattery() == null || !device.getBattery().equals(newBattery)) {
                        hasChanges = true;
                    }
                }
                if (deviceData.containsKey("timestamp")) {
                    newTimestamp = ((Number) deviceData.get("timestamp")).longValue();
                    if (device.getTimestamp() == null || !device.getTimestamp().equals(newTimestamp)) {
                        hasChanges = true;
                    }
                }
            }
            
            if (hasChanges || device.getLatitude() == null) {
                if (newLatitude != null) {
                    device.setLatitude(newLatitude);
                    System.out.println("Set latitude: " + newLatitude);
                }
                if (newLongitude != null) {
                    device.setLongitude(newLongitude);
                    System.out.println("Set longitude: " + newLongitude);
                }
                if (newBattery != null) {
                    device.setBattery(newBattery);
                    System.out.println("Set battery: " + newBattery);
                }
                if (newTimestamp != null) {
                    device.setTimestamp(newTimestamp);
                    System.out.println("Set timestamp: " + newTimestamp);
                }
                device.setUpdatedAt(new Date());
                
                deviceRepository.save(device);
                System.out.println("Device saved successfully");
                
                if (device.getLatitude() != null && device.getLongitude() != null) {
                    DeviceHistory history = new DeviceHistory();
                    history.setUserId(userId);
                    history.setDeviceNum(deviceNum);
                    history.setLatitude(device.getLatitude());
                    history.setLongitude(device.getLongitude());
                    history.setBattery(device.getBattery() != null ? device.getBattery() : 0);
                    history.setTimestamp(device.getTimestamp() != null ? device.getTimestamp() : System.currentTimeMillis());
                    history.setCreatedAt(new Date());
                    deviceHistoryRepository.save(history);
                    System.out.println("Device history saved successfully");
                    
                    LocationRecord record = new LocationRecord();
                    record.setUserId(userId);
                    record.setDeviceNum(deviceNum);
                    record.setLatitude(device.getLatitude());
                    record.setLongitude(device.getLongitude());
                    record.setBattery(device.getBattery() != null ? device.getBattery() : 0);
                    record.setTimestamp(device.getTimestamp() != null ? device.getTimestamp() : System.currentTimeMillis());
                    locationRecordRepository.save(record);
                    System.out.println("Location record saved successfully");
                }
            } else {
                System.out.println("No changes detected, skipping save");
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("Error syncing device data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean syncDeviceData(int userId, String deviceNum) {
        return syncDeviceDataWithRetry(userId, deviceNum, 0);
    }
    
    private boolean syncDeviceDataWithRetry(int userId, String deviceNum, int retryCount) {
        try {
            System.out.println("=== syncDeviceData (single device) called ===");
            System.out.println("userId: " + userId + ", deviceNum: " + deviceNum + ", retry: " + retryCount);
            
            if (!vendorApiService.isAuthenticated()) {
                System.out.println("Not authenticated, logging in to vendor API...");
                if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                    System.err.println("Vendor API login failed!");
                    return false;
                }
                System.out.println("Vendor API login successful");
            }
            
            System.out.println("Fetching device list from vendor API...");
            List<Map<String, Object>> devices = vendorApiService.getDeviceList(1, 100);
            System.out.println("Got " + devices.size() + " devices from vendor API");
            
            if (devices.isEmpty() && vendorApiService.isTokenExpired() && retryCount < 2) {
                System.out.println("Token expired (code: " + vendorApiService.getLastErrorCode() + "), re-logging in and retrying...");
                vendorApiService.login(vendorUsername, vendorPassword);
                return syncDeviceDataWithRetry(userId, deviceNum, retryCount + 1);
            }
            
            boolean found = false;
            for (Map<String, Object> deviceData : devices) {
                String num = (String) deviceData.get("deviceNum");
                System.out.println("Checking device: " + num + " (looking for " + deviceNum + ")");
                if (deviceNum.equals(num)) {
                    found = true;
                    System.out.println("Found matching device! Syncing data...");
                    System.out.println("Device data: " + deviceData);
                    
                    boolean syncResult = syncDeviceData(userId, deviceNum, deviceData);
                    
                    System.out.println("Syncing location history for device: " + deviceNum);
                    syncLocationHistory(userId, deviceNum);
                    
                    return syncResult;
                }
            }
            
            if (!found) {
                System.err.println("Device not found in vendor API: " + deviceNum);
                System.out.println("Available devices in vendor API:");
                for (Map<String, Object> deviceData : devices) {
                    System.out.println("  - " + deviceData.get("deviceNum"));
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error syncing single device: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public void syncLocationHistory(int userId, String deviceNum) {
        try {
            System.out.println("=== syncLocationHistory called ===");
            System.out.println("userId: " + userId + ", deviceNum: " + deviceNum);
            
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (7 * 24 * 60 * 60 * 1000);
            
            List<Map<String, Object>> locations = vendorApiService.getLocationList(deviceNum, 1, 1000, startTime, endTime);
            System.out.println("Got " + locations.size() + " location records from vendor API");
            
            int newRecords = 0;
            for (Map<String, Object> loc : locations) {
                String locDeviceNum = (String) loc.get("deviceNum");
                Double latitude = (Double) loc.get("latitude");
                Double longitude = (Double) loc.get("longitude");
                Integer battery = (Integer) loc.get("battery");
                Long timestamp = (Long) loc.get("timestamp");
                
                if (latitude != null && longitude != null && latitude != 0 && longitude != 0) {
                    LocationRecord existingRecord = locationRecordRepository
                        .findByUserIdAndDeviceNumAndTimestamp(userId, locDeviceNum, timestamp);
                    
                    if (existingRecord == null) {
                        LocationRecord record = new LocationRecord();
                        record.setUserId(userId);
                        record.setDeviceNum(locDeviceNum);
                        record.setLatitude(latitude);
                        record.setLongitude(longitude);
                        record.setBattery(battery != null ? battery : 0);
                        record.setTimestamp(timestamp);
                        locationRecordRepository.save(record);
                        newRecords++;
                    }
                }
            }
            
            System.out.println("Saved " + newRecords + " new location records");
        } catch (Exception e) {
            System.err.println("Error syncing location history: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Scheduled(fixedRateString = "${sync.interval:300000}")
    public void scheduledSync() {
        if (syncEnabled) {
            scheduledSyncWithRetry(0);
        }
    }
    
    private void scheduledSyncWithRetry(int retryCount) {
        System.out.println("=== Scheduled sync triggered at " + new Date() + ", retry: " + retryCount + " ===");
        try {
            List<Device> allDevices = deviceRepository.findAll();
            System.out.println("Found " + allDevices.size() + " devices to sync");
            
            if (!vendorApiService.isAuthenticated()) {
                System.out.println("Not authenticated, logging in to vendor API...");
                if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                    System.err.println("Vendor API login failed!");
                    return;
                }
                System.out.println("Vendor API login successful");
            }
            
            List<Map<String, Object>> devices = vendorApiService.getDeviceList(1, 1000);
            System.out.println("Got " + devices.size() + " devices from vendor API");
            
            if (devices.isEmpty() && vendorApiService.isTokenExpired() && retryCount < 2) {
                System.out.println("Token expired (code: " + vendorApiService.getLastErrorCode() + "), re-logging in and retrying...");
                vendorApiService.login(vendorUsername, vendorPassword);
                scheduledSyncWithRetry(retryCount + 1);
                return;
            }
            
            Map<String, Map<String, Object>> deviceMap = new HashMap<>();
            for (Map<String, Object> deviceData : devices) {
                String deviceNum = (String) deviceData.get("deviceNum");
                deviceMap.put(deviceNum, deviceData);
            }
            
            for (Device device : allDevices) {
                String deviceNum = device.getDeviceNum();
                if (deviceMap.containsKey(deviceNum)) {
                    System.out.println("Syncing device: " + deviceNum);
                    syncDeviceData(device.getUserId(), deviceNum, deviceMap.get(deviceNum));
                }
            }
            
            System.out.println("=== Scheduled sync completed ===");
        } catch (Exception e) {
            System.err.println("Error in scheduled sync: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
