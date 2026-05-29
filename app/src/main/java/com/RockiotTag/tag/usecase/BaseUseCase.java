package com.RockiotTag.tag.usecase;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.RockiotTag.tag.model.Resource;

/**
 * UseCase基类，提供统一的执行框架
 * 
 * 这是MVVM架构中的核心抽象类，所有业务逻辑UseCase都应该继承此类。
 * 它提供了：
 * 1. 统一的异步执行机制（后台线程执行业务逻辑）
 * 2. 统一的状态管理（通过Resource包装返回结果）
 * 3. 统一的错误处理
 * 
 * 使用示例：
 * <pre>
 * public class GetDeviceInfoUseCase extends BaseUseCase<String, DeviceInfo> {
 *     {@literal @}Override
 *     protected DeviceInfo executeSync(String deviceNum) throws Exception {
 *         // 在后台线程执行的业务逻辑
 *         return repository.getDeviceInfo(deviceNum);
 *     }
 * }
 * 
 * // 使用
 * useCase.execute("123456").observe(this, resource -> {
 *     if (resource.isSuccess()) {
 *         updateUI(resource.data);
 *     }
 * });
 * </pre>
 * 
 * @param <Params> 输入参数类型，如果不需要参数可以使用Void
 * @param <Result> 输出结果类型
 */
public abstract class BaseUseCase<Params, Result> {
    
    private static final String TAG = "BaseUseCase";
    
    /**
     * 执行UseCase，返回LiveData
     * 
     * 这个方法会：
     * 1. 立即返回一个LOADING状态的LiveData
     * 2. 在后台线程执行executeSync方法
     * 3. 根据执行结果更新LiveData为SUCCESS或ERROR状态
     * 
     * @param params 输入参数
     * @return LiveData<Resource<Result>> 可观察的结果
     */
    public LiveData<Resource<Result>> execute(Params params) {
        MutableLiveData<Resource<Result>> result = new MutableLiveData<>();
        
        // 立即返回loading状态
        result.setValue(Resource.loading(null));
        
        Log.d(TAG, "UseCase started: " + getClass().getSimpleName());
        
        // 在后台线程执行
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // 执行具体的业务逻辑（由子类实现）
                Result data = executeSync(params);
                
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG, "UseCase completed: " + getClass().getSimpleName() 
                    + " in " + duration + "ms");
                
                // 在主线程返回成功结果
                new Handler(Looper.getMainLooper()).post(() -> {
                    result.setValue(Resource.success(data));
                });
                
            } catch (Exception e) {
                Log.e(TAG, "UseCase failed: " + getClass().getSimpleName(), e);
                
                // 在主线程返回错误结果
                new Handler(Looper.getMainLooper()).post(() -> {
                    String errorMessage = e.getMessage() != null 
                        ? e.getMessage() 
                        : "Unknown error";
                    result.setValue(Resource.error(errorMessage, null));
                });
            }
        }).start();
        
        return result;
    }
    
    /**
     * 同步执行业务逻辑（子类必须实现）
     * 
     * 这个方法会在后台线程中执行，因此可以执行耗时操作（网络请求、数据库查询等）。
     * 如果执行失败，应该抛出异常，基类会自动捕获并转换为ERROR状态。
     * 
     * @param params 输入参数
     * @return 执行结果
     * @throws Exception 执行失败时抛出异常
     */
    protected abstract Result executeSync(Params params) throws Exception;
}
