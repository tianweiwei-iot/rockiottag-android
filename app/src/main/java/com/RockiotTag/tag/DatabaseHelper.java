package com.RockiotTag.tag;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "rockiottag.db";
    private static final int DATABASE_VERSION = 4;

    // 设备表
    private static final String TABLE_DEVICES = "devices";
    private static final String COLUMN_DEVICE_ID = "device_id";
    private static final String COLUMN_DEVICE_NUM = "device_num";
    private static final String COLUMN_DEVICE_NAME = "device_name";
    private static final String COLUMN_TAG = "tag";
    private static final String COLUMN_LATITUDE = "latitude";
    private static final String COLUMN_LONGITUDE = "longitude";
    private static final String COLUMN_SIGNAL_STRENGTH = "signal_strength";
    private static final String COLUMN_LAST_SEEN = "last_seen";

    // 位置历史表
    private static final String TABLE_LOCATION_HISTORY = "location_history";
    private static final String COLUMN_HISTORY_ID = "history_id";
    private static final String COLUMN_HISTORY_DEVICE_ID = "device_id";
    private static final String COLUMN_HISTORY_LATITUDE = "latitude";
    private static final String COLUMN_HISTORY_LONGITUDE = "longitude";
    private static final String COLUMN_HISTORY_TIMESTAMP = "timestamp";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建设备表
        String CREATE_DEVICES_TABLE = "CREATE TABLE " + TABLE_DEVICES + "("
                + COLUMN_DEVICE_ID + " TEXT PRIMARY KEY, "
                + COLUMN_DEVICE_NUM + " TEXT, "
                + COLUMN_DEVICE_NAME + " TEXT, "
                + COLUMN_TAG + " TEXT, "
                + COLUMN_LATITUDE + " REAL, "
                + COLUMN_LONGITUDE + " REAL, "
                + COLUMN_SIGNAL_STRENGTH + " INTEGER, "
                + COLUMN_LAST_SEEN + " INTEGER" + ")";
        db.execSQL(CREATE_DEVICES_TABLE);

        // 创建位置历史表
        String CREATE_LOCATION_HISTORY_TABLE = "CREATE TABLE " + TABLE_LOCATION_HISTORY + "("
                + COLUMN_HISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_HISTORY_DEVICE_ID + " TEXT, "
                + COLUMN_HISTORY_LATITUDE + " REAL, "
                + COLUMN_HISTORY_LONGITUDE + " REAL, "
                + COLUMN_HISTORY_TIMESTAMP + " INTEGER, "
                + "FOREIGN KEY(" + COLUMN_HISTORY_DEVICE_ID + ") REFERENCES " + TABLE_DEVICES + "(" + COLUMN_DEVICE_ID + ")" + ")";
        db.execSQL(CREATE_LOCATION_HISTORY_TABLE);

        Log.d(TAG, "Database tables created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 升级数据库时的操作
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOCATION_HISTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DEVICES);
        onCreate(db);
    }

    // 添加设备
    public void addDevice(Device device) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_DEVICE_ID, device.getDeviceId());
        values.put(COLUMN_DEVICE_NUM, device.getDeviceNum());
        values.put(COLUMN_DEVICE_NAME, device.getName());
        values.put(COLUMN_TAG, device.getTag());
        values.put(COLUMN_LATITUDE, device.getLatitude());
        values.put(COLUMN_LONGITUDE, device.getLongitude());
        values.put(COLUMN_SIGNAL_STRENGTH, device.getSignalStrength());
        values.put(COLUMN_LAST_SEEN, device.getLastSeen());

        // 插入或更新设备
        long result = db.replace(TABLE_DEVICES, null, values);
        Log.d(TAG, "Device added/updated: " + result);

        db.close();
    }



    // 获取所有设备
    public List<Device> getAllDevices() {
        List<Device> deviceList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_DEVICES;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Device device = new Device(
                        cursor.getString(cursor.getColumnIndex(COLUMN_DEVICE_ID)),
                        cursor.getString(cursor.getColumnIndex(COLUMN_DEVICE_NAME))
                );
                int deviceNumIndex = cursor.getColumnIndex(COLUMN_DEVICE_NUM);
                if (deviceNumIndex != -1) {
                    device.setDeviceNum(cursor.getString(deviceNumIndex));
                }
                int tagIndex = cursor.getColumnIndex(COLUMN_TAG);
                if (tagIndex != -1) {
                    device.setTag(cursor.getString(tagIndex));
                }
                device.setLatitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_LATITUDE)));
                device.setLongitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_LONGITUDE)));
                device.setSignalStrength(cursor.getInt(cursor.getColumnIndex(COLUMN_SIGNAL_STRENGTH)));
                device.setLastSeen(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_SEEN)));
                deviceList.add(device);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return deviceList;
    }

    // 根据设备ID获取设备
    public Device getDevice(String deviceId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_DEVICES, null, COLUMN_DEVICE_ID + "=?", new String[]{deviceId}, null, null, null);

        Device device = null;
        if (cursor.moveToFirst()) {
            device = new Device(
                    cursor.getString(cursor.getColumnIndex(COLUMN_DEVICE_ID)),
                    cursor.getString(cursor.getColumnIndex(COLUMN_DEVICE_NAME))
            );
            int deviceNumIndex = cursor.getColumnIndex(COLUMN_DEVICE_NUM);
            if (deviceNumIndex != -1) {
                device.setDeviceNum(cursor.getString(deviceNumIndex));
            }
            int tagIndex = cursor.getColumnIndex(COLUMN_TAG);
            if (tagIndex != -1) {
                device.setTag(cursor.getString(tagIndex));
            }
            device.setLatitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_LATITUDE)));
            device.setLongitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_LONGITUDE)));
            device.setSignalStrength(cursor.getInt(cursor.getColumnIndex(COLUMN_SIGNAL_STRENGTH)));
            device.setLastSeen(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_SEEN)));
        }

        cursor.close();
        db.close();
        return device;
    }

    // 删除设备
    public void deleteDevice(String deviceId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_DEVICES, COLUMN_DEVICE_ID + "=?", new String[]{deviceId});
        Log.d(TAG, "Device deleted: " + result);
        db.close();
    }

    // 获取设备数量
    public int getDeviceCount() {
        String countQuery = "SELECT * FROM " + TABLE_DEVICES;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        int count = cursor.getCount();
        cursor.close();
        db.close();
        return count;
    }

    // 检查设备是否已绑定
    public boolean isDeviceBound(String deviceId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_DEVICES, null, COLUMN_DEVICE_ID + "=?", new String[]{deviceId}, null, null, null);
        boolean isBound = cursor.moveToFirst();
        cursor.close();
        db.close();
        return isBound;
    }

    // 添加位置记录
    public void addLocationRecord(LocationRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_HISTORY_DEVICE_ID, record.getDeviceId());
        values.put(COLUMN_HISTORY_LATITUDE, record.getLatitude());
        values.put(COLUMN_HISTORY_LONGITUDE, record.getLongitude());
        values.put(COLUMN_HISTORY_TIMESTAMP, record.getTimestamp());

        long result = db.insert(TABLE_LOCATION_HISTORY, null, values);
        Log.d(TAG, "Location record added: " + result);

        db.close();
    }

    // 获取指定设备和时间范围的位置记录
    public List<LocationRecord> getLocationRecords(String deviceId, long startTime, long endTime) {
        List<LocationRecord> recordList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String selectQuery = "SELECT * FROM " + TABLE_LOCATION_HISTORY + 
            " WHERE " + COLUMN_HISTORY_DEVICE_ID + "=?" +
            " AND " + COLUMN_HISTORY_TIMESTAMP + ">=?" +
            " AND " + COLUMN_HISTORY_TIMESTAMP + "<=?" +
            " ORDER BY " + COLUMN_HISTORY_TIMESTAMP + " ASC";

        Cursor cursor = db.rawQuery(selectQuery, new String[]{
            deviceId,
            String.valueOf(startTime),
            String.valueOf(endTime)
        });

        if (cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndex(COLUMN_HISTORY_ID));
                String devId = cursor.getString(cursor.getColumnIndex(COLUMN_HISTORY_DEVICE_ID));
                double latitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_HISTORY_LATITUDE));
                double longitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_HISTORY_LONGITUDE));
                long timestamp = cursor.getLong(cursor.getColumnIndex(COLUMN_HISTORY_TIMESTAMP));

                LocationRecord record = new LocationRecord(id, devId, latitude, longitude, timestamp);
                recordList.add(record);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return recordList;
    }

    // 清理超过一个月的位置记录
    public void cleanOldLocationRecords() {
        SQLiteDatabase db = this.getWritableDatabase();
        long oneMonthAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L);
        int deletedCount = db.delete(TABLE_LOCATION_HISTORY, 
            COLUMN_HISTORY_TIMESTAMP + "<?", 
            new String[]{String.valueOf(oneMonthAgo)});
        Log.d(TAG, "Cleaned " + deletedCount + " old location records");
        db.close();
    }
    
    // 删除所有位置记录
    public int deleteAllLocationRecords() {
        SQLiteDatabase db = this.getWritableDatabase();
        int deletedCount = db.delete(TABLE_LOCATION_HISTORY, null, null);
        Log.d(TAG, "Deleted " + deletedCount + " all location records");
        db.close();
        return deletedCount;
    }
    
    // 删除指定设备的位置记录
    public int deleteLocationRecordsByDevice(String deviceId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int deletedCount = db.delete(TABLE_LOCATION_HISTORY, 
            COLUMN_HISTORY_DEVICE_ID + "=?", 
            new String[]{deviceId});
        Log.d(TAG, "Deleted " + deletedCount + " location records for device: " + deviceId);
        db.close();
        return deletedCount;
    }
}
