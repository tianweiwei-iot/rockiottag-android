package com.RockiotTag.tag.usecase;

import android.util.Log;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.repository.DeviceRepository;

import java.util.Map;

public class GetDeviceInfoUseCase extends BaseUseCase<String, NewApiService.DeviceInfo> {
    
    private static final String TAG = "GetDeviceInfoUseCase";
    private final DeviceRepository deviceRepository;
    
    public GetDeviceInfoUseCase(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }
    
    @Override
    protected NewApiService.DeviceInfo executeSync(String deviceNum) throws Exception {
        if (deviceNum == null || deviceNum.isEmpty()) {
            Log.e(TAG, "Device number is empty");
            throw new IllegalArgumentException("设备编号不能为空");
        }
        
        Log.d(TAG, "Fetching device info for: " + deviceNum);
        
        try {
            String apiUrl = ApiConfig.getMyServerUrl(deviceNum);
            NewApiService.setApiBaseUrl(apiUrl);
            Log.d(TAG, "API URL: " + apiUrl);
            
            Device localDevice = deviceRepository.getDeviceByNum(deviceNum);
            String savedCustomerCode = null;
            if (localDevice != null && localDevice.getCustomerCode() != null && !localDevice.getCustomerCode().isEmpty()) {
                savedCustomerCode = localDevice.getCustomerCode();
                Log.d(TAG, "Using saved customerCode from local device: " + savedCustomerCode);
            }
            
            NewApiService.DeviceInfo deviceInfo = null;
            
            if (savedCustomerCode != null) {
                Log.d(TAG, "Trying with saved customerCode: " + savedCustomerCode);
                deviceInfo = NewApiService.getInstance().getDeviceLatest(deviceNum, savedCustomerCode);
            }
            
            if (deviceInfo == null || deviceInfo.deviceNum == null || deviceInfo.deviceNum.isEmpty()) {
                Log.d(TAG, "Trying all customer codes...");
                Map<String, ApiConfig.CustomerConfig> configs = ApiConfig.getAllCustomerConfigs();
                for (Map.Entry<String, ApiConfig.CustomerConfig> entry : configs.entrySet()) {
                    String customerCode = entry.getKey();
                    if (customerCode.equals(savedCustomerCode)) {
                        continue;
                    }
                    
                    Log.d(TAG, "Trying customerCode: " + customerCode);
                    deviceInfo = NewApiService.getInstance().getDeviceLatest(deviceNum, customerCode);
                    if (deviceInfo != null && deviceInfo.deviceNum != null && !deviceInfo.deviceNum.isEmpty()) {
                        Log.d(TAG, "Device found with customerCode: " + customerCode);
                        savedCustomerCode = customerCode;
                        break;
                    }
                }
            }
            
            if (deviceInfo == null || deviceInfo.deviceNum == null || deviceInfo.deviceNum.isEmpty()) {
                Log.e(TAG, "Device info is null");
                throw new RuntimeException("无法获取设备信息，请稍后重试");
            }
            
            Log.d(TAG, "Device info fetched successfully:");
            Log.d(TAG, "  - DeviceNum: " + deviceInfo.deviceNum);
            Log.d(TAG, "  - NickName: " + deviceInfo.nickName);
            Log.d(TAG, "  - Latitude: " + deviceInfo.latitude);
            Log.d(TAG, "  - Longitude: " + deviceInfo.longitude);
            Log.d(TAG, "  - Battery: " + deviceInfo.battery);
            Log.d(TAG, "  - Timestamp: " + deviceInfo.timestamp);
            
            try {
                Device existingDevice = deviceRepository.getDeviceByNum(deviceInfo.deviceNum);
                if (existingDevice != null) {
                    if (savedCustomerCode != null && !savedCustomerCode.equals(existingDevice.getCustomerCode())) {
                        existingDevice.setCustomerCode(savedCustomerCode);
                    }
                    deviceRepository.updateLocalDeviceInfo(existingDevice.getDeviceId(), deviceInfo);
                    if (savedCustomerCode != null) {
                        existingDevice.setCustomerCode(savedCustomerCode);
                        deviceRepository.saveDevice(existingDevice);
                    }
                    Log.d(TAG, "Local database updated");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to update local database: " + e.getMessage());
            }
            
            return deviceInfo;
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching device info: " + e.getMessage(), e);
            
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw new RuntimeException("网络连接超时，请检查网络设置");
            } else if (e.getMessage() != null && e.getMessage().contains("404")) {
                throw new RuntimeException("设备不存在或已被删除");
            } else {
                throw new RuntimeException("获取设备信息失败: " + e.getMessage());
            }
        }
    }
}
