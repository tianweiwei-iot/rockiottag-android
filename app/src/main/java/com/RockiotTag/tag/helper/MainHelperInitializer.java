package com.RockiotTag.tag.helper;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.BLEManager;
import com.RockiotTag.tag.CrowdSourcingManager;
import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.DeviceListFragment;
import com.RockiotTag.tag.MapManager;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.ProfileFragment;
import com.RockiotTag.tag.UnboundDeviceManager;
import com.RockiotTag.tag.integration.LocationOptimizationManager;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.viewmodel.MainViewModel;
import com.RockiotTag.tag.viewmodel.MapViewModel;
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.google.android.gms.maps.SupportMapFragment;

/**
 * Centralizes MainActivity helper initialization (MVVM refactor).
 */
public final class MainHelperInitializer {

    private MainHelperInitializer() {
    }

    public interface Host {
        AppCompatActivity getActivity();

        AMap getAMap();
        void setAMap(AMap map);
        MapView getMapView();
        SupportMapFragment getGoogleMapFragment();
        MapManager getMapManager();
        void setMapManager(MapManager manager);
        IMapAdapter getMapAdapter();
        void setMapAdapter(IMapAdapter adapter);

        MainViewModel getViewModel();
        MapViewModel getMapViewModel();

        DatabaseHelper getDatabaseHelper();
        NewApiService getApiService();
        UnboundDeviceManager getUnboundDeviceManager();
        LocationOptimizationManager getLocationOptimizationManager();
        BLEManager getBleManager();
        CrowdSourcingManager getCrowdSourcingManager();

        MainCrowdSourceMapHelper getCrowdSourceMapHelper();
        void setCrowdSourceMapHelper(MainCrowdSourceMapHelper helper);
        MainDialogHelper getDialogHelper();
        void setDialogHelper(MainDialogHelper helper);
        MainThemeHelper getThemeHelper();
        void setThemeHelper(MainThemeHelper helper);
        MainBleHelper getBleHelper();
        void setBleHelper(MainBleHelper helper);
        MainAuthHelper getAuthHelper();
        void setAuthHelper(MainAuthHelper helper);
        MainMapHelper getMapHelper();
        void setMapHelper(MainMapHelper helper);
        MainLocationHelper getLocationHelper();
        void setLocationHelper(MainLocationHelper helper);
        MainMapInitHelper getMapInitHelper();
        void setMapInitHelper(MainMapInitHelper helper);
        MainDeviceSelectionHelper getDeviceSelectionHelper();
        void setDeviceSelectionHelper(MainDeviceSelectionHelper helper);
        MainDeviceDisplayHelper getDeviceDisplayHelper();
        void setDeviceDisplayHelper(MainDeviceDisplayHelper helper);
        MainDeviceRefreshHelper getDeviceRefreshHelper();
        void setDeviceRefreshHelper(MainDeviceRefreshHelper helper);
        MainSelectDeviceHelper getSelectDeviceHelper();
        void setSelectDeviceHelper(MainSelectDeviceHelper helper);

        double getCurrentLatitude();
        void setCurrentLatitude(double lat);
        double getCurrentLongitude();
        void setCurrentLongitude(double lng);
        Object getCurrentLocationMarker();
        void setCurrentLocationMarker(Object marker);
        Object getDeviceLocationMarker();
        void setDeviceLocationMarker(Object marker);
        boolean isFirstLocation();
        void setFirstLocation(boolean first);
        boolean isUserLocated();
        void setUserLocated(boolean located);
        boolean isPendingDeviceSelection();
        void setPendingDeviceSelection(boolean pending);
        long getLastUserInteractionTime();
        void setLastUserInteractionTime(long time);

        TagDevice getSelectedDevice();
        void setSelectedDevice(TagDevice device);
        double getLastRecordedLatitude();
        void setLastRecordedLatitude(double v);
        double getLastRecordedLongitude();
        void setLastRecordedLongitude(double v);
        long getLastRecordedTimestamp();
        void setLastRecordedTimestamp(long v);
        double getLastAddressLatitude();
        void setLastAddressLatitude(double v);
        double getLastAddressLongitude();
        void setLastAddressLongitude(double v);
        long getLastAddressUpdateTime();
        void setLastAddressUpdateTime(long v);
        int getDeviceRefreshSequence();
        int incrementDeviceRefreshSequence();
        boolean isRefreshInProgress();
        void setRefreshInProgress(boolean inProgress);

        com.RockiotTag.tag.map.google.GoogleLocationService getGoogleLocationService();
        void setGoogleLocationService(com.RockiotTag.tag.map.google.GoogleLocationService service);
        com.RockiotTag.tag.location.SystemLocationService getSystemLocationService();
        void setSystemLocationService(com.RockiotTag.tag.location.SystemLocationService service);

        View getScanningIndicator();
        View getBottomNavigation();
        View getBottomInfo();
        TextView getBatteryLevelText();
        TextView getDeviceAddressText();
        TextView getUpdateTimeText();
        ImageButton getRefreshBtn();
        int getCurrentTab();
        int getScanIntensityLevel();
        void setScanIntensityLevel(int level);

        DeviceListFragment getDeviceListFragment();
        ProfileFragment getProfileFragment();

        com.RockiotTag.tag.util.SafeHandler getTrackRefreshHandler();
        void setTrackRefreshHandler(com.RockiotTag.tag.util.SafeHandler handler);
        Runnable getTrackRefreshRunnable();
        void setTrackRefreshRunnable(Runnable runnable);

        void changeLanguage(String languageCode);
        void onMapProviderChanged(String newMapProvider, int toastMessageResId);
        void showBottomInfo();
        void selectDevice(TagDevice device);
        void updateDeviceNameWithTag(String name, String tag);
        void showCoordinatesAndGeocode(double latitude, double longitude, boolean forceRefresh);
        void moveCameraToDevicePosition(double latitude, double longitude);
        void resetAddressCache();
        void updateDeviceUIDefault();
        void refreshMapWithCurrentDevice(boolean force);
        void updateCustomCompassRotation(float bearing);
        void autoLocateToDevice();
        void locateToDevicePosition(boolean setUserLocated);
        void performDeviceRefresh(boolean showToast);
        void updateCurrentLocationOnMap();
        void notifyFragmentsThemeChanged(boolean isDarkMode);
        void requestPermissions(String[] permissions, int requestCode);
        void refreshProfileFragment();
        void refreshDeviceListFragment();
        void clearMapMarkers();
        void resetDeviceUIToDefault();
        void selectFirstDeviceAndRefresh();
        void invalidateRefreshRequests();
        void onScanIntensitySelected(int level);
        void onLoginSuccess(String token, String username, String nickname, String email);
        void onUpdateMapMarker(TagDevice device);
        String getVersionName();
        int getVersionCode();
        String getString(int resId);
        String getString(int resId, Object... args);
    }

    public static void initAll(Host host) {
        initMapHelper(host);
        initLocationHelper(host);
        initDeviceSelectionHelper(host);
        initMapInitHelper(host);
        initCoreHelpers(host);
    }

    public static void initCoreHelpers(Host host) {
        host.setCrowdSourceMapHelper(new MainCrowdSourceMapHelper(new MainCrowdSourceMapHelper.Host() {
            @Override public AMap getAMap() { return host.getAMap(); }
            @Override public AppCompatActivity getActivity() { return host.getActivity(); }
            @Override public double getCurrentLatitude() { return host.getCurrentLatitude(); }
            @Override public double getCurrentLongitude() { return host.getCurrentLongitude(); }
            @Override public Object getCurrentLocationMarker() { return host.getCurrentLocationMarker(); }
            @Override public void setCurrentLocationMarker(Object marker) { host.setCurrentLocationMarker(marker); }
        }));

        host.setDialogHelper(new MainDialogHelper(host.getActivity(), new MainDialogHelper.DialogCallbacks() {
            @Override
            public MapManager getMapManager() { return host.getMapManager(); }
            @Override
            public DatabaseHelper getDatabaseHelper() { return host.getDatabaseHelper(); }
            @Override
            public void onLanguageChanged(String languageCode) {
                if (languageCode != null) {
                    host.changeLanguage(languageCode);
                }
            }
            @Override
            public void onMapProviderChanged(String newMapProvider, int toastMessageResId) {
                host.onMapProviderChanged(newMapProvider, toastMessageResId);
            }
            @Override
            public LocationOptimizationManager getLocationOptimizationManager() {
                return host.getLocationOptimizationManager();
            }
            @Override
            public View getScanningIndicator() { return host.getScanningIndicator(); }
            @Override
            public int getScanIntensityLevel() { return host.getScanIntensityLevel(); }
            @Override
            public void onScanIntensitySelected(int level) {
                host.onScanIntensitySelected(level);
            }
            @Override
            public void onLoginSuccess(String token, String username, String nickname, String email) {
                host.onLoginSuccess(token, username, nickname, email);
            }
            @Override
            public void refreshProfileFragment() {
                host.refreshProfileFragment();
            }
            @Override
            public void requestPermissions(String[] permissions, int requestCode) {
                host.requestPermissions(permissions, requestCode);
            }
            @Override
            public String getVersionName() { return host.getVersionName(); }
            @Override
            public int getVersionCode() { return host.getVersionCode(); }
        }));

        host.setThemeHelper(new MainThemeHelper(host.getActivity(), new MainThemeHelper.ThemeCallbacks() {
            @Override
            public View getBottomNavigation() { return host.getBottomNavigation(); }
            @Override
            public View getBottomInfo() { return host.getBottomInfo(); }
            @Override
            public TextView getBatteryLevelText() { return host.getBatteryLevelText(); }
            @Override
            public TextView getDeviceAddressText() { return host.getDeviceAddressText(); }
            @Override
            public TextView getUpdateTimeText() { return host.getUpdateTimeText(); }
            @Override
            public int getCurrentTab() { return host.getCurrentTab(); }
            @Override
            public MapManager getMapManager() { return host.getMapManager(); }
            @Override
            public AMap getAMap() { return host.getAMap(); }
            @Override
            public void notifyFragmentsThemeChanged(boolean isDarkMode) {
                host.notifyFragmentsThemeChanged(isDarkMode);
            }
        }));

        host.setBleHelper(new MainBleHelper(host.getActivity(), new MainBleHelper.BleCallbacks() {
            @Override
            public LocationOptimizationManager getLocationOptimizationManager() {
                return host.getLocationOptimizationManager();
            }
            @Override
            public View getScanningIndicator() { return host.getScanningIndicator(); }
            @Override
            public BLEManager getBleManager() { return host.getBleManager(); }
            @Override
            public DatabaseHelper getDatabaseHelper() { return host.getDatabaseHelper(); }
            @Override
            public CrowdSourcingManager getCrowdSourcingManager() { return host.getCrowdSourcingManager(); }
            @Override
            public void onUpdateMapMarker(TagDevice device) {
                host.onUpdateMapMarker(device);
            }
        }));

        host.setAuthHelper(new MainAuthHelper(host.getActivity(), new MainAuthHelper.AuthCallbacks() {
            @Override
            public DatabaseHelper getDatabaseHelper() { return host.getDatabaseHelper(); }
            @Override
            public MainViewModel getViewModel() { return host.getViewModel(); }
            @Override
            public MapViewModel getMapViewModel() { return host.getMapViewModel(); }
            @Override
            public TagDevice getSelectedDevice() { return host.getSelectedDevice(); }
            @Override
            public void setSelectedDevice(TagDevice device) { host.setSelectedDevice(device); }
            @Override
            public void refreshDeviceListFragment() {
                host.refreshDeviceListFragment();
            }
            @Override
            public void refreshProfileFragment() {
                host.refreshProfileFragment();
            }
            @Override
            public void clearMapMarkers() {
                host.clearMapMarkers();
            }
            @Override
            public void resetDeviceUIToDefault() {
                host.resetDeviceUIToDefault();
            }
            @Override
            public void selectFirstDeviceAndRefresh() {
                host.selectFirstDeviceAndRefresh();
            }
            @Override
            public void invalidateRefreshRequests() {
                host.invalidateRefreshRequests();
            }
        }));

        initDeviceDisplayHelper(host);
        initDeviceRefreshHelper(host);
    }

    public static void initMapHelper(Host host) {
        host.setMapHelper(new MainMapHelper(new MainMapHelper.Host() {
            @Override
            public AppCompatActivity getActivity() { return host.getActivity(); }
            @Override
            public IMapAdapter getMapAdapter() { return host.getMapAdapter(); }
            @Override
            public AMap getAMap() { return host.getAMap(); }
            @Override
            public MainViewModel getViewModel() { return host.getViewModel(); }
            @Override
            public MapViewModel getMapViewModel() { return host.getMapViewModel(); }
            @Override
            public TagDevice getSelectedDevice() { return host.getSelectedDevice(); }
            @Override
            public Object getDeviceLocationMarker() { return host.getDeviceLocationMarker(); }
            @Override
            public void setDeviceLocationMarker(Object marker) { host.setDeviceLocationMarker(marker); }
            @Override
            public double getCurrentLatitude() { return host.getCurrentLatitude(); }
            @Override
            public double getCurrentLongitude() { return host.getCurrentLongitude(); }
            @Override
            public boolean isUserLocated() { return host.isUserLocated(); }
            @Override
            public void setUserLocated(boolean located) { host.setUserLocated(located); }
            @Override
            public TextView getUpdateTimeText() { return host.getUpdateTimeText(); }
            @Override
            public void showBottomInfo() { host.showBottomInfo(); }
            @Override
            public void showCoordinatesAndGeocode(double latitude, double longitude, boolean forceRefresh) {
                host.showCoordinatesAndGeocode(latitude, longitude, forceRefresh);
            }
        }));
    }

    public static void initLocationHelper(Host host) {
        host.setLocationHelper(new MainLocationHelper(new MainLocationHelper.Host() {
            @Override
            public AppCompatActivity getActivity() { return host.getActivity(); }
            @Override
            public IMapAdapter getMapAdapter() { return host.getMapAdapter(); }
            @Override
            public MainViewModel getViewModel() { return host.getViewModel(); }
            @Override
            public TagDevice getSelectedDevice() { return host.getSelectedDevice(); }
            @Override
            public double getCurrentLatitude() { return host.getCurrentLatitude(); }
            @Override
            public double getCurrentLongitude() { return host.getCurrentLongitude(); }
            @Override
            public void setCurrentLatitude(double lat) { host.setCurrentLatitude(lat); }
            @Override
            public void setCurrentLongitude(double lng) { host.setCurrentLongitude(lng); }
            @Override
            public boolean isFirstLocation() { return host.isFirstLocation(); }
            @Override
            public void setFirstLocation(boolean first) { host.setFirstLocation(first); }
            @Override
            public void setGoogleLocationService(
                    com.RockiotTag.tag.map.google.GoogleLocationService service) {
                host.setGoogleLocationService(service);
            }
            @Override
            public com.RockiotTag.tag.map.google.GoogleLocationService getGoogleLocationService() {
                return host.getGoogleLocationService();
            }
            @Override
            public void setSystemLocationService(
                    com.RockiotTag.tag.location.SystemLocationService service) {
                host.setSystemLocationService(service);
            }
            @Override
            public com.RockiotTag.tag.location.SystemLocationService getSystemLocationService() {
                return host.getSystemLocationService();
            }
            @Override
            public void onLocationUpdated() { host.updateCurrentLocationOnMap(); }
        }));
    }

    public static void initMapInitHelper(Host host) {
        host.setMapInitHelper(new MainMapInitHelper(new MainMapInitHelper.Host() {
            @Override
            public AppCompatActivity getActivity() { return host.getActivity(); }
            @Override
            public MapView getMapView() { return host.getMapView(); }
            @Override
            public SupportMapFragment getGoogleMapFragment() { return host.getGoogleMapFragment(); }
            @Override
            public void setMapManager(MapManager manager) { host.setMapManager(manager); }
            @Override
            public void setMapAdapter(IMapAdapter adapter) { host.setMapAdapter(adapter); }
            @Override
            public void setAMap(AMap map) { host.setAMap(map); }
            @Override
            public AMap getAMap() { return host.getAMap(); }
            @Override
            public IMapAdapter getMapAdapter() { return host.getMapAdapter(); }
            @Override
            public MapManager getMapManager() { return host.getMapManager(); }
            @Override
            public void onMapReadyRefreshDevice() {
                host.refreshMapWithCurrentDevice(false);
            }
            @Override
            public void restoreSelectedDeviceForMapInit() {
                if (host.getDeviceSelectionHelper() != null) {
                    host.getDeviceSelectionHelper().restoreSelectedDeviceForMapInit();
                }
            }
            @Override
            public void updateCustomCompassRotation(float bearing) {
                host.updateCustomCompassRotation(bearing);
            }
            @Override
            public void setLastUserInteractionTime(long time) { host.setLastUserInteractionTime(time); }
            @Override
            public void autoLocateToDevice() { host.autoLocateToDevice(); }
        }));
    }

    public static void initDeviceSelectionHelper(Host host) {
        host.setDeviceSelectionHelper(new MainDeviceSelectionHelper(new MainDeviceSelectionHelper.Host() {
            @Override
            public AppCompatActivity getActivity() { return host.getActivity(); }
            @Override
            public DatabaseHelper getDatabaseHelper() { return host.getDatabaseHelper(); }
            @Override
            public MainViewModel getViewModel() { return host.getViewModel(); }
            @Override
            public NewApiService getApiService() { return host.getApiService(); }
            @Override
            public UnboundDeviceManager getUnboundDeviceManager() { return host.getUnboundDeviceManager(); }
            @Override
            public LocationOptimizationManager getLocationOptimizationManager() {
                return host.getLocationOptimizationManager();
            }
            @Override
            public IMapAdapter getMapAdapter() { return host.getMapAdapter(); }
            @Override
            public MapManager getMapManager() { return host.getMapManager(); }
            @Override
            public TagDevice getSelectedDevice() { return host.getSelectedDevice(); }
            @Override
            public void setSelectedDevice(TagDevice device) { host.setSelectedDevice(device); }
            @Override
            public Object getDeviceLocationMarker() { return host.getDeviceLocationMarker(); }
            @Override
            public void setDeviceLocationMarker(Object marker) { host.setDeviceLocationMarker(marker); }
            @Override
            public void setPendingDeviceSelection(boolean pending) { host.setPendingDeviceSelection(pending); }
            @Override
            public void setUserLocated(boolean located) { host.setUserLocated(located); }
            @Override
            public void resetAddressCache() { host.resetAddressCache(); }
            @Override
            public void updateDeviceNameWithTag(String name, String tag) {
                host.updateDeviceNameWithTag(name, tag);
            }
            @Override
            public void showBottomInfo() { host.showBottomInfo(); }
            @Override
            public void updateDeviceUIDefault() { host.updateDeviceUIDefault(); }
            @Override
            public void showCoordinatesAndGeocode(double lat, double lng, boolean force) {
                host.showCoordinatesAndGeocode(lat, lng, force);
            }
            @Override
            public TextView getDeviceAddressText() { return host.getDeviceAddressText(); }
            @Override
            public TextView getBatteryLevelText() { return host.getBatteryLevelText(); }
            @Override
            public TextView getUpdateTimeText() { return host.getUpdateTimeText(); }
            @Override
            public double getLastRecordedLatitude() { return host.getLastRecordedLatitude(); }
            @Override
            public void setLastRecordedLatitude(double v) { host.setLastRecordedLatitude(v); }
            @Override
            public double getLastRecordedLongitude() { return host.getLastRecordedLongitude(); }
            @Override
            public void setLastRecordedLongitude(double v) { host.setLastRecordedLongitude(v); }
            @Override
            public long getLastRecordedTimestamp() { return host.getLastRecordedTimestamp(); }
            @Override
            public void setLastRecordedTimestamp(long v) { host.setLastRecordedTimestamp(v); }
            @Override
            public String getString(int resId) { return host.getString(resId); }
            @Override
            public String getString(int resId, Object... args) {
                return host.getString(resId, args);
            }
        }));
    }

    private static void initDeviceDisplayHelper(Host host) {
        host.setDeviceDisplayHelper(new MainDeviceDisplayHelper(new MainDeviceDisplayHelper.Host() {
            @Override
            public AppCompatActivity getActivity() { return host.getActivity(); }
            @Override
            public DatabaseHelper getDatabaseHelper() { return host.getDatabaseHelper(); }
            @Override
            public MapViewModel getMapViewModel() { return host.getMapViewModel(); }
            @Override
            public IMapAdapter getMapAdapter() { return host.getMapAdapter(); }
            @Override
            public AMap getAMap() { return host.getAMap(); }
            @Override
            public TagDevice getSelectedDevice() { return host.getSelectedDevice(); }
            @Override
            public Object getDeviceLocationMarker() { return host.getDeviceLocationMarker(); }
            @Override
            public void setDeviceLocationMarker(Object marker) { host.setDeviceLocationMarker(marker); }
            @Override
            public TextView getDeviceAddressText() { return host.getDeviceAddressText(); }
            @Override
            public TextView getBatteryLevelText() { return host.getBatteryLevelText(); }
            @Override
            public TextView getUpdateTimeText() { return host.getUpdateTimeText(); }
            @Override
            public double getLastAddressLatitude() { return host.getLastAddressLatitude(); }
            @Override
            public void setLastAddressLatitude(double v) { host.setLastAddressLatitude(v); }
            @Override
            public double getLastAddressLongitude() { return host.getLastAddressLongitude(); }
            @Override
            public void setLastAddressLongitude(double v) { host.setLastAddressLongitude(v); }
            @Override
            public long getLastAddressUpdateTime() { return host.getLastAddressUpdateTime(); }
            @Override
            public void setLastAddressUpdateTime(long v) { host.setLastAddressUpdateTime(v); }
            @Override
            public void showBottomInfo() { host.showBottomInfo(); }
            @Override
            public void updateDeviceNameWithTag(String name, String tag) {
                host.updateDeviceNameWithTag(name, tag);
            }
            @Override
            public void moveCameraToDevicePosition(double latitude, double longitude) {
                host.moveCameraToDevicePosition(latitude, longitude);
            }
            @Override
            public void showCoordinatesAndGeocode(double latitude, double longitude, boolean forceRefresh) {
                host.showCoordinatesAndGeocode(latitude, longitude, forceRefresh);
            }
        }));
    }

    private static void initDeviceRefreshHelper(Host host) {
        host.setDeviceRefreshHelper(new MainDeviceRefreshHelper(new MainDeviceRefreshHelper.Host() {
            @Override
            public AppCompatActivity getActivity() { return host.getActivity(); }
            @Override
            public MainViewModel getViewModel() { return host.getViewModel(); }
            @Override
            public DatabaseHelper getDatabaseHelper() { return host.getDatabaseHelper(); }
            @Override
            public TagDevice getSelectedDevice() { return host.getSelectedDevice(); }
            @Override
            public void setSelectedDevice(TagDevice device) { host.setSelectedDevice(device); }
            @Override
            public ImageButton getRefreshBtn() { return host.getRefreshBtn(); }
            @Override
            public int incrementDeviceRefreshSequence() { return host.incrementDeviceRefreshSequence(); }
            @Override
            public int getDeviceRefreshSequence() { return host.getDeviceRefreshSequence(); }
            @Override
            public boolean isRefreshInProgress() { return host.isRefreshInProgress(); }
            @Override
            public void setRefreshInProgress(boolean inProgress) { host.setRefreshInProgress(inProgress); }
            @Override
            public double getLastRecordedLatitude() { return host.getLastRecordedLatitude(); }
            @Override
            public void setLastRecordedLatitude(double v) { host.setLastRecordedLatitude(v); }
            @Override
            public double getLastRecordedLongitude() { return host.getLastRecordedLongitude(); }
            @Override
            public void setLastRecordedLongitude(double v) { host.setLastRecordedLongitude(v); }
            @Override
            public long getLastRecordedTimestamp() { return host.getLastRecordedTimestamp(); }
            @Override
            public void setLastRecordedTimestamp(long v) { host.setLastRecordedTimestamp(v); }
            @Override
            public void updateDeviceUIWithoutCameraMove(NewApiService.DeviceInfo deviceInfo) {
                if (host.getDeviceDisplayHelper() != null) {
                    host.getDeviceDisplayHelper().updateDeviceUIWithoutCameraMove(deviceInfo);
                }
            }
            @Override
            public com.RockiotTag.tag.util.SafeHandler getTrackRefreshHandler() {
                return host.getTrackRefreshHandler();
            }
            @Override
            public void setTrackRefreshHandler(com.RockiotTag.tag.util.SafeHandler handler) {
                host.setTrackRefreshHandler(handler);
            }
            @Override
            public Runnable getTrackRefreshRunnable() { return host.getTrackRefreshRunnable(); }
            @Override
            public void setTrackRefreshRunnable(Runnable runnable) { host.setTrackRefreshRunnable(runnable); }
            @Override
            public NewApiService getApiService() { return host.getApiService(); }
        }));

        host.setSelectDeviceHelper(new MainSelectDeviceHelper(new MainSelectDeviceHelper.Host() {
            @Override public AppCompatActivity getActivity() { return host.getActivity(); }
            @Override public MainViewModel getViewModel() { return host.getViewModel(); }
            @Override public MapViewModel getMapViewModel() { return host.getMapViewModel(); }
            @Override public LocationOptimizationManager getLocationOptimizationManager() {
                return host.getLocationOptimizationManager();
            }
            @Override public IMapAdapter getMapAdapter() { return host.getMapAdapter(); }
            @Override public MapManager getMapManager() { return host.getMapManager(); }
            @Override public TagDevice getSelectedDevice() { return host.getSelectedDevice(); }
            @Override public void setSelectedDevice(TagDevice device) { host.setSelectedDevice(device); }
            @Override public int incrementDeviceRefreshSequence() { return host.incrementDeviceRefreshSequence(); }
            @Override public void resetAddressCache() { host.resetAddressCache(); }
            @Override public void setPendingDeviceSelection(boolean pending) { host.setPendingDeviceSelection(pending); }
            @Override public boolean isUserLocated() { return host.isUserLocated(); }
            @Override public void setUserLocated(boolean located) { host.setUserLocated(located); }
            @Override public void updateDeviceNameWithTag(String name, String tag) {
                host.updateDeviceNameWithTag(name, tag);
            }
            @Override public void showBottomInfo() { host.showBottomInfo(); }
            @Override public TextView getBatteryLevelText() { return host.getBatteryLevelText(); }
            @Override public TextView getDeviceAddressText() { return host.getDeviceAddressText(); }
            @Override public TextView getUpdateTimeText() { return host.getUpdateTimeText(); }
            @Override public void showCoordinatesAndGeocode(double lat, double lng, boolean force) {
                host.showCoordinatesAndGeocode(lat, lng, force);
            }
            @Override public void locateToDevicePosition(boolean setUserLocated) {
                host.locateToDevicePosition(setUserLocated);
            }
            @Override public void refreshMapWithCurrentDevice(boolean force) {
                host.refreshMapWithCurrentDevice(force);
            }
            @Override public void performDeviceRefresh(boolean showToast) {
                host.performDeviceRefresh(showToast);
            }
            @Override public String getString(int resId) { return host.getString(resId); }
            @Override public String getString(int resId, Object... args) {
                return host.getString(resId, args);
            }
        }));
    }
}
