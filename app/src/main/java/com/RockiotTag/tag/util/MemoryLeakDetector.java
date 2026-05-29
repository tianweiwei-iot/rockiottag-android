package com.RockiotTag.tag.util;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 内存泄漏检测工具
 * 
 * 功能：
 * 1. 跟踪 Activity 生命周期
 * 2. 检测可能的内存泄漏
 * 3. 提供泄漏警告
 */
public class MemoryLeakDetector implements Application.ActivityLifecycleCallbacks {
    
    private static final String TAG = "MemoryLeakDetector";
    private static MemoryLeakDetector instance;
    
    private final List<WeakReference<Activity>> activityReferences = new ArrayList<>();
    private int activeActivityCount = 0;
    
    public static synchronized MemoryLeakDetector getInstance() {
        if (instance == null) {
            instance = new MemoryLeakDetector();
        }
        return instance;
    }
    
    /**
     * 注册到 Application
     */
    public void register(Application application) {
        application.registerActivityLifecycleCallbacks(this);
        Log.d(TAG, "MemoryLeakDetector registered");
    }
    
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        activityReferences.add(new WeakReference<>(activity));
        activeActivityCount++;
        Log.d(TAG, "Activity created: " + activity.getClass().getSimpleName() + 
              " (active: " + activeActivityCount + ")");
    }
    
    @Override
    public void onActivityStarted(Activity activity) {
        Log.d(TAG, "Activity started: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivityResumed(Activity activity) {
        Log.d(TAG, "Activity resumed: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivityPaused(Activity activity) {
        Log.d(TAG, "Activity paused: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivityStopped(Activity activity) {
        Log.d(TAG, "Activity stopped: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        // No-op
    }
    
    @Override
    public void onActivityDestroyed(Activity activity) {
        activeActivityCount--;
        Log.d(TAG, "Activity destroyed: " + activity.getClass().getSimpleName() + 
              " (active: " + activeActivityCount + ")");
        
        // 清理已销毁的 Activity 引用
        cleanupDestroyedActivities();
        
        // 检查是否有泄漏
        checkForLeaks();
    }
    
    /**
     * 清理已销毁的 Activity 引用
     */
    private void cleanupDestroyedActivities() {
        activityReferences.removeIf(ref -> ref.get() == null);
    }
    
    /**
     * 检查内存泄漏
     */
    private void checkForLeaks() {
        if (activeActivityCount == 0 && !activityReferences.isEmpty()) {
            Log.w(TAG, "Potential memory leak detected!");
            Log.w(TAG, "Active activities: 0, but " + activityReferences.size() + 
                  " references still exist");
            
            for (WeakReference<Activity> ref : activityReferences) {
                Activity activity = ref.get();
                if (activity != null) {
                    Log.w(TAG, "Leaked activity: " + activity.getClass().getSimpleName());
                }
            }
        }
    }
    
    /**
     * 获取当前活跃的 Activity 数量
     */
    public int getActiveActivityCount() {
        return activeActivityCount;
    }
    
    /**
     * 强制垃圾回收并检查泄漏（仅用于调试）
     */
    public void forceGcAndCheck() {
        Log.d(TAG, "Forcing GC and checking for leaks...");
        
        System.gc();
        System.runFinalization();
        System.gc();
        
        cleanupDestroyedActivities();
        checkForLeaks();
        
        Log.d(TAG, "After GC - Active: " + activeActivityCount + 
              ", References: " + activityReferences.size());
    }
    
    /**
     * 打印当前状态（用于调试）
     */
    public void printStatus() {
        Log.d(TAG, "=== Memory Leak Detector Status ===");
        Log.d(TAG, "Active activities: " + activeActivityCount);
        Log.d(TAG, "Total references: " + activityReferences.size());
        
        int leakedCount = 0;
        for (WeakReference<Activity> ref : activityReferences) {
            Activity activity = ref.get();
            if (activity != null) {
                Log.d(TAG, "  - " + activity.getClass().getSimpleName() + 
                      " (" + (activity.isFinishing() ? "finishing" : "active") + ")");
                if (activity.isFinishing()) {
                    leakedCount++;
                }
            }
        }
        
        if (leakedCount > 0) {
            Log.w(TAG, "Warning: " + leakedCount + " activities are finishing but not garbage collected");
        }
        Log.d(TAG, "=====================================");
    }
}
