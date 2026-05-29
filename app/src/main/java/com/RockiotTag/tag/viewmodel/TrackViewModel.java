package com.RockiotTag.tag.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.RockiotTag.tag.CoordinateUtils;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.model.LocationData;
import com.RockiotTag.tag.util.BLETagFilter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrackViewModel extends ViewModel {
    private static final String TAG = "TrackViewModel";
    
    private DatabaseHelper databaseHelper;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private AtomicBoolean isLoadCancelled = new AtomicBoolean(false);
    
    private Calendar selectedDate;
    private List<StayPoint> currentStayPoints = new ArrayList<>();
    private List<LocationRecord> currentLocationRecords = new ArrayList<>();
    
    private final MutableLiveData<List<LocationRecord>> locationRecords = new MutableLiveData<>();
    private final MutableLiveData<List<StayPoint>> stayPoints = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isSyncingFromServer = new MutableLiveData<>(false);
    private final MutableLiveData<SyncParams> needsServerSync = new MutableLiveData<>();
    
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> currentPlayIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> playSpeed = new MutableLiveData<>(1);
    
    public static class SyncParams {
        public final String deviceNum;
        public final long startTime;
        public final long endTime;
        
        public SyncParams(String deviceNum, long startTime, long endTime) {
            this.deviceNum = deviceNum;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
    
    public static class TrackStatistics {
        public final double totalDistanceKm;
        public final int validSegments;
        public final int filteredJumps;
        
        public TrackStatistics(double totalDistanceKm, int validSegments, int filteredJumps) {
            this.totalDistanceKm = totalDistanceKm;
            this.validSegments = validSegments;
            this.filteredJumps = filteredJumps;
        }
    }
    
    private final MutableLiveData<TrackStatistics> statistics = new MutableLiveData<>();
    
    public TrackViewModel() {
    }
    
    public void init(DatabaseHelper dbHelper) {
        this.databaseHelper = dbHelper;
    }
    
    public LiveData<List<LocationRecord>> getLocationRecords() {
        return locationRecords;
    }
    
    public LiveData<List<StayPoint>> getStayPoints() {
        return stayPoints;
    }
    
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<TrackStatistics> getStatistics() {
        return statistics;
    }
    
    public LiveData<SyncParams> getNeedsServerSync() {
        return needsServerSync;
    }
    
    public LiveData<Boolean> getIsSyncingFromServer() {
        return isSyncingFromServer;
    }
    
    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }
    
    public LiveData<Integer> getCurrentPlayIndex() {
        return currentPlayIndex;
    }
    
    public LiveData<Integer> getPlaySpeed() {
        return playSpeed;
    }
    
    public void setLoading(boolean loading) {
        isLoading.postValue(loading);
    }
    
    public void loadTrackData(String deviceId, Calendar date) {
        loadTrackData(deviceId, date, false);
    }
    
    public void loadTrackData(String deviceId, Calendar date, boolean forceSync) {
        if (databaseHelper == null) {
            errorMessage.setValue("Database not initialized");
            isLoading.setValue(false);
            return;
        }
        
        isLoadCancelled.set(true);
        isLoadCancelled = new AtomicBoolean(false);
        
        isLoading.setValue(true);
        this.selectedDate = date;
        
        final AtomicBoolean currentTaskCancelled = isLoadCancelled;
        executor.submit(() -> {
            try {
                long startTime = getDayStartTime(date);
                long endTime = getDayEndTime(date);
                
                if (currentTaskCancelled.get()) {
                    Log.d(TAG, "Task cancelled before database query");
                    return;
                }
                
                List<LocationRecord> records = databaseHelper.getLocationRecords(deviceId, startTime, endTime);
                
                if (currentTaskCancelled.get()) {
                    Log.d(TAG, "Task cancelled after database query");
                    return;
                }
                
                Log.d(TAG, "Loaded " + (records != null ? records.size() : 0) + " records from database");
                
                if (currentTaskCancelled.get()) {
                    Log.d(TAG, "Task cancelled before processing records");
                    return;
                }
                
                if (records != null && !records.isEmpty()) {
                    currentLocationRecords = new ArrayList<>(records);
                    locationRecords.postValue(records);
                    isSyncingFromServer.postValue(false);
                    
                    List<StayPoint> points = generateStayPoints(records);
                    currentStayPoints = points;
                    stayPoints.postValue(points);
                    
                    TrackStatistics stats = calculateStatistics(records);
                    statistics.postValue(stats);
                    
                    Log.d(TAG, "Loaded " + records.size() + " records, " + points.size() + " stay points");
                } else {
                    Log.d(TAG, "No local track data found for device: " + deviceId + ", triggering server sync");
                    isSyncingFromServer.postValue(true);
                    isLoading.postValue(false);
                    needsServerSync.postValue(new SyncParams(deviceId, startTime, endTime));
                    return;
                }
                
                isLoading.postValue(false);
            } catch (Exception e) {
                Log.e(TAG, "Error loading track data: " + e.getMessage(), e);
                errorMessage.postValue("加载失败: " + e.getMessage());
                isLoading.postValue(false);
            }
        });
    }
    
    public void loadTrackDataFromLocal(String deviceId, Calendar date) {
        if (databaseHelper == null) {
            errorMessage.setValue("Database not initialized");
            isLoading.setValue(false);
            return;
        }
        
        isLoading.setValue(true);
        this.selectedDate = date;
        
        executor.submit(() -> {
            try {
                long startTime = getDayStartTime(date);
                long endTime = getDayEndTime(date);
                
                List<LocationRecord> records = databaseHelper.getLocationRecords(deviceId, startTime, endTime);
                
                if (records != null && !records.isEmpty()) {
                    currentLocationRecords = new ArrayList<>(records);
                    locationRecords.postValue(records);
                    isSyncingFromServer.postValue(false);
                    
                    List<StayPoint> points = generateStayPoints(records);
                    currentStayPoints = points;
                    stayPoints.postValue(points);
                    
                    TrackStatistics stats = calculateStatistics(records);
                    statistics.postValue(stats);
                    
                    Log.d(TAG, "Loaded " + records.size() + " records from local, " + points.size() + " stay points");
                } else {
                    locationRecords.postValue(new ArrayList<>());
                    stayPoints.postValue(new ArrayList<>());
                    statistics.postValue(new TrackStatistics(0, 0, 0));
                }
                
                isLoading.postValue(false);
            } catch (Exception e) {
                Log.e(TAG, "Error loading from local: " + e.getMessage(), e);
                errorMessage.postValue("加载失败: " + e.getMessage());
                isLoading.postValue(false);
            }
        });
    }
    
    public List<StayPoint> generateStayPointsFromRecordsWithAccuracy(List<LocationData> records, int accuracyThreshold) {
        return generateStayPointsWithAccuracy(records, accuracyThreshold);
    }
    
    public void startPlayback() {
        isPlaying.postValue(true);
        currentPlayIndex.postValue(0);
    }
    
    public void pausePlayback() {
        isPlaying.postValue(false);
    }
    
    public void stopPlayback() {
        isPlaying.postValue(false);
        currentPlayIndex.postValue(0);
    }
    
    public void seekTo(int index) {
        currentPlayIndex.postValue(index);
    }
    
    public boolean canMoveToNext() {
        Integer currentIndex = currentPlayIndex.getValue();
        if (currentIndex == null) currentIndex = 0;
        return currentIndex < currentStayPoints.size() - 1;
    }
    
    public StayPoint getCurrentStayPoint() {
        Integer currentIndex = currentPlayIndex.getValue();
        if (currentIndex == null || currentStayPoints.isEmpty()) return null;
        if (currentIndex >= currentStayPoints.size()) return null;
        return currentStayPoints.get(currentIndex);
    }
    
    public StayPoint getNextStayPoint() {
        Integer currentIndex = currentPlayIndex.getValue();
        if (currentIndex == null || currentStayPoints.isEmpty()) return null;
        int nextIndex = currentIndex + 1;
        if (nextIndex >= currentStayPoints.size()) return null;
        return currentStayPoints.get(nextIndex);
    }
    
    public long calculateAnimationDuration(int speed) {
        return 2000 / speed;
    }
    
    public void setPlaySpeed(int speed) {
        playSpeed.postValue(speed);
    }
    
    public int getTotalStayPoints() {
        return currentStayPoints.size();
    }
    
    public void moveToNextPoint() {
        Integer currentIndex = currentPlayIndex.getValue();
        if (currentIndex == null) currentIndex = 0;
        int nextIndex = currentIndex + 1;
        if (nextIndex < currentStayPoints.size()) {
            currentPlayIndex.postValue(nextIndex);
        }
    }
    
    public String getCurrentPlayTimeString() {
        StayPoint current = getCurrentStayPoint();
        if (current == null) return "";
        return com.RockiotTag.tag.util.TimeFormatter.formatTimeHM(current.getArriveTime());
    }
    
    public String getCurrentPlayFullTimeString() {
        StayPoint current = getCurrentStayPoint();
        if (current == null) return "";
        return com.RockiotTag.tag.util.TimeFormatter.formatFullTime(current.getArriveTime());
    }
    
    public boolean shouldUpdateAddress() {
        return true;
    }
    
    public double[] getStayPointCoordinates(int index) {
        if (index < 0 || index >= currentStayPoints.size()) return null;
        StayPoint point = currentStayPoints.get(index);
        return new double[]{point.getLatitude(), point.getLongitude()};
    }
    
    public List<StayPoint> generateStayPointsFromRecords(List<LocationData> records) {
        return generateStayPointsWithAccuracy(records, 200);
    }
    
    public void setSyncingCompleted(boolean completed) {
        isSyncingFromServer.postValue(!completed);
        if (completed) {
            isLoading.postValue(false);
        }
    }
    
    private long getDayStartTime(Calendar date) {
        Calendar start = (Calendar) date.clone();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return start.getTimeInMillis();
    }
    
    private long getDayEndTime(Calendar date) {
        Calendar end = (Calendar) date.clone();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
        return end.getTimeInMillis();
    }
    
    private List<StayPoint> generateStayPoints(List<LocationRecord> records) {
        return generateStayPointsWithAccuracy(convertToLocationData(records), 200);
    }
    
    private List<StayPoint> generateStayPointsWithAccuracy(List<LocationData> records, int accuracyThreshold) {
        List<StayPoint> stayPoints = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return stayPoints;
        }
        
        for (LocationData record : records) {
            StayPoint point = new StayPoint(
                record.getLatitude(),
                record.getLongitude(),
                record.getTimestamp(),
                record.getTimestamp()
            );
            point.setOriginalIndex(stayPoints.size() + 1);
            stayPoints.add(point);
        }
        
        return stayPoints;
    }
    
    private List<LocationData> convertToLocationData(List<LocationRecord> records) {
        List<LocationData> data = new ArrayList<>();
        if (records == null) return data;
        
        for (LocationRecord record : records) {
            LocationData ld = new LocationData();
            ld.setId(record.getId());
            ld.setDeviceId(record.getDeviceId());
            ld.setLatitude(record.getLatitude());
            ld.setLongitude(record.getLongitude());
            ld.setTimestamp(record.getTimestamp());
            data.add(ld);
        }
        return data;
    }
    
    private TrackStatistics calculateStatistics(List<LocationRecord> records) {
        if (records == null || records.size() < 2) {
            return new TrackStatistics(0, 0, 0);
        }
        
        double totalDistanceMeters = 0;
        int validSegments = 0;
        int filteredJumps = 0;
        
        for (int i = 1; i < records.size(); i++) {
            LocationRecord prev = records.get(i - 1);
            LocationRecord curr = records.get(i);
            
            if (prev.getLatitude() != 0 && prev.getLongitude() != 0 &&
                curr.getLatitude() != 0 && curr.getLongitude() != 0) {
                
                double segmentDistance = CoordinateUtils.calculateDistanceMeters(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
                );
                
                long timeDiff = curr.getTimestamp() - prev.getTimestamp();
                if (BLETagFilter.isAnomalous(segmentDistance, timeDiff, 
                                            prev.getAccuracy(), curr.getAccuracy())) {
                    filteredJumps++;
                } else {
                    totalDistanceMeters += segmentDistance;
                    validSegments++;
                }
            }
        }
        
        return new TrackStatistics(totalDistanceMeters / 1000.0, validSegments, filteredJumps);
    }
    
    public void clearData() {
        currentLocationRecords = new ArrayList<>();
        currentStayPoints = new ArrayList<>();
        locationRecords.postValue(new ArrayList<>());
        stayPoints.postValue(new ArrayList<>());
        statistics.postValue(new TrackStatistics(0, 0, 0));
        errorMessage.postValue("暂无轨迹数据");
        isLoading.postValue(false);
        isPlaying.postValue(false);
        currentPlayIndex.postValue(0);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        isLoadCancelled.set(true);
        if (executor != null) {
            executor.shutdown();
        }
        Log.d(TAG, "ViewModel cleared, all tasks cancelled");
    }
}