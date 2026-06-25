package com.RockiotTag.tag.helper;

import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.MapManager;
import com.RockiotTag.tag.R;
import com.RockiotTag.tag.integration.LocationOptimizationManager;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.viewmodel.MainViewModel;
import com.RockiotTag.tag.viewmodel.MapViewModel;

/**
 * MainActivity 设备选择逻辑。
 */
public class MainSelectDeviceHelper {

    private static final String TAG = "MainSelectDeviceHelper";

    public interface Host {
        AppCompatActivity getActivity();
        MainViewModel getViewModel();
        MapViewModel getMapViewModel();
        LocationOptimizationManager getLocationOptimizationManager();
        IMapAdapter getMapAdapter();
        MapManager getMapManager();
        TagDevice getSelectedDevice();
        void setSelectedDevice(TagDevice device);
        int incrementDeviceRefreshSequence();
        void resetAddressCache();
        void setPendingDeviceSelection(boolean pending);
        boolean isUserLocated();
        void setUserLocated(boolean located);
        void updateDeviceNameWithTag(String name, String tag);
        void showBottomInfo();
        TextView getBatteryLevelText();
        TextView getDeviceAddressText();
        TextView getUpdateTimeText();
        void showCoordinatesAndGeocode(double latitude, double longitude, boolean forceRefresh);
        void locateToDevicePosition(boolean setUserLocated);
        void refreshMapWithCurrentDevice(boolean forceRefreshAddress);
        void performDeviceRefresh(boolean showToast);
        String getString(int resId);
        String getString(int resId, Object... args);
    }

    private final Host host;

    public MainSelectDeviceHelper(Host host) {
        this.host = host;
    }

    public void selectDevice(TagDevice device) {
        LogUtil.d(TAG, "=== Selecting device ===");
        LogUtil.d(TAG, "  Device Name: " + device.getName());
        LogUtil.d(TAG, "  Device Num (16-digit): " + device.getDeviceNum());
        LogUtil.d(TAG, "  Device MAC: " + (device.getMac() != null ? device.getMac() : "NULL/EMPTY"));
        LogUtil.d(TAG, "  Device ID (from DB): " + device.getDeviceId());
        LogUtil.d(TAG, "  Device Timestamp: " + device.getLastSeen() + " ("
                + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(device.getLastSeen())) + ")");

        int newSeq = host.incrementDeviceRefreshSequence();
        LogUtil.d(TAG, "  Device refresh sequence: " + newSeq);

        LocationOptimizationManager locationOptimizationManager = host.getLocationOptimizationManager();
        if (locationOptimizationManager != null) {
            String macAddress = device.getMac();
            if (macAddress != null && !macAddress.isEmpty()) {
                locationOptimizationManager.setCurrentSelectedDeviceId(macAddress);
                LogUtil.d(TAG, "Set selected device MAC for bluetooth matching: " + macAddress);
            } else {
                LogUtil.w(TAG, "Device MAC is null/empty, cannot set for bluetooth filtering: " + device.getName());
            }
        }

        host.getMapViewModel().clearDeviceLocation();
        host.resetAddressCache();
        host.getViewModel().invalidateAddressRequests();
        LogUtil.d(TAG, "Cleared old device location from MapViewModel");

        host.getViewModel().selectDevice(device.getDeviceId());
        host.setSelectedDevice(device);

        host.setPendingDeviceSelection(true);
        host.setUserLocated(false);
        LogUtil.d(TAG, "Reset isUserLocated flag for new device selection");

        IMapAdapter mapAdapter = host.getMapAdapter();
        if (mapAdapter != null && mapAdapter.getProvider().equals("google")) {
            host.getMapManager().resetUserInteractionState();
            LogUtil.d(TAG, "Reset Google Map user interaction state for new device");
        }

        host.updateDeviceNameWithTag(device.getName(), device.getTag());
        host.showBottomInfo();

        TextView batteryLevelText = host.getBatteryLevelText();
        TextView deviceAddressText = host.getDeviceAddressText();
        TextView updateTimeText = host.getUpdateTimeText();

        if (device.getLatitude() == 0 || device.getLongitude() == 0) {
            LogUtil.d(TAG, "Device has no valid coordinates, showing loading state");
            batteryLevelText.setText(host.getString(R.string.battery_level_empty));
            deviceAddressText.setText(host.getString(R.string.position_getting_address));
            updateTimeText.setText(host.getString(R.string.last_update_empty));
        } else {
            LogUtil.d(TAG, "Device has local coordinates: " + device.getLatitude() + ", " + device.getLongitude());
            host.showCoordinatesAndGeocode(device.getLatitude(), device.getLongitude(), true);
        }

        if (device.getLatitude() != 0 && device.getLongitude() != 0) {
            LogUtil.d(TAG, "Device has local location: lat=" + device.getLatitude() + ", lng=" + device.getLongitude());
            host.locateToDevicePosition(false);
        } else {
            LogUtil.d(TAG, "Device has no local location, will wait for server data");
            host.refreshMapWithCurrentDevice(false);
        }

        AppCompatActivity activity = host.getActivity();
        android.content.SharedPreferences prefs =
                activity.getSharedPreferences("app_settings", AppCompatActivity.MODE_PRIVATE);
        prefs.edit().putString("selected_device_id", device.getDeviceId()).apply();

        String deviceNumToFetch = device.getDeviceNum() != null ? device.getDeviceNum() : device.getDeviceId();

        LogUtil.d(TAG, "Fetching device info from server: " + deviceNumToFetch);
        host.getViewModel().fetchDeviceInfo(deviceNumToFetch);

        host.performDeviceRefresh(false);
    }
}
