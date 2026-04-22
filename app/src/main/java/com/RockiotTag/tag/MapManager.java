package com.RockiotTag.tag;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;

public class MapManager implements OnMapReadyCallback {
    private static final String TAG = "MapManager";
    private Handler handler;
    private boolean isCheckingMapLoad = false;
    
    public static final String MAP_PROVIDER_AMAP = "amap";
    public static final String MAP_PROVIDER_GOOGLE = "google";
    
    private Context context;
    private MapView amapView;
    private SupportMapFragment googleMapFragment;
    private AMap amap;
    private GoogleMap googleMap;
    private String currentProvider;
    private MapCallback callback;
    private double defaultLat = 22.543611;
    private double defaultLng = 113.881944;
    private boolean googleMapReady = false;
    
    public interface MapCallback {
        void onMapReady();
        void onMapClick(double latitude, double longitude);
    }
    
    public MapManager(Context context, MapView amapView, SupportMapFragment googleMapFragment) {
        this.context = context;
        this.amapView = amapView;
        this.googleMapFragment = googleMapFragment;
        this.handler = new Handler();
        
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        this.currentProvider = prefs.getString("map_provider", MAP_PROVIDER_AMAP);
        Log.d(TAG, "MapManager initialized, current provider: " + currentProvider);
        Log.d(TAG, "googleMapFragment: " + googleMapFragment);
        checkNetworkStatus();
    }
    
    private void checkNetworkStatus() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                Log.d(TAG, "Network status: " + (isConnected ? "Connected" : "Disconnected"));
                if (!isConnected) {
                    Log.w(TAG, "No network connection - Google Maps may not load!");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network status: " + e.getMessage(), e);
        }
    }
    
    public void setCallback(MapCallback callback) {
        this.callback = callback;
    }
    
    public void initAmap() {
        Log.d(TAG, "initAmap called, amapView: " + amapView);
        if (amapView != null) {
            amap = amapView.getMap();
            Log.d(TAG, "AMap instance: " + amap);
            if (amap != null) {
                amap.getUiSettings().setMyLocationButtonEnabled(false);
                amap.setMyLocationEnabled(false);
                amap.getUiSettings().setCompassEnabled(true);
                amap.getUiSettings().setScaleControlsEnabled(true);
                amap.moveCamera(CameraUpdateFactory.zoomTo(17));
                
                amap.setOnMapClickListener(new AMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(com.amap.api.maps.model.LatLng latLng) {
                        if (callback != null) {
                            callback.onMapClick(latLng.latitude, latLng.longitude);
                        }
                    }
                });
                Log.d(TAG, "AMap initialized successfully");
            }
        }
    }
    
    public void initGoogleMap() {
        Log.d(TAG, "initGoogleMap called, googleMapFragment: " + googleMapFragment);
        if (googleMapFragment != null) {
            googleMapFragment.getMapAsync(this);
            Log.d(TAG, "Google Map getMapAsync called");
        } else {
            Log.e(TAG, "googleMapFragment is null!");
        }
    }
    
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "onMapReady called, googleMap: " + googleMap);
        this.googleMap = googleMap;
        this.googleMapReady = true;
        
        if (googleMap != null) {
            try {
                Log.d(TAG, "Configuring Google Map UI settings...");
                googleMap.getUiSettings().setMyLocationButtonEnabled(false);
                googleMap.getUiSettings().setCompassEnabled(true);
                googleMap.getUiSettings().setMapToolbarEnabled(false);
                googleMap.getUiSettings().setZoomControlsEnabled(true);
                
                // 设置默认地图类型为普通地图
                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                Log.d(TAG, "Map type set to NORMAL");
                
                // 添加地图加载完成监听
                googleMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                    @Override
                    public void onMapLoaded() {
                        Log.d(TAG, "Google Map loaded successfully!");
                        isCheckingMapLoad = false;
                        if (context != null) {
                            Toast.makeText(context, "Google Map loaded!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                
                // 添加相机移动监听
                googleMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
                    @Override
                    public void onCameraIdle() {
                        Log.d(TAG, "Camera is idle at: " + googleMap.getCameraPosition().target);
                    }
                });
                
                // 添加相机移动开始监听
                googleMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
                    @Override
                    public void onCameraMoveStarted(int reason) {
                        Log.d(TAG, "Camera move started, reason: " + reason);
                    }
                });
                
                Log.d(TAG, "Setting default location: lat=" + defaultLat + ", lng=" + defaultLng);
                com.google.android.gms.maps.model.LatLng defaultLatLng =
                    new com.google.android.gms.maps.model.LatLng(defaultLat, defaultLng);
                googleMap.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(defaultLatLng, 12));
                Log.d(TAG, "Camera moved to initial position with zoom 12");
                
                // 添加地图点击事件监听以确保地图已激活
                googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(com.google.android.gms.maps.model.LatLng latLng) {
                        Log.d(TAG, "Google Map clicked at: " + latLng.latitude + ", " + latLng.longitude);
                        if (callback != null) {
                            callback.onMapClick(latLng.latitude, latLng.longitude);
                        }
                    }
                });
                
                Log.d(TAG, "Google Map initialized successfully");
                
                if (callback != null) {
                    callback.onMapReady();
                }
                
                // 如果当前是谷歌地图，立即确保视图可见
                if (isGoogleMap()) {
                    Log.d(TAG, "Current provider is Google Map, ensuring view is visible...");
                    if (googleMapFragment != null && googleMapFragment.getView() != null) {
                        googleMapFragment.getView().setVisibility(View.VISIBLE);
                        Log.d(TAG, "Google Map view visibility set to VISIBLE");
                    }
                }
                
                // 延迟一会儿再设置最终缩放级别，给地图更多时间加载
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (googleMap != null) {
                            Log.d(TAG, "Setting final zoom level to 17");
                            com.google.android.gms.maps.model.LatLng finalLatLng =
                                new com.google.android.gms.maps.model.LatLng(defaultLat, defaultLng);
                            googleMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(finalLatLng, 17));
                        }
                    }
                }, 2000);
                
                // 启动地图加载监控
                startMapLoadMonitoring();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Google Map: " + e.getMessage(), e);
                e.printStackTrace();
                if (context != null) {
                    Toast.makeText(context, "Google Map initialization error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Log.e(TAG, "Google Map is null in onMapReady!");
        }
    }
    
    private void startMapLoadMonitoring() {
        if (isCheckingMapLoad) {
            Log.d(TAG, "Map load monitoring already in progress");
            return;
        }
        
        isCheckingMapLoad = true;
        Log.d(TAG, "Starting map load monitoring (timeout in 15 seconds)");
        
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isCheckingMapLoad) {
                    Log.w(TAG, "Map load timeout - tiles may not have loaded!");
                    isCheckingMapLoad = false;
                    
                    if (context != null) {
                        Toast.makeText(context, "Google Map may not have loaded. Check API Key, network, and VPN settings.", Toast.LENGTH_LONG).show();
                    }
                    
                    // 尝试多种方法来触发地图重新加载
                    if (googleMap != null) {
                        try {
                            Log.d(TAG, "Attempting map reload strategies...");
                            
                            // 策略1：切换地图类型
                            Log.d(TAG, "Strategy 1: Toggle map type");
                            int currentType = googleMap.getMapType();
                            googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (googleMap != null) {
                                        googleMap.setMapType(currentType);
                                        Log.d(TAG, "Map type restored");
                                    }
                                }
                            }, 1000);
                            
                            // 策略2：稍微移动相机
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (googleMap != null) {
                                        try {
                                            com.google.android.gms.maps.model.LatLng currentPos = googleMap.getCameraPosition().target;
                                            com.google.android.gms.maps.model.LatLng newPos = new com.google.android.gms.maps.model.LatLng(
                                                currentPos.latitude + 0.01,
                                                currentPos.longitude + 0.01
                                            );
                                            Log.d(TAG, "Strategy 2: Move camera slightly");
                                            googleMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(newPos, 10), 1000, null);
                                            
                                            handler.postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (googleMap != null) {
                                                        googleMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(currentPos, 15), 1000, null);
                                                        Log.d(TAG, "Camera moved back");
                                                    }
                                                }
                                            }, 1500);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error in strategy 2: " + e.getMessage(), e);
                                        }
                                    }
                                }
                            }, 2000);
                            
                            // 策略3：尝试清除并重新设置
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (googleMap != null) {
                                        Log.d(TAG, "Strategy 3: Attempting map reset");
                                        googleMap.clear();
                                        Log.d(TAG, "Map cleared");
                                    }
                                }
                            }, 5000);
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error triggering map reload: " + e.getMessage(), e);
                        }
                    }
                    
                    // 记录诊断信息
                    Log.w(TAG, "=== DIAGNOSTIC INFO ===");
                    Log.w(TAG, "Possible issues:");
                    Log.w(TAG, "1. API Key may be invalid or not properly configured");
                    Log.w(TAG, "2. Maps SDK for Android may not be enabled in Google Cloud Console");
                    Log.w(TAG, "3. API Key may not have proper restrictions (package name/SHA-1)");
                    Log.w(TAG, "4. Network/VPN may not have access to Google services");
                    Log.w(TAG, "5. Check Logcat for Google Maps SDK specific error messages");
                }
            }
        }, 15000); // 15秒超时
    }
    
    public boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
        
        if (resultCode == ConnectionResult.SUCCESS) {
            Log.d(TAG, "Google Play Services is available");
            return true;
        } else {
            Log.e(TAG, "Google Play Services not available, result code: " + resultCode);
            String errorMessage = apiAvailability.getErrorString(resultCode);
            Log.e(TAG, "Error message: " + errorMessage);
            return false;
        }
    }
    
    public void switchToAmap() {
        Log.d(TAG, "switchToAmap called");
        currentProvider = MAP_PROVIDER_AMAP;
        saveMapProvider();
        
        if (amapView != null) {
            amapView.setVisibility(View.VISIBLE);
            Log.d(TAG, "AMap view set to VISIBLE");
        }
        if (googleMapFragment != null && googleMapFragment.getView() != null) {
            googleMapFragment.getView().setVisibility(View.GONE);
            Log.d(TAG, "Google Map view set to GONE");
        } else {
            Log.w(TAG, "Google Map fragment or view is null when switching to AMap");
        }
    }
    
    public void switchToGoogleMap() {
        Log.d(TAG, "switchToGoogleMap called, googleMapReady: " + googleMapReady);
        currentProvider = MAP_PROVIDER_GOOGLE;
        saveMapProvider();
        
        if (amapView != null) {
            amapView.setVisibility(View.GONE);
            Log.d(TAG, "AMap view set to GONE");
        }
        
        if (googleMapFragment != null) {
            if (googleMapFragment.getView() != null) {
                googleMapFragment.getView().setVisibility(View.VISIBLE);
                Log.d(TAG, "Google Map view set to VISIBLE");
            } else {
                Log.w(TAG, "Google Map fragment view is null, fragment: " + googleMapFragment);
            }
            
            if (!googleMapReady) {
                Log.w(TAG, "Google Map not ready yet, re-initializing...");
                initGoogleMap();
            } else {
                Log.d(TAG, "Google Map is already ready");
            }
        } else {
            Log.e(TAG, "Cannot show Google Map - fragment is null");
            if (context != null) {
                Toast.makeText(context, "Google Map initialization failed. Please check if Google Play Services is available.", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    public String getCurrentProvider() {
        return currentProvider;
    }
    
    public boolean isAmap() {
        return MAP_PROVIDER_AMAP.equals(currentProvider);
    }
    
    public boolean isGoogleMap() {
        return MAP_PROVIDER_GOOGLE.equals(currentProvider);
    }
    
    public void moveCamera(double latitude, double longitude, float zoom) {
        if (isAmap() && amap != null) {
            LatLng latLng = com.RockiotTag.tag.CoordinateUtils.wgs84ToGcj02(latitude, longitude);
            amap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        } else if (isGoogleMap() && googleMap != null) {
            com.google.android.gms.maps.model.LatLng latLng = 
                new com.google.android.gms.maps.model.LatLng(latitude, longitude);
            googleMap.animateCamera(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, zoom)
            );
        } else {
            Log.w(TAG, "moveCamera called but map is null, provider: " + currentProvider);
        }
    }
    
    public void setMapType(int type) {
        if (isAmap() && amap != null) {
            amap.setMapType(type);
        } else if (isGoogleMap() && googleMap != null) {
            if (type == AMap.MAP_TYPE_SATELLITE) {
                googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            } else {
                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            }
        }
    }
    
    public Marker addAmapMarker(LatLng latLng, String title, String snippet) {
        if (amap != null) {
            MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(snippet);
            return amap.addMarker(options);
        }
        return null;
    }
    
    public com.google.android.gms.maps.model.Marker addGoogleMarker(
            com.google.android.gms.maps.model.LatLng latLng, String title, String snippet) {
        if (googleMap != null) {
            com.google.android.gms.maps.model.MarkerOptions options = 
                new com.google.android.gms.maps.model.MarkerOptions()
                    .position(latLng)
                    .title(title)
                    .snippet(snippet);
            return googleMap.addMarker(options);
        }
        return null;
    }
    
    public void clearMap() {
        if (isAmap() && amap != null) {
            amap.clear();
        } else if (isGoogleMap() && googleMap != null) {
            googleMap.clear();
        }
    }
    
    public float getZoomLevel() {
        if (isAmap() && amap != null) {
            return amap.getCameraPosition().zoom;
        } else if (isGoogleMap() && googleMap != null) {
            return googleMap.getCameraPosition().zoom;
        }
        return 17;
    }
    
    private void saveMapProvider() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("map_provider", currentProvider);
        editor.apply();
    }
    
    public AMap getAmap() {
        return amap;
    }
    
    public GoogleMap getGoogleMap() {
        return googleMap;
    }
    
    public boolean isGoogleMapReady() {
        return googleMapReady && googleMap != null;
    }
    
    public void setDefaultLocation(double lat, double lng) {
        this.defaultLat = lat;
        this.defaultLng = lng;
        
        if (isAmap() && amap != null) {
            LatLng latLng = new LatLng(lat, lng);
            amap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17));
        } else if (isGoogleMap() && googleMap != null) {
            com.google.android.gms.maps.model.LatLng latLng = 
                new com.google.android.gms.maps.model.LatLng(lat, lng);
            googleMap.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 17));
        }
    }
}
