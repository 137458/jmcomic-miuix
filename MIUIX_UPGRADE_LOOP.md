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

### [ ] M01 基础设施：网关能力扩展 + 设置持久化
- [ ] `Jm3Gateway` 新增：连载更新 `serialization(page)`、分类树 `categoriesList()`、评论回复 `replyComment`、任务 `tasks(type,filter)` / `claimTask`、个人资料 `userProfile(uid)` / `editUserProfile`、观看记录 `watchHistory(page)` / `deleteWatchHistory`、收藏标签 `tagsFavorite()` / `addFavoriteTags` / `removeFavoriteTags`
- [ ] `SettingsRepository` 新增：分页模式（infinite/click）、搜索历史（去重、上限 10、可清除）、卷键翻页提示已读
- [ ] `SettingsStore`（app）实现上述持久化
- [ ] `:core:test` 新增映射与契约测试

### [ ] M02 搜索页（SearchScreen / Home 内搜索）
- [ ] 搜索历史（本地持久化、点击复搜、逐条删除、清空、上限）
- [ ] 高级语法帮助弹层（包含/排除写法）
- [ ] 年份/月份本页过滤（仅过滤已加载结果，带提示文案）
- [ ] 分页模式联动（点击分页 vs 无限滚动）
- [ ] Miuix 高阶：`SearchBar` 固定 + `InputField`、筛选 `TabRow`/`OverlaySpinnerPreference`、空态引导

### [ ] M03 首页（HomeScreen）
- [ ] 多块横滑轮播：连载更新（`serialization`）、热门/分类块、随机推荐；每块「查看更多」
- [ ] 点击分页模式
- [ ] 回顶/随机一部 FAB 联动滚动
- [ ] Miuix 高阶：`PullToRefresh` 阻尼、`FloatingActionButton`、卡片内 `BasicComponent`

### [ ] M04 分类页（BrowseScreen）
- [ ] 子分类横向条（`categoriesList`）+ 更多分类弹窗
- [ ] 每周连载更新 tab（`serialization`）
- [ ] 热门标签行 → 搜索
- [ ] 收藏标签快捷入口
- [ ] Miuix 高阶：`TabRow` 分段、`FlowRow` 胶囊标签、`OverlayBottomSheet` 更多分类

### [ ] M05 详情页（AlbumDetailScreen）
- [ ] 收藏夹选择/移动（复用 `manageFavoriteFolder` + `favorites().folders`）
- [ ] 简介复制到剪贴板 + 禁漫车信息块
- [ ] 章节按 10 话分组 + 已读标记 + 当前话高亮（复核）
- [ ] Miuix 高阶：动作行胶囊化、`TooltipBox`、`OverlayBottomSheet` 文件夹选择

### [ ] M06 阅读器（ReaderScreen）
- [ ] 左右阅读模式（HorizontalPager + `springAnimateToPage` + `pagerGestureOverride`）
- [ ] 顶栏点击显隐 + 音量键翻页（Activity KeyEvent）
- [ ] 章节抽屉 + 上一话/下一话
- [ ] 末页追更引导（三天不再提示）
- [ ] 阅读器内收藏/点赞/评论抽屉
- [ ] Miuix 高阶：`FloatingToolbar`、`OverlayBottomSheet`、`NumberPicker`、`VerticalScrollBar`

### [ ] M07 收藏页（FavoritesScreen）
- [ ] 收藏夹内移动作品
- [ ] 标签收藏 tab（`tagsFavorite`、增删）
- [ ] Miuix 高阶：`OverlaySpinnerPreference`、分段 `TabRow`

### [ ] M08 历史页（HistoryScreen）
- [ ] 按作品聚合 + 最近阅读章节/页 + 一键续读
- [ ] 删除单条/清空（复核）
- [ ] Miuix 高阶：卡式行、`BasicComponent`

### [ ] M09 我的页（MineScreen）
- [ ] 成就/任务（`tasks`、`claimTask`）
- [ ] 个人资料查看/编辑（`userProfile`/`editUserProfile`）
- [ ] 服务端观看记录
- [ ] 网络测速（线路延迟色阶）
- [ ] 签到日历（`dailyCheckInStatus` 记录）
- [ ] Miuix 高阶：签到卡进度可视化、入口分组卡片

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

（每次模块完成后追加：补齐功能 / Miuix 替换 / 发散创新 / 构建结果）
