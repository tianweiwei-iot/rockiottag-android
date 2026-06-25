package com.RockiotTag.tag.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.DeviceApiService;
import com.RockiotTag.tag.model.TagDevice;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/**
 * 维护 SharedPreferences 中的 bound_devices 缓存，并提供统一的设备列表加载逻辑。
 */
public final class BoundDevicesHelper {

    private static final String TAG = "BoundDevicesHelper";
    private static final String PREFS = "app_settings";
    private static final String KEY_BOUND_DEVICES = "bound_devices";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final long FETCH_COOLDOWN_MS = 30_000L;

    private static volatile boolean fetchInProgress = false;
    private static volatile long lastFetchAttemptMs = 0L;
    private static volatile boolean lastFetchFailed = false;

    private BoundDevicesHelper() {}

    public interface FetchCallback {
        void onSuccess(List<DeviceApiService.BoundDevice> devices);
        void onFailure(String message);
    }

    public static boolean isLoggedIn(Context context) {
        if (context == null) return false;
        String token = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_AUTH_TOKEN, null);
        return token != null && !token.isEmpty();
    }

    public static String getAuthToken(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_AUTH_TOKEN, null);
    }

    public static String getBoundDevicesJson(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_BOUND_DEVICES, null);
    }

    public static boolean hasBoundDevicesCache(Context context) {
        String json = getBoundDevicesJson(context);
        return json != null && !json.isEmpty();
    }

    public static void saveBoundDevices(Context context, List<DeviceApiService.BoundDevice> devices) {
        if (context == null) return;
        List<DeviceApiService.BoundDevice> toSave = devices != null ? devices : new ArrayList<>();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_BOUND_DEVICES, new Gson().toJson(toSave))
                .apply();
        LogUtil.d(TAG, "Saved bound_devices cache, count=" + toSave.size());
    }

    public static void clearBoundDevicesCache(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_BOUND_DEVICES)
                .apply();
        resetFetchState();
    }

    public static void resetFetchState() {
        fetchInProgress = false;
        lastFetchAttemptMs = 0L;
        lastFetchFailed = false;
    }

    /**
     * 是否允许发起新的服务器拉取（非进行中且不在失败冷却期）。
     */
    public static boolean canAttemptFetch() {
        if (fetchInProgress) {
            return false;
        }
        return !lastFetchFailed
                || (System.currentTimeMillis() - lastFetchAttemptMs) >= FETCH_COOLDOWN_MS;
    }

    /**
     * 登录用户：有缓存则从本地 DB 过滤；无缓存且可拉取则返回 null（需异步 fetch）。
     * 未登录：返回空列表。
     */
    public static List<TagDevice> loadDisplayedDevices(Context context, DatabaseHelper databaseHelper) {
        List<TagDevice> result = new ArrayList<>();
        if (context == null || databaseHelper == null) {
            return result;
        }
        if (!isLoggedIn(context)) {
            return result;
        }
        String boundDevicesJson = getBoundDevicesJson(context);
        if (boundDevicesJson != null && !boundDevicesJson.isEmpty()) {
            result.addAll(filterLocalDevicesByBoundList(databaseHelper, boundDevicesJson));
        }
        return result;
    }

    /**
     * 是否需要异步从服务器拉取绑定设备（已登录、无缓存、允许拉取）。
     */
    public static boolean needsServerFetch(Context context) {
        return isLoggedIn(context) && !hasBoundDevicesCache(context) && canAttemptFetch();
    }

    /**
     * 从 bound_devices 缓存匹配本地数据库中的设备。
     */
    public static List<TagDevice> filterLocalDevicesByBoundList(
            DatabaseHelper databaseHelper, String boundDevicesJson) {
        List<TagDevice> deviceList = new ArrayList<>();
        if (databaseHelper == null || boundDevicesJson == null || boundDevicesJson.isEmpty()) {
            return deviceList;
        }
        try {
            Gson gson = new Gson();
            List<DeviceApiService.BoundDevice> boundDevices = gson.fromJson(
                    boundDevicesJson,
                    new TypeToken<List<DeviceApiService.BoundDevice>>() {}.getType());

            if (boundDevices == null || boundDevices.isEmpty()) {
                return deviceList;
            }

            List<TagDevice> allLocalDevices = databaseHelper.getAllDevices();
            for (DeviceApiService.BoundDevice boundDevice : boundDevices) {
                String serverDeviceNum = boundDevice.getDeviceNum();
                if (serverDeviceNum == null || serverDeviceNum.isEmpty()) {
                    continue;
                }

                String normalizedServerNum = normalizeDeviceId(serverDeviceNum);
                for (TagDevice localDevice : allLocalDevices) {
                    String localDeviceNum = localDevice.getDeviceNum();
                    String localDeviceId = localDevice.getDeviceId();

                    if (normalizedServerNum.equals(normalizeDeviceId(localDeviceNum))
                            || normalizedServerNum.equals(normalizeDeviceId(localDeviceId))
                            || serverDeviceNum.equals(localDeviceNum)
                            || serverDeviceNum.equals(localDeviceId)) {
                        deviceList.add(localDevice);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error filtering bound devices: " + e.getMessage(), e);
        }
        return deviceList;
    }

    private static String normalizeDeviceId(String id) {
        if (id == null) return "";
        return id.contains(":")
                ? id.replace(":", "").toUpperCase()
                : id.toUpperCase();
    }

    /**
     * 从服务器拉取绑定设备并写入缓存。内置防重试：进行中跳过、失败后 30s 冷却。
     */
    public static void fetchBoundDevicesFromServer(Context context, String token, FetchCallback callback) {
        if (context == null || token == null || token.isEmpty()) {
            if (callback != null) {
                callback.onFailure("Invalid context or token");
            }
            return;
        }
        if (!canAttemptFetch()) {
            LogUtil.d(TAG, "Skip fetch: in progress or cooldown");
            if (callback != null) {
                callback.onFailure("Fetch skipped (in progress or cooldown)");
            }
            return;
        }

        fetchInProgress = true;
        lastFetchAttemptMs = System.currentTimeMillis();

        new Thread(() -> {
            try {
                DeviceApiService.DeviceApiResponse response =
                        DeviceApiService.getInstance().getBoundDevices(token);
                LogUtil.d(TAG, "fetchBoundDevices: success=" + response.isSuccess()
                        + ", devices=" + (response.getDevices() != null ? response.getDevices().size() : "null")
                        + ", statusCode=" + response.getStatusCode());

                if (response.isSuccess() && response.getDevices() != null) {
                    saveBoundDevices(context, response.getDevices());
                    lastFetchFailed = false;
                    if (callback != null) {
                        callback.onSuccess(response.getDevices());
                    }
                } else {
                    lastFetchFailed = true;
                    String msg = response.getMessage() != null ? response.getMessage() : "Fetch failed";
                    Log.e(TAG, "fetchBoundDevices failed: " + msg);
                    if (callback != null) {
                        callback.onFailure(msg);
                    }
                }
            } catch (Exception e) {
                lastFetchFailed = true;
                Log.e(TAG, "fetchBoundDevices error: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            } finally {
                fetchInProgress = false;
            }
        }).start();
    }

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
            LogUtil.d(TAG, "Updated bound_devices nickName for " + deviceNum);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update bound_devices: " + e.getMessage(), e);
        }
    }

    /**
     * 从 bound_devices 缓存中移除设备
     */
    public static void removeDevice(Context context, String deviceNum) {
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

            boolean removed = false;
            for (int i = 0; i < boundDevices.size(); i++) {
                if (deviceNum.equals(boundDevices.get(i).deviceNum)) {
                    boundDevices.remove(i);
                    removed = true;
                    break;
                }
            }

            if (removed) {
                prefs.edit().putString(KEY_BOUND_DEVICES, gson.toJson(boundDevices)).apply();
                LogUtil.d(TAG, "Removed device from bound_devices: " + deviceNum);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove device from bound_devices: " + e.getMessage(), e);
        }
    }
}
