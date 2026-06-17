package com.RockiotTag.tag.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.RockiotTag.tag.DeviceApiService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/**
 * 维护 SharedPreferences 中的 bound_devices 缓存。
 */
public final class BoundDevicesHelper {

    private static final String TAG = "BoundDevicesHelper";
    private static final String PREFS = "app_settings";
    private static final String KEY_BOUND_DEVICES = "bound_devices";

    private BoundDevicesHelper() {}

    public static void updateNickName(Context context, String deviceNum, String nickName) {
        if (context == null || deviceNum == null || deviceNum.isEmpty()) {
            return;
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String boundDevicesJson = prefs.getString(KEY_BOUND_DEVICES, null);
            Gson gson = new Gson();

            List<DeviceApiService.BoundDevice> boundDevices;
            if (boundDevicesJson != null && !boundDevicesJson.isEmpty()) {
                boundDevices = gson.fromJson(boundDevicesJson,
                        new TypeToken<List<DeviceApiService.BoundDevice>>() {}.getType());
            } else {
                boundDevices = new ArrayList<>();
            }
            if (boundDevices == null) {
                boundDevices = new ArrayList<>();
            }

            boolean found = false;
            for (DeviceApiService.BoundDevice device : boundDevices) {
                if (deviceNum.equals(device.deviceNum)) {
                    device.nickName = nickName;
                    device.alias = nickName;
                    found = true;
                    break;
                }
            }
            if (!found) {
                DeviceApiService.BoundDevice entry =
                        new DeviceApiService.BoundDevice(deviceNum, nickName, System.currentTimeMillis());
                entry.nickName = nickName;
                boundDevices.add(entry);
            }

            prefs.edit().putString(KEY_BOUND_DEVICES, gson.toJson(boundDevices)).apply();
            Log.d(TAG, "Updated bound_devices nickName for " + deviceNum);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update bound_devices: " + e.getMessage(), e);
        }
    }
}
