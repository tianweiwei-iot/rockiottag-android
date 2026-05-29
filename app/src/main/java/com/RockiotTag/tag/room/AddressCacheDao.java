package com.RockiotTag.tag.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/**
 * 地址缓存数据访问对象
 */
@Dao
public interface AddressCacheDao {
    
    /**
     * 插入或更新地址缓存
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAddressCache(AddressCacheEntity cache);
    
    /**
     * 根据key获取地址缓存
     */
    @Query("SELECT * FROM address_cache WHERE cache_key = :cacheKey LIMIT 1")
    AddressCacheEntity getAddressCache(String cacheKey);
    
    /**
     * 清理过期的地址缓存
     */
    @Query("DELETE FROM address_cache WHERE cache_timestamp < :cutoffTime")
    int cleanExpiredAddressCache(long cutoffTime);
    
    /**
     * 清理指定地图模式的地址缓存
     */
    @Query("DELETE FROM address_cache WHERE cache_key LIKE :pattern")
    int cleanMapModeAddressCache(String pattern);
}
