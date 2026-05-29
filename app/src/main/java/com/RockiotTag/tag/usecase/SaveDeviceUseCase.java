package com.RockiotTag.tag.usecase;

import android.util.Log;

import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.repository.DeviceRepository;

/**
 * 保存设备的UseCase
 * 
 * 职责：
 * 1. 验证设备数据完整性
 * 2. 保存到本地数据库
 */
public class SaveDeviceUseCase extends BaseUseCase<Device, Boolean> {
    
    private static final String TAG = "SaveDeviceUseCase";
    
    private final DeviceRepository deviceRepository;
    
    public SaveDeviceUseCase(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }
    
    @Override
    protected Boolean executeSync(Device device) throws Exception {
        Log.d(TAG, "Saving device: " + (device != null ? device.getName() : "null"));
        
        // 1. 参数验证
        if (device == null) {
            throw new IllegalArgumentException("设备不能为空");
        }
        
        if (device.getDeviceId() == null || device.getDeviceId().isEmpty()) {
            throw new IllegalArgumentException("设备ID不能为空");
        }
        
        // 2. 保存到数据库
        try {
            deviceRepository.saveDevice(device);
            Log.d(TAG, "Device saved successfully: " + device.getDeviceId());
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to save device: " + e.getMessage(), e);
            throw new RuntimeException("保存设备失败: " + e.getMessage());
        }
    }
}
