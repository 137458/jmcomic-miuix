# JM3 Miuix

JM3 Miuix 是 JM Comic（18comic）的第三方 Android 客户端。应用基于 Jetpack Compose 构建，不含广告、热更新与充值支付。当前版本 2.0.0（versionCode 20000）。

## 功能

| 模块 | 说明 |
| :--- | :--- |
| 首页与发现 | 搜索栏折叠、连载更新、热门标签、随机推荐、最新上架与每周必看。 |
| 搜索与浏览 | 关键词搜索、车号直达、多类型筛选、分类浏览与榜单排行。 |
| 漫画与小说 | 简介折叠、按话分组、首章前 6 页预览、章节阅读、创作者书库与主页。 |
| 阅读器 | 长图与翻页切换、横屏双页、分割图片还原、离线直读、音量键与边缘翻页。 |
| 阅读偏好 | 三档画质、预取页数、邻页预加载、独立亮度与保持亮屏。 |
| 社区与账号 | 论坛发帖回帖、登录自动续期、收藏夹、签到、信箱、历史、连载追踪与成就佩戴。 |
| 离线下载 | 多章节并发队列、断点续传、失败重试、前台进度通知与本地书架。 |
| 外观与线路 | HyperOS 与 ColorOS 双皮肤切换、动态取色、600 dp/840 dp 断点自适应、自定义 API 线路与延迟诊断、应用内更新与 SHA-256 校验。 |

## 系统要求

- 运行系统：Android 12（API 31）及以上。

## 下载安装

1. 在 [Releases](https://github.com/137458/jmcomic-miuix/releases) 页面下载最新正式版 APK。
2. 允许侧载安装后直接安装。
3. 登录后可启用收藏、追踪与签到。

## 免责声明

应用为非官方客户端，与 18comic 无隶属关系。应用不提供任何内容与账号服务，内容均来自第三方站点。使用者应年满 18 周岁或达到所在地区法定成年年龄，并遵守当地法律法规。

## 致谢

协议细节与界面组件参考以下开源项目。

- [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python) —— 协议事实标准参考。
- [JMComic-Api-Java](https://github.com/JUKOMU/JMComic-Api-Java) —— Java 协议实现。
- [Miuix](https://github.com/compose-miuix-ui/miuix) —— HyperOS 风格 Compose 组件库。
- [COUI-Kit](https://github.com/ItosEO/COUI-Kit) —— ColorOS 风格 Compose 组件库。

## 许可

源码采用 [MIT](LICENSE) 许可。标注为 Apache-2.0 或 ISC 的文件除外，明细见 [NOTICE](NOTICE)。协议实现依赖 jmcomic-api，遵循该库自身许可。分发或修改源码时需保留上述许可声明。

## 附录 源码构建

### 环境准备

需要 JDK 21 与 Android SDK Platform 37（`android-37.0`）及 Build-Tools 37.0.0。在根目录 `local.properties` 中配置 `sdk.dir`。仓库地址为 `git@github.com:137458/jmcomic-miuix.git`。下列命令为 Windows 写法，其他平台改用 `./gradlew`。

### 常用命令

```bash
# 构建调试包
.\gradlew.bat :app:assembleDebug

# 构建正式包
.\gradlew.bat :app:assembleRelease

# 运行全仓单元测试
.\gradlew.bat :core:test :app:testDebugUnitTest
```

### 发布签名

签名四项仅从根目录 `local.properties` 读取，该文件不入库：

```properties
storeFile=path/to/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

四项齐全但 keystore 文件缺失时，配置期直接失败。缺少任意一项则回退至 debug 签名，回退产物不可用于正式发布。

### 模块与测试规模

| 模块 | 职责 | 测试 |
| :--- | :--- | :--- |
| `:core` | 纯 Kotlin JVM，协议与领域逻辑，无 Android 依赖 | 80 类、757 例 |
| `:app` | Jetpack Compose 界面，双皮肤 HyperOS 与 ColorOS | 142 类、1,223 例 |

全仓合计 222 个测试类与 1,980 个用例，当前全部通过。`:core` 测试运行于 JVM，无需设备。

### 技术栈

| 类别 | 组件 |
| :--- | :--- |
| 语言与构建 | `Kotlin 2.4.21`、`AGP 9.4.1`、`Gradle 9.8.2`、JDK 21 |
| 界面 | Jetpack Compose（BOM `2026.09.00`）、`Miuix 0.9.4`、`COUI-Kit 0.0.1-alpha12` |
| 协议 | `jmcomic-api v1.1.12` |
| 图片 | `Coil 3.6.3`、`telephoto 0.19.0` |
| 数据与网络 | `Room 2.8.5`、`KSP 2.3.12`、`DataStore 1.3.0`、`OkHttp 4.12.0` |
| 序列化与并发 | `kotlinx-serialization 1.12.0`、`kotlinx-coroutines 1.11.0` |
| SDK 目标 | `minSdk 31`、`targetSdk 36`、`compileSdk android-37.0` |
