package com.RockiotTag.tag.helper;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.CoordinateUtils;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.map.amap.AMapManager;
import com.RockiotTag.tag.map.google.GoogleMapManager;
import com.RockiotTag.tag.model.LocationData;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import com.amap.api.maps.AMap;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.Polyline;
import com.google.android.gms.maps.GoogleMap;

import java.util.ArrayList;
import java.util.List;

/**
 * TrackActivity 当天轨迹终点修正逻辑。
 */
public class TrackEndpointHelper {

    private static final String TAG = "TrackEndpointHelper";

    public interface Host {
        AppCompatActivity getActivity();
        DatabaseHelper getDatabaseHelper();
        TagDevice getSelectedDevice();
        List<StayPoint> getStayPoints();
        List<LocationData> getAllLocationRecords();
        int getCurrentAccuracyThreshold();
        IMapAdapter getMapAdapter();
        boolean isShowPolyline();
        boolean isShowMarkers();
        List<Object> getPositionMarkers();
        List<Object> getArrowMarkers();
        Object getTrackPolyline();
        void setTrackPolyline(Object polyline);
        void updatePlaybackInfo(int count);
        void clearTrackUI();
    }

    public static void correctTrackEndpointForToday(Host host) {
        try {
            LogUtil.d(TAG, "Correcting track endpoint for today...");

            LocationData latestLocation = TrackDataProcessor.getLatestDeviceLocation(
                    host.getDatabaseHelper(), host.getSelectedDevice());
            if (latestLocation == null) {
                LogUtil.d(TAG, "No device location available for endpoint correction");
                return;
            }

            List<StayPoint> stayPoints = host.getStayPoints();
            if (stayPoints.isEmpty()) {
                LogUtil.d(TAG, "No stay points to correct");
                return;
            }

            StayPoint lastStayPoint = stayPoints.get(stayPoints.size() - 1);
            long lastStayPointTime = lastStayPoint.getLeaveTime() > 0
                    ? lastStayPoint.getLeaveTime() : lastStayPoint.getArriveTime();

            if (latestLocation.getTimestamp() > lastStayPointTime) {
                double distanceToLastPoint = CoordinateUtils.calculateDistanceMeters(
                        lastStayPoint.getLatitude(), lastStayPoint.getLongitude(),
                        latestLocation.getLatitude(), latestLocation.getLongitude());

                LogUtil.d(TAG, "Device location is newer than last stay point, distance="
                        + String.format("%.1f", distanceToLastPoint) + "m, accuracyThreshold="
                        + host.getCurrentAccuracyThreshold() + "m");

                List<LocationData> allLocationRecords = host.getAllLocationRecords();

                if (distanceToLastPoint >= host.getCurrentAccuracyThreshold()) {
                    LogUtil.d(TAG, "Distance >= threshold, adding as new endpoint");

                    StayPoint newEndPoint = new StayPoint(
                            latestLocation.getLatitude(),
                            latestLocation.getLongitude(),
                            latestLocation.getTimestamp(),
                            latestLocation.getTimestamp());
                    stayPoints.add(newEndPoint);
                    allLocationRecords.add(latestLocation);
                } else {
                    LogUtil.d(TAG, "Distance < threshold, updating last stay point leave time instead of adding new point");
                    lastStayPoint.setLeaveTime(latestLocation.getTimestamp());
                    allLocationRecords.add(latestLocation);
                }

                AppCompatActivity activity = host.getActivity();
                activity.runOnUiThread(() -> rerenderTrack(host, activity));
            } else {
                LogUtil.d(TAG, "Device location is not newer than last stay point, no correction needed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in correctTrackEndpointForToday: " + e.getMessage(), e);
        }
    }

    private static void rerenderTrack(Host host, AppCompatActivity activity) {
        try {
            LogUtil.d(TAG, "Re-rendering track with corrected endpoint");

            IMapAdapter mapAdapter = host.getMapAdapter();
            if (mapAdapter == null || !mapAdapter.isMapReady()) {
                Log.w(TAG, "Map not ready, skip re-rendering");
                return;
            }

            host.clearTrackUI();

            List<StayPoint> stayPoints = host.getStayPoints();
            boolean isGoogle = mapAdapter.getProvider().equals("google");

            if (isGoogle) {
                rerenderGoogleTrack(host, activity, mapAdapter, stayPoints);
            } else {
                rerenderAmapTrack(host, mapAdapter, stayPoints);
            }

            host.updatePlaybackInfo(host.getAllLocationRecords().size());
        } catch (Exception e) {
            Log.e(TAG, "Error re-rendering track: " + e.getMessage(), e);
        }
    }

    private static void rerenderGoogleTrack(Host host, AppCompatActivity activity,
                                            IMapAdapter mapAdapter, List<StayPoint> stayPoints) {
        GoogleMap gm = ((GoogleMapManager) mapAdapter).getGoogleMap();
        if (gm == null) {
            Log.w(TAG, "GoogleMap is null, skip re-rendering");
            return;
        }

        List<com.google.android.gms.maps.model.LatLng> googleLatLngList = new ArrayList<>();
        for (StayPoint stayPoint : stayPoints) {
            if (Math.abs(stayPoint.getLatitude()) < 0.0001 && Math.abs(stayPoint.getLongitude()) < 0.0001) {
                continue;
            }
            googleLatLngList.add(new com.google.android.gms.maps.model.LatLng(
                    stayPoint.getLatitude(), stayPoint.getLongitude()));
        }

        if (googleLatLngList.size() > 1) {
            com.google.android.gms.maps.model.Polyline googlePolyline =
                    com.RockiotTag.tag.util.GoogleMapTrackRenderer.drawTrackPolyline(gm, stayPoints);
            if (googlePolyline != null) {
                googlePolyline.setVisible(host.isShowPolyline());
                host.setTrackPolyline(googlePolyline);
            }
            List<com.google.android.gms.maps.model.Marker> googleArrows =
                    com.RockiotTag.tag.util.GoogleMapTrackRenderer.addDirectionArrows(gm, googleLatLngList);
            if (googleArrows != null) {
                host.getArrowMarkers().addAll(googleArrows);
            }
        }

        for (int i = 0; i < stayPoints.size(); i++) {
            StayPoint stayPoint = stayPoints.get(i);
            com.google.android.gms.maps.model.MarkerOptions markerOptions;
            if (i == 0) {
                markerOptions = com.RockiotTag.tag.util.GoogleMapTrackRenderer.createStartEndMarkerOption(
                        activity, stayPoint, true, false);
            } else if (i == stayPoints.size() - 1) {
                markerOptions = com.RockiotTag.tag.util.GoogleMapTrackRenderer.createStartEndMarkerOption(
                        activity, stayPoint, false, true);
            } else {
                markerOptions = com.RockiotTag.tag.util.GoogleMapTrackRenderer.createNormalMarkerOption(
                        activity, stayPoint, i);
            }
            com.google.android.gms.maps.model.Marker marker = gm.addMarker(markerOptions);
            host.getPositionMarkers().add(marker);
        }

        LogUtil.d(TAG, "Track endpoint updated silently (Google Map)");
    }

    private static void rerenderAmapTrack(Host host, IMapAdapter mapAdapter, List<StayPoint> stayPoints) {
        AMap am = ((AMapManager) mapAdapter).getAMap();
        if (am == null) {
            Log.w(TAG, "AMap is null, skip re-rendering");
            return;
        }

        List<LocationRecord> recordList = new ArrayList<>();
        for (LocationData data : host.getAllLocationRecords()) {
            recordList.add(new LocationRecord(
                    data.getDeviceId(),
                    data.getLatitude(),
                    data.getLongitude(),
                    data.getTimestamp()));
        }

        List<Marker> amapPositionMarkers = new ArrayList<>();
        List<Marker> amapArrowMarkers = new ArrayList<>();

        Polyline amapPolyline = TrackMapRenderer.renderTrackOnAMap(
                am,
                stayPoints,
                recordList,
                host.isShowPolyline(),
                host.isShowMarkers(),
                false,
                amapPositionMarkers,
                amapArrowMarkers,
                host.getCurrentAccuracyThreshold());
        host.setTrackPolyline(amapPolyline);

        host.getPositionMarkers().addAll(amapPositionMarkers);
        host.getArrowMarkers().addAll(amapArrowMarkers);

        LogUtil.d(TAG, "Track endpoint updated silently");
    }
}
