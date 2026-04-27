package com.RockiotTag.tag;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.core.LatLonPoint;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ApiTestActivity extends AppCompatActivity {

    private TextView logTextView;
    private ScrollView scrollView;
    private EditText deviceNumEdit;
    private EditText latEdit;
    private EditText lngEdit;
    private NewApiService apiService;
    private DatabaseHelper databaseHelper;
    private StringBuilder logBuilder;
    private List<Device> boundDevices;
    private GeocodeSearch geocodeSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_test);

        apiService = NewApiService.getInstance();
        databaseHelper = new DatabaseHelper(this);
        logBuilder = new StringBuilder();

        logTextView = findViewById(R.id.log_text);
        scrollView = findViewById(R.id.scroll_view);
        deviceNumEdit = findViewById(R.id.device_num_edit);
        deviceNumEdit.setText("1756727210030683");
        
        // 添加经纬度输入框
        latEdit = new EditText(this);
        latEdit.setHint("输入纬度 (如: 22.575367)");
        latEdit.setText("22.575367");
        
        lngEdit = new EditText(this);
        lngEdit.setHint("输入经度 (如: 113.918252)");
        lngEdit.setText("113.918252");
        
        Button testGeocodeBtn = findViewById(R.id.test_geocode_btn);
        Button testBindBtn = findViewById(R.id.test_bind_btn);
        Button testUnbindBtn = findViewById(R.id.test_unbind_btn);
        Button testRefreshLocationBtn = findViewById(R.id.test_refresh_location_btn);
        Button testDeviceInfoBtn = findViewById(R.id.test_device_info_btn);
        Button clearLogBtn = findViewById(R.id.clear_log_btn);
        Button backBtn = findViewById(R.id.back_btn);

        // 初始化逆地理编码服务
        try {
            geocodeSearch = new GeocodeSearch(this);
            appendLog("逆地理编码服务初始化成功");
        } catch (Exception e) {
            appendLog("逆地理编码服务初始化失败: " + e.getMessage());
        }

        backBtn.setOnClickListener(v -> finish());

        clearLogBtn.setOnClickListener(v -> {
            logBuilder = new StringBuilder();
            logTextView.setText("");
            appendLog("日志已清空");
        });

        testGeocodeBtn.setOnClickListener(v -> testGeocode());

        testBindBtn.setOnClickListener(v -> testBindDevice());

        testUnbindBtn.setOnClickListener(v -> testUnbindDevice());

        testRefreshLocationBtn.setOnClickListener(v -> testRefreshLocation());

        testDeviceInfoBtn.setOnClickListener(v -> testGetDeviceInfo());

        appendLog("API测试界面已启动");
    }

    private void appendLog(String message) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        logBuilder.append("[").append(timestamp).append("] ").append(message).append("\n\n");
        logTextView.setText(logBuilder.toString());
        
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void testGeocode() {
        appendLog("========== 测试逆地理编码 ==========");
        
        // 使用默认测试坐标（深圳宝安区）
        double latitude = 22.575367;
        double longitude = 113.918252;
        
        appendLog("测试坐标 (WGS84):");
        appendLog("  纬度: " + latitude);
        appendLog("  经度: " + longitude);
        
        if (geocodeSearch == null) {
            appendLog("错误: geocodeSearch 为 null，无法进行逆地理编码");
            return;
        }
        
        appendLog("geocodeSearch 不为 null，开始查询...");
        
        new Thread(() -> {
            try {
                appendLog("创建 LatLonPoint...");
                LatLonPoint latLonPoint = new LatLonPoint(latitude, longitude);
                appendLog("LatLonPoint: " + latLonPoint.getLatitude() + ", " + latLonPoint.getLongitude());
                
                appendLog("创建 RegeocodeQuery (坐标系: GPS/WGS84)...");
                RegeocodeQuery query = new RegeocodeQuery(latLonPoint, 200, GeocodeSearch.GPS);
                
                appendLog("调用 getFromLocation...");
                RegeocodeAddress regeocodeAddress = geocodeSearch.getFromLocation(query);
                
                runOnUiThread(() -> {
                    if (regeocodeAddress != null) {
                        appendLog("========== 逆地理编码成功 ==========");
                        String formatAddress = regeocodeAddress.getFormatAddress();
                        appendLog("完整地址: " + formatAddress);
                        
                        String province = regeocodeAddress.getProvince();
                        String city = regeocodeAddress.getCity();
                        String district = regeocodeAddress.getDistrict();
                        String township = regeocodeAddress.getTownship();
                        String streetNumber = null;
                        if (regeocodeAddress.getStreetNumber() != null) {
                            streetNumber = regeocodeAddress.getStreetNumber().getStreet();
                        }
                        
                        appendLog("省: " + province);
                        appendLog("市: " + city);
                        appendLog("区: " + district);
                        appendLog("街道: " + township);
                        appendLog("道路: " + streetNumber);
                        
                        // 构建详细地址
                        StringBuilder sb = new StringBuilder();
                        if (province != null && !province.isEmpty()) {
                            sb.append(province);
                        }
                        if (city != null && !city.isEmpty() && !city.equals(province)) {
                            sb.append(city);
                        }
                        if (district != null && !district.isEmpty()) {
                            sb.append(district);
                        }
                        if (township != null && !township.isEmpty()) {
                            sb.append(township);
                        }
                        if (streetNumber != null && !streetNumber.isEmpty()) {
                            sb.append(streetNumber);
                        }
                        
                        if (sb.length() > 0) {
                            sb.append("附近");
                        }
                        
                        appendLog("========== 最终地址 ==========");
                        appendLog(sb.toString());
                    } else {
                        appendLog("错误: regeocodeAddress 为 null");
                    }
                });
            } catch (com.amap.api.services.core.AMapException e) {
                runOnUiThread(() -> {
                    appendLog("AMapException 错误:");
                    appendLog("  错误码: " + e.getErrorCode());
                    appendLog("  错误信息: " + e.getMessage());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("Exception 错误:");
                    appendLog("  类型: " + e.getClass().getName());
                    appendLog("  信息: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        }).start();
    }

    private void testBindDevice() {
        String deviceNum = deviceNumEdit.getText() != null ? deviceNumEdit.getText().toString().trim() : "";
        if (deviceNum.isEmpty()) {
            appendLog("请输入设备号！");
            Toast.makeText(this, "请输入设备号", Toast.LENGTH_SHORT).show();
            return;
        }
        
        appendLog("开始绑定设备...");
        appendLog("设备号: " + deviceNum);
        appendLog("昵称: 测试设备");
        
        new Thread(() -> {
            try {
                NewApiService.ApiResponse response = apiService.bindDevice(deviceNum, null, "测试设备");
                
                runOnUiThread(() -> {
                    appendLog("绑定设备响应:");
                    if (response != null) {
                        appendLog("  成功: " + response.isSuccess());
                        appendLog("  状态: " + response.getStatus());
                        appendLog("  代码: " + response.getCode());
                        appendLog("  消息: " + response.getMessage());
                        appendLog("  有items: " + (response.getItems() != null));
                        
                        if (response.getItems() != null) {
                            appendLog("  items内容: " + response.getItems().toString());
                        }
                    } else {
                        appendLog("  响应为空！");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("绑定设备出错: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        }).start();
    }

    private void testUnbindDevice() {
        String deviceNum = deviceNumEdit.getText() != null ? deviceNumEdit.getText().toString().trim() : "";
        if (deviceNum.isEmpty()) {
            appendLog("请输入设备号！");
            Toast.makeText(this, "请输入设备号", Toast.LENGTH_SHORT).show();
            return;
        }
        
        appendLog("========== 开始解绑设备 ==========");
        appendLog("设备号: " + deviceNum);
        
        new Thread(() -> {
            try {
                appendLog("正在调用API解绑...");
                NewApiService.ApiResponse response = apiService.unbindDevice(deviceNum);
                
                runOnUiThread(() -> {
                    appendLog("========== 解绑设备响应 ==========");
                    if (response != null) {
                        appendLog("  成功: " + response.isSuccess());
                        appendLog("  状态: " + response.getStatus());
                        appendLog("  代码: " + response.getCode());
                        appendLog("  消息: " + response.getMessage());
                        appendLog("  有items: " + (response.getItems() != null));
                        
                        if (response.getItems() != null) {
                            appendLog("  items内容: " + response.getItems().toString());
                        }
                        
                        if (response.isSuccess()) {
                            appendLog("========== 解绑成功！ ==========");
                        } else {
                            appendLog("========== 解绑失败！ ==========");
                            appendLog("请检查设备号是否正确");
                        }
                    } else {
                        appendLog("  响应为空！");
                        appendLog("========== 解绑失败！ ==========");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("解绑设备出错: " + e.getMessage());
                    appendLog("========== 解绑失败！ ==========");
                    e.printStackTrace();
                });
            }
        }).start();
    }

    private void testRefreshLocation() {
        String testDeviceNum = deviceNumEdit.getText() != null ? deviceNumEdit.getText().toString().trim() : "";
        if (testDeviceNum.isEmpty()) {
            appendLog("请输入设备号！");
            Toast.makeText(this, "请输入设备号", Toast.LENGTH_SHORT).show();
            return;
        }
        
        appendLog("开始刷新设备位置，设备号: " + testDeviceNum);
        appendLog("注意：refreshLocation只是请求设备上报位置，设备需要时间响应");
        
        new Thread(() -> {
            try {
                NewApiService.ApiResponse response = apiService.refreshLocation(testDeviceNum);
                
                runOnUiThread(() -> {
                    appendLog("刷新设备位置响应:");
                    if (response != null) {
                        appendLog("  成功: " + response.isSuccess());
                        appendLog("  状态: " + response.getStatus());
                        appendLog("  代码: " + response.getCode());
                        appendLog("  消息: " + response.getMessage());
                        
                        if (response.isSuccess()) {
                            appendLog("\n请等待设备上报位置后，再点击\"测试获取设备信息\"");
                            appendLog("建议等待10-30秒后再获取设备信息");
                        }
                    } else {
                        appendLog("  响应为空！");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("刷新设备位置出错: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        }).start();
    }

    private void testGetDeviceInfo() {
        String testDeviceNum = deviceNumEdit.getText() != null ? deviceNumEdit.getText().toString().trim() : "";
        if (testDeviceNum.isEmpty()) {
            appendLog("请输入设备号！");
            Toast.makeText(this, "请输入设备号", Toast.LENGTH_SHORT).show();
            return;
        }
        
        appendLog("开始获取设备信息，使用设备号: " + testDeviceNum);
        appendLog("注意：此方法会先刷新位置再获取，需要等待5秒");
        
        new Thread(() -> {
            try {
                NewApiService.DeviceInfo deviceInfo = apiService.getDeviceInfo(testDeviceNum);
                
                runOnUiThread(() -> {
                    if (deviceInfo != null) {
                        appendLog("设备信息获取成功:");
                        appendLog("  设备号: " + deviceInfo.deviceNum);
                        appendLog("  昵称: " + deviceInfo.nickName);
                        appendLog("  MAC: " + deviceInfo.mac);
                        appendLog("  纬度: " + deviceInfo.latitude);
                        appendLog("  经度: " + deviceInfo.longitude);
                        appendLog("  电池: " + deviceInfo.battery + "%");
                        appendLog("  时间戳: " + deviceInfo.timestamp);
                        
                        if (deviceInfo.timestamp > 0) {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                            appendLog("  更新时间: " + sdf.format(new Date(deviceInfo.timestamp)));
                        }
                        
                        if (deviceInfo.latitude == 0 && deviceInfo.longitude == 0) {
                            appendLog("\n警告：设备位置为(0,0)，可能设备尚未上报位置");
                        }
                    } else {
                        appendLog("设备信息为空！请检查设备是否在API中存在。");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("获取设备信息出错: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        }).start();
    }
}
