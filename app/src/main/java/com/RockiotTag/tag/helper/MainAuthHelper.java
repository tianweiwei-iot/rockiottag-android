package com.RockiotTag.tag.helper;

import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.DeviceApiService;
import com.RockiotTag.tag.R;
import com.google.gson.Gson;

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
        Device getSelectedDevice();
        void setSelectedDevice(Device device);
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
        Log.d(TAG, "Fetching bound devices after login...");
        new Thread(() -> {
            try {
                DeviceApiService.DeviceApiResponse response = DeviceApiService.getInstance().getBoundDevices(token);
                activity.runOnUiThread(() -> {
                    if (response.isSuccess() && response.getDevices() != null) {
                        List<DeviceApiService.BoundDevice> boundDevices = response.getDevices();
                        Log.d(TAG, "Got " + boundDevices.size() + " bound devices from server");

                        // 存储绑定设备列表到SharedPreferences（JSON格式）
                        Gson gson = new Gson();
                        String devicesJson = gson.toJson(boundDevices);
                        activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE).edit()
                            .putString("bound_devices", devicesJson)
                            .apply();

                        // 同步到本地数据库
                        syncBoundDevicesToLocalDatabase(boundDevices);

                        // 检查是否有已保存的选中设备，如果没有则自动选中第一个设备
                        // 注意：必须在 refreshDeviceListFragment 之前执行，这样设备列表加载时才能正确高亮
                        android.content.SharedPreferences prefs = activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
                        String savedDeviceId = prefs.getString("selected_device_id", null);
                        if (savedDeviceId == null || savedDeviceId.isEmpty()) {
                            Log.d(TAG, "No saved device, auto-selecting first device");
                            callbacks.selectFirstDeviceAndRefresh();
                        }

                        // 更新DeviceListFragment（此时 selected_device_id 已设置，可以正确高亮）
                        callbacks.refreshDeviceListFragment();

                    } else {
                        Log.w(TAG, "Failed to get bound devices: " + response.getMessage());
                        // 清空本地绑定设备数据
                        activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE).edit()
                            .remove("bound_devices")
                            .apply();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error fetching bound devices: " + e.getMessage(), e);
            }
        }).start();
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
            Device existingDevice = callbacks.getDatabaseHelper().getDeviceByDeviceNum(deviceNum);
            if (existingDevice != null) {
                // 更新别名（如果服务器有实际别名且与本地不同）
                if (displayName != null && !displayName.isEmpty() && !displayName.equals(existingDevice.getName())) {
                    callbacks.getDatabaseHelper().updateDeviceNameAndTag(existingDevice.getDeviceId(), deviceNum, displayName, existingDevice.getTag());
                    Log.d(TAG, "Updated device alias: " + deviceNum + " -> " + displayName);
                }
            } else {
                // 本地没有此设备，添加新设备（使用 deviceNum 作为 deviceId）
                String deviceName = (displayName != null && !displayName.isEmpty()) ? displayName : deviceNum;
                Device newDevice = new Device(deviceNum, deviceName);
                newDevice.setDeviceNum(deviceNum);
                callbacks.getDatabaseHelper().addDevice(newDevice);
                Log.d(TAG, "Added new bound device: " + deviceNum + " (" + deviceName + ")");
            }
        }
    }

    /**
     * 退出登录后刷新设备列表
     * 清除选中设备，刷新DeviceListFragment显示空列表
     */
    public void refreshDeviceListAfterLogout() {
        Log.d(TAG, "Refreshing device list after logout");
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
            .remove("bound_devices")
            .apply();
        // 重置首页设备信息显示为默认状态
        callbacks.resetDeviceUIToDefault();
        // 清除地图上的设备标记
        callbacks.clearMapMarkers();
        // 刷新DeviceListFragment
        callbacks.refreshDeviceListFragment();
    }
}
