package com.jm3.app.miuix.ui

import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.ReleaseAsset
import com.jm3.core.update.ReleaseInfo

/**
 * 测试更新弹窗的固定假数据：只验证弹窗排版与 Markdown 渲染，全程不触网。
 *
 * 短日志用于日常预览；长日志刻意覆盖真实 Release 说明里会出现的全部块类型与长段落，
 * 用来验证折叠、限高与滚动下弹窗仍然完整可读。
 */

private const val TEST_HTML_URL = "https://github.com/jm3-app/jm3/releases"
private const val TEST_PUBLISHED_AT = "2026-01-01T00:00:00Z"

/** 短日志：保留原来的三行结构，覆盖「有安装包」这条主分支。 */
internal val TEST_UPDATE_RELEASE = ReleaseInfo(
    tagName = "v9.9.9-test",
    name = "测试更新弹窗",
    notes = listOf(
        "## 本次更新",
        "- 新增：作品卡显示标签与车号",
        "- 新增：社区论坛与本地书架显示封面",
        "- 修复：连载追踪误报未登录",
        "",
        "### 说明",
        "这是一条**测试**笔记，用于检查弹窗内的 Markdown 渲染、`行内代码` 与长文滚动。",
    ).joinToString("\n"),
    htmlUrl = TEST_HTML_URL,
    publishedAt = TEST_PUBLISHED_AT,
    assets = listOf(
        ReleaseAsset(
            name = "jm3-9.9.9-test.apk",
            size = 12_345_678L,
            downloadUrl = "https://github.com/jm3-app/jm3/releases/download/v9.9.9-test/jm3-9.9.9-test.apk",
        ),
    ),
)

/**
 * 长日志：不少于 60 行，含 1~6 级标题、无序/有序列表、分隔线、长段落、粗体与内联代码。
 * 用原始字符串而不是三引号加 trimIndent，避免把有意留出的空行与缩进吃掉。
 */
internal val TEST_LONG_UPDATE_NOTES = """# v9.9.9 长日志排版验证

本次发布把设置、阅读器与列表三块的状态口径重新收拢，同时补齐了更新提示在长说明下的排版。下面这份说明故意写得很长，用来检查弹窗在折叠、展开、滚动以及深浅色主题下的可读性，字里行间的粗体、行内代码与列表缩进都会走真实的解析路径，不是一张静态截图。

## 新增

- **阅读进度记忆**：每部漫画按车号独立记住上次读到的页码，退出阅读器、切到别的作品再回来都从原处继续；页码与总页数一起落盘，章节页数变化时不会把读者甩到越界页。
- **分页模式切换生效**：设置里选「点击翻页」后，首页、搜索、书库、观看记录与论坛立即改为底部翻页条，切回「无限滚动」则恢复触底加载，模式本身也写进设置库，冷启动后仍然保持。
- **作品卡片统一**：榜单、首页追更、相关作品、收藏、历史、追踪与书架共用同一套封面比例、车号角标与标签行，不再出现有的页面有标签、有的页面只有标题的情况。
- **更新弹窗信息补全**：弹窗内直接给出当前版本、最新版本、版本名称、发布日期、安装包文件名与大小、附件数量、说明行数，下载中还会显示百分比、已下载与总长以及实时速度。
- **更新操作补全**：除立即下载与稍后，还提供取消下载、忽略此版本、复制更新说明、在浏览器查看完整说明、打开更新页面等入口，下载进行中不再把用户困在一颗禁用的按钮上。

### 界面细节

- 版本信息用 Miuix 的 `BasicComponent` 逐行列出，标签在上、值在下，长文件名自动换行而不会把整行撑破。
- 下载进度用 `LinearProgressIndicator`：服务器给出总长时按百分比推进，使用分块传输、总长未知时退回不确定态的循环动画。
- 更新说明默认只展示前 12 行，末尾给出「展开全部」；展开后整段落在弹窗自己的滚动区里，底部主按钮始终固定在可视范围。
- 操作区按两列排布文字按钮，中文文案不会像早期横向三键那样挤成折行；错误色只留给「忽略此版本」。

#### 兼容性

设置键名与旧版逐字一致，升级不丢已存设置；阅读进度按作品单独成键，新增一部漫画不会重写整张进度表。未附带安装包的版本继续回落到 Releases 页面，接口限流或断网时也可直接在浏览器打开发布页。

##### 已知限制

更新说明的 Markdown 只认标题、无序列表、有序列表、分隔线与段落，行内只认 **粗体** 与 `行内代码`；链接、图片与 HTML 按原文显示。这是刻意的取舍：更新说明是外部输入，多认一种标记就多一份误渲染的风险。

###### 反馈

如果在这份长日志里发现标题层级、列表缩进或折叠按钮的位置不对，请直接带着设备型号与深浅色主题反馈，这类问题只在特定字号下才会暴露。

---

## 修复

1. 修复阅读器退出后不记住页码，重新进入总是从第一页开始的问题；现在页码、跳页目标与页数共用一份计划，翻页时的中间帧不再把页码拽回去。
2. 修复设置页分页模式只改本地副本、列表页读到的仍是旧值的问题；同时处理了启动读盘与用户手动选择的先后顺序，盘上的旧值不会盖掉刚选的新模式。
3. 修复部分作品列表卡片与首页实现不一致：封面比例、圆角、车号角标、标签行数与点击区域现在全部走同一份组件契约。
4. 修复更新弹窗未使用 Miuix 组件、信息量过少、操作不全的问题，并补上长更新日志下的折叠与滚动排版。
5. 修复下载中主按钮被禁用后无处取消的问题，取消会丢弃半成品并把状态干净地退回空闲。

### 升级提示

- 从旧版升级不需要清数据，设置与阅读历史保持原样。
- 首次进入阅读器时若本地已有该漫画的记录，会直接恢复到上次读到的那一页，不额外弹确认框。
- 长更新说明默认折叠，点开后仍可用手指在说明区上下滑动，底部按钮不会跟着滚走。

---

## 优化

- 分页列表的「加载更多」状态统一由 PagedFeed 决定，加载中、加载失败、还有没有下一页不再由各屏各写四个布尔。
- 更新提示的「是否该打扰」规则在启动弹窗与更新页共用一处，进更新页不会重复弹一次启动期已经提示过的版本。
- 下载进度与百分比改由纯函数换算，重复的比例钳制与取整口径被收成一处，测试覆盖除不尽、总长未知与越界三种输入。
- 说明区的行内解析按原文缓存，进度刷新触发的重组不会重新扫描整段 Markdown。

### 备注

以上条目均为排版验证用的假数据，不对应任何真实发布内容；测试入口只操作本地状态，不会访问网络，也不会真的下载或安装任何文件。长日志的行数、段落长度与标记分布刻意不规整，就是为了把折叠边界落在列表中间而不是恰好落在一个标题上。

## 结束

感谢耐心读到这里的每一位。若更新弹窗在你手上是舒服的，那这份长日志就算完成使命了。"""

/** 长日志假版本：多个附件，用来验证安装包选择、大小展示与附件数量。 */
internal val TEST_LONG_UPDATE_RELEASE = ReleaseInfo(
    tagName = "v9.9.9-test-long",
    name = "长日志排版验证版",
    notes = TEST_LONG_UPDATE_NOTES,
    htmlUrl = TEST_HTML_URL,
    publishedAt = "2026-06-18T09:30:00Z",
    assets = listOf(
        ReleaseAsset(name = "jm3-9.9.9-test-mapping.txt", size = 1_024_000L, downloadUrl = "https://example.invalid/mapping.txt"),
        ReleaseAsset(name = "jm3-9.9.9-test-arm64-v8a.apk", size = 42_000_000L, downloadUrl = "https://example.invalid/arm64.apk"),
        ReleaseAsset(
            name = "jm3-9.9.9-test-universal.apk",
            size = 48_000_000L,
            downloadUrl = "https://example.invalid/universal.apk",
        ),
        ReleaseAsset(name = "jm3-9.9.9-test-sources.zip", size = 8_000_000L, downloadUrl = "https://example.invalid/sources.zip"),
    ),
)

/** 测试弹窗的「下载中」假状态：87/100 MB，进度正好是 0.87，避免浮点截断成 86%。 */
internal val TEST_DOWNLOADING_STATE = UpdateDownloadState.Downloading(
    downloaded = 87L * 1_000_000,
    total = 100L * 1_000_000,
    speedBytesPerSec = 2_500_000L,
)

/** 总长未知的分块传输假状态：进度条应退回不确定态，文案里不出现总长。 */
internal val TEST_UNKNOWN_TOTAL_DOWNLOADING_STATE = UpdateDownloadState.Downloading(
    downloaded = 4L * 1_000_000,
    total = 0L,
    speedBytesPerSec = 1_500_000L,
)
