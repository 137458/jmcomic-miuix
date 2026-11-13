# 工程架构约束与开发规范（ENGINEERING）

本文定义 `jm3`（JM3 Miuix 客户端）的可执行工程约束。文档仅记录判定线、真实符号位置与技术决策。

**使用方式**：

- 编写代码前，先核对「决策三问」，再按六章查阅对应红线。
- 若约束与源码现状冲突，以源码现状为准，并在同一次提交内同步修订本文。
- 新增约束必须附带真实文件路径与行号；未核实的内容须显式标明「未核实」。

## 决策三问

开始任何功能或重构前，先回答以下三问：

1. **分层归属**：能进 `:core` 的逻辑禁止写进 `:app`；能写成纯函数的判定禁止藏在 Composable 内。`:core` 为纯 JVM 模块（`core/build.gradle.kts:1-4`），严禁引入 Android 类型。
2. **可测路径**：纯判定、换算、状态机与编解码必须可测。现有全仓共 181 个测试类（`:core` 78 个、`:app` 103 个）。无法测试说明分层有误，须先拆层。
3. **红线冲击**：核对是否破坏单一网关、单一状态真源、双皮肤组件隔离、阅读器手势优先级及 Release 混淆签名规则。

---

## 一、状态与依赖注入

### 1.1 进程组装根与六大域

进程内唯一的组装根为 `Jm3Graph`（`app/src/main/kotlin/com/jm3/app/miuix/Jm3App.kt:161`），由 `Jm3Application`（`:147`，在 `app/src/main/AndroidManifest.xml:17` 注册）与 `MainActivity` 统一经 `init(context)`（`:205`）加锁（`initLock` `:193`，`:207` 处进入 `synchronized`）幂等初始化。组装根只按启动顺序装配六个业务域，不再向屏幕暴露任何转发属性（旧的 `@Deprecated` 单行委托成员已整块删除）：

- `ApiDomain`（`data/ApiDomain.kt:27`）：管理网关实例、线路池缓存与线路切换锁。会话迁移由 `SessionDomain.applyDomains`（`data/SessionDomain.kt:43`）挂接。
- `SessionDomain`（`data/SessionDomain.kt:16`）：管理账号、带启动闸门的 `AuthCall`、未读角标与凭据存储。
- `SettingsDomain`（`data/SettingsDomain.kt:19`）：管理设置仓库与全局分页模式状态。
- `LibraryDomain`（`data/LibraryDomain.kt:18`）：管理唯一 Room 数据库 `jm3.db`（`:24`），提供历史仓库与下载台账存储。
- `DownloadDomain`（`data/DownloadDomain.kt:21`）：管理页文件存储、下载队列、台账与 `DownloadManager` 门面。
- `UpdateDomain`（`data/UpdateDomain.kt:16`）：管理 `UpdateDownloader` 唯一实例与安装包安装出口（`:39`）。

**硬性禁令**：

- 同一域内类型禁止在其他位置重复实例化。
- 屏幕统一经 `ScreenDeps`（`app/src/main/kotlin/com/jm3/app/miuix/ScreenDeps.kt:64`）接收注入依赖，禁止在屏幕内直读 `Jm3Graph.<成员>`。
- 偏好只读面收窄为 `PagingPreference`（`ScreenDeps.kt:38`）与 `BottomBarPreference`（`:48`），屏幕拿不到 `SettingsDomain` 本体；写偏好只允许经 `selectPaginationMode`（`:152`）、`selectBottomBarSize`（`:164`）、`selectBottomBarForm`（`:173`）、`selectWideScreenNavigation`（`:179`）。写序固定为「比较现值 → 写内存态 → 进程作用域落盘」，落盘唯一出口为私有 `persist`（`:187-193`，内部用 `runCatchingSuspending` 并只记日志）；四个入口里只有 `selectPaginationMode` 在写内存态后额外调 `sessionCache.forgetPages()`（`:156`），因为只有它改变了分页语义，其余三项照抄会白清缓存。屏幕不得自行拼这套顺序。
- 读面按关心点继续切窄：只要线路可观测信息的屏幕用 `routes`（`:81-82`），只要图片域名或相册封面地址的屏幕用 `images`（`:85-86`），两者都由网关对象转发，屏幕不必认识内容方法。
- `Jm3Graph` 成员仅允许 `Jm3Application`（`Jm3App.kt:150`）、`MainActivity`（`MainActivity.kt:29`、`:54`、`:58`、`:79`）、`DownloadService`（`data/download/DownloadService.kt:95`、`:147`、`:177`、`:184-185`、`:214`）与装配处 `ScreenDeps(...)`（`Jm3App.kt:432-441`）读取，且禁止在读取处写编排逻辑。

### 1.2 协程作用域与界面编排

- 唯一长期协程作用域为 `appScope`（`Jm3App.kt:164`，`SupervisorJob() + Dispatchers.IO`）。禁止使用 `GlobalScope`。
- 现有用途限于装配注入、`ScreenDeps.appScope`、`ApiDomain`（`:29`、`:119`）、`UpdateDomain`（`:16`、`:39`）、`DownloadManager`（`:28`、`:57`、`:62`）、`DownloadService`（`:147`、`:177`、`:184`）、`MainActivity.onStop`（`:79`）、`ReaderScreen` 退出补记页码（`:609`）、`CouiReaderScreen` 退出补记页码（`ui/coui/page/CouiReaderScreen.kt:645`）与设置页偏好写盘（经 `ScreenDeps.select*` 内部的 `persist`，见 `ScreenDeps.kt:187-193`；因退出设置页会取消 `rememberCoroutineScope`，导致内存状态与 DataStore 半途不一致，故须挂载至进程作用域）。新增使用点必须说明为何无法挂载至 `rememberCoroutineScope`。
- 本仓库不引入 `ViewModel`。屏幕数据状态使用 `remember` 与 `LaunchedEffect` 编排，状态复杂时抽离纯 Kotlin 状态容器（如 `AlbumDetailStateHolder`）。
- 路由目标只在 `AppRoutes`（`app/src/main/kotlin/com/jm3/app/miuix/ui/route/AppRoutes.kt:15`）拼装：构造只收两个出口 `push`（`:16`，普通压栈）与 `replaceTop`（`:17`，栈顶同层替换），对外是 25 个语义动作（`:19-69`；例 `album` `:46`、`reader` `:49`、`readerAt` `:52`、`nextChapter` 换章替换 `:55`、`search` `:69`）。表在 `Jm3App.kt:582` 构造一次（`:581-588`，`remember(backStack)`，`push` 实现为 `backStack.add`、`replaceTop` 实现为栈顶赋值）。
- 屏幕只认识语义动作，不拼 `Route`：屏幕形参统一为 `nav: AppRoutes`（例 `ui/MainScreen.kt:92`、`ui/AlbumDetailScreen.kt:142`、`ui/coui/page/CouiMainScreen.kt:88`），`Jm3App` 的 41 个屏幕调用点一律传 `nav = nav`（`Jm3App.kt:597` 起 23 个 `entry`），`ui/MainScreen.kt:245`、`:250`、`:255`、`:261` 再把同一个 `nav` 转发给四个页签。路由改名、加参数或把二级页改成同层替换只改表内一行，屏幕侧零改动。
- 网关调用一律经 `AuthCall.call`（`core/src/main/kotlin/com/jm3/core/session/AuthCall.kt:36`）。禁止在屏幕内自写 `withContext(Dispatchers.IO)` 调网关，禁止跳过启动闸门与统一失效回调。

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
- Room 数据库 `jm3.db` 仅包含 `history` 与 `downloads` 两张表（`data/history/HistoryStore.kt:24`、`:65`，`@Database(..., version = 3, exportSchema = false)` 见 `:167`），通过 `MIGRATION_2_3`（`:158`）为 `history` 建立 `albumId`、`updatedAt` 索引并为 `downloads` 建立 `updatedAt` 索引。改表必须提供 `Migration` 并升版本，禁止 `fallbackToDestructiveMigration`。

### 1.4 加载结局的单一翻译层

- `SessionOutcome` 至界面状态只允许经 `planLoad`（`app/src/main/kotlin/com/jm3/app/miuix/ui/ScreenLoadPlan.kt:31`）翻译为 `ScreenLoadPlan`（`ScreenLoadPlan.kt:18`，三相位 `Content` / `Expired` / `Failed` 见 `:10`）：有内容时保内容并给 `notice`（`keepContent` 置真），无内容时整屏呈现失效或错误。
- 带副作用的落点统一调用 `applyLoadPlan`：协程作用域重载（`:71`）与挂起重载（`:90`）各持两个钩子 `onKeepContent` / `onFullScreen`，由调用方只写本屏差异；动作类提示复用 `notifyAction`（`:114`）。
- 分页列表的取数编排唯一出口为 `SessionListLoader`（`app/src/main/kotlin/com/jm3/app/miuix/ui/list/SessionListLoader.kt:48`）：屏幕只提供三件事——取哪一页 `fetchPage`（`:52`）、条目 key `idOf`（`:50`）、保留内容时的提示 `notice`（`:53`），文案差异只留 `failureMessage`（`:51`）与 `stalePrefix`（`:54`）。状态面为 `snapshot`（`:59`，`SessionListSnapshot` `:29` 携带 `feed`（`:64`）/ `state`（`:66`）/ `refreshing`（`:68`），三者同名便捷读口见同段）；动作面只有 `loadFirstPage`（`:71`）、`refresh`（`:76`）、`loadMore`（`:81`）、`goToPage`（`:98`）、`removeWhere`（`:103`）。请求代次 `requestToken`（`:62`）只增不减，旧请求返回时对不上即丢弃（`:152`）。`notice` 是屏幕必给项，禁止传空实现——失败原因必须落到该屏的 Snackbar 宿主（COUI 三屏：`ui/coui/page/CouiHomeScreen.kt:217`、`:239`，`ui/coui/page/CouiBrowseScreen.kt:213`、`:234`、`:264`，`ui/coui/page/CouiSearchScreen.kt:157`）。
- 会话缓存是同一构造器上的两个可选尾部参数：`cache`（`:55`，`ListStateCache<T>?`）与 `cacheKey`（`:56`，`SessionListKey`）。给了缓存就以 `cache.read(cacheKey)` 的已落定快照作首帧（`seededSnapshot` `:116-120`），每次成功落定后经 `cacheLanded`（`:126-129`，写回点 `:163`）写回；没给则行为与加缓存前完全一致。缓存接口 `ListStateCache`（`app/src/main/kotlin/com/jm3/app/miuix/ui/SessionCache.kt:112`）只有四个动作 `read`（`:115`）/ `write`（`:118`）/ `forget`（`:121`）/ `hasPage`（`:124`），由 `SessionCache`（`:146`；实现点 `read` `:205`、`write` `:209`、`hasPage` `:214`）承担；缓存只收 `ListLoadState.Loaded`（`remember` `:221-228` 先把 `loadingMore` / `loadMoreFailed` 归一），作废入口是 `forgetPages`（`:238`，清全部账号仓）与 `clear`（`:243`，两皮肤「我的」页登出成功后调用）。条目身份为 `AccountScopedKey`（`:132`）：`SessionCache` 在屏幕给的键前补一层 uid，读写经 `scoped`（`:163`）每次动态取 `currentUid`（`:148`），由 `bindAccount`（`:159`）在组装期绑定（`ScreenDeps.kt:79`）；换账号回来读不到上一账号的仓，屏幕侧也不必知道当前 uid。键空间 `SessionListKey`（`:37-48`）覆盖首页 feed（`:38`）、首页搜索（`:39`）、分类与分类标签（`browseTagCacheKey` `:51`；`BrowsePageKey` `:60-65`）与独立搜索页的四元组（`Search` `:42-47`）。缓存命中即用快照铺首帧、**不补拉**：首拉只在 `state !is ListLoadState.Loaded` 时触发（例 `ui/SearchScreen.kt:129`、`ui/coui/page/CouiSearchScreen.kt:168-173`），刷新靠下拉刷新或改筛选条件；COUI 皮肤无下拉刷新容器（见附录 B），命中后须改排序/时间或换查询词才会重取。
- 失败落点只有一条入口 `applyFailure`（`:180`）：`hasContent = pending.items.isNotEmpty() && (!replace || pulling || keepOnFailure)`（`:187`），有内容时保留内容并按请求类型分派页脚标记（`:189-193`）——刷新或跳页失败只 `copy(loadingMore = false)`，**禁止**置 `loadMoreFailed`（否则页脚误报「加载失败，点击重试」，且 `PagedFeed.shouldLoadMore`（`core/src/main/kotlin/com/jm3/core/paging/PagedFeed.kt:83-84`）会让触底翻页停摆）；只有加载更多失败才 `failLoadMore()`。无内容才整屏 `ListLoadState.Failed`（`:194-205`，`sessionExpired` 取自 `planLoad` 的 `Expired` 相位）。`goToPage` 传 `keepOnFailure = true`（`:99`），因为用户点的是「第 N 页」而不是整屏重载。现有 17 个列表屏共 24 个实例（`SessionListLoader(` 调用点，例 `ui/HomeScreen.kt:150`、`:168`、`ui/BrowseScreen.kt:144`、`:662`、`ui/SearchScreen.kt:104`、`ui/FavoritesScreen.kt:119`、`:156`、`ui/ForumScreen.kt:128`、`ui/TasksScreen.kt:165`、`ui/TrackingScreen.kt:84`、`ui/WatchHistoryScreen.kt:72`、`ui/coui/page/CouiHomeScreen.kt:210`、`:225`、`ui/coui/page/CouiBrowseScreen.kt:206`、`:227`、`:250`、`ui/coui/page/CouiSearchScreen.kt:150`、`ui/coui/page/CouiFavoritesScreen.kt:161`、`ui/coui/page/CouiForumScreen.kt:161`、`ui/coui/page/CouiLibraryScreen.kt:138`、`:156`、`ui/coui/page/CouiTasksScreen.kt:122`、`ui/coui/page/CouiTrackingScreen.kt:111`、`ui/coui/page/CouiWatchHistoryScreen.kt:100`），其中 9 处接了缓存（`ui/HomeScreen.kt:163`、`:189`、`ui/BrowseScreen.kt:157`、`:677`、`ui/SearchScreen.kt:117`、`ui/coui/page/CouiHomeScreen.kt:218`、`ui/coui/page/CouiBrowseScreen.kt:214`、`:265`、`ui/coui/page/CouiSearchScreen.kt:158`）。禁止屏幕再自持代次令牌或自写 `beginLoadMore` / 失败分派顺序。
- 屏幕禁止直接引用 `LOGIN_EXPIRED_MESSAGE` 或自拼「保留内容 + Snackbar」分支。全仓 `planLoad` / `applyLoadPlan` 出现在 25 个文件（含定义处 `ui/ScreenLoadPlan.kt`；列表类统一落点 `ui/list/SessionListLoader.kt:188`，另例 `ui/LibraryScreens.kt:230`、`ui/ProfileScreen.kt:147`、`ui/coui/page/CouiProfileScreen.kt:196`、`ui/AlbumDetailStateHolder.kt:119`）。

---

## 二、网络与协议边界

### 2.1 网关接口与反射成员表

- 网络唯一入口为 `Jm3Gateway`（`core/src/main/kotlin/com/jm3/core/Jm3Gateway.kt:82`），继承 `SessionAuth`（`SessionAuth.kt:14`）、`PhotoSource`（`download/PhotoSource.kt:14`）、`DomainRoutes`（`Jm3Gateway.kt:54`）与 `ImageAddressing`（`:74`）。所有方法均为阻塞调用，须运行在 IO 调度器上。
- `AccountManager` 仅依赖 `SessionAuth` 的 6 个鉴权方法；下载取页组件仅依赖 `PhotoSource` 的 `photo`（`:17`）与 `imageBytes`（`:20`）。被测方只应依赖它实际用到的那一面。
- 测试替身不得为图省事伪造整张网关：`:core` 侧 `FakeSessionAuth`（`core/src/test/kotlin/com/jm3/core/FakeSessionAuth.kt:15`）只实现 6 个方法，未配置的 `login` 直接 `error(...)`（`:25-27`）；`:app` 侧确需整张网关时用 `FakeGateway`（`app/src/test/kotlin/com/jm3/app/miuix/data/FakeGateway.kt:50`），62 个业务方法一律经 `unexpected`（`:60`）失败，只许 `close` 生效。禁止用反射代理按方法名字符串分派（改接口在编译期无反应），也禁止替身返回兜底假数据让误调静默通过。
- 协议库（`io.github.jukomu.jmcomic`）只允许出现在 `:core`，通过 `api(libs.jmcomic.api)`（`core/build.gradle.kts:18`）暴露。禁止在 `:app` 内直接实例化底层协议客户端。
- 反射成员表唯一登记处为 `LibraryReflection`（`core/src/main/kotlin/com/jm3/core/LibraryReflection.kt:35`）：成员名、字段/方法形状与所属类集中在 `members`（`:88-99`）共 10 条，其中 9 条属 `AbstractJmClient`（`:39`）、1 条属 `JmApiClient`（`:40`）。新增或删除成员只能改这张表，并同步 `LibraryReflectionTest`（`core/src/test/kotlin/com/jm3/core/LibraryReflectionTest.kt:31`、`:47-48`、`:52-54`）。
- 解析与降级也收在同一处：`resolve`（`:102`）、`fieldOf`（`:108`）、`methodOf`（`:113`）、`member`（`:122`）、`invokeOrThrow`（`:129`，`InvocationTargetException` 原样上抛库内异常）、`invokeOrNull`（`:146`）。调用方只持成员句柄：`ReflectiveLibraryBridge`（`core/src/main/kotlin/com/jm3/core/LibraryBridge.kt:43`，读取点 `:47`、`:71`、`:82-83`、`:88-89`、`:96`、`:107`、`:113-114`、`:117-119`）与 `CustomDomainPool`（`:29`、`:33`、`:40`、`:42`），两者不得自行解析成员。
- 反射成员缺失时须按契约降级，并由 `member` 经 `Jm3Log.warn` 记录成员名与异常类名（`LibraryReflection.kt:122`），禁止打印 Cookie 或密码值。

### 2.2 线路切换与域名来源

- 网关实例切换走 `ApiDomain.gatewayOverride`（`data/ApiDomain.kt:51`，双检读写在 `:59`、`:88`、`:92`）与 `GatewayFactory`（`core/src/main/kotlin/com/jm3/core/GatewayFactory.kt:17`）。域名清洗一律经 `Domains`（`core/src/main/kotlin/com/jm3/core/settings/Domains.kt:6`），禁止在屏幕硬编码 URL。
- 线路可观测面收在 `DomainRoutes`（`Jm3Gateway.kt:54`）：只暴露 `usedDomain`（`:56`）、`domainsReady`（`:59`）、`domainLatency`（`:62`）与 `reprobeDomains`（`:65`）；网关侧四个 override 只做转发（`:513-519`），生产实现 `LibraryDomainControls`（`:641-662`）直接委托库客户端。屏幕经 `ScreenDeps.routes`（`ScreenDeps.kt:81-82`）读取，现有消费点为 `ui/SettingsScreen.kt:90`、`ui/coui/page/CouiSettingsScreen.kt:150`、`ui/TasksScreen.kt:172`、`ui/coui/page/CouiTasksScreen.kt:135` 与 `ui/CheckInLoadPolicy.kt:457`；禁止为读线路信息而持有整张网关。图片域名与封面地址另走 `ImageAddressing`（`Jm3Gateway.kt:74`，`coverUrl` `:76`、`imageHost` `:79`），屏幕经 `ScreenDeps.images`（`ScreenDeps.kt:85-86`）读取，消费点为 `ui/ShelfScreen.kt:76`、`ui/TasksScreen.kt:170`、`ui/ReaderScreen.kt:309`、`ui/coui/page/CouiShelfScreen.kt:104`、`ui/coui/page/CouiTasksScreen.kt:131` 与 `ui/coui/page/CouiReaderScreen.kt:339`；禁止只为一个封面地址而持有整张网关。
- 启动期域名仅来自自定义线路与上次刷新落盘的 `DomainPoolCache`。官方线路表在 `DomainTable.kt` 运行时拉取解密；仅当两者皆空（如首次安装）时回退至库内初始化链并在完成后落盘。

### 2.3 图片缓存与超采样链路

- 阅读器图片统一经 `ReaderFetcherFactory`（`ui/ReaderImages.kt:73`）获取，缓存键由 `ReaderKeyer.keyOf`（`:42`）统一生成（格式 `jm3-reader/<photoId>/<index>`），禁止另拼前缀。
- 字节流必须先经 `imageLoader.diskCache.openSnapshot`（`:92`）复用；未命中再读取本地下载文件或网关字节，经 `openEditor`（`:116`）与 `commitAndOpenSnapshot`（`:124`）写入磁盘后返回 `ImageSource`。写盘失败须调用 `editor.abort()`（`:141`）并抛出异常。
- **关键约束**：禁止直接将内存 `okio.Buffer` 作为常规路径返回。`telephoto` 分块超采样仅识别 `FileImageSource` 携带的 `diskCacheKey`，直接返回内存流会导致放大发糊。
- `ImageLoader` 由 `acquireReaderImageLoader`（`:208`）持有为进程级共享实例，配置 `reader_images` 磁盘缓存（`:252`，占可用空间 2% 见 `:253`）。重建键 `ReaderLoaderKey`（`:184`）仅包含网关与下载存储，鉴权通道经 `ReaderAuthChannel`（`:197`）在 `SideEffect`（`:237`）动态注入。页面销毁时禁止 `shutdown()`，防止换章重叠时误关新屏缓存。

### 2.4 分页与静默失败红线

- 分页统一采用按 ID 去重与 1 基页码游标。页码换算收口于 `PageNumbers`（`core/src/main/kotlin/com/jm3/core/download/PageNumbers.kt:12`），翻页计算收口于 `PagingBehavior`（`core/src/main/kotlin/com/jm3/core/paging/PagingBehavior.kt`），切片与游标状态机收口于 `PagedFeed`（同包）。
- 网络失败禁止静默清零有效状态。例如 `UnreadBadge`（`core/src/main/kotlin/com/jm3/core/session/UnreadBadge.kt:17`）在请求失败时保留上次计数，且降级理由必须写明于代码注释中。

---

## 三、错误处理与协程规范

1. **禁止吞掉 `CancellationException`**：参考 `DownloadQueue.kt:156-158` 与 `:220-222`。挂起调用收成 `Result` 时一律使用 `runCatchingSuspending`（`core/src/main/kotlin/com/jm3/core/Cancellation.kt:11`），禁止直接使用 `runCatching { suspendCall() }`。
2. **单任务失败隔离**：下载 worker 捕获普通异常后仅标记当前任务失败，禁止因单个任务异常中断整个队列循环（`DownloadQueue.kt:159`）。
3. **状态机硬断言**：非法状态转移使用 `check` 显式抛出，例如 `DownloadTask.kt:48` 的 `check(status.canTransitionTo(next))`，禁止以条件跳过掩盖状态错乱。
4. **用户错误可重试**：全屏错误态统一复用 `ErrorScreen`（`ui/Common.kt:308`），列表加载脚部统一复用 `LoadMoreFooter`（`:418`）。COUI 皮肤对应复用 `CouiErrorGroup`（`ui/coui/component/CouiAppComponents.kt:872`）与 `CouiLoadMoreFooter`（`:904`）。禁止屏幕自造错误布局。
5. **跟手动画取消**：阅读器翻页与复位动画统一调用 `springAnimateToPage`（两皮肤共用 `reader/ReaderActions.kt:41` 的手动翻页与 `:64` 的跳页编排），避免另起协程争夺滚动状态；分页器目标页夹取统一经 `clampPagerTarget`（`reader/ReaderPagerTarget.kt:4-5`）。
6. **文案与日志事实性**：日志只输出任务标识、文件路径或异常类型，不夹带私密数据；用户提示统一使用简体中文与全角标点。

---

## 四、构建、依赖与交付物

1. **单一版本真源**：版本号集中定义于 `gradle/libs.versions.toml`。模块 `build.gradle.kts` 内禁止出现字面版本号。新增依赖须同步核对许可证与 R8 规则。
2. **单向模块依赖**：仅允许 `:app` 依赖 `:core`（`app/build.gradle.kts:82`），禁止反向依赖。`:core` 保持 Java 17（`core/build.gradle.kts:13-14`），`:app` 使用 Java 21（`app/build.gradle.kts:66-67`）。
3. **SDK 坐标与传输安全矩阵**：当前固定 `compileSdkVersion("android-37.0")`（`app/build.gradle.kts:21`）、`minSdk = 31`（`:26`）、`targetSdk = 36`（`:27`）。上述下限受 `COUI-Kit 0.0.1-alpha10` 元数据强制约束，调整前必须先核对依赖矩阵。`AndroidManifest.xml:25` 声明 `android:usesCleartextTraffic="false"` 禁用明文 HTTP 流量，应用内更新下载拒绝非 HTTPS 与 3xx 降级重定向，并通过 `data_extraction_rules.xml`（`AndroidManifest.xml:23`）排除敏感 DataStore 备份迁移。
4. **Release 混淆与压缩**：`release` 构建强制开启 `isMinifyEnabled` 与 `isShrinkResources`（`app/build.gradle.kts:45-46`），规则维护于 `app/proguard-rules.pro`。新增反射式依赖必须实测 Release 包。
5. **签名安全边界**：Release 签名从 `local.properties` 读取四键，缺失时自动回退至调试签名（`app/build.gradle.kts:12-17`、`:49`）。密钥、口令与 Cookie 严禁入库。
6. **Gradle 执行纪律**：Windows 固定使用 `\gradlew.bat`，禁止传入 `--offline` 参数。同一工作树同时只允许运行一个 Gradle 构建进程，并行任务须串行化构建。
7. **AGP 联动开关**：`gradle.properties:7`（`android.builtInKotlin=false`）与 `:9`（`android.newDsl=false`）在升级 AGP 或 KGP 时须成对检查。
8. **仓库入库红线**：`AGENTS.md`、`docs/`、`implementation_plan.md`、`research/`、`.scratch/`、`.trae/` 与 `.tools/` 已在 `.gitignore` 中忽略。内部调研、过程台账与本地工具产物严禁提交至 Git。

---

## 五、界面组件与阅读器内核

### 5.1 双皮肤组件边界

- 皮肤组件严格按界面风格分工：HyperOS 使用 `Miuix`，ColorOS 使用 `COUI-Kit`（`io.github.itoseo:coui:0.0.1-alpha10`，`app/build.gradle.kts:108`，入口 `ui/coui/theme/CouiTheme.kt`）。禁止跨皮肤混用页面骨架与卡片组件。
- COUI 页面仅允许从 Miuix 借用图标组合件与 `SnackbarHostState` 等库内缺件，禁止在全仓引入 `androidx.compose.material3`。
- 两套皮肤的圆角与颜色统一取自 `LocalSkin`（`ui/skin/SkinTokens.kt:201`）：HyperOS 卡片圆角 `16 dp`、控件 `12 dp`（`:63-64`）；ColorOS 卡片圆角 `18 dp`、控件 `14 dp`（`:174-175`）。

### 5.2 公共控件与滚动条

- HyperOS 侧三态与基础组件统一复用 `ui/Common.kt`：`LoadingScreen`（`:294`）、`ErrorScreen`（`:308`）、`EmptyState`（`:368`）、`CenteredEmptyState`（`:388`）、`LoadMoreFooter`（`:418`）、`PagerBar`（`:624`）、`TrackedLazyColumn`（`:512`）、`rememberSelectionHaptic`（`:778`）与 `CoverSkeletonState`（`:873`，配套 `rememberCoverSkeletonState` `:887`、`Modifier.coverSkeleton` `:892`）。
- 长列表一律使用 `TrackedLazyColumn`，由其内部统一承载 `ListScrollBar` 并共享 `contentPadding`，禁止在页面层自拼滚动条。
- COUI 侧统一复用 `CouiAppComponents.kt` 中的 `CouiAppScaffold`（`:194`）、`Modifier.couiPressable`（`:319`）、`CouiLoadingGroup`（`:487`）、`CouiEmptyGroup`（`:502`）、`CouiErrorGroup`（`:872`）与 `CouiLoadMoreFooter`（`:904`）。COUI 页面由 `CouiAppScaffold` 统一留出底栏净空（默认值取自 `couiPageExtraBottomDp`，见 `ui/coui/component/CouiScreenScaffold.kt:75-76`、`:182-183`），禁止页面硬编码底部占位高度。
- 底部净空只有一处推导 `ui/BottomSpacing.kt`：底栏 contentPadding 由 `bottomBarContentPaddingDp`（`:41`；`DOCKED` 退回条高 `:46`、`FLOATING` 加底距与呼吸 `:56`）算出，`ui/glass/BottomBarTokens.kt:71-75`、`:121-125` 直接调用它而不写字面量；COUI 页额外净空、首页 FAB 让位、玻璃首页与创作者页让位分别是 `couiPageExtraBottomDp`（`:75`，只认 `hasFab` / `isImeVisible` 两个开关）、`couiHomeExtraBottomDp`（`:85`）、`glassHomeExtraBottomDp`（`:103`）与 `creatorWorkExtraBottomDp`（`:123`）。旧的 `ui/BottomClearance.kt` 与其测试已整体删除，禁止再引入第二套净空公式。
- 跨屏共享交互件同类收口：复制文本统一走 `copyTextToClipboard`（`ui/ClipboardActions.kt:28`，调用点为 `ui/AlbumDetailScreen.kt:218`、`ui/coui/page/CouiAlbumDetailScreen.kt:310`、`ui/coui/page/CouiUpdateScreen.kt:261`），成功与失败文案由 `clipboardCopyMessage`（`:17`）按 `writeClipboardText`（`:43`）的真实结果给出，禁止各屏先弹成功再写；资料页草稿与脏键集合收进 `ProfileDraftState`（`ui/ProfileDraftState.kt:18`）与 `ProfileDraftStateSaver`（`:77`），两皮肤各持一份（`ui/ProfileScreen.kt:94`、`ui/coui/page/CouiProfileScreen.kt:153`），`StatePersistence.kt` 的 `StringMapSaver` 已随旧实现删除。
- Miuix `0.9.4` 实测基线：`CardDefaults.InsideMargin` 默认为 `0 dp`，弹窗水平内边距为 `24 dp`，调用方勿叠加重复水平边距；圆角裁切与描边优先使用 `squircleClip`、`squircleBorder` 与 `squircleSurface`。

### 5.3 阅读器手势与解码管线

- **手势状态机**（`reader/ReaderEdgeDrag.kt`、`ui/ReaderZoomLogic.kt`）：
  - 缩放态仅由当前页通过 `isPageZoomed`（`ReaderZoomLogic.kt:13`）回写，翻页后自动复位。
  - 横向 `HorizontalPager` 挂载 `.pagerGestureOverride(pagerState, enabled = !pageZoomed)`（`ReaderScreen.kt:908`、`ui/coui/page/CouiReaderScreen.kt:940`），确保放大平移时不触发翻页。
  - 放大贴边翻页由 `collectHorizontalEdgeDrag`（`reader/ReaderEdgeDrag.kt:20`）在 `PointerEventPass.Initial`（`:29`）采集原始位移，多指直接退出（`:31`），交由 `EdgeDragTracker`（`ReaderZoomLogic.kt:58`）与 `decideEdgePageTurn`（`:23`）判定；松手后等 `EDGE_CONFIRM_DELAY_MS` 复核内容静止（`:38-46`），通过再经 `ReaderActions.turnPage` 弹性翻页。
  - 两皮肤逐字重复的阅读器编排收在 `reader` 包：`ReaderActions`（`reader/ReaderActions.kt:20`，`turnPage` `:37`、`goToPage` `:46`、`persistProgress` `:81`）、双页同框单缩放层的 `ReaderSpreadPage`（`reader/ReaderSpreadPage.kt:35`，`rememberZoomableState` `:44`、缩放态判定 `:46`、翻走复位 `:50-52`、双页 `Row` `:74-79`）、页码夹取 `ReaderPagerTarget.kt:4-5`。两屏只剩参数接线（`ui/ReaderScreen.kt:541`、`ui/coui/page/CouiReaderScreen.kt:577`），禁止把编排再抄回屏幕。
  - 纯常量（`ZOOMED_THRESHOLD = 0.02f`、`EDGE_PAGE_DISTANCE_RATIO = 0.18f`、`CONTENT_PIN_TOLERANCE_PX = 24f`、`EDGE_CONFIRM_DELAY_MS = 120L`、`EDGE_CONFIRM_MOVE_PX = 8f`）集中于 `ReaderZoomLogic.kt:4-49`，禁止散落回 Composable。
- **比例与解码**（`ui/ReaderPageLogic.kt`、`ui/ReaderImages.kt`）：
  - 布局比例与解码请求尺寸由 `readerPageFrame`（`ReaderPageLogic.kt:108`）同源算出，展示与预取共用同一换算，确保内存缓存键一致。
  - 像素预算派生自 `ReaderImageQuality.STANDARD.maxDecodePixels`（4,000,000 px，`ReaderPreferences.kt:6`），单边上限 `MAX_PAGE_DECODE_EDGE_PX = 8192`（`ReaderPageLogic.kt:33`）。
  - 统一通过 `configureReaderDefaults()`（`ReaderImages.kt:266`）指定 `Bitmap.Config.RGB_565` 解码，4 MP 标准档单页位图内存约 `8 MB`（较 `ARGB_8888` 降低 50%）。
  - 比例回填走 `readerPageBackfill`（`ReaderPageLogic.kt:155`）：`pageRatios` 无条件更新，`decodeRatios` 仅在 `isBoxLimited`（`:126`）为真时回填。
  - 纵向长图模式不做就地缩放，双击打开全屏缩放覆盖层（`zoomOverlayIndex`，`ReaderScreen.kt:426`；COUI 侧 `ui/coui/page/CouiReaderScreen.kt:483`）；章节种子仅接受不窄于 `MIN_SEED_ASPECT_RATIO = 0.5f`（`ReaderPageLogic.kt:40`）的常规页比例。
- **页码与恢复真源**：页码、跳页冻结与可见页回写统一收口于 `ReaderPagePlan`（`core/src/main/kotlin/com/jm3/core/reader/ReaderPagePlan.kt:17`、`jumpTo` `:53`、`settleJump` `:65`、`visiblePage` `:74`）。屏幕侧会话状态收口于 `ReaderSession`（`app/src/main/kotlin/com/jm3/app/miuix/reader/ReaderSession.kt:52`，两皮肤共用：`ui/ReaderScreen.kt:419`、`ui/coui/page/CouiReaderScreen.kt:476`）：
  - 恢复时序为三态 `ReaderRestorePhase`（`ReaderSession.kt:15`，`AWAITING_LAYOUT` `:17`、`RESTORING` `:20`、`SETTLED` `:23`），`restore`（`:136`）先读旧进度、再置 `restored` 并开写盘闸门，最后解出目标页 `ReaderRestoreOutcome`（`:32`）。
  - 跳页与预取各自只留一个入口：`requestJump`（`:86`）/ `onJumpSettled`（`:94`）与 `onVisibleIndex`（`:103`）/ `resetPrefetchLedger`（`:114`）。页码语义沿用 `:core` 纯函数，禁止在屏幕复制换算。

### 5.4 动效、共享元素与大屏窗口

- **封面骨架与微光**：骨架屏仅挂载于 `AlbumCoverImage`（`ui/AlbumHero.kt:385`），外部卡片不再重复套用骨架修饰符。
- **edge-to-edge 与音量键**：`MainActivity.kt:27` 开启沉浸式，`MainActivity.kt:83-85` 将音量键分发给 `ReaderVolumeKeys`（`ReaderScreen.kt:146`）。长图缩放覆盖层打开时须消费并返回 `false` 屏蔽翻页，销毁时须校验回调归属后再置空。
- **共享元素飞行**：列表侧挂 `.albumHeroCover`（`AlbumHero.kt:538`）、详情侧挂 `.albumHeroHeader`（`:642`），圆角读取 `LocalSkin.current.shapes.cardCorner`，行卡缩略图 `HERO_THUMB_CORNER_DP = 8f`（`:425`）、角标 `HERO_BADGE_CORNER_DP = 6f`（`:428`）。详情路由转场固定为纯 alpha 渐变（`albumDetailTransitionFor`，`Jm3App.kt:370`），禁止改为水平位移动画。
- **窗口断点与大屏策略**：窗口尺寸统一读取 `rememberWindowDpSize()`（`ui/AdaptiveLayout.kt:76`），整数断点使用 `Dp.toLayoutDpInt()`（`:83`）向下取整，禁止使用 `Configuration.screenWidthDp`。大屏布局阈值集中于 `WideLayoutPolicy`（`core/src/main/kotlin/com/jm3/core/layout/WideLayoutPolicy.kt:22`）：中屏 `600 dp`（`:25`）、大屏 `840 dp`（`:28`），详情双栏条件（`:139-141`）、侧栏宽高双条件（`:144-145`）、行卡两列与网格列数均从此处派生。

### 5.5 玻璃表面与运行时能力门槛

- **能力门槛唯一**：运行时着色器是否可用只问 `isRuntimeShaderSupported`（`app/src/main/kotlin/com/jm3/app/miuix/ui/effect/ShaderCapability.kt:44`，带 `@ChecksSdkIntAtLeast` `:43`），禁止再 import `miuix` 库内同名函数或另写 `SDK_INT` 判断。现有调用点收敛在 `ui/effect/RuntimeShaderCompat.kt:21`、`:35`、`:44`、`ui/effect/BgEffectBackground.kt:37`、`ui/glass/BackdropSupport.kt:21`、`ui/glass/FloatingBottomBar.kt:224`、`:236`、`:447`、`ui/glass/GlassScaffold.kt:135`、`ui/glass/GlassSurface.kt:250`、`ui/glass/Lens.kt:26`、`ui/glass/InteractiveHighlight.kt:55`。纯查询形态 `ShaderCapability`（`:15`）与 `resolveShaderCapability`（`:22`）供需要把「为什么不可用」展示出来的场景使用（`ShaderUnavailableReason` `:9`）。
- **顶栏玻璃计划唯一**：顶栏磨砂由 `GlassScaffold` 内部渐进模糊承担，不画 `glassSurface`。形态只解析一次：`resolveTopBarGlass`（`ui/glass/TopBarGlass.kt:35`）返回 `TopBarGlass`（`:20`，字段 `:21-24`），`GlassScaffold.kt:132-140` 以它决定模糊层与兜底色，绘制路径不再回查角色表。顶栏 tint 常量 `TOP_BAR_BLUR_TINT_ALPHA = 0.3f`（`TopBarGlass.kt:28`）。
- **背景采样源**：需要真实内容作模糊源的页面统一调用 `rememberBlurBackdrop()`（`ui/glass/BackdropSupport.kt:20`）并挂 `Modifier.layerBackdropSource`（`:30`），现有页面为 `ui/MainScreen.kt:159`（`:228` 挂采样源）、`ui/LibraryScreens.kt:1011`（创作者页头图，`:1015` 挂采样源，`progressiveTextureBlur` 见 `:1023`）、`ui/ReaderScreen.kt:463`（悬浮工具栏，采样源先垫主题底色避免透明区黑边）与 `GlassScaffold.kt:129`（`:198` 挂采样源）。禁止页面各自用 `graphicsLayer` 手搭采样层。

---

## 六、Agent 工作流与边界测试

### 6.1 改动权限与 Issue 规范

- **拆单四要素**：需求描述、验收标准、架构约束（允许修改范围与复用件）、测试清单。仓库当前无 `.github` 目录，本地规范以本文与 `AGENTS.md` 为准。
- **Agent 可直接修改**：`:app` 屏幕与组件、`:core` 纯函数与单测、文案与样式微调、本文规则与行号同步更新。
- **必须先请示用户**：修改 `Jm3Gateway` / `SessionAuth` 接口签名、改动 `DownloadTask` 状态机、调整模块依赖方向、修改签名与 `proguard-rules.pro` 放宽规则，以及新增对「禁止」条款的例外。

### 6.2 完成定义与 TDD 流程

每次提交前必须依次完成以下步骤（缺一不可）：

1. **红绿重构**：业务逻辑、数据处理与缺陷修复须先写测试，跑出真实业务断言失败（红灯）后再编写最小实现转绿，绿灯后严禁改弱断言。纯 UI 样式调整可豁免先行测试，但其底层状态计算仍需单测覆盖。
2. **构建验证**：执行 `\gradlew.bat :app:assembleDebug`（纯 `:core` 改动至少执行 `:core:compileKotlin`）。若因并发锁等客观原因未跑，汇报时须如实说明「未验证」。
3. **测试通过**：执行 `\gradlew.bat :core:test :app:testDebugUnitTest`。
4. **日志与提交**：在 `CHANGELOG.md` 的 `## [未发布]` 下按「新增 / 修复 / 优化」补充用户可读条目（不泄露内部类名与哈希）；使用纯文本中文 Conventional Commits 逐个逻辑变更提交，严禁包含 Emoji。
5. **四项汇报**：汇报修改内容、修改原因、验证命令与结果、是否触碰既有硬约束。

### 6.3 全仓测试规模

测试基线以 `build/test-results/**/TEST-*.xml` 为准（最近一次全量执行 `:core:test` 与 `:app:testDebugUnitTest`）：

- **`:core`**：78 个测试类 / 735 个测试用例（位于 `core/src/test/kotlin/com/jm3/core/`；同目录另有一个不含 `@Test` 的共用替身 `FakeSessionAuth.kt`）。
- **`:app`**：104 个测试类 / 734 个测试用例（位于 `app/src/test/kotlin/com/jm3/app/miuix/`；同目录另有一个不含 `@Test` 的网关替身 `FakeGateway.kt`）。
- **全仓合计**：182 个测试类 / 1,469 个测试用例，全部通过，0 失败、0 跳过（182 个 `TEST-*.xml` 与 1,469 个 `@Test` 逐一对齐）。

典型纯逻辑边界测试规模：`AccountManagerTest`（39）、`AlbumHeroLogicTest`（31）、`ReaderPageLogicTest`（31）、`CheckInLoadPolicyTest`（31）、`MappersTest`（29）、`ReaderProgressCodecTest`（27）、`AlbumHeroHostTest`（26）、`CouiWorkCardLayoutTest`（26）、`PagedFeedTest`（22）、`ReaderZoomLogicTest`（22）、`DownloadQueueTest`（22）、`PagingBehaviorTest`（21）、`CouiFeedPolicyTest`（21）、`SessionListLoaderTest`（22）、`SessionCacheTest`（15）。

### 6.4 核心边界测试清单

新增或修改以下能力时，对应测试类必须同步覆盖并保持绿灯：

- **下载状态机与队列**：`DownloadTask` 非法状态转移抛出异常 → `DownloadTaskTest`；同话去重、失败重入、并发上限 2 与取消不耗重试 → `DownloadQueueTest`（22） / `DownloadContractTest`；断点恢复与台账分离写 → `DownloadRecoveryTest` / `DownloadLedgerTest` / `DownloadManagerPersistenceTest`。
- **下载文件与前台策略**：页文件原子替换、路径校验且失败不留 `.tmp` → `FileDownloadStoreTest`（17）；窄接口取页契约 → `GatewaySourcesTest`；页码清单编解码 → `DownloadPagesCodecTest`；通知文案与条目呈现 → `DownloadPresentationTest` / `DownloadStatusSemanticsTest` / `DownloadRowTest` / `DownloadRowTextTest`；前台策略与退避 → `ForegroundPolicyTest` / `ForegroundRunnerTest`。
- **分页、封面与协议映射**：1 基页码换算 → `PageNumbersTest`；翻页计算 → `PagingBehaviorTest`（21）；去重与触底距离 → `PagedFeedTest`（22）、`SearchPagesTest`、`CommentPagesTest`、`SerializationPageTest`；分割还原 → `ScrambleTest`；封面三态与缓存键互逆 → `CoverResolverTest`（13）、`CoverLocatorTest`；域名清洗与线路切换缓存 → `DomainsTest`、`CustomDomainPoolTest`、`DomainPoolCacheTest`、`ApiDomainTest`、`GatewayFactoryTest`；章节分组与标签 → `ChapterGroupsTest`、`ChapterLabelsTest`、`NotificationBodyTest`。
- **会话、凭据与更新**：会话编解码 → `SessionCodecTest`；加密凭据落盘、坏数据自清与 Keystore 密钥重建 → `CredentialStorageTest`、`CredentialStoreTest`；取消异常重抛 → `CancellationTest`；反射成员表、解析与降级 → `LibraryReflectionTest`（9）、`LibrarySessionReflectionTest`、`LibraryBridgeRoutingTest`；静默重登、并发竞态与 3 次失败清凭据 → `AccountManagerTest`（39）；启动闸门与失效通知 → `AuthCallTest`；未读角标保留与纪元保护 → `UnreadBadgeTest`；版本等价、更新下载校验与设置归一 → `VersionTextTest`、`UpdateCheckerTest`、`UpdateDownloaderTest`、`ReleaseJsonTest`、`UpdateSettingsTest`、`SettingsRepositoryTest`、`SettingsNormalizationTest`、`SettingsKeyStoreAdapterTest`。
- **列表加载编排**：首屏、刷新、触底、跳页四类请求的代次丢弃与失败落点（有内容保留 + 提示、无内容整屏、`goToPage` 保留旧页、刷新/跳页失败不占用页脚重试位） → `SessionListLoaderTest`（22）；`ListStateCache` 只收已落定快照、按账号分仓与 `forgetPages` / `clear` 作废语义 → `SessionCacheTest`（15）。
- **阅读器、转场、布局与皮肤**：缩放与边缘翻页 → `ReaderZoomLogicTest`（22）；页框比例与解码上限 → `ReaderPageLogicTest`（31）、`ReaderPageBackfillTest`（8）；页码计划与跳页冻结 → `ReaderPagePlanTest`（12）；阅读器会话恢复时序（`AWAITING_LAYOUT` → `RESTORING` → `SETTLED`）与写盘闸门 → `ReaderSessionTest`（13）；进度编解码 → `ReaderProgressCodecTest`（27）；预取步进 → `ReaderPrefetchTest`；共享元素几何、宿主状态机与缓存尺寸 → `AlbumHeroLogicTest`（31）、`AlbumHeroHostTest`（26）、`AlbumHeroStateMachineTest`（13）、`AlbumHeroCoverSizeTest`（4）；详情状态容器 → `AlbumDetailStateHolderTest`（17）；加载结局翻译与两钩子分支 → `ScreenLoadPlanTest`（6）、`ListOutcomeTest`、`NovelActionNoticeTest`；结局翻译与签到策略 → `CheckInLoadPolicyTest`（31）；玻璃表面、能力门槛与顶栏分档 → `GlassSurfaceTest`、`InteractiveHighlightTest`、`ShaderCapabilityTest`（3）、`TopBarGlassTest`（5）；COUI 组件、字阶、通知滑动与阅读器逻辑 → `CouiPageStateTest`、`CouiTypographyTest`、`CouiForumRepliesTest`、`CouiHistoryGroupingTest`、`CouiNotificationsSwipeTest`、`CouiNovelDetailChaptersTest`、`CouiReaderLogicTest`（15）；大屏与底栏导航策略 → `WideLayoutPolicyTest`（15）、`BottomBarPreferencesTest`（9）、`WideScreenNavigationTest`（8）；双页同框页码夹取 → `ReaderPagerTargetTest`（4）；底部净空推导 → `BottomSpacingTest`（6）；复制结果文案 → `ClipboardActionsTest`（3）；资料草稿与脏键恢复 → `ProfileDraftStateTest`（5）。`reader/ReaderActions.kt`、`reader/ReaderSpreadPage.kt` 与 `reader/ReaderEdgeDrag.kt` 目前无对应测试类（缺口见附录 B）。

---

## 附录 A：已定决策与理由

后续改动若与以下既定决策冲突，必须先更新决策再修改代码，禁止暗中绕过：

1. **阅读器缩放选用 `telephoto`**：使用 `me.saket.telephoto:zoomable-image-coil3:0.19.0`（`gradle/libs.versions.toml:16`、`:30`），以同时获得分块超采样与缩放感知解码，避免放大模糊。
2. **手势让渡显式关闭翻页拦截**：Miuix `PagerSwipeNode` 在 `Initial` 阶段判消费，而 `telephoto` 在 `Main` 阶段消费。因此横向 `HorizontalPager` 恒挂 `.pagerGestureOverride(pagerState, enabled = !pageZoomed)`（`ReaderScreen.kt:908`、`CouiReaderScreen.kt:940`），防止平移与翻页同时触发。
3. **放大贴边翻页采用 `Initial` 旁路**：`telephoto` 越过触摸阈值后会消费全部位移，故在 `ReaderPage` 的 `PointerEventPass.Initial` 阶段采集原始位移并减去内容偏移，松手 `120 ms` 后复核内容静止方判定翻页。
4. **长图模式禁止就地缩放**：层内缩放会与纵向列表滚动冲突并裁切内容，故双击弹出全屏缩放覆盖层，单击或返回键退出。页框跟随页内真实比例，避免 3:4 强制裁切导致条幅拉伸模糊。
5. **阅读器图片强制走磁盘快照**：`telephoto` 分块超采样依赖 `FileImageSource` 的 `diskCacheKey`。禁止将内存 `okio.Buffer` 作为常规路径返回；缓存键统一由 `ReaderKeyer.keyOf` 生成。
6. **下载前台服务只管前台态与通知**：队列调度留在 `DownloadQueue`，生命周期决策由纯类 `ForegroundPolicy` 产出，防抖与拉起失败退避由 `ForegroundRunner` 驱动，不引入 `WorkManager`。
7. **通知节流 `300 ms` 与配额退避**：常规进度按 `300 ms` 节流，终态立即刷新。`dataSync` 6 小时配额耗尽后暂停在途任务并留存可见通知，不做保活；系统拒绝拉起前台服务后按 `5 s` 退避重试一次。
8. **许可证隔离规范**：仅允许复制 `Apache-2.0`、`MIT` 或 `ISC` 源码并保留 SPDX 标注；`GPL-3.0`/`AGPL-3.0` 项目仅采思路、自行重写。仓库主体为 `MIT`，其中 `app/src/main/kotlin/com/jm3/app/miuix/ui/` 下共 64 个文件带 `Apache-2.0` 头（含 `glass/` 15 个、`coui/` 34 个、`ui/` 直属 12 个、`effect/` 2 个、`route/` 1 个），`CouiIcons.kt` 带 `ISC` 头，`COUI-Kit` 按 `MPL-2.0` 依赖接入（详情见 `NOTICE`）。
9. **提升型持续通知按 API 36.1 判定**：`setRequestPromotedOngoing` 仅存在于 Android 16.1（API 36.1），必须先判 `SDK_INT >= 36` 再判 `SDK_INT_FULL >= BAKLAVA_1`（`DownloadNotifier.kt:216`、`:228`），避免 Android 16.0 触发 `NoSuchMethodError`。
10. **共享元素采用自绘覆盖层**：因 `miuix-nav 0.9.4` 转场无 `AnimatedVisibilityScope`，采用 `onGloballyPositioned` 登记起止矩形并在 `AlbumHeroOverlay` 插值绘制，使用缩略图地址与 `crossfade(false)`。拿不到起止矩形时退化为普通转场。
11. **静默重登连续失败 3 次清凭据**：`AccountManager` 持久化 `renewFailureCount`，连续 3 次失败后清除已存加密凭据，避免每次冷启动重复无效登录；任一次重登成功立即归零，兼容短暂断网。
12. **`:core` 日志单一出口 `Jm3Log`**：`:core` 不引入 Android 日志依赖，反射成员缺失时统一经 `Jm3Log` 输出成员名与异常类名，`:app` 在初始化时桥接至 `android.util.Log.w`。
13. **会话注入由调用方传入网关实例**：`AccountManager.inject` 直接接收已判定的 `SessionAuth` 实例，防止线路并发切换瞬间将 Cookie 注入废弃网关；启动闸门由 `openStartupGate()` 唯一放行。
14. **网关调用唯一入口 `AuthCall`**：将启动闸门等待、IO 线程切换与单次失效通知收敛在 `AuthCall.call`，消除界面层重复的 401 判断与线程切换样板代码。
15. **屏幕仅认加载结局**：`SessionOutcome` 至界面状态由 `planLoad`（`ScreenLoadPlan.kt:31`）统一翻译，带副作用的落点再经 `applyLoadPlan`（`:71`、`:90`）只写本屏差异；有内容时保内容给提示、无内容时整屏呈现失效或错误，禁止界面直接引用 `LOGIN_EXPIRED_MESSAGE`。
16. **分页状态机统一为 `PagedFeed`**：`PageSlice` 与 `PagedFeed` 负责按 ID 去重、游标推进与触底判定（`PREFETCH_DISTANCE = 3`），翻页算法归入 `PagingBehavior`。
17. **章节分组以首章 ID 为身份**：`ChapterGroups` 按原始正序每 10 章切分一次，倒序展示仅反转组序与组内条目，避免切排序后折叠展开态错位。
18. **下载状态语义枚举与展示分离**：活动/终态/重排判定直接挂于 `DownloadStatus` 枚举，书架行与通知栏展示形状分别由纯函数 `presentRow` 与 `present` 生成。
19. **封面三态模型 `Cover`**：`Cover` 区分 `Ready`、`Unready` 与 `None`（分别对应有址、域名未就绪、无封面），`CoverResolver` 与 `coverCacheKeyText` 互逆校验。列表 DTO 现阶段仍透传可空地址。
20. **反射成员表唯一登记处 `LibraryReflection`**：成员名、所属类、字段/方法形状与参数类型集中为 `members` 表（`LibraryReflection.kt:88-99`），解析、降级与异常包装（`:102`、`:122`、`:129`、`:146`）也在同一处；`ReflectiveLibraryBridge` 与 `CustomDomainPool` 只持句柄，不再各自 `getDeclaredField`。库升级的影响面被压缩成一张 10 行表，单成员缺失只降级不抛异常。
21. **组装根只做装配、屏幕只见窄面**：`Jm3Graph` 仅负责六大域按序初始化，页面全面通过 `ScreenDeps`（`ScreenDeps.kt:64`）单容器获取依赖；读面按关心点切成 `routes`（`:86`）与 `images`（`:90`），偏好读面收窄为 `PagingPreference` / `BottomBarPreference`，写面只经 `select*`，禁止半注入半全局的混合签名。
22. **版本号比对出口唯一化**：版本号规范化、解析与等价判断统一使用 `VersionText`（`core/update/VersionText.kt:11`），设置项去空处理明确命名为 `VersionSettingText`（`SettingsRules.kt:59`）。
23. **下载页落盘原子替换**：`FileDownloadStore` 先写入 `.tmp` 临时文件并加锁执行 `Files.move(..., REPLACE_EXISTING)`，异常时清理 `.tmp`，满足跨平台重下覆盖契约。
24. **复杂屏幕状态进 Holder**：`AlbumDetailStateHolder` 将详情页复杂状态与交互抽离至纯 Kotlin 容器，确保核心界面业务脱离 Compose 运行时直接测试。
25. **玻璃表面统一角色计划表**：除底栏动态按压与录制层外，其余玻璃表面统一经 `glassRoleSpec` 与 `resolveGlassSurface` 按「角色 + 降级档位」计算模糊与遮罩参数。
26. **跨文件换算单一出口**：比例回填（`readerPageBackfill`）、预取步进（`advancePrefetch`）、版本解析与反射成员等规则均保留唯一纯函数出口，避免双写漂移。
27. **偏好写入必须连动作带副作用**：`ScreenDeps.select*`（`:157`、`:169`、`:178`、`:184`）把「比较现值 → 内存态 → 进程作用域落盘」封成一个动作，屏幕只表达意图。分页模式是唯一改变分页语义的入口，故只有 `selectPaginationMode` 额外调 `sessionCache.forgetPages()`（`:161`）；其余三项不得照抄。否则每个设置入口都要重抄这套顺序，漏掉该清的 `forgetPages` 就会让旧分页继续渲染。
28. **加载结局只有一个翻译出口**：`planLoad` + `applyLoadPlan` 承担 `Expired`/`Failed` 分派与 Snackbar 落点（现有 25 个文件消费），屏幕只写差异文案与两处钩子，避免每屏重写「有内容保内容、无内容整屏」的分支。
29. **阅读器会话状态收进 `ReaderSession`**：两套皮肤共用同一状态机（`ReaderScreen.kt:419`、`CouiReaderScreen.kt:476`），恢复时序、跳页冻结、写盘闸门与预取去重只有一份；页码语义仍引用 `:core` 纯函数，不在状态容器里复制换算。
30. **运行时能力与顶栏形态各收一处**：着色器能力只问 `isRuntimeShaderSupported`（`ShaderCapability.kt:44`），顶栏玻璃形态只经 `resolveTopBarGlass`（`TopBarGlass.kt:35`）解析一次；否则同一判断散在六个玻璃文件与绘制分支，降级档位会不一致。
31. **测试替身按被测面收窄、越界即失败**：`:core` 用只实现 6 方法的 `FakeSessionAuth`，`:app` 需要整张网关时用 `FakeGateway`（未配置方法一律 `error`，见 `FakeGateway.kt:60`）。替身不得返回兜底值，也不得用反射代理按方法名分派。
32. **路由目标集中在 `AppRoutes`、屏幕只表达语义**：`AppRoutes`（`AppRoutes.kt:15`）把 25 个语义动作与 `push` / `replaceTop` 两个出口封在一起，屏幕形参收成 `nav: AppRoutes`；否则 40 余个屏幕各自拼 `Route`，改一次路由要扫全仓调用点。语义动作也让「换章是同层替换」这类栈行为只在一处表达（`:55`）。
33. **分页列表的取数编排收进 `SessionListLoader`**：请求代次、在途标记、翻页游标与失败落点只有一份（`SessionListLoader.kt:48`），屏幕只提供 `fetchPage` / `idOf` / `notice` 三个洞。此前两套皮肤十余个列表页各持一份代次令牌与 `beginLoadMore` 顺序，漏一处就会出现旧结果盖新列表或页脚常转。
34. **列表会话缓存只存已落定快照、并按账号分仓**：`ListStateCache`（`SessionCache.kt:112`）只收 `Loaded`，`SessionCache.remember`（`:221-228`）先把 `loadingMore` / `loadMoreFailed` 归一才落缓存，`SessionListLoader` 用 `seededSnapshot`（`:116-120`）把它当首帧，因此缓存不可能把「加载中」或「页脚失败」的瞬时态带到下一次进入；条目身份由 `AccountScopedKey`（`:132`）在屏幕键前补 uid，读写每次动态取当前账号（`scoped` `:163`、`bindAccount` `:159`、绑定点 `ScreenDeps.kt:79`），换账号读不到上一账号的仓；作废入口是 `forgetPages`（`:238`，分页模式变化时清全部账号仓）与 `clear`（`:243`，登出）。
35. **阅读器共性收口到 `reader` 包**：两皮肤逐字重复的翻页/跳页/进度编排、双页同框与边缘拖拽判定分别进 `ReaderActions`、`ReaderSpreadPage`、`ReaderEdgeDrag`，两屏随之瘦身（`ui/ReaderScreen.kt` 1692→1529 行、`ui/coui/page/CouiReaderScreen.kt` 2376→2211 行）；「两皮肤必须一致」的编排留在屏幕里必然漂移，收成模块后一处改动两皮肤同时生效。
36. **底部净空单一推导**：底栏 contentPadding 不再在 `BottomBarTokens` 注释里写 80/108/64，而由 `bottomBarContentPaddingDp`（`BottomSpacing.kt:41`）按条高与形态算出，COUI 页、FAB 与玻璃首页的额外净空也在同一文件；`BottomClearance` 及其测试整体删除，避免两套净空公式各自漂移。

---

## 附录 B：明确暂缓事项与验证缺口

1. **Baseline Profile / macrobenchmark 暂不接入**：缺乏稳定常驻多设备采样矩阵，且仅提供冷启动度量。后续若引入，以独立 `:benchmark` 模块与冷启动耗时作为验收指标。
2. **ktlint / detekt 暂不接入**：全仓历史代码统一格式化会产生巨量无关差异，现阶段以构建检查、单测与人工审查把关。
3. **放大边缘翻页暂不做跟手拖拽**：跟手拖拽需重构 `HorizontalPager` 滚动源，改动面大于当前收益，维持松手判定后弹性翻页方案。
4. **运行验证缺口**：
   - 已真机验证项：已在小米 15（`targetSdk 36`）实测阅读器缩放与边缘翻页、双击整屏缩放覆盖层、`dataSync` 前台服务与进度通知（含提升型通知分支）。
   - 未真机验证项：全仓目前主要依赖编译、181 个测试类（1,465 个用例）的 JVM 单测与 Release 打包（R8 + lintVital）验证。以下路径仍待真机逐项补测：共享元素飞行轨迹与返回落点、封面骨架屏下沉后的转场观感、主界面切换标签后的滚动状态保存、`onTimeout` 6 小时超时回调、窄屏与多折叠窗口下的双栏/列表边界、Android 13+ 通知拒权回退路径、超长条漫真机内存与清晰度实测、Keystore 密钥损毁重建日志路径、真实会话过期后的协议库自动重登链路。
5. **COUI 皮肤暂不实现下拉刷新**：当前 `COUI-Kit` 未提供下拉刷新容器，COUI 页面刷新入口统一由错误态重试与页面操作按钮承担，不在页面层手拼手势容器。
6. **小说列表不做分页**：上游小说列表接口一次返回全量数据且无分页参数，屏幕按全量渲染，不强套 `PagedFeed`。
7. **阅读器共性件暂无单测**：`reader/ReaderActions.kt`、`reader/ReaderSpreadPage.kt` 与 `reader/ReaderEdgeDrag.kt` 由两屏共享，但行为依赖 Compose 运行时（`PagerState` / `LazyListState` / 指针事件），当前无对应测试类；`ReaderPagerTargetTest`（4 个用例）只覆盖纯函数 `clampPagerTarget`。
