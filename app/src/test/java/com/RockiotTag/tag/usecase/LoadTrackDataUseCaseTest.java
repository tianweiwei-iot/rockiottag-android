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
 * LoadTrackDataUseCase 单元测试
 */
public class LoadTrackDataUseCaseTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private LoadTrackDataUseCase useCase;
    private TestLocationRepository testRepository;

    private static class TestLocationRepository extends com.RockiotTag.tag.repository.LocationRepository {
        private List<LocationRecord> mockData = new ArrayList<>();

        public TestLocationRepository() {
            super(null);
        }

        public void setMockData(List<LocationRecord> data) {
            this.mockData = data;
        }

        @Override
        public List<LocationRecord> getLocationsByDeviceAndTimeRange(String deviceNum, long startTime, long endTime) {
            List<LocationRecord> result = new ArrayList<>();
            for (LocationRecord record : mockData) {
                if (record.getTimestamp() >= startTime && record.getTimestamp() <= endTime) {
                    result.add(record);
                }
            }
            return result;
        }
    }

    @Before
    public void setUp() {
        testRepository = new TestLocationRepository();
        useCase = new LoadTrackDataUseCase(testRepository);
    }

    @Test
    public void testEmptyDeviceNum() {
        LoadTrackDataUseCase.Params params = new LoadTrackDataUseCase.Params(
            "", System.currentTimeMillis() - 86400000, System.currentTimeMillis()
        );
        
        Resource<List<LocationRecord>> result = useCase.execute(params).getValue();
        
        assertNotNull(result);
        assertTrue(result.isError());
    }

    @Test
    public void testInvalidTimeRange() {
        LoadTrackDataUseCase.Params params = new LoadTrackDataUseCase.Params(
            "TEST001", System.currentTimeMillis(), System.currentTimeMillis() - 86400000
        );
        
        Resource<List<LocationRecord>> result = useCase.execute(params).getValue();
        
        assertNotNull(result);
        assertTrue(result.isError());
    }

    @Test
    public void testValidDataLoading() {
        List<LocationRecord> mockData = new ArrayList<>();
        long baseTime = System.currentTimeMillis() - 86400000;
        
        for (int i = 0; i < 10; i++) {
            LocationRecord record = new LocationRecord(i, "TEST001", 
                39.9042 + i * 0.001, 116.4074 + i * 0.001, baseTime + i * 3600000);
            mockData.add(record);
        }
        
        testRepository.setMockData(mockData);
        
        LoadTrackDataUseCase.Params params = new LoadTrackDataUseCase.Params(
            "TEST001", baseTime, baseTime + 86400000
        );
        
        Resource<List<LocationRecord>> result = useCase.execute(params).getValue();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(10, result.data.size());
    }

    @Test
    public void testTimeRangeFiltering() {
        List<LocationRecord> mockData = new ArrayList<>();
        long baseTime = System.currentTimeMillis() - 86400000;
        
        for (int i = 0; i < 24; i++) {
            LocationRecord record = new LocationRecord(i, "TEST001", 
                39.9042, 116.4074, baseTime + i * 3600000);
            mockData.add(record);
        }
        
        testRepository.setMockData(mockData);
        
        long queryStart = baseTime + 6 * 3600000;
        long queryEnd = baseTime + 18 * 3600000;
        
        LoadTrackDataUseCase.Params params = new LoadTrackDataUseCase.Params(
            "TEST001", queryStart, queryEnd
        );
        
        Resource<List<LocationRecord>> result = useCase.execute(params).getValue();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(12, result.data.size());
    }

    @Test
    public void testNoMatchingData() {
        List<LocationRecord> mockData = new ArrayList<>();
        long baseTime = System.currentTimeMillis() - 86400000;
        
        LocationRecord record = new LocationRecord(1, "TEST001", 
            39.9042, 116.4074, baseTime);
        mockData.add(record);
        
        testRepository.setMockData(mockData);
        
        long queryStart = System.currentTimeMillis();
        long queryEnd = System.currentTimeMillis() + 86400000;
        
        LoadTrackDataUseCase.Params params = new LoadTrackDataUseCase.Params(
            "TEST001", queryStart, queryEnd
        );
        
        Resource<List<LocationRecord>> result = useCase.execute(params).getValue();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(0, result.data.size());
    }
}
