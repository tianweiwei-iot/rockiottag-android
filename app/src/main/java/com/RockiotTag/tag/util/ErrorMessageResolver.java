package com.RockiotTag.tag.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.RockiotTag.tag.R;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;


/**
 * 友好的错误消息解析器
 * 
 * 将技术性错误转换为用户友好的提示信息
 */
public class ErrorMessageResolver {
    
    /**
     * 解析错误消息为用户友好的提示
     * 
     * @param context 上下文
     * @param error 异常对象
     * @return 用户友好的错误消息
     */
    public static String resolveErrorMessage(Context context, Throwable error) {
        if (error == null) {
            return context.getString(R.string.error_unknown);
        }
        
        // 网络超时
        if (error instanceof SocketTimeoutException) {
            return context.getString(R.string.error_network_timeout);
        }
        
        // 未知主机（DNS解析失败）
        if (error instanceof UnknownHostException) {
            return context.getString(R.string.error_network_unreachable);
        }
        
        // HTTP错误（通过消息内容判断）
        String errorMessage = error.getMessage();
        if (errorMessage != null) {
            if (errorMessage.contains("401") || errorMessage.contains("Unauthorized")) {
                return context.getString(R.string.error_auth_expired);
            } else if (errorMessage.contains("403") || errorMessage.contains("Forbidden")) {
                return context.getString(R.string.error_permission_denied);
            } else if (errorMessage.contains("404") || errorMessage.contains("Not Found")) {
                return context.getString(R.string.error_resource_not_found);
            } else if (errorMessage.contains("500") || errorMessage.contains("Internal Server Error")) {
                return context.getString(R.string.error_server_internal);
            }
        }
        
        // IO异常
        if (error instanceof IOException) {
            return context.getString(R.string.error_network_io);
        }
        
        // 检查网络连接
        if (!isNetworkAvailable(context)) {
            return context.getString(R.string.error_no_network);
        }
        
        // 默认错误消息
        if (errorMessage != null && !errorMessage.isEmpty()) {
            return errorMessage;
        }
        
        return context.getString(R.string.error_unknown);
    }
    
    /**
     * 检查网络是否可用
     */
    private static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}

