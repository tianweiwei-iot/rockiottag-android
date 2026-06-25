package com.RockiotTag.tag.helper;

import com.RockiotTag.tag.util.ToastHelper;

import android.os.Handler;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.CoordinateUtils;
import com.RockiotTag.tag.R;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.viewmodel.MainViewModel;
import com.RockiotTag.tag.viewmodel.MapViewModel;
import com.amap.api.maps.AMap;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MarkerOptions;

/**
 * MainActivity 地图相机、定位与设备标记逻辑。
 */
public class MainMapHelper {

    private static final String TAG = "MainMapHelper";

    public interface Host {
        AppCompatActivity getActivity();
        IMapAdapter getMapAdapter();
        AMap getAMap();
        MainViewModel getViewModel();
        MapViewModel getMapViewModel();
        TagDevice getSelectedDevice();
        Object getDeviceLocationMarker();
        void setDeviceLocationMarker(Object marker);
        double getCurrentLatitude();
        double getCurrentLongitude();
        boolean isUserLocated();
        void setUserLocated(boolean located);
        TextView getUpdateTimeText();
        void showBottomInfo();
        void showCoordinatesAndGeocode(double latitude, double longitude, boolean forceRefresh);
    }

    private final Host host;
    private final Handler locateRetryHandler = new Handler(android.os.Looper.getMainLooper());
    private Runnable pendingLocateRetry;
    private static final int MAX_LOCATE_RETRY = 50;
    private static final long LOCATE_RETRY_DELAY_MS = 100L;

    public MainMapHelper(Host host) {
        this.host = host;
    }

    /** Activity recreate/销毁前取消定位重试，避免切换语言后旧 Activity 回调导致闪退 */
    public void cancelPendingTasks() {
        if (pendingLocateRetry != null) {
            locateRetryHandler.removeCallbacks(pendingLocateRetry);
            pendingLocateRetry = null;
        }
    }

    private boolean isActivityAlive() {
        AppCompatActivity activity = host.getActivity();
        return activity != null && !activity.isFinishing()
                && (android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }

    public void autoLocateToDevice() {
        IMapAdapter mapAdapter = host.getMapAdapter();
        if (mapAdapter == null) return;

        TagDevice selectedDevice = host.getSelectedDevice();
        if (selectedDevice != null && selectedDevice.getLatitude() != 0 && selectedDevice.getLongitude() != 0) {
            mapAdapter.animateCamera(selectedDevice.getLatitude(), selectedDevice.getLongitude(), 17);
        } else if (host.getCurrentLatitude() != 0 && host.getCurrentLongitude() != 0) {
            mapAdapter.animateCamera(host.getCurrentLatitude(), host.getCurrentLongitude(), 17);
            LogUtil.d(TAG, "Auto-locate: moved to current location");
        } else {
            LogUtil.d(TAG, "Auto-locate: no valid position available");
        }
    }

    public void locateToDevicePosition(boolean setUserLocated) {
        cancelPendingTasks();
        locateToDevicePosition(setUserLocated, 0);
    }

    private void locateToDevicePosition(boolean setUserLocated, int attempt) {
        LogUtil.d(TAG, "=== locateToDevicePosition called, setUserLocated=" + setUserLocated
                + ", attempt=" + attempt + " ===");
        if (!isActivityAlive()) {
            return;
        }
        IMapAdapter mapAdapter = host.getMapAdapter();
        TagDevice selectedDevice = host.getSelectedDevice();
        AppCompatActivity activity = host.getActivity();

        boolean isMapReady = mapAdapter != null && mapAdapter.isMapReady();
        if (!isMapReady) {
            if (attempt >= MAX_LOCATE_RETRY) {
                LogUtil.w(TAG, "Map not ready after max retries, stop locateToDevicePosition");
                return;
            }
            LogUtil.w(TAG, "Map is not ready, retrying locateToDevicePosition...");
            pendingLocateRetry = () -> locateToDevicePosition(setUserLocated, attempt + 1);
            locateRetryHandler.postDelayed(pendingLocateRetry, LOCATE_RETRY_DELAY_MS);
            return;
        }

        if (setUserLocated) {
            host.setUserLocated(true);
        }

        if (selectedDevice != null && selectedDevice.getLatitude() != 0 && selectedDevice.getLongitude() != 0) {
            updateDeviceMarkerOnMap(selectedDevice);
            moveCameraToDevicePosition(selectedDevice.getLatitude(), selectedDevice.getLongitude(), setUserLocated);

            TagDevice tagDevice = new TagDevice(selectedDevice.getDeviceId(), selectedDevice.getName());
            tagDevice.setMac(selectedDevice.getMac());
            tagDevice.setLatitude(selectedDevice.getLatitude());
            tagDevice.setLongitude(selectedDevice.getLongitude());
            host.getMapViewModel().updateDeviceLocation(tagDevice);

            host.showBottomInfo();
            host.showCoordinatesAndGeocode(
                    selectedDevice.getLatitude(), selectedDevice.getLongitude(), true);

            TextView updateTimeText = host.getUpdateTimeText();
            if (updateTimeText != null) {
                if (selectedDevice.getLastSeen() > 0) {
                    updateTimeText.setText(activity.getString(R.string.last_update_with_time,
                            com.RockiotTag.tag.util.TimeFormatter.formatSmartTime(
                                    activity, selectedDevice.getLastSeen())));
                } else {
                    updateTimeText.setText(activity.getString(R.string.last_update_not_reported));
                }
            }
        } else if (host.getCurrentLatitude() != 0 && host.getCurrentLongitude() != 0) {
            mapAdapter.animateCamera(host.getCurrentLatitude(), host.getCurrentLongitude(), 17);
        } else {
            ToastHelper.show(activity, R.string.device_position_unknown);
        }
    }

    public void moveCameraToDevicePosition(double latitude, double longitude) {
        moveCameraToDevicePosition(latitude, longitude, false);
    }

    public void moveCameraToDevicePosition(double latitude, double longitude, boolean force) {
        IMapAdapter mapAdapter = host.getMapAdapter();
        if (latitude == 0 || longitude == 0 || mapAdapter == null) return;
        if (!force && host.isUserLocated()) {
            LogUtil.d(TAG, "Skip auto camera move: user has manually located");
            return;
        }
        mapAdapter.animateCamera(latitude, longitude, 17);
    }

    public void updateDeviceMarkerOnMap(TagDevice device) {
        if (device == null) return;
        IMapAdapter mapAdapter = host.getMapAdapter();
        if (mapAdapter == null) return;

        AppCompatActivity activity = host.getActivity();
        Object marker = host.getDeviceLocationMarker();
        if (marker != null) {
            mapAdapter.removeObject(marker);
            host.setDeviceLocationMarker(null);
        }

        if (mapAdapter.getProvider().equals("google")) {
            host.setDeviceLocationMarker(mapAdapter.addMarker(
                    device.getLatitude(), device.getLongitude(),
                    device.getName(), activity.getString(R.string.device_location)));
            return;
        }

        AMap aMap = host.getAMap();
        if (aMap == null) return;

        LatLng deviceLatLng = CoordinateUtils.wgs84ToGcj02(device.getLatitude(), device.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions()
                .position(deviceLatLng)
                .title(device.getName())
                .snippet(activity.getString(R.string.device_location))
                .icon(com.RockiotTag.tag.util.MapMarkerHelper.createCustomMarkerWithR());
        host.setDeviceLocationMarker(aMap.addMarker(markerOptions));
    }

    public void refreshMapWithCurrentDevice(boolean forceRefreshAddress) {
        MapViewModel mapViewModel = host.getMapViewModel();
        MainViewModel viewModel = host.getViewModel();
        TagDevice tagDevice = mapViewModel.getDeviceLocation().getValue();
        TagDevice device = viewModel.getSelectedDevice().getValue();

        if (tagDevice == null && device != null && device.getLatitude() != 0 && device.getLongitude() != 0) {
            tagDevice = new TagDevice(device.getDeviceId(), device.getName());
            tagDevice.setMac(device.getMac());
            tagDevice.setLatitude(device.getLatitude());
            tagDevice.setLongitude(device.getLongitude());
            mapViewModel.updateDeviceLocation(tagDevice);
        }

        if (tagDevice != null) {
            updateDeviceMarkerOnMap(tagDevice);
            host.showCoordinatesAndGeocode(
                    tagDevice.getLatitude(), tagDevice.getLongitude(), forceRefreshAddress);
            if (!host.isUserLocated()) {
                moveCameraToDevicePosition(tagDevice.getLatitude(), tagDevice.getLongitude());
            }
        } else {
            LogUtil.w(TAG, "Cannot refresh map: tagDevice is null");
        }
    }
}
