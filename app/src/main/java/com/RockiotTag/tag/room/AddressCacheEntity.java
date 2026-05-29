package com.RockiotTag.tag.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Room地址缓存实体类
 */
@Entity(tableName = "address_cache")
public class AddressCacheEntity {
    
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "cache_key")
    private String cacheKey;
    
    @ColumnInfo(name = "address")
    private String address;
    
    @ColumnInfo(name = "cache_timestamp")
    private long cacheTimestamp;
    
    @ColumnInfo(name = "language")
    private String language;
    
    public AddressCacheEntity() {
    }
    
    @Ignore
    public AddressCacheEntity(@NonNull String cacheKey, String address, 
                             long cacheTimestamp, String language) {
        this.cacheKey = cacheKey;
        this.address = address;
        this.cacheTimestamp = cacheTimestamp;
        this.language = language;
    }
    
    // Getters and Setters
    @NonNull
    public String getCacheKey() {
        return cacheKey;
    }
    
    public void setCacheKey(@NonNull String cacheKey) {
        this.cacheKey = cacheKey;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public long getCacheTimestamp() {
        return cacheTimestamp;
    }
    
    public void setCacheTimestamp(long cacheTimestamp) {
        this.cacheTimestamp = cacheTimestamp;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
}
