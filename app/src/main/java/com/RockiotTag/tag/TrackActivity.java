package com.RockiotTag.tag;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.GeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class TrackActivity extends AppCompatActivity implements GeocodeSearch.OnGeocodeSearchListener, AMap.OnMarkerClickListener {

    private static final String TAG = "TrackActivity";
    private MapView mapView;
    private AMap aMap;
    private DatabaseHelper databaseHelper;
    private GeocodeSearch geocodeSearch;
    private Button dateBtn;
    private Button startTimeBtn;
    private Button endTimeBtn;
    private Button resetTimeBtn;
    private Calendar selectedDate;
    private Calendar startDate;
    private Calendar endDate;
    private Device selectedDevice;
    private List<Marker> positionMarkers = new ArrayList<>();
    private List<Marker> arrowMarkers = new ArrayList<>();
    private Polyline trackPolyline;
    
    private TextView trackPointTime;
    private TextView trackPointAddress;
    private TextView totalDistanceText;
    
    private ImageButton playBtn;
    private SeekBar playbackSeekbar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView speedBtn;
    
    private List<LocationRecord> allLocationRecords = new ArrayList<>();
    private List<StayPoint> stayPoints = new ArrayList<>();
    private static final double STAY_DISTANCE_THRESHOLD = 30.0; // 30 米，平衡 GPS 精度和停留点识别
    private static final double MAX_SPEED_KMH = 200.0;
    private boolean isPlaying = false;
    private int currentPlayIndex = 0;
    private int playSpeed = 1;
    // 修复：使用静态 Handler 防止内存泄漏
    private static class PlaybackHandler extends Handler {
        private final WeakReference<TrackActivity> activityRef;
        
        PlaybackHandler(TrackActivity activity) {
            activityRef = new WeakReference<>(activity);
        }
        
        @Override
        public void handleMessage(Message msg) {
            TrackActivity activity = activityRef.get();
            if (activity != null && activity.isPlaying) {
                activity.moveToNextPoint();
            }
        }
    }
    private PlaybackHandler playHandler;
    private Marker playMarker = null;
    private Polyline playedPolyline = null;
    private List<LatLng> playedPoints = new ArrayList<>();
    private ValueAnimator moveAnimator = null;
    private LatLng currentPlayPosition = null;
    
    private ImageButton toggleMarkersBtn;
    private ImageButton togglePolylineBtn;
    private ImageButton toggleSatelliteBtn;
    private ImageButton toggleDisplayModeBtn;
    private ImageButton statisticsBtn; // 统计按钮
    private TextView displayModeText;
    private boolean showMarkers = true;
    private boolean showPolyline = true;
    private boolean isSatelliteMode = false;
    private boolean isSimpleMode = false; // 显示模式：false=详细模式，true=精简模式
    
    // 加载进度条
    private ProgressBar loadingProgress;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track);

        databaseHelper = new DatabaseHelper(this);
        selectedDate = Calendar.getInstance();

        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String selectedDeviceId = prefs.getString("selected_device_id", "");
        if (!selectedDeviceId.isEmpty()) {
            selectedDevice = databaseHelper.getDevice(selectedDeviceId);
        }

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        initMap();

        ImageButton backBtn = findViewById(R.id.back_btn);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        dateBtn = findViewById(R.id.date_btn);
        startDate = (Calendar) selectedDate.clone();
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);
        
        endDate = (Calendar) selectedDate.clone();
        endDate.set(Calendar.HOUR_OF_DAY, 23);
        endDate.set(Calendar.MINUTE, 59);
        endDate.set(Calendar.SECOND, 59);
        endDate.set(Calendar.MILLISECOND, 999);
        
        dateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker();
            }
        });

        startTimeBtn = findViewById(R.id.start_time_btn);
        startTimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStartTimePicker();
            }
        });

        endTimeBtn = findViewById(R.id.end_time_btn);
        endTimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEndTimePicker();
            }
        });

        resetTimeBtn = findViewById(R.id.reset_time_btn);
        resetTimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetTimeRange();
            }
        });

        updateDateBtnText();

        ImageButton prevDayBtn = findViewById(R.id.prev_day_btn);
        prevDayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToPreviousDay();
            }
        });

        ImageButton nextDayBtn = findViewById(R.id.next_day_btn);
        nextDayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToNextDay();
            }
        });

        trackPointTime = findViewById(R.id.track_point_time);
        trackPointAddress = findViewById(R.id.track_point_address);
        totalDistanceText = findViewById(R.id.total_distance_text);
        
        try {
            geocodeSearch = new GeocodeSearch(this);
            geocodeSearch.setOnGeocodeSearchListener(this);
        } catch (com.amap.api.services.core.AMapException e) {
            Log.e(TAG, "GeocodeSearch init failed: " + e.getMessage());
        }
        
        // 初始化 Handler
        playHandler = new PlaybackHandler(this);

        initPlaybackControls();
        
        initToolbar();
        
        // 初始化加载进度条
        loadingProgress = findViewById(R.id.loading_progress);

        checkAndCleanOldTrackData();
        
        loadTrackData();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        Log.d(TAG, "TrackActivity onDestroy - cleaning up resources");
        
        // 1. 停止播放
        stopPlayback();
        
        // 2. 移除所有 Marker
        for (Marker marker : positionMarkers) {
            marker.remove();
        }
        positionMarkers.clear();
        
        for (Marker marker : arrowMarkers) {
            marker.remove();
        }
        arrowMarkers.clear();
        
        // 3. 清理播放相关对象
        if (playMarker != null) {
            playMarker.remove();
            playMarker = null;
        }
        if (playedPolyline != null) {
            playedPolyline.remove();
            playedPolyline = null;
        }
        playedPoints.clear();
        currentPlayPosition = null;
        
        // 4. 清理轨迹线
        if (trackPolyline != null) {
            trackPolyline.remove();
            trackPolyline = null;
        }
        
        // 5. 停止动画
        if (moveAnimator != null && moveAnimator.isRunning()) {
            moveAnimator.cancel();
            moveAnimator = null;
        }
        
        // 6. 清理数据集合
        allLocationRecords.clear();
        stayPoints.clear();
        
        // 7. 清理 Handler 消息
        if (playHandler != null) {
            playHandler.removeCallbacksAndMessages(null);
        }
        
        // 8. 释放 GeocodeSearch
        if (geocodeSearch != null) {
            geocodeSearch = null;
        }
        
        // 9. 清理 MapView
        if (mapView != null) {
            mapView.onDestroy();
        }
        
        Log.d(TAG, "TrackActivity resources cleaned up");
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        
        // 停止播放，释放资源
        if (isPlaying) {
            stopPlayback();
        }
        
        // 移除 Handler 消息
        if (playHandler != null) {
            playHandler.removeCallbacksAndMessages(null);
        }
        
        Log.d(TAG, "TrackActivity onStop - playback stopped");
    }

    private void initPlaybackControls() {
        playBtn = findViewById(R.id.play_btn);
        playbackSeekbar = findViewById(R.id.playback_seekbar);
        currentTimeText = findViewById(R.id.current_time_text);
        totalTimeText = findViewById(R.id.total_time_text);
        speedBtn = findViewById(R.id.speed_btn);

        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPlaying) {
                    pausePlayback();
                } else {
                    startPlayback();
                }
            }
        });

        playbackSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && !allLocationRecords.isEmpty()) {
                    currentPlayIndex = progress;
                    updatePlayPosition();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (isPlaying) {
                    pausePlayback();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        speedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleSpeed();
            }
        });
    }

    private void initToolbar() {
        toggleMarkersBtn = findViewById(R.id.toggle_markers_btn);
        togglePolylineBtn = findViewById(R.id.toggle_polyline_btn);
        toggleSatelliteBtn = findViewById(R.id.toggle_satellite_btn);
        toggleDisplayModeBtn = findViewById(R.id.toggle_display_mode_btn);
        statisticsBtn = findViewById(R.id.statistics_btn);
        displayModeText = findViewById(R.id.display_mode_text);

        toggleMarkersBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMarkers();
            }
        });

        togglePolylineBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePolyline();
            }
        });

        toggleSatelliteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSatellite();
            }
        });
        
        toggleDisplayModeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDisplayMode();
            }
        });
        
        statisticsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTrackStatistics();
            }
        });
    }

    private void toggleMarkers() {
        showMarkers = !showMarkers;
        for (Marker marker : positionMarkers) {
            marker.setVisible(showMarkers);
        }
        for (Marker marker : arrowMarkers) {
            marker.setVisible(showMarkers);
        }
        
        if (showMarkers) {
            toggleMarkersBtn.setAlpha(1.0f);
            Toast.makeText(this, R.string.show_markers, Toast.LENGTH_SHORT).show();
        } else {
            toggleMarkersBtn.setAlpha(0.5f);
            Toast.makeText(this, R.string.hide_markers, Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePolyline() {
        showPolyline = !showPolyline;
        if (trackPolyline != null) {
            trackPolyline.setVisible(showPolyline);
        }
        
        if (showPolyline) {
            togglePolylineBtn.setAlpha(1.0f);
            Toast.makeText(this, R.string.show_polyline, Toast.LENGTH_SHORT).show();
        } else {
            togglePolylineBtn.setAlpha(0.5f);
            Toast.makeText(this, R.string.hide_polyline, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleSatellite() {
        isSatelliteMode = !isSatelliteMode;
        if (aMap != null) {
            aMap.setMapType(isSatelliteMode ? AMap.MAP_TYPE_SATELLITE : AMap.MAP_TYPE_NORMAL);
        }
        
        if (isSatelliteMode) {
            toggleSatelliteBtn.setAlpha(1.0f);
            Toast.makeText(this, R.string.satellite_mode, Toast.LENGTH_SHORT).show();
        } else {
            toggleSatelliteBtn.setAlpha(0.5f);
            Toast.makeText(this, R.string.standard_map_mode, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleDisplayMode() {
        isSimpleMode = !isSimpleMode;
        
        // 更新按钮文本
        if (displayModeText != null) {
            displayModeText.setText(isSimpleMode ? R.string.simple : R.string.detailed);
        }
        
        // 重新渲染轨迹
        if (!allLocationRecords.isEmpty()) {
            // 保存当前数据，因为 clearTrack 不再清空数据
            List<LocationRecord> recordsToRender = new ArrayList<>(allLocationRecords);
            
            clearTrack();
            renderTrack(recordsToRender);
            
            // 确保 Marker 和 Polyline 的可见性与当前设置一致
            for (Marker marker : positionMarkers) {
                marker.setVisible(showMarkers);
            }
            for (Marker marker : arrowMarkers) {
                marker.setVisible(showMarkers);
            }
            if (trackPolyline != null) {
                trackPolyline.setVisible(showPolyline);
            }
        }
        
        String modeText = isSimpleMode ? getString(R.string.display_mode_simple) : getString(R.string.display_mode_detailed);
        Toast.makeText(this, modeText, Toast.LENGTH_SHORT).show();
        
        Log.d(TAG, "Display mode changed to: " + (isSimpleMode ? "simple" : "detailed"));
    }

    private void startPlayback() {
        if (allLocationRecords.isEmpty()) {
            Toast.makeText(this, R.string.no_track_data_to_play, Toast.LENGTH_SHORT).show();
            return;
        }

        isPlaying = true;
        playBtn.setImageResource(android.R.drawable.ic_media_pause);
        
        if (playMarker == null) {
            LocationRecord record = allLocationRecords.get(0);
            LatLng startPos = CoordinateUtils.wgs84ToGcj02(record.getLatitude(), record.getLongitude());
            currentPlayPosition = startPos;
            
            MarkerOptions markerOptions = new MarkerOptions()
                .position(startPos)
                .title(getString(R.string.play_position))
                .icon(createPlayMarker())
                .anchor(0.5f, 0.5f);
            playMarker = aMap.addMarker(markerOptions);
            playedPoints.add(startPos);
        }
        
        moveToNextPoint();
    }

    private void pausePlayback() {
        isPlaying = false;
        playBtn.setImageResource(android.R.drawable.ic_media_play);
        if (moveAnimator != null && moveAnimator.isRunning()) {
            moveAnimator.cancel();
        }
        playHandler.removeCallbacksAndMessages(null);
    }

    private void stopPlayback() {
        isPlaying = false;
        playBtn.setImageResource(android.R.drawable.ic_media_play);
        if (moveAnimator != null && moveAnimator.isRunning()) {
            moveAnimator.cancel();
        }
        playHandler.removeCallbacksAndMessages(null);
        currentPlayIndex = 0;
        playedPoints.clear();
        currentPlayPosition = null;
        
        if (playMarker != null) {
            playMarker.remove();
            playMarker = null;
        }
        if (playedPolyline != null) {
            playedPolyline.remove();
            playedPolyline = null;
        }
        
        playbackSeekbar.setProgress(0);
        currentTimeText.setText("00:00:00");
    }

    private void moveToNextPoint() {
        if (!isPlaying || currentPlayIndex >= allLocationRecords.size() - 1) {
            if (currentPlayIndex >= allLocationRecords.size() - 1) {
                pausePlayback();
                currentPlayIndex = allLocationRecords.size() - 1;
            }
            return;
        }

        LocationRecord fromRecord = allLocationRecords.get(currentPlayIndex);
        LocationRecord toRecord = allLocationRecords.get(currentPlayIndex + 1);
        
        LatLng fromPos = CoordinateUtils.wgs84ToGcj02(fromRecord.getLatitude(), fromRecord.getLongitude());
        LatLng toPos = CoordinateUtils.wgs84ToGcj02(toRecord.getLatitude(), toRecord.getLongitude());
        
        if (currentPlayPosition != null) {
            fromPos = currentPlayPosition;
        }
        
        final LatLng finalFromPos = fromPos;
        final LatLng finalToPos = toPos;
        
        // 优化 1: 基于距离计算动画时长，实现匀速播放
        // 计算两点之间的距离（米）
        double distance = CoordinateUtils.calculateDistanceMeters(
            fromRecord.getLatitude(), fromRecord.getLongitude(),
            toRecord.getLatitude(), toRecord.getLongitude()
        );
        
        // 根据距离和播放速度计算动画时长
        // 基准速度：1000 米/秒（1x 速度下，1000 米需要 1 秒）
        double baseSpeed = 1000.0; // 米/秒
        long duration = (long) (distance / (baseSpeed * playSpeed) * 1000);
        
        // 限制时长范围，避免过快或过慢
        if (duration < 300) duration = 300; // 最短 0.3 秒
        if (duration > 5000) duration = 5000; // 最长 5 秒
        
        moveAnimator = ValueAnimator.ofFloat(0f, 1f);
        moveAnimator.setDuration(duration);
        
        moveAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = animation.getAnimatedFraction();
                
                double lat = finalFromPos.latitude + (finalToPos.latitude - finalFromPos.latitude) * fraction;
                double lng = finalFromPos.longitude + (finalToPos.longitude - finalFromPos.longitude) * fraction;
                LatLng newPos = new LatLng(lat, lng);
                
                currentPlayPosition = newPos;
                
                if (playMarker != null) {
                    playMarker.setPosition(newPos);
                }
                
                // 优化 2: 只在关键点更新相机，减少卡顿
                // 每 5 个点或到达终点时更新相机
                if (currentPlayIndex % 5 == 0 || currentPlayIndex >= allLocationRecords.size() - 2) {
                    aMap.animateCamera(com.amap.api.maps.CameraUpdateFactory.newLatLng(newPos));
                }
            }
        });
        
        moveAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {}
            
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isPlaying) {
                    currentPlayIndex++;
                    currentPlayPosition = finalToPos;
                    
                    playedPoints.add(finalToPos);
                    updatePlayedPolyline();
                    
                    playbackSeekbar.setProgress(currentPlayIndex);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                    String timeStr = sdf.format(new Date(toRecord.getTimestamp()));
                    currentTimeText.setText(timeStr.substring(timeStr.indexOf(" ") + 1));
                    trackPointTime.setText(timeStr);
                    
                    // 优化 3: 逆地理编码缓存 + 降低频率
                    // 只在停留点或每隔 10 个点才请求地址
                    if (currentPlayIndex % 10 == 0 || isStayPointAt(currentPlayIndex)) {
                        getAddressForLocation(finalToPos);
                    }
                    
                    // 修复：使用 Handler 消息机制
                    playHandler.sendEmptyMessageDelayed(0, 50);
                }
            }
            
            @Override
            public void onAnimationCancel(Animator animation) {}
            
            @Override
            public void onAnimationRepeat(Animator animation) {}
        });
        
        moveAnimator.start();
    }

    private void updatePlayedPolyline() {
        // 优化 4: 使用 addPoint 而不是重新创建整个 Polyline
        if (playedPoints.size() > 1) {
            if (playedPolyline == null) {
                // 第一次创建
                PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(playedPoints)
                    .color(0xFFFF5722)
                    .width(12f);
                playedPolyline = aMap.addPolyline(polylineOptions);
            } else {
                // 后续只添加新点（高德地图 SDK 支持动态更新）
                playedPolyline.setPoints(playedPoints);
            }
        }
    }

    private void updatePlayPosition() {
        if (allLocationRecords.isEmpty()) return;

        playedPoints.clear();
        for (int i = 0; i <= currentPlayIndex && i < allLocationRecords.size(); i++) {
            LocationRecord record = allLocationRecords.get(i);
            LatLng latLng = CoordinateUtils.wgs84ToGcj02(record.getLatitude(), record.getLongitude());
            playedPoints.add(latLng);
        }

        updatePlayedPolyline();

        if (!playedPoints.isEmpty()) {
            LatLng latLng = playedPoints.get(playedPoints.size() - 1);
            currentPlayPosition = latLng;
            
            if (playMarker != null) {
                playMarker.setPosition(latLng);
            } else {
                LocationRecord record = allLocationRecords.get(currentPlayIndex);
                MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.play_position))
                    .icon(createPlayMarker())
                    .anchor(0.5f, 0.5f);
                playMarker = aMap.addMarker(markerOptions);
            }
            
            aMap.animateCamera(com.amap.api.maps.CameraUpdateFactory.newLatLng(latLng));
            
            // 优化：降低逆地理编码频率
            if (currentPlayIndex % 10 == 0 || isStayPointAt(currentPlayIndex)) {
                getAddressForLocation(latLng);
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
        String timeStr = sdf.format(new Date(allLocationRecords.get(currentPlayIndex).getTimestamp()));
        currentTimeText.setText(timeStr.substring(timeStr.indexOf(" ") + 1));
        trackPointTime.setText(timeStr);
    }
    
    /**
     * 判断当前索引是否是停留点
     */
    private boolean isStayPointAt(int index) {
        if (stayPoints == null || stayPoints.isEmpty() || index >= allLocationRecords.size()) {
            return false;
        }
        
        long timestamp = allLocationRecords.get(index).getTimestamp();
        
        // 查找是否有停留点包含这个时间戳
        for (StayPoint sp : stayPoints) {
            if (sp.isStayPoint()) {
                List<LocationRecord> records = sp.getMergedRecords();
                for (LocationRecord record : records) {
                    if (record.getTimestamp() == timestamp) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    private void cycleSpeed() {
        if (playSpeed == 1) {
            playSpeed = 2;
        } else if (playSpeed == 2) {
            playSpeed = 4;
        } else if (playSpeed == 4) {
            playSpeed = 8;
        } else {
            playSpeed = 1;
        }
        speedBtn.setText(playSpeed + "x");
    }

    private com.amap.api.maps.model.BitmapDescriptor createPlayMarker() {
        // 获取设备的 tag
        String deviceTag = "";
        if (selectedDevice != null && selectedDevice.getTag() != null && !selectedDevice.getTag().isEmpty()) {
            deviceTag = selectedDevice.getTag();
        }
        
        // 根据 tag 获取对应的 emoji 图标
        String emoji = getTagEmoji(deviceTag);
        
        // 使用 emoji 绘制播放图标
        return createEmojiMarker(emoji);
    }
    
    /**
     * 根据 tag 获取对应的 emoji 图标
     */
    private String getTagEmoji(String tag) {
        if (tag == null || tag.isEmpty()) {
            return "🏷️"; // 默认标签图标
        }
        
        switch (tag) {
            case "dog":
                return "🐕";
            case "boy":
                return "👦";
            case "car":
                return "🚗";
            case "bike":
                return "🚴";
            case "bank_card":
                return "💳";
            case "girl":
                return "👧";
            case "key":
                return "🔑";
            case "moto":
                return "🏍️";
            case "pig":
                return "🐷";
            case "wallet":
                return "👛";
            case "bag":
                return "👜";
            case "cat":
                return "🐱";
            case "bird":
                return "🐦";
            default:
                return "🏷️";
        }
    }
    
    /**
     * 使用 emoji 创建播放图标
     */
    private com.amap.api.maps.model.BitmapDescriptor createEmojiMarker(String emoji) {
        int size = 80;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        // 绘制背景圆形（橙色）
        paint.setColor(Color.parseColor("#FF5722"));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, paint);
        
        // 绘制白色边框
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, paint);
        
        // 绘制 emoji 图标
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(48); // emoji 字体大小
        paint.setTextAlign(Paint.Align.CENTER);
        
        // 计算 emoji 垂直居中位置
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float textHeight = fontMetrics.bottom - fontMetrics.top;
        float textY = size / 2f + textHeight / 2 - fontMetrics.bottom;
        
        canvas.drawText(emoji, size / 2f, textY, paint);
        
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }
    
    /**
     * 创建默认的文字图标（后备方案）
     */
    private com.amap.api.maps.model.BitmapDescriptor createDefaultTextMarker(String deviceTag) {
        return createEmojiMarker("🏷️");
    }

    private void initMap() {
        if (aMap == null) {
            aMap = mapView.getMap();
            aMap.getUiSettings().setScaleControlsEnabled(true);
            aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.zoomTo(17));
            aMap.setOnMarkerClickListener(this);
        }
    }
    
    @Override
    public boolean onMarkerClick(Marker marker) {
        int markerIndex = positionMarkers.indexOf(marker);
        if (markerIndex >= 0 && markerIndex < stayPoints.size()) {
            StayPoint stayPoint = stayPoints.get(markerIndex);
            
            SimpleDateFormat timeSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            StringBuilder info = new StringBuilder();
            
            info.append(getString(R.string.track)).append(" #").append(stayPoint.getOriginalIndex()).append("\n");
            info.append(timeSdf.format(new Date(stayPoint.getArriveTime())));
            
            if (stayPoint.getMergedCount() > 1) {
                info.append(" - ").append(timeSdf.format(new Date(stayPoint.getLeaveTime())));
            }
            
            if (stayPoint.isStayPoint()) {
                info.append("\n").append(getString(R.string.stay_duration, stayPoint.getStayDurationFormatted()));
            }
            
            if (stayPoint.getMergedCount() > 1) {
                info.append("\n").append(getString(R.string.merged_points, stayPoint.getMergedCount()));
            }
            
            trackPointTime.setText(timeSdf.format(new Date(stayPoint.getArriveTime())));
            
            LatLng latLng = marker.getPosition();
            getAddressForLocation(latLng);
            
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.track) + " #" + stayPoint.getOriginalIndex());
            builder.setMessage(info.toString());
            builder.setPositiveButton(getString(R.string.confirm), null);
            builder.show();
            
            return true;
        }
        return false;
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                selectedDate.set(Calendar.YEAR, year);
                selectedDate.set(Calendar.MONTH, month);
                selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                
                startDate = (Calendar) selectedDate.clone();
                startDate.set(Calendar.HOUR_OF_DAY, 0);
                startDate.set(Calendar.MINUTE, 0);
                startDate.set(Calendar.SECOND, 0);
                startDate.set(Calendar.MILLISECOND, 0);
                
                endDate = (Calendar) selectedDate.clone();
                endDate.set(Calendar.HOUR_OF_DAY, 23);
                endDate.set(Calendar.MINUTE, 59);
                endDate.set(Calendar.SECOND, 59);
                endDate.set(Calendar.MILLISECOND, 999);
                
                updateDateBtnText();
                loadTrackData();
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        );

        Calendar minDate = Calendar.getInstance();
        minDate.add(Calendar.MONTH, -1);
        datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());

        Calendar maxDate = Calendar.getInstance();
        datePickerDialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());

        datePickerDialog.show();
    }

    private void updateDateBtnText() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        
        Calendar today = Calendar.getInstance();
        if (selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            selectedDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
            selectedDate.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)) {
            dateBtn.setText(getString(R.string.today));
        } else {
            dateBtn.setText(sdf.format(selectedDate.getTime()));
        }
        
        startTimeBtn.setText(timeSdf.format(startDate.getTime()));
        endTimeBtn.setText(timeSdf.format(endDate.getTime()));
    }

    private void showStartTimePicker() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.select_start_time));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setPadding(50, 40, 50, 10);

        final android.widget.NumberPicker hourPicker = new android.widget.NumberPicker(this);
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setValue(startDate.get(Calendar.HOUR_OF_DAY));
        hourPicker.setWrapSelectorWheel(true);
        hourPicker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams hourParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
        hourPicker.setLayoutParams(hourParams);

        TextView colonText = new TextView(this);
        colonText.setText(":");
        colonText.setTextSize(24);
        colonText.setPadding(16, 0, 16, 0);

        final android.widget.NumberPicker minutePicker = new android.widget.NumberPicker(this);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(startDate.get(Calendar.MINUTE));
        minutePicker.setWrapSelectorWheel(true);
        minutePicker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams minuteParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
        minutePicker.setLayoutParams(minuteParams);

        layout.addView(hourPicker);
        layout.addView(colonText);
        layout.addView(minutePicker);

        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startDate.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
                startDate.set(Calendar.MINUTE, minutePicker.getValue());
                startDate.set(Calendar.SECOND, 0);
                startDate.set(Calendar.MILLISECOND, 0);
                updateDateBtnText();
                loadTrackData();
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void showEndTimePicker() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.select_end_time));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setPadding(50, 40, 50, 10);

        final android.widget.NumberPicker hourPicker = new android.widget.NumberPicker(this);
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setValue(endDate.get(Calendar.HOUR_OF_DAY));
        hourPicker.setWrapSelectorWheel(true);
        hourPicker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams hourParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
        hourPicker.setLayoutParams(hourParams);

        TextView colonText = new TextView(this);
        colonText.setText(":");
        colonText.setTextSize(24);
        colonText.setPadding(16, 0, 16, 0);

        final android.widget.NumberPicker minutePicker = new android.widget.NumberPicker(this);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(endDate.get(Calendar.MINUTE));
        minutePicker.setWrapSelectorWheel(true);
        minutePicker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams minuteParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
        minutePicker.setLayoutParams(minuteParams);

        layout.addView(hourPicker);
        layout.addView(colonText);
        layout.addView(minutePicker);

        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                endDate.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
                endDate.set(Calendar.MINUTE, minutePicker.getValue());
                endDate.set(Calendar.SECOND, 59);
                endDate.set(Calendar.MILLISECOND, 999);
                updateDateBtnText();
                loadTrackData();
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void resetTimeRange() {
        selectedDate = Calendar.getInstance();
        
        startDate = (Calendar) selectedDate.clone();
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);
        
        endDate = (Calendar) selectedDate.clone();
        endDate.set(Calendar.HOUR_OF_DAY, 23);
        endDate.set(Calendar.MINUTE, 59);
        endDate.set(Calendar.SECOND, 59);
        endDate.set(Calendar.MILLISECOND, 999);
        
        updateDateBtnText();
        loadTrackData();
    }

    private void goToPreviousDay() {
        selectedDate.add(Calendar.DAY_OF_MONTH, -1);
        Calendar minDate = Calendar.getInstance();
        minDate.add(Calendar.MONTH, -1);
        
        if (selectedDate.before(minDate)) {
            selectedDate.add(Calendar.DAY_OF_MONTH, 1);
            Toast.makeText(this, R.string.only_last_month_track, Toast.LENGTH_SHORT).show();
            return;
        }
        
        startDate = (Calendar) selectedDate.clone();
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);
        
        endDate = (Calendar) selectedDate.clone();
        endDate.set(Calendar.HOUR_OF_DAY, 23);
        endDate.set(Calendar.MINUTE, 59);
        endDate.set(Calendar.SECOND, 59);
        endDate.set(Calendar.MILLISECOND, 999);
        
        updateDateBtnText();
        loadTrackData();
    }

    private void goToNextDay() {
        selectedDate.add(Calendar.DAY_OF_MONTH, 1);
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 23);
        today.set(Calendar.MINUTE, 59);
        today.set(Calendar.SECOND, 59);
        
        if (selectedDate.after(today)) {
            selectedDate.add(Calendar.DAY_OF_MONTH, -1);
            Toast.makeText(this, R.string.cannot_view_future_track, Toast.LENGTH_SHORT).show();
            return;
        }
        
        startDate = (Calendar) selectedDate.clone();
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);
        
        endDate = (Calendar) selectedDate.clone();
        endDate.set(Calendar.HOUR_OF_DAY, 23);
        endDate.set(Calendar.MINUTE, 59);
        endDate.set(Calendar.SECOND, 59);
        endDate.set(Calendar.MILLISECOND, 999);
        
        updateDateBtnText();
        loadTrackData();
    }

    private void loadTrackData() {
        // 默认使用智能缓存策略，不强制同步
        loadTrackData(false);
    }

    private void loadTrackData(final boolean forceSync) {
        if (selectedDevice == null) {
            Toast.makeText(this, R.string.please_select_device, Toast.LENGTH_SHORT).show();
            return;
        }

        clearTrack();

        final Calendar startTime = (Calendar) startDate.clone();
        final Calendar endTime = (Calendar) endDate.clone();
        
        Log.d(TAG, "========== STARTING TRACK LOAD ==========");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
        Log.d(TAG, "Querying from: " + sdf.format(startTime.getTime()));
        Log.d(TAG, "Querying to: " + sdf.format(endTime.getTime()));
        Log.d(TAG, "Device ID: " + selectedDevice.getDeviceId());
        Log.d(TAG, "Device Num: " + selectedDevice.getDeviceNum());
        Log.d(TAG, "forceSync: " + forceSync);

        // 【优化1】智能缓存策略：先查询本地数据
        List<LocationRecord> locationRecords = databaseHelper.getLocationRecords(
            selectedDevice.getDeviceId(),
            startTime.getTimeInMillis(),
            endTime.getTimeInMillis()
        );

        // 如果本地有数据且不是强制同步，检查数据新鲜度
        if (!forceSync && locationRecords != null && !locationRecords.isEmpty()) {
            long newestTimestamp = locationRecords.get(locationRecords.size() - 1).getTimestamp();
            long cacheAge = System.currentTimeMillis() - newestTimestamp;
            
            // 如果数据在 5 分钟内，直接使用本地缓存
            if (cacheAge < 300000) {
                Log.d(TAG, "✅ Using cached data (age: " + (cacheAge / 1000) + "s)");
                renderTrack(locationRecords);
                return;
            } else {
                Log.d(TAG, "Cache age: " + (cacheAge / 1000) + "s, need to sync");
            }
        }

        // 【优化2】增量同步策略：检查本地最新数据
        if (locationRecords != null && !locationRecords.isEmpty()) {
            long localLatestTime = locationRecords.get(locationRecords.size() - 1).getTimestamp();
            long syncStartTime = Math.max(startTime.getTimeInMillis(), localLatestTime);
            
            // 如果本地数据已经覆盖查询范围，不需要同步
            if (syncStartTime >= endTime.getTimeInMillis()) {
                Log.d(TAG, "✅ Local data is up to date, no sync needed");
                renderTrack(locationRecords);
                return;
            }
            
            // 增量同步缺失的数据
            Log.d(TAG, "🔄 Incremental sync from: " + sdf.format(new java.util.Date(syncStartTime)));
            Toast.makeText(this, R.string.loading_track_data, Toast.LENGTH_SHORT).show();
            syncTrackDataIncremental(
                selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId(),
                syncStartTime,
                endTime.getTimeInMillis(),
                locationRecords // 传入已有数据用于合并
            );
        } else {
            // 本地没有数据，完整同步
            Log.d(TAG, "🔄 Full sync from server...");
            Toast.makeText(this, R.string.loading_track_data, Toast.LENGTH_SHORT).show();
            syncTrackDataFromServerAndReload(
                selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId(),
                startTime.getTimeInMillis(),
                endTime.getTimeInMillis()
            );
        }
    }

    /**
     * 增量同步轨迹数据
     */
    private void syncTrackDataIncremental(final String deviceNum, final long startTime, final long endTime, final List<LocationRecord> existingRecords) {
        Log.d(TAG, "=== INCREMENTAL SYNC START ===");
        Log.d(TAG, "DeviceNum: " + deviceNum);
        Log.d(TAG, "Time range: " + startTime + " - " + endTime);
        Log.d(TAG, "Existing records: " + (existingRecords != null ? existingRecords.size() : 0));
        
        NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(deviceNum));
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    NewApiService apiService = NewApiService.getInstance();
                    List<NewApiService.LocationInfo> locations = apiService.getLocations(deviceNum, startTime, endTime);
                    
                    if (locations != null && !locations.isEmpty()) {
                        int addedCount = 0;
                        int skippedCount = 0;
                        
                        for (NewApiService.LocationInfo loc : locations) {
                            if (loc.latitude != 0 && loc.longitude != 0 && loc.timestamp > 0) {
                                LocationRecord record = new LocationRecord(
                                    selectedDevice.getDeviceId(),
                                    loc.latitude,
                                    loc.longitude,
                                    loc.timestamp
                                );
                                
                                // 检查是否重复
                                List<LocationRecord> existingRecords = databaseHelper.getLocationRecords(
                                    selectedDevice.getDeviceId(),
                                    loc.timestamp - 1000,
                                    loc.timestamp + 1000
                                );
                                
                                if (existingRecords == null || existingRecords.isEmpty()) {
                                    databaseHelper.addLocationRecord(record);
                                    addedCount++;
                                } else {
                                    skippedCount++;
                                }
                            }
                        }
                        
                        Log.d(TAG, "Incremental sync summary: added=" + addedCount + ", skipped=" + skippedCount);
                        
                        // 创建 final 变量供内部类使用
                        final int finalAddedCount = addedCount;
                        final int finalSkippedCount = skippedCount;
                        
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // 合并已有数据和新数据
                                List<LocationRecord> allRecords = new ArrayList<>();
                                if (existingRecords != null) {
                                    allRecords.addAll(existingRecords);
                                }
                                
                                // 加载新同步的数据
                                List<LocationRecord> newRecords = databaseHelper.getLocationRecords(
                                    selectedDevice.getDeviceId(),
                                    startTime,
                                    endTime
                                );
                                
                                if (newRecords != null && !newRecords.isEmpty()) {
                                    allRecords.addAll(newRecords);
                                    
                                    // 去重并排序
                                    java.util.Collections.sort(allRecords, new java.util.Comparator<LocationRecord>() {
                                        @Override
                                        public int compare(LocationRecord r1, LocationRecord r2) {
                                            return Long.compare(r1.getTimestamp(), r2.getTimestamp());
                                        }
                                    });
                                    
                                    // 去重
                                    List<LocationRecord> uniqueRecords = new ArrayList<>();
                                    if (!allRecords.isEmpty()) {
                                        uniqueRecords.add(allRecords.get(0));
                                        for (int i = 1; i < allRecords.size(); i++) {
                                            if (allRecords.get(i).getTimestamp() != allRecords.get(i - 1).getTimestamp()) {
                                                uniqueRecords.add(allRecords.get(i));
                                            }
                                        }
                                    }
                                    
                                    Log.d(TAG, "Merged records: " + uniqueRecords.size() + " (existing: " + (existingRecords != null ? existingRecords.size() : 0) + ", new: " + finalAddedCount + ")");
                                    renderTrack(uniqueRecords);
                                } else {
                                    renderTrack(existingRecords);
                                }
                            }
                        });
                    } else {
                        Log.d(TAG, "No new locations from server");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                renderTrack(existingRecords);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in incremental sync: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(TrackActivity.this, R.string.sync_track_error, Toast.LENGTH_SHORT).show();
                            renderTrack(existingRecords);
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * 渲染轨迹（从 loadTrackData 中提取）
     */
    private void renderTrack(List<LocationRecord> locationRecords) {
        // 显示加载进度条
        showLoading();
        
        if (locationRecords == null || locationRecords.isEmpty()) {
            Log.d(TAG, "NO RECORDS TO RENDER!");
            Toast.makeText(this, R.string.no_track_data, Toast.LENGTH_SHORT).show();
            hideLoading();
            updatePlaybackInfo(0);
            return;
        }

        Log.d(TAG, "Rendering track with " + locationRecords.size() + " records");
        
        java.util.Collections.sort(locationRecords, new java.util.Comparator<LocationRecord>() {
            @Override
            public int compare(LocationRecord r1, LocationRecord r2) {
                return Long.compare(r1.getTimestamp(), r2.getTimestamp());
            }
        });
        
        List<LocationRecord> filteredRecords = filterAbnormalPoints(locationRecords);
        allLocationRecords = new ArrayList<>(filteredRecords);
        
        stayPoints = processStayPoints(filteredRecords);
        Log.d(TAG, "Processed " + stayPoints.size() + " stay points from " + filteredRecords.size() + " filtered records (original: " + locationRecords.size() + ")");
        
        Log.d(TAG, "After sorting by time, first point: " + 
            new SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new Date(filteredRecords.get(0).getTimestamp())) +
            ", last point: " + 
            new SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new Date(filteredRecords.get(filteredRecords.size()-1).getTimestamp())));

        List<LatLng> latLngList = new ArrayList<>();
        for (StayPoint stayPoint : stayPoints) {
            LatLng latLng = CoordinateUtils.wgs84ToGcj02(stayPoint.getLatitude(), stayPoint.getLongitude());
            latLngList.add(latLng);
        }

        if (latLngList.size() > 1) {
            PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(latLngList)
                .color(0xFF0088FF)
                .width(12f)
                .setDottedLine(false);
            trackPolyline = aMap.addPolyline(polylineOptions);
            trackPolyline.setVisible(showPolyline);
            
            addDirectionArrows(latLngList);
        }

        // 优化：根据显示模式决定是否只显示关键节点
        for (int i = 0; i < stayPoints.size(); i++) {
            StayPoint stayPoint = stayPoints.get(i);
            
            // 精简模式：如果不是停留点，且不是起点或终点，则跳过
            if (isSimpleMode && !stayPoint.isStayPoint() && i != 0 && i != stayPoints.size() - 1) {
                Log.d(TAG, "Skipping ordinary point " + i + " (simple mode)");
                continue;
            }
            
            LatLng latLng = CoordinateUtils.wgs84ToGcj02(stayPoint.getLatitude(), stayPoint.getLongitude());
            
            com.amap.api.maps.model.BitmapDescriptor icon;
            String title;
            String snippet;
            float anchorV = 0.5f;
            
            SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
            
            if (i == 0) {
                icon = createStartEndMarker(getString(R.string.start_point), Color.parseColor("#4CAF50"));
                title = getString(R.string.start_point);
                anchorV = 1.0f;
            } else if (i == stayPoints.size() - 1) {
                icon = createStartEndMarker(getString(R.string.end_point), Color.parseColor("#F44336"));
                title = getString(R.string.end_point);
                anchorV = 1.0f;
            } else {
                if (stayPoint.isStayPoint()) {
                    icon = createStayPointMarker(stayPoint.getOriginalIndex(), stayPoint.getStayDurationFormatted());
                } else {
                    icon = createNumberedMarker(stayPoint.getOriginalIndex());
                }
                title = String.valueOf(stayPoint.getOriginalIndex());
                anchorV = 0.5f;
            }
            
            StringBuilder snippetBuilder = new StringBuilder();
            snippetBuilder.append(timeSdf.format(new Date(stayPoint.getArriveTime())));
            if (stayPoint.getMergedCount() > 1) {
                snippetBuilder.append(" - ").append(timeSdf.format(new Date(stayPoint.getLeaveTime())));
            }
            if (stayPoint.isStayPoint()) {
                snippetBuilder.append("\n").append(getString(R.string.stay_duration, stayPoint.getStayDurationFormatted()));
            }
            if (stayPoint.getMergedCount() > 1) {
                snippetBuilder.append("\n").append(getString(R.string.merged_points, stayPoint.getMergedCount()));
            }
            snippet = snippetBuilder.toString();
            
            MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(snippet)
                .icon(icon)
                .anchor(0.5f, anchorV);

            Marker marker = aMap.addMarker(markerOptions);
            marker.setZIndex(100);
            marker.setVisible(showMarkers);
            positionMarkers.add(marker);
        }

        if (!latLngList.isEmpty()) {
            if (latLngList.size() == 1) {
                aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(latLngList.get(0), 17));
            } else {
                com.amap.api.maps.model.LatLngBounds.Builder builder = new com.amap.api.maps.model.LatLngBounds.Builder();
                for (LatLng latLng : latLngList) {
                    builder.include(latLng);
                }
                com.amap.api.maps.model.LatLngBounds bounds = builder.build();
                aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngBounds(bounds, 100));
            }
        }

        updatePlaybackInfo(allLocationRecords.size());
        
        Toast.makeText(this, getString(R.string.loaded_stay_points, stayPoints.size()), Toast.LENGTH_SHORT).show();
        
        double totalDistance = calculateTotalDistance(latLngList);
        totalDistanceText.setText(String.format("%.2f km", totalDistance));
        
        if (!latLngList.isEmpty()) {
            LocationRecord firstRecord = allLocationRecords.get(0);
            SimpleDateFormat timeSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            trackPointTime.setText(timeSdf.format(new Date(firstRecord.getTimestamp())));
            
            getAddressForLocation(latLngList.get(0));
        }
        
        // 隐藏加载进度条
        hideLoading();
    }

    /**
     * 完整同步轨迹数据（首次加载或强制刷新时使用）
     */
    private void syncTrackDataFromServerAndReload(final String deviceNum, final long startTime, final long endTime) {
        Log.d(TAG, "=== SYNC TRACK DATA START ===");
        Log.d(TAG, "DeviceNum: " + deviceNum);
        Log.d(TAG, "SelectedDevice ID: " + (selectedDevice != null ? selectedDevice.getDeviceId() : "null"));
        Log.d(TAG, "Time range: " + startTime + " - " + endTime);
        
        // 根据设备号长度设置对应的API URL
        NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(deviceNum));
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    NewApiService apiService = NewApiService.getInstance();
                    
                    Log.d(TAG, "Calling getLocations with deviceNum=" + deviceNum + ", startTime=" + startTime + ", endTime=" + endTime);
                    List<NewApiService.LocationInfo> locations = apiService.getLocations(deviceNum, startTime, endTime);
                    Log.d(TAG, "Got " + (locations != null ? locations.size() : 0) + " locations from server");
                    
                    if (locations != null && !locations.isEmpty()) {
                        int addedCount = 0;
                        int skippedCount = 0;
                        int invalidCount = 0;
                        
                        for (NewApiService.LocationInfo loc : locations) {
                            Log.d(TAG, "Processing location: lat=" + loc.latitude + ", lng=" + loc.longitude + ", ts=" + loc.timestamp);
                            
                            if (loc.latitude != 0 && loc.longitude != 0 && loc.timestamp > 0) {
                                LocationRecord record = new LocationRecord(
                                    selectedDevice.getDeviceId(),
                                    loc.latitude,
                                    loc.longitude,
                                    loc.timestamp
                                );
                                
                                List<LocationRecord> existingRecords = databaseHelper.getLocationRecords(
                                    selectedDevice.getDeviceId(),
                                    loc.timestamp - 1000,
                                    loc.timestamp + 1000
                                );
                                
                                if (existingRecords == null || existingRecords.isEmpty()) {
                                    databaseHelper.addLocationRecord(record);
                                    addedCount++;
                                    Log.d(TAG, "Added location record: " + loc.latitude + ", " + loc.longitude + " at " + loc.timestamp);
                                } else {
                                    skippedCount++;
                                    Log.d(TAG, "Skipped duplicate record at timestamp " + loc.timestamp);
                                }
                            } else {
                                invalidCount++;
                                Log.d(TAG, "Skipped invalid record: lat=" + loc.latitude + ", lng=" + loc.longitude + ", ts=" + loc.timestamp);
                            }
                        }
                        
                        final int finalAddedCount = addedCount;
                        final int finalSkippedCount = skippedCount;
                        final int finalInvalidCount = invalidCount;
                        final int totalFromServer = locations.size();
                        
                        Log.d(TAG, "Sync summary: added=" + finalAddedCount + ", skipped=" + finalSkippedCount + ", invalid=" + finalInvalidCount + ", total=" + totalFromServer);
                        
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "Track data sync completed, added " + finalAddedCount + " records, total from server: " + totalFromServer);
                                if (totalFromServer > 0) {
                                    // 重新从数据库加载并渲染
                                    List<LocationRecord> newRecords = databaseHelper.getLocationRecords(
                                        selectedDevice.getDeviceId(),
                                        startTime,
                                        endTime
                                    );
                                    renderTrack(newRecords);
                                } else {
                                    Toast.makeText(TrackActivity.this, R.string.no_track_data, Toast.LENGTH_SHORT).show();
                                    updatePlaybackInfo(0);
                                }
                            }
                        });
                    } else {
                        Log.d(TAG, "No locations returned from server");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(TrackActivity.this, R.string.no_track_data, Toast.LENGTH_SHORT).show();
                                updatePlaybackInfo(0);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error syncing track data: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(TrackActivity.this, R.string.sync_track_error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private double calculateTotalDistance(List<LatLng> latLngList) {
        double totalDistance = 0;
        for (int i = 0; i < latLngList.size() - 1; i++) {
            LatLng p1 = latLngList.get(i);
            LatLng p2 = latLngList.get(i + 1);
            totalDistance += CoordinateUtils.calculateDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
        }
        return totalDistance / 1000;
    }

    private void getAddressForLocation(LatLng latLng) {
        LatLonPoint latLonPoint = new LatLonPoint(latLng.latitude, latLng.longitude);
        RegeocodeQuery query = new RegeocodeQuery(latLonPoint, 200, GeocodeSearch.AMAP);
        geocodeSearch.getFromLocationAsyn(query);
    }

    @Override
    public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int rCode) {
        if (rCode == 1000) {
            if (regeocodeResult != null && regeocodeResult.getRegeocodeAddress() != null) {
                RegeocodeAddress address = regeocodeResult.getRegeocodeAddress();
                String addressStr = address.getFormatAddress();
                if (addressStr != null && !addressStr.isEmpty()) {
                    trackPointAddress.setText(addressStr);
                } else {
                    trackPointAddress.setText(getString(R.string.unknown_location));
                }
            }
        } else {
            trackPointAddress.setText(getString(R.string.get_address_failed));
        }
    }

    @Override
    public void onGeocodeSearched(GeocodeResult geocodeResult, int rCode) {
    }

    private void updatePlaybackInfo(int count) {
        playbackSeekbar.setMax(Math.max(0, count - 1));
        
        if (count > 0 && !allLocationRecords.isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            String timeStr = sdf.format(new Date(allLocationRecords.get(count - 1).getTimestamp()));
            totalTimeText.setText(timeStr.substring(timeStr.indexOf(" ") + 1));
        } else {
            totalTimeText.setText("00:00:00");
        }
        currentTimeText.setText("00:00:00");
        playbackSeekbar.setProgress(0);
    }
    
    /**
     * 增强的异常点过滤器
     * 过滤规则：
     * 1. GPS 漂移检测（短时间大距离跳变）
     * 2. 速度异常检测（超过最大速度）
     * 3. 时间倒流检测（设备时钟异常）
     * 4. 坐标无效检测（0,0 或其他异常值）
     */
    private List<LocationRecord> filterAbnormalPoints(List<LocationRecord> records) {
        if (records == null || records.isEmpty()) {
            return records;
        }
        
        List<LocationRecord> filtered = new ArrayList<>();
        filtered.add(records.get(0));
        
        int filteredOut = 0;
        int gpsSpikeCount = 0;      // GPS 漂移计数
        int speedAnomalyCount = 0;  // 速度异常计数
        int timeReversalCount = 0;  // 时间倒流计数
        int invalidCoordCount = 0;  // 无效坐标计数
        
        for (int i = 1; i < records.size(); i++) {
            LocationRecord prev = filtered.get(filtered.size() - 1);  // 使用已过滤的最后一个点
            LocationRecord curr = records.get(i);
            
            double distance = CoordinateUtils.calculateDistanceMeters(
                prev.getLatitude(), prev.getLongitude(),
                curr.getLatitude(), curr.getLongitude()
            );
            
            double timeDiffSeconds = (curr.getTimestamp() - prev.getTimestamp()) / 1000.0;
            double timeDiffHours = timeDiffSeconds / 3600.0;
            
            // 检查 1: GPS 漂移检测（短时间大距离跳变）
            // 如果在 60 秒内移动超过 2000 米（约 120 km/h），很可能是 GPS 漂移
            // 这个阈值考虑了高速公路场景（120 km/h = 2000m/min）
            if (timeDiffSeconds > 0 && timeDiffSeconds < 60 && distance > 2000) {
                gpsSpikeCount++;
                Log.d(TAG, String.format("Filtered GPS spike at index %d: distance=%.0fm in %.0fs", 
                    i, distance, timeDiffSeconds));
                continue;
            }
            
            // 检查 2: 速度异常
            if (timeDiffHours > 0) {
                double speedKmh = (distance / 1000.0) / timeDiffHours;
                
                if (speedKmh > MAX_SPEED_KMH) {
                    speedAnomalyCount++;
                    Log.d(TAG, String.format("Filtered speed anomaly at index %d: speed=%.1f km/h, distance=%.0fm", 
                        i, speedKmh, distance));
                    continue;
                }
                
                // 警告：步行/骑行速度异常（但不过滤）
                if (speedKmh > 50 && speedKmh <= MAX_SPEED_KMH) {
                    Log.w(TAG, String.format("Warning: High speed at index %d: %.1f km/h", i, speedKmh));
                }
            }
            
            // 检查 3: 时间倒流（设备时钟异常）
            if (curr.getTimestamp() < prev.getTimestamp()) {
                timeReversalCount++;
                Log.w(TAG, String.format("Filtered time reversal at index %d: prev=%d, curr=%d", 
                    i, prev.getTimestamp(), curr.getTimestamp()));
                continue;
            }
            
            // 检查 4: 坐标无效（0,0 或其他异常值）
            if (Math.abs(curr.getLatitude()) < 0.0001 && Math.abs(curr.getLongitude()) < 0.0001) {
                invalidCoordCount++;
                Log.w(TAG, String.format("Filtered invalid coordinates at index %d: lat=%.6f, lng=%.6f", 
                    i, curr.getLatitude(), curr.getLongitude()));
                continue;
            }
            
            filtered.add(curr);
        }
        
        Log.d(TAG, String.format("Filter results: total=%d, kept=%d, filtered=%d (gpsSpikes=%d, speedAnomalies=%d, timeReversals=%d, invalidCoords=%d)",
            records.size(), filtered.size(), filteredOut + gpsSpikeCount + speedAnomalyCount + timeReversalCount + invalidCoordCount,
            gpsSpikeCount, speedAnomalyCount, timeReversalCount, invalidCoordCount));
        
        return filtered;
    }

    private void clearTrack() {
        // 1. 停止播放动画
        stopPlayback();
        
        // 2. 清理 Marker（防止内存泄漏）
        for (Marker marker : positionMarkers) {
            marker.remove();
        }
        positionMarkers.clear();
        
        for (Marker marker : arrowMarkers) {
            marker.remove();
        }
        arrowMarkers.clear();

        // 3. 清理轨迹线
        if (trackPolyline != null) {
            trackPolyline.remove();
            trackPolyline = null;
        }
        
        // 4. 清理播放相关对象
        if (playedPolyline != null) {
            playedPolyline.remove();
            playedPolyline = null;
        }
        if (playMarker != null) {
            playMarker.remove();
            playMarker = null;
        }
        playedPoints.clear();
        currentPlayPosition = null;
        
        // 5. 停止所有动画
        if (moveAnimator != null && moveAnimator.isRunning()) {
            moveAnimator.cancel();
            moveAnimator = null;
        }
        
        // 6. 清理轨迹数据（释放内存）
        allLocationRecords.clear();
        stayPoints.clear();
        
        Log.d(TAG, "Track cleared, memory released");
    }
    
    private List<StayPoint> processStayPoints(List<LocationRecord> records) {
        List<StayPoint> result = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return result;
        }
        
        int i = 0;
        int pointIndex = 1;
        
        while (i < records.size()) {
            LocationRecord currentRecord = records.get(i);
            double centerLat = currentRecord.getLatitude();
            double centerLng = currentRecord.getLongitude();
            long arriveTime = currentRecord.getTimestamp();
            long leaveTime = currentRecord.getTimestamp();
            
            List<LocationRecord> mergedRecords = new ArrayList<>();
            mergedRecords.add(currentRecord);
            
            int j = i + 1;
            while (j < records.size()) {
                LocationRecord nextRecord = records.get(j);
                double distance = CoordinateUtils.calculateDistanceMeters(
                    centerLat, centerLng,
                    nextRecord.getLatitude(), nextRecord.getLongitude()
                );
                
                if (distance <= STAY_DISTANCE_THRESHOLD) {
                    mergedRecords.add(nextRecord);
                    leaveTime = nextRecord.getTimestamp();
                    
                    double sumLat = 0, sumLng = 0;
                    for (LocationRecord r : mergedRecords) {
                        sumLat += r.getLatitude();
                        sumLng += r.getLongitude();
                    }
                    centerLat = sumLat / mergedRecords.size();
                    centerLng = sumLng / mergedRecords.size();
                    
                    j++;
                } else {
                    break;
                }
            }
            
            StayPoint stayPoint = new StayPoint(centerLat, centerLng, arriveTime, leaveTime);
            stayPoint.setOriginalIndex(pointIndex);
            for (LocationRecord r : mergedRecords) {
                stayPoint.addMergedRecord(r);
            }
            result.add(stayPoint);
            
            Log.d(TAG, "StayPoint " + pointIndex + ": merged " + mergedRecords.size() + 
                  " records, stay=" + stayPoint.getStayDurationFormatted() + 
                  ", isStay=" + stayPoint.isStayPoint());
            
            i = j;
            pointIndex++;
        }
        
        return result;
    }
    
    private void addDirectionArrows(List<LatLng> latLngList) {
        if (latLngList.size() < 2) {
            return;
        }
        
        // 优化：每隔 N 个点显示一个箭头，避免过于密集
        int arrowInterval = 5; // 每 5 个点显示一个箭头
        
        for (int i = 0; i < latLngList.size() - 1; i += arrowInterval) {
            LatLng p1 = latLngList.get(i);
            LatLng p2 = latLngList.get(Math.min(i + 1, latLngList.size() - 1));
            
            double angle = calculateBearing(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
            
            MarkerOptions arrowMarker = new MarkerOptions()
                .position(new LatLng(p1.latitude, p1.longitude))
                .icon(createArrowMarker(angle))
                .anchor(0.5f, 0.5f)
                .zIndex(1);
            
            Marker marker = aMap.addMarker(arrowMarker);
            marker.setVisible(showMarkers);
            arrowMarkers.add(marker);
        }
        
        Log.d(TAG, "Added direction arrows (interval: " + arrowInterval + ")");
    }
    
    private double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        // 计算两点之间的方位角（从北向顺时针）
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLngRad = Math.toRadians(lng2 - lng1);
        
        double y = Math.sin(deltaLngRad) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - 
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLngRad);
        
        double bearing = Math.toDegrees(Math.atan2(y, x));
        
        // 转换为0-360度
        return (bearing + 360) % 360;
    }
    
    /**
     * 显示加载进度条
     */
    private void showLoading() {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 隐藏加载进度条
     */
    private void hideLoading() {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.GONE);
        }
    }
    
    /**
     * 显示轨迹统计信息
     */
    private void showTrackStatistics() {
        if (allLocationRecords.isEmpty()) {
            Toast.makeText(this, R.string.no_track_data, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 计算统计数据
        int totalPoints = allLocationRecords.size();
        
        // 计算总距离
        double totalDistance = 0;
        for (int i = 1; i < allLocationRecords.size(); i++) {
            LocationRecord prev = allLocationRecords.get(i - 1);
            LocationRecord curr = allLocationRecords.get(i);
            totalDistance += CoordinateUtils.calculateDistanceMeters(
                prev.getLatitude(), prev.getLongitude(),
                curr.getLatitude(), curr.getLongitude()
            );
        }
        
        // 计算时间跨度
        long startTime = allLocationRecords.get(0).getTimestamp();
        long endTime = allLocationRecords.get(allLocationRecords.size() - 1).getTimestamp();
        long durationMs = endTime - startTime;
        
        // 格式化时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
        String startTimeStr = sdf.format(new Date(startTime));
        String endTimeStr = sdf.format(new Date(endTime));
        
        long hours = durationMs / (1000 * 60 * 60);
        long minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60);
        String durationStr = String.format("%d 小时 %d 分钟", hours, minutes);
        
        // 计算停留点数量
        int stayPointCount = 0;
        for (StayPoint sp : stayPoints) {
            if (sp.isStayPoint()) {
                stayPointCount++;
            }
        }
        
        // 计算平均速度
        double avgSpeed = 0;
        if (durationMs > 0) {
            avgSpeed = (totalDistance / 1000.0) / (durationMs / (1000.0 * 60 * 60)); // km/h
        }
        
        // 构建统计信息
        StringBuilder stats = new StringBuilder();
        stats.append("📊 轨迹统计信息\n\n");
        stats.append("📍 轨迹点数：").append(totalPoints).append(" 个\n");
        stats.append("📏 总距离：").append(String.format("%.2f km", totalDistance / 1000.0)).append("\n");
        stats.append("⏱️ 时间跨度：").append(durationStr).append("\n");
        stats.append("🕐 开始时间：").append(startTimeStr).append("\n");
        stats.append("🕐 结束时间：").append(endTimeStr).append("\n");
        stats.append("🚶 停留点：").append(stayPointCount).append(" 个\n");
        stats.append("⚡ 平均速度：").append(String.format("%.2f km/h", avgSpeed)).append("\n");
        
        // 显示对话框
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("轨迹统计")
            .setMessage(stats.toString())
            .setPositiveButton("确定", null)
            .show();
    }
    
    private com.amap.api.maps.model.BitmapDescriptor createArrowMarker(double angle) {
        return createDefaultArrowMarker(angle);
    }
    
    private com.amap.api.maps.model.BitmapDescriptor createDefaultArrowMarker(double angle) {
        int size = 29;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        Paint borderPaint = new Paint();
        borderPaint.setColor(0xFFFFFFFF);
        borderPaint.setStyle(Paint.Style.FILL);
        borderPaint.setAntiAlias(true);
        
        Paint fillPaint = new Paint();
        fillPaint.setColor(0xFFFF5722);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);
        
        canvas.save();
        
        canvas.translate(size / 2f, size / 2f);
        canvas.rotate((float) angle);
        
        float halfWidth = size / 4f;
        float height = size / 3f;
        
        android.graphics.Path borderPath = new android.graphics.Path();
        borderPath.moveTo(0, -height - 1);
        borderPath.lineTo(-halfWidth - 1, height + 1);
        borderPath.lineTo(halfWidth + 1, height + 1);
        borderPath.close();
        canvas.drawPath(borderPath, borderPaint);
        
        android.graphics.Path arrowPath = new android.graphics.Path();
        arrowPath.moveTo(0, -height);
        arrowPath.lineTo(-halfWidth, height);
        arrowPath.lineTo(halfWidth, height);
        arrowPath.close();
        canvas.drawPath(arrowPath, fillPaint);
        
        canvas.restore();
        
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }
    
    private void checkAndCleanOldTrackData() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean hasCleanedTrackForNewVersion = prefs.getBoolean("has_cleaned_track_new_v2", false);
        
        if (!hasCleanedTrackForNewVersion) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.clean_old_track_data))
                .setMessage(getString(R.string.clean_old_track_message))
                .setPositiveButton(getString(R.string.clean), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int deletedCount = databaseHelper.deleteAllLocationRecords();
                        android.content.SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("has_cleaned_track_new_v2", true);
                        editor.apply();
                        
                        if (selectedDevice != null && 
                            selectedDevice.getLatitude() != 0 && 
                            selectedDevice.getLongitude() != 0) {
                            
                            long deviceTimestamp = selectedDevice.getLastSeen();
                            LocationRecord firstRecord = new LocationRecord(
                                selectedDevice.getDeviceId(),
                                selectedDevice.getLatitude(),
                                selectedDevice.getLongitude(),
                                deviceTimestamp
                            );
                            databaseHelper.addLocationRecord(firstRecord);
                            Log.d(TAG, "Added initial track point after cleanup: lat=" + 
                                  selectedDevice.getLatitude() + ", lng=" + selectedDevice.getLongitude() +
                                  ", timestamp=" + deviceTimestamp);
                            
                            Toast.makeText(TrackActivity.this, 
                                getString(R.string.cleaned_track_with_current, deletedCount), 
                                Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(TrackActivity.this, 
                                getString(R.string.cleaned_track_records, deletedCount), 
                                Toast.LENGTH_SHORT).show();
                        }
                        
                        loadTrackData();
                    }
                })
                .setNegativeButton(getString(R.string.skip), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        android.content.SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("has_cleaned_track_new_v2", true);
                        editor.apply();
                    }
                })
                .show();
        }
    }
    
    private com.amap.api.maps.model.BitmapDescriptor createStartEndMarker(String text, int color) {
        try {
            int width = 80;
            int height = 100;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            
            android.graphics.Path path = new android.graphics.Path();
            float centerX = width / 2f;
            float circleRadius = 32f;
            
            path.moveTo(centerX, height - 5);
            path.cubicTo(
                centerX - 25, height - 40,
                centerX - circleRadius, circleRadius + 15,
                centerX - circleRadius, circleRadius
            );
            path.arcTo(
                new android.graphics.RectF(centerX - circleRadius, 5, centerX + circleRadius, 5 + circleRadius * 2),
                180, 180
            );
            path.cubicTo(
                centerX + circleRadius, circleRadius + 15,
                centerX + 25, height - 40,
                centerX, height - 5
            );
            path.close();
            
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
            
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            canvas.drawPath(path, paint);
            
            paint.setColor(Color.WHITE);
            paint.setTextSize(28);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStyle(Paint.Style.FILL);
            
            float textX = centerX;
            float textY = circleRadius + 5 - (paint.descent() + paint.ascent()) / 2f;
            canvas.drawText(text, textX, textY, paint);
            
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating start/end marker: " + e.getMessage());
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
        }
    }
    
    private com.amap.api.maps.model.BitmapDescriptor createNumberedMarker(int number) {
        try {
            int size = 80;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            Paint circlePaint = new Paint();
            circlePaint.setColor(Color.RED);
            circlePaint.setAntiAlias(true);
            circlePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, circlePaint);
            
            Paint strokePaint = new Paint();
            strokePaint.setColor(Color.WHITE);
            strokePaint.setAntiAlias(true);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(3);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, strokePaint);
            
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(32);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setAntiAlias(true);
            
            String text = String.valueOf(number);
            float x = size / 2f;
            float y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(text, x, y, textPaint);
            
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating numbered marker: " + e.getMessage());
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
        }
    }
    
    private com.amap.api.maps.model.BitmapDescriptor createStayPointMarker(int number, String duration) {
        try {
            int size = 80;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            Paint circlePaint = new Paint();
            circlePaint.setColor(Color.parseColor("#FF9800"));
            circlePaint.setAntiAlias(true);
            circlePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, circlePaint);
            
            Paint strokePaint = new Paint();
            strokePaint.setColor(Color.WHITE);
            strokePaint.setAntiAlias(true);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(3);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, strokePaint);
            
            Paint numberPaint = new Paint();
            numberPaint.setColor(Color.WHITE);
            numberPaint.setTextSize(26);
            numberPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            numberPaint.setTextAlign(Paint.Align.CENTER);
            numberPaint.setAntiAlias(true);
            
            String numberText = String.valueOf(number);
            float x = size / 2f;
            float y = size / 2f - 8 - (numberPaint.descent() + numberPaint.ascent()) / 2f;
            canvas.drawText(numberText, x, y, numberPaint);
            
            Paint durationPaint = new Paint();
            durationPaint.setColor(Color.WHITE);
            durationPaint.setTextSize(14);
            durationPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            durationPaint.setTextAlign(Paint.Align.CENTER);
            durationPaint.setAntiAlias(true);
            
            float durationY = size / 2f + 12 - (durationPaint.descent() + durationPaint.ascent()) / 2f;
            canvas.drawText(duration, x, durationY, durationPaint);
            
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating stay point marker: " + e.getMessage());
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        
        // 每次恢复时重新从数据库读取设备信息，确保获取最新的位置和时间戳
        refreshSelectedDevice();
    }
    
    private void refreshSelectedDevice() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String selectedDeviceId = prefs.getString("selected_device_id", "");
        if (!selectedDeviceId.isEmpty()) {
            Device device = databaseHelper.getDevice(selectedDeviceId);
            if (device != null) {
                selectedDevice = device;
                Log.d(TAG, "Refreshed device: lat=" + device.getLatitude() + 
                      ", lng=" + device.getLongitude() + 
                      ", lastSeen=" + device.getLastSeen());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
