package com.RockiotTag.tag.network;

import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import com.RockiotTag.tag.ApiConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP 网络请求工具类
 * 
 * 优势：
 * 1. 消除 NewApiService 中的重复代码
 * 2. 统一的超时和错误处理
 * 3. 支持 GET/POST 请求
 * 4. 自动资源管理
 * 5. 支持多客户 API Key
 */
public class HttpHelper {
    
    private static final String TAG = "HttpHelper";
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "(empty)";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
    
    /**
     * 执行 GET 请求（使用默认 API Key）
     */
    public static HttpResponse get(String urlString) {
        return get(urlString, null);
    }
    
    /**
     * 执行 GET 请求（指定客户代码）
     * 
     * @param urlString 完整的 URL
     * @param customerCode 客户代码，为空则使用默认
     * @return 响应字符串，失败返回 null
     */
    public static HttpResponse get(String urlString, String customerCode) {
        HttpURLConnection conn = null;
        try {
            String apiKey = ApiConfig.getApiKeyForCustomer(customerCode);
            LogUtil.d(TAG, "GET request URL: " + urlString);
            LogUtil.d(TAG, "GET request API Key: " + maskApiKey(apiKey));
            
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            return executeRequest(conn);
        } catch (Exception e) {
            Log.e(TAG, "GET request failed: " + e.getMessage(), e);
            return new HttpResponse(-1, null, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * 执行 POST 请求（使用默认 API Key）
     */
    public static HttpResponse post(String urlString, String jsonBody) {
        return post(urlString, jsonBody, null);
    }
    
    /**
     * 执行 POST 请求（指定客户代码）
     * 
     * @param urlString 完整的 URL
     * @param jsonBody JSON 字符串
     * @param customerCode 客户代码，为空则使用默认
     * @return 响应字符串，失败返回 null
     */
    public static HttpResponse post(String urlString, String jsonBody, String customerCode) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-API-Key", ApiConfig.getApiKeyForCustomer(customerCode));
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            
            LogUtil.d(TAG, "Request body: " + jsonBody);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            return executeRequest(conn);
        } catch (Exception e) {
            Log.e(TAG, "POST request failed: " + e.getMessage(), e);
            return new HttpResponse(-1, null, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * 执行 GET 请求（使用 Bearer Token 认证）
     *
     * @param urlString 完整的 URL
     * @param token     Bearer Token，为空则不添加 Authorization 头
     * @return HttpResponse
     */
    public static HttpResponse getWithAuth(String urlString, String token) {
        HttpURLConnection conn = null;
        try {
            LogUtil.d(TAG, "GET (auth) request URL: " + urlString);

            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);

            return executeRequest(conn);
        } catch (Exception e) {
            Log.e(TAG, "GET (auth) request failed: " + e.getMessage(), e);
            return new HttpResponse(-1, null, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 执行 POST 请求（使用 Bearer Token 认证）
     *
     * @param urlString 完整的 URL
     * @param jsonBody  JSON 字符串
     * @param token     Bearer Token，为空则不添加 Authorization 头
     * @return HttpResponse
     */
    public static HttpResponse postWithAuth(String urlString, String jsonBody, String token) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);

            LogUtil.d(TAG, "POST (auth) request body: " + jsonBody);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            return executeRequest(conn);
        } catch (Exception e) {
            Log.e(TAG, "POST (auth) request failed: " + e.getMessage(), e);
            return new HttpResponse(-1, null, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 执行 PUT 请求（使用 Bearer Token 认证）
     *
     * @param urlString 完整的 URL
     * @param jsonBody  JSON 字符串
     * @param token     Bearer Token，为空则不添加 Authorization 头
     * @return HttpResponse
     */
    public static HttpResponse putWithAuth(String urlString, String jsonBody, String token) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);

            LogUtil.d(TAG, "PUT (auth) request body: " + jsonBody);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            return executeRequest(conn);
        } catch (Exception e) {
            Log.e(TAG, "PUT (auth) request failed: " + e.getMessage(), e);
            return new HttpResponse(-1, null, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 执行请求并读取响应
     */
    private static HttpResponse executeRequest(HttpURLConnection conn) throws Exception {
        int responseCode = conn.getResponseCode();
        LogUtil.d(TAG, "Response code: " + responseCode);
        
        BufferedReader in;
        try {
            in = new BufferedReader(new InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ? 
                    conn.getInputStream() : conn.getErrorStream()));
        } catch (Exception e) {
            return new HttpResponse(responseCode, null, "Network Stream Error");
        }
        
        String inputLine;
        StringBuilder response = new StringBuilder();
        
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        
        String responseString = response.toString();
        if (responseCode >= 200 && responseCode < 300) {
            LogUtil.d(TAG, "Response: " + responseString);
        } else {
            Log.e(TAG, "Error response: " + responseString);
        }
        
        return new HttpResponse(responseCode, responseString, null);
    }
    
    /**
     * HTTP 响应封装类
     */
    public static class HttpResponse {
        public final int statusCode;
        public final String body;
        public final String error;
        
        public HttpResponse(int statusCode, String body, String error) {
            this.statusCode = statusCode;
            this.body = body;
            this.error = error;
        }
        
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300 && body != null;
        }
    }
}
