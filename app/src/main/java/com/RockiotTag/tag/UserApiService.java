package com.RockiotTag.tag;

import android.util.Log;

import com.RockiotTag.tag.network.HttpHelper;
import com.RockiotTag.tag.util.LogUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.HttpURLConnection;

/**
 * 用户认证API服务
 * 处理用户注册、登录、个人资料管理
 */
public class UserApiService {
    private static final String TAG = "UserApiService";
    private static UserApiService instance;

    private UserApiService() {}

    public static UserApiService getInstance() {
        if (instance == null) {
            instance = new UserApiService();
        }
        return instance;
    }

    /**
     * 用户注册（仅账号+密码）
     * @param username 登录账号（4-64位，字母/数字/下划线，不能纯数字）
     * @param password 登录密码（>=8位，含大小写字母和数字）
     * @return NewApiService.ApiResponse
     */
    public NewApiService.ApiResponse register(String username, String password) {
        try {
            String url = ApiConfig.MY_SERVER_URL + "/user/register";
            JsonObject params = new JsonObject();
            params.addProperty("username", username);
            params.addProperty("password", password);

            LogUtil.d(TAG, "Register request: " + url);
            HttpHelper.HttpResponse response = HttpHelper.post(url, params.toString());

            return parseAuthResponse(response);
        } catch (Exception e) {
            Log.e(TAG, "Register failed: " + e.getMessage(), e);
            NewApiService.ApiResponse apiResponse = new NewApiService.ApiResponse();
            apiResponse.setStatusCode(-1);
            apiResponse.setMessage("Network error: " + e.getMessage());
            return apiResponse;
        }
    }

    /**
     * 用户登录（账号+密码）
     * @param username 登录账号
     * @param password 登录密码
     * @return NewApiService.ApiResponse（成功时包含token）
     */
    public NewApiService.ApiResponse login(String username, String password) {
        try {
            String url = ApiConfig.MY_SERVER_URL + "/user/login";
            JsonObject params = new JsonObject();
            params.addProperty("username", username);
            params.addProperty("password", password);

            LogUtil.d(TAG, "Login request: " + url);
            HttpHelper.HttpResponse response = HttpHelper.post(url, params.toString());

            return parseAuthResponse(response);
        } catch (Exception e) {
            Log.e(TAG, "Login failed: " + e.getMessage(), e);
            NewApiService.ApiResponse apiResponse = new NewApiService.ApiResponse();
            apiResponse.setStatusCode(-1);
            apiResponse.setMessage("Network error: " + e.getMessage());
            return apiResponse;
        }
    }

    /**
     * 用户登出（作废当前Token）
     * @param token Bearer Token
     * @return 是否成功
     */
    public boolean logout(String token) {
        try {
            String url = ApiConfig.MY_SERVER_URL + "/user/logout";
            LogUtil.d(TAG, "Logout request: " + url);

            HttpURLConnection conn = (HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.getOutputStream().write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            int responseCode = conn.getResponseCode();
            LogUtil.d(TAG, "Logout response code: " + responseCode);
            conn.disconnect();

            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            Log.e(TAG, "Logout failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取用户资料
     * @param token Bearer Token
     * @return UserProfile
     */
    public UserProfile getUserProfile(String token) {
        try {
            String url = ApiConfig.MY_SERVER_URL + "/user/profile";
            LogUtil.d(TAG, "Get profile request: " + url);

            HttpURLConnection conn = (HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            int responseCode = conn.getResponseCode();
            LogUtil.d(TAG, "Get profile response code: " + responseCode);

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
            LogUtil.d(TAG, "Get profile response: " + responseString);

            if (responseCode >= 200 && responseCode < 300 && responseString != null) {
                Gson gson = new Gson();
                JsonParser parser = new JsonParser();
                JsonObject json = parser.parse(responseString).getAsJsonObject();

                UserProfile profile = new UserProfile();
                if (json.has("username")) profile.username = json.get("username").getAsString();
                if (json.has("nickname")) profile.nickname = json.get("nickname").getAsString();
                if (json.has("email")) profile.email = json.get("email").getAsString();
                if (json.has("phone")) profile.phone = json.get("phone").getAsString();
                if (json.has("emailBindTime")) profile.emailBindTime = json.get("emailBindTime").getAsLong();
                if (json.has("phoneBindTime")) profile.phoneBindTime = json.get("phoneBindTime").getAsLong();
                return profile;
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Get profile failed: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 修改昵称
     */
    public NewApiService.ApiResponse updateNickname(String token, String nickname) {
        return putWithToken("/user/nickname", token, "nickname", nickname);
    }

    /**
     * 修改邮箱
     */
    public NewApiService.ApiResponse updateEmail(String token, String email) {
        return putWithToken("/user/email", token, "email", email);
    }

    /**
     * 修改密码
     */
    public NewApiService.ApiResponse updatePassword(String token, String oldPassword, String newPassword) {
        try {
            String url = ApiConfig.MY_SERVER_URL + "/user/password";
            JsonObject params = new JsonObject();
            params.addProperty("oldPassword", oldPassword);
            params.addProperty("newPassword", newPassword);

            LogUtil.d(TAG, "Update password request: " + url);
            HttpHelper.HttpResponse response = putWithAuth(url, params.toString(), token);
            return parseAuthResponse(response);
        } catch (Exception e) {
            Log.e(TAG, "Update password failed: " + e.getMessage(), e);
            NewApiService.ApiResponse apiResponse = new NewApiService.ApiResponse();
            apiResponse.setStatusCode(-1);
            apiResponse.setMessage("Network error: " + e.getMessage());
            return apiResponse;
        }
    }

    private NewApiService.ApiResponse putWithToken(String endpoint, String token, String key, String value) {
        try {
            String url = ApiConfig.MY_SERVER_URL + endpoint;
            JsonObject params = new JsonObject();
            params.addProperty(key, value);

            LogUtil.d(TAG, "PUT request: " + url);
            HttpHelper.HttpResponse response = putWithAuth(url, params.toString(), token);
            return parseAuthResponse(response);
        } catch (Exception e) {
            Log.e(TAG, "PUT request failed: " + e.getMessage(), e);
            NewApiService.ApiResponse apiResponse = new NewApiService.ApiResponse();
            apiResponse.setStatusCode(-1);
            apiResponse.setMessage("Network error: " + e.getMessage());
            return apiResponse;
        }
    }

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

    private NewApiService.ApiResponse parseAuthResponse(HttpHelper.HttpResponse response) {
        NewApiService.ApiResponse apiResponse = new NewApiService.ApiResponse();
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

            if (json.has("token")) apiResponse.setToken(json.get("token").getAsString());
            if (json.has("username")) {
                String username = json.get("username").getAsString();
                // 复用name字段存储username
                apiResponse.setName(username);
            }
            if (json.has("nickname")) apiResponse.setNickName(json.get("nickname").getAsString());
            if (json.has("email")) {
                // 存储email到cid字段（复用）
                apiResponse.setCid(json.get("email").getAsString());
            }
            if (json.has("code")) apiResponse.setCode(json.get("code").getAsString());
            if (json.has("message")) apiResponse.setMessage(json.get("message").getAsString());
            if (json.has("status")) apiResponse.setStatus(json.get("status").getAsInt());
        } catch (Exception e) {
            Log.e(TAG, "Parse auth response error: " + e.getMessage());
        }

        return apiResponse;
    }

    /**
     * 用户资料模型
     */
    public static class UserProfile {
        public String username;
        public String nickname;
        public String email;
        public String phone;
        public long emailBindTime;
        public long phoneBindTime;

        public boolean isEmailBound() {
            return email != null && !email.isEmpty();
        }

        public boolean isPhoneBound() {
            return phone != null && !phone.isEmpty();
        }
    }

    /**
     * 校验用户名格式
     * 规则：4-64位，仅字母/数字/下划线，不能纯数字
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.length() < 4 || username.length() > 64) return false;
        if (!username.matches("^[a-zA-Z0-9_]+$")) return false;
        // 不能纯数字
        if (username.matches("^[0-9]+$")) return false;
        return true;
    }

    /**
     * 校验密码格式
     * 规则：>=8位，必须包含大写字母+小写字母+数字
     */
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasUpper = false, hasLower = false, hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            if (Character.isLowerCase(c)) hasLower = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        return hasUpper && hasLower && hasDigit;
    }
}
