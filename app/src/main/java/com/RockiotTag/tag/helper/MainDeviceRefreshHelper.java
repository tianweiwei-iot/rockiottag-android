package com.RockiotTag.tag.helper;

import com.RockiotTag.tag.util.ToastHelper;

import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.CoordinateUtils;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.R;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.viewmodel.MainViewModel;

/**
 * MainActivity 设备刷新与轨迹记录逻辑。
 */
public class MainDeviceRefreshHelper {

    private static final String TAG = "MainDeviceRefreshHelper";
    private static final long TRACK_REFRESH_INTERVAL = 30_000L;

    public interface Host {
        AppCompatActivity getActivity();
        MainViewModel getViewModel();
        DatabaseHelper getDatabaseHelper();
        TagDevice getSelectedDevice();
        void setSelectedDevice(TagDevice device);
        ImageButton getRefreshBtn();
        int incrementDeviceRefreshSequence();
        int getDeviceRefreshSequence();
        boolean isRefreshInProgress();
        void setRefreshInProgress(boolean inProgress);
        double getLastRecordedLatitude();
        void setLastRecordedLatitude(double v);
        double getLastRecordedLongitude();
        void setLastRecordedLongitude(double v);
        long getLastRecordedTimestamp();
        void setLastRecordedTimestamp(long v);
        void updateDeviceUIWithoutCameraMove(NewApiService.DeviceInfo deviceInfo);
        com.RockiotTag.tag.util.SafeHandler getTrackRefreshHandler();
        void setTrackRefreshHandler(com.RockiotTag.tag.util.SafeHandler handler);
        Runnable getTrackRefreshRunnable();
        void setTrackRefreshRunnable(Runnable runnable);
        NewApiService getApiService();
    }

    private final Host host;

    public MainDeviceRefreshHelper(Host host) {
        this.host = host;
    }

    public void initTrackRefresh() {
        com.RockiotTag.tag.util.SafeHandler handler =
                new com.RockiotTag.tag.util.SafeHandler(msg -> { });
        host.setTrackRefreshHandler(handler);

        Runnable runnable = () -> {
            if (!isHostAlive()) {
                return;
            }
            refreshAndRecordLocation();
            com.RockiotTag.tag.util.SafeHandler h = host.getTrackRefreshHandler();
            Runnable r = host.getTrackRefreshRunnable();
            if (h != null && r != null && isHostAlive()) {
                h.postDelayed(r, TRACK_REFRESH_INTERVAL);
            }
        };
        host.setTrackRefreshRunnable(runnable);
    }

    public void startTrackRefresh() {
        if (!isHostAlive()) {
            return;
        }
        com.RockiotTag.tag.util.SafeHandler handler = host.getTrackRefreshHandler();
        Runnable runnable = host.getTrackRefreshRunnable();
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
            handler.post(runnable);
        }
    }

    public void stopTrackRefresh() {
        com.RockiotTag.tag.util.SafeHandler handler = host.getTrackRefreshHandler();
        Runnable runnable = host.getTrackRefreshRunnable();
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    public void startRefreshAnimation() {
        ImageButton refreshBtn = host.getRefreshBtn();
        if (refreshBtn != null) {
            android.view.animation.Animation rotateAnimation =
                    android.view.animation.AnimationUtils.loadAnimation(
                            host.getActivity(), R.anim.rotate_refresh);
            refreshBtn.startAnimation(rotateAnimation);
        }
    }

    public void stopRefreshAnimation() {
        ImageButton refreshBtn = host.getRefreshBtn();
        if (refreshBtn != null) {
            refreshBtn.clearAnimation();
        }
    }

    public void performDeviceRefresh(boolean showToast) {
        AppCompatActivity activity = host.getActivity();
        LogUtil.d(TAG, "========== performDeviceRefresh (showToast=" + showToast + ") ==========");

        TagDevice currentSelectedDevice = host.getViewModel().getSelectedDevice().getValue();
        if (currentSelectedDevice == null) {
            if (showToast) {
                ToastHelper.show(activity, R.string.please_select_device);
            }
            return;
        }

        if (host.isRefreshInProgress()) {
            LogUtil.d(TAG, "Refresh already in progress, skipping");
            return;
        }
        host.setRefreshInProgress(true);
        startRefreshAnimation();

        final String deviceNum = currentSelectedDevice.getDeviceNum() != null
                ? currentSelectedDevice.getDeviceNum() : currentSelectedDevice.getDeviceId();
        final long currentTimestamp = currentSelectedDevice.getLastSeen();
        final String savedCustomerCode = currentSelectedDevice.getCustomerCode();
        final int currentSeq = host.incrementDeviceRefreshSequence();

        LogUtil.d(TAG, "Refreshing device: " + deviceNum + ", seq: " + currentSeq
                + ", current timestamp: " + currentTimestamp
                + ", savedCustomerCode: " + savedCustomerCode);

        new Thread(() -> {
            try {
                String apiUrl = ApiConfig.getMyServerUrl(deviceNum);
                NewApiService.DeviceInfo latestInfo = null;
                String matchedCustomerCode = null;

                if (savedCustomerCode != null && !savedCustomerCode.isEmpty()) {
                    LogUtil.d(TAG, "Trying with saved customerCode: " + savedCustomerCode);
                    latestInfo = NewApiService.getInstance().getDeviceLatest(apiUrl, deviceNum, savedCustomerCode);
                    if (latestInfo != null && latestInfo.deviceNum != null && !latestInfo.deviceNum.isEmpty()) {
                        matchedCustomerCode = savedCustomerCode;
                    } else {
                        latestInfo = null;
                    }
                }

                if (latestInfo == null) {
                    java.util.Map<String, ApiConfig.CustomerConfig> configs = ApiConfig.getAllCustomerConfigs();
                    for (java.util.Map.Entry<String, ApiConfig.CustomerConfig> entry : configs.entrySet()) {
                        if (currentSeq != host.getDeviceRefreshSequence()) break;
                        String customerCode = entry.getKey();
                        if (customerCode.equals(savedCustomerCode)) continue;
                        latestInfo = NewApiService.getInstance().getDeviceLatest(apiUrl, deviceNum, customerCode);
                        if (latestInfo != null && latestInfo.deviceNum != null && !latestInfo.deviceNum.isEmpty()) {
                            matchedCustomerCode = customerCode;
                            break;
                        }
                        latestInfo = null;
                    }
                }

                if (currentSeq != host.getDeviceRefreshSequence()) {
                    host.setRefreshInProgress(false);
                    return;
                }

                if (latestInfo == null) {
                    runOnUiThreadIfAlive(() -> {
                        stopRefreshAnimation();
                        host.setRefreshInProgress(false);
                        if (showToast) {
                            ToastHelper.show(host.getActivity(), R.string.refresh_failed);
                        }
                    });
                    return;
                }

                TagDevice selectedDevice = host.getSelectedDevice();
                if (matchedCustomerCode != null && selectedDevice != null
                        && !matchedCustomerCode.equals(selectedDevice.getCustomerCode())) {
                    selectedDevice.setCustomerCode(matchedCustomerCode);
                    host.getDatabaseHelper().addDevice(selectedDevice);
                }

                final NewApiService.DeviceInfo finalLatestInfo = latestInfo;
                final String finalMatchedCustomerCode = matchedCustomerCode;
                final boolean finalShowToast = showToast;
                long serverTimestamp = latestInfo.timestamp;

                runOnUiThreadIfAlive(() -> {
                    stopRefreshAnimation();
                    host.setRefreshInProgress(false);
                    MainViewModel viewModel = host.getViewModel();
                    if (viewModel == null) {
                        return;
                    }

                    if (serverTimestamp > currentTimestamp) {
                        TagDevice device = host.getSelectedDevice();
                        if (device != null) {
                            device.setLatitude(finalLatestInfo.latitude);
                            device.setLongitude(finalLatestInfo.longitude);
                            device.setLastSeen(serverTimestamp);
                            device.setBattery(finalLatestInfo.battery);
                            if (finalMatchedCustomerCode != null) {
                                device.setCustomerCode(finalMatchedCustomerCode);
                            }
                            host.getDatabaseHelper().addDevice(device);
                        }
                        viewModel.setSelectedDevice(device);
                        if (finalLatestInfo.battery > 0) {
                            viewModel.updateBatteryLevel(String.valueOf(finalLatestInfo.battery));
                        }
                        viewModel.updateUpdateTime(String.valueOf(serverTimestamp));
                        if (finalShowToast) {
                            ToastHelper.show(host.getActivity(), R.string.refresh_success);
                        }
                    } else if (finalShowToast) {
                        ToastHelper.show(host.getActivity(), R.string.data_already_latest);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e(TAG, "Refresh API call failed: " + e.getMessage(), e);
                runOnUiThreadIfAlive(() -> {
                    stopRefreshAnimation();
                    host.setRefreshInProgress(false);
                    if (showToast) {
                        ToastHelper.show(host.getActivity(), R.string.refresh_failed);
                    }
                });
            }
        }).start();
    }

    public void refreshDeviceLocation() {
        AppCompatActivity activity = host.getActivity();
        TagDevice selectedDevice = host.getSelectedDevice();
        if (selectedDevice == null) {
            stopRefreshAnimation();
            ToastHelper.show(activity, R.string.please_select_device);
            return;
        }
        String deviceNum = selectedDevice.getDeviceNum() != null
                ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
        host.getViewModel().fetchDeviceInfo(deviceNum);
    }

    private void refreshAndRecordLocation() {
        if (!isHostAlive()) {
            return;
        }
        TagDevice selectedDevice = host.getSelectedDevice();
        if (selectedDevice == null) return;

        final String deviceNum = selectedDevice.getDeviceNum() != null
                ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
        final int currentSeq = host.incrementDeviceRefreshSequence();
        String baseUrl = ApiConfig.getMyServerUrl(deviceNum);
        NewApiService apiService = host.getApiService();

        new Thread(() -> {
            try {
                NewApiService.ApiResponse syncResponse = apiService.syncDevice(baseUrl, deviceNum);
                if (currentSeq != host.getDeviceRefreshSequence()) return;

                NewApiService.DeviceInfo deviceInfo = null;
                if (syncResponse != null && syncResponse.isSuccess()) {
                    deviceInfo = apiService.getDeviceLatest(baseUrl, deviceNum);
                }
                if (currentSeq != host.getDeviceRefreshSequence()) return;

                if (deviceInfo == null) {
                    deviceInfo = apiService.getDeviceInfo(baseUrl, deviceNum);
                }
                if (deviceInfo == null || currentSeq != host.getDeviceRefreshSequence()) return;

                final NewApiService.DeviceInfo finalDeviceInfo = deviceInfo;
                runOnUiThreadIfAlive(() ->
                        host.updateDeviceUIWithoutCameraMove(finalDeviceInfo));

                if (deviceInfo.latitude != 0 && deviceInfo.longitude != 0 && deviceInfo.timestamp > 0) {
                    recordLocationIfValid(deviceInfo);
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "Auto refresh failed: " + e.getMessage(), e);
            }
        }).start();
    }

    private void recordLocationIfValid(NewApiService.DeviceInfo deviceInfo) {
        double newLat = deviceInfo.latitude;
        double newLng = deviceInfo.longitude;
        long newTimestamp = deviceInfo.timestamp;
        double lastLat = host.getLastRecordedLatitude();
        double lastLng = host.getLastRecordedLongitude();
        long lastTs = host.getLastRecordedTimestamp();

        if (newTimestamp <= lastTs && lastTs > 0) return;
        if (newLat < -90 || newLat > 90 || newLng < -180 || newLng > 180) return;

        if (lastLat != 0 && lastLng != 0) {
            double distance = CoordinateUtils.calculateDistanceMeters(lastLat, lastLng, newLat, newLng);
            long timeDiff = newTimestamp - lastTs;
            if (timeDiff < 60000 && distance > 500) return;
        }

        boolean shouldRecord = lastLat == 0 && lastLng == 0;
        if (!shouldRecord && lastLat != 0) {
            double distance = CoordinateUtils.calculateDistanceMeters(lastLat, lastLng, newLat, newLng);
            shouldRecord = distance > 10;
        }
        if (!shouldRecord) return;

        TagDevice selectedDevice = host.getSelectedDevice();
        if (selectedDevice == null) return;

        String recordDeviceId = selectedDevice.getDeviceId();
        LocationRecord record = new LocationRecord(recordDeviceId, newLat, newLng, newTimestamp);
        host.getDatabaseHelper().addLocationRecord(record);

        host.setLastRecordedLatitude(newLat);
        host.setLastRecordedLongitude(newLng);
        host.setLastRecordedTimestamp(newTimestamp);
    }

    private boolean isHostAlive() {
        AppCompatActivity activity = host.getActivity();
        return activity != null && !activity.isFinishing()
                && (android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }

    private void runOnUiThreadIfAlive(Runnable action) {
        AppCompatActivity activity = host.getActivity();
        if (!isHostAlive()) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (!isHostAlive()) {
                return;
            }
            action.run();
        });
    }
}
