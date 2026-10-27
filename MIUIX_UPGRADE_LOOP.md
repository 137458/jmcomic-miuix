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
- Miuix 版本：`0.9.4`（`miuix-ui` / `miuix-icons` / `miuix-nav` / `miuix-preference` 已接入）
- 未接入 Miuix 子模块：`miuix-blur`、`miuix-squircle`（后者的能力由 `Modifier.squircleSurface` 在 `miuix-ui` 内提供）

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

### [ ] M10 评论区 / 论坛
- [ ] 评论回复（`replyToComment`）
- [ ] 独立论坛页（全部/漫画评论/闲聊三 tab）
- [ ] 发表评论 ≤200 字校验与错误回显
- [ ] Miuix 高阶：`OverlayBottomSheet` 发帖、缩进回复线

### [ ] M11 设置页（SettingsScreen）
- [ ] 分页模式切换
- [ ] 公告/网址发布页/联系客服外链
- [ ] 版本号连点进入隐藏诊断
- [ ] Miuix 高阶：`miuix-preference` 分组、`OverlayDialog` 纵排按钮

### [ ] M12 全局 Miuix 质感
- [ ] `miuix-blur` 顶栏/底栏渐进模糊（新增依赖）
- [ ] Squircle 裁切与连续曲率
- [ ] 路由转场 `NavTransitions` 与微交互（弹簧、按压反馈、触觉）

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
- 2026-10-27 / M09：我的页新增成就任务/个人资料/云端观看记录三条路由与页面，签到进���条 + 入口分组卡片；修正资料提交键为库记录名并补全 6 个字段。`:core:test` + `:app:compileDebugKotlin` 通过。
- 2026-10-27 / M06：阅读器补左右模式（Pager 弹簧翻页 + 手势接管）、点击显隐上下栏、音量键翻页（首提示一次）、最后一话追更引导（3 天静默）、底部收藏/喜欢/评论动作行与评论抽屉；阅读方向/提示/静默时间落 DataStore。`:core:test` + `:app:compileDebugKotlin` 通过。
