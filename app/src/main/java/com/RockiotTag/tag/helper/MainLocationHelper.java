package com.RockiotTag.tag.helper;

import com.RockiotTag.tag.util.ToastHelper;

import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.location.SystemLocationService;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.viewmodel.MainViewModel;

/**
 * MainActivity 手机定位服务初始化与地图位置更新逻辑。
 */
public class MainLocationHelper {

    private static final String TAG = "MainLocationHelper";

    public interface Host {
        AppCompatActivity getActivity();
        IMapAdapter getMapAdapter();
        MainViewModel getViewModel();
        TagDevice getSelectedDevice();
        double getCurrentLatitude();
        double getCurrentLongitude();
        void setCurrentLatitude(double lat);
        void setCurrentLongitude(double lng);
        boolean isFirstLocation();
        void setFirstLocation(boolean first);
        void setGoogleLocationService(
                com.RockiotTag.tag.map.google.GoogleLocationService service);
        com.RockiotTag.tag.map.google.GoogleLocationService getGoogleLocationService();
        void setSystemLocationService(SystemLocationService service);
        SystemLocationService getSystemLocationService();
        void onLocationUpdated();
    }

    private final Host host;

    public MainLocationHelper(Host host) {
        this.host = host;
    }

    public void initLocation() {
        AppCompatActivity activity = host.getActivity();
        IMapAdapter mapAdapter = host.getMapAdapter();
        try {
            if (mapAdapter != null && mapAdapter.getProvider().equals("google")) {
                com.RockiotTag.tag.map.google.GoogleLocationService existing = host.getGoogleLocationService();
                if (existing != null) {
                    existing.onDestroy();
                    host.setGoogleLocationService(null);
                }
                LogUtil.d(TAG, "Initializing Google Location Service");
                com.RockiotTag.tag.map.google.GoogleLocationService googleLocationService =
                        new com.RockiotTag.tag.map.google.GoogleLocationService(
                                activity.getApplicationContext());
                googleLocationService.setLocationCallback(
                        new com.RockiotTag.tag.map.google.GoogleLocationService.ServiceCallback() {
                            @Override
                            public void onLocationSuccess(double latitude, double longitude, float accuracy) {
                                if (!isActivityAlive(activity)) {
                                    return;
                                }
                                host.setCurrentLatitude(latitude);
                                host.setCurrentLongitude(longitude);
                                MainViewModel viewModel = host.getViewModel();
                                if (viewModel != null) {
                                    viewModel.setCurrentLocation(latitude, longitude);
                                }
                                host.onLocationUpdated();
                            }

                            @Override
                            public void onLocationFailed(String error) {
                                if (!isActivityAlive(activity)) {
                                    return;
                                }
                                Log.e(TAG, "Google location failed: " + error);
                                setDefaultLocation();
                            }
                        });
                host.setGoogleLocationService(googleLocationService);
                googleLocationService.startLocation();
            } else {
                SystemLocationService existing = host.getSystemLocationService();
                if (existing != null) {
                    existing.onDestroy();
                    host.setSystemLocationService(null);
                }
                LogUtil.d(TAG, "Initializing System Location Service (avoid AMap LocationScheduler crash)");
                SystemLocationService systemLocationService =
                        new SystemLocationService(activity.getApplicationContext());
                systemLocationService.setLocationCallback(
                        new SystemLocationService.ServiceCallback() {
                            @Override
                            public void onLocationSuccess(double latitude, double longitude, float accuracy) {
                                if (!isActivityAlive(activity)) {
                                    return;
                                }
                                host.setCurrentLatitude(latitude);
                                host.setCurrentLongitude(longitude);
                                MainViewModel viewModel = host.getViewModel();
                                if (viewModel != null) {
                                    viewModel.setCurrentLocation(latitude, longitude);
                                }
                                host.onLocationUpdated();
                            }

                            @Override
                            public void onLocationFailed(String error) {
                                if (!isActivityAlive(activity)) {
                                    return;
                                }
                                Log.e(TAG, "System location failed: " + error);
                                setDefaultLocation();
                            }
                        });
                host.setSystemLocationService(systemLocationService);
                systemLocationService.startLocation();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: Missing location permissions", e);
            ToastHelper.showLong(activity, R.string.missing_location_permission);
            setDefaultLocation();
        } catch (Throwable e) {
            Log.e(TAG, "Error initializing location: " + e.getMessage(), e);
            ToastHelper.showLong(activity,
                    activity.getString(R.string.location_service_init_failed, e.getMessage()));
            setDefaultLocation();
        }
    }

    public void setDefaultLocation() {
        host.setCurrentLatitude(22.543611);
        host.setCurrentLongitude(113.881944);
        LogUtil.d(TAG, "Set default location (map position unchanged)");
    }

    public void updateCurrentLocationOnMap() {
        AppCompatActivity activity = host.getActivity();
        if (activity == null) {
            return;
        }
        Runnable update = () -> {
            if (!isActivityAlive(activity)) {
                return;
            }
            IMapAdapter mapAdapter = host.getMapAdapter();
            if (mapAdapter == null) {
                return;
            }

            if (mapAdapter.getProvider().equals("google")) {
                LogUtil.d(TAG, "Google Map - auto camera move disabled");
                return;
            }

            TagDevice selectedDevice = host.getSelectedDevice();
            if (selectedDevice == null && host.isFirstLocation()) {
                try {
                    mapAdapter.moveCamera(host.getCurrentLatitude(), host.getCurrentLongitude(), 17);
                    host.setFirstLocation(false);
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to move camera to current location", t);
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            update.run();
        } else {
            activity.runOnUiThread(update);
        }
    }

    private static boolean isActivityAlive(AppCompatActivity activity) {
        return activity != null && !activity.isFinishing()
                && (android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }
}
