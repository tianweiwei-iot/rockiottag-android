package com.RockiotTag.tag.helper;

import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.model.LocationData;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.map.amap.AMapManager;
import com.RockiotTag.tag.map.google.GoogleMapManager;
import com.RockiotTag.tag.util.GoogleMapTrackRenderer;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.viewmodel.TrackViewModel;
import com.amap.api.maps.AMap;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.Polyline;
import com.google.android.gms.maps.GoogleMap;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * TrackActivity 轨迹地图渲染逻辑（高德 / Google 分支）。
 */
public final class TrackRenderHelper {

    private static final String TAG = "TrackRenderHelper";

    private TrackRenderHelper() {
    }

    public interface Host {
        AppCompatActivity getActivity();
        boolean isFinishing();
        boolean isDestroyed();
        IMapAdapter getMapAdapter();
        TrackViewModel getViewModel();
        List<LocationData> getAllLocationRecords();
        List<StayPoint> getStayPoints();
        List<Object> getPositionMarkers();
        List<Object> getArrowMarkers();
        Object getTrackPolyline();
        void setTrackPolyline(Object polyline);
        int getCurrentAccuracyThreshold();
        boolean isShowPolyline();
        boolean isShowMarkers();
        Calendar getSelectedDate();
        float getCurrentZoomLevel();
        boolean hasSavedZoomLevel();
        boolean isGoogleMapUserInteracted();
        TextView getTrackPointTime();
        void showLoading();
        void hideLoading();
        void setLoadingTrackData(boolean loading);
        void clearTrackUI();
        void updatePlaybackInfo(int count);
        void getAddressForLocation(double[] wgs84LatLng);
        void correctTrackEndpointForToday();
    }

    public static void renderTrack(Host host, List<LocationData> locationRecords) {
        LogUtil.d(TAG, "=== renderTrack() called === records="
                + (locationRecords != null ? locationRecords.size() : "null"));
        try {
            if (host.isFinishing() || host.isDestroyed()) {
                Log.w(TAG, "[RENDER_SKIP] Activity is finishing/destroyed");
                host.hideLoading();
                host.setLoadingTrackData(false);
                return;
            }

            host.showLoading();
            float zoomToPreserve = host.getCurrentZoomLevel();
            boolean shouldPreserveZoom = host.hasSavedZoomLevel() && host.isGoogleMapUserInteracted();

            if (locationRecords == null || locationRecords.isEmpty()) {
                host.clearTrackUI();
                host.hideLoading();
                host.updatePlaybackInfo(0);
                host.setLoadingTrackData(false);
                return;
            }

            host.clearTrackUI();

            TrackViewModel viewModel = host.getViewModel();
            List<StayPoint> stayPoints = host.getStayPoints();
            if (viewModel != null) {
                List<StayPoint> recalculated = viewModel.generateStayPointsFromRecordsWithAccuracy(
                        new ArrayList<>(locationRecords), host.getCurrentAccuracyThreshold());
                synchronized (stayPoints) {
                    stayPoints.clear();
                    stayPoints.addAll(recalculated);
                }
            }

            IMapAdapter mapAdapter = host.getMapAdapter();
            if (mapAdapter == null || !mapAdapter.isMapReady()) {
                Log.e(TAG, "[RENDER_ERROR] Map not ready");
                host.hideLoading();
                host.setLoadingTrackData(false);
                return;
            }

            if (host.isFinishing() || host.isDestroyed()) {
                host.hideLoading();
                host.setLoadingTrackData(false);
                return;
            }

            try {
                if (mapAdapter.getProvider().equals("google")) {
                    renderGoogleTrack(host, stayPoints, zoomToPreserve, shouldPreserveZoom);
                } else {
                    renderAmapTrack(host, stayPoints, zoomToPreserve, shouldPreserveZoom);
                }

                if (!host.isFinishing() && !host.isDestroyed()
                        && TrackDataProcessor.isToday(host.getSelectedDate()) && !stayPoints.isEmpty()) {
                    host.correctTrackEndpointForToday();
                }
            } catch (Exception e) {
                Log.e(TAG, "[RENDER_EXCEPTION] " + e.getMessage(), e);
                host.hideLoading();
            } finally {
                host.setLoadingTrackData(false);
                host.hideLoading();
                LogUtil.d(TAG, "=== renderTrack() END ===");
            }
        } catch (Exception e) {
            Log.e(TAG, "[RENDER_EXCEPTION] " + e.getMessage(), e);
            host.hideLoading();
            host.setLoadingTrackData(false);
        }
    }

    private static void renderGoogleTrack(Host host, List<StayPoint> stayPoints,
                                          float zoomToPreserve, boolean shouldPreserveZoom) {
        GoogleMap googleMap = ((GoogleMapManager) host.getMapAdapter()).getGoogleMap();
        if (googleMap == null) {
            host.hideLoading();
            host.setLoadingTrackData(false);
            return;
        }

        GoogleMapTrackRenderer.GoogleTrackRenderResult result =
                GoogleMapTrackRenderer.renderTrackOnGoogleMap(
                        host.getActivity(),
                        googleMap,
                        stayPoints,
                        host.getSelectedDate(),
                        host.isShowPolyline(),
                        host.getCurrentAccuracyThreshold(),
                        zoomToPreserve,
                        shouldPreserveZoom);

        host.setTrackPolyline(result.trackPolyline);
        host.getPositionMarkers().addAll(result.positionMarkers);
        host.getArrowMarkers().addAll(result.arrowMarkers);
        host.updatePlaybackInfo(host.getAllLocationRecords().size());

        if (!result.validLatLngList.isEmpty() && host.getTrackPointTime() != null) {
            LocationData firstRecord = host.getAllLocationRecords().get(0);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            host.getTrackPointTime().setText(sdf.format(new Date(firstRecord.getTimestamp())));
            host.getAddressForLocation(new double[]{
                    result.validLatLngList.get(0).latitude,
                    result.validLatLngList.get(0).longitude});
        }
    }

    private static void renderAmapTrack(Host host, List<StayPoint> stayPoints,
                                        float zoomToPreserve, boolean shouldPreserveZoom) {
        AMap am = ((AMapManager) host.getMapAdapter()).getAMap();
        if (am == null) {
            host.hideLoading();
            host.setLoadingTrackData(false);
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

        if (shouldPreserveZoom) {
            com.amap.api.maps.model.CameraPosition currentPos = am.getCameraPosition();
            if (currentPos != null) {
                am.moveCamera(com.amap.api.maps.CameraUpdateFactory.zoomTo(zoomToPreserve));
            }
        }

        host.updatePlaybackInfo(host.getAllLocationRecords().size());
        if (!stayPoints.isEmpty() && host.getTrackPointTime() != null) {
            StayPoint firstPoint = stayPoints.get(0);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            host.getTrackPointTime().setText(sdf.format(new Date(firstPoint.getArriveTime())));
            host.getAddressForLocation(new double[]{
                    firstPoint.getLatitude(), firstPoint.getLongitude()});
        }
    }
}
