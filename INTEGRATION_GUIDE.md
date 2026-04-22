# RockiotTag Android应用集成指南

## 📱 已完成的工作

### 1. 后端服务器部署 ✅
- Spring Boot 3.2.0 后端已部署到云服务器
- MySQL 9.6.0 数据库已配置
- JWT认证已实现
- 所有API端点已测试通过：
  - POST /api/user/login - 用户登录
  - POST /api/user/register - 用户注册
  - GET /api/devices - 获取设备列表
  - POST /api/devices/bind - 绑定设备
  - POST /api/devices/unbind - 解绑设备
  - POST /api/locations/sync - 同步位置数据
  - GET /api/locations - 获取位置数据

### 2. Android应用集成 ✅
- 创建了NewApiService.java - 适配Spring Boot后端
- 创建了SharedPreferencesManager.java - 管理JWT令牌
- 创建了LoginActivity.java - 登录页面
- 创建了activity_login.xml - 登录页面布局
- 更新了AndroidManifest.xml - 添加LoginActivity

## 🔧 接下来需要完成的步骤

### 步骤1：修改服务器IP地址

**在NewApiService.java中修改API_BASE_URL：**

```java
// 文件位置：d:\Android\RockiotTag\app\src\main\java\com\RockiotTag\tag\NewApiService.java
// 第22行

private static final String API_BASE_URL = "http://你的服务器IP:8080/api";
```

**替换为您的云服务器公网IP地址：**
- 如果使用Nginx反向代理（80端口）：`http://你的服务器IP/api`
- 如果直接访问（8080端口）：`http://你的服务器IP:8080/api`

### 步骤2：修改SplashActivity启动逻辑

**在SplashActivity.java中添加登录检查：**

```java
// 文件位置：d:\Android\RockiotTag\app\src\main\java\com\RockiotTag\tag\SplashActivity.java

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    // 加载保存的认证信息
    SharedPreferencesManager.loadAuth(this);
    
    // 检查是否已登录
    if (SharedPreferencesManager.isAuthenticated(this)) {
        // 已登录，跳转到MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    } else {
        // 未登录，跳转到LoginActivity
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
    }
    
    finish();
}
```

### 步骤3：在MainActivity中集成NewApiService

**在MainActivity.java中替换ApiService为NewApiService：**

```java
// 文件位置：d:\Android\RockiotTag\app\src\main\java\com\RockiotTag\tag\MainActivity.java

// 替换第63行
// private ApiService apiService;
private NewApiService apiService;

// 在onCreate方法中初始化
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    // 检查登录状态
    if (!SharedPreferencesManager.isAuthenticated(this)) {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
        return;
    }
    
    // 加载认证信息
    SharedPreferencesManager.loadAuth(this);
    apiService = NewApiService.getInstance();
    
    // ... 其他初始化代码
}
```

### 步骤4：修改设备绑定功能

**在AddDeviceActivity.java中使用NewApiService：**

```java
// 文件位置：d:\Android\RockiotTag\app\src\main\java\com\RockiotTag\tag\AddDeviceActivity.java

private void bindDevice(String deviceNum, String sn, String nickName) {
    new Thread(new Runnable() {
        @Override
        public void run() {
            NewApiService apiService = NewApiService.getInstance();
            NewApiService.ApiResponse response = apiService.bindDevice(deviceNum, sn, nickName);
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (response != null && response.isSuccess()) {
                        Toast.makeText(AddDeviceActivity.this, "设备绑定成功", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(AddDeviceActivity.this, "设备绑定失败", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }).start();
}
```

### 步骤5：修改位置同步功能

**在LocationManager.java中添加位置同步到服务器：**

```java
// 文件位置：d:\Android\RockiotTag\app\src\main\java\com\RockiotTag\tag\LocationManager.java

public void syncLocationToServer(String deviceNum, double latitude, double longitude, int battery) {
    new Thread(new Runnable() {
        @Override
        public void run() {
            NewApiService apiService = NewApiService.getInstance();
            long timestamp = System.currentTimeMillis();
            
            NewApiService.ApiResponse response = apiService.syncLocation(
                deviceNum, latitude, longitude, battery, timestamp
            );
            
            if (response != null && response.isSuccess()) {
                Log.d(TAG, "Location synced to server successfully");
            } else {
                Log.e(TAG, "Failed to sync location to server");
            }
        }
    }).start();
}
```

### 步骤6：添加网络权限（已完成）

**AndroidManifest.xml中已添加：**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 步骤7：配置网络安全（已完成）

**res/xml/network_security_config.xml已配置：**
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

## 🧪 测试步骤

### 1. 测试用户登录
1. 启动应用
2. 输入用户名和CID
3. 点击"登录"按钮
4. 验证是否成功登录并跳转到MainActivity

### 2. 测试设备绑定
1. 在MainActivity中点击"添加设备"
2. 输入设备编号、序列号和昵称
3. 点击"绑定"按钮
4. 验证设备是否成功绑定

### 3. 测试位置同步
1. 在MainActivity中获取位置
2. 验证位置数据是否同步到服务器
3. 在服务器上查询位置数据

### 4. 测试位置获取
1. 在TrackActivity中查看历史轨迹
2. 验证是否能够正确显示位置数据

## 📝 注意事项

### 1. 服务器IP地址
- 确保使用正确的服务器公网IP地址
- 如果使用域名，请确保域名已解析到服务器IP
- 如果使用HTTPS，请修改API_BASE_URL为https://

### 2. 网络安全
- 生产环境建议使用HTTPS
- 配置SSL证书
- 修改数据库密码

### 3. JWT令牌管理
- 令牌有效期为24小时
- 令牌过期后需要重新登录
- 可以实现令牌自动刷新功能

### 4. 错误处理
- 添加网络错误处理
- 添加服务器错误处理
- 添加用户友好的错误提示

## 🚀 下一步优化

### 1. 功能优化
- 实现令牌自动刷新
- 添加离线数据缓存
- 实现数据同步机制
- 添加推送通知功能

### 2. 性能优化
- 使用Retrofit替代HttpURLConnection
- 实现图片加载优化
- 添加数据分页加载
- 优化内存使用

### 3. 用户体验优化
- 添加加载动画
- 优化UI界面
- 添加帮助文档
- 实现多语言支持

## 📞 技术支持

如果在集成过程中遇到问题，请检查：
1. 服务器是否正常运行
2. 网络连接是否正常
3. API地址是否正确
4. JWT令牌是否有效
5. 数据库连接是否正常

## 🎉 完成标志

当以下功能全部测试通过时，集成工作即完成：
- ✅ 用户登录成功
- ✅ 设备绑定成功
- ✅ 位置同步成功
- ✅ 位置获取成功
- ✅ 数据正确显示

祝您集成顺利！
