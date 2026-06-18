package com.RockiotTag.tag.usecase;

import android.util.Log;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.repository.DeviceRepository;
import com.RockiotTag.tag.util.LogUtil;

import java.util.List;

/**
 * 同步设备列表的UseCase
 * 
 * 职责：
 * 1. 从服务器获取所有设备列表
 * 2. 更新本地数据库
 * 3. 返回设备列表
 */
public class SyncDevicesUseCase extends BaseUseCase<Void, List<NewApiService.DeviceInfo>> {
    
    private static final String TAG = "SyncDevicesUseCase";
    private final DeviceRepository deviceRepository;
    
    public SyncDevicesUseCase(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }
    
    @Override
    protected List<NewApiService.DeviceInfo> executeSync(Void params) throws Exception {
        LogUtil.d(TAG, "Starting device sync");
        
        try {
            // 设置API地址
            String apiUrl = ApiConfig.getDefaultServerUrl();
            NewApiService.setApiBaseUrl(apiUrl);
            
            // 从服务器获取设备列表
            List<NewApiService.DeviceInfo> devices = NewApiService.getInstance().getDevices();
            
            if (devices == null) {
                throw new RuntimeException("无法获取设备列表");
            }
            
            LogUtil.d(TAG, "Fetched " + devices.size() + " devices from server");
            
            // 更新本地数据库
            for (NewApiService.DeviceInfo deviceInfo : devices) {
                try {
                    deviceRepository.updateLocalDeviceInfo(deviceInfo.deviceNum, deviceInfo);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to update local device: " + deviceInfo.deviceNum, e);
                }
            }
            
            LogUtil.d(TAG, "Device sync completed successfully");
            return devices;
            
        } catch (Exception e) {
            Log.e(TAG, "Error syncing devices: " + e.getMessage(), e);
            throw new RuntimeException("同步设备失败: " + e.getMessage());
        }
    }
}
