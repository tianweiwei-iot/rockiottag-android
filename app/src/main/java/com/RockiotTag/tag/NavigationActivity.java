package com.RockiotTag.tag;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DrivePath;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.DriveStep;
import com.amap.api.services.route.RidePath;
import com.amap.api.services.route.RideRouteResult;
import com.amap.api.services.route.RideStep;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkPath;
import com.amap.api.services.route.WalkRouteResult;
import com.amap.api.services.route.WalkStep;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 导航Activity - 使用搜索SDK路线规划 + 地图显示导航路线
 * 支持驾车/步行/骑行路线规划和导航指引
 */
public class NavigationActivity extends AppCompatActivity
        implements RouteSearch.OnRouteSearchListener {

    private static final String TAG = "NavigationActivity";

    private static final int NAVI_MODE_DRIVE = 0;
    private static final int NAVI_MODE_WALK = 1;
    private static final int NAVI_MODE_RIDE = 2;

    private MapView mMapView;
    private AMap mAMap;

    private ImageButton btnBack;
    private TextView naviTitle;
    private LinearLayout naviModeBar;
    private Button btnDrive;
    private Button btnWalk;
    private Button btnRide;
    private ProgressBar progressBar;

    // 路线信息面板
    private LinearLayout routeInfoPanel;
    private TextView tvRouteDistance;
    private TextView tvRouteDuration;
    private TextView tvRouteSteps;
    private Button btnStartNavi;
    private RecyclerView rvSteps;

    // 导航状态面板
    private LinearLayout naviStatusPanel;
    private TextView tvNaviInstruction;
    private TextView tvNaviDistance;
    private Button btnNextStep;
    private Button btnStopNavi;

    private double destLatitude;
    private double destLongitude;
    private double startLatitude;
    private double startLongitude;
    private int currentNaviMode = NAVI_MODE_DRIVE;

    private RouteSearch routeSearch;
    private Marker startMarker;
    private Marker endMarker;
    private List<NavigationStep> navigationSteps = new ArrayList<>();
    private int currentStepIndex = 0;
    private boolean isNavigating = false;

    private DecimalFormat distanceFormat = new DecimalFormat("#.#");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        Intent intent = getIntent();
        destLatitude = intent.getDoubleExtra("dest_latitude", 0);
        destLongitude = intent.getDoubleExtra("dest_longitude", 0);
        startLatitude = intent.getDoubleExtra("start_latitude", 0);
        startLongitude = intent.getDoubleExtra("start_longitude", 0);

        if (destLatitude == 0 || destLongitude == 0) {
            Toast.makeText(this, R.string.navi_no_destination, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // WGS84转GCJ02
        if (startLatitude != 0 && startLongitude != 0) {
            LatLng startGcj = CoordinateUtils.wgs84ToGcj02(startLatitude, startLongitude);
            startLatitude = startGcj.latitude;
            startLongitude = startGcj.longitude;
        }
        LatLng destGcj = CoordinateUtils.wgs84ToGcj02(destLatitude, destLongitude);
        destLatitude = destGcj.latitude;
        destLongitude = destGcj.longitude;

        Log.d(TAG, "Start (GCJ02): " + startLatitude + ", " + startLongitude);
        Log.d(TAG, "Dest (GCJ02): " + destLatitude + ", " + destLongitude);

        initViews(savedInstanceState);
        initMap();
        initRouteSearch();
    }

    private void initViews(Bundle savedInstanceState) {
        mMapView = findViewById(R.id.navi_map);
        mMapView.onCreate(savedInstanceState);

        btnBack = findViewById(R.id.btn_back);
        naviTitle = findViewById(R.id.navi_title);
        naviModeBar = findViewById(R.id.navi_mode_bar);
        btnDrive = findViewById(R.id.btn_drive);
        btnWalk = findViewById(R.id.btn_walk);
        btnRide = findViewById(R.id.btn_ride);
        progressBar = findViewById(R.id.progress_bar);

        routeInfoPanel = findViewById(R.id.route_info_panel);
        tvRouteDistance = findViewById(R.id.tv_route_distance);
        tvRouteDuration = findViewById(R.id.tv_route_duration);
        tvRouteSteps = findViewById(R.id.tv_route_steps);
        btnStartNavi = findViewById(R.id.btn_start_navi);
        rvSteps = findViewById(R.id.rv_steps);

        naviStatusPanel = findViewById(R.id.navi_status_panel);
        tvNaviInstruction = findViewById(R.id.tv_navi_instruction);
        tvNaviDistance = findViewById(R.id.tv_navi_distance);
        btnNextStep = findViewById(R.id.btn_next_step);
        btnStopNavi = findViewById(R.id.btn_stop_navi);

        btnBack.setOnClickListener(v -> {
            if (isNavigating) stopNavigation();
            finish();
        });

        btnDrive.setOnClickListener(v -> {
            currentNaviMode = NAVI_MODE_DRIVE;
            updateModeButtons();
            searchRoute();
        });

        btnWalk.setOnClickListener(v -> {
            currentNaviMode = NAVI_MODE_WALK;
            updateModeButtons();
            searchRoute();
        });

        btnRide.setOnClickListener(v -> {
            currentNaviMode = NAVI_MODE_RIDE;
            updateModeButtons();
            searchRoute();
        });

        btnStartNavi.setOnClickListener(v -> startNavigation());

        btnNextStep.setOnClickListener(v -> showNextStep());

        btnStopNavi.setOnClickListener(v -> stopNavigation());

        updateModeButtons();
    }

    private void initMap() {
        mAMap = mMapView.getMap();
        mAMap.getUiSettings().setZoomControlsEnabled(false);
        mAMap.getUiSettings().setCompassEnabled(true);
        mAMap.getUiSettings().setScaleControlsEnabled(true);

        // 添加终点标记
        LatLng destLatLng = new LatLng(destLatitude, destLongitude);
        endMarker = mAMap.addMarker(new MarkerOptions()
                .position(destLatLng)
                .title(getString(R.string.end_point))
                .snippet(getString(R.string.device_location))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // 添加起点标记
        if (startLatitude != 0 && startLongitude != 0) {
            LatLng startLatLng = new LatLng(startLatitude, startLongitude);
            startMarker = mAMap.addMarker(new MarkerOptions()
                    .position(startLatLng)
                    .title(getString(R.string.start_point))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        }

        // 移动相机到包含起终点的范围
        mAMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destLatLng, 14));
    }

    private void initRouteSearch() {
        try {
            routeSearch = new RouteSearch(this);
            routeSearch.setRouteSearchListener(this);
        } catch (Exception e) {
            Log.e(TAG, "RouteSearch init error: " + e.getMessage(), e);
            Toast.makeText(this, R.string.navi_init_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateModeButtons() {
        btnDrive.setAlpha(currentNaviMode == NAVI_MODE_DRIVE ? 1.0f : 0.5f);
        btnWalk.setAlpha(currentNaviMode == NAVI_MODE_WALK ? 1.0f : 0.5f);
        btnRide.setAlpha(currentNaviMode == NAVI_MODE_RIDE ? 1.0f : 0.5f);
    }

    private void searchRoute() {
        if (routeSearch == null) return;

        progressBar.setVisibility(View.VISIBLE);
        routeInfoPanel.setVisibility(View.GONE);
        naviStatusPanel.setVisibility(View.GONE);
        naviTitle.setText(R.string.navi_calculating_drive);

        LatLonPoint startPoint = (startLatitude != 0 && startLongitude != 0)
                ? new LatLonPoint(startLatitude, startLongitude) : null;
        LatLonPoint endPoint = new LatLonPoint(destLatitude, destLongitude);

        try {
            switch (currentNaviMode) {
                case NAVI_MODE_DRIVE:
                    RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(startPoint, endPoint);
                    RouteSearch.DriveRouteQuery query = new RouteSearch.DriveRouteQuery(
                            fromAndTo, RouteSearch.DRIVING_SINGLE_DEFAULT, null, null, "");
                    routeSearch.calculateDriveRouteAsyn(query);
                    naviTitle.setText(R.string.navi_calculating_drive);
                    break;

                case NAVI_MODE_WALK:
                    RouteSearch.FromAndTo walkFromAndTo = new RouteSearch.FromAndTo(startPoint, endPoint);
                    RouteSearch.WalkRouteQuery walkQuery = new RouteSearch.WalkRouteQuery(
                            walkFromAndTo, RouteSearch.WALK_DEFAULT);
                    routeSearch.calculateWalkRouteAsyn(walkQuery);
                    naviTitle.setText(R.string.navi_calculating_walk);
                    break;

                case NAVI_MODE_RIDE:
                    RouteSearch.FromAndTo rideFromAndTo = new RouteSearch.FromAndTo(startPoint, endPoint);
                    RouteSearch.RideRouteQuery rideQuery = new RouteSearch.RideRouteQuery(
                            rideFromAndTo);
                    routeSearch.calculateRideRouteAsyn(rideQuery);
                    naviTitle.setText(R.string.navi_calculating_ride);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Route search error: " + e.getMessage(), e);
            progressBar.setVisibility(View.GONE);
            naviTitle.setText(R.string.navi_route_failed);
            Toast.makeText(this, R.string.navi_route_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ===== 路线搜索回调 =====

    @Override
    public void onDriveRouteSearched(DriveRouteResult result, int errorCode) {
        progressBar.setVisibility(View.GONE);
        if (errorCode != 1000 || result == null || result.getPaths() == null || result.getPaths().isEmpty()) {
            naviTitle.setText(R.string.navi_route_failed);
            Toast.makeText(this, R.string.navi_route_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        DrivePath drivePath = result.getPaths().get(0);
        navigationSteps.clear();

        // 绘制驾车路线
        mAMap.clear();
        addMarkers();

        List<LatLng> pathPoints = new ArrayList<>();
        for (DriveStep step : drivePath.getSteps()) {
            if (step.getPolyline() != null) {
                for (LatLonPoint point : step.getPolyline()) {
                    pathPoints.add(new LatLng(point.getLatitude(), point.getLongitude()));
                }
            }
            NavigationStep navStep = new NavigationStep();
            navStep.instruction = step.getInstruction();
            navStep.distance = step.getDistance();
            navStep.duration = step.getDuration();
            navStep.roadName = step.getRoad();
            navStep.action = step.getAction();
            navigationSteps.add(navStep);
        }

        drawRoute(pathPoints);

        // 显示路线信息
        float distanceKm = drivePath.getDistance() / 1000f;
        long durationMin = drivePath.getDuration() / 60;
        showRouteInfo(distanceKm, durationMin, drivePath.getSteps().size());
    }

    @Override
    public void onBusRouteSearched(BusRouteResult busRouteResult, int i) {
    }

    @Override
    public void onWalkRouteSearched(WalkRouteResult result, int errorCode) {
        progressBar.setVisibility(View.GONE);
        if (errorCode != 1000 || result == null || result.getPaths() == null || result.getPaths().isEmpty()) {
            naviTitle.setText(R.string.navi_route_failed);
            Toast.makeText(this, R.string.navi_route_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        WalkPath walkPath = result.getPaths().get(0);
        navigationSteps.clear();

        mAMap.clear();
        addMarkers();

        List<LatLng> pathPoints = new ArrayList<>();
        for (WalkStep step : walkPath.getSteps()) {
            if (step.getPolyline() != null) {
                for (LatLonPoint point : step.getPolyline()) {
                    pathPoints.add(new LatLng(point.getLatitude(), point.getLongitude()));
                }
            }
            NavigationStep navStep = new NavigationStep();
            navStep.instruction = step.getInstruction();
            navStep.distance = step.getDistance();
            navStep.duration = step.getDuration();
            navStep.roadName = step.getRoad();
            navStep.action = step.getAction();
            navigationSteps.add(navStep);
        }

        drawRoute(pathPoints);

        float distanceKm = walkPath.getDistance() / 1000f;
        long durationMin = walkPath.getDuration() / 60;
        showRouteInfo(distanceKm, durationMin, walkPath.getSteps().size());
    }

    @Override
    public void onRideRouteSearched(RideRouteResult result, int errorCode) {
        progressBar.setVisibility(View.GONE);
        if (errorCode != 1000 || result == null || result.getPaths() == null || result.getPaths().isEmpty()) {
            naviTitle.setText(R.string.navi_route_failed);
            Toast.makeText(this, R.string.navi_route_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        RidePath ridePath = result.getPaths().get(0);
        navigationSteps.clear();

        mAMap.clear();
        addMarkers();

        List<LatLng> pathPoints = new ArrayList<>();
        for (RideStep step : ridePath.getSteps()) {
            if (step.getPolyline() != null) {
                for (LatLonPoint point : step.getPolyline()) {
                    pathPoints.add(new LatLng(point.getLatitude(), point.getLongitude()));
                }
            }
            NavigationStep navStep = new NavigationStep();
            navStep.instruction = step.getInstruction();
            navStep.distance = step.getDistance();
            navStep.duration = step.getDuration();
            navStep.roadName = step.getRoad();
            navStep.action = step.getAction();
            navigationSteps.add(navStep);
        }

        drawRoute(pathPoints);

        float distanceKm = ridePath.getDistance() / 1000f;
        long durationMin = ridePath.getDuration() / 60;
        showRouteInfo(distanceKm, durationMin, ridePath.getSteps().size());
    }

    // ===== 地图绘制辅助 =====

    private void addMarkers() {
        LatLng destLatLng = new LatLng(destLatitude, destLongitude);
        endMarker = mAMap.addMarker(new MarkerOptions()
                .position(destLatLng)
                .title(getString(R.string.end_point))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        if (startLatitude != 0 && startLongitude != 0) {
            LatLng startLatLng = new LatLng(startLatitude, startLongitude);
            startMarker = mAMap.addMarker(new MarkerOptions()
                    .position(startLatLng)
                    .title(getString(R.string.start_point))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        }
    }

    private void drawRoute(List<LatLng> pathPoints) {
        if (pathPoints.isEmpty()) return;

        mAMap.addPolyline(new PolylineOptions()
                .addAll(pathPoints)
                .width(20)
                .color(Color.parseColor("#4CAF50"))
                .setDottedLine(false));

        // 缩放地图以显示整条路线
        try {
            com.amap.api.maps.model.LatLngBounds.Builder boundsBuilder = new com.amap.api.maps.model.LatLngBounds.Builder();
            for (LatLng point : pathPoints) {
                boundsBuilder.include(point);
            }
            mAMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100));
        } catch (Exception e) {
            Log.e(TAG, "Bounds error: " + e.getMessage());
        }
    }

    private void showRouteInfo(float distanceKm, long durationMin, int stepCount) {
        naviTitle.setText(R.string.navi_route_ready);
        routeInfoPanel.setVisibility(View.VISIBLE);

        String distanceStr = distanceKm < 1
                ? String.format("%s m", distanceFormat.format(distanceKm * 1000))
                : String.format("%s km", distanceFormat.format(distanceKm));
        tvRouteDistance.setText(distanceStr);

        String durationStr = durationMin < 60
                ? String.format("%d %s", durationMin, getString(R.string.navi_minutes))
                : String.format("%d%s%d%s", durationMin / 60, getString(R.string.navi_hour),
                durationMin % 60, getString(R.string.navi_minutes));
        tvRouteDuration.setText(durationStr);

        tvRouteSteps.setText(String.format(getString(R.string.navi_steps_count), stepCount));

        // 设置步骤列表
        rvSteps.setLayoutManager(new LinearLayoutManager(this));
        NavigationStepAdapter adapter = new NavigationStepAdapter(navigationSteps);
        rvSteps.setAdapter(adapter);
    }

    // ===== 导航控制 =====

    private void startNavigation() {
        if (navigationSteps.isEmpty()) return;

        isNavigating = true;
        currentStepIndex = 0;
        routeInfoPanel.setVisibility(View.GONE);
        naviModeBar.setVisibility(View.GONE);
        naviStatusPanel.setVisibility(View.VISIBLE);
        naviTitle.setText(R.string.navi_navigating);

        showCurrentStep();
    }

    private void showCurrentStep() {
        if (currentStepIndex >= navigationSteps.size()) {
            tvNaviInstruction.setText(R.string.navi_arrived);
            tvNaviDistance.setText("");
            btnNextStep.setVisibility(View.GONE);
            return;
        }

        NavigationStep step = navigationSteps.get(currentStepIndex);
        tvNaviInstruction.setText(step.instruction != null ? step.instruction : step.roadName);

        String distStr = step.distance >= 1000
                ? String.format("%s km", distanceFormat.format(step.distance / 1000.0))
                : String.format("%.0f m", step.distance);
        tvNaviDistance.setText(distStr);

        btnNextStep.setVisibility(currentStepIndex < navigationSteps.size() - 1 ? View.VISIBLE : View.GONE);
        btnNextStep.setText(String.format("%s (%d/%d)", getString(R.string.navi_next_step), currentStepIndex + 2, navigationSteps.size()));
    }

    private void showNextStep() {
        if (currentStepIndex < navigationSteps.size() - 1) {
            currentStepIndex++;
            showCurrentStep();
        }
    }

    private void stopNavigation() {
        isNavigating = false;
        naviStatusPanel.setVisibility(View.GONE);
        naviModeBar.setVisibility(View.VISIBLE);
        routeInfoPanel.setVisibility(View.VISIBLE);
        naviTitle.setText(R.string.navi_route_ready);
    }

    // ===== Activity 生命周期 =====

    @Override
    protected void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMapView != null) mMapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mMapView != null) mMapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mMapView != null) mMapView.onSaveInstanceState(outState);
    }

    // ===== 内部数据类 =====

    private static class NavigationStep {
        String instruction;
        float distance;
        float duration;
        String roadName;
        String action;
    }

    // ===== 步骤列表适配器 =====

    private class NavigationStepAdapter extends RecyclerView.Adapter<NavigationStepAdapter.ViewHolder> {

        private List<NavigationStep> steps;

        NavigationStepAdapter(List<NavigationStep> steps) {
            this.steps = steps;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(32, 16, 32, 16);
            tv.setTextSize(14);
            tv.setTextColor(Color.parseColor("#333333"));
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            NavigationStep step = steps.get(position);
            String distStr = step.distance >= 1000
                    ? String.format("%s km", distanceFormat.format(step.distance / 1000.0))
                    : String.format("%.0f m", step.distance);
            String text = String.format("%d. %s (%s)", position + 1,
                    step.instruction != null ? step.instruction : step.roadName, distStr);
            ((TextView) holder.itemView).setText(text);
        }

        @Override
        public int getItemCount() {
            return steps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(TextView itemView) {
                super(itemView);
            }
        }
    }
}
