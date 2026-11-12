# JM3 Miuix

JM3 Miuix 是无广告的 JM Comic（18comic）第三方 Android 客户端。应用基于 `Jetpack Compose` 构建，重做了漫画、小说、书库与社区功能。

本项目不提供广告投放、热更新、充值支付、客服工单与账号注册服务。

## 功能特性

- **首页与发现**：支持搜索栏折叠、连载更新、热门标签、随机推荐、最新上架与每周必看入口。
- **搜索与浏览**：支持关键词搜索、车号直达、多类型筛选、分类浏览、榜单排行与独立结果页。
- **漫画详情**：支持简介折叠、每 10 话分组、首章前 6 页预览、收藏追更、相关推荐与评论互动。
- **漫画阅读**：支持分割图片自动还原、离线直读、邻页预加载、缩放与边缘翻页、音量键翻页及横屏双页模式。
- **阅读偏好**：支持纵向长图与左右翻页切换、保持亮屏、独立亮度调节、三档图片质量与预取页数设置。
- **小说与书库**：支持小说列表、小说详情、章节目录、小说阅读器、创作者书库与创作者主页浏览。
- **社区与账号**：支持论坛发帖回帖、登录与自动续期、收藏夹管理、每日签到、站内信箱、历史记录、连载追踪、成就佩戴与资料编辑。
- **离线下载**：支持多章节并发队列下载、断点续传、失败重试、前台进度通知与本地书架管理。
- **外观与大屏**：支持 HyperOS 与 ColorOS 双界面风格即时切换、动态取色、底栏形态调节及 600 dp/840 dp 断点自适应布局。
- **线路与更新**：支持自定义 API 线路与延迟诊断，支持应用内检查更新、安装包 SHA-256 校验与版本忽略。

## 下载与安装

1. 前往 [Releases](https://github.com/137458/jmcomic-miuix/releases) 页面下载最新正式版 APK 安装包。
2. 在 Android 设备上允许侧载安装后直接安装。
3. 打开应用即可浏览内容，登录后可使用收藏、追踪与签到等功能。

## 系统要求

- 运行系统：`Android 12`（API 31）及以上。

## 技术栈

- **开发语言与界面**：`Kotlin 2.4.21`、`Jetpack Compose`、`Miuix 0.9.4`、`COUI-Kit 0.0.1-alpha10`
- **协议封装**：`jmcomic-api v1.1.12`
- **图片加载与缩放**：`Coil 3.6.3`（自定义 `Fetcher`）、`telephoto 0.19.0`
- **数据存储**：`Room 2.8.5`、`DataStore 1.3.0`、`kotlinx-serialization-json 1.12.0`
- **构建与系统目标**：`Gradle 9.8.2`、AGP `9.4.1`、`compileSdk 37`、`targetSdk 36`、`minSdk 31`

## 致谢

- [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python) —— 协议事实标准参考。
- [JMComic-Api-Java](https://github.com/JUKOMU/JMComic-Api-Java) —— Java 协议实现。
- [Miuix](https://github.com/compose-miuix-ui/miuix) —— HyperOS 风格 Compose 组件库。
- [COUI-Kit](https://github.com/ItosEO/COUI-Kit) —— ColorOS 风格 Compose 组件库。

## 免责声明

本项目仅供学习交流使用，所有内容均来自第三方站点，与本项目无关。使用者应已年满 18 周岁或达到所在地区法定成年年龄，并严格遵守当地法律法规。

## 许可证

本项目主体采用 [MIT](LICENSE) 许可证开源，第三方代码与依赖的许可声明见 [NOTICE](NOTICE)。

---

## 源码构建（面向开发者）

### 环境准备

- `JDK 21` 及以上版本。
- Android SDK，并在根目录 `local.properties` 中配置 `sdk.dir`。
- Windows 环境请使用 `gradlew.bat` 替代下文命令中的 `./gradlew`。

### 常用命令

```bash
# 构建调试包
./gradlew :app:assembleDebug

# 构建发布包（开启 R8 压缩与资源收缩）
./gradlew :app:assembleRelease

# 运行全仓单元测试
./gradlew :core:test :app:testDebugUnitTest
```

### 发布签名

发布签名配置仅从根目录 `local.properties` 读取，严禁提交至代码库。配置以下四项后，`assembleRelease` 自动启用正式签名：

```properties
storeFile=path/to/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

若缺少任意一项配置，`assembleRelease` 将自动回退至调试签名。回退产物仍可本地侧载安装，但不可用于正式发布。

### 模块结构

- `:core`：纯 JVM 协议封装与核心业务逻辑（Java 17），涵盖网关接口、下载台账、分页状态机、封面定位、会话编解码与阅读器计划，含 73 个测试类、656 个测试用例。
- `:app`：Android 界面与本地存储实现（Java 21），涵盖双皮肤界面、Room 数据库、DataStore 持久化与后台下载服务，含 81 个测试类、605 个测试用例。
