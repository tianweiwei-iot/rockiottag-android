package com.RockiotTag.tag.helper;

import android.animation.ValueAnimator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.map.amap.AMapManager;
import com.RockiotTag.tag.map.google.GoogleMapManager;
import com.RockiotTag.tag.model.LocationData;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.viewmodel.TrackViewModel;
import com.amap.api.maps.AMap;
import com.google.android.gms.maps.GoogleMap;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * TrackActivity Helper 初始化集中入口（仿 MainHelperInitializer）。
 */
public final class TrackHelperInitializer {

    private TrackHelperInitializer() {
    }

    public interface Host {
        AppCompatActivity getActivity();

        IMapAdapter getMapAdapter();
        DatabaseHelper getDatabaseHelper();
        TrackViewModel getViewModel();
        TagDevice getSelectedDevice();
        Calendar getSelectedDate();
        Calendar getStartDate();
        Calendar getEndDate();
        void setSelectedDate(Calendar date);
        void setStartDate(Calendar date);
        void setEndDate(Calendar date);

        List<Object> getPositionMarkers();
        List<Object> getArrowMarkers();
        Object getTrackPolyline();
        void setTrackPolyline(Object polyline);
        Object getPlayedPolyline();
        void setPlayedPolyline(Object polyline);
        Object getPlayMarker();
        void setPlayMarker(Object marker);
        List<double[]> getPlayedPoints();
        double[] getCurrentPlayPosition();
        void setCurrentPlayPosition(double[] position);
        ValueAnimator getMoveAnimator();
        void setMoveAnimator(ValueAnimator animator);
        List<LocationData> getAllLocationRecords();
        List<StayPoint> getStayPoints();

        int getCurrentAccuracyThreshold();
        boolean isShowPolyline();
        boolean isShowMarkers();
        boolean isLoadingTrackData();
        void setLoadingTrackData(boolean loading);
        Map<String, Boolean> getSyncedDates();
        Map<String, Long> getLastSyncTimestamps();
        ExecutorService getThreadPool();
        ProgressBar getLoadingProgress();

        LinearLayout getTabHome();
        LinearLayout getTabList();
        LinearLayout getTabTrack();
        LinearLayout getTabProfile();
        ImageView getTabHomeIcon();
        ImageView getTabListIcon();
        ImageView getTabTrackIcon();
        ImageView getTabProfileIcon();

        void setMapCleanupHost(TrackMapCleanupHelper.Host host);
        void setTrackEndpointHost(TrackEndpointHelper.Host host);
        void setLoadingHelper(TrackLoadingHelper helper);
        void setDateHelper(TrackDateHelper helper);
        void setThemeHelper(TrackThemeHelper helper);
        void setSyncHelper(TrackSyncHelper helper);
        void setAutoRefreshHelper(TrackAutoRefreshHelper helper);

        TrackMapCleanupHelper.Host getMapCleanupHost();
        void updateDateBtnText();
        void loadTrackData();
        void loadTrackDataSilently();
        void stopPlayback();
        void hideLoading();
        void hideLoadingDialog();
        void showLoadingDialog();
        void updatePlaybackInfo(int count);
        void clearTrackUI();
        boolean isFinishing();
        boolean isDestroyed();
    }

    public static void initHelpers(Host host) {
        AppCompatActivity activity = host.getActivity();

        host.setMapCleanupHost(new TrackMapCleanupHelper.Host() {
            @Override public IMapAdapter getMapAdapter() { return host.getMapAdapter(); }
            @Override public List<Object> getPositionMarkers() { return host.getPositionMarkers(); }
            @Override public List<Object> getArrowMarkers() { return host.getArrowMarkers(); }
            @Override public Object getTrackPolyline() { return host.getTrackPolyline(); }
            @Override public void setTrackPolyline(Object polyline) { host.setTrackPolyline(polyline); }
            @Override public Object getPlayedPolyline() { return host.getPlayedPolyline(); }
            @Override public void setPlayedPolyline(Object polyline) { host.setPlayedPolyline(polyline); }
            @Override public Object getPlayMarker() { return host.getPlayMarker(); }
            @Override public void setPlayMarker(Object marker) { host.setPlayMarker(marker); }
            @Override public List<double[]> getPlayedPoints() { return host.getPlayedPoints(); }
            @Override public double[] getCurrentPlayPosition() { return host.getCurrentPlayPosition(); }
            @Override public void setCurrentPlayPosition(double[] position) { host.setCurrentPlayPosition(position); }
            @Override public ValueAnimator getMoveAnimator() { return host.getMoveAnimator(); }
            @Override public void setMoveAnimator(ValueAnimator animator) { host.setMoveAnimator(animator); }
            @Override public List<LocationData> getAllLocationRecords() { return host.getAllLocationRecords(); }
            @Override public List<StayPoint> getStayPoints() { return host.getStayPoints(); }
        });

        host.setTrackEndpointHost(new TrackEndpointHelper.Host() {
            @Override public AppCompatActivity getActivity() { return activity; }
            @Override public DatabaseHelper getDatabaseHelper() { return host.getDatabaseHelper(); }
            @Override public TagDevice getSelectedDevice() { return host.getSelectedDevice(); }
            @Override public List<StayPoint> getStayPoints() { return host.getStayPoints(); }
            @Override public List<LocationData> getAllLocationRecords() { return host.getAllLocationRecords(); }
            @Override public int getCurrentAccuracyThreshold() { return host.getCurrentAccuracyThreshold(); }
            @Override public IMapAdapter getMapAdapter() { return host.getMapAdapter(); }
            @Override public boolean isShowPolyline() { return host.isShowPolyline(); }
            @Override public boolean isShowMarkers() { return host.isShowMarkers(); }
            @Override public List<Object> getPositionMarkers() { return host.getPositionMarkers(); }
            @Override public List<Object> getArrowMarkers() { return host.getArrowMarkers(); }
            @Override public Object getTrackPolyline() { return host.getTrackPolyline(); }
            @Override public void setTrackPolyline(Object polyline) { host.setTrackPolyline(polyline); }
            @Override public void updatePlaybackInfo(int count) { host.updatePlaybackInfo(count); }
            @Override public void clearTrackUI() { host.clearTrackUI(); }
        });

        TrackLoadingHelper loadingHelper = new TrackLoadingHelper(activity);
        ProgressBar loadingProgress = host.getLoadingProgress();
        if (loadingProgress != null) {
            loadingHelper.setLoadingProgress(loadingProgress);
        }
        host.setLoadingHelper(loadingHelper);

        host.setDateHelper(new TrackDateHelper(activity, new TrackDateHelper.DateCallbacks() {
            @Override public Calendar getSelectedDate() { return host.getSelectedDate(); }
            @Override public void setSelectedDate(Calendar date) { host.setSelectedDate(date); }
            @Override public Calendar getStartDate() { return host.getStartDate(); }
            @Override public void setStartDate(Calendar date) { host.setStartDate(date); }
            @Override public Calendar getEndDate() { return host.getEndDate(); }
            @Override public void setEndDate(Calendar date) { host.setEndDate(date); }
            @Override public boolean isLoadingTrackData() { return host.isLoadingTrackData(); }
            @Override public void setLoadingTrackData(boolean loading) { host.setLoadingTrackData(loading); }
            @Override public void updateDateBtnText() { host.updateDateBtnText(); }
            @Override public void loadTrackData() { host.loadTrackData(); }
            @Override public void stopPlayback() { host.stopPlayback(); }
        }));

        host.setThemeHelper(new TrackThemeHelper(activity, new TrackThemeHelper.ThemeCallbacks() {
            @Override public boolean isGoogleMapMode() {
                IMapAdapter mapAdapter = host.getMapAdapter();
                return mapAdapter != null && mapAdapter.getProvider().equals("google");
            }
            @Override public GoogleMap getGoogleMap() {
                IMapAdapter mapAdapter = host.getMapAdapter();
                if (mapAdapter instanceof GoogleMapManager) {
                    return ((GoogleMapManager) mapAdapter).getGoogleMap();
                }
                return null;
            }
            @Override public AMap getAMap() {
                IMapAdapter mapAdapter = host.getMapAdapter();
                if (mapAdapter instanceof AMapManager) {
                    return ((AMapManager) mapAdapter).getAMap();
                }
                return null;
            }
            @Override public LinearLayout getTabHome() { return host.getTabHome(); }
            @Override public LinearLayout getTabList() { return host.getTabList(); }
            @Override public LinearLayout getTabTrack() { return host.getTabTrack(); }
            @Override public LinearLayout getTabProfile() { return host.getTabProfile(); }
            @Override public ImageView getTabHomeIcon() { return host.getTabHomeIcon(); }
            @Override public ImageView getTabListIcon() { return host.getTabListIcon(); }
            @Override public ImageView getTabTrackIcon() { return host.getTabTrackIcon(); }
            @Override public ImageView getTabProfileIcon() { return host.getTabProfileIcon(); }
        }));

        host.setSyncHelper(new TrackSyncHelper(new TrackSyncHelper.Host() {
            @Override public AppCompatActivity getActivity() { return activity; }
            @Override public boolean isFinishing() { return host.isFinishing(); }
            @Override public boolean isDestroyed() { return host.isDestroyed(); }
            @Override public ExecutorService getThreadPool() { return host.getThreadPool(); }
            @Override public TagDevice getSelectedDevice() { return host.getSelectedDevice(); }
            @Override public DatabaseHelper getDatabaseHelper() { return host.getDatabaseHelper(); }
            @Override public TrackViewModel getViewModel() { return host.getViewModel(); }
            @Override public Calendar getSelectedDate() { return host.getSelectedDate(); }
            @Override public Map<String, Boolean> getSyncedDates() { return host.getSyncedDates(); }
            @Override public Map<String, Long> getLastSyncTimestamps() { return host.getLastSyncTimestamps(); }
            @Override public void setLoadingTrackData(boolean loading) { host.setLoadingTrackData(loading); }
            @Override public void hideLoading() { host.hideLoading(); }
            @Override public void hideLoadingDialog() { host.hideLoadingDialog(); }
            @Override public void showLoadingDialog() { host.showLoadingDialog(); }
            @Override public void clearTrackUI() {
                TrackMapCleanupHelper.Host mapCleanupHost = host.getMapCleanupHost();
                if (mapCleanupHost != null) {
                    TrackMapCleanupHelper.clearTrackUI(mapCleanupHost);
                }
            }
            @Override public void updatePlaybackInfo(int count) { host.updatePlaybackInfo(count); }
        }));

        TrackAutoRefreshHelper autoRefreshHelper = new TrackAutoRefreshHelper(new TrackAutoRefreshHelper.Host() {
            @Override public AppCompatActivity getActivity() { return activity; }
            @Override public boolean isFinishing() { return host.isFinishing(); }
            @Override public boolean isDestroyed() { return host.isDestroyed(); }
            @Override public Calendar getSelectedDate() { return host.getSelectedDate(); }
            @Override public boolean isViewModelReady() {
                return host.getViewModel() != null
                        && host.getDatabaseHelper() != null
                        && host.getSelectedDevice() != null;
            }
            @Override public void loadTrackDataSilently() { host.loadTrackDataSilently(); }
        });
        autoRefreshHelper.initAutoRefresh();
        host.setAutoRefreshHelper(autoRefreshHelper);
    }
}
