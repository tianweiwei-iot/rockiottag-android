package com.RockiotTag.tag.helper;

import android.util.Log;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;

import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.model.LocationData;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.viewmodel.TrackViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * TrackActivity ViewModel LiveData 观察者注册逻辑。
 */
public final class TrackViewModelObserverHelper {

    private static final String TAG = "TrackViewModelObserver";

    public interface Host {
        LifecycleOwner getLifecycleOwner();
        AppCompatActivity getActivity();
        TrackViewModel getViewModel();
        List<LocationData> getAllLocationRecords();
        List<StayPoint> getStayPoints();
        int getCurrentAccuracyThreshold();
        boolean isLoadingTrackData();
        void setLoadingTrackData(boolean loading);
        ProgressBar getLoadingProgress();
        TagDevice getSelectedDevice();
        void renderTrack(List<LocationData> records);
        void clearTrackUI();
        void updatePlaybackInfo(int count);
        void hideLoading();
        void showLoadingDialog();
        void syncTrackDataFromServerAndReload(String deviceNum, long startTime, long endTime);
        void setIsPlaying(boolean playing);
        void setCurrentPlayIndex(int index);
        void setPlaySpeed(int speed);
    }

    private TrackViewModelObserverHelper() {
    }

    public static void setupObservers(Host host) {
        LogUtil.d(TAG, "=== setupViewModelObservers() called ===");
        TrackViewModel viewModel = host.getViewModel();
        if (viewModel == null) {
            Log.e(TAG, "ViewModel is null, skip observer setup");
            return;
        }

        viewModel.getLocationRecords().observe(host.getLifecycleOwner(), records -> {
            LogUtil.d(TAG, "[OBSERVER] locationRecords observer triggered, records="
                    + (records != null ? records.size() : "null"));
            if (records != null) {
                List<LocationData> newRecords = new ArrayList<>();
                for (LocationRecord record : records) {
                    LocationData data = new LocationData();
                    data.setId(record.getId());
                    data.setDeviceId(record.getDeviceId());
                    data.setLatitude(record.getLatitude());
                    data.setLongitude(record.getLongitude());
                    data.setTimestamp(record.getTimestamp());
                    newRecords.add(data);
                }

                List<LocationData> allLocationRecords = host.getAllLocationRecords();
                synchronized (allLocationRecords) {
                    allLocationRecords.clear();
                    allLocationRecords.addAll(newRecords);
                }
                LogUtil.d(TAG, "[OBSERVER] allLocationRecords updated, size=" + allLocationRecords.size());
            }
        });

        viewModel.getStayPoints().observe(host.getLifecycleOwner(), stays -> {
            AppCompatActivity activity = host.getActivity();
            List<LocationData> allLocationRecords = host.getAllLocationRecords();
            LogUtil.d(TAG, "[OBSERVER] stayPoints observer triggered, stays="
                    + (stays != null ? stays.size() : "null")
                    + ", allLocationRecords=" + allLocationRecords.size());
            if (stays != null) {
                List<StayPoint> finalStayPoints = viewModel.generateStayPointsFromRecordsWithAccuracy(
                        new ArrayList<>(allLocationRecords), host.getCurrentAccuracyThreshold());
                LogUtil.d(TAG, "[OBSERVER] Generated stay points with threshold "
                        + host.getCurrentAccuracyThreshold() + "m, count=" + finalStayPoints.size());

                List<StayPoint> stayPoints = host.getStayPoints();
                synchronized (stayPoints) {
                    stayPoints.clear();
                    stayPoints.addAll(finalStayPoints);
                }

                synchronized (allLocationRecords) {
                    if (!allLocationRecords.isEmpty()) {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            LogUtil.d(TAG, "[OBSERVER] Calling renderTrack with "
                                    + allLocationRecords.size() + " records");
                            host.renderTrack(new ArrayList<>(allLocationRecords));
                        } else {
                            Log.w(TAG, "[OBSERVER] Activity is finishing/destroyed, skip renderTrack");
                            host.setLoadingTrackData(false);
                            LogUtil.d(TAG, "[LOCK_RELEASED] isLoadingTrackData set to FALSE (activity destroyed)");
                        }
                    } else {
                        LogUtil.d(TAG, "[OBSERVER] No location records, clearing UI and hiding loading");
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            host.clearTrackUI();
                            host.updatePlaybackInfo(0);
                            host.hideLoading();
                        }
                        host.setLoadingTrackData(false);
                        LogUtil.d(TAG, "[LOCK_RELEASED] isLoadingTrackData set to FALSE (no records)");
                    }
                }
            } else {
                Log.w(TAG, "[OBSERVER] stays is null");
                host.setLoadingTrackData(false);
                LogUtil.d(TAG, "[LOCK_RELEASED] isLoadingTrackData set to FALSE (stays null)");
            }
        });

        viewModel.getIsLoading().observe(host.getLifecycleOwner(), isLoading -> {
            LogUtil.d(TAG, "[OBSERVER] isLoading observer triggered, isLoading=" + isLoading);
            ProgressBar loadingProgress = host.getLoadingProgress();
            if (loadingProgress != null) {
                loadingProgress.setVisibility(isLoading ? android.view.View.VISIBLE : android.view.View.GONE);
            }
        });

        viewModel.getNeedsServerSync().observe(host.getLifecycleOwner(), params -> {
            LogUtil.d(TAG, "[OBSERVER] needsServerSync observer triggered, params="
                    + (params != null ? params.deviceNum : "null"));
            TagDevice selectedDevice = host.getSelectedDevice();
            if (params != null && selectedDevice != null) {
                host.setLoadingTrackData(false);
                LogUtil.d(TAG, "[LOCK_RELEASED] isLoadingTrackData set to FALSE (needsServerSync)");
                host.showLoadingDialog();
                String deviceNum = params.deviceNum;
                if (selectedDevice.getDeviceNum() != null) {
                    deviceNum = selectedDevice.getDeviceNum();
                }
                LogUtil.d(TAG, "[OBSERVER] Calling syncTrackDataFromServerAndReload for device=" + deviceNum);
                host.syncTrackDataFromServerAndReload(deviceNum, params.startTime, params.endTime);
            }
        });

        viewModel.getErrorMessage().observe(host.getLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Log.e(TAG, "[OBSERVER] errorMessage: " + error);
            }
        });

        viewModel.getIsSyncingFromServer().observe(host.getLifecycleOwner(), isSyncing ->
                LogUtil.d(TAG, "[OBSERVER] isSyncingFromServer observer triggered, isSyncing=" + isSyncing));

        viewModel.getIsPlaying().observe(host.getLifecycleOwner(), playing -> {
            if (playing != null) {
                host.setIsPlaying(playing);
            }
        });

        viewModel.getCurrentPlayIndex().observe(host.getLifecycleOwner(), index -> {
            if (index != null) {
                host.setCurrentPlayIndex(index);
            }
        });

        viewModel.getPlaySpeed().observe(host.getLifecycleOwner(), speed -> {
            if (speed != null) {
                host.setPlaySpeed(speed);
            }
        });
    }
}
