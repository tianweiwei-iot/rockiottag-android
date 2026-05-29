package com.RockiotTag.tag.usecase;

import static org.junit.Assert.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.RockiotTag.tag.LocationRecord;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.model.Resource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * DetectStayPointsUseCase 单元测试
 */
public class DetectStayPointsUseCaseTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private DetectStayPointsUseCase useCase;

    @Before
    public void setUp() {
        useCase = new DetectStayPointsUseCase();
    }

    @Test
    public void testEmptyInput() {
        List<LocationRecord> records = new ArrayList<>();
        
        Resource<List<StayPoint>> result = useCase.execute(records).getValue();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(0, result.data.size());
    }

    @Test
    public void testNullInput() {
        Resource<List<StayPoint>> result = useCase.execute(null).getValue();
        
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
        
        Resource<List<StayPoint>> result = useCase.execute(records).getValue();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(0, result.data.size());
    }

    @Test
    public void testStayPointDetection() {
        List<LocationRecord> records = new ArrayList<>();
        long baseTime = System.currentTimeMillis();
        
        for (int i = 0; i < 5; i++) {
            double lat = 39.9042 + (Math.random() - 0.5) * 0.0001;
            double lng = 116.4074 + (Math.random() - 0.5) * 0.0001;
            records.add(createLocationRecord(i, lat, lng, baseTime + i * 60000));
        }
        
        for (int i = 5; i < 10; i++) {
            double lat = 39.9142 + (Math.random() - 0.5) * 0.0001;
            double lng = 116.4174 + (Math.random() - 0.5) * 0.0001;
            records.add(createLocationRecord(i, lat, lng, baseTime + i * 60000));
        }
        
        Resource<List<StayPoint>> result = useCase.execute(records).getValue();
        
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
        
        for (int area = 0; area < 3; area++) {
            for (int i = 0; i < 5; i++) {
                double lat = 39.9042 + area * 0.01 + (Math.random() - 0.5) * 0.0001;
                double lng = 116.4074 + area * 0.01 + (Math.random() - 0.5) * 0.0001;
                records.add(createLocationRecord(area * 5 + i, lat, lng, 
                    baseTime + (area * 5 + i) * 60000));
            }
        }
        
        Resource<List<StayPoint>> result = useCase.execute(records).getValue();
        
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
