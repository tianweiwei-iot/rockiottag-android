package com.RockiotTag.tag.usecase;

import android.util.Log;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.repository.DeviceRepository;

/**
 * 更新设备位置的UseCase
 * 
 * 职责：
 * 1. 从服务器获取设备最新位置
 * 2. 更新本地数据库
 * 3. 返回设备信息
 */
public class UpdateDeviceLocationUseCase extends BaseUseCase<String, NewApiService.DeviceInfo> {
    
    private static final String TAG = "UpdateDeviceLocationUseCase";
    private final DeviceRepository deviceRepository;
    
    public UpdateDeviceLocationUseCase(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }
    
    @Override
    protected NewApiService.DeviceInfo executeSync(String deviceNum) throws Exception {
        if (deviceNum == null || deviceNum.isEmpty()) {
            throw new IllegalArgumentException("设备编号不能为空");
        }
        
        Log.d(TAG, "Updating device location: " + deviceNum);
        
        try {
            // 设置API地址
            String apiUrl = ApiConfig.getMyServerUrl(deviceNum);
            NewApiService.setApiBaseUrl(apiUrl);
            
            // 获取设备最新位置
            NewApiService.DeviceInfo deviceInfo = NewApiService.getInstance()
                .getDeviceLatest(deviceNum);
            
            if (deviceInfo == null) {
                throw new RuntimeException("无法获取设备位置信息");
            }
            
            Log.d(TAG, "Device location updated: lat=" + deviceInfo.latitude + 
                ", lng=" + deviceInfo.longitude);
            
            // 更新本地数据库
            try {
                Device existingDevice = deviceRepository.getDeviceById(deviceInfo.deviceNum);
                if (existingDevice != null) {
                    deviceRepository.updateLocalDeviceInfo(existingDevice.getDeviceId(), deviceInfo);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to update local database", e);
            }
            
            return deviceInfo;
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating device location: " + e.getMessage(), e);
            throw new RuntimeException("更新设备位置失败: " + e.getMessage());
        }
    }
}
