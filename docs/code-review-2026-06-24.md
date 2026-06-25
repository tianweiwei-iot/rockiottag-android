# RockiotTag 全项目代码审查报告

> **审查日期**：2026-06-24  
> **审查范围**：`d:\Project\Android\RockiotTag` 仓库全部文件（Android 客户端）  
> **审查方式**：静态代码分析 + 架构梳理 + 自动化审查（Bugbot / Security Review）交叉验证  
> **关联报告**：`docs/code-review-2026-06-22.md`、`docs/code-review-2026-06-23.md`（含后端问题，本文聚焦 Android 端并更新当前状态）

---

## 目录

- [1. 执行摘要](#1-执行摘要)
- [2. 项目概览](#2-项目概览)
- [3. 问题统计](#3-问题统计)
- [4. 高危问题（P0 — 立即修复）](#4-高危问题p0--立即修复)
- [5. 中危问题（P1 — 尽快修复）](#5-中危问题p1--尽快修复)
- [6. 低危问题与技术债（P2 — 择机修复）](#6-低危问题与技术债p2--择机修复)
- [7. 架构与设计问题](#7-架构与设计问题)
- [8. 安全审查](#8-安全审查)
- [9. 测试与质量保障](#9-测试与质量保障)
- [10. 优化建议与修复路线图](#10-优化建议与修复路线图)
- [11. 附录：死代码与仓库卫生](#11-附录死代码与仓库卫生)

---

## 1. 执行摘要

RockiotTag 是一款 **BLE 智能标签追踪 Android 应用**，支持高德/Google 双地图、轨迹回放、地理围栏、用户账户绑定等功能。项目已引入 **MVVM + Repository + UseCase** 分层，并完成了密钥外置、地图抽象、位置优化等改进。

**当前最大风险集中在三类：**

1. **构建/配置缺陷**：Manifest 引用缺失的 Activity、Release 构建可能静默嵌入空 API Key  
2. **账户与设备数据一致性**：Fragment 与 Activity 设备列表逻辑分裂、API 成功判定错误、绑定设备拉取重试循环  
3. **架构未完成迁移**：3,500+ 行的 God Class、双数据库层并存、大量死代码与占位测试

本次审查共识别 **Android 端 42 项问题**（高危 6 / 中危 18 / 低危 18），并给出分阶段修复路线图。

---

## 2. 项目概览

### 2.1 模块结构

| 区域 | 路径 | 说明 |
|------|------|------|
| 唯一应用模块 | `app/` | Gradle 单模块工程 |
| 主代码 | `app/src/main/java/com/RockiotTag/tag/` | **~147** 个 Java 源文件 |
| 单元测试 | `app/src/test/java/` | **7** 个测试文件 |
| 仪器化测试 | — | **缺失**（无 `androidTest/`） |
| 资源 | `app/src/main/res/` | 22 个 layout、~42 个 drawable、6 种语言 |
| Native 库 | `app/src/main/jniLibs/arm64-v8a/` | 8 个 `.so`（高德导航等） |
| 本地 SDK | `app/libs/` | 1 个高德 JAR |
| 文档/脚本 | `docs/`、`*.py` | 审查报告、工具脚本 |
| 游离文件 | 根目录 | `TrackActivity_optimized.java`、`SpriteTagBackend_DeviceController.java` 等，**不参与构建** |

### 2.2 技术栈

| 项 | 值 |
|----|-----|
| compileSdk / targetSdk | 35 |
| minSdk | 21 |
| Java | 11 |
| ABI | 仅 `arm64-v8a` |
| Release | minify + shrinkResources + ProGuard |
| 密钥注入 | `secrets.properties` / 环境变量 → `BuildConfig` |

### 2.3 架构现状（简图）

```
UI (Activity/Fragment)
    ↓ 部分使用 LiveData
ViewModel (9 个，仅部分接入)
    ↓
UseCase (13 个，仅 5 个被 MainViewModel 使用)
    ↓
Repository (4 个，DataRepository 未使用)
    ↓
DatabaseHelper (SQLite, 活跃) / AppDatabase (Room,  dormant)
    + NewApiService / DeviceApiService / UserApiService
    + BLEManager / OptimizedBLEScanner
```

---

## 3. 问题统计

| 维度 | 高危 | 中危 | 低危 | 小计 |
|------|:----:|:----:|:----:|:----:|
| 构建与 Manifest | 2 | 1 | 1 | 4 |
| 网络与 API | 1 | 4 | 2 | 7 |
| BLE 与权限 | 0 | 2 | 1 | 3 |
| 账户与设备数据 | 2 | 4 | 2 | 8 |
| 架构与代码质量 | 1 | 4 | 8 | 13 |
| 安全 | 0 | 2 | 3 | 5 |
| 测试 | 0 | 1 | 2 | 3 |
| **合计** | **6** | **18** | **18** | **42** |

---

## 4. 高危问题（P0 — 立即修复）

### 4.1 Manifest 声明了不存在的 Activity

| 项 | 内容 |
|----|------|
| **文件** | `AndroidManifest.xml:73-75` |
| **现象** | 注册了 `ApiTestActivity`，但项目中 **无对应 Java 类**；`activity_api_test.xml` 布局仍存在 |
| **影响** | 编译可能通过（Manifest 合并不校验类存在），但任何启动该 Activity 的代码会导致 `ClassNotFoundException` 崩溃 |
| **建议** | 删除 Manifest 条目和 `activity_api_test.xml`；或恢复/新建 `ApiTestActivity` 类 |

---

### 4.2 Release 构建可静默嵌入空 API Key

| 项 | 内容 |
|----|------|
| **文件** | `app/build.gradle:13-15, 45-56` |
| **现象** | `getSecret()` 默认值为空字符串；缺少 `secrets.properties` 时构建仍成功 |
| **影响** | 地图、厂商 API、用户认证全部在运行时静默失败，难以定位 |
| **建议** | Release 构建时对必填密钥做 `gradle` 校验失败；提供 `secrets.properties.example`；CI 从环境变量注入 |

```gradle
// 示例：Release 构建强制校验
android.applicationVariants.all { variant ->
    if (variant.buildType.name == 'release') {
        variant.preBuildProvider.get().doFirst {
            def required = ['ROCKIOT_API_CID', 'ROCKIOT_GOOGLE_MAPS_API_KEY', ...]
            required.each { key ->
                if (!getSecret(key)) {
                    throw new GradleException("Missing required secret: ${key}")
                }
            }
        }
    }
}
```

---

### 4.3 绑定设备拉取失败触发无限重试循环

| 项 | 内容 |
|----|------|
| **文件** | `DeviceListFragment.java:97-105, 137-143` |
| **现象** | 已登录但 `bound_devices` 为空时发起网络请求；失败或未写入缓存后 `runOnUiThread(() -> loadDevices())` 再次进入「无缓存 → 再请求」分支 |
| **影响** | API 故障或 Token 失效时，`onResume` 每次触发密集重试，耗电、刷日志、可能触发服务端限流 |
| **建议** | 引入「拉取中 / 失败冷却 / 上次尝试时间」状态；失败时展示错误 UI 而非立即重试；区分 HTTP 401 与其他错误 |

---

### 4.4 设备列表数据源分裂（登录态不一致）

| 项 | 内容 |
|----|------|
| **文件** | `DeviceListFragment.java` vs `DeviceListActivity.java:706-737` |
| **现象** | Fragment 按 `bound_devices` 过滤；Activity 直接 `databaseHelper.getAllDevices()` 加载全部本地记录 |
| **影响** | 同一 App 内两处设备列表内容不一致；解绑/绑定后 UI 行为不可预测 |
| **建议** | 抽取 `BoundDeviceRepository` 统一数据源；Activity 与 Fragment 共用同一 ViewModel 或 Helper |

---

### 4.5 `DeviceApiResponse.isSuccess()` 忽略业务层失败

| 项 | 内容 |
|----|------|
| **文件** | `DeviceApiService.java:99-105, 278-280` |
| **现象** | JSON 中 `success: false` 时提前 return，但 `isSuccess()` 仍仅判断 HTTP 2xx |
| **影响** | `MainAuthHelper`、`DeviceListFragment` 等调用方误判成功，跳过错误处理或写入脏数据 |
| **建议** | 增加 `businessSuccess` 字段；或 `isSuccess()` 同时检查 HTTP 状态与 JSON `success` 字段 |

---

### 4.6 MainActivity / TrackActivity 体量过大（维护高危）

| 项 | 内容 |
|----|------|
| **文件** | `MainActivity.java`（**3,567 行**）、`TrackActivity.java`（**3,051 行**） |
| **现象** | 单类承担 UI、BLE、地图、数据库、网络、对话框等全部职责 |
| **影响** | 任何小改动易引入回归；无法有效单元测试；多人协作冲突频繁 |
| **建议** | 按 Helper 模式继续拆分（已有 `MainAuthHelper` 等，但不够）；将 BLE/地图/设备逻辑完全下沉到 ViewModel + Repository |

---

## 5. 中危问题（P1 — 尽快修复）

### 5.1 BLE 扫描在 Android 12+ 缺少位置权限检查

| 项 | 内容 |
|----|------|
| **文件** | `AndroidManifest.xml:10`、`OptimizedBLEScanner.java:497-511`、`LocationOptimizationManager.java:428-440` |
| **现象** | `BLUETOOTH_SCAN` 已移除 `neverForLocation`；Android 12+ 权限检查仅验证蓝牙权限，未验证 `ACCESS_FINE_LOCATION` |
| **影响** | Android 12+ 设备上 BLE 扫描可能无结果，用户看到「扫描不到设备」 |
| **建议** | Android 12+ 同时检查 `BLUETOOTH_SCAN` + `ACCESS_FINE_LOCATION`；或在 Manifest 恢复 `android:usesPermissionFlags="neverForLocation"`（若确实不用于定位） |

---

### 5.2 昵称修改 API 端点可能不存在

| 项 | 内容 |
|----|------|
| **文件** | `UserApiService.java:140-141`、`EditProfileActivity.java:102` |
| **现象** | 客户端调用 `PUT /user/nickname`；历史后端审查显示可能仅有 `/user/username` |
| **影响** | 用户修改昵称静默失败（404），但本地已显示「保存成功」 |
| **建议** | 与后端对齐端点；客户端检查 API 响应并在 UI 反馈失败 |

---

### 5.3 个人资料保存 UX 误导

| 项 | 内容 |
|----|------|
| **文件** | `EditProfileActivity.java:91-114` |
| **现象** | 先写本地 SharedPreferences，立即 Toast「已保存」并 finish；服务端同步在后台线程且异常仅打日志 |
| **影响** | 用户以为已同步到云端，实际可能失败 |
| **建议** | 等待服务端响应后再提示成功/失败；或明确区分「本地已保存，同步中…」 |

---

### 5.4 `createdAt` 日期字符串解析丢失真实绑定时间

| 项 | 内容 |
|----|------|
| **文件** | `DeviceApiService.java:145-152` |
| **现象** | 非数字 `createdAt` 直接使用 `System.currentTimeMillis()` |
| **影响** | 绑定时间显示为「刚刚」，排序/统计错误 |
| **建议** | 增加 ISO 8601 解析（`Instant.parse` 或 `SimpleDateFormat`） |

---

### 5.5 登录切换账号时 `bound_devices` 缓存未先清除

| 项 | 内容 |
|----|------|
| **文件** | `MainDialogHelper.java:469-475` |
| **现象** | 登录时清除 `selected_device_id`，但不清除 `bound_devices` |
| **影响** | 切换账号后短暂显示上一账号设备，直到服务器拉取完成 |
| **建议** | 登录成功写入 token 前先 `remove("bound_devices")` 并刷新 UI；或拉取完成前显示 loading |

---

### 5.6 双绑定系统导致位置/轨迹 403（前后端集成）

| 项 | 内容 |
|----|------|
| **文件** | `AddDeviceViewModel` / `DeviceApiService`（客户端）；后端 `UserDeviceService` vs `DeviceController` |
| **现象** | Android 绑定走 `/api/device/bind` → `UserDevice` 表；部分历史/位置接口校验 `Device.userId` |
| **影响** | 绑定成功但轨迹、历史接口返回 403 |
| **建议** | 后端 `bindDevice` 时同步更新 `Device.userId`；或 Android 统一走同一绑定 API |

---

### 5.7 双数据库层数据可能分叉

| 项 | 内容 |
|----|------|
| **文件** | `DatabaseHelper.java`（活跃，`rockiottag.db`）vs `AppDatabase.java`（Room，`rockiottag_room.db`） |
| **现象** | 全项目使用 SQLiteOpenHelper；Room 层与 `DataRepository` 从未被业务代码实例化 |
| **影响** | 若未来迁移 Room 无数据迁移路径；维护两套 schema 增加成本 |
| **建议** | 明确路线：要么删除 Room 死代码，要么制定 SQLite → Room 迁移计划 |

---

### 5.8 UseCase 层大量闲置

| 项 | 内容 |
|----|------|
| **文件** | `usecase/` 目录 13 个类，仅 5 个被 `MainViewModel` 使用 |
| **未使用** | `SaveDeviceUseCase`、`DeleteDeviceUseCase`、`LoadTrackDataUseCase`、`TrackStatisticsUseCase`、`UpdateDeviceLocationUseCase`、`DetectStayPointsUseCase` |
| **影响** | 停留点检测等逻辑在 `TrackViewModel` / `TrackDataProcessor` 重复实现 |
| **建议** | 删除或接入 UI；统一停留点算法到 `DetectStayPointsUseCase` |

---

### 5.9 `DeviceListViewModel` 未被 UI 使用

| 项 | 内容 |
|----|------|
| **文件** | `DeviceListViewModel.java` vs `DeviceListFragment.java` / `DeviceListActivity.java` |
| **现象** | ViewModel 已实现，Fragment/Activity 仍直接操作 `DatabaseHelper` |
| **建议** | Fragment/Activity 改为观察 ViewModel LiveData |

---

### 5.10 Debug 日志输出完整 API Key

| 项 | 内容 |
|----|------|
| **文件** | `HttpHelper.java:51` |
| **现象** | `LogUtil.d(TAG, "GET request API Key: " + apiKey)` |
| **影响** | Debug 包 logcat 可读取完整密钥（ProGuard 仅剥离 Release 的 Log.d） |
| **建议** | 仅打印 key 前 4 位 + `***`，或完全移除 |

---

### 5.11 `auth_token` 明文存储

| 项 | 内容 |
|----|------|
| **文件** | 多处 `SharedPreferences("app_settings")` |
| **现象** | Bearer Token 以明文存储；已关闭 `allowBackup` 降低风险 |
| **建议** | 使用 `EncryptedSharedPreferences` 或 Android Keystore 包装 |

---

### 5.12 ProGuard 配置过于宽松

| 项 | 内容 |
|----|------|
| **文件** | `proguard-rules.pro:17-21` |
| **现象** | `-dontoptimize` + `-ignorewarnings` |
| **影响** | 可能掩盖 shrink 问题，Release 包体积偏大 |
| **建议** | 逐步移除 `-ignorewarnings`，修复真实警告 |

---

### 5.13 `BLEForegroundService` 权限检查为空实现

| 项 | 内容 |
|----|------|
| **文件** | `BLEForegroundService.java:51-57` |
| **现象** | `checkBluetoothPermissions()` 仅有注释，无实际逻辑 |
| **影响** | 服务启动后可能在无权限环境运行，行为不可预期 |
| **建议** | 复用 `OptimizedBLEScanner` 的权限检查逻辑，无权限时 stopSelf |

---

### 5.14 `LocationProvider` 核心逻辑未完成

| 项 | 内容 |
|----|------|
| **文件** | `LocationProvider.java:147, 202, 212` |
| **现象** | 逆地理编码、缓存读取、电量读取均为 TODO |
| **影响** | 位置融合层功能不完整，可能返回不完整数据 |
| **建议** | 完成 TODO 或移除未使用代码路径 |

---

### 5.15 前后端用户资料接口认证（后端，见 6-23 报告）

| 项 | 内容 |
|----|------|
| **关联** | `docs/code-review-2026-06-23.md` §2.1 |
| **现象** | 后端 `/api/user/password` 等接口若仍为 `permitAll()`，存在账户接管风险 |
| **建议** | Android 端已正确携带 Token，需后端强制认证 |

---

### 5.16 根目录游离 Java 文件造成混淆

| 项 | 内容 |
|----|------|
| **文件** | `TrackActivity_optimized.java`、`SpriteTagBackend_DeviceController.java` |
| **现象** | 后端 Spring 代码和优化草稿放在 Android 项目根目录，不参与构建 |
| **建议** | 移至对应后端仓库或 `docs/reference/`，避免误改 |

---

### 5.17 `extractNativeLibs="true"` 与 16KB 页对齐

| 项 | 内容 |
|----|------|
| **文件** | `AndroidManifest.xml:32`、`app/build.gradle`（16KB 页配置） |
| **现象** | 同时配置了 16KB 页支持与 extract native libs |
| **影响** | Google Play Android 15+ 对 native 库对齐有要求，需验证 AMap SDK 兼容性 |
| **建议** | 在 targetSdk 35 设备上做完整回归；关注 Play Console 警告 |

---

### 5.18 声明但未使用的 `READ_PHONE_STATE` 权限

| 项 | 内容 |
|----|------|
| **文件** | `AndroidManifest.xml:19` |
| **现象** | 代码中无 `READ_PHONE_STATE` 引用 |
| **影响** | 多余敏感权限降低 Play 审核通过率、增加用户疑虑 |
| **建议** | 确认高德 SDK 是否必需；不需要则移除 |

---

## 6. 低危问题与技术债（P2 — 择机修复）

| # | 问题 | 位置 | 建议 |
|---|------|------|------|
| 1 | 大量 `new Thread()`（~40+ 处）无统一线程池 | 全局 | 使用 `ExecutorService` / Kotlin 协程；`BaseUseCase` 统一调度 |
| 2 | Room 允许主线程查询 | `AppDatabase.java:62` | 移除 `allowMainThreadQueries()` |
| 3 | 双 BLE 栈并存 | `BLEManager` + `OptimizedBLEScanner` | 评估合并，减少重复扫描逻辑 |
| 4 | 双 Map Manager | `MapManager` + `AMapManager`/`GoogleMapManager` | 完全迁移到 `IMapAdapter` |
| 5 | 重复 Adapter | `DeviceListFragment` 内部类 vs `adapter/DeviceListAdapter.java` | 删除重复，统一引用 |
| 6 | 死代码：`location/LocationManager.java` | 零引用 | 删除 |
| 7 | 死代码：`data/DeviceDataManager.java` | 零引用 | 删除 |
| 8 | 死代码：`repository/DataRepository.java` | 零引用 | 删除或完成 Room 迁移 |
| 9 | 未使用布局 | `activity_main_optimized.xml` | 删除或接入 |
| 10 | `NewApiService.login()` 空实现 | 返回假成功 | 文档化或移除误导性 API |
| 11 | `SharedPreferencesManager` 标记 @deprecated 仍保留 | — | 清理调用后删除 |
| 12 | 缺少 `secrets.properties.example` | 根目录 | 新增模板文件 |
| 13 | 仓库内重复 AMap SDK 目录 | `AMap/` vs `app/libs` | 统一 SDK 管理，减小仓库体积 |
| 14 | Release baseline profile 被 git 跟踪 | `app/release/baselineProfiles/` | 考虑加入 `.gitignore` 或 CI 生成 |
| 15 | `CrowdSourcingManager` 手动拼接 JSON | 非结构化 | 改用 Gson 序列化，降低格式错误风险 |
| 16 | 多语言字符串可能不同步 | `values-*/strings.xml` | 建立 CI 检查缺失翻译 |
| 17 | `LoginActivity` 非 Launcher 入口 | 仅语言初始化 | 文档说明或合并到 MainActivity |
| 18 | `TrackFragment` 仅为空壳 | 真实轨迹在 `TrackActivity` | 统一入口，减少用户困惑 |

---

## 7. 架构与设计问题

### 7.1 已做得好的部分

- **密钥外置**：`BuildConfig` + `secrets.properties`，`.gitignore` 排除敏感文件  
- **HTTP 统一层**：`HttpHelper` 集中超时、Header、资源释放  
- **地图抽象**：`IMapAdapter` + `MapAdapterFactory` 支持 AMap/Google 切换  
- **位置优化**：`LocationOptimizationManager` 整合 BLE 扫描、手机 GPS、离线缓存、众包上传  
- **部分 MVVM**：`MainViewModel` 正确使用 `observeForever` + `onCleared` 移除 Observer  
- **Cursor 管理**：`DatabaseHelper` 普遍使用 try-with-resources  
- **内存泄漏防护**：`SafeHandler`、`WeakReference` 在部分 Activity 中使用  

### 7.2 核心架构矛盾

| 矛盾 | 说明 |
|------|------|
| **分层 vs 实践** | 引入 UseCase/Repository，但 MainActivity 仍直接操作 DB/BLE/API |
| **单数据源 vs 多入口** | 设备数据来自 SQLite、`bound_devices` 缓存、服务器三处，同步规则不统一 |
| **Room vs SQLite** | Room 层已搭建但未启用，形成「半迁移」状态 |
| **Fragment 删除后逻辑内聚** | 7 个 Track/Map Fragment 已删，逻辑回流到 Activity，加剧 God Class |

### 7.3 推荐目标架构

```
Fragment/Activity (仅 UI 绑定)
    ↓
ViewModel (状态 + 用户事件)
    ↓
UseCase (单一业务操作)
    ↓
Repository (唯一数据入口)
    ↓
Local: DatabaseHelper (短期) → Room (长期)
Remote: DeviceApiService / UserApiService / NewApiService
Device: BLERepository → OptimizedBLEScanner
```

---

## 8. 安全审查

### 8.1 本次改动中的改进（Security Review 结论）

| 改进项 | 说明 |
|--------|------|
| 密钥移出源码 | 不再硬编码于 `ApiConfig` / Manifest |
| `allowBackup=false` | 降低 adb backup 提取 Token/SQLite 风险 |
| 解绑走服务端 | `DeviceListActivity` 先调 `unbindDevice` 再删本地 |
| 新 Activity `exported=false` | 个人资料相关页面不可外部启动 |
| 参数化 SQL | `DatabaseHelper` 使用 `ContentValues` / 绑定参数 |

### 8.2 残余风险

| 风险 | 级别 | 说明 |
|------|------|------|
| API Key 打包进 APK | 中 | 移动端固有限制；可考虑服务端代理 |
| Token 明文 SharedPreferences | 中 | 建议 EncryptedSharedPreferences |
| Debug 日志泄露密钥 | 低-中 | 见 §5.10 |
| 历史 keystore 路径 | 低 | `../myapk/release-key.jks`，需确认未提交 Git 历史 |

### 8.3 安全加固建议（按优先级）

1. Release 构建密钥校验（§4.2）  
2. Token 加密存储  
3. 日志脱敏  
4. 证书固定（可选，针对核心 API 域名）  
5. 后端接口强制 Bearer 认证（配合客户端已有 Token 逻辑）

---

## 9. 测试与质量保障

### 9.1 当前覆盖

| 测试文件 | 质量评价 |
|----------|----------|
| `BLETagFilterTest` | ✅ 实质性（~15 用例） |
| `LocationKalmanFilterTest` | ✅ 实质性 |
| `TrackCalculatorTest` | ✅ 实质性 |
| `DetectStayPointsUseCaseTest` | ✅ 实质性（但 UseCase 未接入 UI） |
| `DeviceListViewModelTest` | ⚠️ 仅测试 ViewModel 创建 |
| `TrackViewModelTest` | ❌ 占位（`assertTrue(true)`） |
| `AddDeviceViewModelTest` | ❌ 占位 |

**覆盖率估算**：约 **4.8%** 源文件有测试（7/147）；核心路径（BLE、网络、DB、UI）**零覆盖**；无 Espresso 仪器化测试。

### 9.2 测试补强建议

| 优先级 | 测试目标 |
|--------|----------|
| P0 | `DeviceApiService.getBoundDevices` 解析逻辑（含 `success:false`、ISO 日期） |
| P0 | `DeviceListFragment` 重试逻辑（Mock API 失败场景） |
| P1 | `HttpHelper` 请求/响应、错误码处理 |
| P1 | `BoundDevicesHelper` 过滤逻辑 |
| P2 | 关键 UI 流程 Espresso（登录 → 绑定 → 显示设备） |

---

## 10. 优化建议与修复路线图

### 阶段一：止血（1–3 天）

| 任务 | 关联问题 |
|------|----------|
| 删除或恢复 `ApiTestActivity` | §4.1 |
| Release 密钥 Gradle 校验 + `secrets.properties.example` | §4.2 |
| 修复 `DeviceApiResponse.isSuccess()` | §4.5 |
| 修复 `DeviceListFragment` 重试循环 | §4.3 |
| Android 12+ BLE 位置权限检查 | §5.1 |
| 对齐昵称 API 端点 | §5.2 |

### 阶段二：数据一致性（1 周）

| 任务 | 关联问题 |
|------|----------|
| 统一 `DeviceListFragment` / `DeviceListActivity` 数据源 | §4.4 |
| 登录时清除 `bound_devices` + loading 态 | §5.5 |
| `EditProfileActivity` 服务端同步反馈 | §5.3 |
| `createdAt` ISO 8601 解析 | §5.4 |
| 与后端确认双绑定系统修复 | §5.6 |

### 阶段三：架构瘦身（2–4 周）

| 任务 | 关联问题 |
|------|----------|
| MainActivity 拆分为 Map/BLE/Device 三个 Coordinator | §4.6 |
| TrackActivity 迁移逻辑到 ViewModel + Helper | §4.6 |
| 接入或删除闲置 UseCase | §5.8 |
| DeviceList UI 接入 ViewModel | §5.9 |
| 清理死代码（Room/DataRepository/LocationManager 等） | §6 |

### 阶段四：质量与安全（持续）

| 任务 | 关联问题 |
|------|----------|
| 补全单元测试（API 解析、重试逻辑） | §9 |
| Token 加密存储 | §5.11 |
| 统一线程池 | §6 #1 |
| ProGuard 收紧 | §5.12 |
| 添加 Espresso 冒烟测试 | §9 |

---

## 11. 附录：死代码与仓库卫生

### 11.1 确认可安全删除（零引用）

| 文件 | 说明 |
|------|------|
| `location/LocationManager.java` | 166 行，无任何 import |
| `repository/DataRepository.java` | Room 封装，未实例化 |
| `data/DeviceDataManager.java` | 未引用 |
| `adapter/DeviceListAdapter.java` | Fragment 使用内部类替代 |
| `res/layout/activity_main_optimized.xml` | MainActivity 未使用 |

### 11.2 需决策后处理

| 文件 | 选项 |
|------|------|
| Room 全套（`AppDatabase`、Entity、Dao） | 完成迁移 **或** 整包删除 |
| `viewmodel/DeviceListViewModel.java` | 接入 UI **或** 删除 |
| `usecase/DetectStayPointsUseCase.java` | 接入 TrackActivity **或** 删除 |
| 根目录 `TrackActivity_optimized.java` | 合并有用改动 **或** 删除 |
| 根目录 `SpriteTagBackend_DeviceController.java` | 移至后端仓库 |

### 11.3 已删除且引用安全（git status）

| 已删文件 | 状态 |
|----------|------|
| `Device.java` | ✅ 已由 `TagDevice` 替代 |
| `model/StayPoint.java` | ✅ 已移至根 `StayPoint.java` |
| `GoogleGeocoder.java` | ✅ 已替换为 `GoogleGeocoderService` |
| 7 个 Fragment + 对应 layout | ✅ 无残留引用 |

### 11.4 大文件行数参考

| 文件 | 行数 |
|------|-----:|
| `MainActivity.java` | 3,567 |
| `TrackActivity.java` | 3,051 |
| `NavigationActivity.java` | 1,262 |
| `LocationOptimizationManager.java` | 1,208 |
| `DatabaseHelper.java` | 861 |
| `DeviceListActivity.java` | 818 |
| `NewApiService.java` | 747 |

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-06-24 | 初版：全项目静态审查 + Bugbot/Security Review 交叉验证 |

---

*本报告由代码审查工具生成，建议在修复后逐项勾选验证。*
