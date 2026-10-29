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

1. 全局单例只允许出现在 `Jm3Graph`（app/src/main/kotlin/com/jm3/app/miuix/Jm3App.kt:83）。网关、会话、设置、账号、数据库、进程级 scope 都在这里组装；屏幕代码通过参数或 Jm3Graph 读取，禁止自建平行单例。
2. 唯一长期协程作用域是 `appScope`（app/src/main/kotlin/com/jm3/app/miuix/Jm3App.kt:137，`SupervisorJob() + Dispatchers.IO`）。它的现有使用点只有 Jm3App.kt:115、:137、:150 与 app/src/main/kotlin/com/jm3/app/miuix/data/download/DownloadManager.kt 的 5 处。新增用途必须说明为什么不能挂在屏幕的 `rememberCoroutineScope` 上。禁止 `GlobalScope`。
3. 本仓库没有 ViewModel（app/src/main 全量 grep `ViewModel` 零命中）。约定是：屏幕数据状态用 `remember` + `LaunchedEffect` 持有，网络与磁盘读写在 `withContext(Dispatchers.IO)` 内完成（例 AlbumDetailScreen.kt:156 起）。不要为了“规范”引入 ViewModel 层；要引入就是一次架构变更，走 Issue。
4. 下载任务状态的真源只有一个：`DownloadQueue.updates`（core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:29-30，`_updates` + `asStateFlow()`）。UI 只做投影，不得另建进度表、不得在 UI 里累加已完成页数。
5. 设置项与会话只经 DataStore 读写：`jm3_settings`（app/src/main/kotlin/com/jm3/app/miuix/data/SettingsStore.kt:16）与 `jm3_session`（app/src/main/kotlin/com/jm3/app/miuix/data/SessionStore.kt:11）。会话内容一律走 `SessionCodec.encode/decode` 单键 JSON（SessionStore.kt:19、:22），禁止把 cookie 拆成多个键。
6. 持久化接口定义在 :core，:app 只提供实现：
   - core/src/main/kotlin/com/jm3/core/settings/SettingsRepository.kt:13（含 InMemorySettingsRepository，:88）
   - core/src/main/kotlin/com/jm3/core/download/DownloadRepository.kt:15（InMemory 在 :51）、core/src/main/kotlin/com/jm3/core/download/DownloadStore.kt:9
   - core/src/main/kotlin/com/jm3/core/history/HistoryRepository.kt:13（InMemory 在 :37）
   新增存储能力先加接口和 InMemory 实现，再有 Android 实现，最后接 UI。
7. Room 只用两个实体：`history` 与 `downloads`（app/src/main/kotlin/com/jm3/app/miuix/data/history/HistoryStore.kt:19、:54），数据库 `jm3.db` 版本 2 且 `exportSchema = false`（HistoryStore.kt:104；库名 jm3.db 见 Jm3App.kt:123）。改表必须写 Migration 并升版本；不许 `fallbackToDestructiveMigration`。

## 二、Network：网络与协议边界

1. 网络唯一入口是 `Jm3Gateway`（core/src/main/kotlin/com/jm3/core/Jm3Gateway.kt:45）。它的所有方法都是阻塞调用（接口注释逐条标注，见 :46、:55、:58、:61、:64），调用方必须已经在 IO 线程上：`withContext(Dispatchers.IO)` 或 `appScope`。
2. 协议库（io.github.jukomu.jmcomic）只允许出现在 :core。:app 的依赖里没有 `jmcomic-api`，它只通过 `api(libs.jmcomic.api)`（core/build.gradle.kts:18）传递可见。新增功能要新的网络能力时，先在 :core 的网关接口上加方法，不要在 :app 里直接 new 协议客户端。
3. 网关实例的切换走 `Jm3Graph.gatewayOverride` 与 `GatewayFactory`（Jm3App.kt:88-95、core/src/main/kotlin/com/jm3/core/GatewayFactory.kt:7）。线路（域名）只能通过 `Domains`（core/src/main/kotlin/com/jm3/core/settings/Domains.kt:4）与设置项改动，禁止在屏幕里硬编码 URL。
4. 图片不走网关文本接口：阅读器用 `ReaderFetcherFactory` 自取（app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderImages.kt:37-91），字节来源只有一处 —— 先 `imageLoader.diskCache.openSnapshot(cacheKey)`，命中即复用（:45-53）；未命中再取字节（本地已下载文件优先，否则网关，:55-60），随后 `openEditor(cacheKey)` → 写 `editor.data` → `commitAndOpenSnapshot()`（:62-84），返回 `ImageSource(snapshot.data, diskCache.fileSystem, cacheKey, snapshot)`；写盘失败必须 `editor.abort()` 再抛（:85-89）。
   - 关键约束：禁止把字节塞进 `okio.Buffer` 直接返回。telephoto 的分块超采样只认 `SuccessResult.diskCacheKey`，而 coil3 只在 fetch 结果源是 `FileImageSource` 时才带这个键（EngineInterceptor 里 `diskCacheKey = (fetchResult.source as? FileImageSource)?.diskCacheKey`），塞 Buffer 会让超采样永久失效、放大后发糊（同文件 :42-44、:61-67 注释）。无磁盘缓存或快照打不开时退回内存流只是兜底，不是常规路径。
   - 新增图片入口必须复用同一套 Keyer/Fetcher，否则内存与磁盘缓存会分裂。
5. 缓存键统一由 `ReaderKeyer.keyOf(data)` 生成（ReaderImages.kt:27-34，格式 `jm3-reader/<photoId>/<index>`），内存键与磁盘键共用一份，不得在别处再拼前缀。自建 `ImageLoader` 必须带磁盘缓存：ReaderImages.kt:107-114 用 `DiskCache.directory(cacheDir/reader_images).maxSizePercent(0.02)`，并在 `DisposableEffect` 里 `shutdown()`（:117-119）；自建 loader 默认不带缓存，去掉它超采样直接失效。
6. 分页一律“按 id 去重 + 页码游标”：`PageNumbers`（core/src/main/kotlin/com/jm3/core/download/PageNumbers.kt:12）负责 1 基页码换算，搜索与评论分页各自有测试（SearchPagesTest、CommentPagesTest）。新增分页必须先在 :core 写成纯函数并测试。
7. 网络失败不得静默：`refreshUnreadNotifications` 失败时保留上一次结果而非清零（Jm3App.kt:110-119）。这类“降级”必须在注释里写清楚为什么，未写清楚的按 bug 处理。

## 三、Error 与 Coroutine

1. 禁止吞掉 `CancellationException`。范本在 core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:143-145（`catch (e: CancellationException) { throw e }`）与 :201-202（重试循环里取消不消耗重试次数）。任何 `catch` 之前先问：这里会不会吞取消？
2. 单任务失败必须隔离：worker 循环里 `catch (Exception)` 只把该任务置失败，不能让整个队列退出（core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:138-151）。
3. 状态机转移用 `check` 硬断言，不用 if 悄悄跳过：`core/src/main/kotlin/com/jm3/core/download/DownloadTask.kt:36` `check(status.canTransitionTo(next)) { "非法状态转移：$status -> $next" }`。失败可见好过状态错乱。
4. 用户可见的错误必须可重试，文案复用现成组件：全屏 `ErrorScreen(message, onRetry)`（Common.kt:195）、列表三态 `LoadMoreFooter(loadingMore, hasMore, loadMoreFailed, onLoadMore)`（Common.kt:276-295）。禁止屏幕里自造“加载失败”布局。
5. 阻塞调用不得进主线程，Composable 体内只做状态编排。用 `LaunchedEffect`/`rememberCoroutineScope` 起协程，用 `withContext(Dispatchers.IO)` 包住 IO。
6. 取消要显式：pager 翻页、缩放复位这类“跟手动画”用 `springAnimateToPage`（ReaderScreen.kt:604）而不是新起 Job 抢滚动状态。
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

1. 组件库只有一个：Miuix。:app 依赖里没有 Material3（app/build.gradle.kts:68-99），全量 grep `androidx.compose.material3` 零命中，版本目录也没有该条目。新增界面一律用 Miuix，禁止“先引一下 material3 的 Icon”。
2. 三态与常用件复用 `Common.kt`：`LoadingScreen`（:181）、`ErrorScreen`（:195）、`EmptyState`/`CenteredEmptyState`（:213、:233）、`LoadMoreFooter`（:276-295）、`PagerBar`/`ResultPagerBar`（:392、:386）、`Modifier.pressScale`（:443）、`rememberSelectionHaptic()`（:469）。新屏自造加载/空态视为重复实现。
3. 阅读器手势是一条有优先级的状态机，新增手势必须在这套状态机里排队（主文件 app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderScreen.kt）：
   - 缩放状态只由当前页写入（ReaderScreen.kt:584-587），翻页即复位（:294、:955-957）。
   - pager 的横向手势在放大时被接管：`pagerGestureOverride(pagerState, enabled = !pageZoomed)` 且 `userScrollEnabled = false`（ReaderScreen.kt:571、:573），嵌套滚动交给 `PagerGestureNestedScrollConnection`（:574）。
   - 放大后拖到边界继续拖才翻页：`onHorizontalEdgeDrag` → `decideEdgePageTurn` → `springAnimateToPage`（:589-609）。判定逻辑在 ReaderZoomLogic.kt:26-44，不在 Composable 里。
4. 阈值与判定必须是纯函数并集中：`ZOOMED_THRESHOLD = 0.02f`、`EDGE_PAGE_DISTANCE_RATIO = 0.25f`、`EDGE_PAGE_FLING_VELOCITY = 1_000f`（ReaderZoomLogic.kt:4、:10、:13），`isPageZoomed`（:16-17）与 `decideEdgePageTurn`（:26-44）已有真实调用点（ReaderScreen.kt:591、:998）。不许把这些数字散落回 Composable。
5. 缩放路径不得写死解码尺寸：`zoomEnabled` 分支只挂 listener 不写 `size`（ReaderScreen.kt:924、:935 注释：写死尺寸会让放大后发糊），普通路径必须写与预取相同的 `pageSize`（:941-942，保证内存缓存可复用）。改这一处会同时影响清晰度与内存。
6. 纵向长图模式不要做就地缩放：3:4 页框会把放大内容裁掉（ReaderScreen.kt:624 注释），双击改为打开整屏覆盖层（:296、:625）。同类“想当然”的交互改动先在真机上验证再提。
7. 动效收敛：缩放跟随用 telephoto 自带的 `rememberZoomableImageState` + `resetZoom`，按压缩放用 `Modifier.pressScale(0.96f)`，触感用 `rememberSelectionHaptic()`。不新增自研动画框架。
8. 边缘到边与返回：`enableEdgeToEdge()` 在 MainActivity.kt:13 统一开启，屏幕自己处理 `innerPadding`；音量键翻页由 `onKeyDown` 交给 `ReaderVolumeKeys`（MainActivity.kt:21-25；ReaderVolumeKeys 定义在 app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderScreen.kt:128，dispatch 在 :132），不要再在屏幕里重复拦截。

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
4. 测试放哪：`:core` 逻辑写 `core/src/test/kotlin/com/jm3/core/<包名>/<被测类>Test.kt`，`:app` 纯逻辑写 `app/src/test/kotlin/com/jm3/app/miuix/.../<被测类>Test.kt`，命名 `XxxTest`。现状：`:core` 21 个测试类（最重的是 `download/DownloadQueueTest.kt`，约 18 KB，另有 `download/DownloadRecoveryTest.kt`），`:app` 1 个（`app/src/test/kotlin/com/jm3/app/miuix/ui/ReaderZoomLogicTest.kt`，15 个用例）。
5. `:app` 已有测试源集：`testImplementation(kotlin("test"))`（app/build.gradle.kts:104）与 `tasks.withType<Test>().configureEach { useJUnitPlatform() }`（app/build.gradle.kts:68-70），现有 `app/src/test/kotlin/com/jm3/app/miuix/ui/ReaderZoomLogicTest.kt` 共 15 个用例，覆盖 `isPageZoomed` 的 null / 0f / 阈值边界，以及 `decideEdgePageTurn` 的页宽非法、页数非法、位移与速度都不达标、位移达标、仅甩动达标、距离达标时忽略反向甩动、首页无上一页、末页无下一页、阈值随页宽缩放。
   心智模型：Composable 只做编排，判定逻辑提成 `*Logic.kt` 纯函数（范例 app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderZoomLogic.kt），测试打在纯函数上。不要为了“能测”去写假 Composable 测试，也不要把判定塞回 Composable 再喊测不了。
6. 边界清单（新增或改动这些逻辑时，测试必须覆盖；每条都已存在对应断言或测试类）：
   - 状态机非法转移必须抛错：core/src/main/kotlin/com/jm3/core/download/DownloadTask.kt:36 → DownloadTaskTest。
   - 同 photoId 去重、FAILED 重入从 0 重来：core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:44-65 → DownloadQueueTest。
   - 并发上限 2、重试 2 次、取消不消耗重试：core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:21、:197-208 → DownloadQueueTest / DownloadContractTest。
   - 断点续传与重启恢复：DownloadRecoveryTest、DownloadRepositoryTest。
   - 页码换算 1 基且越界安全：PageNumbers.kt:12 → PageNumbersTest。
   - 分页结果按 id 去重与游标推进：SearchPagesTest、CommentPagesTest。
   - 会话编解码往返与坏数据降级：SessionCodec.kt:7 → SessionCodecTest。
   - 分割图还原算法：Scramble.kt:14 → ScrambleTest。
   - 封面 URL 与域名拼接：CoverUrls.kt:4、Domains.kt:4 → CoverUrlsTest、DomainsTest。
   - 阅读器缩放判定与边缘翻页阈值：app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderZoomLogic.kt:16-17、:26-44 → ReaderZoomLogicTest（15 用例；改阈值或改判定分支必须同步改这里）。
   - 章节标签与通知文案：ChapterLabels.kt:10 → ChapterLabelsTest、NotificationBodyTest。
   - 设置项映射与默认值：core/src/main/kotlin/com/jm3/core/settings/SettingsRepository.kt:13 → SettingsRepositoryTest。
7. 断言纪律：只测公共入口；不打桩自身控制的协作者；不用 `Thread.sleep` 等时序；一个用例只表达一个业务规则；计算与规则逻辑至少用两组差异数据（常规 + 边界）验证。
8. 提交与 CHANGELOG：提交信息用 Conventional Commits（如 `feat(reader): 支持放大后边缘拖拽翻页`），一个逻辑变更一次提交，不混入生成物与过程物。CHANGELOG 只写进“未发布”段并按 新增/修复/优化 分类（CHANGELOG.md:3、:5），不改历史版本区块。

## 附录 A：已定决策与理由

后续改动若与这些决策冲突，先推翻决策再改代码，不许悄悄绕过。

1. **阅读器缩放用 telephoto，不用 net.engawapg.lib:zoomable**。坐标 `me.saket.telephoto:zoomable-image-coil3` 0.19.0（Apache-2.0），见 gradle/libs.versions.toml:16、:30 与 app/build.gradle.kts:85。理由是它同时给到分块超采样与缩放感知解码，一次依赖解决“放大发糊”；pixez 那条 zoomable 路线缺超采样。
2. **手势让渡必须显式关掉 Miuix 的翻页拦截**。Miuix 的 `PagerSwipeNode` 在 PointerEventPass.Initial 判 `change.isConsumed`，而 telephoto 在 Main pass 消费，Initial 先于 Main，因此放大时保持 `enabled = true` 会“一边平移一边翻页”（.tools/miuix-src/x/commonMain/top/yukonga/miuix/kmp/utils/PagerGestureUtils.kt:168-198）。所以 HorizontalPager 恒为 `.pagerGestureOverride(pagerState, enabled = !pageZoomed)`（app/src/main/kotlin/com/jm3/app/miuix/ui/ReaderScreen.kt:571）。
3. **放大后“内容到边界再翻页”由自研 Main-pass 旁路实现**：父层只在子节点不消费（`change.isConsumed == false`）时累计 dragX 与速度，松手用纯函数判定（ReaderScreen.kt:1016-1042 + ReaderZoomLogic.kt:26-44）。不跟手、不做橡皮筋。
4. **长图模式不就地缩放**：3:4 页框会裁掉放大后的内容，且与 LazyColumn 竖向滚动抢手势；改为双击进整屏覆盖层（ReaderScreen.kt:296、:626、:670-686），单击或返回键退出。
5. **图片必须经 DiskCache 快照复用，禁止塞 okio.Buffer**（理由与判定位置见第二章第 4 条）。`ReaderKeyer.keyOf` 是内存键与磁盘键的唯一来源（ReaderImages.kt:27-34），不许在别处拼字符串。
6. **下载前台服务只做“持有前台状态 + 展示通知”，不做调度**：调度与状态真源仍是 DownloadQueue，服务生命周期唯一驱动量是活动任务数（DownloadService.kt:125-152）。不引入 WorkManager 之类的新依赖。
7. **通知进度节流 300ms，终态立即刷新**（DownloadThrottle，DownloadNotifier.kt:181-200）；dataSync 累计 6 小时配额用尽时把在途任务置 PAUSED 并退出前台（DownloadService.kt:65-79 → DownloadManager.kt:153），不做保活。
8. **许可证口径**：只有 Apache-2.0 / MIT 的代码可直接复制；GPL-3.0 / AGPL-3.0（含 EhViewer 主仓、yunxi、cu guis）一律只采思路、自行重写，不粘贴源码文本、不照搬结构命名。当前仓库没有任何外部复制的代码。

## 附录 B：明确不做的待办与原因

1. **Baseline Profile / macrobenchmark：暂不接入**。需要真机或模拟器反复采集（当前 `adb devices` 为空，SDK 里没有 emulator 组件），且它只提供度量、不提供功能。等有稳定真机后按 androidx.baselineprofile 插件加独立 :benchmark 模块，接入时以“冷启动时间”为唯一验收指标。
2. **ktlint / detekt：暂不接入**。仓库现在没有任何静态检查配置，接入要新增插件依赖并全量格式化，会产出与功能改动无关的巨量 diff。当前以第六章的“提交前跑测试”与人工审查代替；真要接入就单独一次提交、一次性格式化。
3. **放大后的边缘翻页只做松手判定，不做跟手拖拽**。跟手需要 pager 与内容共享 offset，改动面大，且当前无真机可验，风险大于收益。
4. **运行验证缺口（必须如实交代）**：本次改动只做到编译 + 单元测试 + release 打包验证，没有在真机或模拟器上跑过；手势优先级、通知提升胶囊（Android 16+）、`onTimeout` 超时回调都未经实测。