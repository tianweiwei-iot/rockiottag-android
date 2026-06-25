package com.RockiotTag.tag;

import android.util.Log;

import com.RockiotTag.tag.network.HttpHelper;
import com.RockiotTag.tag.util.LogUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

            HttpHelper.HttpResponse response = HttpHelper.postWithAuth(url, "{}", token);
            LogUtil.d(TAG, "Logout response code: " + response.statusCode);

            return response.statusCode >= 200 && response.statusCode < 300;
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

            HttpHelper.HttpResponse response = HttpHelper.getWithAuth(url, token);
            int responseCode = response.statusCode;
            LogUtil.d(TAG, "Get profile response code: " + responseCode);

            String responseString = response.body;
            LogUtil.d(TAG, "Get profile response: " + responseString);

            if (responseCode >= 200 && responseCode < 300 && responseString != null) {
                Gson gson = new Gson();
                JsonObject json = JsonParser.parseString(responseString).getAsJsonObject();

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
     * 用户资料更新结果（昵称与头像可分别成功）
     */
    public static class ProfileUpdateResult {
        public final boolean nicknameSynced;
        public final boolean avatarSynced;
        public final String errorMessage;

        public ProfileUpdateResult(boolean nicknameSynced, boolean avatarSynced, String errorMessage) {
            this.nicknameSynced = nicknameSynced;
            this.avatarSynced = avatarSynced;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return nicknameSynced;
        }
    }

    /**
     * HTTP 2xx 视为成功（兼容 204 / 空 body）；若 body 含明确业务错误码则判失败。
     */
    public static boolean isOperationSuccess(NewApiService.ApiResponse response) {
        if (response == null) {
            return false;
        }
        int httpCode = response.getStatusCode();
        if (httpCode < 200 || httpCode >= 300) {
            return false;
        }
        String code = response.getCode();
        if (code != null && !code.isEmpty() && !"0000".equals(code) && !"0".equals(code)) {
            int status = response.getStatus();
            if (status != 0 && status != 200) {
                return false;
            }
        }
        return true;
    }

    /**
     * 同步昵称与头像到服务端（优先合并请求，失败时分字段重试）
     */
    public ProfileUpdateResult updateProfile(String token, String nickname, Integer avatarIndex,
                                             boolean syncNickname, boolean syncAvatar) {
        if (syncNickname || syncAvatar) {
            NewApiService.ApiResponse combined = putProfileFields(token,
                    syncNickname ? nickname : null,
                    syncAvatar ? avatarIndex : null);
            if (isOperationSuccess(combined)) {
                return new ProfileUpdateResult(true, true, null);
            }
            LogUtil.w(TAG, "PUT /user/profile failed: " + combined.getStatusCode()
                    + " " + combined.getMessage());
        }

        boolean nicknameOk = !syncNickname;
        boolean avatarOk = !syncAvatar;
        String errorMessage = null;

        if (syncNickname) {
            NewApiService.ApiResponse nickResp = updateNickname(token, nickname);
            nicknameOk = isOperationSuccess(nickResp);
            if (!nicknameOk) {
                errorMessage = nickResp.getMessage();
                LogUtil.w(TAG, "Update nickname failed: " + nickResp.getStatusCode()
                        + " " + errorMessage);
            }
        }

        if (syncAvatar && avatarIndex != null) {
            NewApiService.ApiResponse avatarResp = updateAvatar(token, String.valueOf(avatarIndex));
            avatarOk = isOperationSuccess(avatarResp);
            if (!avatarOk) {
                LogUtil.w(TAG, "Update avatar failed: " + avatarResp.getStatusCode()
                        + " " + avatarResp.getMessage());
            }
        }

        return new ProfileUpdateResult(nicknameOk, avatarOk, errorMessage);
    }

    /**
     * 修改昵称
     */
    public NewApiService.ApiResponse updateNickname(String token, String nickname) {
        NewApiService.ApiResponse response = putWithToken("/user/nickname", token, "nickname", nickname);
        if (!isOperationSuccess(response)) {
            LogUtil.w(TAG, "/user/nickname failed (" + response.getStatusCode()
                    + "), trying /user/profile");
            response = putWithToken("/user/profile", token, "nickname", nickname);
        }
        if (!isOperationSuccess(response)) {
            LogUtil.w(TAG, "/user/profile nickname failed (" + response.getStatusCode()
                    + "), trying /user/username with nickname field");
            response = putWithToken("/user/username", token, "nickname", nickname);
        }
        if (!isOperationSuccess(response) && isValidUsername(nickname)) {
            LogUtil.w(TAG, "Trying /user/username with username field");
            response = putWithToken("/user/username", token, "username", nickname);
        }
        return response;
    }

    /**
     * 修改邮箱
     */
    public NewApiService.ApiResponse updateEmail(String token, String email) {
        return putWithToken("/user/email", token, "email", email);
    }

    /**
     * 修改头像
     */
    public NewApiService.ApiResponse updateAvatar(String token, String avatarIndex) {
        NewApiService.ApiResponse response = putWithToken("/user/avatar", token, "avatar", avatarIndex);
        if (!isOperationSuccess(response)) {
            LogUtil.w(TAG, "/user/avatar failed (" + response.getStatusCode()
                    + "), trying avatarIndex field");
            response = putWithToken("/user/avatar", token, "avatarIndex", avatarIndex);
        }
        if (!isOperationSuccess(response)) {
            LogUtil.w(TAG, "/user/avatar failed, trying /user/profile");
            response = putWithToken("/user/profile", token, "avatar", avatarIndex);
        }
        return response;
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

            LogUtil.d(TAG, "PUT request: " + url + " {" + key + ": ...}");
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

    private NewApiService.ApiResponse putProfileFields(String token, String nickname, Integer avatarIndex) {
        try {
            String url = ApiConfig.MY_SERVER_URL + "/user/profile";
            JsonObject params = new JsonObject();
            if (nickname != null) {
                params.addProperty("nickname", nickname);
            }
            if (avatarIndex != null) {
                params.addProperty("avatar", String.valueOf(avatarIndex));
            }
            if (params.size() == 0) {
                NewApiService.ApiResponse empty = new NewApiService.ApiResponse();
                empty.setStatusCode(400);
                empty.setMessage("Nothing to update");
                return empty;
            }

            LogUtil.d(TAG, "PUT profile request: " + url);
            HttpHelper.HttpResponse response = putWithAuth(url, params.toString(), token);
            return parseAuthResponse(response);
        } catch (Exception e) {
            Log.e(TAG, "PUT profile failed: " + e.getMessage(), e);
            NewApiService.ApiResponse apiResponse = new NewApiService.ApiResponse();
            apiResponse.setStatusCode(-1);
            apiResponse.setMessage("Network error: " + e.getMessage());
            return apiResponse;
        }
    }

    private HttpHelper.HttpResponse putWithAuth(String urlString, String jsonBody, String token) {
        return HttpHelper.putWithAuth(urlString, jsonBody, token);
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
            JsonObject json = JsonParser.parseString(response.body).getAsJsonObject();

            if (json.has("token")) apiResponse.setToken(json.get("token").getAsString());
            if (json.has("username")) {
                String username = json.get("username").getAsString();
                // 复用name字段存储username
                apiResponse.setName(username);
            }
            if (json.has("nickname")) {
                apiResponse.setNickName(json.get("nickname").getAsString());
            } else if (json.has("nickName")) {
                apiResponse.setNickName(json.get("nickName").getAsString());
            }
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
