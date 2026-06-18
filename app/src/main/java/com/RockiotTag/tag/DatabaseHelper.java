package com.RockiotTag.tag;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.RockiotTag.tag.util.LogUtil;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "rockiottag.db";
    private static final int DATABASE_VERSION = 8;

    // 设备表
    private static final String TABLE_DEVICES = "devices";
    private static final String COLUMN_DEVICE_ID = "device_id";
    private static final String COLUMN_DEVICE_NUM = "device_num";
    private static final String COLUMN_DEVICE_NAME = "device_name";
    private static final String COLUMN_DEVICE_MAC = "device_mac";
    private static final String COLUMN_CUSTOMER_CODE = "customer_code";
    private static final String COLUMN_TAG = "tag";
    private static final String COLUMN_LATITUDE = "latitude";
    private static final String COLUMN_LONGITUDE = "longitude";
    private static final String COLUMN_SIGNAL_STRENGTH = "signal_strength";
    private static final String COLUMN_LAST_SEEN = "last_seen";
    private static final String COLUMN_BATTERY = "battery";

    // 位置历史表
    private static final String TABLE_LOCATION_HISTORY = "location_history";
    private static final String COLUMN_HISTORY_ID = "history_id";
    private static final String COLUMN_HISTORY_DEVICE_ID = "device_id";
    private static final String COLUMN_HISTORY_LATITUDE = "latitude";
    private static final String COLUMN_HISTORY_LONGITUDE = "longitude";
    private static final String COLUMN_HISTORY_ACCURACY = "accuracy"; // 新增：精度字段
    private static final String COLUMN_HISTORY_TIMESTAMP = "timestamp";
    
    // 地址缓存表
    private static final String TABLE_ADDRESS_CACHE = "address_cache";
    public static final String TABLE_ADDRESS_CACHE_PUBLIC = TABLE_ADDRESS_CACHE; // 公开表名供外部使用
    public static final String COLUMN_CACHE_KEY = "cache_key"; // lat,lng,language
    public static final String COLUMN_CACHE_ADDRESS = "address";
    public static final String COLUMN_CACHE_TIMESTAMP = "cache_timestamp";
    public static final String COLUMN_CACHE_LANGUAGE = "language";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_DEVICES_TABLE = "CREATE TABLE " + TABLE_DEVICES + "("
                + COLUMN_DEVICE_ID + " TEXT PRIMARY KEY, "
                + COLUMN_DEVICE_NUM + " TEXT, "
                + COLUMN_DEVICE_NAME + " TEXT, "
                + COLUMN_DEVICE_MAC + " TEXT DEFAULT '', "
                + COLUMN_CUSTOMER_CODE + " TEXT DEFAULT '', "
                + COLUMN_TAG + " TEXT, "
                + COLUMN_LATITUDE + " REAL, "
                + COLUMN_LONGITUDE + " REAL, "
                + COLUMN_SIGNAL_STRENGTH + " INTEGER, "
                + COLUMN_LAST_SEEN + " INTEGER, "
                + COLUMN_BATTERY + " INTEGER DEFAULT -1" + ")";
        db.execSQL(CREATE_DEVICES_TABLE);

        String CREATE_LOCATION_HISTORY_TABLE = "CREATE TABLE " + TABLE_LOCATION_HISTORY + "("
                + COLUMN_HISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_HISTORY_DEVICE_ID + " TEXT, "
                + COLUMN_HISTORY_LATITUDE + " REAL, "
                + COLUMN_HISTORY_LONGITUDE + " REAL, "
                + COLUMN_HISTORY_ACCURACY + " REAL DEFAULT 50.0, "
                + COLUMN_HISTORY_TIMESTAMP + " INTEGER, "
                + "FOREIGN KEY(" + COLUMN_HISTORY_DEVICE_ID + ") REFERENCES " + TABLE_DEVICES + "(" + COLUMN_DEVICE_ID + ")" + ")";
        db.execSQL(CREATE_LOCATION_HISTORY_TABLE);
        
        String CREATE_ADDRESS_CACHE_TABLE = "CREATE TABLE " + TABLE_ADDRESS_CACHE + "("
                + COLUMN_CACHE_KEY + " TEXT PRIMARY KEY, "
                + COLUMN_CACHE_ADDRESS + " TEXT, "
                + COLUMN_CACHE_TIMESTAMP + " INTEGER, "
                + COLUMN_CACHE_LANGUAGE + " TEXT" + ")";
        db.execSQL(CREATE_ADDRESS_CACHE_TABLE);

        LogUtil.d(TAG, "Database tables created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LogUtil.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        
        if (oldVersion < 8) {
            try {
                LogUtil.d(TAG, "Adding battery column to devices table");
                db.execSQL("ALTER TABLE " + TABLE_DEVICES + " ADD COLUMN " + COLUMN_BATTERY + " INTEGER DEFAULT -1");
                LogUtil.d(TAG, "battery column added successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error adding battery column: " + e.getMessage());
            }
        }
        
        if (oldVersion < 7) {
            try {
                LogUtil.d(TAG, "Adding customer_code column to devices table");
                db.execSQL("ALTER TABLE " + TABLE_DEVICES + " ADD COLUMN " + COLUMN_CUSTOMER_CODE + " TEXT DEFAULT ''");
                LogUtil.d(TAG, "customer_code column added successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error adding customer_code column: " + e.getMessage());
            }
        }
        
        if (oldVersion < 6) {
            try {
                LogUtil.d(TAG, "Adding accuracy column to location_history table");
                db.execSQL("ALTER TABLE " + TABLE_LOCATION_HISTORY + " ADD COLUMN " + COLUMN_HISTORY_ACCURACY + " REAL DEFAULT 50.0");
                LogUtil.d(TAG, "Accuracy column added successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error adding accuracy column: " + e.getMessage());
            }
        }
        
        if (oldVersion < 5) {
            try {
                LogUtil.d(TAG, "Adding MAC address column to devices table");
                db.execSQL("ALTER TABLE " + TABLE_DEVICES + " ADD COLUMN " + COLUMN_DEVICE_MAC + " TEXT DEFAULT ''");
                LogUtil.d(TAG, "MAC address column added successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error adding MAC column: " + e.getMessage());
            }
        }
        
        if (oldVersion < 4) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOCATION_HISTORY);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_DEVICES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ADDRESS_CACHE);
            onCreate(db);
        }
    }
    
    /**
     * 手动升级数据库（不删除数据）
     * 用于从旧版本升级到支持多语言缓存的版本
     */
    public void upgradeToMultiLanguageCache() {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            // 检查是否需要添加language列
            Cursor cursor = db.rawQuery("PRAGMA table_info(" + TABLE_ADDRESS_CACHE + ")", null);
            boolean hasLanguageColumn = false;
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    if (columnName.equals(COLUMN_CACHE_LANGUAGE)) {
                        hasLanguageColumn = true;
                        break;
                    }
                }
                cursor.close();
            }
            
            if (!hasLanguageColumn) {
                LogUtil.d(TAG, "Adding language column to address_cache table");
                db.execSQL("ALTER TABLE " + TABLE_ADDRESS_CACHE + " ADD COLUMN " + COLUMN_CACHE_LANGUAGE + " TEXT DEFAULT 'zh-CN'");
                
                // 更新现有的缓存记录的cache_key，添加语言后缀
                db.execSQL("UPDATE " + TABLE_ADDRESS_CACHE + " SET " + COLUMN_CACHE_LANGUAGE + "='zh-CN' WHERE " + COLUMN_CACHE_LANGUAGE + " IS NULL");
                
                LogUtil.d(TAG, "Database upgraded to support multi-language cache");
            } else {
                LogUtil.d(TAG, "Database already supports multi-language cache");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error upgrading database: " + e.getMessage(), e);
        }
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
    }

    public void addDevice(Device device) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_DEVICE_ID, device.getDeviceId());
        values.put(COLUMN_DEVICE_NUM, device.getDeviceNum());
        values.put(COLUMN_DEVICE_NAME, device.getName());
        values.put(COLUMN_DEVICE_MAC, device.getMac() != null ? device.getMac() : "");
        values.put(COLUMN_CUSTOMER_CODE, device.getCustomerCode() != null ? device.getCustomerCode() : "");
        values.put(COLUMN_TAG, device.getTag());
        values.put(COLUMN_LATITUDE, device.getLatitude());
        values.put(COLUMN_LONGITUDE, device.getLongitude());
        values.put(COLUMN_SIGNAL_STRENGTH, device.getSignalStrength());
        values.put(COLUMN_LAST_SEEN, device.getLastSeen());
        values.put(COLUMN_BATTERY, device.getBattery());

        long result = db.replace(TABLE_DEVICES, null, values);
        LogUtil.d(TAG, "Device added/updated: " + result + ", deviceId=" + device.getDeviceId() + ", name=" + device.getName() + ", mac=" + device.getMac() + ", customerCode=" + device.getCustomerCode());
    }

    /**
     * 更新设备位置、电量和最后更新时间
     */
    public void updateDeviceLocationAndBattery(String deviceId, double latitude, double longitude, int battery, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_LATITUDE, latitude);
        values.put(COLUMN_LONGITUDE, longitude);
        values.put(COLUMN_LAST_SEEN, timestamp);
        values.put(COLUMN_BATTERY, battery);
        
        int rowsAffected = db.update(TABLE_DEVICES, values, COLUMN_DEVICE_ID + "=?", new String[]{deviceId});
        LogUtil.d(TAG, "Updated device location/battery: " + rowsAffected + " rows for id: " + deviceId + ", battery=" + battery);
    }
    
    /**
     * 更新设备的MAC地址
     * @param deviceNum 设备号（16位数字）
     * @param mac MAC地址
     */
    public void updateDeviceMac(String deviceNum, String mac) {
        if (deviceNum == null || deviceNum.isEmpty() || mac == null || mac.isEmpty()) {
            Log.w(TAG, "Invalid parameters for updateDeviceMac");
            return;
        }
        
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_DEVICE_MAC, mac);
            
            int rowsAffected = db.update(TABLE_DEVICES, values, COLUMN_DEVICE_NUM + "=?", new String[]{deviceNum});
            LogUtil.d(TAG, "Updated MAC address for device " + deviceNum + ": " + rowsAffected + " rows, mac=" + mac);
        } catch (Exception e) {
            Log.e(TAG, "Error updating MAC address for device " + deviceNum, e);
        }
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
    }
    
    // 专门用于更新设备名称和标签的方法
    public boolean updateDeviceNameAndTag(String deviceId, String deviceNum, String name, String tag) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        try {
            // 步骤1：先查询设备是否存在
            LogUtil.d(TAG, "=== Step 1: Query device before update ===");
            Cursor cursor = db.query(TABLE_DEVICES, 
                new String[]{COLUMN_DEVICE_ID, COLUMN_DEVICE_NUM, COLUMN_DEVICE_NAME, COLUMN_TAG},
                null, null, null, null, null);
            
            LogUtil.d(TAG, "Total devices in database: " + cursor.getCount());
            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_ID));
                String num = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_NUM));
                String n = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_NAME));
                String t = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TAG));
                LogUtil.d(TAG, "  Device: id=" + id + ", num=" + num + ", name=" + n + ", tag=" + t);
            }
            cursor.close();
            
            // 步骤2：使用原始 SQL UPDATE
            LogUtil.d(TAG, "=== Step 2: Executing UPDATE ===");
            LogUtil.d(TAG, "Target deviceNum: " + deviceNum);
            LogUtil.d(TAG, "New name: " + name);
            LogUtil.d(TAG, "New tag: " + tag);
            
            String sql = "UPDATE " + TABLE_DEVICES + 
                        " SET " + COLUMN_DEVICE_NAME + "=?, " + COLUMN_TAG + "=?" +
                        " WHERE " + COLUMN_DEVICE_NUM + "=?";
            
            db.execSQL(sql, new String[]{name, tag, deviceNum});
            LogUtil.d(TAG, "✅ UPDATE SQL executed successfully");
            
            // 步骤3：验证更新结果
            LogUtil.d(TAG, "=== Step 3: Verify update ===");
            cursor = db.query(TABLE_DEVICES,
                new String[]{COLUMN_DEVICE_ID, COLUMN_DEVICE_NUM, COLUMN_DEVICE_NAME, COLUMN_TAG},
                COLUMN_DEVICE_NUM + "=?",
                new String[]{deviceNum},
                null, null, null);
            
            if (cursor.moveToFirst()) {
                String verifyId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_ID));
                String verifyNum = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_NUM));
                String verifyName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_NAME));
                String verifyTag = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TAG));
                
                LogUtil.d(TAG, "✅ VERIFICATION SUCCESS!");
                LogUtil.d(TAG, "  After update: id=" + verifyId + ", num=" + verifyNum + ", name=" + verifyName + ", tag=" + verifyTag);
                
                boolean success = verifyName.equals(name) && verifyTag.equals(tag);
                cursor.close();
                return success;
            } else {
                Log.e(TAG, "❌ VERIFICATION FAILED: Device not found after update!");
                cursor.close();
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ UPDATE EXCEPTION: " + e.getMessage(), e);

            return false;
        }
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
    }



    // 获取设备最新的位置记录时间戳
    public long getLatestRecordTimestamp(String deviceId) {
        SQLiteDatabase db = this.getReadableDatabase();
        long latestTimestamp = 0;
        
        try {
            Cursor cursor = db.query(TABLE_LOCATION_HISTORY,
                new String[]{"MAX(" + COLUMN_HISTORY_TIMESTAMP + ")"},
                COLUMN_HISTORY_DEVICE_ID + "=?",
                new String[]{deviceId},
                null, null, null);
            
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                latestTimestamp = cursor.getLong(0);

            }
            cursor.close();
        } catch (Exception e) {

        }
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
        
        return latestTimestamp;
    }

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
                int macIndex = cursor.getColumnIndex(COLUMN_DEVICE_MAC);
                if (macIndex != -1) {
                    String mac = cursor.getString(macIndex);
                    device.setMac(mac);
                    LogUtil.d(TAG, "Device loaded: id=" + device.getDeviceId() + ", num=" + device.getDeviceNum() + ", name=" + device.getName() + ", mac=" + mac);
                }
                int customerCodeIndex = cursor.getColumnIndex(COLUMN_CUSTOMER_CODE);
                if (customerCodeIndex != -1) {
                    device.setCustomerCode(cursor.getString(customerCodeIndex));
                }
                int tagIndex = cursor.getColumnIndex(COLUMN_TAG);
                if (tagIndex != -1) {
                    device.setTag(cursor.getString(tagIndex));
                }
                device.setLatitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_LATITUDE)));
                device.setLongitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_LONGITUDE)));
                device.setSignalStrength(cursor.getInt(cursor.getColumnIndex(COLUMN_SIGNAL_STRENGTH)));
                device.setLastSeen(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_SEEN)));
                int batteryIndex = cursor.getColumnIndex(COLUMN_BATTERY);
                if (batteryIndex != -1) {
                    device.setBattery(cursor.getInt(batteryIndex));
                }
                deviceList.add(device);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return deviceList;
    }

    public Device getDevice(String deviceId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_DEVICES, null, COLUMN_DEVICE_ID +"=?", new String[]{deviceId}, null, null, null);
    
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
            int macIndex = cursor.getColumnIndex(COLUMN_DEVICE_MAC);
            if (macIndex != -1) {
                device.setMac(cursor.getString(macIndex));
            }
            int customerCodeIndex = cursor.getColumnIndex(COLUMN_CUSTOMER_CODE);
            if (customerCodeIndex != -1) {
                device.setCustomerCode(cursor.getString(customerCodeIndex));
            }
            int tagIndex = cursor.getColumnIndex(COLUMN_TAG);
            if (tagIndex != -1) {
                device.setTag(cursor.getString(tagIndex));
            }
            device.setLatitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_LATITUDE)));
            device.setLongitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_LONGITUDE)));
            device.setSignalStrength(cursor.getInt(cursor.getColumnIndex(COLUMN_SIGNAL_STRENGTH)));
            device.setLastSeen(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_SEEN)));
            int batteryIndex = cursor.getColumnIndex(COLUMN_BATTERY);
            if (batteryIndex != -1) {
                device.setBattery(cursor.getInt(batteryIndex));
            }
        }
    
        cursor.close();
        return device;
    }
        
    public Device getDeviceByDeviceNum(String deviceNum) {
        if (deviceNum == null || deviceNum.isEmpty()) {
            return null;
        }
            
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();
            cursor = db.query(TABLE_DEVICES, null, COLUMN_DEVICE_NUM +"=?", new String[]{deviceNum}, null, null, null);
        
            Device device = null;
            if (cursor != null && cursor.moveToFirst()) {
                device = new Device(
                        cursor.getString(cursor.getColumnIndex(COLUMN_DEVICE_ID)),
                        cursor.getString(cursor.getColumnIndex(COLUMN_DEVICE_NAME))
                );
                int deviceNumIndex = cursor.getColumnIndex(COLUMN_DEVICE_NUM);
                if (deviceNumIndex != -1) {
                    device.setDeviceNum(cursor.getString(deviceNumIndex));
                }
                int macIndex = cursor.getColumnIndex(COLUMN_DEVICE_MAC);
                if (macIndex != -1) {
                    device.setMac(cursor.getString(macIndex));
                }
                int customerCodeIndex = cursor.getColumnIndex(COLUMN_CUSTOMER_CODE);
                if (customerCodeIndex != -1) {
                    device.setCustomerCode(cursor.getString(customerCodeIndex));
                }
                int tagIndex = cursor.getColumnIndex(COLUMN_TAG);
                if (tagIndex != -1) {
                    device.setTag(cursor.getString(tagIndex));
                }
                device.setLatitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_LATITUDE)));
                device.setLongitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_LONGITUDE)));
                device.setSignalStrength(cursor.getInt(cursor.getColumnIndex(COLUMN_SIGNAL_STRENGTH)));
                device.setLastSeen(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_SEEN)));
                int batteryIndex = cursor.getColumnIndex(COLUMN_BATTERY);
                if (batteryIndex != -1) {
                    device.setBattery(cursor.getInt(batteryIndex));
                }
            }
            return device;
        } catch (Exception e) {
            Log.e(TAG, "Error getting device by deviceNum: " + deviceNum, e);
            return null;
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    // 删除设备，返回删除的行数
    public int deleteDevice(String deviceId) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            int result = db.delete(TABLE_DEVICES, COLUMN_DEVICE_ID + "=?", new String[]{deviceId});

            
            // 如果通过deviceId删除失败，尝试通过deviceNum删除
            if (result == 0) {

            }
            
            return result;
        } catch (Exception e) {

            return 0;
        }
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
    }

    // 获取设备数量
    public int getDeviceCount() {
        String countQuery = "SELECT * FROM " + TABLE_DEVICES;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        int count = cursor.getCount();
        cursor.close();
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
        return count;
    }

    // 检查设备是否已绑定
    public boolean isDeviceBound(String deviceId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_DEVICES, null, COLUMN_DEVICE_ID + "=?", new String[]{deviceId}, null, null, null);
        boolean isBound = cursor.moveToFirst();
        cursor.close();
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
        return isBound;
    }

    // 添加位置记录
    public void addLocationRecord(LocationRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_HISTORY_DEVICE_ID, record.getDeviceId());
        values.put(COLUMN_HISTORY_LATITUDE, record.getLatitude());
        values.put(COLUMN_HISTORY_LONGITUDE, record.getLongitude());
        values.put(COLUMN_HISTORY_ACCURACY, record.getAccuracy());
        values.put(COLUMN_HISTORY_TIMESTAMP, record.getTimestamp());

        long result = db.insert(TABLE_LOCATION_HISTORY, null, values);


        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
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

        LogUtil.d("DatabaseHelper", "[DB_QUERY] Query: deviceId=" + deviceId + ", startTime=" + startTime + ", endTime=" + endTime);

        Cursor cursor = db.rawQuery(selectQuery, new String[]{
            deviceId,
            String.valueOf(startTime),
            String.valueOf(endTime)
        });

        LogUtil.d("DatabaseHelper", "[DB_QUERY] Cursor count: " + cursor.getCount());

        if (cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndex(COLUMN_HISTORY_ID));
                String devId = cursor.getString(cursor.getColumnIndex(COLUMN_HISTORY_DEVICE_ID));
                double latitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_HISTORY_LATITUDE));
                double longitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_HISTORY_LONGITUDE));
                float accuracy = cursor.getFloat(cursor.getColumnIndex(COLUMN_HISTORY_ACCURACY));
                long timestamp = cursor.getLong(cursor.getColumnIndex(COLUMN_HISTORY_TIMESTAMP));

                LocationRecord record = new LocationRecord(id, devId, latitude, longitude, accuracy, timestamp);
                recordList.add(record);
            } while (cursor.moveToNext());
        }

        cursor.close();
        
        LogUtil.d("DatabaseHelper", "[DB_QUERY] Returned records: " + recordList.size());
        if (!recordList.isEmpty()) {
            LogUtil.d("DatabaseHelper", "[DB_QUERY] First record: devId=" + recordList.get(0).getDeviceId() + ", ts=" + recordList.get(0).getTimestamp());
            LogUtil.d("DatabaseHelper", "[DB_QUERY] Last record: devId=" + recordList.get(recordList.size()-1).getDeviceId() + ", ts=" + recordList.get(recordList.size()-1).getTimestamp());
        }
        
        return recordList;
    }

    // 清理超过3个月的位置记录（离线缓存保留3个月）
    public void cleanOldLocationRecords() {
        SQLiteDatabase db = this.getWritableDatabase();
        long threeMonthsAgo = System.currentTimeMillis() - (90L * 24L * 60L * 60L * 1000L);  // 3个月
        int deletedCount = db.delete(TABLE_LOCATION_HISTORY, 
            COLUMN_HISTORY_TIMESTAMP + "<?", 
            new String[]{String.valueOf(threeMonthsAgo)});
        LogUtil.d(TAG, "Cleaned " + deletedCount + " location records older than 3 months");
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
    }
    
    // 删除所有位置记录
    public int deleteAllLocationRecords() {
        SQLiteDatabase db = this.getWritableDatabase();
        int deletedCount = db.delete(TABLE_LOCATION_HISTORY, null, null);
        LogUtil.d(TAG, "Deleted " + deletedCount + " all location records");
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
        return deletedCount;
    }
    
    // 删除所有设备
    public int deleteAllDevices() {
        SQLiteDatabase db = this.getWritableDatabase();
        int deletedCount = db.delete(TABLE_DEVICES, null, null);
        LogUtil.d(TAG, "Deleted " + deletedCount + " all devices");
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
        return deletedCount;
    }
    
    // 删除指定设备的位置记录
    public int deleteLocationRecordsByDevice(String deviceId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int deletedCount = db.delete(TABLE_LOCATION_HISTORY, 
            COLUMN_HISTORY_DEVICE_ID + "=?", 
            new String[]{deviceId});
        LogUtil.d(TAG, "Deleted " + deletedCount + " location records for device: " + deviceId);
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
        return deletedCount;
    }
    
    /**
     * 删除指定的单条位置记录（上传成功后调用）
     * @param deviceId 设备ID
     * @param timestamp 时间戳
     */
    public void deleteLocationRecord(String deviceId, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            int deletedCount = db.delete(TABLE_LOCATION_HISTORY, 
                COLUMN_HISTORY_DEVICE_ID + "=? AND " + COLUMN_HISTORY_TIMESTAMP + "=?",
                new String[]{deviceId, String.valueOf(timestamp)});
            LogUtil.d(TAG, "Deleted " + deletedCount + " location record for device: " + deviceId + ", timestamp: " + timestamp);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting location record", e);
        }
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
    }
    
    // ==================== 地址缓存方法 ====================
    
    /**
     * 从缓存中获取地址
     * @param latitude 纬度
     * @param longitude 经度
     * @param languageCode 语言代码（如：zh-CN, en等）
     * @param maxAgeMillis 最大缓存时间（毫秒），0表示不检查过期
     * @return 缓存的地址，如果不存在或已过期则返回null
     */
    /**
     * 获取缓存的地址
     * @param latitude 纬度
     * @param longitude 经度
     * @param languageCode 语言代码
     * @param maxAgeMillis 缓存最大年龄（毫秒），0表示不检查过期
     * @param mapMode 地图模式（"amap" 或 "google"）
     * @return 缓存的地址，如果没有缓存或缓存过期则返回null
     */
    public String getCachedAddress(double latitude, double longitude, String languageCode, long maxAgeMillis, String mapMode) {
        // 修复：缓存key必须包含地图模式标识，区分高德(GCJ-02)和谷歌(WGS84)的地址缓存
        String cacheKey = String.format("%s:%.6f,%.6f,%s", mapMode, latitude, longitude, languageCode);
        SQLiteDatabase db = this.getReadableDatabase();
        
        try {
            Cursor cursor = db.query(TABLE_ADDRESS_CACHE,
                new String[]{COLUMN_CACHE_ADDRESS, COLUMN_CACHE_TIMESTAMP},
                COLUMN_CACHE_KEY + "=?",
                new String[]{cacheKey},
                null, null, null);
            
            if (cursor.moveToFirst()) {
                String address = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CACHE_ADDRESS));
                long cacheTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CACHE_TIMESTAMP));
                
                // 检查缓存是否过期
                if (maxAgeMillis > 0) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - cacheTimestamp > maxAgeMillis) {
                        LogUtil.d(TAG, "Address cache expired for key: " + cacheKey);
                        cursor.close();
                        return null;
                    }
                }
                
                LogUtil.d(TAG, "Address cache hit for key: " + cacheKey + ", language: " + languageCode);
                cursor.close();
                return address;
            }
            
            cursor.close();
            LogUtil.d(TAG, "Address cache miss for key: " + cacheKey + ", language: " + languageCode);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached address: " + e.getMessage(), e);
            return null;
        }
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
    }
    
    /**
     * 保存地址到缓存（使用默认语言）
     * @param latitude 纬度
     * @param longitude 经度
     * @param mapMode 地图模式（"amap" 或 "google"）
     * @param address 地址字符串
     */
    public void saveAddressToCache(double latitude, double longitude, String mapMode, String address) {
        saveAddressToCache(latitude, longitude, "zh-CN", mapMode, address);
    }
    
    /**
     * 保存地址到缓存（指定语言）
     * @param latitude 纬度
     * @param longitude 经度
     * @param languageCode 语言代码（如：zh-CN, en等）
     * @param mapMode 地图模式（"amap" 或 "google"）
     * @param address 地址字符串
     */
    public void saveAddressToCache(double latitude, double longitude, String languageCode, String mapMode, String address) {
        if (address == null || address.isEmpty()) {
            Log.w(TAG, "Attempted to cache empty address");
            return;
        }
        
        // 修复：缓存key必须包含地图模式标识，区分高德(GCJ-02)和谷歌(WGS84)的地址缓存
        String cacheKey = String.format("%s:%.6f,%.6f,%s", mapMode, latitude, longitude, languageCode);
        SQLiteDatabase db = this.getWritableDatabase();
        
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_CACHE_KEY, cacheKey);
            values.put(COLUMN_CACHE_ADDRESS, address);
            values.put(COLUMN_CACHE_TIMESTAMP, System.currentTimeMillis());
            values.put(COLUMN_CACHE_LANGUAGE, languageCode);
            
            // 使用replace实现插入或更新
            long result = db.replace(TABLE_ADDRESS_CACHE, null, values);
            LogUtil.d(TAG, "Address cached: " + cacheKey + ", language: " + languageCode + ", result=" + result);
        } catch (Exception e) {
            Log.e(TAG, "Error saving address to cache: " + e.getMessage(), e);
        }
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
    }
    
    /**
     * 清理过期的地址缓存（超过7天）
     */
    public void cleanExpiredAddressCache() {
        SQLiteDatabase db = this.getWritableDatabase();
        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24L * 60L * 60L * 1000L);
        
        try {
            int deletedCount = db.delete(TABLE_ADDRESS_CACHE,
                COLUMN_CACHE_TIMESTAMP + "<?",
                new String[]{String.valueOf(sevenDaysAgo)});
            LogUtil.d(TAG, "Cleaned " + deletedCount + " expired address cache entries");
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning address cache: " + e.getMessage(), e);
        }
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
    }
    
    /**
     * 清理指定地图模式的地址缓存
     * @param mapMode 地图模式（"amap" 或 "google"）
     */
    public void cleanMapModeAddressCache(String mapMode) {
        if (mapMode == null || mapMode.isEmpty()) {
            Log.w(TAG, "Attempted to clean cache with null or empty map mode");
            return;
        }
        
        SQLiteDatabase db = this.getWritableDatabase();
        
        try {
            // 删除所有以该地图模式开头的缓存项
            String pattern = mapMode + ":%";
            int deletedCount = db.delete(TABLE_ADDRESS_CACHE,
                COLUMN_CACHE_KEY + " LIKE ?",
                new String[]{pattern});
            LogUtil.d(TAG, "Cleaned " + deletedCount + " address cache entries for map mode: " + mapMode);
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning address cache for map mode " + mapMode + ": " + e.getMessage(), e);
        }
        // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
    }
    
    /**
     * 执行批量操作（带事务管理）
     * @param operation 批量操作函数
     * @return 是否成功
     */
    public boolean executeBatchTransaction(BatchOperation operation) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            boolean success = operation.execute(db);
            if (success) {
                db.setTransactionSuccessful();
                LogUtil.d(TAG, "Batch transaction committed successfully");
            } else {
                Log.w(TAG, "Batch transaction rolled back");
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Error in batch transaction: " + e.getMessage(), e);
            return false;
        } finally {
            db.endTransaction();
            // 注意：SQLiteOpenHelper 内部管理连接池，不应手动关闭
        }
    }
    
    /**
     * 批量保存设备列表（使用事务）
     * @param devices 设备列表
     * @return 保存成功的数量
     */
    public int saveDevicesBatch(List<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            return 0;
        }
        
        final int[] successCount = {0};
        
        boolean success = executeBatchTransaction(db -> {
            for (Device device : devices) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_DEVICE_ID, device.getDeviceId());
                values.put(COLUMN_DEVICE_NUM, device.getDeviceNum());
                values.put(COLUMN_DEVICE_NAME, device.getName());
                values.put(COLUMN_DEVICE_MAC, device.getMac() != null ? device.getMac() : ""); // 保存MAC地址
                values.put(COLUMN_TAG, device.getTag());
                values.put(COLUMN_LATITUDE, device.getLatitude());
                values.put(COLUMN_LONGITUDE, device.getLongitude());
                values.put(COLUMN_SIGNAL_STRENGTH, device.getSignalStrength());
                values.put(COLUMN_LAST_SEEN, device.getLastSeen());
                
                long result = db.replace(TABLE_DEVICES, null, values);
                if (result != -1) {
                    successCount[0]++;
                }
            }
            return true;
        });
        
        LogUtil.d(TAG, "Batch saved " + successCount[0] + "/" + devices.size() + " devices");
        return successCount[0];
    }
    
    /**
     * 批量操作接口
     */
    public interface BatchOperation {
        boolean execute(SQLiteDatabase db);
    }
}
