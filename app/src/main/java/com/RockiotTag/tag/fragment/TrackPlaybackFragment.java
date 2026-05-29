package com.RockiotTag.tag.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.manager.TrackPlaybackManager;
import com.RockiotTag.tag.util.TimeFormatter;
import com.RockiotTag.tag.viewmodel.TrackViewModel;

import java.util.List;

/**
 * 轨迹播放控制 Fragment
 * 职责：管理轨迹播放控制和UI更新
 */
public class TrackPlaybackFragment extends Fragment {
    private static final String TAG = "TrackPlaybackFragment";
    
    // UI 组件
    private ImageButton playPauseButton;
    private SeekBar playbackSeekbar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView trackPointTime;
    private TextView trackPointAddress;
    private TextView totalDistanceText;
    
    // 管理器
    private TrackViewModel viewModel;
    private TrackPlaybackManager playbackManager;
    
    public interface OnPlaybackControlListener {
        void onPlayStateChanged(boolean isPlaying);
        void onSeekTo(int index);
        void onSpeedChanged(int speed);
    }
    
    private OnPlaybackControlListener listener;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_track_playback, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 初始化 ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(TrackViewModel.class);
        
        // 初始化 UI
        initViews(view);
        
        // 观察数据变化
        observeData();
    }
    
    /**
     * 初始化视图
     */
    private void initViews(View view) {
        playPauseButton = view.findViewById(R.id.play_pause_button);
        playbackSeekbar = view.findViewById(R.id.playback_seekbar);
        currentTimeText = view.findViewById(R.id.current_time_text);
        totalTimeText = view.findViewById(R.id.total_time_text);
        trackPointTime = view.findViewById(R.id.track_point_time);
        trackPointAddress = view.findViewById(R.id.track_point_address);
        totalDistanceText = view.findViewById(R.id.total_distance_text);
        
        // 设置点击监听器
        playPauseButton.setOnClickListener(v -> togglePlayback());
        
        playbackSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && playbackManager != null) {
                    playbackManager.seekTo(progress);
                    if (listener != null) {
                        listener.onSeekTo(progress);
                    }
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
    
    /**
     * 观察数据变化
     */
    private void observeData() {
        viewModel.getStayPoints().observe(getViewLifecycleOwner(), stayPoints -> {
            if (stayPoints != null && !stayPoints.isEmpty()) {
                setupPlayback(stayPoints);
            }
        });
    }
    
    /**
     * 设置播放控制器
     */
    private void setupPlayback(List<StayPoint> stayPoints) {
        if (playbackManager != null) {
            playbackManager.release();
        }
        
        playbackManager = new TrackPlaybackManager(stayPoints, 
            new TrackPlaybackManager.PlaybackListener() {
                @Override
                public void onPlaybackProgress(int currentIndex, StayPoint currentPoint) {
                    updatePlaybackUI(currentIndex, currentPoint);
                }
                
                @Override
                public void onPlaybackComplete() {
                    updatePlayButtonState(false);
                }
            });
        
        // 更新总时间显示
        if (!stayPoints.isEmpty()) {
            StayPoint lastPoint = stayPoints.get(stayPoints.size() - 1);
            totalTimeText.setText(TimeFormatter.formatTimeHM(lastPoint.getArriveTime()));
            playbackSeekbar.setMax(stayPoints.size() - 1);
        }
    }
    
    /**
     * 更新播放 UI
     */
    private void updatePlaybackUI(int currentIndex, StayPoint currentPoint) {
        if (currentIndex >= 0 && currentIndex < playbackManager.getTotalCount()) {
            playbackSeekbar.setProgress(currentIndex);
            
            String timeStr = TimeFormatter.formatFullTime(currentPoint.getArriveTime());
            currentTimeText.setText(timeStr.substring(timeStr.indexOf(" ") + 1));
            trackPointTime.setText(timeStr);
            
            updatePlayButtonState(playbackManager.isPlaying());
        }
    }
    
    /**
     * 切换播放状态
     */
    private void togglePlayback() {
        if (playbackManager == null) {
            return;
        }
        
        if (playbackManager.isPlaying()) {
            playbackManager.pausePlayback();
            updatePlayButtonState(false);
        } else {
            playbackManager.startPlayback();
            updatePlayButtonState(true);
        }
        
        if (listener != null) {
            listener.onPlayStateChanged(playbackManager.isPlaying());
        }
    }
    
    /**
     * 更新播放按钮状态
     */
    private void updatePlayButtonState(boolean isPlaying) {
        if (isPlaying) {
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
        }
    }
    
    /**
     * 设置交互监听器
     */
    public void setOnPlaybackControlListener(OnPlaybackControlListener listener) {
        this.listener = listener;
    }
    
    /**
     * 获取播放管理器
     */
    public TrackPlaybackManager getPlaybackManager() {
        return playbackManager;
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (playbackManager != null) {
            playbackManager.release();
            playbackManager = null;
        }
    }
}
