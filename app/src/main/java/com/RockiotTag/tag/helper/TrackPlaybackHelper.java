package com.RockiotTag.tag.helper;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.util.Log;
import android.view.animation.LinearInterpolator;
import android.widget.SeekBar;
import android.widget.TextView;

import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.util.TimeFormatter;

import java.util.List;

/**
 * 轨迹播放控制助手
 * 职责：封装播放逻辑和动画控制
 */
public class TrackPlaybackHelper {
    private static final String TAG = "TrackPlaybackHelper";
    
    public interface PlaybackCallback {
        void onPlaybackProgress(int currentIndex, StayPoint currentPoint);
        void onPlaybackComplete();
    }
    
    private List<StayPoint> stayPoints;
    private int currentIndex = 0;
    private boolean isPlaying = false;
    private int playSpeed = 1;
    private PlaybackCallback callback;
    private Handler playHandler;
    private ValueAnimator moveAnimator;
    
    public TrackPlaybackHelper(List<StayPoint> stayPoints, PlaybackCallback callback) {
        this.stayPoints = stayPoints;
        this.callback = callback;
        this.playHandler = new Handler();
    }
    
    /**
     * 开始播放
     */
    public void startPlayback() {
        if (stayPoints == null || stayPoints.isEmpty()) {
            return;
        }
        
        isPlaying = true;
        if (currentIndex >= stayPoints.size() - 1) {
            currentIndex = 0;
        }
        
        scheduleNextMove();
        Log.d(TAG, "Playback started");
    }
    
    /**
     * 暂停播放
     */
    public void pausePlayback() {
        isPlaying = false;
        if (playHandler != null) {
            playHandler.removeCallbacksAndMessages(null);
        }
        if (moveAnimator != null) {
            moveAnimator.cancel();
        }
        Log.d(TAG, "Playback paused at index " + currentIndex);
    }
    
    /**
     * 停止播放
     */
    public void stopPlayback() {
        pausePlayback();
        currentIndex = 0;
    }
    
    /**
     * 跳转到指定位置
     */
    public void seekTo(int index) {
        if (index >= 0 && index < stayPoints.size()) {
            currentIndex = index;
            StayPoint currentPoint = stayPoints.get(currentIndex);
            
            if (callback != null) {
                callback.onPlaybackProgress(currentIndex, currentPoint);
            }
            
            Log.d(TAG, "Seeked to index " + index);
        }
    }
    
    /**
     * 移动到下一个点
     */
    public void moveToNextPoint() {
        if (!isPlaying || currentIndex >= stayPoints.size() - 1) {
            if (currentIndex >= stayPoints.size() - 1) {
                pausePlayback();
                if (callback != null) {
                    callback.onPlaybackComplete();
                }
            }
            return;
        }
        
        currentIndex++;
        StayPoint currentPoint = stayPoints.get(currentIndex);
        
        if (callback != null) {
            callback.onPlaybackProgress(currentIndex, currentPoint);
        }
        
        scheduleNextMove();
    }
    
    /**
     * 调度下一次移动
     */
    private void scheduleNextMove() {
        if (playHandler != null && isPlaying) {
            playHandler.postDelayed(this::moveToNextPoint, 1000 / playSpeed);
        }
    }
    
    /**
     * 设置播放速度
     */
    public void setPlaySpeed(int speed) {
        this.playSpeed = speed;
        Log.d(TAG, "Play speed set to " + speed + "x");
    }
    
    /**
     * 获取当前索引
     */
    public int getCurrentIndex() {
        return currentIndex;
    }
    
    /**
     * 是否正在播放
     */
    public boolean isPlaying() {
        return isPlaying;
    }
    
    /**
     * 获取总点数
     */
    public int getTotalCount() {
        return stayPoints != null ? stayPoints.size() : 0;
    }
    
    /**
     * 获取播放速度
     */
    public int getPlaySpeed() {
        return playSpeed;
    }
    
    /**
     * 是否可以移动到下一个点
     */
    public boolean canMoveToNext() {
        return currentIndex < stayPoints.size() - 1;
    }
    
    /**
     * 获取当前停留点
     */
    public StayPoint getCurrentStayPoint() {
        if (currentIndex >= 0 && currentIndex < stayPoints.size()) {
            return stayPoints.get(currentIndex);
        }
        return null;
    }
    
    /**
     * 获取下一个停留点
     */
    public StayPoint getNextStayPoint() {
        if (currentIndex + 1 < stayPoints.size()) {
            return stayPoints.get(currentIndex + 1);
        }
        return null;
    }
    
    /**
     * 计算动画时长
     */
    public long calculateAnimationDuration() {
        return 1000 / playSpeed;
    }
    
    /**
     * 获取当前播放时间字符串
     */
    public String getCurrentPlayTimeString() {
        StayPoint point = getCurrentStayPoint();
        if (point != null) {
            String timeStr = TimeFormatter.formatFullTime(point.getArriveTime());
            return timeStr.substring(timeStr.indexOf(" ") + 1);
        }
        return "--:--:--";
    }
    
    /**
     * 获取当前完整时间字符串
     */
    public String getCurrentPlayFullTimeString() {
        StayPoint point = getCurrentStayPoint();
        if (point != null) {
            return TimeFormatter.formatFullTime(point.getArriveTime());
        }
        return "yyyy-MM-dd HH:mm:ss";
    }
    
    /**
     * 是否应该更新地址（每10个点更新一次）
     */
    public boolean shouldUpdateAddress() {
        return currentIndex % 10 == 0;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stopPlayback();
        playHandler = null;
        callback = null;
        stayPoints = null;
    }
}
