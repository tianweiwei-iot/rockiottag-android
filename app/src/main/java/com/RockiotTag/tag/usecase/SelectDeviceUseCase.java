package com.RockiotTag.tag.usecase;

import android.util.Log;

import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.repository.DeviceRepository;

/**
 * 选择设备的UseCase
 * 
 * 职责：
 * 1. 验证设备有效性
 * 2. 保存选中状态到SharedPreferences
 * 3. 返回选中的设备
 */
public class SelectDeviceUseCase extends BaseUseCase<String, Device> {
    
    private static final String TAG = "SelectDeviceUseCase";
    
    private final DeviceRepository deviceRepository;
    
    public SelectDeviceUseCase(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }
    
    @Override
    protected Device executeSync(String deviceId) throws Exception {
        Log.d(TAG, "Selecting device: " + deviceId);
        
        // 1. 参数验证
        if (deviceId == null || deviceId.isEmpty()) {
            throw new IllegalArgumentException("设备ID不能为空");
        }
        
        // 2. 从数据库获取设备
        Device device = deviceRepository.getDeviceById(deviceId);
        
        if (device == null) {
            Log.w(TAG, "Device not found: " + deviceId);
            throw new RuntimeException("设备不存在或已被删除");
        }
        
        // 3. 验证设备数据完整性
        if (device.getDeviceId() == null || device.getDeviceId().isEmpty()) {
            throw new RuntimeException("设备数据不完整");
        }
        
        Log.d(TAG, "Device selected successfully: " + device.getName());
        
        return device;
    }
}
