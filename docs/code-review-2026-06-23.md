# RockiotTag 全项目代码审查报告

> **审查日期**：2026-06-23
> **审查范围**：Android 客户端 (`d:\Project\Android\RockiotTag`) + 后端服务 (`D:\Project\RockiotTagBackend`)
> **审查维度**：安全性、架构设计、并发线程安全、资源管理、前后端集成、数据库设计、API 一致性

---

## 目录

- [1. 问题概览](#1-问题概览)
- [2. 高危问题（P0 - 立即修复）](#2-高危问题p0---立即修复)
- [3. 中危问题（P1 - 尽快修复）](#3-中危问题p1---尽快修复)
- [4. 低危问题（P2 - 择机修复）](#4-低危问题p2---择机修复)
- [5. 修复优先级路线图](#5-修复优先级路线图)

---

## 1. 问题概览

本次审查共发现 **88 个问题**，分布如下：

| 维度 | 高危 | 中危 | 低危 | 小计 |
|------|------|------|------|------|
| 后端安全 | 5 | 7 | 6 | 18 |
| 后端 API 控制器 | 1 | 4 | 6 | 11 |
| 后端 Service 层 | 1 | 4 | 4 | 9 |
| 后端数据库/实体 | 1 | 3 | 2 | 6 |
| 后端配置 | 0 | 1 | 2 | 3 |
| Android 架构 | 2 | 4 | 0 | 6 |
| Android 并发线程 | 1 | 4 | 1 | 6 |
| Android 资源管理 | 0 | 4 | 1 | 5 |
| Android NPE 空安全 | 1 | 3 | 1 | 5 |
| Android 网络数据库 | 1 | 3 | 2 | 6 |
| 前后端集成 | 3 | 5 | 5 | 13 |
| **合计** | **16** | **41** | **30** | **88** |

---

## 2. 高危问题（P0 - 立即修复）

### 2.1 【后端安全】用户资料修改接口完全无认证保护

- **文件**：`SecurityConfig.java:41-47`
- **问题描述**：`/api/user/password`、`/api/user/username`、`/api/user/email`、`/api/user/phone`、`/api/user/avatar` 等敏感接口被配置为 `permitAll()`。注释声称"由 BearerTokenFilter 处理认证"，但 `BearerTokenFilter` 在请求**不带** `Authorization` 头时直接 `filterChain.doFilter()` 放行。**任何人无需登录即可修改任意用户密码**。
- **影响**：账户接管、密码篡改、数据泄露
- **修复建议**：

```java
// SecurityConfig.java - 移除这些 permitAll，改为需要认证
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/user/register").permitAll()
    .requestMatchers("/api/user/login").permitAll()
    .requestMatchers("/api/user/secondVerify").permitAll()
    .requestMatchers("/api/user/apikey/**").permitAll()
    .requestMatchers("/api/sms/**").permitAll()
    // 移除以下行，让其走 authenticated()
    // .requestMatchers("/api/user/password").permitAll()
    // .requestMatchers("/api/user/username").permitAll()
    // .requestMatchers("/api/user/email").permitAll()
    // .requestMatchers("/api/user/phone").permitAll()
    // .requestMatchers("/api/user/avatar").permitAll()
    .anyRequest().authenticated()
)
```

同时在 `BearerTokenFilter` 中对无 Token 的非公开请求返回 401：

```java
String authHeader = request.getHeader("Authorization");
if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"success\":false,\"message\":\"未提供认证令牌\"}");
    return;
}
```

---

### 2.2 【后端安全】JWT 密钥硬编码且未使用配置

- **文件**：`JwtUtil.java:9`
- **问题描述**：JWT 密钥直接硬编码 `"RockiotTagSecretKey2026RockiotTagSecretKey2026ExtraKey"`，完全忽略了 `application.properties` 中的 `jwt.secret` 配置。密钥泄露后攻击者可伪造任意用户令牌。
- **影响**：Token 伪造、身份冒充
- **修复建议**：

```java
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }
    // 改为实例方法，注入使用
}
```

---

### 2.3 【后端安全】短信验证码明文记录到日志

- **文件**：`SmsService.java:30`
- **问题描述**：`log.info("SMS code sent to phone: {}, type: {}, code: {}", phone, type, code)` 将验证码明文打印到日志，任何能访问日志文件的人都能获取验证码。
- **影响**：短信验证绕过
- **修复建议**：

```java
log.info("SMS code sent to phone: {}, type: {}", phone, type);
// 绝不记录验证码本身
```

---

### 2.4 【后端安全】AuthService 自动注册逻辑

- **文件**：`AuthService.java:15-33`
- **问题描述**：`login()` 方法中，如果用户不存在则**自动注册**。任何人只要调用 `/api/user/apikey/login` 传入任意 name，就能创建新账号并获取 API Key。
- **影响**：未授权账户创建
- **修复建议**：

```java
public User login(String name, String cid) {
    User user = userRepository.findByName(name);
    if (user == null) {
        throw new RuntimeException("用户不存在，请先注册");
    }
    // ... 后续逻辑
}
```

---

### 2.5 【后端安全】位置/同步/扫描日志接口无认证

- **文件**：`BearerTokenFilter.java:79-90`
- **问题描述**：`isPublicPath()` 将 `/api/locations/`、`/api/sync/`、`/api/scan-logs/` 全部视为公开路径，跳过认证。攻击者可任意查询位置历史、触发数据同步、上传伪造扫描日志。
- **影响**：位置数据泄露、数据篡改
- **修复建议**：移除这些路径的公开访问，仅保留 `/api/sms/`、`/api/user/register`、`/api/user/login` 等真正公开的端点。

---

### 2.6 【后端数据库】BluetoothScanLog 使用错误的 persistence 包

- **文件**：`BluetoothScanLog.java:3`
- **问题描述**：`import javax.persistence.*` 应为 `import jakarta.persistence.*`。Spring Boot 3 使用 Jakarta EE 命名空间，`javax.persistence` 会导致实体无法被 JPA 识别。
- **影响**：扫描日志功能完全失效
- **修复建议**：`import jakarta.persistence.*;`

---

### 2.7 【后端 Service】VendorApiService 线程安全问题

- **文件**：`VendorApiService.java:42-44,51`
- **问题描述**：`@Service` 是单例，但 `token`、`userId`、`lastErrorCode` 是普通实例变量，多线程并发访问会导致数据竞争。
- **影响**：供应商 API 调用混乱、数据错乱
- **修复建议**：使用 `volatile` 或 `AtomicReference` 管理可变状态，或改为每个请求独立认证上下文。

---

### 2.8 【Android 架构】DatabaseHelper 单例被多处 close()，导致后续使用崩溃

- **文件**：`MainActivity.java:3306-3308`、`TrackActivity.java:479-481`、`BLEForegroundService.java:77-79`
- **问题描述**：`DatabaseHelper` 是全局单例，但三个组件在 `onDestroy` 中都调用了 `databaseHelper.close()`。任一组件先销毁会关闭底层 SQLiteDatabase，其他组件再访问数据库将抛 `IllegalStateException`。
- **影响**：应用崩溃
- **修复建议**：移除所有 `databaseHelper.close()` 调用，让单例随进程生命周期存在。

```java
// MainActivity.onDestroy - 删除以下代码
// if (databaseHelper != null) {
//     databaseHelper.close();
//     databaseHelper = null;
// }
// 改为仅置空本地引用
databaseHelper = null;
```

---

### 2.9 【Android 架构】MainViewModel 使用 observeForever 且从不移除，导致内存泄漏与重复回调

- **文件**：`MainViewModel.java:197, 364, 385, 427, 483`
- **问题描述**：`fetchDeviceInfo`、`triggerBuzzer`、`selectDevice`、`getAddress`、`syncDevices` 五处均用 `observeForever`，但从未 `removeObserver`。每次调用都新增一个 Observer，导致同一请求结果被回调多次，且 LiveData 永久持有 Observer 引用造成泄漏。
- **影响**：内存泄漏、UI 重复刷新、数据错乱
- **修复建议**：在 ViewModel 中保存 Observer 引用，`onCleared` 中移除；或改用 `Transformations.switchMap`。

```java
private LiveData<Resource<DeviceInfo>> currentDeviceInfoLiveData;
private Observer<Resource<DeviceInfo>> currentDeviceInfoObserver;

public void fetchDeviceInfo(String deviceNum) {
    int currentSeq = ++fetchSequence;
    isLoading.setValue(true);
    if (currentDeviceInfoObserver != null && currentDeviceInfoLiveData != null) {
        currentDeviceInfoLiveData.removeObserver(currentDeviceInfoObserver);
    }
    currentDeviceInfoLiveData = getDeviceInfoUseCase.execute(deviceNum);
    currentDeviceInfoObserver = resource -> {
        if (currentSeq != fetchSequence) return;
        isLoading.setValue(false);
        // ...
    };
    currentDeviceInfoLiveData.observeForever(currentDeviceInfoObserver);
}

@Override
protected void onCleared() {
    super.onCleared();
    if (currentDeviceInfoLiveData != null && currentDeviceInfoObserver != null) {
        currentDeviceInfoLiveData.removeObserver(currentDeviceInfoObserver);
    }
}
```

---

### 2.10 【Android 并发】NewApiService.setApiBaseUrl 修改全局静态变量，多线程并发互相覆盖

- **文件**：`NewApiService.java:26, 32-35`
- **调用点**：`DeviceRepository.java:144`、`LocationOptimizationManager.java:1071`
- **问题描述**：`API_BASE_URL` 是 `static volatile` 字段，`setApiBaseUrl` 修改它后，所有后续请求都用这个 URL。DeviceRepository 在后台线程调用 `setApiBaseUrl`，若此时另一线程正在发请求，会用到错误的 base URL。
- **影响**：请求发到错误服务器、数据错乱
- **修复建议**：将 base URL 作为方法参数传递，而非全局静态状态。

```java
// 改为每个请求显式传入 baseUrl
public ApiResponse getRequest(String baseUrl, String endpoint, boolean requireAuth, String customerCode) {
    String url = baseUrl + endpoint;
    // ...
}
```

---

### 2.11 【Android NPE】MainViewModel.handleDeviceInfoSuccess 未判空 deviceInfo

- **文件**：`MainViewModel.java:218-219`
- **问题描述**：`handleDeviceInfoSuccess(NewApiService.DeviceInfo deviceInfo)` 直接 `deviceInfo.deviceNum`，但调用方 `resource.data` 在 `isSuccess()` 时可能为 null。一旦为 null 即 NPE。
- **影响**：应用崩溃
- **修复建议**：

```java
private void handleDeviceInfoSuccess(NewApiService.DeviceInfo deviceInfo) {
    if (deviceInfo == null) {
        Log.e(TAG, "deviceInfo is null");
        errorMessage.setValue("设备信息为空");
        return;
    }
    // ...
}
```

---

### 2.12 【Android 网络】HttpHelper 将 API Key 明文写入日志

- **文件**：`HttpHelper.java:51`
- **问题描述**：`LogUtil.d(TAG, "GET request API Key: " + apiKey)` 将 API Key 完整输出到 logcat。任何持有 READ_LOGS 权限的应用可读取。
- **影响**：API Key 泄露
- **修复建议**：移除敏感信息日志，或仅打印 key 的前 4 位 + `***`。

---

### 2.13 【集成】UserApiService.updateNickname 调用不存在的后端端点（404）

- **文件**：`UserApiService.java:140-142`、`EditProfileActivity.java:102`
- **问题描述**：Android 端请求 `/api/user/nickname`，但后端 `AccountController` 仅有 `@PutMapping("/username")`，不存在 `/nickname` 端点。用户修改昵称时静默失败（404）。
- **影响**：用户修改昵称功能完全失效
- **修复建议**：后端增加 `@PutMapping("/nickname")` 端点并在 `UserAccount` 实体增加 `nickname` 字段；或 Android 端改为调用 `/user/username`。

---

### 2.14 【集成】createdAt 日期格式处理丢失实际绑定时间

- **文件**：`DeviceApiService.java:145-154`、`UserDeviceService.java:81`
- **问题描述**：后端 `UserDevice.createdAt` 是 `Date` 类型，Spring Boot 默认 Jackson 将其序列化为 ISO 8601 字符串。Android 端解析时，若 `createdAt` 不是数字则直接使用 `System.currentTimeMillis()`，导致绑定时间永远显示为当前时间。
- **影响**：绑定时间数据丢失
- **修复建议**：Android 端增加 ISO 8601 日期字符串解析逻辑；或后端将 `createdAt` 转为 `Long` 时间戳返回。

```java
// Android 端解析逻辑
if (deviceObj.get("createdAt").isJsonPrimitive()) {
    if (deviceObj.get("createdAt").getAsJsonPrimitive().isNumber()) {
        device.bindTime = deviceObj.get("createdAt").getAsLong();
    } else {
        // ISO 8601 字符串解析
        String dateStr = deviceObj.get("createdAt").getAsString();
        try {
            device.bindTime = java.time.Instant.parse(dateStr).toEpochMilli();
        } catch (Exception e) {
            // 兼容其他格式
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
                device.bindTime = sdf.parse(dateStr).getTime();
            } catch (Exception ignored) {
                device.bindTime = System.currentTimeMillis();
            }
        }
    }
}
```

---

### 2.15 【集成】双绑定系统数据不一致（UserDevice 表与 Device 表）

- **文件**：`AddDeviceViewModel.java:261-300`、`UserDeviceService.java:30-58`、`DeviceController.java:179-193`
- **问题描述**：存在两套独立的设备绑定系统：
  1. `/api/device/bind`（`AccountDeviceController`）→ 写入 `UserDevice` 表，**不更新 `Device.userId`**。
  2. `/api/devices/bind`（`DeviceController`）→ 写入 `Device.userId`。

  Android 绑定设备时仅调用系统1，导致 `DeviceController.checkDeviceOwnership`（校验 `device.getUserId() == userId`）失败，设备历史/最新位置等接口返回"无权访问"。
- **影响**：设备位置/轨迹接口 403
- **修复建议**：后端 `UserDeviceService.bindDevice` 在创建 `UserDevice` 记录时，同步更新 `Device.userId`。

```java
@Transactional
public UserDevice bindDevice(Long userAccount, String deviceNum, String nickName) {
    UserDevice existing = userDeviceRepository.findByUserAccountAndDeviceNum(userAccount, deviceNum);
    if (existing != null) {
        return existing;
    }
    UserDevice userDevice = new UserDevice();
    userDevice.setUserAccount(userAccount);
    userDevice.setDeviceNum(deviceNum);
    UserDevice saved = userDeviceRepository.save(userDevice);

    // 同步更新 Device.userId
    Device device = deviceRepository.findByDeviceNum(deviceNum);
    if (device != null) {
        device.setUserId(userAccount.intValue());
        if (nickName != null && !nickName.isEmpty()) {
            device.setNickName(nickName);
        }
        deviceRepository.save(device);
    }
    return saved;
}
```

---

### 2.16 【Android 架构】MainActivity.onCreate 设置全局空 UncaughtExceptionHandler，吞掉所有崩溃

- **文件**：`MainActivity.java:207-208`
- **问题描述**：`Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {});` 设置了全局空异常处理器，所有未捕获异常被静默吞掉，掩盖严重 bug。
- **影响**：崩溃无法被捕获和定位
- **修复建议**：删除该全局空处理器；若需防崩溃，应仅捕获主线程异常并交由崩溃上报 SDK 处理。

---

## 3. 中危问题（P1 - 尽快修复）

### 3.1 后端安全问题

| 编号 | 文件 | 问题 | 修复建议 |
|------|------|------|----------|
| S6 | `WebConfig.java:12` | CORS 允许所有来源 `allowedOrigins("*")` | 限制为前端域名 |
| S7 | `ApiKeyAuthenticationFilter.java:53,67,84` | API Key 明文记日志 | 脱敏处理 |
| S8 | `application.properties:10,16,46` | 硬编码敏感凭证默认值 | 移除默认值，强制环境变量 |
| S9 | `AuthController.java:18,25,56` | 直接返回 User 实体暴露 token/apiKey | 使用 DTO 或 @JsonIgnore |
| S10 | `UserAccountService.java:30,171` | 密码无复杂度校验 | 添加最小长度+字母数字要求 |
| S11 | `UserAccountService.java:94-102` | validateToken 不验证 JWT 签名 | 先验签名再查 DB |
| S12 | `application.properties:14` | 数据库 useSSL=false | 生产环境启用 SSL |

### 3.2 后端 API 控制器问题

| 编号 | 文件 | 问题 | 修复建议 |
|------|------|------|----------|
| C1 | `LocationController.java:23-72` | LocationController 完全无认证 | 添加认证检查 |
| C2 | `DeviceController.java:56-77` | getDevices 无认证返回空列表 | 未认证返回 401 |
| C3 | `AccountController.java:23,51,82` | 使用 Map 接收参数无校验 | 定义 DTO + @Valid |
| C4 | `AccountController.java:249-253` | 异常处理返回错误状态码 | 区分认证异常和系统异常 |
| C5 | `ScanLogController.java:35` | 无认证无大小限制 | 添加认证+限制大小+saveAll |

### 3.3 后端 Service 层问题

| 编号 | 文件 | 问题 | 修复建议 |
|------|------|------|----------|
| V2 | `UserDeviceService.java:66-96` | getBoundDevices N+1 查询 | 批量查询 `findByDeviceNumIn` |
| V3 | `DeviceService.java:59-66` | unbindDevice 破坏性删除设备 | 仅解除关联，不删除设备 |
| V4 | `VendorDeviceBindService.java:55-157` | 长事务持有 DB 连接（含 HTTP+sleep） | 外部调用移出事务 |
| V5 | `LocationSyncService.java:72-73` | 死代码 + Thread.sleep 阻塞 | 移除死代码，改异步轮询 |

**N+1 查询修复示例**：

```java
public List<Map<String, Object>> getBoundDevices(Long userAccount) {
    List<UserDevice> userDevices = userDeviceRepository.findByUserAccount(userAccount);

    // 批量查询设备，避免 N+1
    List<String> deviceNums = userDevices.stream()
        .map(UserDevice::getDeviceNum)
        .collect(Collectors.toList());
    List<Device> devices = deviceRepository.findByDeviceNumIn(deviceNums);
    Map<String, Device> deviceMap = devices.stream()
        .collect(Collectors.toMap(Device::getDeviceNum, d -> d));

    UserAccount account = userAccountRepository.findById(userAccount).orElse(null);
    String username = account != null ? account.getUsername() : null;
    String phone = account != null ? account.getPhone() : null;

    return userDevices.stream().map(ud -> {
        Map<String, Object> map = new HashMap<>();
        map.put("id", ud.getId());
        map.put("userAccount", ud.getUserAccount());
        map.put("username", username);
        map.put("phone", phone);
        map.put("deviceNum", ud.getDeviceNum());
        map.put("createdAt", ud.getCreatedAt() != null ? ud.getCreatedAt().getTime() : null);

        Device device = deviceMap.get(ud.getDeviceNum());
        if (device != null) {
            map.put("sn", device.getSn());
            map.put("mac", device.getMac());
            map.put("nickName", device.getNickName());
            map.put("latitude", device.getLatitude());
            map.put("longitude", device.getLongitude());
            map.put("battery", device.getBattery());
            map.put("address", device.getAddress());
            map.put("timestamp", device.getTimestamp());
        }
        return map;
    }).collect(Collectors.toList());
}
```

### 3.4 后端数据库问题

| 编号 | 文件 | 问题 | 修复建议 |
|------|------|------|----------|
| D2 | `LocationRecordRepository.java:36-40` | JPQL 使用非标准 LIMIT | 改用 `findFirstBy` 命名查询 |
| D3 | `DeviceHistoryRepository.java:15,22` | 同上 LIMIT 问题 | 同上 |
| D4 | `LocationRecord.java`, `Device.java` | 关键表缺少索引 | 添加索引注解 |

**索引添加示例**：

```java
@Entity
@Table(name = "location_records", indexes = {
    @Index(name = "idx_device_timestamp", columnList = "device_num, timestamp"),
    @Index(name = "idx_vendor_record_id", columnList = "vendor_record_id", unique = true)
})
public class LocationRecord { ... }

@Entity
@Table(name = "devices", indexes = {
    @Index(name = "idx_device_num", columnList = "device_num", unique = true)
})
public class Device { ... }
```

### 3.5 Android 架构问题

| 编号 | 文件 | 问题 | 修复建议 |
|------|------|------|----------|
| A3 | `DeviceApiService.java:26-31` | 单例非线程安全 | volatile + synchronized |
| A4 | `BLERepository.java:18-25` | instance 缺 volatile | 添加 volatile |
| A5 | `MainActivity.java:207-208` | 全局空 UncaughtExceptionHandler | 删除（见 2.16） |
| A6 | `MainActivity.java:68` | 静态变量 pendingTabSwitch 持 UI 状态 | 改用 Intent extra |

### 3.6 Android 并发问题

| 编号 | 文件 | 问题 | 修复建议 |
|------|------|------|----------|
| T8 | `TrackActivity.java:133-136` | HashMap 多线程访问 | 改用 ConcurrentHashMap |
| T9 | `TrackActivity.java:73-74` | 静态 threadPool 从不 shutdown | 改实例字段，onDestroy shutdown |
| T10 | `LocationOptimizationManager.java:159-161` | 同步集合迭代未加锁 | synchronized 块包裹迭代 |
| T11 | `DeviceListFragment.java:117-152` | 后台线程访问 requireContext() | 主线程预先获取 Context |

**同步集合迭代修复示例**：

```java
// LocationOptimizationManager.java
String firstMac;
synchronized (boundDeviceIds) {
    if (!boundDeviceIds.isEmpty()) {
        firstMac = boundDeviceIds.iterator().next();
    } else {
        return;
    }
}
```

### 3.7 Android 资源管理问题

| 编号 | 文件 | 问题 | 修复建议 |
|------|------|------|----------|
| R13 | `HttpHelper.java:237-252` | BufferedReader 未 try-with-resources | 改用 try-with-resources |
| R14 | `AMapGeocoder.java:29-37` | 持有 Activity Context | 改用 applicationContext |
| R15 | `MainActivity.java:461,677,936` | 匿名 Handler postDelayed 未移除 | 统一使用 SafeHandler |
| R16 | `TrackActivity.java:452-476` | onDestroy NPE 被空 catch 吞 | 判空 + 记录日志 |

### 3.8 Android NPE 问题

| 编号 | 文件 | 问题 | 修复建议 |
|------|------|------|----------|
| N19 | `MainViewModel.java:388-390` | selectDevice 未判空 resource.data | `if (resource.isSuccess() && resource.data != null)` |
| N20 | `MainActivity.java:632-639` | updateBottomInfo 未判空 TextView | setText 前判空 |
| N21 | `MainAuthHelper.java:55-106` | 未检查 isFinishing 即更新 UI | `if (activity.isFinishing() \|\| activity.isDestroyed()) return;` |

### 3.9 前后端集成问题

| 编号 | 问题 | 修复建议 |
|------|------|----------|
| I4 | 登录响应字段不匹配（nickname/email 缺失） | 后端登录响应增加 email 字段 |
| I5 | 用户资料接口字段不匹配 | 对齐字段 |
| I6 | BoundDevice 缺失服务器返回的多项字段 | 增加 mac/latitude/longitude/battery 等字段 |
| I7 | Token 失效（401）未专门处理 | 检测 401 清除 token 跳转登录 |
| I8 | 双绑定系统不同步（见 2.15） | 同步更新 Device.userId |

---

## 4. 低危问题（P2 - 择机修复）

### 4.1 后端低危问题

| 编号 | 文件 | 问题 |
|------|------|------|
| S13 | `SmsService.java:46-48` | 验证码用 Math.random()，改用 SecureRandom |
| S14 | `SmsController.java:17-33` | 短信发送无频率限制 |
| S15 | `UserAccountService.java:50-62` | 登录无暴力破解防护 |
| S16 | `GlobalExceptionHandler.java:37,100` | 异常详情泄露给客户端 |
| S17 | `DataSyncController.java:215` | 返回服务器文件系统路径 |
| S18 | `SecurityConfig.java:23` | CSRF 禁用未做说明 |
| C6 | `DeviceController.java:113,146` | 缩进不一致 |
| C7 | `DeviceController.java:133-140` | refreshDevice 死代码 try-catch |
| C8 | `LocationController.java:64-65` | 使用 System.out.println |
| C9 | `DeviceController.java:195-207` | 鉴权失败返回空列表而非 403 |
| C10 | `DataSyncController.java:47,75` | token 解析不严谨 |
| C11 | `DataSyncController.java:226-243` | unbindVendorDevice 不验证 token |
| V6 | `UserAccountService.java:74-92` | completeLogin 踢出所有设备 |
| V7 | `UserAccountService.java:94-102` | validateToken 每次请求写 DB |
| V8 | `DataSyncService.java:268-283` | 使用 new Thread 非线程池 |
| V9 | `DataSyncService.java:333,388` | NPE 风险 |
| D5 | `V1__baseline_existing_schema.sql` | Flyway 迁移脚本不完整 |
| D6 | `Device.java:38-40` | 缺少 @PrePersist |
| P2 | `pom.xml:88-98` | 含未使用依赖 |
| P3 | `pom.xml:76-79` | JAXB 用旧命名空间 |

### 4.2 Android 低危问题

| 编号 | 文件 | 问题 |
|------|------|------|
| T12 | `DatabaseHelper.java:271-333` | 查询+更新+验证未事务化 |
| R17 | `BLEManager.java:259-261` 等 | 空 catch 块 |
| N22 | `DeviceListFragment.java:483-489` | Adapter bind 频繁读 SharedPreferences |
| N24 | `NewApiService.java:356` | deviceNum 未 URL 编码 |
| N25 | `DatabaseHelper.java:116-121` | onUpgrade DROP 表丢数据 |
| N26 | `HttpHelper.java` | 无重试机制 |
| N27 | `NewApiService.java:13-18` | 未使用的 import |
| N28 | `LocationOptimizationManager.java:673-689` | 逆地理编码空实现 |

### 4.3 集成低危问题

| 编号 | 问题 |
|------|------|
| I9 | parseResponse 不解析 success 字段 |
| I10 | 登录未传递 device_id 和 device_info |
| I11 | 设备昵称本地编辑不同步服务器 |
| I12 | 401 错误响应格式不一致 |
| I13 | GlobalExceptionHandler 与 Controller 异常处理冲突 |

---

## 5. 修复优先级路线图

### 第一阶段：P0 高危（立即修复）

**后端**：
1. 修复 SecurityConfig 认证绕过（2.1）— 移除敏感接口的 permitAll
2. JWT 密钥改为配置注入（2.2）
3. 移除短信验证码日志（2.3）
4. 移除 AuthService 自动注册（2.4）
5. 移除位置/同步接口的公开访问（2.5）
6. 修复 BluetoothScanLog persistence 包（2.6）
7. 修复 VendorApiService 线程安全（2.7）

**Android**：
8. 移除 DatabaseHelper.close() 调用（2.8）
9. 修复 MainViewModel observeForever 泄漏（2.9）
10. 修复 NewApiService 全局静态竞态（2.10）
11. 修复 handleDeviceInfoSuccess NPE（2.11）
12. 移除 API Key 日志（2.12）
13. 删除全局空 UncaughtExceptionHandler（2.16）

**集成**：
14. 修复 updateNickname 404（2.13）
15. 修复 createdAt 日期解析（2.14）
16. 修复双绑定系统不同步（2.15）

### 第二阶段：P1 中危（尽快修复）

**后端**：
- CORS 限制域名、API Key 脱敏、密码复杂度校验、JWT 签名验证
- LocationController 添加认证、参数校验 DTO 化
- N+1 查询优化、unbindDevice 非破坏性、长事务拆分
- 数据库索引添加、JPQL LIMIT 修复

**Android**：
- 单例线程安全修复（DeviceApiService、BLERepository）
- ConcurrentHashMap 替换 HashMap、线程池 shutdown
- try-with-resources、Context 泄漏修复
- NPE 判空保护、isFinishing 检查

**集成**：
- 登录响应字段对齐、BoundDevice 字段补全
- 401 Token 失效处理

### 第三阶段：P2 低危（择机修复）

- 后端：短信限流、暴力破解防护、异常详情屏蔽
- Android：URL 编码、数据库迁移保护、重试机制
- 集成：success 字段解析、设备信息传递、昵称同步

---

## 附录：已正确实现的部分

以下方面经审查确认实现正确：

1. **Bearer Token 认证传递**：Android `HttpHelper.getWithAuth/postWithAuth/putWithAuth` 正确添加 `Authorization: Bearer <token>` 头
2. **设备绑定/解绑/列表主路径**：`/api/device/bind`、`/api/device/list`、`/api/device/unbind` 路径与 HTTP 方法均匹配
3. **请求字段名 `deviceNum`/`nickName`**：绑定接口两端字段名一致（驼峰）
4. **`devices` 数组键名**：后端返回 `devices`，Android 优先解析 `devices` 键，匹配正确
5. **登录/注册/登出主路径**：路径匹配
6. **HTTP 超时设置**：Android 端连接超时 10s、读取超时 30s，合理
7. **错误响应体读取**：`HttpHelper.executeRequest` 在非 2xx 时读取 `getErrorStream()`
8. **LogUtil 日志封装**：Release 构建自动关闭 Log.d/v/i
9. **IMapAdapter 适配器模式**：正确抽象高德/Google 双地图引擎
10. **ProGuard 代码混淆**：minifyEnabled true + shrinkResources true 已启用

---

*文档结束*
