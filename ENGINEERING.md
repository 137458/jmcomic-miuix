# 工程约束与开发规范

本文定义 `jm3`（JM3 Miuix Android 客户端）的可执行约束。只记录判定线、真实符号位置与决策理由。

## 使用方式

- 写代码前先答「决策三问」，再按章查对应红线。
- 约束与源码冲突时以源码为准，并在同一次提交内修订本文。
- 每条约束必须附真实路径或符号。未核实内容显式标「未核实」。
- 行号只是写作时快照。**符号名与文件路径才是判定依据**；行号对不上时按符号定位，顺手校正。

## 决策三问

1. **分层归属**：能进 `:core` 的逻辑禁止写进 `:app`；能写成纯函数的判定禁止留在 Composable 内。`:core` 为纯 JVM 模块（`core/build.gradle.kts:1-4`），禁止引入 Android 类型。
2. **可测路径**：判定、换算、状态机与编解码必须可测。现有 222 个测试类（`:core` 80 个、`:app` 142 个）。测不了说明分层有误，先拆层。
3. **红线冲击**：核对单一网关、单一状态真源、双皮肤组件隔离、阅读器手势优先级、Release 混淆与签名规则。

---

## 一、状态与依赖注入

### 1.1 进程组装根与六大域

进程内唯一组装根是 `Jm3Graph`（`app/src/main/kotlin/com/jm3/app/miuix/Jm3App.kt:161`）。

`Jm3Application`（`:147`，注册于 `app/src/main/AndroidManifest.xml:17`）与 `MainActivity` 都经 `init(context)`（`:205`）初始化。加锁字段 `initLock`（`:193`），进入点 `:207`，保证幂等。

组装根只按启动顺序装配六个域，不向屏幕暴露转发属性：

- `ApiDomain`（`data/ApiDomain.kt:27`）：网关实例、线路池缓存、切换锁。会话迁移由 `SessionDomain.applyDomains`（`data/SessionDomain.kt:43`）挂接。
- `SessionDomain`（`data/SessionDomain.kt:16`）：账号、带启动闸门的 `AuthCall`、未读角标、凭据存储。
- `SettingsDomain`（`data/SettingsDomain.kt:19`）：设置仓库、全局分页模式状态。
- `LibraryDomain`（`data/LibraryDomain.kt:18`）：唯一 Room 库 `jm3.db`（`:24`），提供历史仓库与下载台账存储。
- `DownloadDomain`（`data/DownloadDomain.kt:21`）：页文件存储、下载队列、台账、`DownloadManager` 门面。
- `UpdateDomain`（`data/UpdateDomain.kt:16`）：`UpdateDownloader` 单例与安装包出口（`:39`）。

**硬性禁令**：

- 同一域内类型禁止在别处重复实例化。
- 屏幕只经 `ScreenDeps`（`app/src/main/kotlin/com/jm3/app/miuix/ScreenDeps.kt:49`）取依赖，禁止直读 `Jm3Graph.<成员>`。
- 偏好读面是四个扁平只读属性：`paginationMode`（`:110`）、`bottomBarSize`（`:114`）、`bottomBarForm`（`:118`）、`wideScreenNavigation`（`:122`）。屏幕拿不到 `SettingsDomain` 本体。
- 偏好写面只有 `selectPaginationMode`（`:131`）、`selectBottomBarSize`（`:143`）、`selectBottomBarForm`（`:152`）、`selectWideScreenNavigation`（`:158`）。
- 写序固定为「比较现值 → 写内存态 → 进程作用域落盘」。落盘唯一出口是私有 `persist`（`:166`，内部 `runCatchingSuspending` 且只记日志）。屏幕禁止自行拼这套顺序。
- 四个写口里只有 `selectPaginationMode` 额外调 `sessionCache.forgetPages()`（`:135`）。只有它改变分页语义；其余三项照抄会白清缓存。
- 读面按关心点切窄：只要线路信息用 `routes`（`:71`），只要图片域名或封面地址用 `images`（`:75`）。两者均由网关对象转发。
- `Jm3Graph` 成员只允许以下位置读取，且读取处禁止写编排：
    - `Jm3Application`（`Jm3App.kt:150` 与启动读盘段 `:417-428`、`:440`、`:518`）
    - `MainActivity`（`MainActivity.kt:29`、`:54`、`:58`、`:79`）
    - `DownloadService`（`data/download/DownloadService.kt:95`、`:147`、`:177`、`:184-185`、`:214`）
    - 装配处 `ScreenDeps(...)`（`Jm3App.kt:341-350`）

### 1.2 协程作用域与界面编排

- 唯一长期协程作用域是 `appScope`（`Jm3App.kt:164`，`SupervisorJob() + Dispatchers.IO`）。禁止 `GlobalScope`。
- `appScope` 现有用途限于以下场景，禁止承载界面状态：
    - 装配注入与 `ScreenDeps.appScope`
    - `ApiDomain`（`:29`、`:119`）、`UpdateDomain`（`:16`、`:39`）
    - `DownloadManager`（`:28`、`:57`、`:62`）、`DownloadService`（`:147`、`:177`、`:184`）
    - `MainActivity.onStop`（`:79`）
    - 退出阅读器补记页码（`ui/ReaderEffects.kt:213`，作用域经 `ui/ReaderPages.kt:259` 传入，COUI 侧 `ui/coui/page/CouiReaderPages.kt:306`）
    - 设置页偏好写盘（`ScreenDeps.kt:166-172`）、线路落盘与清库（`ui/settings/SettingsScaffold.kt:437`、`:493`）
- 偏好写盘必须挂进程作用域：退出设置页会取消 `rememberCoroutineScope`，内存态与 DataStore 会半途不一致。
- 新增 `appScope` 用法须说明为何无法挂 `rememberCoroutineScope`。
- 不引入 `ViewModel`。屏幕状态用 `remember` 与 `LaunchedEffect` 编排；状态复杂时抽纯 Kotlin 容器（如 `AlbumDetailStateHolder`）。
- 路由目标只在 `AppRoutes`（`ui/route/AppRoutes.kt:15`）拼装。构造只收两个出口：`push`（`:16`）与 `replaceTop`（`:17`）。
- 对外 24 个语义动作（`:19-69`），例 `album`（`:46`）、`reader`（`:49`）、`readerAt`（`:52`）、`nextChapter`（`:55`，同层替换）、`search`（`:69`）。表在 `Jm3App.kt:491-497` 构造一次（`remember(backStack)`）。
- 屏幕只认语义动作，不拼 `Route`。屏幕形参统一为 `nav: AppRoutes`（`ui/MainScreen.kt:87`、`ui/AlbumDetailScreen.kt:144`、`ui/coui/page/CouiMainScreen.kt:77`）。
- `Jm3NavHost.kt:91` 起 23 个 `entry`，除登录与更新外，每个 entry 的两种皮肤各传一次 `nav = nav`；`ui/MainScreen.kt:241`、`:246`、`:251`、`:257` 再转发给四个页签。
- 路由改名、加参数或把二级页改成同层替换，只改表内一行，屏幕零改动。
- 网关调用一律经 `AuthCall.call`（`core/src/main/kotlin/com/jm3/core/session/AuthCall.kt:36`）。禁止屏幕自写 `withContext(Dispatchers.IO)` 调网关，禁止跳过启动闸门与统一失效回调。

### 1.3 持久化与单真源

- 下载任务状态唯一真源是 `DownloadQueue.updates`（`core/src/main/kotlin/com/jm3/core/download/DownloadQueue.kt:30`）。UI 禁止另建进度表或在界面层累加页数。
- 设置与会话只经 DataStore 读写，禁止另开 `SharedPreferences`。库名为 `jm3_settings`（`data/SettingsStore.kt:19`）与 `jm3_session`（`data/SessionStore.kt:17`）。
- 两个库都配 `ReplaceFileCorruptionHandler` 损坏自清，并在 `AndroidManifest.xml:23` 经 `res/xml/data_extraction_rules.xml` 排除云备份与设备迁移。
- 会话内容经 `SessionCodec` 单键 JSON 读写。自动登录密文固定存为 `jm3_settings` 下的 `credentials_blob` 键（`SettingsStore.kt:27`）。
- 持久化契约先在 `:core` 定义接口与 `InMemory` 实现，再在 `:app` 落地：
    - 设置：`SettingsRepository` / `InMemorySettingsRepository`（`core/src/main/kotlin/com/jm3/core/settings/SettingsRepository.kt:23`、`:52`）。
    - 下载：`DownloadLedger`（`core/src/main/kotlin/com/jm3/core/download/DownloadLedger.kt:60`）、`DownloadRecord`（`DownloadRecord.kt:4`）、`DownloadStore`（`DownloadStore.kt:9`）。
    - 历史：`HistoryRepository` / `InMemoryHistoryRepository`（`core/src/main/kotlin/com/jm3/core/history/HistoryRepository.kt:19`、`:52`）。
    - 凭据：`CredentialStorage`、`CredentialCipher`、`EncryptedCredentialStore`（`core/src/main/kotlin/com/jm3/core/CredentialStorage.kt:16`、`:69`、`:92`）。Android 侧密钥由 `KeystoreCredentialCipher`（`data/CredentialStore.kt:34`）提供，密钥失效时自动重建并重试。
    - 例外：`DomainPoolCache`（`core/src/main/kotlin/com/jm3/core/settings/DomainPoolCache.kt:9`，Android 原子写盘实现 `data/DomainPoolCache.kt:16`）。冷启动网关装配在首屏前同步执行，故用阻塞文件缓存而非 DataStore。
- Room 库 `jm3.db` 只含 `history` 与 `downloads` 两张表（`data/history/HistoryStore.kt:25-26`、`:81`）。
- `@Database(..., version = 3, exportSchema = true)` 见 `HistoryStore.kt:193`。schema 导出至 `app/schemas/`，由 `HistorySchemaTest` 守住。
- `MIGRATION_2_3`（`:181`）为 `history` 建 `albumId`、`updatedAt` 索引，为 `downloads` 建 `updatedAt` 索引。
- 改表必须提供 `Migration` 并升版本，禁止 `fallbackToDestructiveMigration`。

### 1.4 加载结局的单一翻译层

- `SessionOutcome` 至界面状态只经 `planLoad`（`app/src/main/kotlin/com/jm3/app/miuix/ui/ScreenLoadPlan.kt:31`）翻译为 `ScreenLoadPlan`（`:18`）。三相位 `Content` / `Expired` / `Failed` 见 `:10`。
- 有内容时保内容并给 `notice`（`keepContent` 置真）；无内容时整屏呈现失效或错误。
- 副作用落点统一调 `applyLoadPlan`：协程重载（`:71`）与挂起重载（`:90`）各持两个钩子 `onKeepContent` / `onFullScreen`，调用方只写本屏差异。动作类提示复用 `notifyAction`（`:114`）。
- 分页取数唯一出口是 `SessionListLoader`（`ui/list/SessionListLoader.kt:48`）。屏幕只提供三件事：取哪一页 `fetchPage`（`:52`）、条目 key `idOf`（`:50`）、保留内容时的提示 `notice`（`:53`）。文案差异只留 `failureMessage`（`:51`）与 `stalePrefix`（`:54`）。
- 状态面为 `snapshot`（`:59`，`SessionListSnapshot` `:29` 携带 `feed` `:64`、`state` `:66`、`refreshing` `:68`，三者同名便捷读口见同段）。
- 动作面只有 `loadFirstPage`（`:71`）、`refresh`（`:76`）、`loadMore`（`:81`）、`goToPage`（`:98`）、`removeWhere`（`:103`）。
- 请求代次 `requestToken`（`:62`）只增不减；旧请求返回时对不上即丢弃（`:152`）。
- `notice` 是屏幕必给项，禁止传空实现——失败原因必须落到该屏 Snackbar 宿主。
- 会话缓存是同一构造器上的两个可选尾部参数：`cache`（`:55`，`ListStateCache<T>?`）与 `cacheKey`（`:56`，`SessionListKey?`）。
- 给了缓存就以 `cache.read(cacheKey)` 的已落定快照作首帧（`seededSnapshot` `:116-120`），每次成功落定后经 `cacheLanded`（`:126-129`，写回点 `:163`）写回；不给则行为与加缓存前一致。
- 缓存接口 `ListStateCache`（`ui/SessionCache.kt:112`）只有四个动作：`read`（`:115`）、`write`（`:118`）、`forget`（`:121`）、`hasPage`（`:124`），由 `SessionCache`（`:146`；实现点 `:205`、`:209`、`:214`）承担。
- 缓存只收 `ListLoadState.Loaded`（`remember` `:221-228` 先把 `loadingMore` / `loadMoreFailed` 归一）。作废入口是 `forgetPages`（`:238`，清全部账号仓）与 `clear`（`:243`，两皮肤「我的」页登出成功后调用）。
- 条目身份为 `AccountScopedKey`（`:132`）：`SessionCache` 在屏幕给的键前补一层 uid，读写经 `scoped`（`:163`）每次动态取 `currentUid`（`:148`），由 `bindAccount`（`:159`）在组装期绑定（`ScreenDeps.kt:64`）。换账号读不到上一账号的仓，屏幕也不必知道当前 uid。
- 键空间 `SessionListKey`（`:37-48`）覆盖首页 feed（`:38`）、首页搜索（`:39`）、分类与分类标签（`browseTagCacheKey` `:51`；`BrowsePageKey` `:60-65`）与独立搜索页的四元组（`Search` `:42-47`）。
- 缓存命中即铺首帧、**不补拉**：首拉只在 `state !is ListLoadState.Loaded` 时触发（`ui/SearchScreen.kt:102`、`ui/coui/page/CouiSearchScreen.kt:171-176`）。刷新靠下拉刷新或改筛选条件。COUI 皮肤无下拉刷新容器（见附录 B），命中后须改排序、时间或查询词才会重取。
- 失败落点只有 `applyFailure`（`:180`）：`hasContent = pending.items.isNotEmpty() && (!replace || pulling || keepOnFailure)`（`:187`）。有内容时保留内容并按请求类型分派页脚标记（`:189-193`）。
- 刷新或跳页失败只 `copy(loadingMore = false)`，**禁止**置 `loadMoreFailed`。否则页脚误报「加载失败，点击重试」，且 `PagedFeed.shouldLoadMore`（`core/src/main/kotlin/com/jm3/core/paging/PagedFeed.kt:79`）会让触底翻页停摆。只有加载更多失败才 `failLoadMore()`。
- 无内容才整屏 `ListLoadState.Failed`（`:194-205`，`sessionExpired` 取自 `planLoad` 的 `Expired` 相位）。`goToPage` 传 `keepOnFailure = true`（`:99`），因为用户点的是「第 N 页」而非整屏重载。
- 现有 15 处 `SessionListLoader(` 构造，分布 13 个文件：
    - `ui/home/HomeScaffold.kt:587`、`ui/browse/BrowseScaffold.kt:247`、`ui/BrowseScreen.kt:610`
    - `ui/favorites/FavoriteScaffold.kt:793`、`ui/FavoritesScreen.kt:114`、`ui/ForumScreen.kt:137`
    - `ui/coui/page/CouiForumScreen.kt:156`、`ui/search/SearchScaffold.kt:168`、`ui/tasks/TasksScaffold.kt:247`
    - `ui/tracking/TrackingScaffold.kt:149`、`ui/watchhistory/WatchHistoryScaffold.kt:118`
    - `ui/coui/page/CouiBrowseScreen.kt:202`、`:221`、`ui/coui/page/CouiLibraryScreen.kt:138`、`:156`
- COUI 首页、收藏、任务、追踪与观看记录页不再自建 loader，改由同名共享骨架提供（附录 A #38）。
- 其中 8 处注入会话缓存：`ui/home/HomeScaffold.kt:593`、`:607`、`ui/browse/BrowseScaffold.kt:255`、`ui/BrowseScreen.kt:625`、`ui/search/SearchScaffold.kt:179`、`ui/SearchScreen.kt:90`、`ui/coui/page/CouiBrowseScreen.kt:236`、`ui/coui/page/CouiSearchScreen.kt:161`。
- 禁止屏幕自持代次令牌，禁止自写 `beginLoadMore` 或失败分派顺序。
- 屏幕禁止引用 `LOGIN_EXPIRED_MESSAGE`，也禁止自拼「保留内容 + Snackbar」分支。
- 全仓 `planLoad` / `applyLoadPlan` 出现在 17 个生产文件：
    - 定义处 `ui/ScreenLoadPlan.kt`，列表统一落点 `ui/list/SessionListLoader.kt:188`
    - 另例 `ui/profile/ProfileScaffold.kt:160`、`:195`、`ui/forum/ForumScaffold.kt`、`ui/AlbumDetailStateHolder.kt:120`、`:163`
    - 测试由 `ui/ScreenLoadPlanTest.kt` 覆盖

---

## 二、网络与协议边界

### 2.1 网关接口与反射成员表

- 网络唯一入口是 `Jm3Gateway`（`core/src/main/kotlin/com/jm3/core/Jm3Gateway.kt:82`），继承 `SessionAuth`（`SessionAuth.kt:14`）、`PhotoSource`（`download/PhotoSource.kt:14`）、`DomainRoutes`（`Jm3Gateway.kt:54`）与 `ImageAddressing`（`:74`）。所有方法均为阻塞调用，须运行在 IO 调度器。
- `AccountManager` 只依赖 `SessionAuth` 的 6 个鉴权方法；下载取页组件只依赖 `PhotoSource` 的 `photo`（`:17`）与 `imageBytes`（`:20`）。被测方只依赖它实际用到的面。
- 测试替身禁止伪造整张网关：
    - `:core` 侧 `FakeSessionAuth`（`core/src/test/kotlin/com/jm3/core/FakeSessionAuth.kt:15`）只实现 6 个方法，未配置的 `login` 直接 `error(...)`（`:25-27`）
    - `:app` 侧确需整张网关时用 `FakeGateway`（`app/src/test/kotlin/com/jm3/app/miuix/data/FakeGateway.kt:50`），62 个业务方法一律经 `unexpected`（`:60`）失败，只许 `close` 生效
- 禁止用反射代理按方法名字符串分派（改接口在编译期无反应），也禁止替身返回兜底假数据让误调静默通过。
- 协议库 `io.github.jukomu.jmcomic` 只允许出现在 `:core` 主代码，经 `implementation(libs.jmcomic.api)`（`core/build.gradle.kts:18`）内部使用、不再对外暴露。禁止在 `:app` 直接实例化底层协议客户端。
- `:core` 直用的 okhttp（`Cookie` / `HttpUrl`）由 `implementation(libs.okhttp)`（`core/build.gradle.kts:22-24`）显式声明，不吃 `jmcomic-core` 的传递依赖。
- 唯一例外是 `jmcomic-android-support`（`app/build.gradle.kts:136-138`）：只提供 Android 侧 WebP 解码，不暴露协议类型。
- 异常归类收在 `:core`：`classifyFailure`（`core/src/main/kotlin/com/jm3/core/FailureKind.kt`）给出 `FailureKind`，`:app` 只挑文案（`app/src/main/kotlin/com/jm3/app/miuix/FailureCopy.kt`）。`:app` 主代码禁止出现协议库 import。
- 反射成员表唯一登记处是 `LibraryReflection`（`core/src/main/kotlin/com/jm3/core/LibraryReflection.kt:35`）：成员名、字段/方法形状与所属类集中在 `members`（`:88-99`）共 10 条，其中 9 条属 `AbstractJmClient`（`:39`）、1 条属 `JmApiClient`（`:40`）。
- 新增或删除成员只能改这张表，并同步 `LibraryReflectionTest`（`core/src/test/kotlin/com/jm3/core/LibraryReflectionTest.kt:31`、`:47-48`、`:52-54`）。
- 解析与降级也收在同一处：`resolve`（`:102`）、`fieldOf`（`:108`）、`methodOf`（`:113`）、`member`（`:122`）、`invokeOrThrow`（`:129`，`InvocationTargetException` 原样上抛库内异常）、`invokeOrNull`（`:146`）。
- 调用方只持成员句柄：
    - `ReflectiveLibraryBridge`（`core/src/main/kotlin/com/jm3/core/LibraryBridge.kt:43`，读取点 `:47`、`:71`、`:82-83`、`:88-89`、`:96`、`:107`、`:113-114`、`:117-119`）
    - `CustomDomainPool`（`core/src/main/kotlin/com/jm3/core/CustomDomainPool.kt:29`、`:33`、`:40`、`:42`）
- 两者不得自行解析成员。
- 反射成员缺失时须按契约降级，并由 `member` 经 `Jm3Log.warn` 记录成员名与异常类名（`LibraryReflection.kt:122`）。禁止打印 Cookie 或密码值。

### 2.2 线路切换与域名来源

- 网关实例切换走 `ApiDomain.gatewayOverride`（`data/ApiDomain.kt:51`，双检读写在 `:59`、`:88`、`:92`）与 `GatewayFactory`（`core/src/main/kotlin/com/jm3/core/GatewayFactory.kt:17`）。
- 域名清洗一律经 `Domains`（`core/src/main/kotlin/com/jm3/core/settings/Domains.kt:6`）。禁止在屏幕硬编码 URL。
- 线路可观测面收在 `DomainRoutes`（`Jm3Gateway.kt:54`）：只暴露 `usedDomain`（`:56`）、`domainsReady`（`:59`）、`domainLatency`（`:62`）与 `reprobeDomains`（`:65`）。网关侧四个 override 只做转发（`:520-527`），生产实现 `LibraryDomainControls`（`:651-669`）直接委托库客户端。
- 屏幕经 `ScreenDeps.routes`（`ScreenDeps.kt:71`）读取，现有消费点集中在共享骨架：
    - `ui/settings/SettingsScaffold.kt:397`、`:418`
    - `ui/tasks/TasksScaffold.kt:376`、`:381`
    - `ui/shelf/ShelfScaffold.kt:183`、`:187`
    - `ui/CheckInLoadPolicy.kt:457`、`ui/reader/ReaderEntryScaffold.kt:143`
- 禁止为读线路信息而持有整张网关。
- 已知偏差：`ui/AlbumDetailScreen.kt:170` 与 `ui/coui/page/CouiAlbumDetailScreen.kt:252` 仍由屏幕直读网关线路。改动这两处时改走 `deps.routes`。
- 图片域名与封面地址走 `ImageAddressing`（`Jm3Gateway.kt:74`，`coverUrl` `:76`、`imageHost` `:79`），屏幕经 `ScreenDeps.images`（`ScreenDeps.kt:75`）读取。禁止只为一个封面地址而持有整张网关。
- 启动期域名只来自自定义线路与上次刷新落盘的 `DomainPoolCache`。官方线路表在 `DomainTable.kt` 运行时拉取解密；仅当两者皆空（如首次安装）时回退库内初始化链，完成后落盘。

### 2.3 图片缓存与超采样链路

- 阅读器图片统一经 `ReaderFetcherFactory`（`ui/ReaderImages.kt:73`）获取，缓存键由 `ReaderKeyer.keyOf`（`:42`）统一生成（格式 `jm3-reader/<photoId>/<index>`）。禁止另拼前缀。
- 字节流必须先经 `imageLoader.diskCache.openSnapshot`（`:92`）复用；未命中再读本地下载文件或网关字节，经 `openEditor`（`:116`）与 `commitAndOpenSnapshot`（`:124`）写盘后返回 `ImageSource`。写盘失败须调 `editor.abort()`（`:141`）并抛异常。
- **关键约束**：禁止直接把内存 `okio.Buffer` 当作常规路径返回。`telephoto` 分块超采样只识别 `FileImageSource` 携带的 `diskCacheKey`，返回内存流会导致放大发糊。
- `ImageLoader` 由 `acquireReaderImageLoader`（`:208`）持有为进程级共享实例，配置 `reader_images` 磁盘缓存（`:252`，占可用空间 2%，见 `:253`）。
- 重建键 `ReaderLoaderKey`（`:184`）只含网关与下载存储；鉴权通道经 `ReaderAuthChannel`（`:197`）在 `SideEffect`（`:237`）动态注入。
- 页面销毁时禁止 `shutdown()`，防止换章重叠时误关新屏缓存。

### 2.4 分页与静默失败红线

- 分页统一按 ID 去重与 1 基页码游标。页码换算收口于 `PageNumbers`（`core/src/main/kotlin/com/jm3/core/download/PageNumbers.kt:12`），翻页计算收口于 `PagingBehavior`（`core/src/main/kotlin/com/jm3/core/paging/PagingBehavior.kt`），切片与游标状态机收口于 `PagedFeed`（同包）。
- 网络失败禁止静默清零有效状态。例：`UnreadBadge`（`core/src/main/kotlin/com/jm3/core/session/UnreadBadge.kt:17`）请求失败时保留上次计数，且降级理由必须写在代码注释里。

---

## 三、错误处理与协程规范

1. **禁止吞掉 `CancellationException`**：参考 `DownloadQueue.kt:156-158` 与 `:220-222`。挂起调用收成 `Result` 时一律用 `runCatchingSuspending`（`core/src/main/kotlin/com/jm3/core/Cancellation.kt:11`），禁止直接 `runCatching { suspendCall() }`。
2. **单任务失败隔离**：下载 worker 捕获普通异常后只标记当前任务失败，禁止因单任务异常中断整个队列循环（`DownloadQueue.kt:159`）。
3. **状态机硬断言**：非法状态转移用 `check` 显式抛出，例 `DownloadTask.kt:48` 的 `check(status.canTransitionTo(next))`。禁止用条件跳过掩盖状态错乱。
4. **错误布局复用**：全屏错误态统一复用 `ErrorScreen`（`ui/components/StatusViews.kt:42`），列表脚部统一复用 `LoadMoreFooter`（`ui/components/ListContainers.kt:44`）。COUI 侧复用 `CouiErrorGroup`（`ui/coui/component/CouiLoadingStates.kt:65`）与 `CouiLoadMoreFooter`（`:97`）。禁止屏幕自造错误布局。
5. **跟手动画取消**：阅读器翻页与复位统一调 `springAnimateToPage`（`reader/ReaderActions.kt:43` 手动翻页、`:59` 跳页编排），避免另起协程争夺滚动状态。分页器目标页夹取统一经 `clampPagerTarget`（`reader/ReaderPagerTarget.kt:4`）。
6. **文案与日志事实性**：日志只输出任务标识、文件路径或异常类型，不夹带私密数据。用户提示统一简体中文与全角标点。

---

## 四、构建、依赖与交付物

1. **依赖版本单一真源**：版本号集中定义于 `gradle/libs.versions.toml`，模块 `build.gradle.kts` 内禁止出现依赖字面版本号。`versionCode = 20000` 与 `versionName = "2.0.0"`（`app/build.gradle.kts:47-48`）仍为模块内字面量，改动须同步 CHANGELOG 与 Git tag。新增依赖须同步核对许可证与 R8 规则。
2. **单向模块依赖**：只允许 `:app` 依赖 `:core`（`app/build.gradle.kts:107`），禁止反向依赖。`:core` 保持 Java 17（`core/build.gradle.kts:13-14`），`:app` 用 Java 21（`app/build.gradle.kts:86-87`）。
3. **SDK 坐标与传输安全矩阵**：固定 `compileSdkVersion("android-37.0")`（`app/build.gradle.kts:40`）、`minSdk = 31`（`:45`）、`targetSdk = 36`（`:46`）。下限受 `COUI-Kit 0.0.1-alpha12` 元数据强制约束，调整前先核对依赖矩阵。
4. **明文流量禁用**：`AndroidManifest.xml:25` 声明 `android:usesCleartextTraffic="false"`。应用内更新下载拒绝非 HTTPS 与 3xx 降级重定向（`UpdateDownloader.kt:337-338`、`:362-363`）。`data_extraction_rules.xml`（`AndroidManifest.xml:23`）排除敏感 DataStore 备份迁移。
5. **Release 混淆与压缩**：`release` 强制开启 `isMinifyEnabled` 与 `isShrinkResources`（`app/build.gradle.kts:64-65`），规则维护于 `app/proguard-rules.pro`。新增反射式依赖必须实测 Release 包。
6. **签名安全边界**：Release 签名从 `local.properties` 读取四键（`app/build.gradle.kts:12-17`、`:69`）。四键缺失时回退调试签名；四键齐全但 keystore 文件不存在时配置期直接抛 `GradleException`（`:24-30`）。密钥、口令与 Cookie 严禁入库。
7. **Gradle 执行纪律**：Windows 固定用 `.\\gradlew.bat`，禁止传 `--offline`。同一工作树同时只允许一个 Gradle 构建进程，并行任务须串行化构建。
8. **AGP 联动开关**：`gradle.properties:7`（`android.builtInKotlin=false`）与 `:9`（`android.newDsl=false`）在升级 AGP 或 KGP 时须成对检查。
9. **仓库入库红线**：`.gitignore` 已忽略 `AGENTS.md`、`docs/`、`.scratch/`、`.screenshots/`、`.tools/`、`androidMain/`、`*.apk` 与 `local.properties`。内部调研、过程台账与本地工具产物严禁提交至 Git。

---

## 五、界面组件与阅读器内核

### 5.1 双皮肤组件边界

- 皮肤按界面风格分工：HyperOS 用 `Miuix`，ColorOS 用 `COUI-Kit`（`io.github.itoseo:coui:0.0.1-alpha12`，`app/build.gradle.kts:133`，入口 `ui/coui/theme/CouiTheme.kt`）。禁止跨皮肤混用页面骨架与卡片组件。
- COUI 页面只允许从 Miuix 借图标组合件与 `SnackbarHostState` 等库内缺件，禁止全仓引入 `androidx.compose.material3`。
- 两套皮肤的圆角与颜色统一取自 `LocalSkin`（`ui/skin/SkinTokens.kt:201`）：HyperOS 卡片圆角 `16 dp`、控件 `12 dp`（`:63-64`）；ColorOS 卡片圆角 `18 dp`、控件 `14 dp`（`:174-175`）。

### 5.2 公共控件与底部净空

- HyperOS 侧三态与基础组件统一复用 `ui/components/`：
    - `LoadingScreen`、`ErrorScreen`、`EmptyState`、`CenteredEmptyState` 在 `StatusViews.kt`
    - `LoadMoreFooter`、`PagerBar`、`TrackedLazyColumn` 在 `ListContainers.kt`
    - `rememberSelectionHaptic` 与 `CoverSkeletonState`（配套 `rememberCoverSkeletonState`、`Modifier.coverSkeleton`）在 `InteractionModifiers.kt`
- 长列表一律用 `TrackedLazyColumn`，由其内部承载 `ListScrollBar` 并共享 `contentPadding`。禁止在页面层自拼滚动条。
- COUI 侧复用 `CouiAppComponents.kt` 的 `CouiAppScaffold`（`:90`）、`Modifier.couiPressable`（`:184`），以及 `CouiLoadingStates.kt` 的 `CouiLoadingGroup`（`:35`）、`CouiEmptyGroup`（`:50`）、`CouiErrorGroup`（`:65`）与 `CouiLoadMoreFooter`（`:159`）。
- COUI 页面由 `CouiAppScaffold` 统一留出底栏净空（默认值取自 `couiPageExtraBottomDp`，见 `ui/coui/component/CouiScreenScaffold.kt:71`、`:176`）。禁止页面硬编码底部占位高度。
- 底部净空只有一处推导 `ui/BottomSpacing.kt`：`bottomBarContentPaddingDp`（`:41`；`DOCKED` 退回条高 `:46`、`FLOATING` 加底距与呼吸 `:47-57`）。`ui/glass/BottomBarTokens.kt:71-75`、`:121-125` 直接调用，不写字面量。
- 其余净空函数：`couiPageExtraBottomDp`（`:101`，只认 `hasFab` 与 `imeOccupiesPage` 两个开关）、`couiHomeExtraBottomDp`（`:111`）、`glassHomeExtraBottomDp`（`:129`）、`creatorWorkExtraBottomDp`（`:167`）。旧的 `ui/BottomClearance.kt` 与其测试已整体删除，禁止再引入第二套净空公式。
- 跨屏交互件同类收口：复制文本统一走 `copyTextToClipboard`（`ui/ClipboardActions.kt:28`，调用点 `ui/album/AlbumDetailScaffold.kt:195`、`ui/coui/page/CouiUpdateScreen.kt:214`），成败文案由 `clipboardCopyMessage`（`:17`）按 `writeClipboardText`（`:43`）的真实结果给出。禁止各屏先弹成功再写。
- 资料页草稿与脏键集合收进 `ProfileDraftState`（`ui/ProfileDraftState.kt:18`）与 `ProfileDraftStateSaver`（`:77`），由 `ui/profile/ProfileScaffold.kt:229` 的共享骨架持有一份。
- 两皮肤各自的 `ProfileScreen` 与 `CouiProfileScreen` 只调 `rememberProfileScaffoldState`（`ui/ProfileScreen.kt:57`、`ui/coui/page/CouiProfileScreen.kt:106`）。
- Miuix `0.9.4` 实测基线：`CardDefaults.InsideMargin` 默认为 `0 dp`，弹窗水平内边距为 `24 dp`，调用方勿叠加重复水平边距。圆角裁切与描边优先用 `squircleClip`、`squircleBorder` 与 `squircleSurface`。

### 5.3 阅读器手势与解码管线

- **缩放态**只由当前页经 `isPageZoomed`（`ui/ReaderZoomLogic.kt:13`）回写，翻页后自动复位。
- 横向 `HorizontalPager` 恒挂 `.pagerGestureOverride(pagerState, enabled = !pageZoomed)`（`ui/ReaderPageHost.kt:99`、`ui/coui/page/CouiReaderPageContent.kt:107`），保证放大平移时不触发翻页。
- **放大贴边翻页**由 `collectHorizontalEdgeDrag`（`reader/ReaderEdgeDrag.kt:18`）采集：在 `PointerEventPass.Initial`（`reader/ReaderEdgeDrag.kt:27`）取原始位移，多指直接退出（`:29`），交由 `EdgeDragTracker`（`ReaderZoomLogic.kt:58`）与 `decideEdgePageTurn`（`:23`）判定。
- 松手后等 `EDGE_CONFIRM_DELAY_MS = 120L`（`ui/ReaderZoomLogic.kt:46`）复核内容静止，判定下沉至 `reader/ReaderEdgeConfirm.kt` 的 `edgeDragStillPinned`；通过后再经 `ReaderActions.turnPage` 弹性翻页。
- 两皮肤逐字重复的编排收在 `reader` 包：
    - `ReaderActions`（`reader/ReaderActions.kt:19`，`turnPage` `:36`、`goToPage` `:48`、`persistProgress` `:76`）
    - 双页同框单缩放层 `ReaderSpreadPage`（`reader/ReaderSpreadPage.kt:35`，`rememberZoomableState` `:44`、缩放态判定 `:46`、翻走复位 `:50-52`、排布分支 `when (spreadArrangeFor(...))` `:74-89`）
    - 页码夹取 `ReaderPagerTarget.kt:4`
- 两屏只剩参数接线（`ui/ReaderPageHost.kt:116`、`ui/coui/page/CouiReaderPageContent.kt:124`）。禁止把编排抄回屏幕。
- 纯常量集中于 `ReaderZoomLogic.kt:4-49`：`ZOOMED_THRESHOLD = 0.02f`、`EDGE_PAGE_DISTANCE_RATIO = 0.18f`、`CONTENT_PIN_TOLERANCE_PX = 24f`、`EDGE_CONFIRM_DELAY_MS = 120L`、`EDGE_CONFIRM_MOVE_PX = 8f`。禁止散落回 Composable。
- **布局比例与解码尺寸**由 `readerPageFrame`（`ui/ReaderPageLogic.kt:108`）同源算出，展示与预取共用同一换算，确保内存缓存键一致。
- 像素预算派生自 `ReaderImageQuality.STANDARD.maxDecodePixels`（4,000,000 px，`ui/ReaderPreferences.kt:6`），单边上限 `MAX_PAGE_DECODE_EDGE_PX = 8192`（`ReaderPageLogic.kt:33`）。
- 解码统一经 `configureReaderDefaults()`（`ui/ReaderImages.kt:266`）指定 `Bitmap.Config.RGB_565`。4 MP 标准档单页位图约 `8 MB`，较 `ARGB_8888` 降低 50%。
- 比例回填走 `readerPageBackfill`（`ReaderPageLogic.kt:155`）：`pageRatios` 无条件更新，`decodeRatios` 仅在 `isBoxLimited`（`:126`）为真时回填。
- 纵向长图不做就地缩放，双击打开全屏缩放覆盖层（`zoomOverlayIndex`，`ui/ReaderPages.kt:130`；COUI 侧 `ui/coui/page/CouiReaderPages.kt:144`）。章节种子只接受不窄于 `MIN_SEED_ASPECT_RATIO = 0.5f`（`ReaderPageLogic.kt:40`）的常规页比例。
- **页码真源**是 `ReaderPagePlan`（`core/src/main/kotlin/com/jm3/core/reader/ReaderPagePlan.kt:17`，`jumpTo` `:53`、`settleJump` `:65`、`visiblePage` `:74`）。
- 屏幕会话状态收口于 `ReaderSession`（`app/src/main/kotlin/com/jm3/app/miuix/reader/ReaderSession.kt:52`），两皮肤共用（`ui/ReaderPages.kt:123`、`ui/coui/page/CouiReaderPages.kt:137`）。
- 恢复时序为三态 `ReaderRestorePhase`（`ReaderSession.kt:15`：`AWAITING_LAYOUT` `:17`、`RESTORING` `:20`、`SETTLED` `:23`）。`restore`（`:136`）先读旧进度，再置 `restored` 并开写盘闸门，最后解出目标页 `ReaderRestoreOutcome`（`:32`）。
- 跳页与预取各留一个入口：`requestJump`（`:86`）/ `onJumpSettled`（`:94`）、`onVisibleIndex`（`:103`）/ `resetPrefetchLedger`（`:114`）。页码语义沿用 `:core` 纯函数，禁止在屏幕复制换算。

### 5.4 动效、共享元素与大屏窗口

- **封面骨架**只挂载于 `AlbumCoverImage`（`ui/AlbumHero.kt:387`），外部卡片不再重复套用骨架修饰符。
- **沉浸式**由 `MainActivity.kt:27` 开启，`MainActivity.kt:84` 把音量键分发给 `ReaderVolumeKeys`（`ui/ReaderScreen.kt:29`）。
- 长图缩放覆盖层打开时，音量键**不消费并返回 `false`**，按键交回系统（`ui/ReaderEffects.kt:246-248`；COUI 策略 `ui/coui/page/CouiReaderLogic.kt:150-154` 的 `couiShouldHandleReaderVolumeKey` 同时要求无覆盖层、无弹窗）。禁止把覆盖层态写成消费。
- 销毁时须校验回调归属后再置空（`ReaderEffects.kt:260-263`、`ui/coui/page/CouiReaderPages.kt:562`）。
- **共享元素飞行**：列表侧挂 `.albumHeroCover`（`AlbumHero.kt:540`），详情侧挂 `.albumHeroHeader`（`:644`）。圆角读 `LocalSkin.current.shapes.cardCorner`；行卡缩略图 `HERO_THUMB_CORNER_DP = 8f`（`:427`），角标 `HERO_BADGE_CORNER_DP = 6f`（`:430`）。
- 详情路由转场固定为纯 alpha 渐变（`albumDetailTransitionFor`，`Jm3App.kt:370`）。禁止改为水平位移动画。
- **窗口尺寸**统一读 `rememberWindowDpSize()`（`ui/AdaptiveLayout.kt:76`），整数断点用 `Dp.toLayoutDpInt()`（`:83`）向下取整。禁止使用 `Configuration.screenWidthDp`。
- 大屏阈值集中于 `WideLayoutPolicy`（`core/src/main/kotlin/com/jm3/core/layout/WideLayoutPolicy.kt:22`）：中屏 `600 dp`（`:25`）、大屏 `840 dp`（`:28`）、中屏正文列上限 `720 dp`（`:45`）、大屏正文列上限 `1,120 dp`（`:53`）、详情双栏条件（`:56`、`:59`、`:62`）、侧栏宽高双条件（`:78`）。行卡两列与网格列数均从此处派生。

### 5.5 玻璃表面与运行时能力门槛

- **能力门槛唯一**：运行时着色器可用性只问 `isRuntimeShaderSupported`（`ui/effect/ShaderCapability.kt:44`，带 `@ChecksSdkIntAtLeast` `:43`）。禁止 import `miuix` 库内同名函数，禁止另写 `SDK_INT` 判断。
- 调用点收敛于以下位置：
    - `ui/effect/RuntimeShaderCompat.kt:21`、`:35`、`:44`、`ui/effect/BgEffectBackground.kt:37`
    - `ui/glass/BackdropSupport.kt:21`、`ui/glass/FloatingBottomBar.kt:224`、`:236`、`:447`
    - `ui/glass/GlassScaffold.kt:130`、`ui/glass/GlassSurface.kt:253`、`ui/glass/Lens.kt:26`、`ui/glass/InteractiveHighlight.kt:55`
- 需要展示「为什么不可用」时用查询形态 `ShaderCapability`（`:15`）与 `resolveShaderCapability`（`:22`），原因枚举 `ShaderUnavailableReason`（`:9`）。
- **顶栏玻璃计划唯一**：顶栏磨砂由 `GlassScaffold` 内部渐进模糊承担，不画 `glassSurface`。
- 形态只解析一次：`resolveTopBarGlass`（`ui/glass/TopBarGlass.kt:35`）返回 `TopBarGlass`（`:20`，字段 `:21-24`），`GlassScaffold.kt:127-135` 依它决定模糊层与兜底色。tint 常量 `TOP_BAR_BLUR_TINT_ALPHA = 0.3f`（`TopBarGlass.kt:28`）。
- **背景采样源**：需要真实内容作模糊源的页面统一调 `rememberBlurBackdrop()`（`ui/glass/BackdropSupport.kt:20`）并挂 `Modifier.layerBackdropSource`（`:30`）。现有消费点：
    - `ui/MainScreen.kt:153`（`:224` 挂采样源）
    - `ui/LibraryScreens.kt:1026`（创作者页头图，`:1030` 挂采样源，`progressiveTextureBlur` 见 `:1038`）
    - `ui/ReaderPages.kt:175`（悬浮工具栏，先垫主题底色避免透明区黑边）
    - `GlassScaffold.kt:124`（`:196` 挂采样源）
- 禁止页面各自用 `graphicsLayer` 手搭采样层。

---

## 六、Agent 工作流与边界测试

### 6.1 改动权限与拆单要素

- **拆单四要素**：需求描述、验收标准、架构约束（允许修改范围与可复用件）、测试清单。仓库已配 `.github/workflows/ci.yml`，本地规范以本文与 `AGENTS.md` 为准。
- **Agent 可直接改**：`:app` 屏幕与组件、`:core` 纯函数与单测、文案与样式微调、本文规则与行号同步更新。
- **须先请示用户**：修改 `Jm3Gateway` / `SessionAuth` 接口签名、改动 `DownloadTask` 状态机、调整模块依赖方向、修改签名与放宽 `proguard-rules.pro`、新增对「禁止」条款的例外。

### 6.2 完成定义与 TDD 流程

每次提交前依次完成，缺一不可：

1. **红绿重构**：业务逻辑、数据处理与缺陷修复先写测试，跑出真实业务断言失败，再写最小实现转绿。绿灯后严禁改弱断言。纯 UI 样式调整可豁免先行测试，但其底层状态计算仍需单测覆盖。
2. **构建验证**：执行 `.\gradlew.bat :app:assembleDebug`（纯 `:core` 改动至少执行 `:core:compileKotlin`）。因并发锁等原因未跑时，汇报须如实写「未验证」。
3. **测试通过**：执行 `.\gradlew.bat :core:test :app:testDebugUnitTest`。
4. **更新日志**：在 `CHANGELOG.md` 的 `## [未发布]` 下按「新增 / 修复 / 优化」补用户可读条目，不泄露内部类名与哈希。
5. **提交**：纯文本中文 Conventional Commits，逐个逻辑变更提交，严禁 Emoji。
6. **四项汇报**：改了什么、为什么改、验证命令与结果、是否触碰既有硬约束。

### 6.3 全仓测试规模

测试基线以 `build/test-results/**/TEST-*.xml` 为准：

- **`:core`**：80 个测试类 / 757 个用例，位于 `core/src/test/kotlin/com/jm3/core/`。同目录另有不含 `@Test` 的共用替身 `FakeSessionAuth.kt`。
- **`:app`**：142 个测试类 / 1,223 个用例，位于 `app/src/test/kotlin/com/jm3/app/miuix/`。网关替身 `FakeGateway.kt` 在子目录 `data/` 下，不含 `@Test`。
- **合计**：222 个测试类 / 1,980 个用例，0 失败、0 跳过（222 个 `TEST-*.xml` 与 1,980 个 `@Test` 逐一对齐）。

双皮肤共享骨架测试的断言框架为 `kotlin.test`。其中 7 个类为构造 `mutableStateOf` 引入 Compose Runtime，不引入 Android 运行时：`TasksScaffoldTest`、`BrowseScaffoldTest`、`ForumScaffoldTest`、`LoginScaffoldTest`、`MineScaffoldTest`、`ProfileScaffoldTest`、`WeekScaffoldTest`。

规模靠前的骨架测试：

- `FavoriteScaffoldTest`（42）、`TasksScaffoldTest`（26）、`SearchScaffoldTest`（24）
- `ForumScaffoldTest`（23）、`BrowseScaffoldTest`（22）、`MainScaffoldTest`（22）
- `NotificationsScaffoldTest`（21）、`HistoryScaffoldTest`（20）、`TrackingScaffoldTest`（19）
- `WeekScaffoldTest`（19）、`MineScaffoldTest`（18）、`SettingsSectionsTest`（18）
- `UpdateScaffoldTest`（17）、`ShelfScaffoldTest`（17）

典型纯逻辑测试：

- `AccountManagerTest`（39）、`AlbumHeroLogicTest`（31）、`ReaderPageLogicTest`（31）
- `CheckInLoadPolicyTest`（30）、`MappersTest`（29）、`ReaderProgressCodecTest`（27）
- `AlbumHeroHostTest`（26）、`CouiWorkCardLayoutTest`（26）、`PagedFeedTest`（22）
- `ReaderZoomLogicTest`（22）、`DownloadQueueTest`（22）、`SessionListLoaderTest`（22）
- `PagingBehaviorTest`（21）、`CouiFeedPolicyTest`（21）、`SessionCacheTest`（15）

### 6.4 核心边界测试清单

新增或修改以下能力时，对应测试类必须同步覆盖并保持绿灯：

- **下载状态机与队列**
    - 非法状态转移 → `DownloadTaskTest`
    - 同话去重、失败重入、并发上限 2、取消不耗重试 → `DownloadQueueTest`、`DownloadContractTest`
    - 断点恢复与台账分离写 → `DownloadRecoveryTest`、`DownloadLedgerTest`、`DownloadManagerPersistenceTest`
- **下载文件与前台策略**
    - 页文件原子替换、路径校验、失败不留 `.tmp` → `FileDownloadStoreTest`（17）
    - 窄接口取页契约 → `GatewaySourcesTest`；页码清单编解码 → `DownloadPagesCodecTest`
    - 通知文案与条目呈现 → `DownloadPresentationTest`、`DownloadStatusSemanticsTest`、`DownloadRowTest`、`DownloadRowTextTest`
    - 前台策略与退避 → `ForegroundPolicyTest`、`ForegroundRunnerTest`
- **分页、封面与协议映射**
    - 1 基页码换算 → `PageNumbersTest`；翻页计算 → `PagingBehaviorTest`
    - 去重与触底距离 → `PagedFeedTest`、`SearchPagesTest`、`CommentPagesTest`、`SerializationPageTest`
    - 分割还原 → `ScrambleTest`
    - 封面三态与缓存键互逆 → `CoverResolverTest`、`CoverLocatorTest`
    - 域名清洗与线路切换缓存 → `DomainsTest`、`CustomDomainPoolTest`、`DomainPoolCacheTest`、`ApiDomainTest`、`GatewayFactoryTest`
    - 章节分组与标签 → `ChapterGroupsTest`、`ChapterLabelsTest`、`NotificationBodyTest`
- **会话、凭据与更新**
    - 会话编解码 → `SessionCodecTest`
    - 加密凭据落盘、坏数据自清、Keystore 密钥重建 → `CredentialStorageTest`、`CredentialStoreTest`
    - 取消异常重抛 → `CancellationTest`
    - 反射成员表与降级 → `LibraryReflectionTest`、`LibrarySessionReflectionTest`、`LibraryBridgeRoutingTest`
    - 静默重登、并发竞态、3 次失败清凭据 → `AccountManagerTest`（39）；启动闸门与失效通知 → `AuthCallTest`
    - 未读角标保留与纪元保护 → `UnreadBadgeTest`
    - 版本等价、更新校验与设置归一 → `VersionTextTest`、`UpdateCheckerTest`、`UpdateDownloaderTest`、`ReleaseJsonTest`
    - 设置存取与归一 → `UpdateSettingsTest`、`SettingsRepositoryTest`、`SettingsNormalizationTest`、`SettingsKeyStoreAdapterTest`
- **列表加载编排**
    - 首屏、刷新、触底、跳页四类请求的代次丢弃与失败落点 → `SessionListLoaderTest`（22）
    - 失败落点细则：有内容保留并提示、无内容整屏、`goToPage` 保留旧页、刷新与跳页失败不占用页脚重试位
    - `ListStateCache` 只收已落定快照、按账号分仓、`forgetPages` 与 `clear` 作废语义 → `SessionCacheTest`（15）
- **阅读器、转场、布局与皮肤**
    - 缩放与边缘翻页 → `ReaderZoomLogicTest`（22）
    - 页框比例与解码上限 → `ReaderPageLogicTest`（31）、`ReaderPageBackfillTest`（8）
    - 页码计划与跳页冻结 → `ReaderPagePlanTest`（12）
    - 恢复时序与写盘闸门 → `ReaderSessionTest`（13）；预取步进 → `ReaderPrefetchTest`
    - 进度编解码 → `ReaderProgressCodecTest`（27）
    - 共享元素几何、宿主状态机与缓存尺寸 → `AlbumHeroLogicTest`（31）、`AlbumHeroHostTest`（26）、`AlbumHeroStateMachineTest`（13）、`AlbumHeroCoverSizeTest`（4）
    - 详情状态容器 → `AlbumDetailStateHolderTest`（17）
    - 加载结局翻译与两钩子分支 → `ScreenLoadPlanTest`（6）、`ListOutcomeTest`、`NovelActionNoticeTest`
    - 签到策略 → `CheckInLoadPolicyTest`（30）
    - 玻璃表面、能力门槛与顶栏分档 → `GlassSurfaceTest`、`InteractiveHighlightTest`、`ShaderCapabilityTest`（3）、`TopBarGlassTest`（5）
    - COUI 组件与阅读器逻辑 → `CouiPageStateTest`、`CouiTypographyTest`、`CouiForumRepliesTest`、`HistoryGroupingTest`、`CouiNotificationsSwipeTest`、`CouiNovelDetailChaptersTest`、`CouiReaderLogicTest`（13）
    - 大屏与底栏策略 → `WideLayoutPolicyTest`（16）、`BottomBarPreferencesTest`（9）、`WideScreenNavigationTest`（8）
    - 双页同框页码夹取 → `ReaderPagerTargetTest`（4）；底部净空推导 → `BottomSpacingTest`（9）
    - 复制结果文案 → `ClipboardActionsTest`（3）；资料草稿与脏键恢复 → `ProfileDraftStateTest`（5）
- 缺口：`reader/ReaderActions.kt`、`reader/ReaderSpreadPage.kt` 与 `reader/ReaderEdgeDrag.kt` 目前无对应测试类，见附录 B #7。

---

## 附录 A：已定决策与理由

与以下决策冲突的改动，必须先更新决策再动代码，禁止暗中绕过。

1. **缩放选用 `telephoto`**：`me.saket.telephoto:zoomable-image-coil3:0.19.0`（`gradle/libs.versions.toml:19`、`:33`）同时提供分块超采样与缩放感知解码，避免放大模糊。
2. **手势让渡显式关闭翻页拦截**：Miuix `PagerSwipeNode` 在 `Initial` 阶段判消费，`telephoto` 在 `Main` 阶段消费。故横向 `HorizontalPager` 恒挂 `.pagerGestureOverride(pagerState, enabled = !pageZoomed)`（`ui/ReaderPageHost.kt:99`、`ui/coui/page/CouiReaderPageContent.kt:107`），防止平移与翻页同时触发。
3. **放大贴边翻页用 `Initial` 旁路**：`telephoto` 越过触摸阈值后会消费全部位移，故在 `PointerEventPass.Initial` 采集原始位移并减去内容偏移（`reader/ReaderEdgeDrag.kt:27`），松手 `120 ms` 后复核内容静止再判定翻页。
4. **长图模式禁止就地缩放**：层内缩放会与纵向滚动冲突并裁切内容，故双击弹出全屏缩放覆盖层。页框跟随页内真实比例，避免 3:4 强制裁切导致条幅拉伸模糊。
5. **阅读器图片强制走磁盘快照**：`telephoto` 分块超采样依赖 `FileImageSource` 的 `diskCacheKey`。禁止把内存 `okio.Buffer` 当常规路径返回；缓存键统一由 `ReaderKeyer.keyOf` 生成。
6. **下载前台服务只管前台态与通知**：队列调度留在 `DownloadQueue`，生命周期决策由纯类 `ForegroundPolicy` 产出，防抖与拉起失败退避由 `ForegroundRunner` 驱动，不引入 `WorkManager`。
7. **通知节流 `300 ms` 与配额退避**：常规进度按 `300 ms` 节流，终态立即刷新。`dataSync` 6 小时配额耗尽后暂停在途任务并保留可见通知，不做保活；系统拒绝拉起前台服务后按 `5 s` 退避重试一次。
8. **许可证隔离**：只允许复制 `Apache-2.0`、`MIT` 或 `ISC` 源码并保留 SPDX 标注；`GPL-3.0` / `AGPL-3.0` 项目只采思路、自行重写。仓库主体为 `MIT`。`app/src/main/kotlin/com/jm3/app/miuix/ui/` 下 70 个文件带 `Apache-2.0` 头：
    - `glass/` 15、`coui/` 38、`ui/` 直属 12、`effect/` 2、`route/` 1、`favorites/` 1、`week/` 1。
    - `CouiIcons.kt` 带 `ISC` 头；`COUI-Kit` 按 `MPL-2.0` 依赖接入。明细见 `NOTICE`。
9. **提升型持续通知按 API 36.1 判定**：`setRequestPromotedOngoing` 只存在于 Android 16.1（API 36.1），必须先判 `SDK_INT >= 36` 再判 `SDK_INT_FULL >= BAKLAVA_1`（`DownloadNotifier.kt:216`、`:228`），避免 Android 16.0 触发 `NoSuchMethodError`。
10. **共享元素用自绘覆盖层**：`miuix-nav 0.9.4` 转场无 `AnimatedVisibilityScope`，故用 `onGloballyPositioned` 登记起止矩形并在 `AlbumHeroOverlay` 插值绘制，取缩略图地址与 `crossfade(false)`。拿不到起止矩形时退化为普通转场。
11. **静默重登连续失败 3 次清凭据**：`AccountManager` 持久化 `renewFailureCount`，连续 3 次失败后清除已存加密凭据，避免每次冷启动重复无效登录；任一次成功立即归零，兼容短暂断网。
12. **`:core` 日志单一出口 `Jm3Log`**：`:core` 不引入 Android 日志依赖，反射成员缺失时统一经 `Jm3Log` 输出成员名与异常类名，`:app` 在初始化时桥接至 `android.util.Log.w`。
13. **会话注入由调用方传网关实例**：`AccountManager.inject` 直接接收已判定的 `SessionAuth`，防止线路并发切换瞬间把 Cookie 注入废弃网关。启动闸门由 `openStartupGate()` 唯一放行。
14. **网关调用唯一入口 `AuthCall`**：启动闸门等待、IO 切换与单次失效通知收敛在 `AuthCall.call`，消除界面层重复的 401 判断与线程切换样板。
15. **屏幕只认加载结局**：`SessionOutcome` 至界面状态由 `planLoad`（`ScreenLoadPlan.kt:31`）统一翻译，副作用落点再经 `applyLoadPlan`（`:71`、`:90`）只写本屏差异。禁止界面直接引用 `LOGIN_EXPIRED_MESSAGE`。
16. **分页状态机统一为 `PagedFeed`**：`PageSlice` 与 `PagedFeed` 负责按 ID 去重、游标推进与触底判定（`PREFETCH_DISTANCE = 3`），翻页算法归 `PagingBehavior`。
17. **章节分组以首章 ID 为身份**：`ChapterGroups` 按原始正序每 10 章切分一次，倒序展示只反转组序与组内条目，避免切排序后折叠态错位。
18. **下载状态语义与展示分离**：活动、终态、重排判定直接挂 `DownloadStatus` 枚举，书架行与通知栏形状分别由纯函数 `presentRow` 与 `present` 生成。
19. **封面三态 `Cover`**：区分 `Ready`、`Unready` 与 `None`（有址、域名未就绪、无封面），`CoverResolver` 与 `coverCacheKeyText` 互逆校验。列表 DTO 现阶段仍透传可空地址。
20. **反射成员表唯一登记处 `LibraryReflection`**：成员名、所属类、字段/方法形状与参数类型集中为 `members`（`LibraryReflection.kt:88-99`），解析、降级与异常包装（`:102`、`:122`、`:129`、`:146`）同处。库升级的影响面被压成一张 10 行表，单成员缺失只降级不抛异常。
21. **组装根只做装配、屏幕只见窄面**：`Jm3Graph` 只按序初始化六大域，页面全部经 `ScreenDeps`（`ScreenDeps.kt:49`）取依赖；读面切成 `routes`（`:71`）与 `images`（`:75`），偏好收窄为四个扁平只读属性，写面只经 `select*`。禁止半注入半全局的混合签名。
22. **版本比对出口唯一**：规范化、解析与等价判断统一用 `VersionText`（`core/update/VersionText.kt:11`），设置项去空命名 `VersionSettingText`（`SettingsRules.kt:59`）。
23. **下载页落盘原子替换**：`FileDownloadStore` 先写 `.tmp` 并加锁 `Files.move(..., REPLACE_EXISTING)`，异常时清理 `.tmp`，满足跨平台重下覆盖契约。
24. **复杂屏幕状态进 Holder**：`AlbumDetailStateHolder` 把详情页复杂状态抽成纯 Kotlin 容器，使核心界面业务脱离 Compose 运行时可直接测试。
25. **玻璃表面统一角色计划表**：除底栏动态按压与录制层外，其余玻璃表面统一经 `glassRoleSpec` 与 `resolveGlassSurface` 按「角色 + 降级档位」计算模糊与遮罩参数。
26. **跨文件换算单一出口**：比例回填（`readerPageBackfill`）、预取步进（`advancePrefetch`）、版本解析与反射成员等规则各保留唯一纯函数出口，避免双写漂移。
27. **偏好写入连动作带副作用**：`ScreenDeps.select*`（`:131`、`:143`、`:152`、`:158`）把三段写序封成一个动作，屏幕只表达意图。分页模式是唯一改变分页语义的入口，故只有 `selectPaginationMode` 额外调 `forgetPages()`（`:135`）。漏掉该清的缓存会让旧分页继续渲染。
28. **加载结局只有一个翻译出口**：`planLoad` + `applyLoadPlan` 承担 `Expired` / `Failed` 分派与 Snackbar 落点（现有 17 个生产文件引用），屏幕只写差异文案与两处钩子。
29. **阅读器会话状态收进 `ReaderSession`**：两皮肤共用同一状态机（`ui/ReaderPages.kt:123`、`ui/coui/page/CouiReaderPages.kt:137`），恢复时序、跳页冻结、写盘闸门与预取去重只有一份；页码语义仍引用 `:core` 纯函数。
30. **运行时能力与顶栏形态各收一处**：着色器能力只问 `isRuntimeShaderSupported`（`ShaderCapability.kt:44`），顶栏玻璃形态只经 `resolveTopBarGlass`（`TopBarGlass.kt:35`）解析一次。否则同一判断散在六个玻璃文件与绘制分支，降级档位会不一致。
31. **测试替身按被测面收窄、越界即失败**：`:core` 用只实现 6 方法的 `FakeSessionAuth`，`:app` 需整张网关时用 `FakeGateway`（未配置方法一律 `error`，见 `data/FakeGateway.kt:60`）。替身不得返回兜底值，不得用反射代理按方法名分派。
32. **路由目标集中在 `AppRoutes`、屏幕只表达语义**：`AppRoutes`（`AppRoutes.kt:15`）把 24 个语义动作与 `push` / `replaceTop` 两个出口封在一起，屏幕形参收成 `nav: AppRoutes`。否则 46 个屏幕调用点各自拼 `Route`，改一次路由要扫全仓。语义动作也让「换章是同层替换」只在一处表达（`:55`）。
33. **分页取数编排收进 `SessionListLoader`**：请求代次、在途标记、翻页游标与失败落点只有一份（`SessionListLoader.kt:48`），屏幕只提供 `fetchPage` / `idOf` / `notice` 三个洞。此前十余个列表页各持一份代次令牌与 `beginLoadMore` 顺序，漏一处就会出现旧结果盖新列表或页脚常转。
34. **列表会话缓存只存已落定快照、按账号分仓**：`ListStateCache`（`SessionCache.kt:112`）只收 `Loaded`，`remember`（`:221-228`）先归一瞬时态，`SessionListLoader` 用 `seededSnapshot`（`:116-120`）把它当首帧，故缓存不会把「加载中」或「页脚失败」带到下一次进入。
    - 条目身份由 `AccountScopedKey`（`:132`）在键前补 uid，读写每次动态取当前账号（`scoped` `:163`、`bindAccount` `:159`、绑定点 `ScreenDeps.kt:64`）。
    - 作废入口是 `forgetPages`（`:238`，分页模式变化时清全部账号仓）与 `clear`（`:243`，登出）。
35. **阅读器共性收口到 `reader` 包**：两皮肤逐字重复的翻页、跳页、进度编排与双页同框、边缘拖拽判定分别进 `ReaderActions`、`ReaderSpreadPage`、`ReaderEdgeDrag`，两屏随之瘦身（`ui/ReaderScreen.kt` 108 行、`ui/coui/page/CouiReaderScreen.kt` 116 行）。「两皮肤必须一致」的编排留在屏幕里必然漂移。
36. **会话 Cookie 明文落盘是已知取舍**：`SessionStore` 把 `Jm3Session` 以 JSON 写入 `jm3_session`（`data/SessionStore.kt:16-36`），账号密码却经 Keystore AES/GCM/256 加密（`data/CredentialStore.kt:160-168`），敏感度看似倒挂。
    - 实际威胁模型是 root 或调试镜像下的文件读取，此时 Keystore 密钥同样可取用，加密只换来启动期解封开销。
    - 云备份与设备迁移已由 `allowBackup=false` 与 `data_extraction_rules.xml` 排除（`AndroidManifest.xml:23`）。
    - 改动该取舍前须先证明威胁模型变化。
37. **底部净空单一推导**：底栏 contentPadding 不再在 `BottomBarTokens` 注释里写 80 / 108 / 64，而由 `bottomBarContentPaddingDp`（`BottomSpacing.kt:41`）按条高与形态算出；COUI 页、FAB 与玻璃首页的额外净空同处。`BottomClearance` 及其测试整体删除，避免两套公式漂移。
38. **双皮肤同名页共享骨架、视觉各留皮肤**：20 组同名页面（首页、搜索、作品详情、分类浏览、收藏、论坛、更新、通知、历史、每周连载、任务、书架、追踪、观看记录、我的、个人资料、登录、阅读器入口、主框架、设置页）的共性收进同包 `XxxScaffold.kt`。
    - 收纳内容：加载编排、失败归类、翻页语义、弹窗与确认流状态、逐字文案常量。
    - 骨架形态：`internal class XxxScaffoldState` + `rememberXxxScaffoldState` + 纯函数 + 常量，零皮肤控件，仅 `SnackbarHostState` 类型入参；搜索页例外，只有 `SearchCriteria` / `SearchInputState` 纯状态机。
    - 皮肤侧只保留 `@Composable` 布局与控件选择，可见差异用显式参数或枚举表达（如 `HomePaginationReload`、`TrackingUndoReload`、`SettingsOptions`）。
    - 每对页面配 `kotlin.test` 测试固定状态迁移。
    - 逐字重复的双皮肤复制是「改一处漏一处」的主因（重构前同名页逐字重复 3,690 行）。

---

## 附录 B：明确暂缓事项与验证缺口

1. **Baseline Profile / macrobenchmark 暂不接入**：缺乏稳定常驻的多设备采样矩阵，且只提供冷启动度量。后续引入时以独立 `:benchmark` 模块与冷启动耗时为验收指标。
2. **ktlint / detekt 暂不接入**：全仓统一格式化会产生巨量无关差异，现阶段以构建检查、单测与人工审查把关。
3. **放大边缘翻页暂不做跟手拖拽**：跟手拖拽需重构 `HorizontalPager` 滚动源，改动面大于收益，维持松手判定后弹性翻页（`reader/ReaderEdgeDrag.kt:12-49`）。
4. **运行验证缺口**：
    - 已真机验证：小米 15（`targetSdk 36`）上的阅读器缩放与边缘翻页、双击整屏缩放覆盖层、`dataSync` 前台服务与进度通知（含提升型分支）。
    - 未真机验证：全仓主要靠编译、222 个测试类（1,980 个用例）的 JVM 单测与 Release 打包（R8 + lintVital）把关。待补测项：
        - 共享元素飞行轨迹与返回落点、封面骨架下沉后的转场观感
        - 切换标签后的滚动状态保存、`onTimeout` 6 小时超时回调（`DownloadService.kt:80`）
        - 窄屏与多折叠窗口下的双栏与列表边界
        - Android 13 及以上通知拒权回退
        - 超长条漫的真机内存与清晰度
        - Keystore 密钥损毁后的重建日志、真实会话过期后的自动重登链路
5. **COUI 皮肤暂不实现下拉刷新**：`COUI-Kit 0.0.1-alpha12` 的 `coui/scroll/` 只有 overscroll、fling 与 physics，无下拉刷新容器。COUI 页面的刷新入口统一由错误态重试与页面操作按钮承担，禁止在页面层手拼手势容器。
6. **小说列表只取第 1 页**：网关 `novelList(order, page = 1)`（`core/src/main/kotlin/com/jm3/core/Jm3Gateway.kt:210`）按页返回，两个小说列表屏都只请求第 1 页（`ui/coui/page/CouiNovelListScreen.kt:145`、`ui/novel/NovelScreens.kt:230`），故不强套 `PagedFeed`。需要翻页时必须先补屏幕编排与页码真源。
7. **阅读器共性件只测得到纯函数部分**：`reader/ReaderActions.kt`、`reader/ReaderSpreadPage.kt` 与 `reader/ReaderEdgeDrag.kt` 依赖 Compose 运行时（`PagerState`、`LazyListState`、指针事件），编排本身无测试类。可判定部分已抽纯函数并覆盖：
    - `planReaderTurnEntry`（`ReaderTurnEntryTest`，8 例）
    - `planJumpTarget`（`ReaderJumpTargetTest`，4 例）
    - `pagerGestureEnabled`（`ReaderGesturePolicyTest`，3 例）
    - `clampPagerTarget`（`ReaderPagerTargetTest`，4 例）
    - `ReaderActions` 的冻结与解冻顺序由 `ReaderWiringContractTest`（3 例）以源码契约固定
    - 「读到末页即清进度键」已下沉到 `core/src/main/kotlin/com/jm3/core/reader/ReaderProgress.kt:79` 的 `persistReaderProgress`，由 `ReaderProgressPersistTest`（5 例）覆盖
    - 余下 Compose 状态编排仍只能真机验证

