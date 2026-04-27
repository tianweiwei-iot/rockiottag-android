package com.rockiot.tag.controller;

import com.rockiot.tag.service.DataSyncService;
import com.rockiot.tag.service.VendorApiService;
import com.rockiot.tag.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sync")
public class DataSyncController {
    private static final Logger log = LoggerFactory.getLogger(DataSyncController.class);
    @Autowired
    private DataSyncService dataSyncService;
    
    @Autowired
    private VendorApiService vendorApiService;
    
    @Value("${vendor.username:XHD_HSWL_API}")
    private String vendorUsername;
    
    @Value("${vendor.password:123456}")
    private String vendorPassword;
    
    @PostMapping("/bindVendorDevice")
    public Map<String, Object> bindVendorDevice(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> params) {
        
        // 参数验证
        if (params == null || !params.containsKey("deviceNum")) {
            log.warn("bindVendorDevice called with missing deviceNum");
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "缺少设备号参数");
            return result;
        }
        
        int userId;
        try {
            userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        } catch (Exception e) {
            log.error("Invalid token: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "无效的认证令牌");
            return result;
        }
        
        String deviceNum = params.get("deviceNum");
        String nickName = params.get("nickName");
        
        log.info("=== bindVendorDevice called ===");
        log.info("userId: {}, deviceNum: {}, nickName: {}", userId, deviceNum, nickName);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("Step 1: Login to vendor API...");
            if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                log.error("Vendor API login failed");
                result.put("success", false);
                result.put("message", "供应商 API 登录失败");
                return result;
            }
            log.info("Vendor API login successful");
                    
            log.info("Step 2: Binding device to vendor API...");
            boolean bindSuccess = vendorApiService.bindDevice(deviceNum, nickName);
            log.info("Bind result: {}", bindSuccess);
                    
            if (!bindSuccess) {
                log.warn("Bind failed, trying to re-login and retry...");
                if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                    log.error("Vendor API re-login failed");
                    result.put("success", false);
                    result.put("message", "供应商 API 重新登录失败");
                    return result;
                }
                bindSuccess = vendorApiService.bindDevice(deviceNum, nickName);
                log.info("Retry bind result: {}", bindSuccess);
            }
                    
            log.info("Step 3: Refreshing device location...");
            vendorApiService.refreshLocation(deviceNum);
                    
            log.info("Step 4: Waiting for device to report location...");
            Thread.sleep(3000);
                    
            log.info("Step 5: Syncing device data...");
            boolean syncSuccess = dataSyncService.syncDeviceData(userId, deviceNum);
            log.info("Sync result: {}", syncSuccess);
                    
            result.put("success", bindSuccess && syncSuccess);
            result.put("bindSuccess", bindSuccess);
            result.put("syncSuccess", syncSuccess);
            result.put("message", (bindSuccess && syncSuccess) ? "设备绑定并同步成功" : 
                       (bindSuccess ? "设备绑定成功，但同步失败" : "设备绑定失败"));
        } catch (InterruptedException e) {
            log.error("Thread interrupted during binding: {}", e.getMessage());
            Thread.currentThread().interrupt();
            result.put("success", false);
            result.put("message", "绑定过程被中断");
        } catch (Exception e) {
            log.error("Error during device binding: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "绑定失败: " + e.getMessage());
        }
        
        return result;
    }
    
    @PostMapping("/all")
    public Map<String, Object> syncAllData(@RequestHeader("Authorization") String token) {
        int userId;
        try {
            userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        } catch (Exception e) {
            log.error("Invalid token: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "无效的认证令牌");
            return result;
        }
        
        try {
            log.info("Syncing all data for user: {}", userId);
            boolean success = dataSyncService.syncAllData(userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "同步成功" : "同步失败");
            return result;
        } catch (Exception e) {
            log.error("Error syncing all data for user {}: {}", userId, e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "同步失败: " + e.getMessage());
            return result;
        }
    }
    
    @PostMapping("/device/{deviceNum}")
    public Map<String, Object> syncDeviceData(
            @RequestHeader("Authorization") String token,
            @PathVariable String deviceNum) {
        
        if (deviceNum == null || deviceNum.isEmpty()) {
            log.warn("syncDeviceData called with empty deviceNum");
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "设备号不能为空");
            return result;
        }
        
        int userId;
        try {
            userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        } catch (Exception e) {
            log.error("Invalid token: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "无效的认证令牌");
            return result;
        }
        
        try {
            log.info("Syncing device data - userId: {}, deviceNum: {}", userId, deviceNum);
            boolean success = dataSyncService.syncDeviceData(userId, deviceNum);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "同步成功" : "同步失败");
            return result;
        } catch (Exception e) {
            log.error("Error syncing device {} for user {}: {}", deviceNum, userId, e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "同步失败: " + e.getMessage());
            return result;
        }
    }
    
    @PostMapping("/unbindVendorDevice")
    public Map<String, Object> unbindVendorDevice(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> params) {
            
        // 参数验证
        if (params == null || !params.containsKey("deviceNum")) {
            log.warn("unbindVendorDevice called with missing deviceNum");
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "缺少设备号参数");
            return result;
        }
            
        String deviceNum = params.get("deviceNum");
            
        log.info("=== unbindVendorDevice called ===");
        log.info("deviceNum: {}", deviceNum);
            
        Map<String, Object> result = new HashMap<>();
            
        try {
            log.info("Step 1: Login to vendor API...");
            if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                log.error("Vendor API login failed");
                result.put("success", false);
                result.put("message", "供应商 API 登录失败");
                return result;
            }
            log.info("Vendor API login successful");
                
            log.info("Step 2: Unbinding device from vendor API...");
            boolean unbindSuccess = vendorApiService.unbindDevice(deviceNum);
            log.info("Unbind result: {}", unbindSuccess);
                
            result.put("success", unbindSuccess);
            result.put("message", unbindSuccess ? "设备解绑成功" : "设备解绑失败");
        } catch (Exception e) {
            log.error("Error during device unbinding: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "解绑失败: " + e.getMessage());
        }
            
        return result;
    }
}