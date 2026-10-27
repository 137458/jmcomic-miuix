# Miuix 升级闭环账本（Autonomous Loop Ledger）

> 状态图例：`[ ] Pending` / `[~] In Progress` / `[x] Completed` / `[!] Blocked`
> 基线事实来源：
> - 原版参考：`.tools/apk-web/src/**`（JMComic3 v2.1.12 还原源码）+ `research/original-feature-parity.md`
> - 当前目标：`app/src/main/kotlin/com/jm3/app/miuix/**`、`core/src/main/kotlin/com/jm3/core/**`
> - 库能力：`.tools/jm-src/jmcomic-api|core`（v1.1.12）、`.tools/miuix-src-lead`（Miuix 0.9.4）
> 每模块完成定义：补齐原版功能点 → Miuix 高阶组件替换 → 编译通过（`:app:compileDebugKotlin`）→ 回填本账本。

## 侦测结果

- 原版路径：`E:\project\jm3\.tools\apk-web\src`（APK 内前端源码，唯一事实来源）
- 当前路径：`E:\project\jm3`（单仓库，分支 `main`；`app` + `core` 两模块）
- Miuix 版本：`0.9.4`（`miuix-ui` / `miuix-icons` / `miuix-nav` / `miuix-preference` / `miuix-blur` / `miuix-squircle` 已接入）
- 未接入 Miuix 子模块：`miuix-shader`（`miuix-blur`/`miuix-squircle` 已传递依赖引入）、`miuix-window`

## 模块清单

### [x] M01 基础设施：网关能力扩展 + 设置持久化
- [x] `Jm3Gateway` 新增：连载更新 `serialization(page)`、分类树 `categoriesList()`、评论回复 `replyComment`、任务 `tasks(type,filter)`、个人资料 `userProfile(uid)` / `editUserProfile`、观看记录 `watchHistory(page)` / `deleteWatchHistory`、收藏标签 `tagsFavorite()` / `addFavoriteTags` / `removeFavoriteTags`
- [x] `SettingsRepository` 新增：分页模式（infinite/click）、搜索历史（去重、上限 10、可清除）
- [x] `SettingsStore`（app）实现上述持久化
- [x] `:core:test` 新增映射与契约测试（`LoopCoreTest`：分类树/任务/资料/标签/搜索历史/分页模式）
- 备注：`claimTask` 库标注 @Deprecated 且参数体无文档，暂不接入（避免臆造）；评论投票服务端已停用（@Deprecated），不做。
- 验证：`:core:test` + `:app:compileDebugKotlin` 通过（BUILD SUCCESSFUL）。

### [x] M02 搜索页（SearchScreen / Home 内搜索）
- [x] 搜索历史：DataStore 持久化，Home 搜索框内卡片展示；点击复搜、逐条删除、清空、上限 10、提交即记录
- [x] 高级语法帮助弹层（空格=同时包含、`+`=必须包含、`-`=排除），入口常驻搜索框尾部
- [x] 分页模式联动：独立搜索页与首页搜索均读取 `Jm3Graph.paginationMode`，CLICK 时切换为单页 + 公共 `ResultPagerBar`
- [x] 时间范围筛选：新增 `BrowseTime`（服务端 `TimeOption`）双 TabRow；原版 `y`/`m` 年月参数库 `SearchQuery` 不支持，采用等价的服务端时间过滤（记录为替代决策）
- [x] Miuix 高阶：固定 `SearchBar` + `InputField`、排序/时间双 `TabRow`、公共翻页条 `ResultPagerBar`/`pagedSearchResultItems`
- 验证：`:core:test`（含 `搜索查询_时间范围默认为全部并可按周过滤`）+ `:app:compileDebugKotlin` 通过。

### [x] M03 首页（HomeScreen）
- [x] 多块横滑：连载更新（`serialization(1)` 前 12 条）、热门标签折叠胶囊流（更多/收起）、随机推荐（随便看看）、最新上架
- [x] 点击分页模式（复用公共分页列表与翻页条）
- [x] 搜索历史卡（首屏并行加载；词条点击/删除/清空）
- [x] 回顶 / 随机一部 FAB，滚动后出现并与列表状态联动
- [x] Miuix 高阶：`PullToRefresh`、`FloatingActionButton`、`Card` + `BasicComponent`、`SmallTitle`、`FlowRow` 胶囊标签、`OverlayDialog` 语法帮助（纵向全宽按钮）

### [x] M04 分类页（BrowseScreen）
- [x] 子分类横向胶囊条（`categoriesList`）+ 更多分类弹窗（`FlowRow` 分区 blocks）
- [x] 每周连载更新（顶栏 `Weeks` 图标 → `OverlayBottomSheet`，滚动到底自动合并下一页）
- [x] 热门标签行（前 12）→ 标签检索
- [x] 标签/子分类结果区（分页、错误重试、返回恢复）
- 说明：原版子分类按 `slug` 过滤，库 `BrowseFilter` 未暴露 sub_slug，改用子分类名称做标签检索（结果集近似，记录为替代决策）
- [x] Miuix 高阶：`TabRow` 分段、`FlowRow` 胶囊标签、`OverlayBottomSheet` 更多分类/连载

### [x] M05 详情页（AlbumDetailScreen）
- [x] 收藏夹选择/移动：未收藏点击收藏 → `OverlayBottomSheet` 选夹后加入；已收藏 → 选夹移动或取消收藏（`manageFavoriteFolder` + `favorites().folders`）
- [x] 简介复制到剪贴板（标题栏 Copy 图标 + 复制成功提示）
- [x] 禁漫车信息块（车号可复制 + 页数）
- [x] 章节按 10 话分组 + 已读标记 + 续读组自动展开 + 当前话高亮（复核并保留）
- [x] 高频动作台：阅读主按钮全宽 + 收藏/喜欢/追更/下载四等分胶囊卡（填充图标回显状态，禁用态降灰）
- [x] Miuix 高阶：`TooltipBox`（分享/下载全部）、`OverlayBottomSheet` 文件夹选择、`Card` + `BasicComponent` 信息块
- 验证：`:app:compileDebugKotlin` 通过。

### [x] M06 阅读器（ReaderScreen）
- [x] 左右阅读模式（`HorizontalPager` + `springAnimateToPage` + `pagerGestureOverride` + `PagerGestureNestedScrollConnection`，`userScrollEnabled=false` 避免双份手势）
- [x] 顶栏点击显隐（点击正文切换上下栏，类似原版 `closeNav`）+ 音量键翻页（`MainActivity.onKeyDown` → `ReaderVolumeKeys`，150ms 节流，首次提示一次）
- [x] 章节抽屉 + 上一话/下一话（顶栏左右箭头 + 章节 `OverlayBottomSheet`，当前话高亮）
- [x] 末页追更引导（最后一话最后一页弹「已经最后一话了！」，勾选「3天不要显示」写入静默截止时间，可直接追踪连载）
- [x] 阅读器内收藏/点赞/评论：底部动作行开关（收藏/喜欢实心回显），评论抽屉含列表 + `CommentComposer` 复用（≤200 字、未登录引导）
- [x] Miuix 高阶：`FloatingToolbar` 五等分动作行、`OverlayBottomSheet`（跳页/章节/评论）、`OverlayDialog` + `Checkbox`（追更引导）、`NumberPicker`、`VerticalScrollBar`
- [x] 阅读方向与音量提示持久化：`SettingsRepository.readerHorizontal/volumeKeyHintShown/trackPromptMuteUntil`
- 验证：`:core:test`（新增阅读器偏好用例）+ `:app:compileDebugKotlin` 通过。

### [x] M07 收藏页（FavoritesScreen）
- [x] 收藏夹内移动作品：行尾 `MoveFile` → `OverlayBottomSheet` 选择目标收藏夹（排除“全部”），成功后提示并刷新
- [x] 标签收藏 tab：`tagsFavorite()` 加载、新增（弹窗输入）、删除（二次确认）
- [x] 未登录、空态、错误重试、下拉刷新齐备
- [x] Miuix 高阶：分段 `TabRow`（收藏夹/标签收藏）、`Card` + `BasicComponent`、`OverlayBottomSheet`、`OverlayDialog`、`OverlaySpinnerPreference`

### [x] M08 历史页（HistoryScreen）
- [x] 按作品聚合：同一 albumId 折叠为一条，显示最近章节、已读话数、最近阅读时间与封面
- [x] 一键续读（整行点击 + 「继续阅读」按钮直达 `Route.Reader(latest.photoId)`），「详情」进入作品页
- [x] 删除单部作品记录（`HistoryRepository.deleteAlbum` + Room DAO）与清空全部（二次确认弹窗）
- [x] Miuix 高阶：`Card` 行、`OverlayDialog` 纵排确认、`IconButton` 删除、分页「加载更多」

### [x] M09 我的页（MineScreen）
- [x] 成就/任务：新增 `Route.Tasks` + `TasksScreen`（称号/勋章 `TabRow` + 我的/全部筛选、达成进度汇总条、逐项已达成状态、服务端 message 透传、下拉刷新与空/错态）
- [x] 个人资料查看/编辑：新增 `Route.Profile` + `ProfileScreen`（只读用户名/邮箱；昵称/姓/名/生日/网站/位置/公司/学校/关于我/其他信息/收藏类别/理想型/敏感区域/最喜欢/最讨厌；感情状态与性取向走底部选择器；标题栏与页尾双保存入口，保存后以服务端回包刷新）
- [x] 服务端观看记录：新增 `Route.WatchHistory` + `WatchHistoryScreen`（`watchHistory(page)` + 逐条 `deleteWatchHistory` + 下拉刷新 + 分页模式联动：无限滚动/点击翻页）
- [x] 我的页入口分组卡片：账号（个人资料/退出登录）、我的内容（收藏/历史/书架/信箱/连载追踪）、成就与服务（成就任务/云端观看记录/设置）
- [x] 签到进度可视化：`LinearProgressIndicator` 胶囊进度 + 精确天数（`signedDays/totalDays`）
- 替代决策 1：网络测速已由设置页「线路诊断」（`usedDomain`/`domainLatency`/`reprobeDomains` 延时升序）覆盖，我的页不重复造轮子。
- 替代决策 2：`claimTask` 库标注 @Deprecated 且参数无文档，成就页只读展示，不做领取动作。
- 替代决策 3：原版签到日历为逐月打卡图片，库接口仅提供 `signedDays/totalDays` 与 `currentProgress`，落为进度条而非日历图。
- 修正：`UserProfile.editableFields()` 键改为与库 `JmUserProfile` 记录名一致（`lastName`/`firstName`/`birthPlace`/`aboutMe`/`infoHere`/`collections`/`ideal`/`erogenic`），补全原先缺失的 6 个字段，避免提交蛇形键导致服务端忽略。
- 验证：`:core:test`（资料映射断言更新）+ `:app:compileDebugKotlin` 通过。
- Miuix 高阶：`LinearProgressIndicator` 进度、双段 `TabRow`、`OverlayBottomSheet` + `RadioButtonPreference` 选择器、`ArrowPreference` 分组卡片、`PullToRefresh`

### [x] M10 评论区 / 论坛
- [x] 评论回复（`replyToComment`）：共享 `CommentReplySheet`（200 字实时计数、发送态、换目标清草稿），详情页与阅读器均已接入；`CommentRow` 增加回复入口、默认折叠 2 条回复与「展开 N 条回复」。
- [x] 独立论坛页（全部/漫画评论/闲聊三 tab）：新增 `Route.Forum` + `ForumScreen`，`TabRow` 切换档位、`PullToRefresh`、空/错/加载态、两种分页模式（点击翻页/加载更多）；闲聊档展示可折叠「社区规则」卡片；我的页新增「社区论坛」入口。
- [x] 发表评论 ≤200 字校验与错误回显：发帖 `OverlayBottomSheet` 实时字数与超限截断、空内容禁用发布、失败保留草稿并 Snackbar 提示。
- [x] Miuix 高阶：`TabRow` 三档切换、`OverlayBottomSheet` 发帖/回复、`Card` 包裹评论、`drawBehind` 缩进回复线、`PullToRefresh` 阻尼刷新。
- 替代决策 1：原版全站论坛请求不带 `aid`，而库 `ForumQuery.Builder` 强制 `entityId` 非空（空即抛异常）且 `JmClient` 无全局论坛方法 → 以 `ForumQuery.album("0")` 占位调用 `getComments`，服务端不支持时表现空列表由空态兜底。
- 替代决策 2：库无删除评论、剧透标记、推荐书号、算术验证码、举报入口 → 本轮不做，仅保留发帖/回复（回复需评论带 `aid`，闲聊楼层提示「暂不支持回复」）。
- 替代决策 3：`CommentItem` 新增 `albumId`（取自 `JmComment.aid()`，`0`/空归一为 null），用于论坛楼层定位回复实体，同时为后续「评论跳作品」预留。
- 验证：`:core:test` + `:app:compileDebugKotlin` 通过。

### [x] M11 设置页（SettingsScreen）
- [x] 分页模式切换：新增「通用」分组 `ArrowPreference` 入口 + `OverlayBottomSheet` + `RadioButtonPreference` 两档（无限滚动/点击翻页），写入 `settings.setPaginationMode` 并同步 `Jm3Graph.paginationMode`，各列表页 `LaunchedEffect(mode)` 自动从第一页重载。
- [x] 公告/网址发布页/联系客服外链：新增「公告与支持」分组，JM 公告（Trello）、网址发布页、广告合作（mailto）经 `Intent.ACTION_VIEW` 打开，失败回落 Snackbar。
- [x] 版本号连点进入隐藏诊断：关于卡版本行 2 秒内连点 5 次打开 `OverlayBottomSheet` 诊断面板，展示包名/版本名与 versionCode/设备/系统/当前线路/自定义线路/各线路延迟/分页模式/主题与动态取色。
- [x] Miuix 高阶：`ArrowPreference` 分组卡片、`OverlayBottomSheet` + `RadioButtonPreference`（带 summary 说明两档语义）、`BasicComponent` 行式诊断。
- 替代决策 1：原版「语言（繁/简）」需要整套 i18n 资源，本项目单语言（简体），不引入切换。
- 替代决策 2：原版「切换图源」依赖网页端多 CDN 策略，库仅暴露图片域名裁决，不提供图源切换入口。
- 替代决策 3：原版「赞助/看广告」属支付与广告 SDK 域，不含在本客户端范围；广告合作邮箱外链保留。
- 验证：`:app:compileDebugKotlin` 通过。

### [x] M12 全局 Miuix 质感
- [x] 依赖接入：`libs.versions.toml` + `app/build.gradle.kts` 新增 `miuix-blur`、`miuix-squircle`（0.9.4，Gradle 缓存离线可用）
- [x] 阅读器悬浮工具栏毛玻璃：`rememberLayerBackdrop()` 录屏内容层（`Modifier.layerBackdrop`），工具栏用 `Modifier.drawBackdrop(backdrop, shape = { RoundedCornerShape(50.dp) }, effects = { blur(22.dp.toPx()) })` 采样模糊；`FloatingToolbar` 改为 `surfaceContainer.copy(alpha = 0.78f)` + `outSidePadding = 0`，避免不透明底色盖住模糊；左右模式 Pager 改为整屏沉浸（去掉 `innerPadding`），使图片真正从工具栏下方穿过
- [x] Squircle 连续曲率：6 处封面 `clip(RoundedCornerShape(x))` 全部替换为 `Modifier.squircleClip(x.dp)`（搜索行、收藏、历史、连载追踪、观看记录），Android 13 以下由库自动回落普通圆角
- [x] 微交互：新增公共 `Modifier.pressScale(scaleDown, hapticFeedback)` —— `graphicsLayer` 弹簧缩放（`spring(dampingRatio = 0.55f, stiffness = 900f)`）＋ `pointerInput` 旁路监听（不消费点击，不吞事件）；搜索结果卡与阅读器底栏 5 个动作位启用，动作位同时 `LocalHapticFeedback` 轻触反馈
- 替代决策：顶栏（`TopAppBar`）与主底栏（`FloatingNavigationBar`）在 Miuix `Scaffold` 中不覆盖内容（body 已按 bar 高度内缩），无 backdrop 可采样，强行模糊只会得到空采样；故毛玻璃落在真正覆盖内容的阅读器悬浮工具栏。路由转场沿用既有 `NavDisplayEffects(cornerClipRadius, backdropColor)` + 全路由 `swipeDismiss`。
- 验证：`:app:compileDebugKotlin` 一次通过（exit 0）。

### [ ] M13 小说域（列表/详情/阅读）
- [ ] 网关小说接口 + 三个路由 + 字体设置

### [ ] M14 书库域（作者/作品）
- [ ] 网关 creator 接口 + 作者列表/作品列表/作品详情

## 迭代日志

- 2026-10-27 / M01：扩展网关 11 项能力 + 分页/搜索历史持久化 + 9 项 core 测试。构建与测试通过，提交 `b51d6e6`。
- 2026-10-27 / M02+M03+M04+M07：首页（连载横滑/热标签/搜索历史/语法帮助/点击分页）、搜索页（时间范围服务端过滤 + 点击分页）、分类页（子分类/更多分类/每周连载/热标签/标签结果）、收藏页（移动收藏夹/标签收藏增删）。新增公共 `ResultPagerBar`/`pagedSearchResultItems` 消除重复。`:core:test` + `:app:compileDebugKotlin` 通过。
- 替代决策：原版搜索年/月（`y`/`m`）库不支持 → 改用库 `TimeOption`（今日/本周/本月/全部）；原版子分类 `slug` 过滤库未暴露 → 以名称做标签检索。
- 2026-10-27 / M05：详情页补收藏夹选择/移动、简介与车号复制、禁漫车信息块、四等分动作台（收藏/喜欢/追更/下载），章节分组与已读标记复核通过。`:app:compileDebugKotlin` 通过。
- 2026-10-27 / M07+M08：收藏页（移动收藏夹/标签收藏增删）、历史页（按作品聚合 + 续读 + 删除整部 + 清空确认 + Room `deleteAlbum`）。`:app:compileDebugKotlin` 通过。
- 2026-10-27 / M11：设置页补分页模式切换（弹层单选 + 全局状态同步）、公告/网址发布页/广告合作外链、版本号 5 连点隐藏诊断面板。`:app:compileDebugKotlin` 通过。
- 2026-10-27 / M10：详情页/阅读器评论回复（共享 `CommentReplySheet`、折叠回复与缩进线）、独立论坛页三档（`Route.Forum` + `ForumScreen`，发帖面板、两种分页、社区规则折叠卡）、我的页论坛入口；`CommentItem` 增补 `albumId`。`:core:test` + `:app:compileDebugKotlin` 通过。
- 2026-10-27 / M09：我的页新增成就任务/个人资料/云端观看记录三条路由与页面，签到进度条 + 入口分组卡片；修正资料提交键为库记录名并补全 6 个字段。`:core:test` + `:app:compileDebugKotlin` 通过。
- 2026-10-27 / M06：阅读器补左右模式（Pager 弹簧翻页 + 手势接管）、点击显隐上下栏、音量键翻页（首提示一次）、最后一话追更引导（3 天静默）、底部收藏/喜欢/评论动作行与评论抽屉；阅读方向/提示/静默时间落 DataStore。`:core:test` + `:app:compileDebugKotlin` 通过。
- 2026-10-27 / M12：接入 `miuix-blur` + `miuix-squircle`；阅读器底栏实现 `drawBackdrop` 毛玻璃胶囊（backdrop 录屏 + 22dp 模糊）、6 处封面改 `squircleClip`、新增公共 `Modifier.pressScale`（弹簧 + 触觉）并用于搜索结果卡与阅读器动作位。`:app:compileDebugKotlin` 通过。
