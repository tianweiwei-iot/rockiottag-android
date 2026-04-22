package com.rockiot.tag.controller;

import com.rockiot.tag.service.DataSyncService;
import com.rockiot.tag.service.VendorApiService;
import com.rockiot.tag.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sync")
public class DataSyncController {
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
        int userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        String deviceNum = params.get("deviceNum");
        String nickName = params.get("nickName");
        
        System.out.println("=== bindVendorDevice (admin API) called ===");
        System.out.println("userId: " + userId + ", deviceNum: " + deviceNum + ", nickName: " + nickName);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            System.out.println("Step 1: Login to vendor API...");
            if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                result.put("success", false);
                result.put("message", "供应商API登录失败");
                return result;
            }
            System.out.println("Vendor API login successful");
            
            System.out.println("Step 2: Binding device to vendor API...");
            boolean bindSuccess = vendorApiService.bindDevice(deviceNum, nickName);
            System.out.println("Bind result: " + bindSuccess);
            
            if (!bindSuccess) {
                System.out.println("Bind failed, trying to re-login and retry...");
                if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                    result.put("success", false);
                    result.put("message", "供应商API重新登录失败");
                    return result;
                }
                bindSuccess = vendorApiService.bindDevice(deviceNum, nickName);
                System.out.println("Retry bind result: " + bindSuccess);
            }
            
            System.out.println("Step 3: Refreshing device location...");
            vendorApiService.refreshLocation(deviceNum);
            
            System.out.println("Step 4: Waiting for device to report location...");
            Thread.sleep(3000);
            
            System.out.println("Step 5: Syncing device data...");
            boolean syncSuccess = dataSyncService.syncDeviceData(userId, deviceNum);
            System.out.println("Sync result: " + syncSuccess);
            
            result.put("success", bindSuccess && syncSuccess);
            result.put("bindSuccess", bindSuccess);
            result.put("syncSuccess", syncSuccess);
            result.put("message", (bindSuccess && syncSuccess) ? "设备绑定并同步成功" : 
                       (bindSuccess ? "设备绑定成功，但同步失败" : "设备绑定失败"));
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "绑定失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    @PostMapping("/all")
    public Map<String, Object> syncAllData(@RequestHeader("Authorization") String token) {
        int userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        boolean success = dataSyncService.syncAllData(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "同步成功" : "同步失败");
        return result;
    }
    
    @PostMapping("/device/{deviceNum}")
    public Map<String, Object> syncDeviceData(
            @RequestHeader("Authorization") String token,
            @PathVariable String deviceNum) {
        int userId = JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
        boolean success = dataSyncService.syncDeviceData(userId, deviceNum);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "同步成功" : "同步失败");
        return result;
    }
    
    @PostMapping("/unbindVendorDevice")
    public Map<String, Object> unbindVendorDevice(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> params) {
        String deviceNum = params.get("deviceNum");
        
        System.out.println("=== unbindVendorDevice (admin API) called ===");
        System.out.println("deviceNum: " + deviceNum);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            System.out.println("Step 1: Login to vendor API...");
            if (!vendorApiService.login(vendorUsername, vendorPassword)) {
                result.put("success", false);
                result.put("message", "供应商API登录失败");
                return result;
            }
            System.out.println("Vendor API login successful");
            
            System.out.println("Step 2: Unbinding device from vendor API...");
            boolean unbindSuccess = vendorApiService.unbindDevice(deviceNum);
            System.out.println("Unbind result: " + unbindSuccess);
            
            result.put("success", unbindSuccess);
            result.put("message", unbindSuccess ? "设备解绑成功" : "设备解绑失败");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "解绑失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
}