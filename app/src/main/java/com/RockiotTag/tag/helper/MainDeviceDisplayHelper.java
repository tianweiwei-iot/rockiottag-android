package com.RockiotTag.tag.helper;

import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.CoordinateUtils;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.R;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.viewmodel.MapViewModel;
import com.amap.api.maps.AMap;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MarkerOptions;

/**
 * MainActivity 设备信息展示与地图标记更新逻辑。
 */
public class MainDeviceDisplayHelper {

    private static final String TAG = "MainDeviceDisplayHelper";
    private static final float ADDRESS_UPDATE_THRESHOLD = 200f;
    private static final long ADDRESS_UPDATE_INTERVAL = 60_000L;

    public interface Host {
        AppCompatActivity getActivity();
        DatabaseHelper getDatabaseHelper();
        MapViewModel getMapViewModel();
        IMapAdapter getMapAdapter();
        AMap getAMap();
        TagDevice getSelectedDevice();
        Object getDeviceLocationMarker();
        void setDeviceLocationMarker(Object marker);
        TextView getDeviceAddressText();
        TextView getBatteryLevelText();
        TextView getUpdateTimeText();
        double getLastAddressLatitude();
        void setLastAddressLatitude(double v);
        double getLastAddressLongitude();
        void setLastAddressLongitude(double v);
        long getLastAddressUpdateTime();
        void setLastAddressUpdateTime(long v);
        void showBottomInfo();
        void updateDeviceNameWithTag(String name, String tag);
        void moveCameraToDevicePosition(double latitude, double longitude);
        void showCoordinatesAndGeocode(double latitude, double longitude, boolean forceRefresh);
    }

    private final Host host;

    public MainDeviceDisplayHelper(Host host) {
        this.host = host;
    }

    public void updateDeviceUI(NewApiService.DeviceInfo deviceInfo) {
        updateDeviceUIWithLatest(deviceInfo, true);
    }

    public void updateDeviceUIWithoutCameraMove(NewApiService.DeviceInfo deviceInfo) {
        updateDeviceUIWithLatest(deviceInfo, false);
    }

    public void updateDeviceUIDefault() {
        AppCompatActivity activity = host.getActivity();
        com.RockiotTag.tag.util.DeviceInfoUpdater.resetToDefault(
                host.getBatteryLevelText(), host.getDeviceAddressText(),
                host.getUpdateTimeText(), activity);
    }

    public void resetAddressCache() {
        host.setLastAddressLatitude(0);
        host.setLastAddressLongitude(0);
        host.setLastAddressUpdateTime(0);
    }

    public void updateDeviceUIWithLatest(NewApiService.DeviceInfo deviceInfo, boolean moveCamera) {
        AppCompatActivity activity = host.getActivity();
        if (!isActivityAlive(activity)) {
            LogUtil.w(TAG, "Activity not alive, skip device UI update");
            return;
        }
        MapViewModel mapViewModel = host.getMapViewModel();
        if (mapViewModel == null) {
            LogUtil.w(TAG, "MapViewModel is null, skip device UI update");
            return;
        }
        LogUtil.d(TAG, "Updating device UI: " + deviceInfo.deviceNum + ", moveCamera=" + moveCamera);

        host.showBottomInfo();

        TagDevice selectedDevice = host.getSelectedDevice();
        if (selectedDevice != null && selectedDevice.getName() != null) {
            host.updateDeviceNameWithTag(selectedDevice.getName(), selectedDevice.getTag());
        }

        TextView deviceAddressText = host.getDeviceAddressText();
        TextView updateTimeText = host.getUpdateTimeText();

        if (deviceInfo.latitude != 0 && deviceInfo.longitude != 0) {
            if (deviceInfo.latitude < -90 || deviceInfo.latitude > 90
                    || deviceInfo.longitude < -180 || deviceInfo.longitude > 180) {
                LogUtil.w(TAG, "Invalid coordinates: lat=" + deviceInfo.latitude + ", lng=" + deviceInfo.longitude);
                deviceAddressText.setText(activity.getString(R.string.position_not_reported));
                return;
            }

            updateMapMarker(deviceInfo);

            if (moveCamera) {
                host.moveCameraToDevicePosition(deviceInfo.latitude, deviceInfo.longitude);
            }

            TagDevice tagDevice = new TagDevice(
                    selectedDevice != null ? selectedDevice.getDeviceId() : deviceInfo.deviceNum,
                    selectedDevice != null ? selectedDevice.getName() : deviceInfo.deviceNum);
            if (selectedDevice != null) {
                tagDevice.setMac(selectedDevice.getMac());
            }
            tagDevice.setLatitude(deviceInfo.latitude);
            tagDevice.setLongitude(deviceInfo.longitude);
            mapViewModel.updateDeviceLocation(tagDevice);

            double localLatBeforeUpdate = 0;
            double localLngBeforeUpdate = 0;
            if (selectedDevice != null && selectedDevice.getLatitude() != 0 && selectedDevice.getLongitude() != 0) {
                localLatBeforeUpdate = selectedDevice.getLatitude();
                localLngBeforeUpdate = selectedDevice.getLongitude();
            }

            if (selectedDevice != null) {
                selectedDevice.setLatitude(deviceInfo.latitude);
                selectedDevice.setLongitude(deviceInfo.longitude);
                host.getDatabaseHelper().addDevice(selectedDevice);
            }

            updateAddressDisplay(deviceInfo, localLatBeforeUpdate, localLngBeforeUpdate);
        } else {
            deviceAddressText.setText(activity.getString(R.string.position_not_reported));
        }

        if (deviceInfo.timestamp > 0) {
            com.RockiotTag.tag.util.DeviceInfoUpdater.updateTime(
                    updateTimeText, deviceInfo.timestamp, activity);
        } else if (deviceInfo.updatedAt != null && !deviceInfo.updatedAt.isEmpty()) {
            updateTimeText.setText(activity.getString(R.string.last_update_with_time, deviceInfo.updatedAt));
        } else {
            updateTimeText.setText(activity.getString(R.string.last_update_not_reported));
        }
    }

    private void updateAddressDisplay(NewApiService.DeviceInfo deviceInfo,
                                      double localLatBeforeUpdate, double localLngBeforeUpdate) {
        AppCompatActivity activity = host.getActivity();
        TextView deviceAddressText = host.getDeviceAddressText();
        double addressLat = deviceInfo.latitude;
        double addressLng = deviceInfo.longitude;

        if (localLatBeforeUpdate != 0 && localLngBeforeUpdate != 0) {
            double distance = CoordinateUtils.calculateDistanceMeters(
                    localLatBeforeUpdate, localLngBeforeUpdate,
                    deviceInfo.latitude, deviceInfo.longitude);
            if (distance > 1000000) {
                // 使用服务器坐标
            } else if (distance > 100) {
                addressLat = localLatBeforeUpdate;
                addressLng = localLngBeforeUpdate;
            }
        } else {
            double serverLat = deviceInfo.latitude;
            double serverLng = deviceInfo.longitude;
            boolean isShenzhenDefault = Math.abs(serverLat - 22.543611) < 0.01
                    && Math.abs(serverLng - 113.881944) < 0.01;
            if (isShenzhenDefault) {
                deviceAddressText.setText(String.format("%.6f, %.6f", serverLat, serverLng));
                return;
            }
        }

        long currentTime = System.currentTimeMillis();
        long timeSinceLastUpdate = currentTime - host.getLastAddressUpdateTime();
        double lastAddressLat = host.getLastAddressLatitude();
        double lastAddressLng = host.getLastAddressLongitude();

        if (lastAddressLat != 0 && lastAddressLng != 0) {
            double distanceToLastAddress = CoordinateUtils.calculateDistanceMeters(
                    lastAddressLat, lastAddressLng, addressLat, addressLng);
            boolean needUpdateByDistance = distanceToLastAddress >= ADDRESS_UPDATE_THRESHOLD;
            boolean needUpdateByTime = timeSinceLastUpdate >= ADDRESS_UPDATE_INTERVAL;
            if (needUpdateByDistance || needUpdateByTime) {
                host.setLastAddressLatitude(addressLat);
                host.setLastAddressLongitude(addressLng);
                host.setLastAddressUpdateTime(currentTime);
                host.showCoordinatesAndGeocode(addressLat, addressLng, true);
            }
        } else {
            host.setLastAddressLatitude(addressLat);
            host.setLastAddressLongitude(addressLng);
            host.setLastAddressUpdateTime(currentTime);
            host.showCoordinatesAndGeocode(addressLat, addressLng, true);
        }
    }

    private void updateMapMarker(NewApiService.DeviceInfo deviceInfo) {
        IMapAdapter mapAdapter = host.getMapAdapter();
        if (mapAdapter == null) return;

        AppCompatActivity activity = host.getActivity();
        TagDevice selectedDevice = host.getSelectedDevice();

        Object deviceLocationMarker = host.getDeviceLocationMarker();
        if (deviceLocationMarker != null) {
            mapAdapter.removeObject(deviceLocationMarker);
            host.setDeviceLocationMarker(null);
        }

        if (mapAdapter.getProvider().equals("google")) {
            host.setDeviceLocationMarker(mapAdapter.addMarker(
                    deviceInfo.latitude, deviceInfo.longitude,
                    selectedDevice != null ? selectedDevice.getName() : activity.getString(R.string.device),
                    activity.getString(R.string.device_location)));
        } else {
            AMap aMap = host.getAMap();
            if (aMap != null) {
                LatLng deviceLatLng = CoordinateUtils.wgs84ToGcj02(deviceInfo.latitude, deviceInfo.longitude);
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(deviceLatLng)
                        .title(selectedDevice != null ? selectedDevice.getName() : activity.getString(R.string.device))
                        .snippet(activity.getString(R.string.device_location))
                        .icon(com.RockiotTag.tag.util.MapMarkerHelper.createCustomMarkerWithR());
                host.setDeviceLocationMarker(aMap.addMarker(markerOptions));
            }
        }
    }

    private static boolean isActivityAlive(AppCompatActivity activity) {
        return activity != null && !activity.isFinishing()
                && (android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }
}
