# 工程架构约束与开发规范（ENGINEERING）

本文定义 `jm3`（JM3 Miuix 客户端）的可执行工程约束。文档仅记录判定线、真实符号位置与技术决策。

**使用方式**：

- 编写代码前，先核对「决策三问」，再按六章查阅对应红线。
- 若约束与源码现状冲突，以源码现状为准，并在同一次提交内同步修订本文。
- 新增约束必须附带真实文件路径与行号；未核实的内容须显式标明「未核实」。

## 决策三问

开始任何功能或重构前，先回答以下三问：

1. **分层归属**：能进 `:core` 的逻辑禁止写进 `:app`；能写成纯函数的判定禁止藏在 Composable 内。`:core` 为纯 JVM 模块（`core/build.gradle.kts:1-4`），严禁引入 Android 类型。
2. **可测路径**：纯判定、换算、状态机与编解码必须可测。现有全仓共 164 个测试类（`:core` 73 个、`:app` 91 个）。无法测试说明分层有误，须先拆层。
3. **红线冲击**：核对是否破坏单一网关、单一状态真源、双皮肤组件隔离、阅读器手势优先级及 Release 混淆签名规则。

---

## 一、状态与依赖注入

### 1.1 进程组装根与六大域

进程内唯一的组装根为 `Jm3Graph`（`app/src/main/kotlin/com/jm3/app/miuix/Jm3App.kt:153`），由 `Jm3Application`（`:139`，在 `app/src/main/AndroidManifest.xml:17` 注册）与 `MainActivity` 统一经 `init(context)`（`:197`）加锁（`initLock` `:185`）幂等初始化。组装根仅负责按启动顺序装配六个业务域，并提供 `@Deprecated` 单行委托属性：

- `ApiDomain`（`data/ApiDomain.kt:27`）：管理网关实例、线路池缓存与线路切换锁。会话迁移由 `SessionDomain.applyDomains`（`data/SessionDomain.kt:43`）挂接。
- `SessionDomain`（`data/SessionDomain.kt:16`）：管理账号、带启动闸门的 `AuthCall`、未读角标与凭据存储。
- `SettingsDomain`（`data/SettingsDomain.kt:19`）：管理设置仓库与全局分页模式状态。
- `LibraryDomain`（`data/LibraryDomain.kt:18`）：管理唯一 Room 数据库 `jm3.db`（`:24`），提供历史仓库与下载台账存储。
- `DownloadDomain`（`data/DownloadDomain.kt:21`）：管理页文件存储、下载队列、台账与 `DownloadManager` 门面。
- `UpdateDomain`（`data/UpdateDomain.kt:15`）：管理 `UpdateDownloader` 唯一实例与安装包安装出口。

**硬性禁令**：

- 同一域内类型禁止在其他位置重复实例化。
- 屏幕统一经 `ScreenDeps`（`app/src/main/kotlin/com/jm3/app/miuix/ScreenDeps.kt:26`）接收注入依赖，禁止在屏幕内直读 `Jm3Graph.<成员>`。
- `Jm3Graph` 委托属性仅允许 `Jm3Application`、`MainActivity` 与 `DownloadService` 等进程级入口调用，且禁止在委托内写编排逻辑。

### 1.2 协程作用域与界面编排

- 唯一长期协程作用域为 `appScope`（`Jm3App.kt:156`，`SupervisorJob() + Dispatchers.IO`）。禁止使用 `GlobalScope`。
- 现有用途限于装配注入、`ScreenDeps.appScope`、`ApiDomain`（`:29`）、`UpdateDomain`（`:29`）、`DownloadManager`（`:28`）、`DownloadService`（`:153`、`:160`、`:213`）、`MainActivity.onStop`（`:79`）、`ReaderScreen` 退出补记页码（`:630`）与 `CouiReaderScreen` 退出补记页码（`ui/coui/page/CouiReaderScreen.kt:691`）。新增使用点必须说明为何无法挂载至 `rememberCoroutineScope`。
- 本仓库不引入 `ViewModel`。屏幕数据状态使用 `remember` 与 `LaunchedEffect` 编排，状态复杂时抽离纯 Kotlin 状态容器（如 `AlbumDetailStateHolder`）。
- 网关调用一律经 `AuthCall.call`（`core/src/main/kotlin/com/jm3/core/session/AuthCall.kt:32`）。禁止在屏幕内自写 `withContext(Dispatchers.IO)` 调网关，禁止跳过启动闸门与统一失效回调。

### 1.3 持久化与单真源

- 下载任务状态唯一真源为 `DownloadQueue.updates`（`core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:30`）。UI 禁止另建进度表或在界面层累加页数。
- 设置项与会话仅经 DataStore 读写，禁止另开 `SharedPreferences`。库名分别为 `jm3_settings`（`data/SettingsStore.kt:19`）与 `jm3_session`（`data/SessionStore.kt:17`），均配置 `ReplaceFileCorruptionHandler` 损坏自清，并在 `AndroidManifest.xml:23` 经 `res/xml/data_extraction_rules.xml` 排除云备份与设备迁移。
- 会话内容须经 `SessionCodec` 单键 JSON 读写；自动登录密文统一存为 `jm3_settings` 下的 `credentials_blob` 键（`SettingsStore.kt:27`）。
- 持久化契约先在 `:core` 定义接口与 `InMemory` 实现，再在 `:app` 落地实现：
  - 设置：`SettingsRepository` / `InMemorySettingsRepository`（`core/src/main/kotlin/com/jm3/core/settings/SettingsRepository.kt:23`、`:52`）。
  - 下载：`DownloadLedger`（`core/src/main/kotlin/com/jm3/core/download/DownloadLedger.kt:60`）、`DownloadRecord`（`DownloadRecord.kt:4`）、`DownloadStore`（`DownloadStore.kt:9`）。
  - 历史：`HistoryRepository` / `InMemoryHistoryRepository`（`core/src/main/kotlin/com/jm3/core/history/HistoryRepository.kt:13`、`:37`）。
  - 凭据：`CredentialStorage`、`CredentialCipher`、`EncryptedCredentialStore`（`core/src/main/kotlin/com/jm3/core/CredentialStorage.kt:16`、`:69`、`:92`），Android 侧密钥由 `KeystoreCredentialCipher`（`data/CredentialStore.kt:34`）提供（支持 Keystore 密钥失效时自动重建重试）。
  - 例外：`DomainPoolCache`（`core/src/main/kotlin/com/jm3/core/settings/DomainPoolCache.kt:9`、Android 原子写盘实现 `FileDomainPoolCache` 见 `data/DomainPoolCache.kt:16`）因冷启动网关装配在首屏前同步执行，采用阻塞文件缓存而非 DataStore。
- Room 数据库 `jm3.db` 仅包含 `history` 与 `downloads` 两张表（`data/history/HistoryStore.kt:30`、`:70`、`version = 3`、`:148`），通过 `MIGRATION_2_3`（`:140`）为 `history` 建立 `albumId`、`updatedAt` 索引并为 `downloads` 建立 `updatedAt` 索引。改表必须提供 `Migration` 并升版本，禁止 `fallbackToDestructiveMigration`。

---

## 二、网络与协议边界

### 2.1 网关接口与反射白名单

- 网络唯一入口为 `Jm3Gateway`（`core/src/main/kotlin/com/jm3/core/Jm3Gateway.kt:47`），继承自 `SessionAuth`（`SessionAuth.kt:14`）与 `PhotoSource`（`download/PhotoSource.kt:14`）。所有方法均为阻塞调用，须运行在 IO 调度器上。
- `AccountManager` 仅依赖 `SessionAuth` 的 6 个鉴权方法；下载取页组件仅依赖 `PhotoSource` 的 `photo`（`:17`）与 `imageBytes`（`:20`）。禁止测试造全宽假网关。
- 协议库（`io.github.jukomu.jmcomic`）只允许出现在 `:core`，通过 `api(libs.jmcomic.api)`（`core/build.gradle.kts:18`）暴露。禁止在 `:app` 内直接实例化底层协议客户端。
- `java.lang.reflect` 全仓仅允许出现在 `LibraryBridge`（`core/src/main/kotlin/com/jm3/core/LibraryBridge.kt:15`、`:43`）与 `CustomDomainPool`（`CustomDomainPool.kt:32`、`:49`）。合法反射成员白名单共 10 个：
  - `LibraryBridge`（8 个）：`cookieManager`、`cacheUsername`、`autoLoginRefreshAllowed`、`loginHost`、`encryptPasswordInMemory`、`encryptedPassword`、`newHttpUrlBuilder`、`executeGetRequest`。
  - `CustomDomainPool`（2 个）：`domainManager`、`createDomainProbe`。
- 反射成员缺失时须按契约降级，并通过 `Jm3Log.warn` 记录成员名与异常类名，禁止打印 Cookie 或密码值。

### 2.2 线路切换与域名来源

- 网关实例切换走 `ApiDomain.gatewayOverride`（`data/ApiDomain.kt:45`）与 `GatewayFactory`（`core/src/main/kotlin/com/jm3/core/GatewayFactory.kt:17`）。域名清洗一律经 `Domains`（`core/src/main/kotlin/com/jm3/core/settings/Domains.kt:6`），禁止在屏幕硬编码 URL。
- 启动期域名仅来自自定义线路与上次刷新落盘的 `DomainPoolCache`。官方线路表在 `DomainTable.kt` 运行时拉取解密；仅当两者皆空（如首次安装）时回退至库内初始化链并在完成后落盘。

### 2.3 图片缓存与超采样链路

- 阅读器图片统一经 `ReaderFetcherFactory`（`ui/ReaderImages.kt:45`）获取，缓存键由 `ReaderKeyer.keyOf`（`:40`）统一生成（格式 `jm3-reader/<photoId>/<index>`），禁止另拼前缀。
- 字节流必须先经 `imageLoader.diskCache.openSnapshot`（`:62`）复用；未命中再读取本地下载文件或网关字节，经 `openEditor`（`:77`）与 `commitAndOpenSnapshot`（`:85`）写入磁盘后返回 `ImageSource`。写盘失败须调用 `editor.abort()`（`:102`）并抛出异常。
- **关键约束**：禁止直接将内存 `okio.Buffer` 作为常规路径返回。`telephoto` 分块超采样仅识别 `FileImageSource` 携带的 `diskCacheKey`，直接返回内存流会导致放大发糊。
- `ImageLoader` 由 `acquireReaderImageLoader`（`:165`）持有为进程级共享实例，配置 `reader_images` 磁盘缓存（`:210`，占可用空间 2%）。重建键 `ReaderLoaderKey`（`:141`）仅包含网关与下载存储，鉴权通道经 `ReaderAuthChannel`（`:154`）在 `SideEffect`（`:194`）动态注入。页面销毁时禁止 `shutdown()`，防止换章重叠时误关新屏缓存。

### 2.4 分页与静默失败红线

- 分页统一采用按 ID 去重与 1 基页码游标。页码换算收口于 `PageNumbers`（`core/src/main/kotlin/com/jm3/core/download/PageNumbers.kt:12`），翻页计算收口于 `PagingBehavior`（`core/src/main/kotlin/com/jm3/core/paging/PagingBehavior.kt`）。
- 网络失败禁止静默清零有效状态。例如 `UnreadBadge`（`core/src/main/kotlin/com/jm3/core/session/UnreadBadge.kt:17`）在请求失败时保留上次计数，且降级理由必须写明于代码注释中。

---

## 三、错误处理与协程规范

1. **禁止吞掉 `CancellationException`**：参考 `DownloadQueue.kt:155-157` 与 `:217-219`。挂起调用收成 `Result` 时一律使用 `runCatchingSuspending`（`core/src/main/kotlin/com/jm3/core/Cancellation.kt:11`），禁止直接使用 `runCatching { suspendCall() }`。
2. **单任务失败隔离**：下载 worker 捕获普通异常后仅标记当前任务失败，禁止因单个任务异常中断整个队列循环（`DownloadQueue.kt:158`）。
3. **状态机硬断言**：非法状态转移使用 `check` 显式抛出，例如 `DownloadTask.kt:48` 的 `check(status.canTransitionTo(next))`，禁止以条件跳过掩盖状态错乱。
4. **用户错误可重试**：全屏错误态统一复用 `ErrorScreen`（`ui/Common.kt:306`），列表加载脚部统一复用 `LoadMoreFooter`（`:416`）。COUI 皮肤对应复用 `CouiErrorGroup`（`ui/coui/component/CouiAppComponents.kt:872`）与 `CouiLoadMoreFooter`（`:904`）。禁止屏幕自造错误布局。
5. **跟手动画取消**：阅读器翻页与复位动画统一调用 `springAnimateToPage`（`ui/ReaderScreen.kt:538`、`:562`），避免另起协程争夺滚动状态。
6. **文案与日志事实性**：日志只输出任务标识、文件路径或异常类型，不夹带私密数据；用户提示统一使用简体中文与全角标点。

---

## 四、构建、依赖与交付物

1. **单一版本真源**：版本号集中定义于 `gradle/libs.versions.toml`。模块 `build.gradle.kts` 内禁止出现字面版本号。新增依赖须同步核对许可证与 R8 规则。
2. **单向模块依赖**：仅允许 `:app` 依赖 `:core`（`app/build.gradle.kts:82`），禁止反向依赖。`:core` 保持 Java 17（`core/build.gradle.kts:13-14`），`:app` 使用 Java 21（`app/build.gradle.kts:66-67`）。
3. **SDK 坐标与传输安全矩阵**：当前固定 `compileSdkVersion("android-37.0")`（`app/build.gradle.kts:21`）、`minSdk = 31`（`:26`）、`targetSdk = 36`（`:27`）。上述下限受 `COUI-Kit 0.0.1-alpha10` 元数据强制约束，调整前必须先核对依赖矩阵。`AndroidManifest.xml:25` 声明 `android:usesCleartextTraffic="false"` 禁用明文 HTTP 流量，应用内更新下载拒绝非 HTTPS 与 3xx 降级重定向，并通过 `data_extraction_rules.xml`（`AndroidManifest.xml:23`）排除敏感 DataStore 备份迁移。
4. **Release 混淆与压缩**：`release` 构建强制开启 `isMinifyEnabled` 与 `isShrinkResources`（`app/build.gradle.kts:45-46`），规则维护于 `app/proguard-rules.pro`。新增反射式依赖必须实测 Release 包。
5. **签名安全边界**：Release 签名从 `local.properties` 读取四键，缺失时自动回退至调试签名（`app/build.gradle.kts:12-17`、`:49`）。密钥、口令与 Cookie 严禁入库。
6. **Gradle 执行纪律**：Windows 下固定使用 `.\gradlew.bat`，禁止传入 `--offline` 参数。同一工作树同时只允许运行一个 Gradle 构建进程，并行任务须串行化构建。
7. **AGP 联动开关**：`gradle.properties:7`（`android.builtInKotlin=false`）与 `:9`（`android.newDsl=false`）在升级 AGP 或 KGP 时须成对检查。
8. **仓库入库红线**：`AGENTS.md`、`docs/`、`implementation_plan.md`、`research/`、`.scratch/`、`.trae/` 与 `.tools/` 已在 `.gitignore` 中忽略。内部调研、过程台账与本地工具产物严禁提交至 Git。

---

## 五、界面组件与阅读器内核

### 5.1 双皮肤组件边界

- 皮肤组件严格按界面风格分工：HyperOS 使用 `Miuix`，ColorOS 使用 `COUI-Kit`（`io.github.itoseo:coui:0.0.1-alpha10`，`app/build.gradle.kts:108`，入口 `ui/coui/theme/CouiTheme.kt`）。禁止跨皮肤混用页面骨架与卡片组件。
- COUI 页面仅允许从 Miuix 借用图标组合件与 `SnackbarHostState` 等库内缺件，禁止在全仓引入 `androidx.compose.material3`。
- 两套皮肤的圆角与颜色统一取自 `LocalSkin`（`ui/skin/SkinTokens.kt:201`）：HyperOS 卡片圆角 `16 dp`、控件 `12 dp`（`:63-64`）；ColorOS 卡片圆角 `18 dp`、控件 `14 dp`（`:174-175`）。

### 5.2 公共控件与滚动条

- HyperOS 侧三态与基础组件统一复用 `ui/Common.kt`：`LoadingScreen`（`:292`）、`ErrorScreen`（`:306`）、`EmptyState`（`:366`）、`CenteredEmptyState`（`:386`）、`LoadMoreFooter`（`:416`）、`PagerBar`（`:620`）、`TrackedLazyColumn`（`:508`）、`rememberSelectionHaptic`（`:769`）与 `CoverSkeletonState`（`:864`）。
- 长列表一律使用 `TrackedLazyColumn`，由其内部统一承载 `ListScrollBar` 并共享 `contentPadding`，禁止在页面层自拼滚动条。
- COUI 侧统一复用 `CouiAppComponents.kt` 中的 `CouiAppScaffold`（`:194`）、`Modifier.couiPressable`（`:319`）、`CouiLoadingGroup`（`:487`）、`CouiEmptyGroup`（`:502`）、`CouiErrorGroup`（`:872`）与 `CouiLoadMoreFooter`（`:904`）。COUI 页面由 `CouiAppScaffold` 统一留出底栏净空，禁止页面硬编码底部占位高度。
- Miuix `0.9.4` 实测基线：`CardDefaults.InsideMargin` 默认为 `0 dp`，弹窗水平内边距为 `24 dp`，调用方勿叠加重复水平边距；圆角裁切与描边优先使用 `squircleClip`、`squircleBorder` 与 `squircleSurface`。

### 5.3 阅读器手势与解码管线

- **手势状态机**（`ui/ReaderScreen.kt`、`ui/ReaderZoomLogic.kt`）：
  - 缩放态仅由当前页通过 `isPageZoomed`（`ReaderZoomLogic.kt:13`）回写，翻页后自动复位。
  - 横向 `HorizontalPager` 挂载 `.pagerGestureOverride(pagerState, enabled = !pageZoomed)`（`ReaderScreen.kt:929`、`CouiReaderScreen.kt:986`），确保放大平移时不触发翻页。
  - 放大贴边翻页在 `PointerEventPass.Initial` 采集原始位移，交由 `EdgeDragTracker`（`ReaderZoomLogic.kt:58`）与 `decideEdgePageTurn`（`:23`）判定，复核通过后执行 `springAnimateToPage`。
  - 纯常量（`ZOOMED_THRESHOLD = 0.02f`、`EDGE_PAGE_DISTANCE_RATIO = 0.18f`、`CONTENT_PIN_TOLERANCE_PX = 24f`、`EDGE_CONFIRM_DELAY_MS = 120L`、`EDGE_CONFIRM_MOVE_PX = 8f`）集中于 `ReaderZoomLogic.kt:4-49`，禁止散落回 Composable。
- **比例与解码**（`ui/ReaderPageLogic.kt`、`ui/ReaderImages.kt`）：
  - 布局比例与解码请求尺寸由 `readerPageFrame`（`ReaderPageLogic.kt:108`）同源算出，展示与预取共用同一换算，确保内存缓存键一致。
  - 像素预算派生自 `ReaderImageQuality.STANDARD.maxDecodePixels`（4,000,000 px，`ReaderPreferences.kt:6`），单边上限 `MAX_PAGE_DECODE_EDGE_PX = 8192`（`ReaderPageLogic.kt:33`）。
  - 统一通过 `configureReaderDefaults()`（`ReaderImages.kt:223-225`）指定 `Bitmap.Config.RGB_565` 解码，4 MP 标准档单页位图内存约 `8 MB`（较 `ARGB_8888` 降低 50%）。
  - 比例回填走 `readerPageBackfill`（`ReaderPageLogic.kt:155`）：`pageRatios` 无条件更新，`decodeRatios` 仅在 `isBoxLimited`（`:126`）为真时回填。
  - 纵向长图模式不做就地缩放，双击打开全屏缩放覆盖层（`zoomOverlayIndex`，`ReaderScreen.kt:416`）；章节种子仅接受不窄于 `MIN_SEED_ASPECT_RATIO = 0.5f`（`ReaderPageLogic.kt:40`）的常规页比例。
- **页码真源**：页码、跳页冻结与可见页回写统一收口于 `ReaderPagePlan`（`core/src/main/kotlin/com/jm3/core/reader/ReaderPagePlan.kt:17`、`jumpTo` `:53`、`settleJump` `:65`、`visiblePage` `:74`）。

### 5.4 动效、共享元素与大屏窗口

- **封面骨架与微光**：骨架屏仅挂载于 `AlbumCoverImage`（`ui/AlbumHero.kt:385`），外部卡片不再重复套用骨架修饰符。
- **edge-to-edge 与音量键**：`MainActivity.kt:27` 开启沉浸式，`MainActivity.kt:83-85` 将音量键分发给 `ReaderVolumeKeys`（`ReaderScreen.kt:156`）。长图缩放覆盖层打开时须消费并返回 `false` 屏蔽翻页，销毁时须校验回调归属后再置空。
- **共享元素飞行**：列表侧挂 `.albumHeroCover`（`AlbumHero.kt:538`）、详情侧挂 `.albumHeroHeader`（`:642`），圆角读取 `LocalSkin.current.shapes.cardCorner`，行卡缩略图 `HERO_THUMB_CORNER_DP = 8f`（`:425`）、角标 `HERO_BADGE_CORNER_DP = 6f`（`:428`）。详情路由转场固定为纯 alpha 渐变（`albumDetailTransitionFor`，`Jm3App.kt:415`），禁止改为水平位移动画。
- **窗口断点与大屏策略**：窗口尺寸统一读取 `rememberWindowDpSize()`（`ui/AdaptiveLayout.kt:76`），整数断点使用 `Dp.toLayoutDpInt()`（`:83`）向下取整，禁止使用 `Configuration.screenWidthDp`。大屏布局阈值集中于 `WideLayoutPolicy`（`core/src/main/kotlin/com/jm3/core/layout/WideLayoutPolicy.kt:22`）：中屏 `600 dp`（`:25`）、大屏 `840 dp`（`:28`），详情双栏、行卡两列、网格列数与侧栏宽高双条件（`:139-145`）均从此处派生。

---

## 六、Agent 工作流与边界测试

### 6.1 改动权限与 Issue 规范

- **拆单四要素**：需求描述、验收标准、架构约束（允许修改范围与复用件）、测试清单。仓库当前无 `.github` 目录，本地规范以本文与 `AGENTS.md` 为准。
- **Agent 可直接修改**：`:app` 屏幕与组件、`:core` 纯函数与单测、文案与样式微调、本文规则与行号同步更新。
- **必须先请示用户**：修改 `Jm3Gateway` / `SessionAuth` 接口签名、改动 `DownloadTask` 状态机、调整模块依赖方向、修改签名与 `proguard-rules.pro` 放宽规则，以及新增对「禁止」条款的例外。

### 6.2 完成定义与 TDD 流程

每次提交前必须依次完成以下步骤（缺一不可）：

1. **红绿重构**：业务逻辑、数据处理与缺陷修复须先写测试，跑出真实业务断言失败（红灯）后再编写最小实现转绿，绿灯后严禁改弱断言。纯 UI 样式调整可豁免先行测试，但其底层状态计算仍需单测覆盖。
2. **构建验证**：执行 `.\gradlew.bat :app:assembleDebug`（纯 `:core` 改动至少执行 `:core:compileKotlin`）。若因并发锁等客观原因未跑，汇报时须如实说明「未验证」。
3. **测试通过**：执行 `.\gradlew.bat :core:test :app:testDebugUnitTest`。
4. **日志与提交**：在 `CHANGELOG.md` 的 `## [未发布]` 下按「新增 / 修复 / 优化」补充用户可读条目（不泄露内部类名与哈希）；使用纯文本中文 Conventional Commits 逐个逻辑变更提交，严禁包含 Emoji。
5. **四项汇报**：汇报修改内容、修改原因、验证命令与结果、是否触碰既有硬约束。

### 6.3 全仓测试规模

测试基线（2026-11-12 实跑 `:core:test` 与 `:app:testDebugUnitTest`，以 `build/test-results/**/TEST-*.xml` 为准）：

- **`:core`**：73 个测试类 / 679 个测试用例（位于 `core/src/test/kotlin/com/jm3/core/`）。
- **`:app`**：91 个测试类 / 636 个测试用例（位于 `app/src/test/kotlin/com/jm3/app/miuix/`）。
- **全仓合计**：164 个测试类 / 1,315 个测试用例，全部通过，0 失败、0 跳过。

典型纯逻辑边界测试规模：`AccountManagerTest`（36）、`AlbumHeroLogicTest`（31）、`ReaderPageLogicTest`（31）、`MappersTest`（29）、`CheckInLoadPolicyTest`（29）、`ReaderProgressCodecTest`（27）、`AlbumHeroHostTest`（26）、`CouiWorkCardLayoutTest`（26）、`ReaderZoomLogicTest`（22）、`DownloadQueueTest`（21）。

### 6.4 核心边界测试清单

新增或修改以下能力时，对应测试类必须同步覆盖并保持绿灯：

- **下载状态机与队列**：`DownloadTask` 非法状态转移抛出异常 → `DownloadTaskTest`；同话去重、失败重入、并发上限 2 与取消不耗重试 → `DownloadQueueTest`（21） / `DownloadContractTest`；断点恢复与台账分离写 → `DownloadRecoveryTest` / `DownloadLedgerTest` / `DownloadManagerPersistenceTest`。
- **下载文件与前台策略**：页文件原子替换、路径校验且失败不留 `.tmp` → `FileDownloadStoreTest`（15）；窄接口取页契约 → `GatewaySourcesTest`；页码清单编解码 → `DownloadPagesCodecTest`；通知文案与条目呈现 → `DownloadPresentationTest` / `DownloadStatusSemanticsTest` / `DownloadRowTest` / `DownloadRowTextTest`；前台策略与退避 → `ForegroundPolicyTest` / `ForegroundRunnerTest`。
- **分页、封面与协议映射**：1 基页码换算 → `PageNumbersTest`；翻页计算 → `PagingBehaviorTest`；去重与触底距离 → `PagedFeedTest`（17）、`SearchPagesTest`、`CommentPagesTest`、`SerializationPageTest`；分割还原 → `ScrambleTest`；封面三态与缓存键互逆 → `CoverResolverTest`（13）、`CoverLocatorTest`；域名清洗与线路切换缓存 → `DomainsTest`、`CustomDomainPoolTest`、`DomainPoolCacheTest`、`ApiDomainTest`；章节分组与标签 → `ChapterGroupsTest`、`ChapterLabelsTest`、`NotificationBodyTest`。
- **会话、凭据与更新**：会话编解码 → `SessionCodecTest`；加密凭据落盘、坏数据自清与 Keystore 密钥重建 → `CredentialStorageTest`、`CredentialStoreTest`；取消异常重抛 → `CancellationTest`；反射字段契约与降级路由 → `LibrarySessionReflectionTest`、`LibraryBridgeRoutingTest`；静默重登、并发竞态与 3 次失败清凭据 → `AccountManagerTest`（36）；启动闸门与失效通知 → `AuthCallTest`；未读角标保留与纪元保护 → `UnreadBadgeTest`；版本等价、更新下载校验与设置归一 → `VersionTextTest`、`UpdateCheckerTest`、`UpdateDownloaderTest`、`ReleaseJsonTest`、`UpdateSettingsTest`、`SettingsRepositoryTest`、`SettingsNormalizationTest`、`SettingsKeyStoreAdapterTest`。
- **阅读器、转场、布局与皮肤**：缩放与边缘翻页 → `ReaderZoomLogicTest`（22）；页框比例与解码上限 → `ReaderPageLogicTest`（31）、`ReaderPageBackfillTest`（8）；页码计划与跳页冻结 → `ReaderPagePlanTest`（12）；进度编解码 → `ReaderProgressCodecTest`（27）；预取步进 → `ReaderPrefetchTest`；共享元素几何、宿主状态机与缓存尺寸 → `AlbumHeroLogicTest`（31）、`AlbumHeroHostTest`（26）、`AlbumHeroStateMachineTest`（13）、`AlbumHeroCoverSizeTest`（4）；详情状态容器 → `AlbumDetailStateHolderTest`（16）；结局翻译与签到策略 → `ScreenLoadPlanTest`、`ListOutcomeTest`、`NovelActionNoticeTest`、`CheckInLoadPolicyTest`（29）；玻璃表面与高光计划 → `GlassSurfaceTest`、`InteractiveHighlightTest`；COUI 组件、字阶、通知滑动与阅读器逻辑 → `CouiPageStateTest`、`CouiTypographyTest`、`CouiForumRepliesTest`、`CouiHistoryGroupingTest`、`CouiNotificationsSwipeTest`、`CouiNovelDetailChaptersTest`、`CouiReaderLogicTest`（13）；大屏与底栏导航策略 → `WideLayoutPolicyTest`（15）、`BottomBarPreferencesTest`（9）、`WideScreenNavigationTest`（8）。

---

## 附录 A：已定决策与理由

后续改动若与以下既定决策冲突，必须先更新决策再修改代码，禁止暗中绕过：

1. **阅读器缩放选用 `telephoto`**：使用 `me.saket.telephoto:zoomable-image-coil3:0.19.0`（`gradle/libs.versions.toml:16`、`:30`），以同时获得分块超采样与缩放感知解码，避免放大模糊。
2. **手势让渡显式关闭翻页拦截**：Miuix `PagerSwipeNode` 在 `Initial` 阶段判消费，而 `telephoto` 在 `Main` 阶段消费。因此横向 `HorizontalPager` 恒挂 `.pagerGestureOverride(pagerState, enabled = !pageZoomed)`（`ReaderScreen.kt:927`），防止平移与翻页同时触发。
3. **放大贴边翻页采用 `Initial` 旁路**：`telephoto` 越过触摸阈值后会消费全部位移，故在 `ReaderPage` 的 `PointerEventPass.Initial` 阶段采集原始位移并减去内容偏移，松手 `120 ms` 后复核内容静止方判定翻页。
4. **长图模式禁止就地缩放**：层内缩放会与纵向列表滚动冲突并裁切内容，故双击弹出全屏缩放覆盖层，单击或返回键退出。页框跟随页内真实比例，避免 3:4 强制裁切导致条幅拉伸模糊。
5. **阅读器图片强制走磁盘快照**：`telephoto` 分块超采样依赖 `FileImageSource` 的 `diskCacheKey`。禁止将内存 `okio.Buffer` 作为常规路径返回；缓存键统一由 `ReaderKeyer.keyOf` 生成。
6. **下载前台服务只管前台态与通知**：队列调度留在 `DownloadQueue`，生命周期决策由纯类 `ForegroundPolicy` 产出，防抖与拉起失败退避由 `ForegroundRunner` 驱动，不引入 `WorkManager`。
7. **通知节流 `300 ms` 与配额退避**：常规进度按 `300 ms` 节流，终态立即刷新。`dataSync` 6 小时配额耗尽后暂停在途任务并留存可见通知，不做保活；系统拒绝拉起前台服务后按 `5 s` 退避重试一次。
8. **许可证隔离规范**：仅允许复制 `Apache-2.0`、`MIT` 或 `ISC` 源码并保留 SPDX 标注；`GPL-3.0`/`AGPL-3.0` 项目仅采思路、自行重写。仓库主体为 `MIT`，其中 `app/src/main/kotlin/com/jm3/app/miuix/ui/` 下共 60 个文件带 `Apache-2.0` 头（含 `glass/` 14 个、`coui/` 34 个、`ui/` 与 `route/` 12 个），`CouiIcons.kt` 带 `ISC` 头，`COUI-Kit` 按 `MPL-2.0` 依赖接入（详情见 `NOTICE`）。
9. **提升型持续通知按 API 36.1 判定**：`setRequestPromotedOngoing` 仅存在于 Android 16.1（API 36.1），必须先判 `SDK_INT >= 36` 再判 `SDK_INT_FULL >= BAKLAVA_1`（`DownloadNotifier.kt:216`、`:228`），避免 Android 16.0 触发 `NoSuchMethodError`。
10. **共享元素采用自绘覆盖层**：因 `miuix-nav 0.9.4` 转场无 `AnimatedVisibilityScope`，采用 `onGloballyPositioned` 登记起止矩形并在 `AlbumHeroOverlay` 插值绘制，使用缩略图地址与 `crossfade(false)`。拿不到起止矩形时退化为普通转场。
11. **静默重登连续失败 3 次清凭据**：`AccountManager` 持久化 `renewFailureCount`，连续 3 次失败后清除已存加密凭据，避免每次冷启动重复无效登录；任一次重登成功立即归零，兼容短暂断网。
12. **`:core` 日志单一出口 `Jm3Log`**：`:core` 不引入 Android 日志依赖，反射成员缺失时统一经 `Jm3Log` 输出成员名与异常类名，`:app` 在初始化时桥接至 `android.util.Log.w`。
13. **会话注入由调用方传入网关实例**：`AccountManager.inject` 直接接收已判定的 `SessionAuth` 实例，防止线路并发切换瞬间将 Cookie 注入废弃网关；启动闸门由 `openStartupGate()` 唯一放行。
14. **网关调用唯一入口 `AuthCall`**：将启动闸门等待、IO 线程切换与单次失效通知收敛在 `AuthCall.call`，消除界面层重复的 401 判断与线程切换样板代码。
15. **屏幕仅认加载结局**：`SessionOutcome` 至界面状态由 `planLoad`（`ScreenLoadPlan.kt:28`）统一翻译，有内容时保内容给提示、无内容时整屏呈现失效或错误，禁止界面直接引用 `LOGIN_EXPIRED_MESSAGE`。
16. **分页状态机统一为 `PagedFeed`**：`PageSlice` 与 `PagedFeed` 负责按 ID 去重、游标推进与触底判定（`PREFETCH_DISTANCE = 3`），翻页算法归入 `PagingBehavior`。
17. **章节分组以首章 ID 为身份**：`ChapterGroups` 按原始正序每 10 章切分一次，倒序展示仅反转组序与组内条目，避免切排序后折叠展开态错位。
18. **下载状态语义枚举与展示分离**：活动/终态/重排判定直接挂于 `DownloadStatus` 枚举，书架行与通知栏展示形状分别由纯函数 `presentRow` 与 `present` 生成。
19. **封面三态模型 `Cover`**：`Cover` 区分 `Ready`、`Unready` 与 `None`（分别对应有址、域名未就绪、无封面），`CoverResolver` 与 `coverCacheKeyText` 互逆校验。列表 DTO 现阶段仍透传可空地址。
20. **协议反射收口于 `LibraryBridge`**：除 `CustomDomainPool` 所需的 2 个域名管理成员外，其余 8 个协议内部状态反射全部收口至 `ReflectiveLibraryBridge`；取页测试隔离至两方法的 `PhotoSource`。
21. **组装根只做装配**：`Jm3Graph` 仅负责六大域按序初始化，页面全面通过 `ScreenDeps` 单容器获取依赖，禁止半注入半全局的混合签名。
22. **版本号比对出口唯一化**：版本号规范化、解析与等价判断统一使用 `VersionText`（`core/update/VersionText.kt:11`），设置项去空处理明确命名为 `VersionSettingText`（`SettingsRules.kt:59`）。
23. **下载页落盘原子替换**：`FileDownloadStore` 先写入 `.tmp` 临时文件并加锁执行 `Files.move(..., REPLACE_EXISTING)`，异常时清理 `.tmp`，满足跨平台重下覆盖契约。
24. **复杂屏幕状态进 Holder**：`AlbumDetailStateHolder` 将详情页复杂状态与交互抽离至纯 Kotlin 容器，确保核心界面业务脱离 Compose 运行时直接测试。
25. **玻璃表面统一角色计划表**：除底栏动态按压与录制层外，其余玻璃表面统一经 `glassRoleSpec` 与 `resolveGlassSurface` 按「角色 + 降级档位」计算模糊与遮罩参数。
26. **跨文件换算单一出口**：比例回填（`readerPageBackfill`）、预取步进（`advancePrefetch`）、版本解析与反射成员等规则均保留唯一纯函数出口，避免双写漂移。

---

## 附录 B：明确暂缓事项与验证缺口

1. **Baseline Profile / macrobenchmark 暂不接入**：缺乏稳定常驻多设备采样矩阵，且仅提供冷启动度量。后续若引入，以独立 `:benchmark` 模块与冷启动耗时作为验收指标。
2. **ktlint / detekt 暂不接入**：全仓历史代码统一格式化会产生巨量无关差异，现阶段以构建检查、单测与人工审查把关。
3. **放大边缘翻页暂不做跟手拖拽**：跟手拖拽需重构 `HorizontalPager` 滚动源，改动面大于当前收益，维持松手判定后弹性翻页方案。
4. **运行验证缺口**：
   - 已真机验证项：已在小米 15（`targetSdk 36`）实测阅读器缩放与边缘翻页、双击整屏缩放覆盖层、`dataSync` 前台服务与进度通知（含提升型通知分支）。
   - 未真机验证项：全仓目前主要依赖编译、164 个测试类（1,315 个用例）的 JVM 单测与 Release 打包（R8 + lintVital）验证。以下路径仍待真机逐项补测：共享元素飞行轨迹与返回落点、封面骨架屏下沉后的转场观感、主界面切换标签后的滚动状态保存、`onTimeout` 6 小时超时回调、窄屏与多折叠窗口下的双栏/列表边界、Android 13+ 通知拒权回退路径、超长条漫真机内存与清晰度实测、Keystore 密钥损毁重建日志路径、真实会话过期后的协议库自动重登链路。
5. **COUI 皮肤暂不实现下拉刷新**：当前 `COUI-Kit` 未提供下拉刷新容器，COUI 页面刷新入口统一由错误态重试与页面操作按钮承担，不在页面层手拼手势容器。
6. **小说列表不做分页**：上游小说列表接口一次返回全量数据且无分页参数，屏幕按全量渲染，不强套 `PagedFeed`。
