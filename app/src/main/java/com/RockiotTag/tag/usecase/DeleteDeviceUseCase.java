package com.RockiotTag.tag.usecase;

import android.content.Context;
import android.util.Log;

import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.repository.DeviceRepository;
import com.RockiotTag.tag.util.LogUtil;

/**
 * 删除设备的UseCase
 * 
 * 职责：
 * 1. 验证设备ID
 * 2. 从本地数据库删除设备
 */
public class DeleteDeviceUseCase extends BaseUseCase<String, Boolean> {
    
    private static final String TAG = "DeleteDeviceUseCase";
    
    private final DeviceRepository deviceRepository;
    private final DatabaseHelper dbHelper;
    
    public DeleteDeviceUseCase(DeviceRepository deviceRepository, Context context) {
        this.deviceRepository = deviceRepository;
        this.dbHelper = new DatabaseHelper(context.getApplicationContext());
    }
    
    @Override
    protected Boolean executeSync(String deviceId) throws Exception {
        LogUtil.d(TAG, "Deleting device: " + deviceId);
        
        // 1. 参数验证
        if (deviceId == null || deviceId.isEmpty()) {
            throw new IllegalArgumentException("设备ID不能为空");
        }
        
        // 2. 检查设备是否存在
        if (!deviceRepository.isDeviceBound(deviceId)) {
            Log.w(TAG, "Device not bound: " + deviceId);
            throw new RuntimeException("设备未绑定或不存在");
        }
        
        // 3. 从数据库删除设备
        try {
            int deletedCount = dbHelper.deleteDevice(deviceId);
            
            if (deletedCount > 0) {
                LogUtil.d(TAG, "Device deleted successfully: " + deviceId);
                return true;
            } else {
                Log.w(TAG, "Device not found in database: " + deviceId);
                throw new RuntimeException("设备不存在");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete device: " + e.getMessage(), e);
            throw new RuntimeException("删除设备失败: " + e.getMessage());
        }
    }
}
