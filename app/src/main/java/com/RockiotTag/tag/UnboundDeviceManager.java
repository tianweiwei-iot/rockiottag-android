package com.RockiotTag.tag;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class UnboundDeviceManager {
    private static final String PREF_NAME = "app_settings";
    private static final String PREF_UNBOUND_DEVICES = "unbound_devices";
    private static UnboundDeviceManager instance;
    private SharedPreferences prefs;
    private Set<String> unboundDevices;
    
    private UnboundDeviceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(PREF_UNBOUND_DEVICES, null);
        unboundDevices = new HashSet<>();
        if (saved != null) {
            unboundDevices.addAll(saved);
        }
    }
    
    public static synchronized UnboundDeviceManager getInstance(Context context) {
        if (instance == null) {
            instance = new UnboundDeviceManager(context.getApplicationContext());
        }
        return instance;
    }
    
    public static synchronized UnboundDeviceManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("UnboundDeviceManager not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }
    
    public void addUnboundDevice(String deviceNum) {
        if (deviceNum != null && !deviceNum.isEmpty()) {
            unboundDevices.add(deviceNum);
            saveUnboundDevices();
        }
    }
    
    public void removeUnboundDevice(String deviceNum) {
        unboundDevices.remove(deviceNum);
        saveUnboundDevices();
    }
    
    public boolean isDeviceUnbound(String deviceNum) {
        return unboundDevices.contains(deviceNum);
    }
    
    public Set<String> getUnboundDevices() {
        return new HashSet<>(unboundDevices);
    }
    
    public void clearAllUnboundDevices() {
        unboundDevices.clear();
        saveUnboundDevices();
    }
    
    public int getUnboundCount() {
        return unboundDevices.size();
    }
    
    private void saveUnboundDevices() {
        prefs.edit().putStringSet(PREF_UNBOUND_DEVICES, unboundDevices).apply();
    }
}
