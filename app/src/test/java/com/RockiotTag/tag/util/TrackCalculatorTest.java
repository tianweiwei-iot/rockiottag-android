package com.RockiotTag.tag.util;

import static org.junit.Assert.*;

import com.RockiotTag.tag.model.LocationData;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TrackCalculator 单元测试
 * 测试轨迹距离计算和统计逻辑
 */
public class TrackCalculatorTest {

    @Test
    public void testNullInput() {
        assertEquals(0.0, TrackCalculator.calculateTotalDistance(null), 0.001);
    }

    @Test
    public void testEmptyList() {
        List<LocationData> records = new ArrayList<>();
        assertEquals(0.0, TrackCalculator.calculateTotalDistance(records), 0.001);
    }

    @Test
    public void testSinglePoint() {
        List<LocationData> records = Arrays.asList(
            new LocationData("device1", 39.9042, 116.4074, 1000L)
        );
        assertEquals(0.0, TrackCalculator.calculateTotalDistance(records), 0.001);
    }

    @Test
    public void testTwoPointsDistance() {
        // 北京天安门 -> 北京故宫（约 0.8km），61秒间隔避免触发异常跳变过滤
        List<LocationData> records = Arrays.asList(
            new LocationData("device1", 39.9042, 116.4074, 1000L),
            new LocationData("device1", 39.9163, 116.3971, 62000L)
        );
        double distance = TrackCalculator.calculateTotalDistance(records);
        assertTrue("Distance should be > 0.5km, got: " + distance, distance > 0.5);
        assertTrue("Distance should be < 2km, got: " + distance, distance < 2.0);
    }

    @Test
    public void testMultiplePointsDistance() {
        // 三个点形成路径，每个间隔61秒
        List<LocationData> records = Arrays.asList(
            new LocationData("device1", 39.9042, 116.4074, 1000L),
            new LocationData("device1", 39.9163, 116.3971, 62000L),
            new LocationData("device1", 39.9080, 116.3900, 123000L)
        );
        double distance = TrackCalculator.calculateTotalDistance(records);
        assertTrue("Distance should be > 1km, got: " + distance, distance > 1.0);
    }

    @Test
    public void testAnomalousJumpFiltered() {
        // 正常点 + 异常跳变（500米在1秒内，速度超限）
        List<LocationData> records = Arrays.asList(
            new LocationData("device1", 39.9042, 116.4074, 1000L),
            new LocationData("device1", 39.9042, 116.4074, 2000L),  // 正常
            new LocationData("device1", 39.9089, 116.4140, 2500L)  // 500m in 500ms = 异常跳变
        );
        TrackCalculator.TrackStatistics stats =
            TrackCalculator.calculateTrackStatistics(records);
        assertTrue("Should have filtered jumps", stats.filteredJumps > 0);
    }

    @Test
    public void testInvalidCoordinatesSkipped() {
        // 4个点，第3个无效，有效段仍应被计算
        List<LocationData> records = Arrays.asList(
            new LocationData("device1", 39.9042, 116.4074, 1000L),
            new LocationData("device1", 39.9163, 116.3971, 62000L),  // 有效段 1→2
            new LocationData("device1", 0, 0, 123000L),              // 无效
            new LocationData("device1", 39.9080, 116.3900, 184000L)  // 有效段 3→4（3被跳过）
        );
        TrackCalculator.TrackStatistics stats =
            TrackCalculator.calculateTrackStatistics(records);
        assertTrue("Should have at least 1 valid segment, got: " + stats.validSegments,
            stats.validSegments >= 1);
    }

    @Test
    public void testStatisticsValues() {
        List<LocationData> records = Arrays.asList(
            new LocationData("device1", 39.9042, 116.4074, 1000L),
            new LocationData("device1", 39.9163, 116.3971, 61000L)  // 60秒后
        );
        TrackCalculator.TrackStatistics stats =
            TrackCalculator.calculateTrackStatistics(records);
        assertTrue("Distance > 0", stats.totalDistanceKm > 0);
        assertEquals("Valid segments", 1, stats.validSegments);
        assertEquals("Filtered jumps", 0, stats.filteredJumps);
    }
}
