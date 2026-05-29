package com.RockiotTag.tag.usecase;

import android.util.Log;

import com.RockiotTag.tag.repository.BLERepository;

/**
 * 触发蜂鸣器的UseCase
 * 
 * 职责：
 * 1. 通过蓝牙控制设备蜂鸣器
 * 2. 2秒后自动关闭蜂鸣器
 * 
 * 这是一个无参数的UseCase示例
 * 
 * 使用示例：
 * <pre>
 * TriggerBuzzerUseCase useCase = new TriggerBuzzerUseCase(bleRepository);
 * useCase.execute(null).observe(this, resource -> {
 *     if (resource.isSuccess()) {
 *         showToast("蜂鸣器已触发");
 *     } else if (resource.isError()) {
 *         showToast(resource.message);
 *     }
 * });
 * </pre>
 */
public class TriggerBuzzerUseCase extends BaseUseCase<Void, Boolean> {
    
    private static final String TAG = "TriggerBuzzerUseCase";
    private static final long BUZZER_DURATION_MS = 2000; // 蜂鸣器持续时间
    
    private final BLERepository bleRepository;
    
    /**
     * 构造函数
     * 
     * @param bleRepository BLE数据仓库
     */
    public TriggerBuzzerUseCase(BLERepository bleRepository) {
        this.bleRepository = bleRepository;
    }
    
    /**
     * 执行触发蜂鸣器的业务逻辑
     * 
     * @param unused 不使用参数（传null）
     * @return true表示成功触发
     * @throws Exception 如果触发失败
     */
    @Override
    protected Boolean executeSync(Void unused) throws Exception {
        Log.d(TAG, "Triggering buzzer");
        
        // 1. 检查蓝牙连接状态
        if (!bleRepository.isConnected()) {
            Log.w(TAG, "Bluetooth not connected");
            throw new RuntimeException("设备未连接，请先连接设备");
        }
        
        try {
            // 2. 开启蜂鸣器
            bleRepository.controlBuzzer(true);
            Log.d(TAG, "Buzzer turned on");
            
            // 3. 等待指定时间后关闭
            Thread.sleep(BUZZER_DURATION_MS);
            
            // 4. 关闭蜂鸣器
            bleRepository.controlBuzzer(false);
            Log.d(TAG, "Buzzer turned off after " + BUZZER_DURATION_MS + "ms");
            
            return true;
            
        } catch (InterruptedException e) {
            // 线程被中断，确保关闭蜂鸣器
            bleRepository.controlBuzzer(false);
            Thread.currentThread().interrupt();
            throw new RuntimeException("蜂鸣器操作被中断");
            
        } catch (Exception e) {
            // 确保异常时也关闭蜂鸣器
            try {
                bleRepository.controlBuzzer(false);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to turn off buzzer", ex);
            }
            
            Log.e(TAG, "Error triggering buzzer: " + e.getMessage(), e);
            throw new RuntimeException("触发蜂鸣器失败: " + e.getMessage());
        }
    }
}
