package com.RockiotTag.tag.helper;

import android.animation.ValueAnimator;

import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.model.LocationData;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.StayPoint;

import java.util.List;

/**
 * TrackActivity 地图轨迹元素清理逻辑。
 */
public class TrackMapCleanupHelper {

    private static final String TAG = "TrackMapCleanupHelper";

    public interface Host {
        IMapAdapter getMapAdapter();
        List<Object> getPositionMarkers();
        List<Object> getArrowMarkers();
        Object getTrackPolyline();
        void setTrackPolyline(Object polyline);
        Object getPlayedPolyline();
        void setPlayedPolyline(Object polyline);
        Object getPlayMarker();
        void setPlayMarker(Object marker);
        List<double[]> getPlayedPoints();
        double[] getCurrentPlayPosition();
        void setCurrentPlayPosition(double[] position);
        ValueAnimator getMoveAnimator();
        void setMoveAnimator(ValueAnimator animator);
        List<LocationData> getAllLocationRecords();
        List<StayPoint> getStayPoints();
    }

    /** 只清除 UI，保留数据用于重新渲染 */
    public static void clearTrackUI(Host host) {
        IMapAdapter mapAdapter = host.getMapAdapter();
        if (mapAdapter == null) return;

        for (Object marker : host.getPositionMarkers()) {
            mapAdapter.removeObject(marker);
        }
        host.getPositionMarkers().clear();

        for (Object marker : host.getArrowMarkers()) {
            mapAdapter.removeObject(marker);
        }
        host.getArrowMarkers().clear();

        Object trackPolyline = host.getTrackPolyline();
        if (trackPolyline != null) {
            mapAdapter.removeObject(trackPolyline);
            host.setTrackPolyline(null);
        }

        Object playedPolyline = host.getPlayedPolyline();
        if (playedPolyline != null) {
            mapAdapter.removeObject(playedPolyline);
            host.setPlayedPolyline(null);
        }

        Object playMarker = host.getPlayMarker();
        if (playMarker != null) {
            mapAdapter.removeObject(playMarker);
            host.setPlayMarker(null);
        }

        host.getPlayedPoints().clear();
        host.setCurrentPlayPosition(null);

        ValueAnimator animator = host.getMoveAnimator();
        if (animator != null && animator.isRunning()) {
            animator.cancel();
            host.setMoveAnimator(null);
        }

        LogUtil.d(TAG, "Track UI cleared, data preserved");
    }

    /** 清除 UI 并释放轨迹数据 */
    public static void clearTrack(Host host) {
        clearTrackUI(host);
        host.getAllLocationRecords().clear();
        host.getStayPoints().clear();
        LogUtil.d(TAG, "Track cleared, memory released");
    }
}
