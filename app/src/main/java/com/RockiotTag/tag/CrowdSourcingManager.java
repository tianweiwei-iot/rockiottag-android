package com.RockiotTag.tag;

import android.os.AsyncTask;
import android.util.Log;

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

public class CrowdSourcingManager {
    private static final String TAG = "CrowdSourcingManager";
    private static String SERVER_URL = ApiConfig.MY_SERVER_URL + "/crowdsource";
    private NewApiService apiService;
    private NearbyDevicesCallback callback;

    public CrowdSourcingManager() {
        apiService = NewApiService.getInstance();
    }

    public void setCallback(NearbyDevicesCallback callback) {
        this.callback = callback;
    }

    public void sendDeviceData(Device device) {
        new SendDataTask().execute(device);
    }

    public void requestNearbyDevices(double latitude, double longitude, double radius) {
        new RequestDevicesTask().execute(latitude, longitude, radius);
    }

    // 使用ApiService获取设备列表
    public void getDeviceList() {
        new GetDeviceListTask().execute();
    }

    private class SendDataTask extends AsyncTask<Device, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Device... devices) {
            Device device = devices[0];
            try {
                URL url = new URL(SERVER_URL + "/send");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonData = "{" +
                        "\"deviceId\":\"" + device.getDeviceId() + "\","
                        + "\"latitude\":" + device.getLatitude() + ","
                        + "\"longitude\":" + device.getLongitude() + ","
                        + "\"signalStrength\":" + device.getSignalStrength() + ","
                        + "\"timestamp\":" + System.currentTimeMillis()
                        + "}";

                OutputStream os = conn.getOutputStream();
                os.write(jsonData.getBytes());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    Log.d(TAG, "Device data sent successfully: " + response.toString());
                    return true;
                } else {
                    Log.e(TAG, "Failed to send device data, response code: " + responseCode);
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending device data: " + e.getMessage());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                Log.d(TAG, "Device data sent successfully");
            } else {
                Log.e(TAG, "Failed to send device data");
            }
        }
    }

    private class RequestDevicesTask extends AsyncTask<Double, Void, String> {
        @Override
        protected String doInBackground(Double... params) {
            double latitude = params[0];
            double longitude = params[1];
            double radius = params[2];

            try {
                URL url = new URL(SERVER_URL + "/nearby?lat=" + latitude + "&lng=" + longitude + "&radius=" + radius);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    Log.d(TAG, "Nearby devices received: " + response.toString());
                    return response.toString();
                } else {
                    Log.e(TAG, "Failed to request nearby devices, response code: " + responseCode);
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error requesting nearby devices: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String response) {
            if (response != null) {
                Log.d(TAG, "Received nearby devices data");
                // 解析响应数据并更新UI
                parseNearbyDevicesResponse(response);
            } else {
                Log.e(TAG, "Failed to receive nearby devices data");
                if (callback != null) {
                    callback.onNearbyDevicesReceived(null);
                }
            }
        }
    }

    private class GetDeviceListTask extends AsyncTask<Void, Void, List<Device>> {
        @Override
        protected List<Device> doInBackground(Void... voids) {
            if (!apiService.isAuthenticated()) {
                Log.e(TAG, "Not authenticated, cannot get device list");
                return null;
            }

            NewApiService.ApiResponse response = apiService.getDeviceList(1, 100);
            if (response != null && response.isSuccess() && response.getItems() != null) {
                try {
                    // 解析设备列表
                    return parseDeviceListResponse(response.getItems());
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing device list: " + e.getMessage());
                    return null;
                }
            } else {
                Log.e(TAG, "Failed to get device list: " + (response != null ? response.getMessage() : "Unknown error"));
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<Device> devices) {
            if (devices != null) {
                Log.d(TAG, "Device list received: " + devices.size() + " devices");
                if (callback != null) {
                    callback.onDeviceListReceived(devices);
                }
            } else {
                Log.e(TAG, "Failed to receive device list");
                if (callback != null) {
                    callback.onDeviceListReceived(null);
                }
            }
        }
    }

    // 解析附近设备响应
    private void parseNearbyDevicesResponse(String response) {
        try {
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            if (jsonObject.has("devices")) {
                JsonArray devicesArray = jsonObject.getAsJsonArray("devices");
                List<Device> devices = new ArrayList<>();

                for (int i = 0; i < devicesArray.size(); i++) {
                    JsonObject deviceJson = devicesArray.get(i).getAsJsonObject();
                    Device device = new Device(
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
    private List<Device> parseDeviceListResponse(Object items) {
        List<Device> devices = new ArrayList<>();
        // 这里需要根据实际的API响应格式进行解析
        // 假设items是一个包含设备信息的列表
        // 实际项目中需要根据API文档进行调整
        return devices;
    }

    // 回调接口
    public interface NearbyDevicesCallback {
        void onNearbyDevicesReceived(List<Device> devices);
        void onDeviceListReceived(List<Device> devices);
    }
}