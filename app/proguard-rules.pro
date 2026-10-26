# jmcomic 使用 Gson 反序列化 API 模型，反射依赖字段与泛型签名，全量保留
-keep class io.github.jukomu.jmcomic.** { *; }
# Gson 泛型解析需要签名属性
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
# 已排除 webp-imageio（AWT）依赖，保留引用处的编译期符号不报警
-dontwarn io.github.darkxanter.webp.**
# AwtImageProcessor 是库的桌面 AWT 实现，Android 端由 jmcomic-android-support 的
# ImageProcessor SPI 替换，运行时不可达；仅编译期引用需放行
-dontwarn java.awt.Graphics2D
-dontwarn java.awt.Image
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ImageObserver
-dontwarn java.awt.image.RenderedImage
-dontwarn javax.imageio.ImageIO
# kotlinx-serialization 与 Room 自带 consumer 规则，无需额外 keep；如后续构建报缺失序列化器再按报错补充
