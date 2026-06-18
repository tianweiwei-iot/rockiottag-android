package com.RockiotTag.tag.viewmodel;

import android.app.Application;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.RockiotTag.tag.model.TagDevice;

/**
 * 地图管理 ViewModel - 负责处理地图标记、相机移动和轨迹渲染
 */
public class MapViewModel extends AndroidViewModel {
    private static final String TAG = "MapViewModel";
    
    // LiveData for UI observation
    private final MutableLiveData<TagDevice> deviceLocation = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isCameraMoving = new MutableLiveData<>(false);
    private final MutableLiveData<String> mapStatus = new MutableLiveData<>("就绪");
    
    public MapViewModel(@NonNull Application application) {
        super(application);
    }
    
    /**
     * 更新设备位置并触发地图标记更新
     */
    public void updateDeviceLocation(TagDevice device) {
        if (device != null && (device.getLatitude() != 0 || device.getLongitude() != 0)) {
            deviceLocation.setValue(device);
            LogUtil.d(TAG, "Device location updated: " + device.getName() + " at " + device.getLatitude() + ", " + device.getLongitude());
        } else if (device != null) {
            // 即使坐标为0，也要更新（可能是新选择的设备，等待服务器返回坐标）
            deviceLocation.setValue(device);
            LogUtil.d(TAG, "Device selected (waiting for coordinates): " + device.getName());
        }
    }
    
    /**
     * 清除当前设备位置（切换设备时调用）
     */
    public void clearDeviceLocation() {
        deviceLocation.setValue(null);
        LogUtil.d(TAG, "Device location cleared");
    }
    
    /**
     * 请求移动地图相机
     */
    public void moveCamera(double latitude, double longitude, float zoom) {
        isCameraMoving.setValue(true);
        // 具体的相机移动逻辑需要在 Activity/Fragment 中观察 LiveData 后执行
        LogUtil.d(TAG, "Requesting camera move to: " + latitude + ", " + longitude);
    }
    
    /**
     * 获取设备位置
     */
    public LiveData<TagDevice> getDeviceLocation() {
        return deviceLocation;
    }
    
    /**
     * 获取相机移动状态
     */
    public LiveData<Boolean> getIsCameraMoving() {
        return isCameraMoving;
    }
    
    /**
     * 获取地图状态
     */
    public LiveData<String> getMapStatus() {
        return mapStatus;
    }
}
