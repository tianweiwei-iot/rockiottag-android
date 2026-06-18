# RockiotTag ProGuard Rules

# ========================
# 日志移除配置
# ========================
# 移除 Log.d/v/i 调用（Release 构建优化）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ========================
# 通用配置
# ========================
# 不优化
-dontoptimize
# 混淆时不预校验
-dontpreverify
# 忽略警告
-ignorewarnings

# 保持异常类不被混淆
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,*Annotation*,SourceFile,LineNumberTable

# ========================
# Android 基础类
# ========================
# 保持 Activity/Fragment 等组件不被混淆
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# 保持 Application 类
-keep public class * extends android.app.Application

# ========================
# ViewModel 和 LiveData
# ========================
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# ========================
# Room 数据库
# ========================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ========================
# Gson 序列化
# ========================
# 保持 Gson 序列化的数据类
-keep class com.RockiotTag.tag.model.** { *; }
-keep class com.RockiotTag.tag.DeviceApiService$BoundDevice { *; }
-keep class com.RockiotTag.tag.NewApiService$DeviceInfo { *; }
-keep class com.RockiotTag.tag.NewApiService$ApiResponse { *; }
-keep class com.RockiotTag.tag.DeviceApiService$DeviceApiResponse { *; }

# 保持 @SerializedName 注解
-keepattributes Signature
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ========================
# 高德地图 SDK
# ========================
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.a.a.** { *; }
-keep class com.loc.** { *; }

# ========================
# Google Maps SDK
# ========================
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.gms.location.** { *; }

# ========================
# ZXing 条码扫描
# ========================
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

# ========================
# Google ML Kit
# ========================
-keep class com.google.mlkit.** { *; }

# ========================
# CameraX
# ========================
-keep class androidx.camera.** { *; }

# ========================
# Glide 图片加载
# ========================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder { *** rewind(); }

# ========================
# 项目自有类
# ========================
# 保持 Device 类（用于数据库和 Gson）
-keep class com.RockiotTag.tag.Device { *; }
-keep class com.RockiotTag.tag.DatabaseHelper { *; }

# 保持 Helper 类的 public 方法
-keep class com.RockiotTag.tag.helper.** { public *; }
-keep class com.RockiotTag.tag.util.** { public *; }

# 保持 UseCase 类
-keep class com.RockiotTag.tag.usecase.** { *; }
-keep class com.RockiotTag.tag.repository.** { *; }

# 保持 ViewModel 类
-keep class com.RockiotTag.tag.viewmodel.** { *; }
