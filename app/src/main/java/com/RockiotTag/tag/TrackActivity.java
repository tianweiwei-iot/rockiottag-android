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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
    private static final double STAY_DISTANCE_THRESHOLD = 20.0;
    private static final double MAX_SPEED_KMH = 200.0;
    private boolean isPlaying = false;
    private int currentPlayIndex = 0;
    private int playSpeed = 1;
    private Handler playHandler = new Handler();
    private Marker playMarker = null;
    private Polyline playedPolyline = null;
    private List<LatLng> playedPoints = new ArrayList<>();
    private ValueAnimator moveAnimator = null;
    private LatLng currentPlayPosition = null;
    
    private ImageButton toggleMarkersBtn;
    private ImageButton togglePolylineBtn;
    private ImageButton toggleSatelliteBtn;
    private boolean showMarkers = true;
    private boolean showPolyline = true;
    private boolean isSatelliteMode = false;
    
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

        initPlaybackControls();
        
        initToolbar();

        checkAndCleanOldTrackData();
        
        loadTrackData();
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
        
        long duration = 1000 / playSpeed;
        
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
                
                aMap.animateCamera(com.amap.api.maps.CameraUpdateFactory.newLatLng(newPos));
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
                    
                    getAddressForLocation(finalToPos);
                    
                    playHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            moveToNextPoint();
                        }
                    }, 50);
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
        if (playedPolyline != null) {
            playedPolyline.remove();
        }
        if (playedPoints.size() > 1) {
            PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(playedPoints)
                .color(0xFFFF5722)
                .width(12f);
            playedPolyline = aMap.addPolyline(polylineOptions);
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
            
            getAddressForLocation(latLng);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
        String timeStr = sdf.format(new Date(allLocationRecords.get(currentPlayIndex).getTimestamp()));
        currentTimeText.setText(timeStr.substring(timeStr.indexOf(" ") + 1));
        trackPointTime.setText(timeStr);
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
        int size = 80;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        paint.setColor(Color.parseColor("#FF5722"));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, paint);
        
        paint.setColor(Color.WHITE);
        paint.setTextSize(30);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        canvas.drawText("▶", size / 2f, size / 2f + 10, paint);
        
        return BitmapDescriptorFactory.fromBitmap(bitmap);
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
        loadTrackData(true);
    }

    private void loadTrackData(final boolean syncFromServer) {
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
        Log.d(TAG, "syncFromServer: " + syncFromServer);

        if (syncFromServer) {
            Log.d(TAG, "Syncing from server first...");
            Toast.makeText(this, R.string.loading_track_data, Toast.LENGTH_SHORT).show();
            syncTrackDataFromServerAndReload(
                selectedDevice.getDeviceNum() != null ? selectedDevice.getDeviceNum() : selectedDevice.getDeviceId(),
                startTime.getTimeInMillis(),
                endTime.getTimeInMillis()
            );
            return;
        }

        List<LocationRecord> locationRecords = databaseHelper.getLocationRecords(
            selectedDevice.getDeviceId(),
            startTime.getTimeInMillis(),
            endTime.getTimeInMillis()
        );

        if (locationRecords == null || locationRecords.isEmpty()) {
            Log.d(TAG, "NO RECORDS FOUND IN DATABASE!");
            Toast.makeText(this, R.string.no_track_data, Toast.LENGTH_SHORT).show();
            updatePlaybackInfo(0);
            return;
        }

        Log.d(TAG, "Total records from DB: " + locationRecords.size());
        
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

        for (int i = 0; i < stayPoints.size(); i++) {
            StayPoint stayPoint = stayPoints.get(i);
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
    }

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
                    
                    if (!apiService.isAuthenticated()) {
                        Log.d(TAG, "Not authenticated, logging in...");
                        NewApiService.ApiResponse loginResponse = apiService.login(
                            "6h7lMJOVpVOld5R9CApqH6coCR1W8iqL",
                            "XHD_HSWL_API",
                            "123456"
                        );
                        
                        if (loginResponse == null || !loginResponse.isSuccess()) {
                            Log.e(TAG, "Login failed, cannot sync track data");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(TrackActivity.this, R.string.sync_track_failed, Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }
                        Log.d(TAG, "Login successful");
                    }
                    
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
                                    loadTrackData(false);
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
    
    private List<LocationRecord> filterAbnormalPoints(List<LocationRecord> records) {
        if (records == null || records.isEmpty()) {
            return records;
        }
        
        List<LocationRecord> filtered = new ArrayList<>();
        filtered.add(records.get(0));
        
        int filteredOut = 0;
        for (int i = 1; i < records.size(); i++) {
            LocationRecord prev = records.get(i - 1);
            LocationRecord curr = records.get(i);
            
            double distance = CoordinateUtils.calculateDistanceMeters(
                prev.getLatitude(), prev.getLongitude(),
                curr.getLatitude(), curr.getLongitude()
            );
            
            double timeDiffHours = (curr.getTimestamp() - prev.getTimestamp()) / 3600000.0;
            
            if (timeDiffHours > 0) {
                double speedKmh = (distance / 1000.0) / timeDiffHours;
                
                if (speedKmh > MAX_SPEED_KMH) {
                    filteredOut++;
                    Log.d(TAG, "Filtered abnormal point " + i + ": speed=" + String.format("%.1f", speedKmh) + " km/h, distance=" + String.format("%.1f", distance) + "m");
                    continue;
                }
            }
            
            filtered.add(curr);
        }
        
        Log.d(TAG, "Filtered " + filteredOut + " abnormal points, kept " + filtered.size() + " / " + records.size());
        return filtered;
    }

    private void clearTrack() {
        stopPlayback();
        
        for (Marker marker : positionMarkers) {
            marker.remove();
        }
        positionMarkers.clear();
        
        for (Marker marker : arrowMarkers) {
            marker.remove();
        }
        arrowMarkers.clear();

        if (trackPolyline != null) {
            trackPolyline.remove();
            trackPolyline = null;
        }
        
        allLocationRecords.clear();
        stayPoints.clear();
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
        
        for (int i = 0; i < latLngList.size() - 1; i++) {
            LatLng p1 = latLngList.get(i);
            LatLng p2 = latLngList.get(i + 1);
            
            double segmentDistance = CoordinateUtils.calculateDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
            
            int arrowCount = Math.max(1, Math.min(5, (int) (segmentDistance / 300)));
            
            if (arrowCount == 1) {
                double arrowLat = (p1.latitude + p2.latitude) / 2;
                double arrowLng = (p1.longitude + p2.longitude) / 2;
                double angle = calculateBearing(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
                
                MarkerOptions arrowMarker = new MarkerOptions()
                    .position(new LatLng(arrowLat, arrowLng))
                    .icon(createArrowMarker(angle))
                    .anchor(0.5f, 0.5f)
                    .zIndex(1);
                
                Marker marker = aMap.addMarker(arrowMarker);
                marker.setVisible(showMarkers);
                arrowMarkers.add(marker);
            } else {
                double interval = segmentDistance / (arrowCount + 1);
                
                for (int j = 1; j <= arrowCount; j++) {
                    double ratio = (interval * j) / segmentDistance;
                    double arrowLat = p1.latitude + (p2.latitude - p1.latitude) * ratio;
                    double arrowLng = p1.longitude + (p2.longitude - p1.longitude) * ratio;
                    double angle = calculateBearing(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
                    
                    MarkerOptions arrowMarker = new MarkerOptions()
                        .position(new LatLng(arrowLat, arrowLng))
                        .icon(createArrowMarker(angle))
                        .anchor(0.5f, 0.5f)
                        .zIndex(1);
                    
                    Marker marker = aMap.addMarker(arrowMarker);
                    marker.setVisible(showMarkers);
                    arrowMarkers.add(marker);
                }
            }
        }
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
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
