package com.RockiotTag.tag.helper;

import com.RockiotTag.tag.util.ToastHelper;

import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.UnboundDeviceManager;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.viewmodel.BleViewModel;
import com.RockiotTag.tag.viewmodel.MainViewModel;
import com.RockiotTag.tag.viewmodel.MapViewModel;
import com.amap.api.maps.AMap;

/**
 * MainActivity ViewModel LiveData 观察者注册。
 */
public class MainViewModelObserverHelper {

    private static final String TAG = "MainViewModelObserverHelper";

    public interface Host {
        LifecycleOwner getLifecycleOwner();
        AppCompatActivity getActivity();
        MainViewModel getViewModel();
        BleViewModel getBleViewModel();
        MapViewModel getMapViewModel();
        UnboundDeviceManager getUnboundDeviceManager();
        AMap getAMap();
        TagDevice getSelectedDevice();
        void setSelectedDevice(TagDevice device);
        boolean isPendingDeviceSelection();
        void setPendingDeviceSelection(boolean pending);
        double getLastAddressLatitude();
        double getLastAddressLongitude();
        TextView getBatteryLevelText();
        TextView getDeviceAddressText();
        TextView getUpdateTimeText();
        void updateDeviceNameWithTag(String name, String tag);
        void locateToDevicePosition(boolean setUserLocated);
        void updateDeviceMarkerOnMap(TagDevice device);
        void showCoordinatesAndGeocode(double latitude, double longitude, boolean forceRefresh);
        void startRefreshAnimation();
        void stopRefreshAnimation();
        String getString(int resId);
        String getString(int resId, Object... args);
    }

    public static void setupObservers(Host host) {
        LifecycleOwner owner = host.getLifecycleOwner();
        MainViewModel viewModel = host.getViewModel();
        AppCompatActivity activity = host.getActivity();

        viewModel.getSelectedDevice().observe(owner, device -> {
            if (device != null) {
                LogUtil.d(TAG, "ViewModel: Selected device updated: " + device.getName()
                        + ", lat=" + device.getLatitude() + ", lng=" + device.getLongitude());

                TagDevice selectedDevice = host.getSelectedDevice();
                String oldDeviceId = selectedDevice != null ? selectedDevice.getDeviceId() : null;
                String newDeviceId = device.getDeviceId();
                boolean isDeviceChanged = oldDeviceId == null || !newDeviceId.equals(oldDeviceId);
                double oldLat = selectedDevice != null ? selectedDevice.getLatitude() : 0;
                double oldLng = selectedDevice != null ? selectedDevice.getLongitude() : 0;
                boolean coordsJustAvailable = (oldLat == 0 || oldLng == 0)
                        && device.getLatitude() != 0 && device.getLongitude() != 0;

                if (selectedDevice != null && !isDeviceChanged && !host.isPendingDeviceSelection()
                        && !coordsJustAvailable) {
                    long currentTimestamp = selectedDevice.getLastSeen();
                    long newTimestamp = device.getLastSeen();
                    if (newTimestamp > 0 && currentTimestamp > 0 && newTimestamp <= currentTimestamp) {
                        LogUtil.d(TAG, "Server data has older timestamp (" + newTimestamp + " <= "
                                + currentTimestamp + "), ignoring update");
                        return;
                    }
                }

                host.setSelectedDevice(device);
                host.updateDeviceNameWithTag(device.getName(), device.getTag());

                TextView batteryLevelText = host.getBatteryLevelText();
                TextView deviceAddressText = host.getDeviceAddressText();
                TextView updateTimeText = host.getUpdateTimeText();

                if (device.getLatitude() != 0 && device.getLongitude() != 0) {
                    LogUtil.d(TAG, "Device has valid coordinates, isDeviceChanged=" + isDeviceChanged
                            + ", pendingDeviceSelection=" + host.isPendingDeviceSelection()
                            + ", coordsJustAvailable=" + coordsJustAvailable);

                    if (isDeviceChanged || host.isPendingDeviceSelection() || coordsJustAvailable) {
                        LogUtil.d(TAG, "Moving camera to device position (switch/pending/new coords)");
                        host.setPendingDeviceSelection(false);
                        host.locateToDevicePosition(false);
                    } else {
                        LogUtil.d(TAG, "Background refresh, updating marker and address (no camera move)");
                        TagDevice tagDevice = new TagDevice(device.getDeviceId(), device.getName());
                        tagDevice.setMac(device.getMac());
                        tagDevice.setLatitude(device.getLatitude());
                        tagDevice.setLongitude(device.getLongitude());
                        host.getMapViewModel().updateDeviceLocation(tagDevice);
                        host.updateDeviceMarkerOnMap(tagDevice);
                        boolean bgCoordsChanged = Math.abs(device.getLatitude() - host.getLastAddressLatitude()) > 0.000001
                                || Math.abs(device.getLongitude() - host.getLastAddressLongitude()) > 0.000001;
                        if (bgCoordsChanged) {
                            host.showCoordinatesAndGeocode(device.getLatitude(), device.getLongitude(), false);
                        }
                    }
                } else {
                    LogUtil.d(TAG, "Device has no valid coordinates yet, pendingDeviceSelection="
                            + host.isPendingDeviceSelection());
                    if (host.isPendingDeviceSelection()) {
                        deviceAddressText.setText(host.getString(R.string.position_getting_address));
                    } else {
                        batteryLevelText.setText(host.getString(R.string.battery_level_empty));
                        deviceAddressText.setText(host.getString(R.string.position_empty));
                        updateTimeText.setText(host.getString(R.string.last_update_empty));
                    }
                }
            }
        });

        viewModel.getBatteryLevel().observe(owner, batteryStr -> {
            TextView batteryLevelText = host.getBatteryLevelText();
            if (batteryLevelText != null) {
                if (host.getSelectedDevice() == null) {
                    batteryLevelText.setText(host.getString(R.string.battery_level_empty));
                    return;
                }
                if (batteryStr != null) {
                    try {
                        int battery = Integer.parseInt(batteryStr);
                        if (battery > 0) {
                            batteryLevelText.setText(host.getString(R.string.battery_level_value,
                                    String.valueOf(battery)));
                        } else if (battery == 0) {
                            batteryLevelText.setText(host.getString(R.string.battery_level_zero));
                        } else {
                            batteryLevelText.setText(host.getString(R.string.battery_level_empty));
                        }
                    } catch (NumberFormatException e) {
                        batteryLevelText.setText(host.getString(R.string.battery_level_empty));
                    }
                }
            }
        });

        viewModel.getDeviceAddress().observe(owner, address -> {
            TextView deviceAddressText = host.getDeviceAddressText();
            if (deviceAddressText != null) {
                if (host.getSelectedDevice() == null) {
                    deviceAddressText.setText(host.getString(R.string.position_empty));
                    return;
                }
                if (address != null) {
                    LogUtil.d(TAG, "LiveData observer received address: " + address);
                    if ("not_reported".equals(address)) {
                        deviceAddressText.setText(host.getString(R.string.position_empty));
                    } else {
                        deviceAddressText.setText(host.getString(R.string.position_with_address, address));
                    }
                }
            }
        });

        viewModel.getUpdateTime().observe(owner, timeStr -> {
            TextView updateTimeText = host.getUpdateTimeText();
            if (updateTimeText != null) {
                if (host.getSelectedDevice() == null) {
                    updateTimeText.setText(host.getString(R.string.last_update_empty));
                    return;
                }
                if (timeStr != null) {
                    LogUtil.d(TAG, "========== UPDATE TIME OBSERVER TRIGGERED ==========");
                    LogUtil.d(TAG, "Received timeStr: " + timeStr);

                    if ("not_reported".equals(timeStr)) {
                        LogUtil.d(TAG, "Setting text to: --");
                        updateTimeText.setText(host.getString(R.string.last_update_empty));
                    } else {
                        try {
                            long timestamp = Long.parseLong(timeStr);
                            String formattedTime = com.RockiotTag.tag.util.TimeFormatter.formatSmartTime(
                                    activity, timestamp);
                            LogUtil.d(TAG, "Parsed timestamp: " + timestamp + " ("
                                    + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                                    java.util.Locale.getDefault()).format(new java.util.Date(timestamp)) + ")");
                            LogUtil.d(TAG, "Formatted time: " + formattedTime);

                            updateTimeText.setText(host.getString(R.string.last_update_with_time, formattedTime));
                            LogUtil.d(TAG, "✓ UI updated with timestamp: " + timestamp);
                        } catch (NumberFormatException e) {
                            android.util.Log.e(TAG, "✗ Failed to parse timestamp: " + timeStr, e);
                            updateTimeText.setText(host.getString(R.string.last_update_empty));
                        }
                    }
                }
                LogUtil.d(TAG, "====================================================");
            }
        });

        viewModel.getIsLoading().observe(owner, isLoading -> {
            if (isLoading != null) {
                if (isLoading) host.startRefreshAnimation();
                else host.stopRefreshAnimation();
            }
        });

        viewModel.getErrorMessage().observe(owner, error -> {
            if (error != null && !error.isEmpty()) {
                ToastHelper.show(activity, error);
            }
        });

        BleViewModel bleViewModel = host.getBleViewModel();
        bleViewModel.getScanResults().observe(owner, devices -> {
            UnboundDeviceManager unboundDeviceManager = host.getUnboundDeviceManager();
            if (devices != null && unboundDeviceManager != null) {
                unboundDeviceManager.updateUnboundDevices(devices);
            }
        });

        bleViewModel.getIsConnected().observe(owner, connected -> {
            if (connected) {
                ToastHelper.show(activity, R.string.bluetooth_connected);
            }
        });

        host.getMapViewModel().getDeviceLocation().observe(owner, device -> {
            if (device != null && host.getAMap() != null) {
                host.updateDeviceMarkerOnMap(device);
            }
        });
    }
}
