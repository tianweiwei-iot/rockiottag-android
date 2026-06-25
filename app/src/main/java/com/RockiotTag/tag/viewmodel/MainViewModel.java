package com.RockiotTag.tag.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.model.Resource;
import com.RockiotTag.tag.repository.DeviceRepository;
import com.RockiotTag.tag.repository.LocationRepository;
import com.RockiotTag.tag.repository.BLERepository;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.usecase.GetDeviceInfoUseCase;
import com.RockiotTag.tag.usecase.ReverseGeocodeUseCase;
import com.RockiotTag.tag.usecase.SelectDeviceUseCase;
import com.RockiotTag.tag.usecase.TriggerBuzzerUseCase;
import com.RockiotTag.tag.usecase.SyncDevicesUseCase;

import java.util.List;

/**
 * MainActivity的ViewModel - 完全MVVM版本
 * 
 * 职责：
 * 1. 管理UI状态（通过LiveData）
 * 2. 协调UseCase执行业务逻辑
 * 3. 转换数据格式供UI显示
 * 
 * 注意：不包含具体业务逻辑，所有业务逻辑都在UseCase中
 */
public class MainViewModel extends AndroidViewModel {
    private static final String TAG = "MainViewModel";
    
    // UseCases
    private final GetDeviceInfoUseCase getDeviceInfoUseCase;
    private final ReverseGeocodeUseCase reverseGeocodeUseCase;
    private final TriggerBuzzerUseCase triggerBuzzerUseCase;
    private final SelectDeviceUseCase selectDeviceUseCase;
    private final SyncDevicesUseCase syncDevicesUseCase;
    
    // Repositories
    private final DeviceRepository deviceRepository;
    private final BLERepository bleRepository;
    private final DatabaseHelper dbHelper;
    
    // LiveData for UI observation
    private final MutableLiveData<TagDevice> selectedDevice = new MutableLiveData<>();
    private final MutableLiveData<String> deviceName = new MutableLiveData<>();
    private final MutableLiveData<String> batteryLevel = new MutableLiveData<>("-1");  // 初始值-1表示未知
    private final MutableLiveData<String> deviceAddress = new MutableLiveData<>("not_reported");
    private final MutableLiveData<String> updateTime = new MutableLiveData<>("not_reported");
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    // 并发控制：设备信息请求序列号，确保只处理最新请求的结果
    private volatile int fetchSequence = 0;
    
    // 地址请求序列号：确保只处理最新的地址请求结果
    private volatile int addressRequestSequence = 0;
    
    // observeForever Observer 引用，onCleared 时移除防止泄漏
    private LiveData<Resource<NewApiService.DeviceInfo>> fetchDeviceInfoLiveData;
    private Observer<Resource<NewApiService.DeviceInfo>> fetchDeviceInfoObserver;
    
    private LiveData<Resource<Boolean>> triggerBuzzerLiveData;
    private Observer<Resource<Boolean>> triggerBuzzerObserver;
    
    private LiveData<Resource<TagDevice>> selectDeviceLiveData;
    private Observer<Resource<TagDevice>> selectDeviceObserver;
    
    private LiveData<Resource<String>> getAddressLiveData;
    private Observer<Resource<String>> getAddressObserver;
    
    private LiveData<Resource<List<NewApiService.DeviceInfo>>> syncDevicesLiveData;
    private Observer<Resource<List<NewApiService.DeviceInfo>>> syncDevicesObserver;
    
    // Current location
    private double currentLatitude = 22.543611;
    private double currentLongitude = 113.881944;
    
    public MainViewModel(@NonNull Application application) {
        super(application);
        
        // 初始化Repositories
        deviceRepository = DeviceRepository.getInstance(application);
        LocationRepository locationRepository = LocationRepository.getInstance(application);
        bleRepository = BLERepository.getInstance(application);
        dbHelper = DatabaseHelper.getInstance(application);
        
        // 初始化UseCases
        getDeviceInfoUseCase = new GetDeviceInfoUseCase(deviceRepository);
        reverseGeocodeUseCase = new ReverseGeocodeUseCase(application, dbHelper);
        triggerBuzzerUseCase = new TriggerBuzzerUseCase(bleRepository);
        selectDeviceUseCase = new SelectDeviceUseCase(deviceRepository);
        syncDevicesUseCase = new SyncDevicesUseCase(deviceRepository);
    }
    
    /**
     * 获取选中的设备
     */
    public LiveData<TagDevice> getSelectedDevice() {
        return selectedDevice;
    }
    
    /**
     * 设置选中的设备
     */
    public void setSelectedDevice(TagDevice device) {
        selectedDevice.setValue(device);
        if (device != null) {
            deviceName.setValue(device.getName());
        }
    }
    
    /**
     * 清除选中设备（退出登录时调用）
     * 使正在进行的请求失效，防止退出后仍更新UI
     */
    public void clearSelectedDevice() {
        fetchSequence++; // 使正在进行的fetchDeviceInfo请求失效
        selectedDevice.setValue(null);
        deviceName.setValue(null);
        batteryLevel.setValue("-1");
        deviceAddress.setValue("not_reported");
        updateTime.setValue("not_reported");
    }
    
    /**
     * 获取设备名称
     */
    public LiveData<String> getDeviceName() {
        return deviceName;
    }
    
    /**
     * 获取电池电量
     */
    public LiveData<String> getBatteryLevel() {
        return batteryLevel;
    }
    
    /**
     * 获取设备地址
     */
    public LiveData<String> getDeviceAddress() {
        return deviceAddress;
    }
    
    /**
     * 获取更新时间
     */
    public LiveData<String> getUpdateTime() {
        return updateTime;
    }
    
    /**
     * 获取加载状态
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    /**
     * 获取错误信息
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 获取当前位置纬度
     */
    public double getCurrentLatitude() {
        return currentLatitude;
    }
    
    /**
     * 获取当前位置经度
     */
    public double getCurrentLongitude() {
        return currentLongitude;
    }
    
    /**
     * 设置当前位置
     */
    public void setCurrentLocation(double latitude, double longitude) {
        this.currentLatitude = latitude;
        this.currentLongitude = longitude;
    }
    
    /**
     * 从服务器获取设备最新信息（使用UseCase）
     * 使用序列号机制：每次新请求递增序列号，旧请求的结果会被丢弃
     */
    public void fetchDeviceInfo(String deviceNum) {
        if (deviceNum == null || deviceNum.isEmpty()) {
            errorMessage.setValue("设备编号不能为空");
            return;
        }
        
        // 递增序列号，使旧请求的结果失效
        int currentSeq = ++fetchSequence;
        LogUtil.d(TAG, "Fetch request #" + currentSeq + " for device: " + deviceNum);
        
        isLoading.setValue(true);
        
        // 移除旧的 Observer
        if (fetchDeviceInfoObserver != null && fetchDeviceInfoLiveData != null) {
            fetchDeviceInfoLiveData.removeObserver(fetchDeviceInfoObserver);
        }
        
        // 执行UseCase
        fetchDeviceInfoLiveData = getDeviceInfoUseCase.execute(deviceNum);
        fetchDeviceInfoObserver = resource -> {
            // 只处理最新请求的结果，丢弃旧请求
            if (currentSeq != fetchSequence) {
                LogUtil.d(TAG, "Fetch request #" + currentSeq + " ignored (stale), current=#" + fetchSequence);
                return;
            }
            
            isLoading.setValue(false);
            
            if (resource.isSuccess()) {
                handleDeviceInfoSuccess(resource.data);
            } else if (resource.isError()) {
                errorMessage.setValue(resource.message);
                Log.e(TAG, "Failed to fetch device info: " + resource.message);
            }
        };
        fetchDeviceInfoLiveData.observeForever(fetchDeviceInfoObserver);
    }
    
    /**
     * 处理设备信息成功获取
     */
    private void handleDeviceInfoSuccess(NewApiService.DeviceInfo deviceInfo) {
        if (deviceInfo == null) {
            Log.e(TAG, "handleDeviceInfoSuccess: deviceInfo is null");
            errorMessage.setValue("设备信息为空");
            return;
        }
        LogUtil.d(TAG, "Processing device info: " + deviceInfo.deviceNum);
        
        // 3. 关键优化：从本地数据库获取最新设备信息，比较时间戳，保留更新的那一个
        String deviceId = deviceInfo.deviceNum;
        TagDevice localDevice = null;
        
        try {
            localDevice = deviceRepository.getDeviceById(deviceId);
        } catch (IllegalStateException e) {
            // 数据库连接池已关闭，跳过本地数据比较
            Log.w(TAG, "Database connection pool closed, skipping local data comparison");
            localDevice = null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting local device", e);
            localDevice = null;
        }
        
        TagDevice finalDevice;
        long finalTimestamp;
        
        if (localDevice != null) {
            long localTimestamp = localDevice.getLastSeen();
            long serverTimestamp = deviceInfo.timestamp > 0 ? deviceInfo.timestamp : 0;
            
            // 【最简单逻辑】直接比较时间戳，哪个新用哪个
            if (serverTimestamp > localTimestamp) {
                // 服务器时间更新，使用服务器数据（位置、电量等）
                LogUtil.d(TAG, "Server newer: " + serverTimestamp + " > " + localTimestamp);

                TagDevice updatedDevice = new TagDevice(localDevice.getDeviceId(), localDevice.getName());
                updatedDevice.setDeviceNum(localDevice.getDeviceNum());
                updatedDevice.setTag(localDevice.getTag());
                updatedDevice.setMac(localDevice.getMac());
                updatedDevice.setCustomerCode(localDevice.getCustomerCode());
                updatedDevice.setLatitude(deviceInfo.latitude);
                updatedDevice.setLongitude(deviceInfo.longitude);
                updatedDevice.setLastSeen(serverTimestamp);
                updatedDevice.setBattery(deviceInfo.battery);

                // 昵称：保留本地昵称，不使用服务器昵称覆盖
                // 因为本地昵称可能是用户刚修改但尚未同步到服务器的
                deviceName.setValue(localDevice.getName());

                deviceRepository.saveDevice(updatedDevice);
                selectedDevice.setValue(updatedDevice);

                finalDevice = updatedDevice;
                finalTimestamp = serverTimestamp;
            } else {
                // 本地时间更新或相等，使用本地数据
                LogUtil.d(TAG, "Local newer or equal: " + localTimestamp + " >= " + serverTimestamp);

                // 昵称：保留本地昵称，不使用服务器昵称覆盖
                deviceName.setValue(localDevice.getName());

                if (deviceInfo.battery > 0) {
                    localDevice.setBattery(deviceInfo.battery);
                }

                // 本地无有效坐标时，合并服务器坐标（避免蓝牙更新时间戳后丢失位置）
                if ((localDevice.getLatitude() == 0 || localDevice.getLongitude() == 0)
                        && deviceInfo.latitude != 0 && deviceInfo.longitude != 0) {
                    localDevice.setLatitude(deviceInfo.latitude);
                    localDevice.setLongitude(deviceInfo.longitude);
                    LogUtil.d(TAG, "Merged server coordinates into local device (local had no coords)");
                }

                deviceRepository.saveDevice(localDevice);
                selectedDevice.setValue(localDevice);

                finalDevice = localDevice;
                finalTimestamp = localTimestamp;
            }
        } else {
            // 本地没有该设备，直接使用服务器数据创建新设备
            LogUtil.d(TAG, "Device not found in local database, creating new device from server data");
            
            TagDevice newDevice = new TagDevice(deviceInfo.deviceNum,
                (deviceInfo.nickName != null && !deviceInfo.nickName.isEmpty()) ? 
                deviceInfo.nickName : deviceInfo.deviceNum);
            newDevice.setDeviceNum(deviceInfo.deviceNum);
            newDevice.setMac(deviceInfo.mac);
            newDevice.setLatitude(deviceInfo.latitude);
            newDevice.setLongitude(deviceInfo.longitude);
            newDevice.setLastSeen(deviceInfo.timestamp > 0 ? deviceInfo.timestamp : System.currentTimeMillis());
            newDevice.setBattery(deviceInfo.battery);
            
            // 保存到本地数据库
            deviceRepository.saveDevice(newDevice);
            selectedDevice.setValue(newDevice);
            
            if (deviceInfo.nickName != null && !deviceInfo.nickName.isEmpty()) {
                deviceName.setValue(deviceInfo.nickName);
            }
            
            finalDevice = newDevice;
            finalTimestamp = newDevice.getLastSeen();
        }
        
        // 1. 更新电池电量（使用最终设备的电池信息）
        LogUtil.d(TAG, "Server returned battery: " + deviceInfo.battery);
        if (deviceInfo.battery > 0) {
            batteryLevel.setValue(String.valueOf(deviceInfo.battery));
            LogUtil.d(TAG, "✓ Battery level set to: " + deviceInfo.battery + "%");
        } else if (deviceInfo.battery == 0) {
            batteryLevel.setValue("0");
            LogUtil.d(TAG, "✓ Battery level set to: 0%");
        } else {
            batteryLevel.setValue("-1"); // 未知
            Log.w(TAG, "✗ Battery level is unknown (-1), server may not have this data");
        }
        
        // 2. 更新时间戳（使用最终确定的时间戳）
        LogUtil.d(TAG, "========== FINAL TIMESTAMP DECISION ==========");
        LogUtil.d(TAG, "finalTimestamp=" + finalTimestamp + " (" + 
            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(finalTimestamp)) + ")");
        LogUtil.d(TAG, "finalDevice=" + (finalDevice != null ? finalDevice.getName() : "null"));
        if (finalDevice != null) {
            LogUtil.d(TAG, "finalDevice.lastSeen=" + finalDevice.getLastSeen() + " (" + 
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date(finalDevice.getLastSeen())) + ")");
        }
        
        if (finalTimestamp > 0) {
            updateTime.setValue(String.valueOf(finalTimestamp));
            LogUtil.d(TAG, "✓ Update time SET to: " + finalTimestamp + " (" + 
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date(finalTimestamp)) + ")");
        } else {
            updateTime.setValue("not_reported");
            LogUtil.d(TAG, "✗ Update time set to: not_reported");
        }
        LogUtil.d(TAG, "==============================================");
        
        // 4. 【关键改进】不要在这里更新地址，让MainActivity的地址获取逻辑单独处理
        // 地址获取由MainActivity的onLocationUpdated回调或refreshMapWithCurrentDevice处理
        // 这样可以避免地址被多次设置导致覆盖问题
        LogUtil.d(TAG, "Address update will be handled by MainActivity's callback or refreshMapWithCurrentDevice");
    }
    
    /**
     * 触发蜂鸣器（使用UseCase）
     */
    public void triggerBuzzer() {
        // 移除旧的 Observer
        if (triggerBuzzerObserver != null && triggerBuzzerLiveData != null) {
            triggerBuzzerLiveData.removeObserver(triggerBuzzerObserver);
        }
        
        triggerBuzzerLiveData = triggerBuzzerUseCase.execute(null);
        triggerBuzzerObserver = resource -> {
            if (resource.isSuccess()) {
                LogUtil.d(TAG, "Buzzer triggered successfully");
            } else if (resource.isError()) {
                errorMessage.setValue(resource.message);
                Log.e(TAG, "Buzzer failed: " + resource.message);
            }
        };
        triggerBuzzerLiveData.observeForever(triggerBuzzerObserver);
    }
    
    /**
     * 选择设备（使用UseCase）
     */
    public void selectDevice(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            errorMessage.setValue("设备ID不能为空");
            return;
        }
        
        isLoading.setValue(true);
        
        // 移除旧的 Observer
        if (selectDeviceObserver != null && selectDeviceLiveData != null) {
            selectDeviceLiveData.removeObserver(selectDeviceObserver);
        }
        
        selectDeviceLiveData = selectDeviceUseCase.execute(deviceId);
        selectDeviceObserver = resource -> {
            isLoading.setValue(false);
            
            if (resource.isSuccess() && resource.data != null) {
                setSelectedDevice(resource.data);
                LogUtil.d(TAG, "Device selected: " + resource.data.getName());
            } else if (resource.isError()) {
                errorMessage.setValue(resource.message);
                Log.e(TAG, "Failed to select device: " + resource.message);
            }
        };
        selectDeviceLiveData.observeForever(selectDeviceObserver);
    }
    
    /**
     * 使进行中的逆地理编码请求失效（切换设备时调用）
     */
    public void invalidateAddressRequests() {
        addressRequestSequence++;
        LogUtil.d(TAG, "Address requests invalidated, sequence=" + addressRequestSequence);
    }
    
    /**
     * 获取地址（使用UseCase）
     * 
     * @param latitude 纬度
     * @param longitude 经度
     * @param languageCode 语言代码
     * @param forceRefresh 是否强制刷新
     * @param useAMapGeocoder 是否使用高德地理编码
     * @param mapMode 地图模式（"amap" 或 "google"）
     */
    public void getAddress(double latitude, double longitude, String languageCode, boolean forceRefresh, boolean useAMapGeocoder, String mapMode) {
        // 关键修复：递增请求序列号，确保只处理最新请求的结果
        int currentSequence = ++addressRequestSequence;
        LogUtil.d(TAG, "Address request #" + currentSequence + " started: lat=" + latitude + ", lng=" + longitude);
        
        isLoading.setValue(true);
        
        // 移除旧的 Observer
        if (getAddressObserver != null && getAddressLiveData != null) {
            getAddressLiveData.removeObserver(getAddressObserver);
        }
        
        ReverseGeocodeUseCase.Params params = new ReverseGeocodeUseCase.Params(
            latitude, longitude, languageCode, forceRefresh, useAMapGeocoder, mapMode
        );
        
        getAddressLiveData = reverseGeocodeUseCase.execute(params);
        getAddressObserver = resource -> {
            // 关键修复：只处理最新请求的结果，忽略过期请求
            if (currentSequence != addressRequestSequence) {
                LogUtil.d(TAG, "Address request #" + currentSequence + " ignored (stale), current=#" + addressRequestSequence);
                return;
            }
            
            isLoading.setValue(false);
            
            if (resource.isSuccess()) {
                deviceAddress.setValue(resource.data);
                LogUtil.d(TAG, "Address request #" + currentSequence + " success, setting address: " + resource.data);
            } else if (resource.isError()) {
                // 失败时显示坐标
                String coordStr = String.format("%.6f, %.6f", latitude, longitude);
                Log.w(TAG, "Address request #" + currentSequence + " FAILED, setting coordinates: " + coordStr);
                Log.w(TAG, "Error: " + resource.message);
                deviceAddress.setValue(coordStr);
                errorMessage.setValue(resource.message);
            }
        };
        getAddressLiveData.observeForever(getAddressObserver);
    }
    
    /**
     * 更新电池电量
     */
    public void updateBatteryLevel(String battery) {
        batteryLevel.postValue(battery);
    }
    
    /**
     * 更新设备地址
     */
    public void updateDeviceAddress(String address) {
        deviceAddress.postValue(address);
    }
    
    /**
     * 更新更新时间
     */
    public void updateUpdateTime(String time) {
        updateTime.postValue(time);
    }
    
    /**
     * 同步设备列表（使用UseCase）
     */
    public void syncDevices() {
        // 并发安全：防止重复同步
        if (isLoading.getValue() != null && isLoading.getValue()) {
            LogUtil.d(TAG, "Sync already in progress, ignoring request");
            return;
        }
        
        isLoading.setValue(true);
        
        // 移除旧的 Observer
        if (syncDevicesObserver != null && syncDevicesLiveData != null) {
            syncDevicesLiveData.removeObserver(syncDevicesObserver);
        }
        
        syncDevicesLiveData = syncDevicesUseCase.execute(null);
        syncDevicesObserver = resource -> {
            isLoading.setValue(false);
            
            if (resource.isSuccess()) {
                LogUtil.d(TAG, "Devices synced successfully: " + resource.data.size());
            } else if (resource.isError()) {
                errorMessage.setValue(resource.message);
                Log.e(TAG, "Failed to sync devices: " + resource.message);
            }
        };
        syncDevicesLiveData.observeForever(syncDevicesObserver);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        // 移除所有 observeForever 注册的 Observer，防止内存泄漏
        if (fetchDeviceInfoObserver != null && fetchDeviceInfoLiveData != null) {
            fetchDeviceInfoLiveData.removeObserver(fetchDeviceInfoObserver);
        }
        if (triggerBuzzerObserver != null && triggerBuzzerLiveData != null) {
            triggerBuzzerLiveData.removeObserver(triggerBuzzerObserver);
        }
        if (selectDeviceObserver != null && selectDeviceLiveData != null) {
            selectDeviceLiveData.removeObserver(selectDeviceObserver);
        }
        if (getAddressObserver != null && getAddressLiveData != null) {
            getAddressLiveData.removeObserver(getAddressObserver);
        }
        if (syncDevicesObserver != null && syncDevicesLiveData != null) {
            syncDevicesLiveData.removeObserver(syncDevicesObserver);
        }
    }
}
