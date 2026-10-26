# JM3

JM Comic 第三方 Android 客户端，无广告，使用 Jetpack Compose + Miuix 构建。

## 特性

- 搜索与车号直达
- 相册详情与章节浏览
- 内置阅读器（自动还原服务端分割图片）

## 构建

依赖 Android SDK（API 36）与 JDK 17+。

```
./gradlew :app:assembleDebug
```

## 开源依赖

- [jmcomic-api-java](https://github.com/JUKOMU/jmcomic-api-java)（协议层）
- [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix)（UI 框架）
- [Coil](https://coil-kt.github.io/coil/)（图片加载）

## 许可证

[MIT](LICENSE)
