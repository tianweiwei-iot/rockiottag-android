# RockiotTag 全栈优化建议文档

> 生成日期：2026-06-25
> 范围：Android 客户端（`d:\Project\Android\RockiotTag`）+ Spring Boot 后端（`D:\Project\RockiotTagBackend`）
> 方法：静态代码审查 + 前后端契约对照

---

## 一、总体架构概览

| 层 | 技术栈 | 关键问题 |
|----|--------|----------|
| Android 客户端 | Java 11、AGP 8.13、AMap + Google Maps、BLE、SQLite + Room（未启用）、HttpURLConnection | God Activity、双数据库、无 DI、无线程池、BLE 占位 UUID |
| 后端 | Spring Boot 3.2 / Java 17 / MySQL / JPA / JWT + API Key / 3DES | 硬编码密钥、JWT 不校验签名/过期、IDOR、`Thread.sleep` 阻塞 Servlet 线程、N+1 查询 |
| 通信 | REST + 厂商 API 代理（`device.vernal.ltd/tagapi`） | 无 WebSocket/MQTT、无 API 版本、无分页、错误响应格式不统一 |

---

## 二、严重问题（P0 — 必须立即修复）

### 2.1 后端：JWT 校验跳过签名与过期检查

**位置**：`D:\Project\RockiotTagBackend\src\main\java\com\rockiot\tag\service\UserAccountService.java:97-105`

`validateToken()` 仅在 `user_token` 表中查询 token 字符串是否存在，**从不解析 JWT**。过期 token 在被显式删除前一直有效。

**建议**：
```java
public boolean validateToken(String token) {
    try {
        Jwts.parser().verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(token);
        return userTokenRepository.existsByToken(token); // 表存在 + 签名有效 + 未过期
    } catch (JwtException e) {
        return false;
    }
}
```

### 2.2 后端：3DES/ECB 加密已废弃

**位置**：`D:\Project\RockiotTagBackend\src\main\java\com\rockiot\tag\util\VendorCryptoUtil.java:25,41`

`DESede/ECB/PKCS5Padding` — 3DES 密钥短、块小；ECB 模式泄露明文模式。

**建议**：迁移到 `AES/GCM/NoPadding`，密钥从环境变量注入，IV 每次随机生成并与密文一同传输。

### 2.3 后端：硬编码生产密钥默认值

**位置**：`D:\Project\RockiotTagBackend\src\main\resources\application.properties:10,16,46,51,56`

`Root@123456`、`RockiotTagSecretKey2026`、`123456`、`tutnpnwq`、`6h7lMJOVpVOld5R9CApqH6coCR1W8iqL` 等默认值已提交到代码库。

**建议**：
- 删除所有 `${ENV:默认值}` 中的默认值，改为 `${ENV}`（缺失时启动失败）。
- 使用 Spring Cloud Config / Vault / Kubernetes Secrets 管理。
- 立即轮换所有已泄露的密钥（DB 密码、JWT secret、厂商 cid、SSL keystore 密码）。

### 2.4 后端：IDOR — 位置读写无归属校验

**位置**：`D:\Project\RockiotTagBackend\src\main\java\com\rockiot\tag\controller\LocationController.java:26-75`

POST `/api/locations/sync` 与 GET `/api/locations` 从 token 取 userId，但**不校验 `deviceNum` 是否属于该用户**。任何已登录用户可读写任意设备位置。

**建议**：在 `LocationService` 调用前注入 `UserDeviceService.checkOwnership(userId, deviceNum)`，未通过返回 403。

### 2.5 后端：IDOR — 厂商解绑无鉴权

**位置**：`D:\Project\RockiotTagBackend\src\main\java\com\rockiot\tag\controller\DataSyncController.java:229-246`

`/api/sync/unbindVendorDevice` 仅检查 `Authorization` 头非空，**不验证 token、不校验设备归属**。

**建议**：强制走 `BearerTokenFilter`，并加 `checkDeviceOwnership`。

### 2.6 后端：permitAll 端点泄露厂商 token

**位置**：`D:\Project\RockiotTagBackend\src\main\java\com\rockiot\tag\controller\AuthController.java:25,35,47-68`

`/api/user/apikey/login`、`/register`、`/by-api-key` 直接返回 `User` 实体，包含 `cid`、`token`（厂商 JWT）、`apiKey`。`/by-api-key` 为 `permitAll`，猜到 API Key 即可获取厂商 token。

**建议**：
- 用 DTO 替代实体返回，仅暴露必要字段。
- `/by-api-key` 改为 `authenticated()` + 管理员角色。
- 厂商 token 不应下发到客户端，应由后端代理调用厂商 API。

### 2.7 后端：SMS 验证码可暴力破解

**位置**：`D:\Project\RockiotTagBackend\src\main\java\com\rockiot\tag\service\SmsService.java:19-36,50-52`

`sendCode` 永远返回 `true` 但**从不真正发送短信**；`Math.random()` 生成 6 位码；`/api/user/secondVerify` 无频率限制。

**建议**：
- 接入真实 SMS 网关（阿里云/腾讯云）。
- 用 `SecureRandom` 替代 `Math.random`。
- 加 Redis 限流：同一手机号 60s 内 1 次、同一 IP 10 次/小时。
- 验证码错误 5 次锁定。

### 2.8 客户端：BLE 蜂鸣器使用占位 UUID

**位置**：`d:\Project\Android\RockiotTag\app\src\main\java\com\RockiotTag\tag\BLEManager.java:450-451`

`"YOUR_CUSTOM_SERVICE_UUID"` / `"YOUR_BUZZER_CHARACTERISTIC_UUID"` — 功能不可用，已发布到生产。

**建议**：填入真实 UUID，或在功能未实现前从 UI 隐藏入口并抛出 `UnsupportedOperationException`。

### 2.9 客户端：BLE 前台服务权限检查为空

**位置**：`d:\Project\Android\RockiotTag\app\src\main\java\com\RockiotTag\tag\BLEForegroundService.java:51-57`

`checkBluetoothPermissions()` 方法体只有注释，服务可在无 BLE 权限时启动。

**建议**：实现权限校验，缺失时调用 `stopSelf()` 并发通知引导用户授权。

### 2.10 客户端：build.gradle 重复 testOptions 覆盖

**位置**：`d:\Project\Android\RockiotTag\app\build.gradle:93-117`

两个 `testOptions` 块，第二个覆盖第一个，导致 `returnDefaultValues = true` 丢失，单元测试在调用 Android API 时抛异常。

**建议**：合并为一个 `testOptions` 块。

---

## 三、高优先级问题（P1 — 本迭代修复）

### 3.1 后端：Servlet 线程上 Thread.sleep 阻塞

| 位置 | 时长 |
|------|------|
| `LocationSyncService.fetchLatestFromVendor:73` | 5000ms |
| `VendorDeviceBindService.bindVendorDevice:74,99` | 1000ms + 3000ms |
| `DataSyncService.scheduledSyncWithRetry:346` | 200ms × N |
| `DataSyncService.syncDeviceDataQuick:415` | 2000ms |

每个 sleep 占用 HTTP 工作线程，并发时迅速耗尽 HikariCP 连接池（max 50）与 Tomcat 线程池。

**建议**：
- 请求线程上禁止 `Thread.sleep`；改用 `CompletableFuture` + 异步 `@Async` + 厂商 API 异步客户端（WebClient）。
- `VendorDeviceBindService.bindVendorDevice` 的 `@Transactional` 内禁止 sleep + 远程调用，拆分为多个本地事务。

### 3.2 后端：N+1 查询

**位置**：
- `UserDeviceService.java:75-96` — 循环中调用 `deviceRepository.findByDeviceNum`
- `DeviceController.java:81-89` — 同样的循环查询

**建议**：
```java
// Repository
@Query("select ud from UserDevice ud join fetch ud.device where ud.userId = :userId")
List<UserDevice> findAllWithDeviceByUserId(@Param("userId") Long userId);
```
或一次性 `findAllByDeviceNumIn(List<String>)`。

### 3.3 后端：缺失索引导致全表扫描

**位置**：`location_records.vendor_record_id` 字段被 `existsByVendorRecordId` 在每次同步逐条调用，但 `database_index_optimization.sql` 未加索引。

**建议**：
```sql
CREATE INDEX idx_location_records_vendor_record_id ON location_records(vendor_record_id);
```

### 3.4 后端：无分页加载全量历史

**位置**：
- `DeviceController.getDeviceFullData:437` — `findByDeviceNumOrderByTimestampAsc`（全部）
- `getAllDeviceHistoryAuthenticated:386`、`getAllDeviceHistory:231`
- `ScanLogController.getDeviceScanLogs:106` — 加载全部后 `subList`

老设备单次请求可 OOM。

**建议**：所有列表端点加 `Pageable`，前端配合分页加载。

### 3.5 后端：RestTemplate 无超时

**位置**：`VendorApiService.java:38` — `new RestTemplate()` 无连接/读取超时，厂商慢响应会挂死线程。

**建议**：
```java
RestTemplate template = new RestTemplate();
var factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(5000);
factory.setReadTimeout(15000);
template.setRequestFactory(factory);
```
或迁移到 `WebClient`。

### 3.6 后端：GlobalExceptionHandler 泄露内部错误

**位置**：`GlobalExceptionHandler.java:37,100` — `Exception` 与 `RuntimeException` 直接返回 `e.getMessage()`。

**建议**：
- 日志记录完整堆栈，响应只返回追踪 ID + 通用提示。
- 增加 `MethodArgumentNotValidException`（400）、`DataIntegrityViolationException`（409）、`AuthenticationException`（401）处理器。

### 3.7 后端：CORS 通配 + API Key 查询参数

**位置**：`WebConfig.java:17-21`、`ApiKeyAuthenticationFilter.java:39-40,102-108`

`allowedOrigins("*")` 允许任意站点调用；`api_key` 查询参数会进入访问日志、浏览器历史、Referer。

**建议**：
- CORS 改为白名单（`https://5gp.blackrockiot.com` 等）。
- 移除查询参数支持，仅接受 `X-API-Key` 头。

### 3.8 客户端：Bearer Token 明文存储

**位置**：`SharedPreferences` 中 `auth_token` 键明文存储。

**建议**：迁移到 `EncryptedSharedPreferences`（Jetpack Security）。
```java
MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
SharedPreferences sp = EncryptedSharedPreferences.create(context, "secret", key,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
```

### 3.9 客户端：无界线程创建

**位置**：
- `BaseUseCase.java:67` — 每次 `execute()` 都 `new Thread()`
- `NetworkManager.java:54` — `executeWithRetry()` 每次 `new Thread()`
- 全项目 40+ 处 `new Thread()`

**建议**：注入共享 `ExecutorService`（IO 密集型用 `Executors.newFixedThreadPool(Runtime.availableProcessors() * 2)`），或迁移到 Kotlin Coroutines（即使 Java 侧也可用 `ListenableFuture` + Guava）。

### 3.10 客户端：Room `allowMainThreadQueries()`

**位置**：`room/AppDatabase.java:62` — 注释承认是临时方案。

**建议**：移除 `allowMainThreadQueries()`，所有 DAO 调用走 `DataRepository` 的 `ExecutorService`。但当前 `DataRepository` 是死代码——需先决定 Room 是否启用（见 3.11）。

### 3.11 客户端：双数据库无迁移路径

`DatabaseHelper`（SQLite，活跃）vs `AppDatabase`（Room，休眠）。若启用 Room，SQLite 中的用户数据丢失。

**建议**：
- **方案 A（推荐）**：完全迁移到 Room，编写一次性迁移逻辑从 SQLite 读出数据写入 Room。
- **方案 B**：删除 Room 相关代码（`AppDatabase`、`DataRepository`、实体、DAO），减少混淆。

### 3.12 客户端：God Activity

| 文件 | 行数 |
|------|------|
| `MainActivity.java` | 3,567 |
| `TrackActivity.java` | 3,051 |
| `NavigationActivity.java` | 1,262 |

Helper 拆分是机械的，状态仍在 Activity。

**建议**：
- `MainActivity` 拆分为多个 Fragment + 各自 ViewModel（已有 `MainViewModel`，扩展之）。
- 地图、BLE、设备列表分别由 Fragment 承载，Activity 仅做导航容器。
- 引入 Hilt 做依赖注入，便于测试。

---

## 四、中优先级问题（P2 — 下个迭代）

### 4.1 后端：双身份系统混乱

`User`/`users_api_key`（API Key 客户）vs `UserAccount`/`users`（App 用户），两套 JWT 签发路径，`CurrentUserUtil` 混用 `currentUser`/`currentAccount`。

**建议**：统一为单一用户模型 + 角色字段（`ROLE_API_KEY`、`ROLE_USER`、`ROLE_ADMIN`），删除 `users_api_key` 表，API Key 作为 `UserAccount` 的一个字段。

### 4.2 后端：双设备绑定模型

`Device.userId`（1:1）与 `UserDevice`（M:N）并存，`bindDevice` 覆盖 `Device.userId` 为最后绑定者，归属歧义。

**建议**：删除 `Device.userId`，统一用 `UserDevice` 表，加唯一约束 `(user_id, device_num)`。

### 4.3 后端：响应格式不统一

部分端点返回 `ApiResponse<T>`，部分返回 `Map<String,Object>`，部分返回裸实体。

**建议**：所有端点统一返回 `ApiResponse<T>`（含 `code`、`message`、`data`、`timestamp`、`traceId`）。

### 4.4 后端：单实例定时同步不可水平扩展

`@Scheduled` 在每个实例都执行，无 leader 选举；`lastSyncTimestamp` 与 `locationListCache` 为本地内存，重启丢失。

**建议**：
- 用 ShedLock 或 Quartz 集群做 leader 选举。
- `lastSyncTimestamp` 存 Redis/DB；缓存改用 Redis（依赖已在 pom 中）。

### 4.5 后端：ThreadLocal 在调度线程不清理

`VendorApiService` 的 `threadToken`/`threadUserId` 由 `VendorApiCleanupInterceptor` 在 HTTP 请求后清理，但 `@Scheduled` 任务运行在调度线程，ThreadLocal 永不清理，跨轮次复用陈旧 token。

**建议**：调度任务入口 `try/finally` 显式 `threadToken.remove()`。

### 4.6 后端：死依赖

`pom.xml` 中 `spring-boot-starter-data-redis`、`spring-boot-starter-cache`、`javax.xml.bind:jaxb-api` 未使用。

**建议**：移除；若启用 Redis 缓存（4.4），则保留 redis starter。

### 4.7 客户端：UseCase 层半采用

13 个 UseCase 仅 5 个被 `MainViewModel` 使用；`TrackViewModel` 重新实现 `DetectStayPointsUseCase` 的逻辑；`AddDeviceViewModel` 直接 `new Thread()` 绕过 `BaseUseCase`。

**建议**：统一所有 ViewModel 通过 UseCase 访问数据；删除未使用的 UseCase；`AddDeviceViewModel` 改用 `BindDeviceUseCase`。

### 4.8 客户端：ProGuard keep 规则过宽

`proguard-rules.pro:36` — `-keep class com.RockiotTag.tag.** { *; }` 实际禁用了全部混淆。

**建议**：仅 keep 反射使用的类（Gson 模型、序列化对象），其余允许混淆。

### 4.9 客户端：`getDeviceCount()` 用 SELECT *

`DatabaseHelper.java:513` — `SELECT *` 后 `cursor.getCount()`，加载全部列。

**建议**：`SELECT COUNT(*) FROM devices`。

### 4.10 客户端：HttpHelper 资源泄漏

`HttpHelper.java:262` — `BufferedReader` 在循环后 `in.close()`，`readLine()` 抛异常时泄漏。

**建议**：改用 try-with-resources。

### 4.11 客户端：BLEManager 静默吞异常

`BLEManager.java` 多处 `catch (Exception e) {}` 空体（行 290、314、334、353、366、379、391、403）。

**建议**：至少 `LogUtil.w(TAG, "connect failed", e)`，便于排查。

### 4.12 客户端：`NewApiService.login()` 是假成功

`NewApiService.java:56-64` — stub 永远返回成功，调用方（`AddDeviceViewModel.bindDevice:131`）信以为真。

**建议**：删除该方法或改为 `throw new UnsupportedOperationException("API Key auth, no login needed")`。

### 4.13 客户端：UserApiService.updateNickname 试 4 个端点

`UserApiService.java` 依次尝试 `/user/nickname` → `/user/profile` → `/user/username`(nickname) → `/user/username`(username)，说明后端契约不明。

**建议**：与后端对齐单一端点 `PUT /api/user/nickname`，删除重试逻辑。

### 4.14 客户端：`TrackActivity.threadPool` 为静态

`TrackActivity.java:84` — 静态 `ExecutorService` 可通过 Runnable 引用已销毁的 Activity。

**建议**：改为实例字段，`onDestroy` 中 `shutdown()`。

---

## 五、低优先级问题（P3 — 持续改进）

### 5.1 后端：无 Spring Profile / 无 Actuator

单一 `application.properties` 不分环境；无健康检查端点。

**建议**：`application-dev.properties` / `application-prod.properties`；引入 `spring-boot-starter-actuator` + Prometheus。

### 5.2 后端：Controller 含业务逻辑

`DeviceController` 注入 7 个依赖（含 3 个 Repository），实现 `getUserIdFromRequest`、`checkDeviceOwnership`。

**建议**：所有业务逻辑下沉到 Service；Controller 仅做路由 + DTO 转换。

### 5.3 后端：`System.out.println` 调试代码

`LocationController.java:67-68,87`。

**建议**：改用 SLF4J Logger。

### 5.4 后端：`DataSyncController.syncDeviceData` 创建目录

`DataSyncController.java:206-209` — Controller 中 `new File("logs/").mkdirs()`。

**建议**：文件系统副作用移到 Service 或删除。

### 5.5 客户端：根目录污染

`d:\Project\Android\RockiotTag\` 下混入 `TrackActivity_optimized.java`、`SpriteTagBackend_*.java`、`RockiotTagBackend_*.java`、`*.py`、`*.docx`、`*.pdf`、`AMap/` 重复 SDK。

**建议**：清理；后端代码不应出现在 Android 仓库。

### 5.6 客户端：LoginActivity 是死代码

`LoginActivity` 仅做语言初始化后 `finish()`，`MainActivity` 才是启动器。

**建议**：删除 `LoginActivity`，语言初始化移到 `Application.onCreate()`。

### 5.7 客户端：测试覆盖率约 4.8%

7 个测试文件，多数为 `assertTrue(true)` 占位；无 Espresso。

**建议**：补齐 ViewModel 与 Repository 单测；关键流程加 Espresso。

### 5.8 客户端：`CrowdSourcingManager` 手拼 JSON

字符串拼接 JSON 易错。

**建议**：用 Gson 序列化对象。

### 5.9 客户端：`TagDevice` 双访问器

`getBattery()` / `getBatteryLevel()` 并存。

**建议**：保留一个，另一个 `@Deprecated` 后删除。

### 5.10 客户端：`MemoryLeakDetector` 逐回调日志

每个生命周期回调都打日志，噪音大。

**建议**：仅 debug 构建且仅警告级别输出。

---

## 六、前后端契约不一致

| 问题 | 客户端 | 后端 | 建议 |
|------|--------|------|------|
| 昵称更新端点 | 试 4 个端点 | `PUT /api/user/username`、`PUT /api/user/profile` | 统一为 `PUT /api/user/nickname` |
| 设备绑定 | `/device/bind`、`/devices/bind` 都调 | `/api/device/bind` + `/api/devices/bind` 两套 | 合并为 `/api/devices/bind` |
| 错误响应 | 期望 `businessSuccess` 字段 | 部分返回 `Map`、部分 `ApiResponse` | 统一 `ApiResponse` |
| 历史接口 | 一次拉全量 | 无分页 | 双方加 `Pageable` |
| 厂商 token | 客户端持有 `cid`+`token` | `AuthController` 下发 | 后端代理，不下发 |
| 登录 | `NewApiService.login()` 假成功 | API Key 无需登录 | 删除客户端 stub |

---

## 七、优化路线图

### 第一阶段（1-2 周）— 安全紧急修复
1. 后端密钥移除默认值 + 轮换（2.3）
2. JWT 校验签名/过期（2.1）
3. IDOR 修复：位置、解绑、scan-log 加归属校验（2.4、2.5、2.9）
4. permitAll 端点改 DTO + 鉴权（2.6）
5. SMS 接入真实网关 + 限流（2.7）
6. 3DES → AES-GCM（2.2）
7. 客户端 Token 迁移 EncryptedSharedPreferences（3.8）
8. BLE 占位 UUID 修复或隐藏入口（2.8）

### 第二阶段（2-4 周）— 性能与稳定性
1. 后端 Thread.sleep 移除 + 异步化（3.1）
2. N+1 修复 + 索引补齐（3.2、3.3）
3. 列表端点分页（3.4）
4. RestTemplate 超时（3.5）
5. GlobalExceptionHandler 补全 + 不泄露内部信息（3.6）
6. CORS 白名单 + 移除查询参数 API Key（3.7）
7. 客户端线程池统一（3.9）
8. 客户端 build.gradle testOptions 合并（2.10）
9. 客户端 BLE 权限校验实现（2.9）

### 第三阶段（4-8 周）— 架构重构
1. 后端统一身份模型（4.1）
2. 后端统一设备绑定模型（4.2）
3. 后端统一响应格式（4.3）
4. 后端定时同步水平扩展（ShedLock + Redis）（4.4）
5. 客户端数据库二选一 + 迁移（3.11）
6. 客户端 God Activity 拆分（3.12）
7. 客户端 UseCase 层统一（4.7）
8. 客户端引入 Hilt DI

### 第四阶段（持续）— 工程化
1. 后端 Profile + Actuator + Prometheus（5.1）
2. 后端 Controller 瘦身（5.2）
3. 客户端测试覆盖提升（5.7）
4. 客户端 ProGuard 规则收紧（4.8）
5. 仓库结构清理（5.5）

---

## 八、关键代码示例

### 8.1 后端统一 ApiResponse

```java
@Data
@Builder
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;
    private String traceId;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().code(0).message("success").data(data)
            .timestamp(Instant.now().toEpochMilli())
            .traceId(MDC.get("traceId")).build();
    }
    public static <T> ApiResponse<T> fail(int code, String msg) {
        return ApiResponse.<T>builder().code(code).message(msg)
            .timestamp(Instant.now().toEpochMilli())
            .traceId(MDC.get("traceId")).build();
    }
}
```

### 8.2 后端 AES-GCM 加密工具

```java
public class VendorCryptoUtil {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private final SecretKey key;

    public VendorCryptoUtil(String base64Key) {
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[12];
        SecureRandom.getInstanceStrong().nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        ByteBuffer bb = ByteBuffer.allocate(iv.length + cipherText.length);
        bb.put(iv).put(cipherText);
        return Base64.getEncoder().encodeToString(bb.array());
    }

    public String decrypt(String ciphertext) throws GeneralSecurityException {
        byte[] decoded = Base64.getDecoder().decode(ciphertext);
        byte[] iv = Arrays.copyOfRange(decoded, 0, 12);
        byte[] cipherText = Arrays.copyOfRange(decoded, 12, decoded.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }
}
```

### 8.3 客户端共享线程池

```java
public class AppExecutors {
    private final ExecutorService io = Executors.newFixedThreadPool(
        Math.max(4, Runtime.getRuntime().availableProcessors() * 2));
    private final ExecutorService network = Executors.newFixedThreadPool(6);
    private final Executor main = ContextCompat.getMainExecutor(context);

    public <T> void io(Callable<T> task, Consumer<T> onResult) {
        io.submit(() -> {
            T result = task.call();
            main.execute(() -> onResult.accept(result));
        });
    }
}
```

### 8.4 客户端 EncryptedSharedPreferences 封装

```java
public class SecurePrefs {
    private final SharedPreferences prefs;

    public SecurePrefs(Context context) {
        MasterKey key = new MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        prefs = EncryptedSharedPreferences.create(
            context, "secure_prefs", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    public void putToken(String token) { prefs.edit().putString("auth_token", token).apply(); }
    public String getToken() { return prefs.getString("auth_token", null); }
}
```

---

## 九、问题统计

| 优先级 | 后端 | 客户端 | 契约 | 合计 |
|--------|------|--------|------|------|
| P0 | 7 | 3 | 0 | 10 |
| P1 | 7 | 5 | 0 | 12 |
| P2 | 6 | 8 | 0 | 14 |
| P3 | 4 | 6 | 0 | 10 |
| 契约不一致 | - | - | 6 | 6 |
| **合计** | **24** | **22** | **6** | **52** |

---

## 十、已修复确认（相对 2026-06-24 评审）

| 项 | 状态 |
|----|------|
| 客户端 release 密钥校验 | ✅ 已加 |
| 客户端 `secrets.properties.example` | ✅ 已加 |
| 客户端 `DeviceApiResponse.businessSuccess` | ✅ 已加 |
| 客户端 `parseCreatedAt` ISO-8601 | ✅ 已支持 |
| 客户端 `HttpHelper.maskApiKey` | ✅ 已使用 |
| 客户端 `READ_PHONE_STATE` 权限 | ✅ 已移除 |

---

*本文档基于静态代码审查生成，建议结合运行时监控（后端 APM、客户端 Crash 报告）持续验证。*
