package com.RockiotTag.tag.util;

import static org.junit.Assert.*;

import com.RockiotTag.tag.LocationRecord;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * BLETagFilter 单元测试
 * 测试 BLE Tag 专用过滤器的异常跳变检测和静止漂移聚合
 */
public class BLETagFilterTest {

    // ===== isAnomalous 测试 =====

    @Test
    public void testNormalMovementNotAnomalous() {
        // 50米移动，10秒，速度 5m/s (18km/h) — 正常
        assertFalse(BLETagFilter.isAnomalous(50, 10000));
    }

    @Test
    public void testExcessiveSpeedAnomalous() {
        // 1000米移动，1秒，速度 1000m/s (3600km/h) — 异常
        assertTrue(BLETagFilter.isAnomalous(1000, 1000));
    }

    @Test
    public void testZeroTimeDiffAnomalous() {
        // 时间差为0 — 异常（时间倒流）
        assertTrue(BLETagFilter.isAnomalous(10, 0));
    }

    @Test
    public void testNegativeTimeDiffAnomalous() {
        // 时间差为负 — 异常
        assertTrue(BLETagFilter.isAnomalous(10, -100));
    }

    @Test
    public void testLargeDistanceReasonableSpeedNotAnomalous() {
        // 400米移动，20秒，速度 20m/s (72km/h) — 距离大但速度合理
        assertFalse(BLETagFilter.isAnomalous(400, 20000));
    }

    @Test
    public void testSmallDistanceNotAnomalous() {
        // 10米漂移，5秒 — 正常漂移
        assertFalse(BLETagFilter.isAnomalous(10, 5000));
    }

    @Test
    public void testAccuracyAwareAnomalous() {
        // 精度 10m + 10m = 20m，3倍 = 60m
        // 距离 200m > 60m，但速度 200/1 = 200m/s > 33.33m/s → 异常
        assertTrue(BLETagFilter.isAnomalous(200, 1000, 10f, 10f));
    }

    @Test
    public void testAccuracyAwareNotAnomalous() {
        // 精度 10m + 10m = 20m，3倍 = 60m
        // 距离 50m < 60m → 正常
        assertFalse(BLETagFilter.isAnomalous(50, 5000, 10f, 10f));
    }

    // ===== filterStationaryDrift 测试 =====

    @Test
    public void testFilterNullReturnsEmpty() {
        List<LocationRecord> result = BLETagFilter.filterStationaryDrift(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFilterSingleRecord() {
        List<LocationRecord> records = Arrays.asList(
            new LocationRecord("dev1", 39.9042, 116.4074, 1000L)
        );
        List<LocationRecord> result = BLETagFilter.filterStationaryDrift(records);
        assertEquals(1, result.size());
    }

    @Test
    public void testStationaryPointsAggregated() {
        // 3个点在 100米范围内（静止漂移），应聚合为1个点
        List<LocationRecord> records = Arrays.asList(
            new LocationRecord("dev1", 39.9042, 116.4074, 1000L),
            new LocationRecord("dev1", 39.9043, 116.4075, 2000L),  // ~11m
            new LocationRecord("dev1", 39.9041, 116.4073, 3000L)   // ~11m
        );
        List<LocationRecord> result = BLETagFilter.filterStationaryDrift(records);
        assertEquals("Stationary points should be aggregated to 1", 1, result.size());
    }

    @Test
    public void testMovingPointsNotAggregated() {
        // 2个点相距很远（>100m），不应聚合
        List<LocationRecord> records = Arrays.asList(
            new LocationRecord("dev1", 39.9042, 116.4074, 1000L),
            new LocationRecord("dev1", 39.9200, 116.4300, 2000L)  // > 1km away
        );
        List<LocationRecord> result = BLETagFilter.filterStationaryDrift(records);
        assertEquals("Moving points should not be aggregated", 2, result.size());
    }

    @Test
    public void testMixedMovingAndStationary() {
        // 3个静止点 + 1个远距离移动点
        List<LocationRecord> records = Arrays.asList(
            new LocationRecord("dev1", 39.9042, 116.4074, 1000L),
            new LocationRecord("dev1", 39.9043, 116.4075, 2000L),  // ~11m 静止
            new LocationRecord("dev1", 39.9041, 116.4073, 3000L),   // ~11m 静止
            new LocationRecord("dev1", 39.9300, 116.4500, 4000L)    // 远距离移动
        );
        List<LocationRecord> result = BLETagFilter.filterStationaryDrift(records);
        assertEquals("Should have 2 points (1 aggregated + 1 moving)", 2, result.size());
    }

    // ===== 阈值 getter 测试 =====

    @Test
    public void testGetMovingDistanceThreshold() {
        assertEquals(50.0, BLETagFilter.getMovingDistanceThreshold(), 0.01);
    }

    @Test
    public void testGetMovingTimeThreshold() {
        assertEquals(60000L, BLETagFilter.getMovingTimeThreshold());
    }

    @Test
    public void testGetStationaryDistanceThreshold() {
        assertEquals(100.0, BLETagFilter.getStationaryDistanceThreshold(), 0.01);
    }

    @Test
    public void testGetStationaryTimeThreshold() {
        assertEquals(30 * 60 * 1000L, BLETagFilter.getStationaryTimeThreshold());
    }
}
