package com.RockiotTag.tag.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.DeviceApiService;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.repository.DeviceRepository;
import com.RockiotTag.tag.util.LogUtil;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * 将本地已有的设备同步到账号（设备已在本地数据库但不在 bound_devices 中）
     */
    public void syncDeviceToAccount(String deviceNum, String nickname, BindCallback callback) {
        new Thread(() -> {
            try {
                android.content.SharedPreferences prefs = getApplication().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
                String token = prefs.getString("auth_token", null);

                if (token == null || token.isEmpty()) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        callback.onError("请先登录"));
                    return;
                }

                // 获取本地设备信息
                TagDevice localDevice = deviceRepository.getDeviceByNum(deviceNum);
                if (localDevice == null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        callback.onError("设备不存在"));
                    return;
                }

                String finalNickname = (nickname != null && !nickname.isEmpty()) ? nickname : localDevice.getName();

                // 调用服务器绑定 API
                DeviceApiService deviceApiService = DeviceApiService.getInstance();
                DeviceApiService.DeviceApiResponse response = deviceApiService.bindDevice(token, deviceNum, finalNickname);

                if (response.isSuccess()) {
                    LogUtil.d(TAG, "Device synced to account successfully: " + deviceNum);
                    // 更新本地 bound_devices
                    updateBoundDevicesList(prefs, deviceNum, finalNickname);

                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        callback.onSuccess(localDevice));
                } else {
                    String msg = response.getMessage();
                    Log.e(TAG, "Failed to sync device to account: " + msg);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        callback.onError(msg != null ? msg : "同步失败"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error syncing device to account: " + e.getMessage(), e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onError("同步失败: " + e.getMessage()));
            }
        }).start();
    }

    public interface BindCallback {
        void onSuccess(TagDevice device);
        void onError(String error);
    }
    
    public void bindDevice(String deviceNum, String nickname, String tag, BindCallback callback) {
        final String finalDeviceNum = (deviceNum != null) ? deviceNum.trim().replaceAll("\\s+", "") : "";
        
        LogUtil.d(TAG, "Binding device: [" + finalDeviceNum + "], nickname: " + nickname);
        
        isBinding.setValue(true);
        statusMessage.postValue("正在查找设备...");
        
        new Thread(() -> {
            try {
                String baseUrl = ApiConfig.getMyServerUrl(finalDeviceNum);
                
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
                    LogUtil.d(TAG, "Trying customer: " + customerCode + " for device: " + finalDeviceNum);
                    
                    statusMessage.postValue("正在尝试 " + entry.getValue().name + "...");
                    
                    NewApiService.DeviceInfo info = apiService.getDeviceLatest(baseUrl, finalDeviceNum, customerCode);
                    if (info != null && info.deviceNum != null && !info.deviceNum.isEmpty()) {
                        deviceInfo = info;
                        foundCustomerCode = customerCode;
                        LogUtil.d(TAG, "Device found with customer: " + customerCode);
                        break;
                    }
                }
                
                if (deviceInfo == null || deviceInfo.deviceNum == null || deviceInfo.deviceNum.isEmpty()) {
                    handleError("设备未激活或不存在", callback);
                    return;
                }
                
                final String finalCustomerCode = foundCustomerCode;
                LogUtil.d(TAG, "Device found with customerCode: " + finalCustomerCode);
                
                String actualDeviceNum = deviceInfo.deviceNum;
                LogUtil.d(TAG, "Device info from server - deviceNum: " + actualDeviceNum + ", mac: " + deviceInfo.mac + ", nickName: " + deviceInfo.nickName);
                
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
                
                TagDevice newDevice = new TagDevice(actualDeviceNum, finalNickname);
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
                
                LogUtil.d(TAG, "Device bound successfully: " + newDevice.getDeviceId() + ", customerCode: " + finalCustomerCode);
                
                // 如果用户已登录，将设备绑定到账号
                bindDeviceToUserAccount(actualDeviceNum, finalNickname);
                
                if (needSyncToServer) {
                    try {
                        NewApiService.ApiResponse updateResponse = apiService.updateDevice(ApiConfig.getMyServerUrl(actualDeviceNum), actualDeviceNum, finalNickname, finalCustomerCode);
                        if (updateResponse != null && updateResponse.isSuccess()) {
                            LogUtil.d(TAG, "Nickname synced to server successfully");
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
    
    /**
     * 将设备绑定到用户账号（如果用户已登录）
     * @param deviceNum 设备号
     * @param alias 设备别名
     */
    private void bindDeviceToUserAccount(String deviceNum, String alias) {
        try {
            // 检查用户是否已登录
            android.content.SharedPreferences prefs = getApplication().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
            String token = prefs.getString("auth_token", null);

            if (token == null || token.isEmpty()) {
                LogUtil.d(TAG, "User not logged in, skip binding to account");
                return;
            }

            LogUtil.d(TAG, "User logged in, binding device to account: " + deviceNum + " with alias: " + alias);

            // 调用后端API绑定设备到账号（传递昵称）
            DeviceApiService deviceApiService = DeviceApiService.getInstance();
            DeviceApiService.DeviceApiResponse response = deviceApiService.bindDevice(token, deviceNum, alias);

            if (response.isSuccess()) {
                LogUtil.d(TAG, "Device bound to account successfully: " + deviceNum);
                // 更新本地bound_devices列表
                updateBoundDevicesList(prefs, deviceNum, alias);
            } else {
                Log.e(TAG, "Failed to bind device to account: " + response.getMessage());
                // 绑定失败，通知UI显示错误
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    String msg = response.getMessage();
                    if (msg != null && (msg.contains("already") || msg.contains("已绑定") || msg.contains("重复"))) {
                        errorMessage.postValue("该设备已绑定，请勿重复绑定");
                    } else {
                        errorMessage.postValue("绑定失败: " + (msg != null ? msg : "未知错误"));
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error binding device to account: " + e.getMessage(), e);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                errorMessage.postValue("绑定失败: " + e.getMessage());
            });
        }
    }
    
    /**
     * 更新本地bound_devices列表
     * @param prefs SharedPreferences
     * @param deviceNum 设备号
     * @param alias 设备别名
     */
    private void updateBoundDevicesList(android.content.SharedPreferences prefs, String deviceNum, String alias) {
        try {
            String boundDevicesJson = prefs.getString("bound_devices", null);
            Gson gson = new Gson();
            
            List<DeviceApiService.BoundDevice> boundDevices;
            if (boundDevicesJson != null && !boundDevicesJson.isEmpty()) {
                com.google.gson.reflect.TypeToken<List<DeviceApiService.BoundDevice>> tokenType = 
                    new com.google.gson.reflect.TypeToken<List<DeviceApiService.BoundDevice>>() {};
                boundDevices = gson.fromJson(boundDevicesJson, tokenType.getType());
            } else {
                boundDevices = new ArrayList<>();
            }
            
            // 检查是否已存在
            boolean exists = false;
            for (DeviceApiService.BoundDevice device : boundDevices) {
                if (deviceNum.equals(device.deviceNum)) {
                    exists = true;
                    // 更新别名
                    if (alias != null && !alias.isEmpty()) {
                        device.alias = alias;
                    }
                    break;
                }
            }
            
            // 如果不存在，添加新设备
            if (!exists) {
                DeviceApiService.BoundDevice newBoundDevice = new DeviceApiService.BoundDevice(deviceNum, alias, System.currentTimeMillis());
                boundDevices.add(newBoundDevice);
            }
            
            // 保存更新后的列表
            String updatedJson = gson.toJson(boundDevices);
            prefs.edit().putString("bound_devices", updatedJson).apply();
            LogUtil.d(TAG, "Updated bound_devices list, total: " + boundDevices.size());
        } catch (Exception e) {
            Log.e(TAG, "Error updating bound_devices list: " + e.getMessage(), e);
        }
    }
}
