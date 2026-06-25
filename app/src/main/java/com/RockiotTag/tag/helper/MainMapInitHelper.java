package com.RockiotTag.tag.helper;

import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.MapManager;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.map.MapAdapterFactory;
import com.RockiotTag.tag.map.MapLayerController;
import com.RockiotTag.tag.util.LogUtil;
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.google.android.gms.maps.SupportMapFragment;

/**
 * MainActivity 地图 SDK 初始化逻辑。
 */
public class MainMapInitHelper {

    private static final String TAG = "MainMapInitHelper";

    public interface Host {
        AppCompatActivity getActivity();
        MapView getMapView();
        SupportMapFragment getGoogleMapFragment();
        void setMapManager(MapManager manager);
        void setMapAdapter(IMapAdapter adapter);
        void setAMap(AMap aMap);
        AMap getAMap();
        IMapAdapter getMapAdapter();
        MapManager getMapManager();
        void onMapReadyRefreshDevice();
        void restoreSelectedDeviceForMapInit();
        void updateCustomCompassRotation(float bearing);
        void setLastUserInteractionTime(long time);
        void autoLocateToDevice();
    }

    private final Host host;
    private final Handler mainHandler = new Handler(android.os.Looper.getMainLooper());
    private Runnable pendingAutoLocateRunnable;

    public MainMapInitHelper(Host host) {
        this.host = host;
    }

    /** Activity 销毁或 recreate 前取消延迟任务，避免多次切换语言后堆积回调导致闪退 */
    public void cancelPendingTasks() {
        if (pendingAutoLocateRunnable != null) {
            mainHandler.removeCallbacks(pendingAutoLocateRunnable);
            pendingAutoLocateRunnable = null;
        }
    }

    private boolean isHostAlive() {
        AppCompatActivity activity = host.getActivity();
        return activity != null && !activity.isFinishing()
                && (android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }

    public void initMap() {
        AppCompatActivity activity = host.getActivity();
        MapView mapView = host.getMapView();
        SupportMapFragment googleMapFragment = host.getGoogleMapFragment();

        LogUtil.d(TAG, "Initializing map...");

        MapManager mapManager = new MapManager(activity, mapView, googleMapFragment);
        host.setMapManager(mapManager);

        String provider = MapAdapterFactory.getSavedProvider(activity);
        LogUtil.d(TAG, "Using map provider from preferences: " + provider);
        IMapAdapter mapAdapter;
        if (MapAdapterFactory.PROVIDER_AMAP.equals(provider)) {
            mapAdapter = MapAdapterFactory.createAMapAdapter(activity, mapView);
        } else {
            mapAdapter = MapAdapterFactory.createGoogleMapAdapter(activity, googleMapFragment);
        }
        host.setMapAdapter(mapAdapter);

        mapAdapter.setCallback(new IMapAdapter.MapCallback() {
            @Override
            public void onMapReady() {
                LogUtil.d(TAG, "Map is ready");
                if (mapAdapter.getProvider().equals("amap")) {
                    AMap aMap = ((com.RockiotTag.tag.map.amap.AMapManager) mapAdapter).getAMap();
                    host.setAMap(aMap);
                    mapManager.switchToAmap();
                } else {
                    host.setAMap(null);
                    com.google.android.gms.maps.GoogleMap googleMap =
                            ((com.RockiotTag.tag.map.google.GoogleMapManager) mapAdapter).getGoogleMap();
                    mapManager.attachGoogleMap(googleMap);
                    mapManager.switchToGoogleMap();
                }
                host.onMapReadyRefreshDevice();
            }

            @Override
            public void onMapClick(double latitude, double longitude) {
                LogUtil.d(TAG, "Map clicked: " + latitude + ", " + longitude);
            }
        });

        host.restoreSelectedDeviceForMapInit();

        MapLayerController.applyLayers(provider, mapView, googleMapFragment);

        if (MapAdapterFactory.PROVIDER_AMAP.equals(provider)) {
            LogUtil.d(TAG, "Initializing AMap via MapManager...");
            mapManager.initAmap();
        } else {
            host.setAMap(null);
            LogUtil.d(TAG, "Google mode: skip AMap SDK init");
        }

        LogUtil.d(TAG, "Initializing map via adapter...");
        mapAdapter.initMap();

        if (mapAdapter.getProvider().equals("amap")) {
            AMap aMap = ((com.RockiotTag.tag.map.amap.AMapManager) mapAdapter).getAMap();
            host.setAMap(aMap);
            if (aMap != null) {
                MainMapUiConfigurator.applyMainAmapSettings(activity, new MainMapUiConfigurator.AmapHost() {
                    @Override
                    public void setLastUserInteractionTime(long time) {
                        host.setLastUserInteractionTime(time);
                    }

                    @Override
                    public void updateCustomCompassRotation(float bearing) {
                        host.updateCustomCompassRotation(bearing);
                    }
                }, aMap);
                host.onMapReadyRefreshDevice();
            } else {
                Log.e(TAG, "Failed to get AMap instance");
            }
        } else {
            mapManager.switchToGoogleMap();
        }

        cancelPendingTasks();
        pendingAutoLocateRunnable = () -> {
            if (isHostAlive()) {
                host.autoLocateToDevice();
            }
        };
        mainHandler.postDelayed(pendingAutoLocateRunnable, 800);
    }
}
