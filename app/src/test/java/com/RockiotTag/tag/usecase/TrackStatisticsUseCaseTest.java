package com.RockiotTag.tag.usecase;

import static org.junit.Assert.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.model.Resource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * TrackStatisticsUseCase 单元测试
 */
public class TrackStatisticsUseCaseTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private TrackStatisticsUseCase useCase;

    @Before
    public void setUp() {
        useCase = new TrackStatisticsUseCase();
    }

    @Test
    public void testEmptyInput() {
        List<LocationRecord> records = new ArrayList<>();
        
        Resource<TrackStatisticsUseCase.Result> result = useCase.execute(records).getValue();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(0, result.data.totalDistance, 0.001);
        assertEquals(0, result.data.duration);
        assertEquals(0, result.data.averageSpeed, 0.001);
        assertEquals(0, result.data.pointCount);
    }

    @Test
    public void testNullInput() {
        Resource<TrackStatisticsUseCase.Result> result = useCase.execute(null).getValue();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(0, result.data.totalDistance, 0.001);
        assertEquals(0, result.data.pointCount);
    }

    @Test
    public void testSingleRecord() {
        List<LocationRecord> records = new ArrayList<>();
        LocationRecord record = createLocationRecord(1, 39.9042, 116.4074, System.currentTimeMillis());
        records.add(record);
        
        Resource<TrackStatisticsUseCase.Result> result = useCase.execute(records).getValue();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(0, result.data.totalDistance, 0.001);
        assertEquals(1, result.data.pointCount);
    }

    @Test
    public void testTwoPointsDistance() {
        List<LocationRecord> records = new ArrayList<>();
        long baseTime = System.currentTimeMillis();
        
        records.add(createLocationRecord(1, 39.9042, 116.4074, baseTime));
        records.add(createLocationRecord(2, 39.9163, 116.3972, baseTime + 600000));
        
        Resource<TrackStatisticsUseCase.Result> result = useCase.execute(records).getValue();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertTrue("距离应该大于0", result.data.totalDistance > 0);
        assertEquals(2, result.data.pointCount);
        assertTrue("持续时间应该为10分钟", result.data.duration >= 600000);
    }

    @Test
    public void testMultiplePointsStatistics() {
        List<LocationRecord> records = new ArrayList<>();
        long baseTime = System.currentTimeMillis();
        
        double[] lats = {39.9042, 39.9080, 39.9120, 39.9160, 39.9200};
        double[] lngs = {116.4074, 116.4100, 116.4130, 116.4160, 116.4190};
        
        for (int i = 0; i < 5; i++) {
            records.add(createLocationRecord(i, lats[i], lngs[i], baseTime + i * 300000));
        }
        
        Resource<TrackStatisticsUseCase.Result> result = useCase.execute(records).getValue();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertTrue("总距离应该大于0", result.data.totalDistance > 0);
        assertEquals(5, result.data.pointCount);
        assertTrue("持续时间应该为20分钟", result.data.duration >= 1200000);
        assertTrue("平均速度应该大于0", result.data.averageSpeed > 0);
    }

    @Test
    public void testAverageSpeedCalculation() {
        List<LocationRecord> records = new ArrayList<>();
        long baseTime = System.currentTimeMillis();
        
        records.add(createLocationRecord(1, 39.9042, 116.4074, baseTime));
        records.add(createLocationRecord(2, 40.0042, 116.5074, baseTime + 3600000));
        
        Resource<TrackStatisticsUseCase.Result> result = useCase.execute(records).getValue();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        
        double avgSpeedKmh = result.data.averageSpeed * 3.6;
        assertTrue("平均速度应该合理: " + avgSpeedKmh + " km/h", 
            avgSpeedKmh >= 0 && avgSpeedKmh <= 200);
    }

    private LocationRecord createLocationRecord(int id, double lat, double lng, long timestamp) {
        return new LocationRecord(id, "TEST_DEVICE", lat, lng, timestamp);
    }
}
