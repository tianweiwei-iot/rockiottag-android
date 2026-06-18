package com.RockiotTag.tag.manager;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;
import android.widget.SeekBar;
import android.widget.TextView;

import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.util.TimeFormatter;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * 轨迹播放管理器
 * 职责：统一管理轨迹播放逻辑
 */
public class TrackPlaybackManager {
    private static final String TAG = "TrackPlaybackManager";
    
    public interface PlaybackListener {
        void onPlaybackProgress(int currentIndex, StayPoint currentPoint);
        void onPlaybackComplete();
    }
    
    private List<StayPoint> stayPoints;
    private int currentIndex = 0;
    private boolean isPlaying = false;
    private int playSpeed = 1;
    private PlaybackListener listener;
    
    private PlaybackHandler playbackHandler;
    
    private static class PlaybackHandler extends Handler {
        private final WeakReference<TrackPlaybackManager> managerRef;
        
        PlaybackHandler(TrackPlaybackManager manager) {
            managerRef = new WeakReference<>(manager);
        }
        
        @Override
        public void handleMessage(Message msg) {
            TrackPlaybackManager manager = managerRef.get();
            if (manager != null && manager.isPlaying) {
                manager.moveToNextPoint();
            }
        }
    }
    
    public TrackPlaybackManager(List<StayPoint> stayPoints, PlaybackListener listener) {
        this.stayPoints = stayPoints;
        this.listener = listener;
        this.playbackHandler = new PlaybackHandler(this);
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
        LogUtil.d(TAG, "Playback started");
    }
    
    /**
     * 暂停播放
     */
    public void pausePlayback() {
        isPlaying = false;
        if (playbackHandler != null) {
            playbackHandler.removeCallbacksAndMessages(null);
        }
        LogUtil.d(TAG, "Playback paused at index " + currentIndex);
    }
    
    /**
     * 停止播放
     */
    public void stopPlayback() {
        pausePlayback();
        currentIndex = 0;
    }
    
    /**
     * 移动到下一个点
     */
    private void moveToNextPoint() {
        if (!isPlaying || currentIndex >= stayPoints.size() - 1) {
            if (currentIndex >= stayPoints.size() - 1) {
                pausePlayback();
                if (listener != null) {
                    listener.onPlaybackComplete();
                }
            }
            return;
        }
        
        currentIndex++;
        StayPoint currentPoint = stayPoints.get(currentIndex);
        
        if (listener != null) {
            listener.onPlaybackProgress(currentIndex, currentPoint);
        }
        
        scheduleNextMove();
    }
    
    /**
     * 调度下一次移动
     */
    private void scheduleNextMove() {
        if (playbackHandler != null && isPlaying) {
            playbackHandler.sendEmptyMessageDelayed(0, 1000 / playSpeed);
        }
    }
    
    /**
     * 设置播放速度
     * @param speed 速度倍数（1, 2, 4, 8）
     */
    public void setPlaySpeed(int speed) {
        this.playSpeed = speed;
        LogUtil.d(TAG, "Play speed set to " + speed + "x");
    }
    
    /**
     * 跳转到指定位置
     * @param index 目标索引
     */
    public void seekTo(int index) {
        if (index >= 0 && index < stayPoints.size()) {
            currentIndex = index;
            StayPoint currentPoint = stayPoints.get(currentIndex);
            
            if (listener != null) {
                listener.onPlaybackProgress(currentIndex, currentPoint);
            }
            
            LogUtil.d(TAG, "Seeked to index " + index);
        }
    }
    
    /**
     * 获取当前播放索引
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
     * 释放资源
     */
    public void release() {
        stopPlayback();
        playbackHandler = null;
        listener = null;
    }
}
