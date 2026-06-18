package com.RockiotTag.tag.room;

import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * 数据库迁移：版本1 -> 版本2
 * 添加accuracy字段到location_history表
 */
public class Migration_1_2 extends Migration {
    private static final String TAG = "Migration_1_2";
    
    public Migration_1_2() {
        super(1, 2);
    }
    
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        LogUtil.d(TAG, "Migrating database from version 1 to 2: Adding accuracy column");
        
        // 添加accuracy列，默认值为50.0（BLE Tag典型精度）
        database.execSQL("ALTER TABLE location_history ADD COLUMN accuracy REAL NOT NULL DEFAULT 50.0");
        
        LogUtil.d(TAG, "Migration completed successfully");
    }
}
