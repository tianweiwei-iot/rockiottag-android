package com.RockiotTag.tag.util;

import android.util.Log;

/**
 * 用户友好的错误提示工具类
 * 将技术性错误信息转换为用户可理解的提示
 */
public class UserFriendlyError {
    private static final String TAG = "UserFriendlyError";
    
    /**
     * 将技术错误转换为用户友好的消息
     * @param technicalError 技术性错误信息
     * @return 用户友好的错误提示
     */
    public static String getUserMessage(String technicalError) {
        if (technicalError == null || technicalError.isEmpty()) {
            return "操作失败，请稍后重试";
        }
        
        String error = technicalError.toLowerCase();
        
        // 网络相关错误
        if (error.contains("timeout") || error.contains("timed out")) {
            return "网络连接超时，请检查网络设置后重试";
        }
        if (error.contains("no internet") || error.contains("network unavailable") || 
            error.contains("unable to resolve host")) {
            return "网络连接不可用，请检查网络连接";
        }
        if (error.contains("connection refused") || error.contains("connect failed")) {
            return "无法连接到服务器，请稍后重试";
        }
        
        // 认证相关错误
        if (error.contains("unauthorized") || error.contains("401")) {
            return "认证失败，请重新登录";
        }
        if (error.contains("forbidden") || error.contains("403")) {
            return "没有权限执行此操作";
        }
        if (error.contains("token expired") || error.contains("session expired")) {
            return "登录已过期，请重新登录";
        }
        
        // 设备相关错误
        if (error.contains("not found") || error.contains("404")) {
            return "设备不存在，请检查设备编号";
        }
        if (error.contains("device not connected") || error.contains("bluetooth disconnected")) {
            return "设备未连接，请先连接设备";
        }
        if (error.contains("device busy")) {
            return "设备忙，请稍后重试";
        }
        
        // 位置相关错误
        if (error.contains("location permission") || error.contains("gps permission")) {
            return "需要位置权限，请在设置中授权";
        }
        if (error.contains("gps disabled") || error.contains("location disabled")) {
            return "GPS已关闭，请开启定位服务";
        }
        if (error.contains("location unavailable")) {
            return "暂时无法获取位置信息";
        }
        
        // 蓝牙相关错误
        if (error.contains("bluetooth not enabled") || error.contains("bluetooth off")) {
            return "蓝牙未开启，请开启蓝牙";
        }
        if (error.contains("bluetooth permission")) {
            return "需要蓝牙权限，请在设置中授权";
        }
        
        // 数据库相关错误
        if (error.contains("database") || error.contains("sqlite")) {
            return "数据操作失败，请稍后重试";
        }
        
        // 服务器相关错误
        if (error.contains("500") || error.contains("internal server error")) {
            return "服务器错误，请稍后重试";
        }
        if (error.contains("502") || error.contains("503") || error.contains("service unavailable")) {
            return "服务暂时不可用，请稍后重试";
        }
        
        // 解析相关错误
        if (error.contains("parse error") || error.contains("json") || error.contains("malformed")) {
            return "数据格式错误，请联系技术支持";
        }
        
        // 默认错误
        Log.w(TAG, "Unmapped error: " + technicalError);
        return "操作失败，请稍后重试";
    }
    
    /**
     * 根据错误代码获取用户友好消息
     * @param errorCode 错误代码
     * @return 用户友好的错误提示
     */
    public static String getMessageByCode(int errorCode) {
        switch (errorCode) {
            case 401:
                return "认证失败，请重新登录";
            case 403:
                return "没有权限执行此操作";
            case 404:
                return "设备不存在，请检查设备编号";
            case 500:
                return "服务器错误，请稍后重试";
            case 502:
            case 503:
                return "服务暂时不可用，请稍后重试";
            default:
                return "操作失败（错误码：" + errorCode + "），请稍后重试";
        }
    }
}
