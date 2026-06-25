package com.RockiotTag.tag;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.RockiotTag.tag.ApiConfig;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CrowdSourcingManager {
    private static final String TAG = "CrowdSourcingManager";
    private static String SERVER_URL = ApiConfig.MY_SERVER_URL + "/crowdsource";
    private final NewApiService apiService;
    private NearbyDevicesCallback callback;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public CrowdSourcingManager() {
        apiService = NewApiService.getInstance();
    }

    public void setCallback(NearbyDevicesCallback callback) {
        this.callback = callback;
    }

    public void sendDeviceData(TagDevice device) {
        executor.execute(() -> {
            boolean success = doSendDeviceData(device);
            mainHandler.post(() -> {
                if (success) {
                    LogUtil.d(TAG, "Device data sent successfully");
                } else {
                    Log.e(TAG, "Failed to send device data");
                }
            });
        });
    }

    public void requestNearbyDevices(double latitude, double longitude, double radius) {
        executor.execute(() -> {
            String response = doRequestNearbyDevices(latitude, longitude, radius);
            mainHandler.post(() -> {
                if (response != null) {
                    LogUtil.d(TAG, "Received nearby devices data");
                    parseNearbyDevicesResponse(response);
                } else {
                    Log.e(TAG, "Failed to receive nearby devices data");
                    if (callback != null) {
                        callback.onNearbyDevicesReceived(null);
                    }
                }
            });
        });
    }

    // 使用ApiService获取设备列表
    public void getDeviceList() {
        executor.execute(() -> {
            if (!apiService.isAuthenticated()) {
                Log.e(TAG, "Not authenticated, cannot get device list");
                mainHandler.post(() -> {
                    if (callback != null) callback.onDeviceListReceived(null);
                });
                return;
            }

            NewApiService.ApiResponse response = apiService.getDeviceList(ApiConfig.getDefaultServerUrl(), 1, 100);
            List<TagDevice> devices = null;
            if (response != null && response.isSuccess() && response.getItems() != null) {
                try {
                    devices = parseDeviceListResponse(response.getItems());
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing device list: " + e.getMessage());
                }
            } else {
                Log.e(TAG, "Failed to get device list: " + (response != null ? response.getMessage() : "Unknown error"));
            }

            List<TagDevice> finalDevices = devices;
            mainHandler.post(() -> {
                if (finalDevices != null) {
                    LogUtil.d(TAG, "Device list received: " + finalDevices.size() + " devices");
                    if (callback != null) callback.onDeviceListReceived(finalDevices);
                } else {
                    Log.e(TAG, "Failed to receive device list");
                    if (callback != null) callback.onDeviceListReceived(null);
                }
            });
        });
    }

    private boolean doSendDeviceData(TagDevice device) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(SERVER_URL + "/send");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            String jsonData = "{" +
                    "\"deviceId\":\"" + device.getDeviceId() + "\","
                    + "\"latitude\":" + device.getLatitude() + ","
                    + "\"longitude\":" + device.getLongitude() + ","
                    + "\"signalStrength\":" + device.getSignalStrength() + ","
                    + "\"timestamp\":" + System.currentTimeMillis()
                    + "}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonData.getBytes());
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    LogUtil.d(TAG, "Device data sent successfully: " + response.toString());
                }
                return true;
            } else {
                Log.e(TAG, "Failed to send device data, response code: " + responseCode);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending device data: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String doRequestNearbyDevices(double latitude, double longitude, double radius) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(SERVER_URL + "/nearby?lat=" + latitude + "&lng=" + longitude + "&radius=" + radius);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    LogUtil.d(TAG, "Nearby devices received: " + response.toString());
                    return response.toString();
                }
            } else {
                Log.e(TAG, "Failed to request nearby devices, response code: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting nearby devices: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // 解析附近设备响应
    private void parseNearbyDevicesResponse(String response) {
        try {
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            if (jsonObject.has("devices")) {
                JsonArray devicesArray = jsonObject.getAsJsonArray("devices");
                List<TagDevice> devices = new ArrayList<>();

                for (int i = 0; i < devicesArray.size(); i++) {
                    JsonObject deviceJson = devicesArray.get(i).getAsJsonObject();
                    TagDevice device = new TagDevice(
                            deviceJson.get("deviceId").getAsString(),
                            deviceJson.get("name").getAsString()
                    );
                    device.setLatitude(deviceJson.get("latitude").getAsDouble());
                    device.setLongitude(deviceJson.get("longitude").getAsDouble());
                    device.setSignalStrength(deviceJson.get("signalStrength").getAsInt());
                    device.setLastSeen(deviceJson.get("timestamp").getAsLong());
                    devices.add(device);
                }

                if (callback != null) {
                    callback.onNearbyDevicesReceived(devices);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing nearby devices response: " + e.getMessage());
            if (callback != null) {
                callback.onNearbyDevicesReceived(null);
            }
        }
    }

    // 解析设备列表响应
    private List<TagDevice> parseDeviceListResponse(Object items) {
        List<TagDevice> devices = new ArrayList<>();
        // 这里需要根据实际的API响应格式进行解析
        // 假设items是一个包含设备信息的列表
        // 实际项目中需要根据API文档进行调整
        return devices;
    }

    // 回调接口
    public interface NearbyDevicesCallback {
        void onNearbyDevicesReceived(List<TagDevice> devices);
        void onDeviceListReceived(List<TagDevice> devices);
    }
}