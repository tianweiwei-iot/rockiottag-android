package com.RockiotTag.tag.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.repository.DeviceRepository;

import java.util.Map;

/**
 * AddDeviceActivity的ViewModel - 管理添加设备的业务逻辑
 */
public class AddDeviceViewModel extends AndroidViewModel {
    private static final String TAG = "AddDeviceViewModel";
    
    private DeviceRepository deviceRepository;
    private NewApiService apiService;
    
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isBinding = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> bindSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    public AddDeviceViewModel(@NonNull Application application) {
        super(application);
        deviceRepository = DeviceRepository.getInstance(application);
        apiService = NewApiService.getInstance();
    }
    
    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }
    
    public LiveData<Boolean> getIsBinding() {
        return isBinding;
    }
    
    public LiveData<Boolean> getBindSuccess() {
        return bindSuccess;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public boolean isDeviceBound(String deviceNum) {
        return deviceRepository.isDeviceBound(deviceNum);
    }
    
    public interface BindCallback {
        void onSuccess(Device device);
        void onError(String error);
    }
    
    public void bindDevice(String deviceNum, String nickname, String tag, BindCallback callback) {
        final String finalDeviceNum = (deviceNum != null) ? deviceNum.trim().replaceAll("\\s+", "") : "";
        
        Log.d(TAG, "Binding device: [" + finalDeviceNum + "], nickname: " + nickname);
        
        isBinding.setValue(true);
        statusMessage.postValue("正在查找设备...");
        
        new Thread(() -> {
            try {
                NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(finalDeviceNum));
                
                if (!apiService.isAuthenticated()) {
                    NewApiService.ApiResponse loginResponse = apiService.login(
                        ApiConfig.getCid(), 
                        ApiConfig.getCustomerCode(), 
                        ApiConfig.getPassword()
                    );
                    
                    if (loginResponse == null || !loginResponse.isSuccess()) {
                        handleError("登录失败", callback);
                        return;
                    }
                }
                
                NewApiService.DeviceInfo deviceInfo = null;
                String foundCustomerCode = null;
                
                Map<String, ApiConfig.CustomerConfig> configs = ApiConfig.getAllCustomerConfigs();
                for (Map.Entry<String, ApiConfig.CustomerConfig> entry : configs.entrySet()) {
                    String customerCode = entry.getKey();
                    Log.d(TAG, "Trying customer: " + customerCode + " for device: " + finalDeviceNum);
                    
                    statusMessage.postValue("正在尝试 " + entry.getValue().name + "...");
                    
                    NewApiService.DeviceInfo info = apiService.getDeviceLatest(finalDeviceNum, customerCode);
                    if (info != null && info.deviceNum != null && !info.deviceNum.isEmpty()) {
                        deviceInfo = info;
                        foundCustomerCode = customerCode;
                        Log.d(TAG, "Device found with customer: " + customerCode);
                        break;
                    }
                }
                
                if (deviceInfo == null || deviceInfo.deviceNum == null || deviceInfo.deviceNum.isEmpty()) {
                    handleError("设备未激活或不存在", callback);
                    return;
                }
                
                final String finalCustomerCode = foundCustomerCode;
                Log.d(TAG, "Device found with customerCode: " + finalCustomerCode);
                
                String actualDeviceNum = deviceInfo.deviceNum;
                Log.d(TAG, "Device info from server - deviceNum: " + actualDeviceNum + ", mac: " + deviceInfo.mac + ", nickName: " + deviceInfo.nickName);
                
                String macAddress = deviceInfo.mac;
                String finalNickname;
                boolean needSyncToServer = false;
                
                if (nickname != null && !nickname.isEmpty()) {
                    finalNickname = nickname;
                    needSyncToServer = true;
                } else if (deviceInfo.nickName == null || deviceInfo.nickName.isEmpty()) {
                    finalNickname = "Tag" + actualDeviceNum.substring(Math.max(0, actualDeviceNum.length() - 4));
                    needSyncToServer = true;
                } else {
                    finalNickname = deviceInfo.nickName;
                }
                
                Device newDevice = new Device(actualDeviceNum, finalNickname);
                newDevice.setMac(macAddress);
                newDevice.setCustomerCode(finalCustomerCode);
                newDevice.setTag(tag);
                
                if (deviceInfo.latitude != 0 && deviceInfo.longitude != 0) {
                    newDevice.setLatitude(deviceInfo.latitude);
                    newDevice.setLongitude(deviceInfo.longitude);
                }
                if (deviceInfo.battery != 0) {
                    newDevice.setSignalStrength(deviceInfo.battery);
                }
                if (deviceInfo.timestamp != 0) {
                    newDevice.setLastSeen(deviceInfo.timestamp);
                }
                if (deviceInfo.address != null && !deviceInfo.address.isEmpty()) {
                    newDevice.setAddress(deviceInfo.address);
                }
                
                deviceRepository.saveDevice(newDevice);
                
                Log.d(TAG, "Device bound successfully: " + newDevice.getDeviceId() + ", customerCode: " + finalCustomerCode);
                
                if (needSyncToServer) {
                    try {
                        NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(actualDeviceNum));
                        NewApiService.ApiResponse updateResponse = apiService.updateDevice(actualDeviceNum, finalNickname, finalCustomerCode);
                        if (updateResponse != null && updateResponse.isSuccess()) {
                            Log.d(TAG, "Nickname synced to server successfully");
                        } else {
                            Log.e(TAG, "Failed to sync nickname to server: " + (updateResponse != null ? updateResponse.getMessage() : "null response"));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error syncing nickname to server: " + e.getMessage(), e);
                    }
                }
                
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    isBinding.setValue(false);
                    statusMessage.postValue("绑定成功");
                    bindSuccess.setValue(true);
                    
                    if (callback != null) {
                        callback.onSuccess(newDevice);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error binding device: " + e.getMessage(), e);
                handleError("绑定失败: " + e.getMessage(), callback);
            }
        }).start();
    }
    
    private void handleError(String error, BindCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            isBinding.setValue(false);
            statusMessage.postValue(error);
            errorMessage.postValue(error);
            
            if (callback != null) {
                callback.onError(error);
            }
        });
    }
}
