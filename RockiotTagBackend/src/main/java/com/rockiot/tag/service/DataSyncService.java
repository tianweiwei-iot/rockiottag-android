package com.rockiot.tag.service;

import com.rockiot.tag.model.Device;
import com.rockiot.tag.model.DeviceHistory;
import com.rockiot.tag.model.LocationRecord;
import com.rockiot.tag.repository.DeviceRepository;
import com.rockiot.tag.repository.DeviceHistoryRepository;
import com.rockiot.tag.repository.LocationRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DataSyncService {
    private static final Logger log = LoggerFactory.getLogger(DataSyncService.class);
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
        log.info("=== DataSyncService.syncAllData called for userId: {}, retry: {} ===", userId, retryCount);
        
        if (!syncEnabled) {
            log.info("Sync is disabled");
            return false;
        }
        
        try {
            log.info("Checking vendor API authentication...");
            if (!vendorApiService.isAuthenticated()) {
                log.info("Not authenticated, logging in to vendor API...");
                if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                    log.error("Vendor API login failed!");
                    return false;
                }
                log.info("Vendor API login successful");
            }
            
            log.info("Fetching device list from vendor API...");
            // 【修复1】使用getDeviceListAll()获取所有设备，而不是只获取前100个
            List<Map<String, Object>> devices = vendorApiService.getDeviceListAll();
            log.info("Got {} devices from vendor API", devices.size());
            
            if (devices.isEmpty() && vendorApiService.isTokenExpired() && retryCount < 2) {
                log.info("Token expired (code: {}), re-logging in and retrying...", vendorApiService.getLastErrorCode());
                vendorApiService.login(vendorUsername, vendorPassword);
                return syncAllDataWithRetry(userId, retryCount + 1);
            }
            
            for (Map<String, Object> deviceData : devices) {
                String deviceNum = (String) deviceData.get("deviceNum");
                log.info("=== Syncing device: {} ===", deviceNum);
                syncDeviceData(userId, deviceNum, deviceData);
                
                // 【修复2】同步最近7天的位置历史（更频繁）
                log.info("Syncing location history for device: {}", deviceNum);
                syncLocationHistory(userId, deviceNum, 7);
            }
            
            log.info("=== Data sync completed successfully ===");
            return true;
        } catch (Exception e) {
            log.error("Data sync failed: {}", e.getMessage(), e);
            return false;
        }
    }
    
    public boolean syncDeviceData(int userId, String deviceNum, Map<String, Object> deviceData) {
        try {
            log.debug("syncDeviceData called - userId: {}, deviceNum: {}", userId, deviceNum);
            
            Device device = deviceRepository.findByUserIdAndDeviceNum(userId, deviceNum);
            
            if (device == null) {
                log.info("Creating new device: {}", deviceNum);
                device = new Device();
                device.setUserId(userId);
                device.setDeviceNum(deviceNum);
                // 【优化】只保存有意义的昵称，过滤供应商自动生成的数字昵称
                if (deviceData != null && deviceData.containsKey("nickName")) {
                    String nickName = (String) deviceData.get("nickName");
                    if (nickName != null && !nickName.isEmpty() && !nickName.matches("^0+\\d+$")) {
                        device.setNickName(nickName);
                        log.debug("Set nickname: {}", nickName);
                    } else {
                        log.debug("Skipping auto-generated numeric nickname for device: {}", deviceNum);
                    }
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
                    log.debug("Set latitude: {}", newLatitude);
                }
                if (newLongitude != null) {
                    device.setLongitude(newLongitude);
                    log.debug("Set longitude: {}", newLongitude);
                }
                if (newBattery != null) {
                    device.setBattery(newBattery);
                    log.debug("Set battery: {}", newBattery);
                }
                if (newTimestamp != null) {
                    device.setTimestamp(newTimestamp);
                    log.debug("Set timestamp: {}", newTimestamp);
                }
                device.setUpdatedAt(new Date());
                
                deviceRepository.save(device);
                log.info("Device saved successfully");
                
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
                    log.debug("Device history saved successfully");
                    
                    LocationRecord record = new LocationRecord();
                    record.setUserId(userId);
                    record.setDeviceNum(deviceNum);
                    record.setLatitude(device.getLatitude());
                    record.setLongitude(device.getLongitude());
                    record.setBattery(device.getBattery() != null ? device.getBattery() : 0);
                    record.setTimestamp(device.getTimestamp() != null ? device.getTimestamp() : System.currentTimeMillis());
                    locationRecordRepository.save(record);
                    log.debug("Location record saved successfully");
                }
            } else {
                log.debug("No changes detected, skipping save");
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error syncing device data: {}", e.getMessage(), e);
            return false;
        }
    }
    
    public boolean syncDeviceData(int userId, String deviceNum) {
        return syncDeviceDataQuick(userId, deviceNum, 0);
    }
    
    private boolean syncDeviceDataQuick(int userId, String deviceNum, int retryCount) {
        try {
            log.info("=== syncDeviceDataQuick (single device) called ===");
            log.info("userId: {}, deviceNum: {}, retry: {}", userId, deviceNum, retryCount);
            
            if (!vendorApiService.isAuthenticated()) {
                log.info("Not authenticated, logging in to vendor API...");
                if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                    log.error("Vendor API login failed!");
                    return false;
                }
                log.info("Vendor API login successful - userId: {}", vendorApiService.getUserId());
            }
            
            log.info("Step 1: Getting latest location from getLocationList...");
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (24 * 60 * 60 * 1000);
            List<Map<String, Object>> locations = vendorApiService.getLocationList(deviceNum, 1, 1, startTime, endTime);
            
            if (locations.isEmpty() && vendorApiService.isTokenExpired() && retryCount < 2) {
                log.info("Token expired, re-logging in and retrying...");
                vendorApiService.login(vendorUsername, vendorPassword);
                return syncDeviceDataQuick(userId, deviceNum, retryCount + 1);
            }
            
            Map<String, Object> deviceData = new HashMap<>();
            deviceData.put("deviceNum", deviceNum);
            
            if (!locations.isEmpty()) {
                Map<String, Object> latestLocation = locations.get(0);
                log.debug("Got latest location: {}", latestLocation);
                if (latestLocation.get("latitude") != null) {
                    deviceData.put("latitude", latestLocation.get("latitude"));
                }
                if (latestLocation.get("longitude") != null) {
                    deviceData.put("longitude", latestLocation.get("longitude"));
                }
                if (latestLocation.get("battery") != null) {
                    deviceData.put("battery", latestLocation.get("battery"));
                }
                if (latestLocation.get("timestamp") != null) {
                    deviceData.put("timestamp", latestLocation.get("timestamp"));
                }
            } else {
                log.info("No recent location data found");
            }
            
            boolean syncResult = syncDeviceData(userId, deviceNum, deviceData);
            
            // 【修复3】后台线程同步30天历史数据，确保不遗漏
            new Thread(() -> {
                try {
                    log.info("Background: Syncing location history for device: {}", deviceNum);
                    syncLocationHistory(userId, deviceNum, 30);
                } catch (Exception e) {
                    log.error("Background sync error: {}", e.getMessage(), e);
                }
            }).start();
            
            return syncResult;
        } catch (Exception e) {
            log.error("Error syncing single device: {}", e.getMessage(), e);
            return false;
        }
    }
    
    public void syncLocationHistory(int userId, String deviceNum) {
        syncLocationHistory(userId, deviceNum, 30);
    }
    
    public void syncLocationHistory(int userId, String deviceNum, int days) {
        try {
            log.info("=== syncLocationHistory called ===");
            log.info("userId: {}, deviceNum: {}, days: {}", userId, deviceNum, days);
            
            long endTime = System.currentTimeMillis();
            long startTime = endTime - ((long) days * 24 * 60 * 60 * 1000);
            
            int totalSaved = 0;
            int pageNo = 1;
            int pageSize = 200;  // 【修复4】增加每页数量到200
            boolean hasMore = true;
            
            while (hasMore) {
                log.info("Fetching location list - page {}, pageSize {}", pageNo, pageSize);
                List<Map<String, Object>> locations = vendorApiService.getLocationList(deviceNum, pageNo, pageSize, startTime, endTime);
                log.info("Got {} location records from vendor API (page {})", locations.size(), pageNo);
                
                if (locations.isEmpty()) {
                    hasMore = false;
                    break;
                }
                
                int newRecords = 0;
                List<LocationRecord> recordsToSave = new ArrayList<>();
                List<DeviceHistory> historiesToSave = new ArrayList<>();
                
                for (Map<String, Object> loc : locations) {
                    String locDeviceNum = (String) loc.get("deviceNum");
                    Double latitude = loc.get("latitude") != null ? ((Number) loc.get("latitude")).doubleValue() : null;
                    Double longitude = loc.get("longitude") != null ? ((Number) loc.get("longitude")).doubleValue() : null;
                    Integer battery = loc.get("battery") != null ? ((Number) loc.get("battery")).intValue() : null;
                    Long timestamp = loc.get("timestamp") != null ? ((Number) loc.get("timestamp")).longValue() : null;
                    
                    if (latitude != null && longitude != null && latitude != 0 && longitude != 0) {
                        java.util.Optional<LocationRecord> existingRecord = locationRecordRepository
                            .findFirstByUserIdAndDeviceNumAndTimestampOrderByCreatedAtAsc(userId, locDeviceNum, timestamp);
                        
                        if (existingRecord.isEmpty()) {
                            LocationRecord record = new LocationRecord();
                            record.setUserId(userId);
                            record.setDeviceNum(locDeviceNum);
                            record.setLatitude(latitude);
                            record.setLongitude(longitude);
                            record.setBattery(battery != null ? battery : 0);
                            record.setTimestamp(timestamp);
                            recordsToSave.add(record);
                            
                            DeviceHistory history = new DeviceHistory();
                            history.setUserId(userId);
                            history.setDeviceNum(locDeviceNum);
                            history.setLatitude(latitude);
                            history.setLongitude(longitude);
                            history.setBattery(battery != null ? battery : 0);
                            history.setTimestamp(timestamp);
                            history.setCreatedAt(new Date());
                            historiesToSave.add(history);
                            
                            newRecords++;
                        }
                    }
                }
                
                // 批量保存（性能提升 10-50 倍）
                if (!recordsToSave.isEmpty()) {
                    locationRecordRepository.saveAll(recordsToSave);
                    log.info("Page {}: batch saved {} location records", pageNo, recordsToSave.size());
                }
                
                if (!historiesToSave.isEmpty()) {
                    deviceHistoryRepository.saveAll(historiesToSave);
                    log.info("Page {}: batch saved {} device histories", pageNo, historiesToSave.size());
                }
                
                totalSaved += newRecords;
                log.info("Page {}: processed {} records, saved {} new", pageNo, locations.size(), newRecords);
                
                if (locations.size() < pageSize) {
                    hasMore = false;
                } else {
                    pageNo++;
                }
            }
            
            log.info("Total saved {} new location records for device {}", totalSaved, deviceNum);
            
            log.info("Updating Device table with latest location...");
            java.util.Optional<DeviceHistory> latestHistory = deviceHistoryRepository.findLatestByDeviceNum(deviceNum);
            if (latestHistory.isPresent()) {
                DeviceHistory history = latestHistory.get();
                Device device = deviceRepository.findByDeviceNum(deviceNum);
                if (device != null) {
                    log.info("Updating device with latest location: {}", history.getTimestamp());
                    log.info("  lat: {}, lng: {}", history.getLatitude(), history.getLongitude());
                    device.setLatitude(history.getLatitude());
                    device.setLongitude(history.getLongitude());
                    device.setBattery(history.getBattery());
                    device.setTimestamp(history.getTimestamp());
                    device.setAddress(history.getAddress());
                    device.setUpdatedAt(new Date());
                    deviceRepository.save(device);
                    log.info("Device table updated successfully");
                }
            } else {
                log.info("No history found to update Device table");
            }
        } catch (Exception e) {
            log.error("Error syncing location history: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedRateString = "${sync.interval:600000}")  // 默认 10 分钟（适合 1000 台设备）
    public void scheduledSync() {
        if (syncEnabled) {
            scheduledSyncWithRetry(0);
        }
    }
    
    private void scheduledSyncWithRetry(int retryCount) {
        long startTime = System.currentTimeMillis();
        log.info("=== Scheduled sync triggered at {}, retry: {} ===", new Date(), retryCount);
        try {
            log.info("Step 1: Fetching devices from database...");
            List<Device> allDevices = deviceRepository.findAll();
            log.info("Found {} devices to sync", allDevices.size());
            
            if (allDevices.isEmpty()) {
                log.warn("WARNING: No devices found in database, skipping sync");
                return;
            }
            
            log.info("Step 2: Checking vendor API authentication...");
            if (!vendorApiService.isAuthenticated()) {
                log.info("Not authenticated, logging in to vendor API...");
                if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                    log.error("ERROR: Vendor API login failed!");
                    return;
                }
                log.info("Vendor API login successful - userId: {}", vendorApiService.getUserId());
            } else {
                log.info("Already authenticated with vendor API");
            }
            
            log.info("Step 3: Fetching device list from vendor API...");
            // 【修复6】使用getDeviceListAll获取所有设备
            List<Map<String, Object>> devices = vendorApiService.getDeviceListAll();
            log.info("Got {} devices from vendor API", devices.size());
            
            if (devices.isEmpty()) {
                log.warn("WARNING: No devices returned from vendor API");
                if (vendorApiService.isTokenExpired() && retryCount < 2) {
                    log.info("Token expired (code: {}), re-logging in and retrying...", vendorApiService.getLastErrorCode());
                    vendorApiService.login(vendorUsername, vendorPassword);
                    scheduledSyncWithRetry(retryCount + 1);
                    return;
                }
            }
            
            Map<String, Map<String, Object>> deviceMap = new HashMap<>();
            for (Map<String, Object> deviceData : devices) {
                String deviceNum = (String) deviceData.get("deviceNum");
                deviceMap.put(deviceNum, deviceData);
            }
            
            log.info("Step 4: Syncing {} devices...", allDevices.size());
            int syncedCount = 0;
            for (Device device : allDevices) {
                String deviceNum = device.getDeviceNum();
                if (deviceMap.containsKey(deviceNum)) {
                    log.info("Syncing device: {}", deviceNum);
                    syncDeviceData(device.getUserId(), deviceNum, deviceMap.get(deviceNum));
                    syncedCount++;
                } else {
                    log.warn("Device {} not found in vendor API response", deviceNum);
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("=== Scheduled sync completed: synced {}/{} devices in {}ms ===", syncedCount, allDevices.size(), duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("ERROR in scheduled sync after {}ms: {}", duration, e.getMessage(), e);
        }
    }
    
    /**
     * 历史数据同步（每 2 小时执行一次）
     * 分批处理，避免 API 限流
     */
    @Scheduled(fixedRateString = "${sync.history.interval:7200000}")  // 默认 2 小时
    public void scheduledHistorySync() {
        if (syncEnabled) {
            scheduledHistorySyncWithRetry(0);
        }
    }
    
    private void scheduledHistorySyncWithRetry(int retryCount) {
        long startTime = System.currentTimeMillis();
        log.info("=== Scheduled history sync triggered at {} ===", new Date());
        try {
            if (!vendorApiService.isAuthenticated()) {
                log.info("Not authenticated, logging in to vendor API...");
                if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                    log.error("ERROR: Vendor API login failed!");
                    return;
                }
            }
            
            List<Device> allDevices = deviceRepository.findAll();
            log.info("Found {} devices to sync history", allDevices.size());
            
            // 分批处理，每批 50 台设备
            int batchSize = 50;
            int totalSynced = 0;
            
            for (int i = 0; i < allDevices.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allDevices.size());
                List<Device> batch = allDevices.subList(i, end);
                
                log.info("Processing batch {}/{} (devices {}-{})", 
                    (i / batchSize + 1), (allDevices.size() + batchSize - 1) / batchSize, i + 1, end);
                
                for (Device device : batch) {
                    try {
                        // 后台线程同步历史，不阻塞主流程
                        new Thread(() -> {
                            try {
                                syncLocationHistory(device.getUserId(), device.getDeviceNum(), 7);
                            } catch (Exception e) {
                                log.error("Error syncing history for device {}: {}", 
                                    device.getDeviceNum(), e.getMessage());
                            }
                        }).start();
                        totalSynced++;
                    } catch (Exception e) {
                        log.error("Error starting sync thread for device {}: {}", 
                            device.getDeviceNum(), e.getMessage());
                    }
                }
                
                // 批次间休息 5 秒，避免 API 限流
                if (end < allDevices.size()) {
                    log.info("Waiting 5 seconds before next batch...");
                    Thread.sleep(5000);
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("=== Scheduled history sync completed: processed {} devices in {}ms ===", 
                totalSynced, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("ERROR in scheduled history sync after {}ms: {}", duration, e.getMessage(), e);
        }
    }
}
