package com.RockiotTag.tag;

import android.util.Log;

import com.RockiotTag.tag.network.HttpHelper;
import com.RockiotTag.tag.util.LogUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备绑定API服务
 * 处理设备绑定、解绑、获取绑定列表、更新别名
 */
public class DeviceApiService {
    private static final String TAG = "DeviceApiService";
    private static DeviceApiService instance;

    private DeviceApiService() {}

    public static DeviceApiService getInstance() {
        if (instance == null) {
            instance = new DeviceApiService();
        }
        return instance;
    }

    /**
     * 绑定设备到账户
     * @param token Bearer Token
     * @param deviceNum 设备号
     * @return DeviceApiResponse
     */
    public DeviceApiResponse bindDevice(String token, String deviceNum) {
        return bindDevice(token, deviceNum, null);
    }

    /**
     * 绑定设备到用户账号（带昵称）
     * @param token Bearer Token
     * @param deviceNum 设备号
     * @param nickName 设备昵称（可选）
     * @return DeviceApiResponse
     */
    public DeviceApiResponse bindDevice(String token, String deviceNum, String nickName) {
        try {
            String url = ApiConfig.MY_SERVER_URL + "/device/bind";
            JsonObject params = new JsonObject();
            params.addProperty("deviceNum", deviceNum);
            if (nickName != null && !nickName.isEmpty()) {
                params.addProperty("nickName", nickName);
            }

            LogUtil.d(TAG, "Bind device request: " + url + ", deviceNum: " + deviceNum + ", nickName: " + nickName);
            HttpHelper.HttpResponse response = postWithAuth(url, params.toString(), token);

            return parseResponse(response);
        } catch (Exception e) {
            Log.e(TAG, "Bind device failed: " + e.getMessage(), e);
            DeviceApiResponse apiResponse = new DeviceApiResponse();
            apiResponse.setStatusCode(-1);
            apiResponse.setMessage("Network error: " + e.getMessage());
            return apiResponse;
        }
    }

    /**
     * 获取用户绑定的设备列表
     * @param token Bearer Token
     * @return DeviceApiResponse（包含设备列表）
     */
    public DeviceApiResponse getBoundDevices(String token) {
        try {
            String url = ApiConfig.MY_SERVER_URL + "/device/list";
            LogUtil.d(TAG, "Get bound devices request: " + url);

            HttpURLConnection conn = (HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            int responseCode = conn.getResponseCode();
            LogUtil.d(TAG, "Get bound devices response code: " + responseCode);

            java.io.BufferedReader in = new java.io.BufferedReader(
                new java.io.InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ?
                    conn.getInputStream() : conn.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            conn.disconnect();

            String responseString = response.toString();
            LogUtil.d(TAG, "Get bound devices response: " + responseString);

            DeviceApiResponse apiResponse = new DeviceApiResponse();
            apiResponse.setStatusCode(responseCode);
            apiResponse.setRawResponse(responseString);

            if (responseCode >= 200 && responseCode < 300 && responseString != null) {
                try {
                    JsonParser parser = new JsonParser();
                    JsonObject json = parser.parse(responseString).getAsJsonObject();

                    if (json.has("code") && !json.get("code").isJsonNull()) {
                        apiResponse.setCode(json.get("code").getAsString());
                    }
                    if (json.has("message") && !json.get("message").isJsonNull()) {
                        apiResponse.setMessage(json.get("message").getAsString());
                    }

                    // 解析设备列表（后端返回字段名为 "devices"）
                    String arrayKey = null;
                    if (json.has("devices") && json.get("devices").isJsonArray()) {
                        arrayKey = "devices";
                    } else if (json.has("data") && json.get("data").isJsonArray()) {
                        arrayKey = "data";
                    }
                    if (arrayKey != null) {
                        JsonArray dataArray = json.getAsJsonArray(arrayKey);
                        List<BoundDevice> devices = new ArrayList<>();

                        for (int i = 0; i < dataArray.size(); i++) {
                            JsonObject deviceObj = dataArray.get(i).getAsJsonObject();
                            BoundDevice device = new BoundDevice();
                            if (deviceObj.has("deviceNum") && !deviceObj.get("deviceNum").isJsonNull()) {
                                device.deviceNum = deviceObj.get("deviceNum").getAsString();
                            }
                            if (deviceObj.has("alias") && !deviceObj.get("alias").isJsonNull()) {
                                device.alias = deviceObj.get("alias").getAsString();
                            }
                            // 服务器返回的昵称字段名为 nickName，优先使用 nickName
                            if (deviceObj.has("nickName") && !deviceObj.get("nickName").isJsonNull()) {
                                String nickName = deviceObj.get("nickName").getAsString();
                                if (nickName != null && !nickName.isEmpty()) {
                                    device.nickName = nickName;
                                    device.alias = nickName;  // 同时赋值给 alias 以兼容旧逻辑
                                }
                            }
                            if (deviceObj.has("bindTime") && !deviceObj.get("bindTime").isJsonNull()) {
                                try { device.bindTime = deviceObj.get("bindTime").getAsLong(); } catch (Exception ignored) {}
                            }
                            if (deviceObj.has("createdAt") && !deviceObj.get("createdAt").isJsonNull()) {
                                try { device.bindTime = deviceObj.get("createdAt").getAsLong(); } catch (Exception ignored) {}
                            }
                            devices.add(device);
                        }
                        apiResponse.setDevices(devices);
                        LogUtil.d(TAG, "Parsed " + devices.size() + " bound devices");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parse bound devices error: " + e.getMessage());
                    apiResponse.setMessage("Parse error: " + e.getMessage());
                }
            }
            return apiResponse;
        } catch (Exception e) {
            Log.e(TAG, "Get bound devices failed: " + e.getMessage(), e);
            DeviceApiResponse apiResponse = new DeviceApiResponse();
            apiResponse.setStatusCode(-1);
            apiResponse.setMessage("Network error: " + e.getMessage());
            return apiResponse;
        }
    }

    /**
     * 解绑设备
     * @param token Bearer Token
     * @param deviceNum 设备号
     * @return DeviceApiResponse
     */
    public DeviceApiResponse unbindDevice(String token, String deviceNum) {
        try {
            String url = ApiConfig.MY_SERVER_URL + "/device/unbind";
            JsonObject params = new JsonObject();
            params.addProperty("deviceNum", deviceNum);

            LogUtil.d(TAG, "Unbind device request: " + url + ", deviceNum: " + deviceNum);
            HttpHelper.HttpResponse response = postWithAuth(url, params.toString(), token);

            return parseResponse(response);
        } catch (Exception e) {
            Log.e(TAG, "Unbind device failed: " + e.getMessage(), e);
            DeviceApiResponse apiResponse = new DeviceApiResponse();
            apiResponse.setStatusCode(-1);
            apiResponse.setMessage("Network error: " + e.getMessage());
            return apiResponse;
        }
    }

    /**
     * 更新设备别名
     * @param token Bearer Token
     * @param deviceNum 设备号
     * @param alias 新别名
     * @return DeviceApiResponse
     */
    public DeviceApiResponse updateAlias(String token, String deviceNum, String alias) {
        try {
            String url = ApiConfig.MY_SERVER_URL + "/device/alias";
            JsonObject params = new JsonObject();
            params.addProperty("deviceNum", deviceNum);
            params.addProperty("alias", alias);

            LogUtil.d(TAG, "Update alias request: " + url + ", deviceNum: " + deviceNum + ", alias: " + alias);
            HttpHelper.HttpResponse response = putWithAuth(url, params.toString(), token);

            return parseResponse(response);
        } catch (Exception e) {
            Log.e(TAG, "Update alias failed: " + e.getMessage(), e);
            DeviceApiResponse apiResponse = new DeviceApiResponse();
            apiResponse.setStatusCode(-1);
            apiResponse.setMessage("Network error: " + e.getMessage());
            return apiResponse;
        }
    }

    /**
     * 带认证的POST请求
     */
    private HttpHelper.HttpResponse postWithAuth(String urlString, String jsonBody, String token) {
        HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            LogUtil.d(TAG, "POST request body: " + jsonBody);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            java.io.BufferedReader in = new java.io.BufferedReader(
                new java.io.InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ?
                    conn.getInputStream() : conn.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            String responseString = response.toString();
            LogUtil.d(TAG, "POST response code: " + responseCode + ", body: " + responseString);

            return new HttpHelper.HttpResponse(responseCode, responseString, null);
        } catch (Exception e) {
            Log.e(TAG, "POST request failed: " + e.getMessage(), e);
            return new HttpHelper.HttpResponse(-1, null, e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 带认证的PUT请求
     */
    private HttpHelper.HttpResponse putWithAuth(String urlString, String jsonBody, String token) {
        HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            LogUtil.d(TAG, "PUT request body: " + jsonBody);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            java.io.BufferedReader in = new java.io.BufferedReader(
                new java.io.InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ?
                    conn.getInputStream() : conn.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            String responseString = response.toString();
            LogUtil.d(TAG, "PUT response code: " + responseCode + ", body: " + responseString);

            return new HttpHelper.HttpResponse(responseCode, responseString, null);
        } catch (Exception e) {
            Log.e(TAG, "PUT request failed: " + e.getMessage(), e);
            return new HttpHelper.HttpResponse(-1, null, e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 解析API响应
     */
    private DeviceApiResponse parseResponse(HttpHelper.HttpResponse response) {
        DeviceApiResponse apiResponse = new DeviceApiResponse();
        if (response == null) {
            apiResponse.setStatusCode(-1);
            apiResponse.setMessage("No response");
            return apiResponse;
        }

        apiResponse.setStatusCode(response.statusCode);
        apiResponse.setRawResponse(response.body);

        if (response.body == null || response.body.isEmpty()) {
            return apiResponse;
        }

        try {
            JsonParser parser = new JsonParser();
            JsonObject json = parser.parse(response.body).getAsJsonObject();

            if (json.has("code")) apiResponse.setCode(json.get("code").getAsString());
            if (json.has("message")) apiResponse.setMessage(json.get("message").getAsString());
            if (json.has("status")) apiResponse.setStatus(json.get("status").getAsInt());
        } catch (Exception e) {
            Log.e(TAG, "Parse response error: " + e.getMessage());
        }

        return apiResponse;
    }

    /**
     * 设备绑定API响应模型
     */
    public static class DeviceApiResponse {
        private int statusCode;
        private String rawResponse;
        private String code;
        private String message;
        private int status;
        private List<BoundDevice> devices;

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public void setRawResponse(String rawResponse) {
            this.rawResponse = rawResponse;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public List<BoundDevice> getDevices() {
            return devices;
        }

        public void setDevices(List<BoundDevice> devices) {
            this.devices = devices;
        }
    }

    /**
     * 绑定设备模型
     */
    public static class BoundDevice {
        public String deviceNum;
        public String alias;
        @com.google.gson.annotations.SerializedName("nickName")
        public String nickName;  // 服务器返回的昵称字段
        public long bindTime;

        public BoundDevice() {}

        public BoundDevice(String deviceNum, String alias, long bindTime) {
            this.deviceNum = deviceNum;
            this.alias = alias;
            this.nickName = alias;  // 兼容旧逻辑
            this.bindTime = bindTime;
        }

        public String getDeviceNum() {
            return deviceNum;
        }

        /**
         * 获取别名，优先返回 nickName，其次 alias，最后 deviceNum
         */
        public String getAlias() {
            if (nickName != null && !nickName.isEmpty()) {
                return nickName;
            }
            if (alias != null && !alias.isEmpty()) {
                return alias;
            }
            return deviceNum;
        }

        public long getBindTime() {
            return bindTime;
        }

        @Override
        public String toString() {
            return "BoundDevice{" +
                    "deviceNum='" + deviceNum + '\'' +
                    ", alias='" + alias + '\'' +
                    ", nickName='" + nickName + '\'' +
                    ", bindTime=" + bindTime +
                    '}';
        }
    }
}