package com.RockiotTag.tag.helper;

import android.graphics.Color;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import com.amap.api.maps.AMap;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MainActivity 众包/BLE 扫描多设备地图标记与轨迹线（高德 AMap 专用）。
 */
public class MainCrowdSourceMapHelper {

    private static final String TAG = "MainCrowdSourceMapHelper";

    public interface Host {
        AMap getAMap();
        AppCompatActivity getActivity();
        double getCurrentLatitude();
        double getCurrentLongitude();
        Object getCurrentLocationMarker();
        void setCurrentLocationMarker(Object marker);
    }

    private final Host host;
    private final Map<String, Object> deviceMarkers = new HashMap<>();
    private final Map<String, Object> devicePolylines = new HashMap<>();
    private final Map<String, List<LatLng>> deviceLocations = new HashMap<>();

    public MainCrowdSourceMapHelper(Host host) {
        this.host = host;
    }

    public void updateMapMarker(TagDevice device) {
        AMap aMap = host.getAMap();
        if (aMap == null || device == null) {
            return;
        }

        double latitude = device.getLatitude();
        double longitude = device.getLongitude();

        if (latitude == 0 && longitude == 0) {
            LogUtil.d(TAG, "Device has no valid location: " + device.getName());
            return;
        }

        LatLng latLng = new LatLng(latitude, longitude);
        String deviceId = device.getDeviceId();

        List<LatLng> locations = deviceLocations.get(deviceId);
        if (locations == null) {
            locations = new ArrayList<>();
            deviceLocations.put(deviceId, locations);
        }
        locations.add(latLng);
        if (locations.size() > 100) {
            locations.remove(0);
        }

        updateDevicePolyline(aMap, deviceId, locations);

        AppCompatActivity activity = host.getActivity();
        Object markerObj = deviceMarkers.get(deviceId);
        if (markerObj != null) {
            Marker marker = (Marker) markerObj;
            marker.setPosition(latLng);
            marker.setTitle(device.getName());
            marker.setSnippet(activity.getString(R.string.signal_strength, device.getSignalStrength()) + "\n"
                    + activity.getString(R.string.last_update_with_time,
                    com.RockiotTag.tag.util.TimeFormatter.formatSmartTime(activity, device.getLastSeen())));
        } else {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title(device.getName())
                    .snippet(activity.getString(R.string.signal_strength, device.getSignalStrength()) + "\n"
                            + activity.getString(R.string.last_update_with_time,
                            com.RockiotTag.tag.util.TimeFormatter.formatSmartTime(activity, device.getLastSeen())))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));

            markerObj = aMap.addMarker(markerOptions);
            deviceMarkers.put(deviceId, markerObj);
        }
    }

    private void updateDevicePolyline(AMap aMap, String deviceId, List<LatLng> locations) {
        if (locations.size() < 2) {
            return;
        }

        Object polylineObj = devicePolylines.get(deviceId);
        if (polylineObj != null) {
            ((Polyline) polylineObj).setPoints(locations);
        } else {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(locations)
                    .width(5)
                    .color(Color.BLUE)
                    .setDottedLine(false);

            polylineObj = aMap.addPolyline(polylineOptions);
            devicePolylines.put(deviceId, polylineObj);
        }
    }

    public void updateCurrentLocationMarker() {
        AMap aMap = host.getAMap();
        if (aMap == null) {
            return;
        }

        LatLng latLng = new LatLng(host.getCurrentLatitude(), host.getCurrentLongitude());

        if (host.getCurrentLocationMarker() == null) {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .icon(com.RockiotTag.tag.util.MapMarkerHelper.createCustomMarkerWithR());
            host.setCurrentLocationMarker(aMap.addMarker(markerOptions));
        } else {
            ((Marker) host.getCurrentLocationMarker()).setPosition(latLng);
        }
    }
}
