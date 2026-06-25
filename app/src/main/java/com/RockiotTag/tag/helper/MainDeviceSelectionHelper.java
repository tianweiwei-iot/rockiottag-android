package com.RockiotTag.tag.helper;

import com.RockiotTag.tag.util.ToastHelper;

import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.MapManager;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.R;
import com.RockiotTag.tag.UnboundDeviceManager;
import com.RockiotTag.tag.integration.LocationOptimizationManager;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.viewmodel.MainViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * MainActivity 设备选中恢复与同步逻辑。
 */
public class MainDeviceSelectionHelper {

    private static final String TAG = "MainDeviceSelectionHelper";

    public interface Host {
        AppCompatActivity getActivity();
        DatabaseHelper getDatabaseHelper();
        MainViewModel getViewModel();
        NewApiService getApiService();
        UnboundDeviceManager getUnboundDeviceManager();
        LocationOptimizationManager getLocationOptimizationManager();
        IMapAdapter getMapAdapter();
        MapManager getMapManager();
        TagDevice getSelectedDevice();
        void setSelectedDevice(TagDevice device);
        Object getDeviceLocationMarker();
        void setDeviceLocationMarker(Object marker);
        void setPendingDeviceSelection(boolean pending);
        void setUserLocated(boolean located);
        void resetAddressCache();
        void updateDeviceNameWithTag(String name, String tag);
        void showBottomInfo();
        void updateDeviceUIDefault();
        void showCoordinatesAndGeocode(double lat, double lng, boolean force);
        TextView getDeviceAddressText();
        TextView getBatteryLevelText();
        TextView getUpdateTimeText();
        double getLastRecordedLatitude();
        void setLastRecordedLatitude(double v);
        double getLastRecordedLongitude();
        void setLastRecordedLongitude(double v);
        long getLastRecordedTimestamp();
        void setLastRecordedTimestamp(long v);
        String getString(int resId);
        String getString(int resId, Object... args);
    }

    private final Host host;

    public MainDeviceSelectionHelper(Host host) {
        this.host = host;
    }

    public void restoreSelectedDeviceForMapInit() {
        DatabaseHelper db = host.getDatabaseHelper();
        if (db == null) return;

        AppCompatActivity activity = host.getActivity();
        android.content.SharedPreferences prefs =
                activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);

        String token = prefs.getString("auth_token", null);
        if (token == null || token.isEmpty()) return;

        String selectedDeviceId = prefs.getString("selected_device_id", "");
        if (selectedDeviceId.isEmpty()) return;

        TagDevice device = db.getDevice(selectedDeviceId);
        MapManager mapManager = host.getMapManager();
        if (device != null && device.getLatitude() != 0 && device.getLongitude() != 0 && mapManager != null) {
            mapManager.setTargetLocation(device.getLatitude(), device.getLongitude(), 17);
        }
    }

    public void restoreSelectedDevice() {
        DatabaseHelper db = host.getDatabaseHelper();
        if (db == null) return;

        AppCompatActivity activity = host.getActivity();
        android.content.SharedPreferences prefs =
                activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);

        String token = prefs.getString("auth_token", null);
        if (token == null || token.isEmpty()) return;

        String selectedDeviceId = prefs.getString("selected_device_id", "");
        List<TagDevice> allDevices = db.getAllDevices();

        if (!selectedDeviceId.isEmpty() && allDevices != null) {
            for (TagDevice device : allDevices) {
                if (device.getDeviceId().equals(selectedDeviceId)) {
                    applyDeviceSelection(device);
                    if (device.getLatitude() != 0 && device.getLongitude() != 0) {
                        host.showCoordinatesAndGeocode(
                                device.getLatitude(), device.getLongitude(), true);
                    } else {
                        TextView address = host.getDeviceAddressText();
                        if (address != null) {
                            address.setText(host.getString(R.string.position_getting_address));
                        }
                    }
                    initLastRecordedStateFromDb(selectedDeviceId);
                    String deviceNum = device.getDeviceNum() != null
                            ? device.getDeviceNum() : device.getDeviceId();
                    host.getViewModel().fetchDeviceInfo(deviceNum);
                    return;
                }
            }
            clearDeviceInfo();
        }

        if (allDevices == null || allDevices.isEmpty()) {
            clearDeviceInfo();
        } else {
            selectFirstDevice(allDevices);
        }
    }

    public void clearDeviceInfo() {
        host.setSelectedDevice(null);
        host.updateDeviceNameWithTag(host.getString(R.string.no_device_selected), null);
        host.showBottomInfo();
        TextView battery = host.getBatteryLevelText();
        TextView address = host.getDeviceAddressText();
        TextView time = host.getUpdateTimeText();
        if (battery != null) battery.setText(host.getString(R.string.battery_level_empty));
        if (address != null) address.setText(host.getString(R.string.position_empty));
        if (time != null) time.setText(host.getString(R.string.last_update_empty));

        Object marker = host.getDeviceLocationMarker();
        IMapAdapter mapAdapter = host.getMapAdapter();
        if (marker != null && mapAdapter != null) {
            mapAdapter.removeObject(marker);
            host.setDeviceLocationMarker(null);
        }
    }

    public void selectFirstDevice(List<TagDevice> devices) {
        if (devices == null || devices.isEmpty()) {
            clearDeviceInfo();
            return;
        }
        TagDevice firstDevice = devices.get(0);
        applyDeviceSelection(firstDevice);

        android.content.SharedPreferences prefs = host.getActivity()
                .getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        prefs.edit().putString("selected_device_id", firstDevice.getDeviceId()).apply();

        if (firstDevice.getLatitude() != 0 && firstDevice.getLongitude() != 0) {
            host.showCoordinatesAndGeocode(firstDevice.getLatitude(), firstDevice.getLongitude(), true);
        } else {
            TextView address = host.getDeviceAddressText();
            if (address != null) {
                address.setText(host.getString(R.string.position_getting_address));
            }
        }

        String deviceNum = firstDevice.getDeviceNum() != null
                ? firstDevice.getDeviceNum() : firstDevice.getDeviceId();
        host.getViewModel().fetchDeviceInfo(deviceNum);
    }

    public void syncDevicesFromApiAndSelectFirst() {
        String baseUrl = ApiConfig.SERVER_URL_12BIT;
        AppCompatActivity activity = host.getActivity();

        new Thread(() -> {
            try {
                NewApiService apiService = host.getApiService();
                DatabaseHelper db = host.getDatabaseHelper();
                UnboundDeviceManager unbound = host.getUnboundDeviceManager();

                NewApiService.ApiResponse syncResponse = apiService.syncAll(baseUrl);
                LogUtil.d(TAG, "Sync response: " + (syncResponse != null ? syncResponse.isSuccess() : "null"));

                List<NewApiService.DeviceInfo> apiDevices = apiService.getBoundDeviceList(baseUrl);
                if (apiDevices.isEmpty()) {
                    activity.runOnUiThread(this::clearDeviceInfo);
                    return;
                }

                List<TagDevice> syncedDevices = new ArrayList<>();
                int skippedCount = 0;

                for (NewApiService.DeviceInfo info : apiDevices) {
                    if (info.deviceNum == null) continue;
                    String deviceId = info.deviceNum;

                    if (unbound != null && unbound.isDeviceUnbound(deviceId)) {
                        skippedCount++;
                        continue;
                    }

                    if (!db.isDeviceBound(deviceId)) {
                        TagDevice device = new TagDevice(deviceId,
                                info.nickName != null ? info.nickName
                                        : host.getString(R.string.device_default_name, info.deviceNum));
                        device.setDeviceNum(info.deviceNum);
                        device.setLatitude(info.latitude);
                        device.setLongitude(info.longitude);
                        device.setLastSeen(info.timestamp > 0 ? info.timestamp : System.currentTimeMillis());
                        db.addDevice(device);
                        syncedDevices.add(device);
                    } else {
                        TagDevice existing = db.getDevice(deviceId);
                        if (existing != null) {
                            if (existing.getDeviceNum() == null) {
                                existing.setDeviceNum(info.deviceNum);
                            }
                            existing.setLatitude(info.latitude);
                            existing.setLongitude(info.longitude);
                            existing.setLastSeen(info.timestamp > 0 ? info.timestamp : System.currentTimeMillis());
                            db.addDevice(existing);
                            syncedDevices.add(existing);
                        }
                    }
                }

                final int finalSkipped = skippedCount;
                activity.runOnUiThread(() -> {
                    if (!syncedDevices.isEmpty()) {
                        selectFirstDevice(syncedDevices);
                        ToastHelper.show(activity,
                                host.getString(R.string.synced_devices, syncedDevices.size()));
                    } else if (finalSkipped > 0) {
                        clearDeviceInfo();
                    } else {
                        clearDeviceInfo();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error syncing device list: " + e.getMessage(), e);
                activity.runOnUiThread(() -> ToastHelper.show(activity,
                        host.getString(R.string.sync_device_failed, e.getMessage())));
            }
        }).start();
    }

    private void applyDeviceSelection(TagDevice device) {
        LocationOptimizationManager locOpt = host.getLocationOptimizationManager();
        if (locOpt != null) {
            String mac = device.getMac();
            if (mac != null && !mac.isEmpty()) {
                locOpt.setCurrentSelectedDeviceId(mac);
            }
        }

        host.getViewModel().setSelectedDevice(device);
        host.setSelectedDevice(device);
        host.setPendingDeviceSelection(true);
        host.setUserLocated(false);
        host.resetAddressCache();
        host.getViewModel().invalidateAddressRequests();
        host.updateDeviceNameWithTag(device.getName(), device.getTag());
        host.showBottomInfo();
        host.updateDeviceUIDefault();
    }

    private void initLastRecordedStateFromDb(String deviceId) {
        Calendar startTime = Calendar.getInstance();
        startTime.set(Calendar.HOUR_OF_DAY, 0);
        startTime.set(Calendar.MINUTE, 0);
        startTime.set(Calendar.SECOND, 0);
        startTime.set(Calendar.MILLISECOND, 0);

        Calendar endTime = Calendar.getInstance();
        endTime.set(Calendar.HOUR_OF_DAY, 23);
        endTime.set(Calendar.MINUTE, 59);
        endTime.set(Calendar.SECOND, 59);
        endTime.set(Calendar.MILLISECOND, 999);

        List<LocationRecord> records = host.getDatabaseHelper().getLocationRecords(
                deviceId, startTime.getTimeInMillis(), endTime.getTimeInMillis());

        if (records != null && !records.isEmpty()) {
            LocationRecord last = records.get(records.size() - 1);
            host.setLastRecordedLatitude(last.getLatitude());
            host.setLastRecordedLongitude(last.getLongitude());
            host.setLastRecordedTimestamp(last.getTimestamp());
        } else {
            host.setLastRecordedLatitude(0);
            host.setLastRecordedLongitude(0);
            host.setLastRecordedTimestamp(0);
        }
    }
}
