package com.RockiotTag.tag.usecase;

import android.util.Log;

import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.repository.LocationRepository;
import com.RockiotTag.tag.util.LogUtil;

import java.util.List;

/**
 * 加载轨迹数据的UseCase
 * 
 * 职责：
 * 1. 从服务器获取设备轨迹数据
 * 2. 按时间范围过滤
 * 3. 按时间排序
 */
public class LoadTrackDataUseCase extends BaseUseCase<LoadTrackDataUseCase.Params, List<LocationRecord>> {
    
    private static final String TAG = "LoadTrackDataUseCase";
    
    public static class Params {
        public final String deviceNum;
        public final long startTime;
        public final long endTime;
        
        public Params(String deviceNum, long startTime, long endTime) {
            this.deviceNum = deviceNum;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
    
    private final LocationRepository locationRepository;
    
    public LoadTrackDataUseCase(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }
    
    @Override
    protected List<LocationRecord> executeSync(Params params) throws Exception {
        LogUtil.d(TAG, "Loading track data for device: " + params.deviceNum);
        
        // 1. 参数验证
        if (params.deviceNum == null || params.deviceNum.isEmpty()) {
            throw new IllegalArgumentException("设备编号不能为空");
        }
        
        if (params.startTime <= 0 || params.endTime <= 0) {
            throw new IllegalArgumentException("时间范围无效");
        }
        
        if (params.startTime > params.endTime) {
            throw new IllegalArgumentException("开始时间不能大于结束时间");
        }
        
        // 2. 从服务器获取轨迹数据
        List<LocationRecord> records = locationRepository.getTrackData(
            params.deviceNum,
            params.startTime,
            params.endTime
        );
        
        if (records == null) {
            throw new RuntimeException("无法获取轨迹数据");
        }
        
        LogUtil.d(TAG, "Loaded " + records.size() + " location records");
        
        // 3. 按时间排序（升序）
        records.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
        
        return records;
    }
}
