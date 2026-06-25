package com.RockiotTag.tag;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.RockiotTag.tag.model.TagDevice;

public class BLEForegroundService extends Service {
    private static final String TAG = "BLEForegroundService";
    private static final String CHANNEL_ID = "ble_scanning_channel";
    private static final int NOTIFICATION_ID = 1;

    private BLEManager bleManager;
    private DatabaseHelper databaseHelper;
    private CrowdSourcingManager crowdSourcingManager;

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        BLEForegroundService getService() {
            return BLEForegroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();


        bleManager = new BLEManager(this);
        databaseHelper = DatabaseHelper.getInstance(this);
        crowdSourcingManager = new CrowdSourcingManager();

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        // 检查并请求蓝牙权限
        checkBluetoothPermissions();
    }

    private void checkBluetoothPermissions() {
        // 这里需要实现蓝牙权限检查和请求逻辑
        // 对于Android 12及以上，需要请求BLUETOOTH_SCAN和BLUETOOTH_CONNECT权限
        // 对于Android 13及以上，还需要请求BLUETOOTH_PRIVILEGED权限（如果需要）
        
        // 不再自动开始持续扫描，只在需要时扫描
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (bleManager != null) {
            bleManager.stopScanning();
        }
        // 释放数据库引用（单例不 close，随进程生命周期存在）
        databaseHelper = null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "BLE Scanning",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("正在扫描蓝牙设备");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("RockiotTag")
                .setContentText("正在扫描蓝牙设备...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void startBLEScanning() {
        if (bleManager.isBluetoothEnabled()) {
            bleManager.startScanning(new BLEManager.DeviceScanCallback() {
                @Override
                public void onDeviceFound(TagDevice device) {

                    databaseHelper.addDevice(device);
                    
                    // 检查是否是已绑定的设备
                    if (isDeviceBound(device)) {
                        // 尝试连接已绑定的设备
                        connectToBoundDevice(device);
                    }
                    
                    // 发送设备数据到众包网络
                    crowdSourcingManager.sendDeviceData(device);
                }

                @Override
                public void onScanComplete() {

                    // 扫描完成后，不再继续扫描
                }
            });
        } else {

        }
    }

    private boolean isDeviceBound(TagDevice device) {
        // 检查设备是否已绑定
        // 实际实现中应该从数据库或SharedPreferences中查询
        return databaseHelper.isDeviceBound(device.getDeviceId());
    }

    private void connectToBoundDevice(TagDevice device) {
        // 尝试连接已绑定的设备
        // 这里需要获取BluetoothDevice对象
        // 实际实现中应该通过BluetoothAdapter.getRemoteDevice(deviceId)获取

        // 暂时只记录日志，实际应用中需要实现连接逻辑
    }

    public void stopScanning() {
        if (bleManager != null) {
            bleManager.stopScanning();
        }
    }

    public void startScanning() {
        startBLEScanning();
    }
}