# ENGINEERING

本文件是 jm3（JM Comic Miuix 客户端）的可执行工程约束。它不描述愿景，只记录“这样做会坏、那样做才对”的判定线与佐证位置。

适用方式：

- 写代码前先过一遍“决策三问”，再按六章查对应规则。
- 规则与代码冲突时，以代码现状为准，并在同一个改动里同步修订本文件。
- 新增约束必须带真实文件:行号；无法核实的写“未核实”，不许凭印象写。

## 决策三问

任何改动前先回答，答不上来就先不写：

1. 这段逻辑属于哪一层？能进 :core 的绝不写进 :app；能写成纯函数的绝不藏在 Composable 里。:core 是纯 JVM（core/build.gradle.kts:1-4 只应用 kotlin.jvm 与 serialization），一旦混入 Android 类型就再也回不去。
2. 这段逻辑怎么测？纯判定、换算、状态机、编解码都属于“必须可测”（现有 21 个测试类在 core/src/test/kotlin/com/jm3/core/）。测不了说明分层错了，先拆再写。
3. 这次改动会不会破坏既有约束？重点看：单一网关入口、单一状态真源、Miuix 单组件库、缩放与翻页的手势优先级、release 的 R8 与签名。

## 一、State：状态归属

1. 全局单例只允许出现在 `Jm3Graph`（app/src/main/kotlin/com/jm3/app/miuix/Jm3App.kt:102）。网关、会话、设置、账号、数据库、进程级 scope 都在这里组装；屏幕代码通过参数或 Jm3Graph 读取，禁止自建平行单例。
2. 唯一长期协程作用域是 `appScope`（app/src/main/kotlin/com/jm3/app/miuix/Jm3App.kt:182，`SupervisorJob() + Dispatchers.IO`）。现有使用点：Jm3App.kt:130、:160、:182-185、:198；app/src/main/kotlin/com/jm3/app/miuix/data/download/DownloadManager.kt:56、:64、:67、:69、:141；app/src/main/kotlin/com/jm3/app/miuix/update/UpdateDownloader.kt:68、:75、:97-98；同目录 DownloadService.kt:134-135、:193；以及 app/src/main/kotlin/com/jm3/app/miuix/MainActivity.kt:68（`onStop` 里回写会话 Cookie —— 屏幕已销毁，这里只能挂进程级 scope）。新增用途必须说明为什么不能挂在屏幕的 `rememberCoroutineScope` 上。禁止 `GlobalScope`。
3. 本仓库没有 ViewModel（app/src/main 全量 grep `ViewModel` 零命中）。约定是：屏幕数据状态用 `remember` + `LaunchedEffect` 持有，网络与磁盘读写在 `withContext(Dispatchers.IO)` 内完成（例 AlbumDetailScreen.kt:156 起）。不要为了“规范”引入 ViewModel 层；要引入就是一次架构变更，走 Issue。
4. 下载任务状态的真源只有一个：`DownloadQueue.updates`（core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:29-30，`_updates` + `asStateFlow()`）。UI 只做投影，不得另建进度表、不得在 UI 里累加已完成页数。
5. 设置项与会话只经 DataStore 读写：`jm3_settings`（app/src/main/kotlin/com/jm3/app/miuix/data/SettingsStore.kt:16）与 `jm3_session`（app/src/main/kotlin/com/jm3/app/miuix/data/SessionStore.kt:11）。会话内容一律走 `SessionCodec.encode/decode` 单键 JSON（SessionStore.kt:19、:22），禁止把 cookie 拆成多个键。一次性标记类持久化（如通知权限已询问）同样归 `jm3_settings`（SettingsStore.kt:119-123 的 `notificationPermissionAsked`），自动登录用的账号密码密文也放在同一库（SettingsStore.kt 的 `credentials_blob`，单键 Base64 密文），禁止为省事另开 `SharedPreferences`。
6. 持久化接口定义在 :core，:app 只提供实现：
   - core/src/main/kotlin/com/jm3/core/settings/SettingsRepository.kt:13（含 InMemorySettingsRepository，:97）
   - core/src/main/kotlin/com/jm3/core/download/DownloadRepository.kt:15（InMemory 在 :51）、core/src/main/kotlin/com/jm3/core/download/DownloadStore.kt:9
   - core/src/main/kotlin/com/jm3/core/history/HistoryRepository.kt:13（InMemory 在 :37）
   - core/src/main/kotlin/com/jm3/core/CredentialStorage.kt:16（InMemory 在 :23；密文编解码 `EncryptedCredentialStore` 在 :84，密钥抽象 `CredentialCipher` 在 :61，Android 实现见 app/src/main/kotlin/com/jm3/app/miuix/data/CredentialStore.kt 的 `KeystoreCredentialCipher`）
   新增存储能力先加接口和 InMemory 实现，再有 Android 实现，最后接 UI。凭据是「接口 + 密文包装 + InMemory 后端」三层，Android 侧只实现 `CredentialBlobStore`（SettingsStore）与 `CredentialCipher`（Keystore），加解密与坏数据降级都在 :core 里可测。
   - 例外：线路池缓存走 `DomainPoolCache`（core/src/main/kotlin/com/jm3/core/settings/DomainPoolCache.kt，Android 实现在 app/src/main/kotlin/com/jm3/app/miuix/data/DomainPoolCache.kt）。接口刻意是阻塞的：网关装配在首屏请求之前同步执行，等不到 DataStore 的挂起读取，所以这份缓存不能进 `jm3_settings`。内容只是「上次可用线路」的快照，丢了最多多拉一次线路表。
7. Room 只用两个实体：`history` 与 `downloads`（app/src/main/kotlin/com/jm3/app/miuix/data/history/HistoryStore.kt:19、:54），数据库 `jm3.db` 版本 2 且 `exportSchema = false`（HistoryStore.kt:104；库名 jm3.db 见 Jm3App.kt:168）。改表必须写 Migration 并升版本；不许 `fallbackToDestructiveMigration`。

## 二、Network：网络与协议边界

1. 网络唯一入口是 `Jm3Gateway`（core/src/main/kotlin/com/jm3/core/Jm3Gateway.kt:45）。它的所有方法都是阻塞调用（接口注释逐条标注，见 :46、:55、:58、:61、:64），调用方必须已经在 IO 线程上：`withContext(Dispatchers.IO)` 或 `appScope`。
2. 协议库（io.github.jukomu.jmcomic）只允许出现在 :core。:app 的依赖里没有 `jmcomic-api`，它只通过 `api(libs.jmcomic.api)`（core/build.gradle.kts:18）传递可见。新增功能要新的网络能力时，先在 :core 的网关接口上加方法，不要在 :app 里直接 new 协议客户端。
3. 网关实例的切换走 `Jm3Graph.gatewayOverride` 与 `GatewayFactory`（Jm3App.kt:108-112、:210-229、core/src/main/kotlin/com/jm3/core/GatewayFactory.kt:9）。线路（域名）只能通过 `Domains`（core/src/main/kotlin/com/jm3/core/settings/Domains.kt:4）与设置项改动，禁止在屏幕里硬编码 URL。
   - 启动期的域名只有两处来源：设置页的自定义线路，以及上次刷新落盘的线路池（`DomainPoolCache`）。官方线路表由分发服务器下发，运行时在 core/src/main/kotlin/com/jm3/core/DomainTable.kt 拉取并解密，禁止把域名写进源码：服务端换表后写死的表必然过期，那时的表现是首屏直接报错而不是变慢。
   - 两处来源都空（首次安装、没填自定义线路）时回退到库自己的初始化链 —— 它是唯一还能拿到线路表的路径，成本约 3.5s；跑完立刻落盘，之后冷启动不再走这条路。
4. 图片不走网关文本接口：阅读器用 `ReaderFetcherFactory` 自取（app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderImages.kt:36-90），字节来源只有一处 —— 先 `imageLoader.diskCache.openSnapshot(cacheKey)`，命中即复用（:46-52）；未命中再取字节（本地已下载文件优先，否则网关，:54-59），随后 `openEditor(cacheKey)` → 写 `editor.data` → `commitAndOpenSnapshot()`（:61-83），返回 `ImageSource(snapshot.data, diskCache.fileSystem, cacheKey, snapshot)`；写盘失败必须 `editor.abort()` 再抛（:84-88）。
   - 关键约束：禁止把字节塞进 `okio.Buffer` 直接返回。telephoto 的分块超采样只认 `SuccessResult.diskCacheKey`，而 coil3 只在 fetch 结果源是 `FileImageSource` 时才带这个键（EngineInterceptor 里 `diskCacheKey = (fetchResult.source as? FileImageSource)?.diskCacheKey`），塞 Buffer 会让超采样永久失效、放大后发糊（同文件 :42-44、:61-67 注释）。无磁盘缓存或快照打不开时退回内存流只是兜底，不是常规路径。
   - 新增图片入口必须复用同一套 Keyer/Fetcher，否则内存与磁盘缓存会分裂。
5. 缓存键统一由 `ReaderKeyer.keyOf(data)` 生成（ReaderImages.kt:29-32，格式 `jm3-reader/<photoId>/<index>`），内存键与磁盘键共用一份，不得在别处再拼前缀。自建 `ImageLoader` 必须带磁盘缓存：ReaderImages.kt:143-148 用 `DiskCache.directory(cacheDir/reader_images).maxSizePercent(0.02)`；自建 loader 默认不带缓存，去掉它超采样直接失效。loader 是进程级共享实例（:92-123），只在网关或下载源变化时重建，页面销毁时不 `shutdown()`：Coil3 对同一目录只保留一个 DiskCache，按屏创建再按屏关闭，会在换章重叠时序下关掉新屏正在用的缓存。
6. 分页一律“按 id 去重 + 页码游标”：`PageNumbers`（core/src/main/kotlin/com/jm3/core/download/PageNumbers.kt:12）负责 1 基页码换算，搜索与评论分页各自有测试（SearchPagesTest、CommentPagesTest）。新增分页必须先在 :core 写成纯函数并测试。
7. 网络失败不得静默：`refreshUnreadNotifications` 失败时保留上一次结果而非清零（Jm3App.kt:155-164）。这类“降级”必须在注释里写清楚为什么，未写清楚的按 bug 处理。

## 三、Error 与 Coroutine

1. 禁止吞掉 `CancellationException`。范本在 core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:143-145（`catch (e: CancellationException) { throw e }`）与 :201-202（重试循环里取消不消耗重试次数）。任何 `catch` 之前先问：这里会不会吞取消？需要把异常收成 `Result` 的挂起调用一律用 core/src/main/kotlin/com/jm3/core/Cancellation.kt:11 的 `runCatchingSuspending`，它在最内层重抛 `CancellationException`，外层 `runCatching` 只看到真实失败；直接用 `runCatching { suspendCall() }` 会把取消当成业务失败吞掉。
2. 单任务失败必须隔离：worker 循环里 `catch (Exception)` 只把该任务置失败，不能让整个队列退出（core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:138-151）。
3. 状态机转移用 `check` 硬断言，不用 if 悄悄跳过：`core/src/main/kotlin/com/jm3/core/download/DownloadTask.kt:36` `check(status.canTransitionTo(next)) { "非法状态转移：$status -> $next" }`。失败可见好过状态错乱。
4. 用户可见的错误必须可重试，文案复用现成组件：全屏 `ErrorScreen(message, onRetry)`（Common.kt:197）、列表三态 `LoadMoreFooter(loadingMore, hasMore, loadMoreFailed, onLoadMore)`（Common.kt:278-297）。禁止屏幕里自造“加载失败”布局。
5. 阻塞调用不得进主线程，Composable 体内只做状态编排。用 `LaunchedEffect`/`rememberCoroutineScope` 起协程，用 `withContext(Dispatchers.IO)` 包住 IO。
6. 取消要显式：pager 翻页、缩放复位这类“跟手动画”用 `springAnimateToPage`（ReaderScreen.kt:615，另一处调用在 :347）而不是新起 Job 抢滚动状态。
7. 日志与异常只报告事实（哪个任务、哪个文件、哪次重试），不带情绪化修饰；用户提示文案统一中文，全角标点。

## 四、构建、依赖与产物

1. 版本只有一处来源：`gradle/libs.versions.toml`（:2-16 版本号，:30、:43-45 等为库别名）。模块脚本里禁止出现字面版本号。新增依赖的完整动作是：写版本、写库别名、在模块里 `implementation(libs.x)`、确认许可证、确认是否需要 keep 规则。
2. 模块方向单向：`:app` 依赖 `:core`（app/build.gradle.kts:69），反之不可。:core 保持纯 JVM（JVM 17，core/build.gradle.kts:6-15），:app 用 Java 21（app/build.gradle.kts:56-66）。不要因为 :app 用了 21 就把 :core 提上去。
3. SDK 坐标固定：`compileSdkVersion("android-37.0")`、minSdk 26、targetSdk 36（app/build.gradle.kts:21、:25、:26）。compileSdk 的写法是被依赖 AAR 的元数据要求的（同文件 :21 注释），改动前先确认依赖矩阵。
4. release 必须开 R8 与资源压缩：`isMinifyEnabled`/`isShrinkResources` + `proguardFiles`（app/build.gradle.kts:42-50，规则文件 app/proguard-rules.pro）。现有规则：keep `io.github.jukomu.jmcomic.**`（proguard-rules.pro:2）、保留 `Signature/InnerClasses/EnclosingMethod/Annotation` 属性（:4）、webp-imageio 与 AWT 的 dontwarn（:9-14）；kotlinx-serialization 与 Room 靠自带 consumer 规则（:15 注释）。新增反射式依赖（序列化、DI、注解处理产物）必须验证 release 包，缺 keep 只在 release 崩。
5. 签名密钥只从 `local.properties` 读，缺失回退 debug 签名（app/build.gradle.kts:11-17、:48）。因此“release 包”不等于“别人签的包”，发布前必须核对签名来源。任何密钥、口令、cookie 都不入库；`local.properties` 已在 .gitignore:4。
6. 构建命令固定用仓库自带的 `.\gradlew.bat`，禁止 `--offline`：曾因 `androidx.tracing:tracing-ktx:2.0.1` 未缓存导致离线构建失败。同一工作树同时只允许一个 Gradle 构建进程，并行任务要串行化构建，不要用离线参数“解决”锁冲突。
7. 版本目录与 AGP 的联动开关在 gradle.properties:7（`android.builtInKotlin=false`）与 :9（`android.newDsl=false`）。升级 AGP 或 KGP 时这两项必须成对检查，否则报插件冲突而不是编译错误。
8. 内部过程物不入库：`.scratch/`（.gitignore:13）、`implementation_plan.md`（:10）、`research/`（:11）都在忽略列表里。调研报告、计划、截图留在本地。

## 五、UI 与阅读器

1. 组件库只有一个：Miuix。:app 依赖里没有 Material3（app/build.gradle.kts:72-105），全量 grep `androidx.compose.material3` 零命中，版本目录也没有该条目。新增界面一律用 Miuix，禁止“先引一下 material3 的 Icon”。
2. 三态与常用件复用 `Common.kt`：`LoadingScreen`（:196）、`ErrorScreen`（:210）、`EmptyState`/`CenteredEmptyState`（:270、:290）、`LoadMoreFooter`（:316）、`PagerBar`/`ResultPagerBar`（:436、:430）、`BoxScope.ListScrollBar`（:344，历史与详情共用）、`Modifier.pressScale`（:487）、`rememberSelectionHaptic()`（:513）、封面骨架屏三件套 `CoverSkeletonState`/`rememberCoverSkeletonState`/`Modifier.coverSkeleton`（:565、:579、:584，`Modifier.shimmer` 在 :528）。新屏自造加载/空态、自抄滚动条或封面骨架屏视为重复实现。
3. 阅读器手势是一条有优先级的状态机，新增手势必须在这套状态机里排队（主文件 app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderScreen.kt）：
   - 缩放状态只由当前页写入（ReaderScreen.kt:593 `if (index == pagerState.currentPage) pageZoomed = zoomed`，信号源是 :1023-1025 的 `derivedStateOf` + :1026 的 `LaunchedEffect(zoomed)`），翻页即复位（状态 :291 的 `LaunchedEffect(pagerState.currentPage)`，另有 :1028-1030 在页翻走时 `resetZoom()`）。
   - pager 的横向手势在放大时被接管：`pagerGestureOverride(pagerState, enabled = !pageZoomed)` 且 `userScrollEnabled = false`（ReaderScreen.kt:578、:580），嵌套滚动交给 `PagerGestureNestedScrollConnection`（:581）。
   - 放大后拖到边界继续拖才翻页：`onHorizontalEdgeDrag`（:596）→ `decideEdgePageTurn`（判定调用 :598）→ `springAnimateToPage`（复核与动画 :613）。判定逻辑在 ReaderZoomLogic.kt:23-41，不在 Composable 里。
   - 边界位移必须在 `PointerEventPass.Initial` 自己数：telephoto 越过触摸阈值后即使内容已不能移动，也会在 Main pass 之前把所有 `positionChanged()` 消费掉（transformable.kt:245-249），父节点在 Main pass 只能看到 `isConsumed == true`。手势采集实现在 ReaderScreen.kt:1041-1074，位移口径是「累计手势位移 − 内容偏移变化」（`EdgeDragTracker`，ReaderZoomLogic.kt:58-82），不是逐帧判断是否被消费。
4. 阈值与判定必须是纯函数并集中：`ZOOMED_THRESHOLD = 0.02f`、`EDGE_PAGE_DISTANCE_RATIO = 0.18f`、`CONTENT_PIN_TOLERANCE_PX = 24f`、`EDGE_CONFIRM_DELAY_MS = 120L`、`EDGE_CONFIRM_MOVE_PX = 8f`（ReaderZoomLogic.kt:4、:10、:43、:46、:49），`isPageZoomed`（:13-14）、`decideEdgePageTurn`（:23-41）与 `EdgeDragTracker`（:58-82）已有真实调用点（ReaderScreen.kt:598、:1024、:1046）。不许把这些数字散落回 Composable。
5. 解码尺寸必须跟随这一页的真实宽高比，且与预取同源：尺寸由纯函数 `readerPageDecodeSize(pageWidthPx, aspectRatio)` 算（app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderPageLogic.kt:51：宽固定为页宽、高按真实比例，再等比收敛到 `MAX_PAGE_DECODE_EDGE_PX = 8192` 与 `MAX_PAGE_DECODE_PIXELS = 4_000_000` 两条硬上限，:29、:26，ARGB_8888 约 16 MB），展示侧 `decodeSize = readerPageRequestSize(pageWidthPx, ratio)`（ReaderScreen.kt:637）与预取侧 `.size(readerPageRequestSize(...))`（ReaderScreen.kt:884）走同一入口，否则内存缓存键不同、预取等于白解。缩放路径仍只挂 listener 不写 `size`（ReaderScreen.kt:1005-1016，写死尺寸会让放大后发糊）。写死 3:4 的代价：800x20000 的条幅会被解成约 58x1440 再拉满整宽，约 18.6 倍上采样，肉眼看就是糊。改这一处会同时影响清晰度、内存与缓存命中率。
6. 纵向长图模式不要做就地缩放：页框按真实宽高比布局（`Modifier.aspectRatio(aspectRatio)`，ReaderScreen.kt:1113），双击改为打开整屏覆盖层（状态 :294、进入 :649、渲染 :699-707 的 `ReaderPage(zoomEnabled = true)`），单击或返回键退出。层内就地缩放会与 LazyColumn 的竖向滚动抢手势，页框一旦按内容比例抖动，放大后的内容还会被裁掉。
   占位比例三级回退：本页已解出的比例 → 同章种子 `chapterRatioSeed` → 3:4 兜底，判据在 `readerPagePlaceholderRatio`（ReaderPageLogic.kt:67），回填在 `onIntrinsicRatio`（ReaderScreen.kt:642-646）。改比例相关行为必须同时看占位、解码尺寸与页面内存三处；同类“想当然”的交互改动先在真机上验证再提。
7. 动效收敛：缩放跟随用 telephoto 自带的 `rememberZoomableImageState` + `resetZoom`，按压缩放用 `Modifier.pressScale(0.96f)`（默认值见 Common.kt:487），触感用 `rememberSelectionHaptic()`（:513）。不新增自研动画框架；页面切换用 `AnimatedContent` + 主题内置的 `tween`/`fadeIn`/`scaleIn` 组合（app/src/main/kotlin/com/jm3/app/miuix/ui/MainScreen.kt:203-215），微光骨架屏只有 `Modifier.shimmer` 与 `Modifier.coverSkeleton` 两个入口，颜色与周期是常量，不在调用点写死。
8. 边缘到边与返回：`enableEdgeToEdge()` 在 app/src/main/kotlin/com/jm3/app/miuix/MainActivity.kt:25 统一开启，屏幕自己处理 `innerPadding`；音量键翻页由 `onKeyDown` 交给 `ReaderVolumeKeys`（MainActivity.kt:72-74；ReaderVolumeKeys 定义在 app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderScreen.kt:123，dispatch 在 :127），不要再在屏幕里重复拦截。阅读器在长图覆盖层等浮层打开时必须让音量键返回 false，且 `onDispose` 只在 handler 仍是自己时才置空（ReaderScreen.kt:490-518），否则换章重叠会把新屏注册的 handler 清掉。
9. 共享元素飞行的规矩：列表侧封面挂 `.albumHeroCover(albumId, url, cornerDp)`、详情页头图挂 `.albumHeroHeader(albumId, url, cornerDp)`（app/src/main/kotlin/com/jm3/app/miuix/ui/AlbumHero.kt:202、:225），`cornerDp` 必须等于该卡片真实的 `squircleClip` 圆角契约常量（集中定义在 app/src/main/kotlin/com/jm3/app/miuix/ui/AlbumHero.kt:304-307：卡片/头图 `HERO_CARD_CORNER_DP = 16f`（`MIUIX_CARD_CORNER_DP` 同值，行卡封面顶满卡片后也用这个）、历史行 `HERO_HISTORY_ROW_CORNER_DP = 8f`、收藏行 `HERO_FAVORITE_ROW_CORNER_DP = 6f`），严禁手写近似字面量，防止飞行中出现两端圆角突变。详情页路由转场必须是纯 alpha（Jm3App.kt 的 `albumDetailTransition`），不许改成水平位移：hero 矩形取自 `LayoutCoordinates.positionInRoot()`，页面滑动会把 graphicsLayer 的平移算进矩形，飞行端点每帧漂移。

## 六、Agent 工作流与测试

1. 拆 Issue 四项：需求（要什么）、验收（怎么判定完成）、架构约束（允许改哪些文件、必须复用哪些现成实现）、测试要求（哪些用例必须存在）。仓库目前没有 `.github` 目录（无 Issue 模板、无 CI），新建模板时按这四项写。
2. Agent 可直接做的：:app 屏幕与组件、:core 新增纯函数与其测试、文案与样式、本文件的规则补充。
   必须先问人的：`Jm3Gateway` 接口签名（core/src/main/kotlin/com/jm3/core/Jm3Gateway.kt:45）、`DownloadTask` 状态机、依赖方向、签名与密钥配置、R8 规则放宽、本文件“禁止”类条款的例外。
3. 完成定义（缺一项不算完成）：编译通过 → 相关测试通过 → CHANGELOG 更新 → 一次一个逻辑变更的提交。
   - 编译：`.\gradlew.bat :app:assembleDebug`；纯 :core 改动至少 `:core:compileKotlin`。
   - 测试：`.\gradlew.bat :core:test` 与 `.\gradlew.bat :app:testDebugUnitTest`（JUnit Platform，core/build.gradle.kts:24、:28-30，app/build.gradle.kts:68-70、:104）。
   - 先确认红灯：写完测试先跑一次，必须看到业务断言失败（例如 `15 tests completed, 1 failed`），再用最小实现转绿。编译错误、导入错误、类型错误不算红灯，不许拿它们冒充。绿灯后禁止改弱期望值来迁就实现。
   - 纯 UI 样式改动可只保证编译，但涉及状态与数据的部分仍要有测试。
   - 如果因为并发构建无法验证，必须在汇报里明说“未验证”和原因，不许假装通过。
4. 测试放哪：`:core` 逻辑写 `core/src/test/kotlin/com/jm3/core/<包名>/<被测类>Test.kt`，`:app` 纯逻辑写 `app/src/test/kotlin/com/jm3/app/miuix/.../<被测类>Test.kt`，命名 `XxxTest`。现状：`:core` 37 个测试类 / 261 用例（最重的是 `download/DownloadQueueTest.kt`，约 18 KB，另有 `download/DownloadRecoveryTest.kt`、`CredentialStorageTest.kt`、`CancellationTest.kt`、`LibrarySessionReflectionTest.kt`），`:app` 20 个测试类 / 192 用例（`AccountManagerTest.kt` 23、`AlbumHeroLogicTest.kt` 24、`ReaderZoomLogicTest.kt` 22、`AlbumHeroHostTest.kt` 20、`ReaderPageLogicTest.kt` 16、`AlbumHeroStateMachineTest.kt` 12、`DownloadTaskMapsTest.kt` 11、`DownloadThrottleTest.kt` 8、`GlassFloatingActionBarTest.kt` 8、`AlbumHeroRegistrationTest.kt` 7、`NovelChapterLogicTest.kt` 7、`AlbumCoverCacheKeyTest.kt` 5、`HomeSearchStateTest.kt` 5、`UpdateFormatTest.kt` 5、`AlbumHeroCoverSizeTest.kt` 4、`ChapterGroupToggleTest.kt` 4、`CheckInSummaryTest.kt` 4、`WorkCardTokensTest.kt` 3、`AlbumHeroBackwardFlightTest.kt` 2、`AlbumHeroCornerContractTest.kt` 2，合 192 用例；全工程共 453 用例）。数字变了就同步改这里，别让它过期。
5. `:app` 已有测试源集：`testImplementation(kotlin("test"))`（app/build.gradle.kts:104）与 `tasks.withType<Test>().configureEach { useJUnitPlatform() }`（app/build.gradle.kts:68-70），现有 `app/src/test/kotlin/com/jm3/app/miuix/ui/ReaderZoomLogicTest.kt` 共 22 个用例，覆盖 `isPageZoomed` 的 null / 0f / 阈值边界，以及 `decideEdgePageTurn` 的页宽非法、页数非法、位移与速度都不达标、位移达标、仅甩动达标、距离达标时忽略反向甩动、首页无上一页、末页无下一页、阈值随页宽缩放。
   心智模型：Composable 只做编排，判定逻辑提成 `*Logic.kt` 纯函数（范例 app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderZoomLogic.kt），测试打在纯函数上。不要为了“能测”去写假 Composable 测试，也不要把判定塞回 Composable 再喊测不了。
6. 边界清单（新增或改动这些逻辑时，测试必须覆盖；每条都已存在对应断言或测试类）：
   - 状态机非法转移必须抛错：core/src/main/kotlin/com/jm3/core/download/DownloadTask.kt:36 → DownloadTaskTest。
   - 同 photoId 去重、FAILED 重入从 0 重来：core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:44-65 → DownloadQueueTest。
   - 并发上限 2、重试 2 次、取消不消耗重试：core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:25、:197-208 → DownloadQueueTest / DownloadContractTest。
   - 断点续传与重启恢复：DownloadRecoveryTest、DownloadRepositoryTest。
   - 页码换算 1 基且越界安全：PageNumbers.kt:12 → PageNumbersTest。
   - 分页结果按 id 去重与游标推进：SearchPagesTest、CommentPagesTest。
   - 会话编解码往返与坏数据降级：SessionCodec.kt:7 → SessionCodecTest。
   - 分割图还原算法：Scramble.kt:14 → ScrambleTest。
   - 封面 URL 与域名拼接：CoverUrls.kt:4、Domains.kt:4 → CoverUrlsTest、DomainsTest。
   - 阅读器缩放判定与边缘翻页阈值：app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderZoomLogic.kt:13-14、:23-41、:58-82 → ReaderZoomLogicTest（22 用例；改阈值、改判定分支或改 `EdgeDragTracker` 口径必须同步改这里）。
   - 共享元素飞行几何与宿主生命周期：app/src/main/kotlin/com/jm3/app/miuix/ui/AlbumHeroLogic.kt:41-121 → AlbumHeroLogicTest（24 用例；改 `HERO_MIN_TRAVEL_PX`、`HERO_FLIGHT_DURATION_MS`、`HERO_PENDING_TIMEOUT_MS` 或 `shouldRunHero` 判据必须同步改这里，且超时值必须大于飞行时长）；宿主契约与生命周期流转 → AlbumHeroHostTest（20 用例，覆盖正反向飞行起止矩形/圆角对调、URL 透传、超时取消复原、未登记来源拦截与头图注销快照降级，以及覆盖层就绪门控：就绪前两端保持可见、就绪后隐藏、错 id 与结束回调不误伤）。封面缓存契约（显式 `MemoryCache.Key` + 固定解码尺寸，三端共用同一条内存缓存）→ AlbumHeroCoverSizeTest（4 用例；改 `HERO_COVER_WIDTH_DP`/`HERO_COVER_HEIGHT_DP` 或尺寸换算必须同步改这里）。
   - 章节标签与通知文案：ChapterLabels.kt:10 → ChapterLabelsTest、NotificationBodyTest。
   - 设置项映射与默认值：core/src/main/kotlin/com/jm3/core/settings/SettingsRepository.kt:13 → SettingsRepositoryTest。
   - 凭据落盘必须是密文、坏数据必须自清：core/src/main/kotlin/com/jm3/core/CredentialStorage.kt:16（`InMemoryCredentialStorage` :23、`EncryptedCredentialStore` :84、`CredentialCipher` :61）→ CredentialStorageTest（覆盖落盘内容不含明文、解密失败即 `clear()` 并返回 null、`clear` 后读空、`SavedCredentials.toString` 不泄密码）。
   - 取消异常不得被吞：core/src/main/kotlin/com/jm3/core/Cancellation.kt:11 → CancellationTest（`CancellationException` 必须原样抛出，业务异常仍收成 `Result.failure`）。
   - 长图页框比例与解码上限：app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderPageLogic.kt:37、:51、:67 → ReaderPageLogicTest（16 用例；改 `MAX_PAGE_DECODE_EDGE_PX`、`MAX_PAGE_DECODE_PIXELS` 或比例回退链必须同步改这里）。
   - 库反射契约：core/src/test/kotlin/com/jm3/core/LibrarySessionReflectionTest.kt（字段名与类型对得上真实 jar；prime 之后能解出原密码、forget 之后解不出）。
   - 会话续期与 Cookie 回写：app/src/main/kotlin/com/jm3/app/miuix/data/AccountManager.kt → AccountManagerTest（23 用例；同网关只静默重登一次、并发续期只登一次、登出后不被回写、回写内容与顺序无关）。
7. 断言纪律：只测公共入口；不打桩自身控制的协作者；不用 `Thread.sleep` 等时序；一个用例只表达一个业务规则；计算与规则逻辑至少用两组差异数据（常规 + 边界）验证。
8. 提交与 CHANGELOG：提交信息用 Conventional Commits（如 `feat(reader): 支持放大后边缘拖拽翻页`），一个逻辑变更一次提交，不混入生成物与过程物。CHANGELOG 只写进“未发布”段并按 新增/修复/优化 分类（CHANGELOG.md:3、:5），不改历史版本区块。

## 附录 A：已定决策与理由

后续改动若与这些决策冲突，先推翻决策再改代码，不许悄悄绕过。

1. **阅读器缩放用 telephoto，不用 net.engawapg.lib:zoomable**。坐标 `me.saket.telephoto:zoomable-image-coil3` 0.19.0（Apache-2.0），见 gradle/libs.versions.toml:16、:30 与 app/build.gradle.kts:85。理由是它同时给到分块超采样与缩放感知解码，一次依赖解决“放大发糊”；pixez 那条 zoomable 路线缺超采样。
2. **手势让渡必须显式关掉 Miuix 的翻页拦截**。Miuix 的 `PagerSwipeNode` 在 PointerEventPass.Initial 判 `change.isConsumed`，而 telephoto 在 Main pass 消费，Initial 先于 Main，因此放大时保持 `enabled = true` 会“一边平移一边翻页”（.tools/miuix-src/x/commonMain/top/yukonga/miuix/kmp/utils/PagerGestureUtils.kt:168-198）。所以 HorizontalPager 恒为 `.pagerGestureOverride(pagerState, enabled = !pageZoomed)`（app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderScreen.kt:578）。
3. **放大后“内容到边界再翻页”由自研 Initial-pass 旁路实现**：telephoto 一旦越过触摸阈值就无条件消费全部位移（内容到边界也不放手），Main-pass 拿不到未消费事件，所以父层改在 `PointerEventPass.Initial` 抢先取原始位移，用「累计手势位移 − 内容偏移变化」得到贴边空拖量，松手 120ms 后再复核内容是否仍在移动，仍在动就判为平移（ReaderScreen.kt:1041-1074 + ReaderZoomLogic.kt:23-41、:58-82）。不跟手、不做橡皮筋。
4. **长图模式不就地缩放**：层内缩放会与 LazyColumn 竖向滚动抢手势，页框按内容比例抖动时放大后的内容还会被裁掉；改为双击进整屏覆盖层（ReaderScreen.kt:294、:649、:699-707），单击或返回键退出。页框比例本身不写死 3:4：一页一比例、解码尺寸跟随真实宽高比（ReaderPageLogic.kt:51、:67；旧方案的代价见第五章第 5 条），覆盖层这条路保留不变。
5. **图片必须经 DiskCache 快照复用，禁止塞 okio.Buffer**（理由与判定位置见第二章第 4 条）。`ReaderKeyer.keyOf` 是内存键与磁盘键的唯一来源（ReaderImages.kt:29-32），不许在别处拼字符串。
6. **下载前台服务只做“持有前台状态 + 展示通知”，不做调度**：调度与状态真源仍是 DownloadQueue，服务生命周期唯一驱动量是活动任务数（DownloadService.kt:131-162）。不引入 WorkManager 之类的新依赖。
7. **通知进度节流 300ms，终态立即刷新**（DownloadThrottle，DownloadNotifier.kt:237；周期常量 :35）；dataSync 累计 6 小时配额用尽时把在途任务置 PAUSED、退出前台并留一条可见通知（DownloadService.kt:66-82 → DownloadManager.kt:164），不做保活：前台服务启动失败时不再定时重试，只等下一次活动任务数变化（DownloadService.kt:131-162 的 `countChanged`）。
8. **许可证口径**：只有 Apache-2.0 / MIT 的代码可直接复制；GPL-3.0 / AGPL-3.0（含 EhViewer 主仓、yunxi、cu guis）一律只采思路、自行重写，不粘贴源码文本、不照搬结构命名。仓库整体以 MIT 分发（LICENSE:1），但 app/src/main/kotlin/com/jm3/app/miuix/ui/glass/ 下 12 个文件（BackdropSupport、CombinedBackdrop、DampedDragAnimation、FloatingBottomBar、FloatingBottomBarItems、FloatingBottomBarLayers、GlassPullToRefresh、GlassScaffold、InnerShadow、InteractiveHighlight、Lens、Vibrancy）保留 `// SPDX-License-Identifier: Apache-2.0` 头，按 Apache-2.0 分发；再复制第三方代码时必须同步更新 LICENSE、本文件与依赖清单，不许含糊其辞。
9. **提升型持续通知必须按 API 36.1 判版本**：`setRequestPromotedOngoing` 的 `since` 是 36.1，`setShortCriticalText` 是 36.0，共用 `SDK_INT >= 36` 一个守卫会让 Android 16.0 直接 `NoSuchMethodError` 崩掉前台服务。判定写成 `Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1`（DownloadNotifier.kt:175-182），前一段短路避免 `SDK_INT_FULL` 的 `NoSuchFieldError`；`BAKLAVA_1` 是编译期内联常量，不会在旧系统上触发字段解析。
10. **共享元素飞行自绘 overlay，不用 Compose 原生 `Modifier.sharedBounds`**。原生方案要求两端节点处于同一个 `AnimatedVisibilityScope`，或在 `SharedTransitionLayout` 下切换 `AnimatedVisibility`；jm3 的导航是 miuix-nav 0.9.4，转场靠 `graphicsLayer` 直接改写（`NavTransition.transformEntry`），源码里既没有 AnimatedVisibilityScope 也没有 EnterTransition 接线，原生 sharedBounds 无处挂载；引入 compose-destinations 或 material-motion 只为动效不划算。因此改为：列表与详情各自用 `onGloballyPositioned` 把矩形登记进 `AlbumHeroHost`（AlbumHero.kt:47-51、:202、:225），飞行用一个覆盖在 NavDisplay 之上的 `AlbumHeroOverlay` 按 `Animatable` 插值绘制（AlbumHero.kt:155-199），几何算法全在 AlbumHeroLogic.kt。代价与取舍：hero 图用列表侧缩略图 URL + `crossfade(false)`（避免换高清 URL 出现空窗）；拿不到任一端矩形直接退化为普通页转场（`startForward` 返回 false）；同一 albumId 若在两层同时可见，`coverRects` 只保留最后登记的矩形，返回落点可能落在被遮挡那个上，属于已知局限。

## 附录 B：明确不做的待办与原因

1. **Baseline Profile / macrobenchmark：暂不接入**。需要真机或模拟器反复采集（只有临时接入的单台真机，SDK 里没有 emulator 组件），且它只提供度量、不提供功能。等有稳定真机后按 androidx.baselineprofile 插件加独立 :benchmark 模块，接入时以“冷启动时间”为唯一验收指标。
2. **ktlint / detekt：暂不接入**。仓库现在没有任何静态检查配置，接入要新增插件依赖并全量格式化，会产出与功能改动无关的巨量 diff。当前以第六章的“提交前跑测试”与人工审查代替；真要接入就单独一次提交、一次性格式化。
3. **放大后的边缘翻页只做松手判定，不做跟手拖拽**。跟手要求 pager 与内容共享同一份 offset（翻页时内容跟着手指走、松手再决定归位还是切页），需要改 pager 的滚动源，改动面大于当前收益；现有实现是松手后整页 `springAnimateToPage`。
4. **运行验证缺口（必须如实交代）**：仓库已在 Xiaomi 15 / Android 17（targetSdk 36）真机上实测阅读器缩放与边缘翻页、双击整屏覆盖层、dataSync 前台服务与进度通知（含提升型通知分支）；其余只有编译、单元测试（`:core:test` 261 用例 + `:app:testDebugUnitTest` 192 用例，共 453 用例）与 release 打包（含 R8 与 lintVital）验证。仍未实测：共享元素飞行（列表 → 详情的飞行轨迹、返回落点与 380ms 时长的观感，等设备可用后必须补测）、`onTimeout` 超时回调、状态栏玻璃延伸的横向硬边、窄屏榜单列数、不同 ROM 下的通知拒绝路径、长图真实比例与 4MP 解码上限的实际观感和内存占用（含超长条幅仍会等比降采样）、Keystore 首次生成密钥与解不开时自清的路径、库自动重登在真实会话失效时的表现（只在单元测试里验证了反射契约）。CHANGELOG 涉及时按「已实现、部分真机验证」措辞，不写成肯定结论。