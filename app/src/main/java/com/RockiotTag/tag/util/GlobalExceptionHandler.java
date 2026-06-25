package com.RockiotTag.tag.util;

import android.content.Context;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局异常处理器
 * 
 * 捕获未处理的异常，记录日志并上报（可选）
 */
public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {
    
    private static final String TAG = "GlobalExceptionHandler";
    private static final String CRASH_LOG_DIR = "crash_logs";
    
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;
    
    public GlobalExceptionHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }
    
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        Log.e(TAG, "Uncaught exception in thread: " + thread.getName(), ex);
        
        // 保存崩溃日志
        saveCrashLog(thread, ex);
        
        // 这里可以添加崩溃上报逻辑（如Firebase Crashlytics）
        // reportCrashToServer(ex);
        
        // 调用默认处理器（通常会终止应用）
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        } else {
            // 如果没有默认处理器，终止进程
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }
    
    /**
     * 保存崩溃日志到文件
     */
    private void saveCrashLog(Thread thread, Throwable ex) {
        try {
            // 创建日志目录
            File logDir = new File(context.getExternalFilesDir(null), CRASH_LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // 生成日志文件名
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            String fileName = "crash_" + timestamp + ".log";
            File logFile = new File(logDir, fileName);
            
            // 写入日志内容
            StringBuilder sb = new StringBuilder();
            sb.append("=== Crash Report ===\n");
            sb.append("Time: ").append(timestamp).append("\n");
            sb.append("Thread: ").append(thread.getName()).append("\n");
            sb.append("Device: ").append(android.os.Build.MODEL).append("\n");
            sb.append("Android Version: ").append(android.os.Build.VERSION.RELEASE).append("\n");
            sb.append("App Version: ").append(getAppVersion()).append("\n");
            sb.append("\n=== Exception ===\n");
            sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append("\n");
            sb.append("\n=== Stack Trace ===\n");
            sb.append(Log.getStackTraceString(ex));
            sb.append("\n==================\n\n");
            
            // 写入文件
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(sb.toString());
            }
            
            LogUtil.d(TAG, "Crash log saved to: " + logFile.getAbsolutePath());
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to save crash log", e);
        }
    }
    
    /**
     * 获取应用版本号
     */
    private String getAppVersion() {
        try {
            android.content.pm.PackageInfo packageInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get app version", e);
            return "unknown";
        }
    }
}
