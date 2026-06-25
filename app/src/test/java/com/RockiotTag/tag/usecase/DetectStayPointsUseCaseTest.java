package com.RockiotTag.tag.usecase;

import static org.junit.Assert.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;

import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.model.Resource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

/**
 * DetectStayPointsUseCase 单元测试
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class DetectStayPointsUseCaseTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private DetectStayPointsUseCase useCase;

    @Before
    public void setUp() {
        useCase = new DetectStayPointsUseCase();
    }

    /**
     * 等待 LiveData 发出非 LOADING 状态的结果
     * BaseUseCase 在后台线程执行，通过 Handler.post 回到主线程
     * 需要等待后台线程完成 + flush 主线程消息队列
     */
    @SuppressWarnings("unchecked")
    private Resource<List<StayPoint>> awaitResult(LiveData<Resource<List<StayPoint>>> liveData) {
        // 轮询等待后台线程完成并通过 Handler 回调到主线程
        for (int i = 0; i < 50; i++) {
            ShadowLooper.idleMainLooper();
            Resource<List<StayPoint>> value = liveData.getValue();
            if (value != null && value.status != Resource.Status.LOADING) {
                return value;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            ShadowLooper.idleMainLooper();
        }
        fail("UseCase 执行超时（5秒）");
        return null;
    }

    @Test
    public void testEmptyInput() {
        List<LocationRecord> records = new ArrayList<>();

        Resource<List<StayPoint>> result = awaitResult(useCase.execute(records));

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(0, result.data.size());
    }

    @Test
    public void testNullInput() {
        Resource<List<StayPoint>> result = awaitResult(useCase.execute(null));

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(0, result.data.size());
    }

    @Test
    public void testSingleRecord() {
        List<LocationRecord> records = new ArrayList<>();
        LocationRecord record = createLocationRecord(1, 39.9042, 116.4074, System.currentTimeMillis());
        records.add(record);

        Resource<List<StayPoint>> result = awaitResult(useCase.execute(records));

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(0, result.data.size());
    }

    @Test
    public void testStayPointDetection() {
        List<LocationRecord> records = new ArrayList<>();
        long baseTime = System.currentTimeMillis();

        // 5个点在同一个区域，间隔2分钟，总停留8分钟（超过5分钟阈值）
        for (int i = 0; i < 5; i++) {
            double lat = 39.9042 + (Math.random() - 0.5) * 0.0001;
            double lng = 116.4074 + (Math.random() - 0.5) * 0.0001;
            records.add(createLocationRecord(i, lat, lng, baseTime + i * 120000));
        }

        // 5个点在另一个区域，间隔2分钟
        for (int i = 5; i < 10; i++) {
            double lat = 39.9142 + (Math.random() - 0.5) * 0.0001;
            double lng = 116.4174 + (Math.random() - 0.5) * 0.0001;
            records.add(createLocationRecord(i, lat, lng, baseTime + i * 120000));
        }

        Resource<List<StayPoint>> result = awaitResult(useCase.execute(records));

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertTrue("应该检测到至少一个停留点", result.data.size() >= 1);

        if (!result.data.isEmpty()) {
            StayPoint firstStay = result.data.get(0);
            assertTrue("停留点序号应该>=0", firstStay.getOriginalIndex() >= 0);
        }
    }

    @Test
    public void testSequentialIndexAssignment() {
        List<LocationRecord> records = new ArrayList<>();
        long baseTime = System.currentTimeMillis();

        // 3个区域，每个5个点，间隔2分钟（确保超过5分钟停留阈值）
        for (int area = 0; area < 3; area++) {
            for (int i = 0; i < 5; i++) {
                double lat = 39.9042 + area * 0.01 + (Math.random() - 0.5) * 0.0001;
                double lng = 116.4074 + area * 0.01 + (Math.random() - 0.5) * 0.0001;
                records.add(createLocationRecord(area * 5 + i, lat, lng,
                    baseTime + (area * 5 + i) * 120000));
            }
        }

        Resource<List<StayPoint>> result = awaitResult(useCase.execute(records));

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);

        for (int i = 1; i < result.data.size(); i++) {
            int prevIndex = result.data.get(i - 1).getOriginalIndex();
            int currIndex = result.data.get(i).getOriginalIndex();
            assertTrue("序号应该递增: " + prevIndex + " -> " + currIndex,
                currIndex > prevIndex);
        }
    }

    private LocationRecord createLocationRecord(int id, double lat, double lng, long timestamp) {
        return new LocationRecord(id, "TEST_DEVICE", lat, lng, timestamp);
    }
}
