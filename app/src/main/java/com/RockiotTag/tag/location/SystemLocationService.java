package com.RockiotTag.tag.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.RockiotTag.tag.util.LogUtil;

/**
 * 基于 Android 系统 LocationManager 的手机定位服务。
 * 用于替代高德 AMapLocationClient 连续定位，避免部分机型上 LocationScheduler 原生崩溃 (SIGABRT)。
 */
public class SystemLocationService {

    private static final String TAG = "SystemLocationService";
    private static final long MIN_TIME_MS = 5000L;
    private static final float MIN_DISTANCE_M = 5f;

    private final Context context;
    private final LocationManager locationManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LocationListener locationListener;
    private ServiceCallback callback;

    public interface ServiceCallback {
        void onLocationSuccess(double latitude, double longitude, float accuracy);
        void onLocationFailed(String error);
    }

    public SystemLocationService(Context context) {
        this.context = context.getApplicationContext();
        this.locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
        LogUtil.d(TAG, "SystemLocationService initialized");
    }

    public void setLocationCallback(ServiceCallback callback) {
        this.callback = callback;
    }

    public void startLocation() {
        stopLocation();
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted");
            notifyFailed("Missing location permissions");
            return;
        }
        if (locationManager == null) {
            notifyFailed("LocationManager unavailable");
            return;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location == null) {
                    return;
                }
                deliverLocation(location);
            }

            @Override
            public void onProviderEnabled(String provider) {
                LogUtil.d(TAG, "Provider enabled: " + provider);
            }

            @Override
            public void onProviderDisabled(String provider) {
                Log.w(TAG, "Provider disabled: " + provider);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                // deprecated, no-op
            }
        };

        try {
            boolean started = false;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_TIME_MS,
                        MIN_DISTANCE_M,
                        locationListener,
                        Looper.getMainLooper());
                started = true;
                LogUtil.d(TAG, "GPS location updates started");
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_MS,
                        MIN_DISTANCE_M,
                        locationListener,
                        Looper.getMainLooper());
                started = true;
                LogUtil.d(TAG, "Network location updates started");
            }

            if (!started) {
                Location cached = getBestLastKnownLocation();
                if (cached != null) {
                    deliverLocation(cached);
                } else {
                    notifyFailed("No location provider enabled");
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException starting location updates", e);
            notifyFailed("Missing location permissions");
        } catch (Throwable e) {
            Log.e(TAG, "Error starting location updates", e);
            notifyFailed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    public void stopLocation() {
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception e) {
                Log.w(TAG, "Error removing location updates: " + e.getMessage());
            }
        }
        locationListener = null;
    }

    public void onDestroy() {
        callback = null;
        mainHandler.removeCallbacksAndMessages(null);
        stopLocation();
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private Location getBestLastKnownLocation() {
        Location best = null;
        for (String provider : new String[]{
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location == null) {
                    continue;
                }
                if (best == null || location.getTime() > best.getTime()) {
                    best = location;
                }
            } catch (SecurityException e) {
                Log.w(TAG, "Cannot read last known location for " + provider, e);
            }
        }
        return best;
    }

    private void deliverLocation(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        float accuracy = location.getAccuracy();
        LogUtil.d(TAG, "Location success: lat=" + latitude + ", lng=" + longitude
                + ", accuracy=" + accuracy + "m, provider=" + location.getProvider());
        ServiceCallback cb = callback;
        if (cb == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cb.onLocationSuccess(latitude, longitude, accuracy);
        } else {
            mainHandler.post(() -> {
                ServiceCallback active = callback;
                if (active != null) {
                    active.onLocationSuccess(latitude, longitude, accuracy);
                }
            });
        }
    }

    private void notifyFailed(String error) {
        ServiceCallback cb = callback;
        if (cb == null) {
            return;
        }
        mainHandler.post(() -> {
            ServiceCallback active = callback;
            if (active != null) {
                active.onLocationFailed(error);
            }
        });
    }
}
