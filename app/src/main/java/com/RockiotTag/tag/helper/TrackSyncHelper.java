package com.RockiotTag.tag.helper;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.model.LocationData;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.util.TimeFormatter;
import com.RockiotTag.tag.viewmodel.TrackViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * TrackActivity 轨迹服务器同步逻辑。
 */
public class TrackSyncHelper {

    private static final String TAG = "TrackSyncHelper";

    public interface Host {
        AppCompatActivity getActivity();
        boolean isFinishing();
        boolean isDestroyed();
        ExecutorService getThreadPool();
        TagDevice getSelectedDevice();
        DatabaseHelper getDatabaseHelper();
        TrackViewModel getViewModel();
        Calendar getSelectedDate();
        Map<String, Boolean> getSyncedDates();
        Map<String, Long> getLastSyncTimestamps();
        void setLoadingTrackData(boolean loading);
        void hideLoading();
        void hideLoadingDialog();
        void showLoadingDialog();
        void clearTrackUI();
        void updatePlaybackInfo(int count);
    }

    private final Host host;

    public TrackSyncHelper(Host host) {
        this.host = host;
    }

    public static long getDayStartTime(Calendar date) {
        Calendar start = (Calendar) date.clone();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return start.getTimeInMillis();
    }

    public static long getDayEndTime(Calendar date) {
        Calendar end = (Calendar) date.clone();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
        return end.getTimeInMillis();
    }

    public void syncTrackDataFromServerAndReload(final String deviceNum, final long startTime, final long endTime) {
        LogUtil.d(TAG, "=== SYNC TRACK DATA START ===");
        LogUtil.d(TAG, "DeviceNum: " + deviceNum);
        TagDevice selectedDevice = host.getSelectedDevice();
        LogUtil.d(TAG, "SelectedDevice ID: " + (selectedDevice != null ? selectedDevice.getDeviceId() : "null"));
        LogUtil.d(TAG, "Time range: " + startTime + " - " + endTime);

        final String savedCustomerCode = selectedDevice != null ? selectedDevice.getCustomerCode() : null;
        String baseUrl = ApiConfig.getMyServerUrl(deviceNum);

        host.getThreadPool().execute(() -> {
            try {
                NewApiService apiService = NewApiService.getInstance();
                List<NewApiService.LocationInfo> locations =
                        fetchLocationsWithCustomerCode(apiService, baseUrl, deviceNum,
                                startTime, endTime, savedCustomerCode, selectedDevice);

                LogUtil.d(TAG, "Got " + (locations != null ? locations.size() : 0) + " locations from server");

                if (locations != null && !locations.isEmpty()) {
                    for (int i = 0; i < Math.min(3, locations.size()); i++) {
                        NewApiService.LocationInfo loc = locations.get(i);
                        LogUtil.d(TAG, "Server location[" + i + "]: lat=" + loc.latitude
                                + ", lng=" + loc.longitude + ", ts=" + loc.timestamp);
                    }
                }

                if (locations != null && !locations.isEmpty()) {
                    processFullSyncResult(deviceNum, locations, selectedDevice);
                } else {
                    LogUtil.d(TAG, "No locations returned from server");
                    host.getActivity().runOnUiThread(() -> {
                        try {
                            host.getViewModel().setSyncingCompleted(false);
                            host.hideLoading();
                            host.setLoadingTrackData(false);
                            host.updatePlaybackInfo(0);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in no data handling: " + e.getMessage(), e);
                            host.hideLoading();
                            host.setLoadingTrackData(false);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error syncing track data: " + e.getMessage(), e);
                host.getActivity().runOnUiThread(() -> {
                    try {
                        host.getViewModel().setSyncingCompleted(false);
                        host.hideLoading();
                        host.setLoadingTrackData(false);
                    } catch (Exception ex) {
                        Log.e(TAG, "Error in sync error handling: " + ex.getMessage(), ex);
                        host.hideLoading();
                        host.setLoadingTrackData(false);
                    }
                });
            }
        });
    }

    public void syncTrackFromServer(String deviceNum, long startTime, long endTime) {
        LogUtil.d(TAG, "syncTrackFromServer: deviceNum=" + deviceNum
                + ", startTime=" + startTime + ", endTime=" + endTime);

        if (host.isFinishing() || host.isDestroyed()) {
            host.getViewModel().setLoading(false);
            host.hideLoading();
            host.setLoadingTrackData(false);
            return;
        }

        TagDevice selectedDevice = host.getSelectedDevice();
        final String savedCustomerCode = selectedDevice != null ? selectedDevice.getCustomerCode() : null;

        host.getThreadPool().execute(() -> {
            try {
                NewApiService apiService = NewApiService.getInstance();
                List<NewApiService.LocationInfo> serverRecords =
                        fetchLocationsWithCustomerCode(apiService,
                                ApiConfig.getMyServerUrl(deviceNum), deviceNum,
                                startTime, endTime, savedCustomerCode, selectedDevice);

                LogUtil.d(TAG, "Received " + (serverRecords != null ? serverRecords.size() : 0) + " records from server");

                if (serverRecords != null && !serverRecords.isEmpty()) {
                    DatabaseHelper db = host.getDatabaseHelper();
                    for (NewApiService.LocationInfo info : serverRecords) {
                        LocationRecord record = new LocationRecord(
                                info.deviceNum, info.latitude, info.longitude, info.timestamp);
                        db.addLocationRecord(record);
                    }
                    LogUtil.d(TAG, "Saved " + serverRecords.size() + " records to local database");

                    String dateKey = TimeFormatter.formatDate(host.getSelectedDate().getTimeInMillis());
                    host.getSyncedDates().put(dateKey, true);
                    host.getLastSyncTimestamps().put(dateKey, System.currentTimeMillis());

                    host.getActivity().runOnUiThread(() -> {
                        if (!host.isFinishing() && !host.isDestroyed()) {
                            host.getViewModel().loadTrackDataFromLocal(deviceNum, host.getSelectedDate());
                        }
                    });
                } else {
                    host.getActivity().runOnUiThread(() -> {
                        host.getViewModel().setLoading(false);
                        host.hideLoading();
                        host.setLoadingTrackData(false);
                        if (!host.isFinishing() && !host.isDestroyed()) {
                            host.clearTrackUI();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error syncing from server: " + e.getMessage(), e);
                host.getActivity().runOnUiThread(() -> {
                    host.getViewModel().setLoading(false);
                    host.hideLoading();
                    host.setLoadingTrackData(false);
                });
            }
        });
    }

    public void checkServerForNewDataAsync() {
        TagDevice selectedDevice = host.getSelectedDevice();
        if (selectedDevice == null) {
            Log.w(TAG, "No device selected, skip server check");
            return;
        }

        String deviceNum = selectedDevice.getDeviceNum() != null
                ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
        final String savedCustomerCode = selectedDevice.getCustomerCode();
        Calendar selectedDate = host.getSelectedDate();

        host.getThreadPool().execute(() -> {
            try {
                long startTime = getDayStartTime(selectedDate);
                long endTime = getDayEndTime(selectedDate);
                DatabaseHelper db = host.getDatabaseHelper();
                List<LocationRecord> localRecords = db.getLocationRecords(deviceNum, startTime, endTime);
                long localLatestTime = 0;
                if (localRecords != null && !localRecords.isEmpty()) {
                    localLatestTime = localRecords.get(localRecords.size() - 1).getTimestamp();
                }

                NewApiService apiService = NewApiService.getInstance();
                NewApiService.DeviceInfo latestInfo = fetchLatestWithCustomerCode(
                        apiService, deviceNum, savedCustomerCode, selectedDevice);

                if (latestInfo != null && latestInfo.timestamp > 0) {
                    long serverLatestTime = latestInfo.timestamp;
                    if (serverLatestTime > localLatestTime) {
                        LogUtil.d(TAG, "Server has new data, syncing...");
                        host.getActivity().runOnUiThread(() -> {
                            if (!host.isFinishing() && !host.isDestroyed()) {
                                host.showLoadingDialog();
                                syncTrackFromServer(deviceNum, startTime, endTime);
                            }
                        });
                    } else {
                        LogUtil.d(TAG, "Server has no new data, using local cache");
                    }
                } else {
                    Log.w(TAG, "Failed to get latest data from server or no timestamp");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking server for new data: " + e.getMessage(), e);
            }
        });
    }

    private void processFullSyncResult(String deviceNum, List<NewApiService.LocationInfo> locations,
                                       TagDevice selectedDevice) {
        DatabaseHelper db = host.getDatabaseHelper();
        int addedCount = 0;
        int skippedCount = 0;
        int invalidCount = 0;

        for (NewApiService.LocationInfo loc : locations) {
            if (loc.latitude != 0 && loc.longitude != 0 && loc.timestamp > 0) {
                String recordDeviceNum = selectedDevice.getDeviceNum() != null
                        ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
                LocationRecord record = new LocationRecord(
                        recordDeviceNum, loc.latitude, loc.longitude, loc.timestamp);

                List<LocationRecord> existingRecords = db.getLocationRecords(
                        deviceNum, loc.timestamp - 1000, loc.timestamp + 1000);

                if (existingRecords == null || existingRecords.isEmpty()) {
                    db.addLocationRecord(record);
                    addedCount++;
                } else {
                    skippedCount++;
                }
            } else {
                invalidCount++;
            }
        }

        final String finalDeviceNum = selectedDevice.getDeviceNum() != null
                ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId();
        final int finalAddedCount = addedCount;
        final int finalSkippedCount = skippedCount;
        final int finalInvalidCount = invalidCount;
        final int totalFromServer = locations.size();

        List<LocationData> syncedLocationData = new ArrayList<>();
        for (NewApiService.LocationInfo loc : locations) {
            if (loc.latitude != 0 && loc.longitude != 0 && loc.timestamp > 0) {
                LocationData data = new LocationData();
                data.setDeviceId(finalDeviceNum);
                data.setLatitude(loc.latitude);
                data.setLongitude(loc.longitude);
                data.setTimestamp(loc.timestamp);
                syncedLocationData.add(data);
            }
        }

        List<StayPoint> generatedStayPoints =
                host.getViewModel().generateStayPointsFromRecords(syncedLocationData);
        final int finalSyncedCount = syncedLocationData.size();
        final int finalStayPointsCount = generatedStayPoints.size();

        LogUtil.d(TAG, "Sync summary: added=" + finalAddedCount + ", skipped=" + finalSkippedCount
                + ", invalid=" + finalInvalidCount + ", total=" + totalFromServer
                + ", syncedData=" + finalSyncedCount + ", stayPoints=" + finalStayPointsCount);

        host.getActivity().runOnUiThread(() -> {
            try {
                if (finalAddedCount > 0 || finalSyncedCount > 0) {
                    host.getViewModel().setSyncingCompleted(true);
                    host.getViewModel().loadTrackData(finalDeviceNum, host.getSelectedDate());
                } else {
                    host.getViewModel().setSyncingCompleted(false);
                    host.hideLoading();
                    host.setLoadingTrackData(false);
                    host.updatePlaybackInfo(0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in sync result processing: " + e.getMessage(), e);
                host.hideLoading();
                host.setLoadingTrackData(false);
            }
        });
    }

    private List<NewApiService.LocationInfo> fetchLocationsWithCustomerCode(
            NewApiService apiService, String baseUrl, String deviceNum,
            long startTime, long endTime, String savedCustomerCode, TagDevice selectedDevice) {

        List<NewApiService.LocationInfo> locations = null;
        String matchedCustomerCode = null;

        if (savedCustomerCode != null && !savedCustomerCode.isEmpty()) {
            locations = apiService.getLocations(baseUrl, deviceNum, startTime, endTime, savedCustomerCode);
            if (locations != null && !locations.isEmpty()) {
                matchedCustomerCode = savedCustomerCode;
            } else {
                locations = null;
            }
        }

        if (locations == null) {
            for (String customerCode : ApiConfig.getAllCustomerConfigs().keySet()) {
                if (customerCode.equals(savedCustomerCode)) continue;
                locations = apiService.getLocations(baseUrl, deviceNum, startTime, endTime, customerCode);
                if (locations != null && !locations.isEmpty()) {
                    matchedCustomerCode = customerCode;
                    break;
                }
                locations = null;
            }
        }

        persistCustomerCode(selectedDevice, matchedCustomerCode);
        return locations;
    }

    private NewApiService.DeviceInfo fetchLatestWithCustomerCode(
            NewApiService apiService, String deviceNum,
            String savedCustomerCode, TagDevice selectedDevice) {

        NewApiService.DeviceInfo latestInfo = null;
        String matchedCustomerCode = null;

        if (savedCustomerCode != null && !savedCustomerCode.isEmpty()) {
            latestInfo = apiService.getDeviceLatest(deviceNum, savedCustomerCode);
            if (latestInfo != null && latestInfo.deviceNum != null && !latestInfo.deviceNum.isEmpty()) {
                matchedCustomerCode = savedCustomerCode;
            } else {
                latestInfo = null;
            }
        }

        if (latestInfo == null) {
            for (String customerCode : ApiConfig.getAllCustomerConfigs().keySet()) {
                if (customerCode.equals(savedCustomerCode)) continue;
                latestInfo = apiService.getDeviceLatest(deviceNum, customerCode);
                if (latestInfo != null && latestInfo.deviceNum != null && !latestInfo.deviceNum.isEmpty()) {
                    matchedCustomerCode = customerCode;
                    break;
                }
                latestInfo = null;
            }
        }

        persistCustomerCode(selectedDevice, matchedCustomerCode);
        return latestInfo;
    }

    private void persistCustomerCode(TagDevice selectedDevice, String matchedCustomerCode) {
        if (matchedCustomerCode != null && selectedDevice != null
                && !matchedCustomerCode.equals(selectedDevice.getCustomerCode())) {
            selectedDevice.setCustomerCode(matchedCustomerCode);
            host.getDatabaseHelper().addDevice(selectedDevice);
            LogUtil.d(TAG, "Updated device customerCode to: " + matchedCustomerCode);
        }
    }
}
