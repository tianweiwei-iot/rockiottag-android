package com.RockiotTag.tag.helper;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.MapManager;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.map.MapAdapterFactory;
import com.RockiotTag.tag.map.amap.AMapManager;
import com.RockiotTag.tag.map.google.GoogleMapManager;
import com.RockiotTag.tag.util.LogUtil;
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;

/**
 * MainActivity 地图提供商切换（已改为 recreate Activity，见 MainActivity.onMapProviderChanged）。
 * 保留此类供测试或备用；生产路径不再调用。
 */
@Deprecated
public class MainMapSwitchHelper {

    private static final String TAG = "MainMapSwitchHelper";

    public interface Host {
        AppCompatActivity getActivity();
        MapView getMapView();
        SupportMapFragment getGoogleMapFragment();
        MapManager getMapManager();
        IMapAdapter getMapAdapter();
        void setMapAdapter(IMapAdapter adapter);
        void setAMap(AMap aMap);
        void onMapReadyRefreshDevice();
        void reinitLocationService();
        void releaseLocationServices();
        void setLastUserInteractionTime(long time);
        void updateCustomCompassRotation(float bearing);
        void onMapSwitchFinished();
    }

    public static boolean switchProvider(Host host, String newProvider) {
        AppCompatActivity activity = host.getActivity();
        if (!isActivityAlive(activity)) {
            return false;
        }

        MapManager mapManager = host.getMapManager();
        MapView mapView = host.getMapView();
        SupportMapFragment googleMapFragment = host.getGoogleMapFragment();
        if (mapManager == null || mapView == null) {
            LogUtil.w(TAG, "MapManager or MapView not ready, skip switch");
            return false;
        }

        IMapAdapter oldAdapter = host.getMapAdapter();
        String currentProvider = oldAdapter != null
                ? oldAdapter.getProvider()
                : MapAdapterFactory.getSavedProvider(activity);
        if (newProvider.equals(currentProvider)) {
            return false;
        }

        LogUtil.d(TAG, "Switching map provider in-place: " + currentProvider + " -> " + newProvider);

        host.releaseLocationServices();

        if (oldAdapter != null) {
            oldAdapter.hideMap();
            if (MapAdapterFactory.PROVIDER_GOOGLE.equals(oldAdapter.getProvider())) {
                mapManager.resetGoogleMapState();
            }
            oldAdapter.onDestroy();
        }

        IMapAdapter newAdapter;
        if (MapAdapterFactory.PROVIDER_AMAP.equals(newProvider)) {
            newAdapter = MapAdapterFactory.createAMapAdapter(activity, mapView);
        } else {
            if (googleMapFragment == null) {
                LogUtil.e(TAG, "GoogleMapFragment is null, cannot switch to Google");
                return false;
            }
            newAdapter = MapAdapterFactory.createGoogleMapAdapter(activity, googleMapFragment);
        }
        host.setMapAdapter(newAdapter);

        newAdapter.setCallback(new IMapAdapter.MapCallback() {
            @Override
            public void onMapReady() {
                if (!isActivityAlive(activity)) {
                    return;
                }
                LogUtil.d(TAG, "Map ready after provider switch: " + newProvider);

                if (MapAdapterFactory.PROVIDER_AMAP.equals(newProvider)) {
                    AMap aMap = ((AMapManager) newAdapter).getAMap();
                    host.setAMap(aMap);
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
                    mapManager.switchToAmap();
                    newAdapter.showMap();
                } else {
                    host.setAMap(null);
                    GoogleMap googleMap = ((GoogleMapManager) newAdapter).getGoogleMap();
                    if (googleMap != null) {
                        mapManager.attachGoogleMap(googleMap);
                    }
                    mapManager.switchToGoogleMap();
                    newAdapter.showMap();
                    mapManager.ensureGoogleMapVisible();
                }

                host.reinitLocationService();
                host.onMapReadyRefreshDevice();
                host.onMapSwitchFinished();
            }

            @Override
            public void onMapClick(double latitude, double longitude) {
                LogUtil.d(TAG, "Map clicked: " + latitude + ", " + longitude);
            }
        });

        if (MapAdapterFactory.PROVIDER_AMAP.equals(newProvider)) {
            mapManager.initAmap();
        } else {
            host.setAMap(null);
            mapView.setVisibility(android.view.View.GONE);
        }
        newAdapter.initMap();

        if (MapAdapterFactory.PROVIDER_AMAP.equals(newProvider)) {
            mapManager.switchToAmap();
            newAdapter.showMap();
            LogUtil.d(TAG, "AMap switch completed synchronously");
        } else {
            mapManager.switchToGoogleMap();
            newAdapter.showMap();
            mapManager.ensureGoogleMapVisible();
        }

        return true;
    }

    private static boolean isActivityAlive(AppCompatActivity activity) {
        return activity != null && !activity.isFinishing()
                && (android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }
}
