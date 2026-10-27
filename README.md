# JM3

JM Comic 第三方 Android 客户端。无广告，基于 Jetpack Compose 与 Miuix 构建。

## 功能

- 首页：固定搜索栏 + 发现流（热门标签、随机推荐、最新上架），下拉刷新与自动分页
- 搜索：关键词搜索、车号直达、搜索类型（综合/作品/作者/标签/登场人物）、排序切换（最新/最多观看/图片最多/最多喜欢）与独立结果页
- 浏览：分类筛选、榜单排序、时间范围过滤，列表分页加载、下拉刷新
- 交互：统一 Miuix 组件（Scaffold/TopAppBar/Preference/PullToRefresh/Snackbar），错误态可重试、edge-to-edge 显示，下载任务底栏角标
- 详情：简介折叠、章节分组与已读标记、续读定位、收藏/喜欢/连载追踪/分享、更多相关作品、标签与作者可点击搜索、发表评论
- 阅读：分割图片自动还原、内存缓存与邻页预加载、已下载页离线直读、上下话切换、页码跳转、章节抽屉
- 账号：登录/登出、会话持久化与启动恢复、收藏列表（按收藏夹筛选）、每日签到、阅读历史
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

- `:core`：协议封装与纯 JVM 业务逻辑（网关、下载引擎、搜索合并、封面 URL 构造），JUnit 测试覆盖
- `:app`：Compose UI 与 Android 实现（Room、DataStore、文件存储）

## 致谢

- [hect0x7/JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python)：协议事实标准参考
- [JUKOMU/JMComic-Api-Java](https://github.com/JUKOMU/JMComic-Api-Java)：Java 协议实现（MIT）
- [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix)：UI 组件库

## 免责声明

本项目仅供学习交流，内容来自第三方站点，与本项目无关。您应当已年满 18 周岁（或所在地区法定年龄）方可使用。请遵守当地法律法规，因使用本项目产生的任何后果由使用者自行承担。

## 许可证

[MIT](LICENSE)
