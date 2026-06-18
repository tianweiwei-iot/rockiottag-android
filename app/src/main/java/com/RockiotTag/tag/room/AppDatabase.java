package com.RockiotTag.tag.room;

import android.content.Context;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room数据库
 */
@Database(
    entities = {
        DeviceEntity.class,
        LocationRecordEntity.class,
        AddressCacheEntity.class
    },
    version = 2,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    
    private static final String TAG = "AppDatabase";
    private static final String DATABASE_NAME = "rockiottag_room.db";
    
    private static volatile AppDatabase INSTANCE;
    
    public abstract DeviceDao deviceDao();
    public abstract LocationRecordDao locationRecordDao();
    public abstract AddressCacheDao addressCacheDao();
    
    /**
     * 获取数据库实例（单例）
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                        )
                        .addMigrations(new Migration_1_2())
                        .addCallback(new Callback() {
                            @Override
                            public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                super.onCreate(db);
                                LogUtil.d(TAG, "Room database created");
                            }
                            
                            @Override
                            public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                super.onOpen(db);
                                LogUtil.d(TAG, "Room database opened");
                            }
                        })
                        .allowMainThreadQueries() // 临时允许主线程查询，后续应改为异步
                        .build();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 销毁数据库实例（用于测试或重置）
     */
    public static void destroyInstance() {
        if (INSTANCE != null && INSTANCE.isOpen()) {
            INSTANCE.close();
        }
        INSTANCE = null;
    }
}
