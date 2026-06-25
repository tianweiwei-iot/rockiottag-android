package com.RockiotTag.tag.helper;

import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.DeviceApiService;
import com.RockiotTag.tag.R;
import com.RockiotTag.tag.util.BoundDevicesHelper;
import com.RockiotTag.tag.util.LogUtil;

import java.util.List;

/**
 * MainActivity 认证辅助类
 * 负责封装登录后的设备同步、退出登录后的清理逻辑
 */
public class MainAuthHelper {

    private static final String TAG = "MainAuthHelper";

    private final AppCompatActivity activity;
    private final AuthCallbacks callbacks;

    /**
     * 认证回调接口，由 Activity 实现以提供必要的依赖
     */
    public interface AuthCallbacks {
        com.RockiotTag.tag.DatabaseHelper getDatabaseHelper();
        com.RockiotTag.tag.viewmodel.MainViewModel getViewModel();
        com.RockiotTag.tag.viewmodel.MapViewModel getMapViewModel();
        TagDevice getSelectedDevice();
        void setSelectedDevice(TagDevice device);
        void refreshDeviceListFragment();
        void refreshProfileFragment();
        void clearMapMarkers();
        void resetDeviceUIToDefault();
        void selectFirstDeviceAndRefresh();
        void invalidateRefreshRequests(); // 使正在进行的刷新请求失效
    }

    public MainAuthHelper(AppCompatActivity activity, AuthCallbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    /**
     * 登录成功后获取用户绑定的设备列表
     * @param token Bearer Token
     */
    public void fetchBoundDevicesAfterLogin(String token) {
        LogUtil.d(TAG, "Fetching bound devices after login, token=" + (token != null ? "exists" : "null"));
        BoundDevicesHelper.clearBoundDevicesCache(activity);
        callbacks.refreshDeviceListFragment();
        BoundDevicesHelper.fetchBoundDevicesFromServer(activity, token, new BoundDevicesHelper.FetchCallback() {
            @Override
            public void onSuccess(List<DeviceApiService.BoundDevice> boundDevices) {
                activity.runOnUiThread(() -> handleBoundDevicesFetched(boundDevices));
            }

            @Override
            public void onFailure(String message) {
                Log.e(TAG, "Failed to get bound devices: " + message);
                activity.runOnUiThread(() -> callbacks.refreshDeviceListFragment());
            }
        });
    }

    private void handleBoundDevicesFetched(List<DeviceApiService.BoundDevice> boundDevices) {
        LogUtil.d(TAG, "Got " + boundDevices.size() + " bound devices from server");

        for (DeviceApiService.BoundDevice bd : boundDevices) {
            LogUtil.d(TAG, "BoundDevice: deviceNum=" + bd.getDeviceNum()
                    + ", nickName=" + bd.nickName + ", alias=" + bd.alias);
        }

        syncBoundDevicesToLocalDatabase(boundDevices);

        android.content.SharedPreferences prefs =
                activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        String savedDeviceId = prefs.getString("selected_device_id", null);
        if ((savedDeviceId == null || savedDeviceId.isEmpty()) && !boundDevices.isEmpty()) {
            LogUtil.d(TAG, "No saved device, auto-selecting first device");
            callbacks.selectFirstDeviceAndRefresh();
        }

        callbacks.refreshDeviceListFragment();
    }

    /**
     * 同步绑定设备到本地数据库
     * @param boundDevices 从服务器获取的绑定设备列表
     */
    public void syncBoundDevicesToLocalDatabase(List<DeviceApiService.BoundDevice> boundDevices) {
        if (callbacks.getDatabaseHelper() == null || boundDevices == null) return;

        for (DeviceApiService.BoundDevice boundDevice : boundDevices) {
            String deviceNum = boundDevice.getDeviceNum();
            // 优先使用 nickName 字段，其次使用 alias 字段
            String displayName = boundDevice.nickName;
            if (displayName == null || displayName.isEmpty()) {
                displayName = boundDevice.alias;
            }

            // 使用 getDeviceByDeviceNum 查询设备（通过 deviceNum 字段查询，而不是 deviceId）
            TagDevice existingDevice = callbacks.getDatabaseHelper().getDeviceByDeviceNum(deviceNum);
            if (existingDevice != null) {
                // 更新别名（如果服务器有实际别名且与本地不同）
                if (displayName != null && !displayName.isEmpty() && !displayName.equals(existingDevice.getName())) {
                    callbacks.getDatabaseHelper().updateDeviceNameAndTag(existingDevice.getDeviceId(), deviceNum, displayName, existingDevice.getTag());
                    LogUtil.d(TAG, "Updated device alias: " + deviceNum + " -> " + displayName);
                }
            } else {
                // 本地没有此设备，添加新设备（使用 deviceNum 作为 deviceId）
                String deviceName = (displayName != null && !displayName.isEmpty()) ? displayName : deviceNum;
                TagDevice newDevice = new TagDevice(deviceNum, deviceName);
                newDevice.setDeviceNum(deviceNum);
                callbacks.getDatabaseHelper().addDevice(newDevice);
                LogUtil.d(TAG, "Added new bound device: " + deviceNum + " (" + deviceName + ")");
            }
        }
    }

    /**
     * 退出登录后刷新设备列表
     * 清除选中设备，刷新DeviceListFragment显示空列表
     */
    public void refreshDeviceListAfterLogout() {
        LogUtil.d(TAG, "Refreshing device list after logout");
        // 使正在进行的刷新请求失效，防止退出后仍更新UI
        callbacks.invalidateRefreshRequests();
        // 清除选中设备（使用clearSelectedDevice使正在进行的请求失效）
        callbacks.setSelectedDevice(null);
        if (callbacks.getViewModel() != null) {
            callbacks.getViewModel().clearSelectedDevice();
        }
        // 清除内存中缓存的设备位置数据
        if (callbacks.getMapViewModel() != null) {
            callbacks.getMapViewModel().clearDeviceLocation();
        }
        activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE).edit()
            .remove("selected_device_id")
            .apply();
        BoundDevicesHelper.clearBoundDevicesCache(activity);
        // 重置首页设备信息显示为默认状态
        callbacks.resetDeviceUIToDefault();
        // 清除地图上的设备标记
        callbacks.clearMapMarkers();
        // 刷新DeviceListFragment
        callbacks.refreshDeviceListFragment();
    }
}
