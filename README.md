# JM3 Miuix

JM Comic（18comic）Android 客户端，用 Jetpack Compose + Miuix 重写原版 JMComic3 v2.1.12。无广告。

原版是 Capacitor + React 的 WebView 套壳应用，承载的是综合成人内容门户：漫画（首页信息流/分类/排行榜/每周必看/搜索/详情/阅读/下载）、小说、H 动漫、游戏、博客、论坛评论、书库，以及含收藏、追踪、签到、成就、订单、设置的会员中心。本项目按内容域分批重写其客户端形态。

不复制官方运营闭环：广告投放、OTA 热更新、充值支付、客服工单、注册/找回密码。这些依附官方服务端与商业渠道，第三方客户端没有对应能力。

## 功能

- 首页：固定搜索栏 + 发现流（热门标签、随机推荐、最新上架），每周必看入口，滚动后的随机一部/回到顶部悬浮按钮，下拉刷新与自动分页
- 搜索：关键词搜索、车号直达、搜索类型（综合/作品/作者/标签/登场人物）、排序切换（最新/最多观看/图片最多/最多喜欢）与独立结果页
- 浏览：分类筛选、榜单排序、时间范围过滤，列表分页加载、下拉刷新
- 交互：统一 Miuix 组件（悬浮胶囊导航栏/Scaffold/TopAppBar/Preference/PullToRefresh/Snackbar），未读数字角标、图标 Tooltip、错误态可重试、edge-to-edge 显示
- 详情：简介折叠、章节分组与已读标记、续读定位、收藏/喜欢/连载追踪/分享、更多相关作品、标签与作者可点击搜索、发表评论
- 阅读：分割图片自动还原、内存缓存与邻页预加载、已下载页离线直读、上下话切换、页码跳转、章节抽屉
- 账号：登录/登出、会话持久化与启动恢复、收藏列表（按收藏夹筛选与新建/重命名/删除）、每日签到、信箱（未读角标与已读标记）、连载追踪、阅读历史
- 下载：章节离线下载（并发队列、断点续传、失败重试）、本地书架管理
- 设置：主题三选与动态取色、自定义 API 线路与线路诊断（域名延迟/重新探活）、数据管理、关于页

## 技术栈

- Kotlin / Jetpack Compose / Miuix（HyperOS 风格组件库）
- 协议层：jmcomic-api v1.1.12
- 图片：Coil 3（自定义 Fetcher）
- 存储：Room（历史与下载元数据）、DataStore（会话与设置）
- Gradle 9.8.2 / AGP 9.4.1 / Kotlin 2.4.21

## 构建

要求：JDK 21+，Android SDK（命令行工具或 Android Studio），`local.properties` 中配置 `sdk.dir`。

```
# 调试包
./gradlew :app:assembleDebug

# 发布包（R8 压缩 + 资源收缩）
./gradlew :app:assembleRelease

# 单元测试
./gradlew :core:test
./gradlew :app:testDebugUnitTest
```

### 发布签名

签名信息只从 `local.properties` 读取，绝不入库。配置以下四项即启用 release 签名：

```properties
storeFile=path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

任一项缺失时，`assembleRelease` 自动回退 debug 签名，产物仍可直接侧载安装，但不可用于正式分发。

## 模块结构

- `:core`：协议封装与纯 JVM 业务逻辑（网关、下载引擎、搜索合并、封面 URL 构造），JUnit 单元测试覆盖（146 用例）
- `:app`：Compose UI 与 Android 实现（Room、DataStore、文件存储），JUnit 单元测试覆盖核心交互几何、共享元素宿主生命周期与数据映射（99 用例）

## 致谢

- [hect0x7/JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python)：协议事实标准参考
- [JUKOMU/JMComic-Api-Java](https://github.com/JUKOMU/JMComic-Api-Java)：Java 协议实现（MIT）
- [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix)：UI 组件库

## 免责声明

本项目仅供学习交流，内容来自第三方站点，与本项目无关。您应当已年满 18 周岁（或所在地区法定年龄）方可使用。请遵守当地法律法规，因使用本项目产生的任何后果由使用者自行承担。

## 许可证

[MIT](LICENSE)
