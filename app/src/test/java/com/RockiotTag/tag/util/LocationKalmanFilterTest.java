package com.RockiotTag.tag.util;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * LocationKalmanFilter 单元测试
 * 测试双维度卡尔曼滤波器的精度感知滤波逻辑
 */
public class LocationKalmanFilterTest {

    private LocationKalmanFilter filter;

    @Before
    public void setUp() {
        filter = new LocationKalmanFilter();
    }

    @Test
    public void testFirstObservationReturnsInput() {
        double[] result = filter.filter(39.9042, 116.4074, 50f);
        assertEquals(39.9042, result[0], 0.0001);
        assertEquals(116.4074, result[1], 0.0001);
    }

    @Test
    public void testSecondObservationConverges() {
        // 第一次观测
        filter.filter(39.9042, 116.4074, 50f);
        // 第二次观测（稍有偏移）
        double[] result = filter.filter(39.9043, 116.4075, 50f);
        // 滤波结果应在两个观测值之间
        assertTrue("Latitude should be between observations",
            result[0] > 39.9042 && result[0] < 39.9043);
        assertTrue("Longitude should be between observations",
            result[1] > 116.4074 && result[1] < 116.4075);
    }

    @Test
    public void testMultipleObservationsConvergeToTrueValue() {
        // 模拟围绕真实位置 (39.9042, 116.4074) 的多次漂移观测
        double trueLat = 39.9042;
        double trueLng = 116.4074;
        double[][] observations = {
            {trueLat + 0.0001, trueLng + 0.0001},
            {trueLat - 0.0001, trueLng + 0.0002},
            {trueLat + 0.0002, trueLng - 0.0001},
            {trueLat - 0.0002, trueLng - 0.0002},
            {trueLat + 0.0001, trueLng - 0.0001},
            {trueLat - 0.0001, trueLng + 0.0001},
            {trueLat + 0.0003, trueLng + 0.0002},
            {trueLat - 0.0002, trueLng - 0.0001},
            {trueLat + 0.0001, trueLng + 0.0001},
            {trueLat - 0.0001, trueLng - 0.0002},
        };

        double[] result = null;
        for (double[] obs : observations) {
            result = filter.filter(obs[0], obs[1], 50f);
        }

        // 经过多次滤波，结果应接近真实值
        assertNotNull(result);
        assertTrue("Filtered latitude should converge near true value, got: " + result[0],
            Math.abs(result[0] - trueLat) < 0.0005);
        assertTrue("Filtered longitude should converge near true value, got: " + result[1],
            Math.abs(result[1] - trueLng) < 0.0005);
    }

    @Test
    public void testHighAccuracyWeightsMore() {
        // 高精度（小 accuracy）应该让滤波结果更接近观测值
        filter.filter(39.9042, 116.4074, 50f);

        // 低精度观测：结果偏离观测值较多
        double[] lowAccuracyResult = filter.filter(39.9050, 116.4080, 200f);

        // 重置后用高精度观测同样的偏移
        filter.reset();
        filter.filter(39.9042, 116.4074, 50f);
        double[] highAccuracyResult = filter.filter(39.9050, 116.4080, 5f);

        // 高精度时滤波结果应更接近观测值
        double lowAccDelta = Math.abs(lowAccuracyResult[0] - 39.9050);
        double highAccDelta = Math.abs(highAccuracyResult[0] - 39.9050);
        assertTrue("High accuracy should weight observation more (lat delta: high=" +
            highAccDelta + " vs low=" + lowAccDelta + ")",
            highAccDelta < lowAccDelta);
    }

    @Test
    public void testResetClearsState() {
        // 第一次使用
        filter.filter(39.9042, 116.4074, 50f);
        filter.reset();

        // 重置后第一次观测应直接返回输入值
        double[] result = filter.filter(40.0000, 117.0000, 50f);
        assertEquals(40.0000, result[0], 0.0001);
        assertEquals(117.0000, result[1], 0.0001);
    }

    @Test
    public void testSetProcessNoiseValid() {
        filter.setProcessNoise(0.1);
        // 不抛异常即通过，内部值已更新
        filter.filter(39.9042, 116.4074, 50f);
    }

    @Test
    public void testSetProcessNoiseInvalid() {
        // 无效值不应改变当前设置
        filter.setProcessNoise(0.0);  // 太小
        filter.setProcessNoise(2.0);  // 太大
        filter.setProcessNoise(-1.0); // 负数
        // 不抛异常即通过
        filter.filter(39.9042, 116.4074, 50f);
    }

    @Test
    public void testFilterReturnsTwoElementArray() {
        double[] result = filter.filter(39.9042, 116.4074, 50f);
        assertNotNull(result);
        assertEquals(2, result.length);
    }

    @Test
    public void testSuccessiveFiltersProduceStableResults() {
        // 连续滤波不应产生发散
        double[] prev = filter.filter(39.9042, 116.4074, 50f);
        for (int i = 0; i < 100; i++) {
            double[] curr = filter.filter(39.9042, 116.4074, 50f);
            // 每次滤波结果变化应很小（收敛后稳定）
            double delta = Math.abs(curr[0] - prev[0]);
            assertTrue("Filter should be stable, delta: " + delta, delta < 0.01);
            prev = curr;
        }
    }
}
