package com.jm3.app.miuix.ui.coui.page

import android.graphics.BlendMode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.diagnostic.renderDiagnosticProbe
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.TopAppBarSurface
import com.jm3.app.miuix.ui.coui.component.CouiLibraryNavBarMinSdk
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiSelectedContainerAlpha
import com.jm3.app.miuix.ui.coui.component.CouiTopBarPlainBackButton
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiSpotlightLight
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.core.model.Chapter
import top.yukonga.miuix.kmp.basic.Icon

/** HUD 外间距常量：底栏、顶栏与 Snackbar 避让共用同一组数值。 */
internal const val ReaderHudBarVerticalMarginDp = 8
internal val ReaderHudBarVerticalMargin = ReaderHudBarVerticalMarginDp.dp

/** 顶栏最小高度：只放返回、标题、页码与上下话，字号放大时可随内容撑开。 */
internal val ReaderHudTopHeight = 56.dp

/** 底栏最小高度：图标 22dp 加一行说明文字。 */
internal const val ReaderHudBottomHeightDp = 64
internal val ReaderHudBottomHeight = ReaderHudBottomHeightDp.dp

/** 降级底栏的不透明度：太实会挡住正文，太虚又读不清栏内文字。 */
private const val ReaderHudFallbackAlpha = 0.92f

/** 创建带实色背景的 HUD 采样源：防止页边缘留白或加载态露出透明像素。 */
@Composable
internal fun rememberCouiReaderBackdrop(diagnosticName: String): CouiBackdrop {
    val pageBackground = CouiKitTheme.colorScheme.background
    return rememberCouiBackdrop(
        diagnosticName = diagnosticName,
        drawSource = {
            drawRect(pageBackground)
            drawContent()
        },
    )
}

/** 漫画与小说阅读器共用的顶部渐变模糊承托：覆盖状态栏到浮栏下缘，消除内容硬切。 */
@Composable
internal fun BoxScope.ReaderTopAppBarSurface(
    visible: Boolean,
    backdrop: CouiBackdrop,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    val dark = CouiKitTheme.isDark
    val background = CouiKitTheme.colorScheme.background
    TopAppBarSurface(
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        backdrop = backdrop,
        fadeHeight = CouiTopAppBarDefaults.fadeHeight,
        bottomExtension = CouiTopAppBarDefaults.bottomExtension,
        gradientSpeed = 3f,
        gradientColor = background.copy(alpha = if (dark) 0.55f else 0.78f),
        dark = dark,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = ReaderHudBarVerticalMargin),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/**
 * HUD 顶/底栏的统一胶囊外壳：API 34 起走库的浮动表面（透镜折射、投影与描边一次给全），
 * 更低版本拿不到那套材质，退化成投影加半透明胶囊：容器色叠 0.92 不透明度，背后正文仍能透出来。
 * 两条路径以 [height] 作为最小高度，字体放大或标题两行时可自然撑开而不裁剪。
 */
@Composable
internal fun ReaderHudBar(
    backdrop: CouiBackdrop,
    modifier: Modifier = Modifier,
    height: Dp,
    placement: CouiReaderHudPlacement = CouiReaderHudPlacement.BOTTOM,
    diagnosticName: String,
    content: @Composable () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk) {
        ReaderHudFloatingBar(
            backdrop = backdrop,
            modifier = modifier,
            height = height,
            placement = placement,
            diagnosticName = diagnosticName,
            content = content,
        )
    } else {
        Box(
            modifier = modifier
                .heightIn(min = height)
                // 探针只在 CouiRenderDiagnostics 开启时生效，生产环境是空操作
                .renderDiagnosticProbe(diagnosticName)
                .dropShadow(
                    shape = CouiCapsuleShape,
                    shadow = Shadow(
                        radius = 12.dp,
                        color = Color.Black,
                        alpha = if (CouiKitTheme.isDark) 0.5f else 0.16f,
                    ),
                )
                .clip(CouiCapsuleShape)
                .background(CouiKitTheme.colorScheme.container.copy(alpha = ReaderHudFallbackAlpha))
                // 低版本拿不到库材质，但凝光门槛更低：垫完底色就补光，与 34+ 的观感差距只剩折射
                .couiSpotlightLight(SpotlightPreset.TranslucentLarge1, interactive = true),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@RequiresApi(34)
@Composable
private fun ReaderHudFloatingBar(
    backdrop: CouiBackdrop,
    modifier: Modifier = Modifier,
    height: Dp,
    placement: CouiReaderHudPlacement,
    diagnosticName: String,
    content: @Composable () -> Unit,
) {
    val dark = CouiKitTheme.isDark
    val contourGlowEnabled = LocalCouiContourGlowEnabled.current
    val density = LocalDensity.current
    val contentLayer = rememberGraphicsLayer()
    // 一次只画一条栏，单主体渲染器就够；两处栏各自持有实例，互不共享坐标
    val renderer = remember { CouiFloatingRenderer.standalone() }
    val material = remember(dark, density) { with(density) { readerHudMaterial(dark) } }
    val shadow = remember(placement, dark, contourGlowEnabled, density) {
        with(density) { readerHudShadow(placement, dark, contourGlowEnabled) }
    }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    // 库的浮动材质本身不带凝光：光只能在 beforeContent 里补，那里是「材质之后、内容之前」，
    // 输入挂在外层并在 couiFloatingSurface 之前测量，外层不裁切阴影与前景合成区域
    val light = rememberCouiSpotlight(
        interactive = true,
        preset = SpotlightPreset.TranslucentLarge1,
        dark = dark,
    )

    Box(
        modifier = modifier
            .heightIn(min = height)
            .renderDiagnosticProbe(diagnosticName)
            .onSizeChanged { bounds = it }
            .then(light.first)
            .couiFloatingSurface(
                backdrop = backdrop,
                renderer = renderer,
                material = material,
                shadow = shadow,
                shape = CouiCapsuleShape,
                contentLayer = contentLayer,
                contourGlowEnabled = contourGlowEnabled,
                foregroundComposition = true,
                beforeContent = { path ->
                    clipPath(path) { light.second?.invoke(this) }
                },
                surface = {
                    CouiFloatingSurface(
                        bounds = Rect(0f, 0f, bounds.width.toFloat(), bounds.height.toFloat()),
                        cornerWeight = 1f,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 内层负责把内容录进图层並按胶囊裁剪，浮动表面才有透明前景可以折射叠加
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .drawWithContent { contentLayer.record { this@drawWithContent.drawContent() } }
                .clip(CouiCapsuleShape),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** 浮动表面的材质：深浅两套参数与 4dp 折射外扩取自库底栏，保证与首页底栏同质。 */
private fun Density.readerHudMaterial(dark: Boolean): CouiFloatingMaterial = if (dark) {
    CouiFloatingMaterial(
        firstColor = Color(0x80666666),
        firstBlendMode = BlendMode.OVERLAY,
        secondColor = Color(0xB3262626),
        edgeAlpha = 0.8f,
        innerGlowAlpha = 0.03f,
        extension = 4.dp.roundToPx(),
    )
} else {
    CouiFloatingMaterial(
        firstColor = Color(0xA3666666),
        firstBlendMode = BlendMode.COLOR_DODGE,
        secondColor = Color(0x80F0F0F0),
        edgeAlpha = 0.25f,
        innerGlowAlpha = 0.1f,
        extension = 4.dp.roundToPx(),
    )
}

/** 浮动表面的投影与焦散：按顶/底区分配置，顶栏关闭白色底晕，底栏复用 NavigationBar 胶囊焦散比例。 */
private fun Density.readerHudShadow(
    placement: CouiReaderHudPlacement,
    dark: Boolean,
    contourGlowEnabled: Boolean,
): CouiFloatingShadow {
    val spec = couiReaderHudShadowSpec(placement, dark, contourGlowEnabled)
    fun pxSize(dp: Float): Float {
        val px = dp * density
        return (if (px >= 0f) px + 0.5f else px - 0.5f).toInt().toFloat()
    }
    fun pxOffset(dp: Float): Float = (dp * density).toInt().toFloat()
    return CouiFloatingShadow(
        elevation = pxSize(spec.elevationDp),
        color = Color.Black.copy(alpha = spec.shadowAlpha),
        lightY = pxSize(spec.lightYDp),
        lightZ = if (spec.lightZDp == 0f) 0f else pxSize(spec.lightZDp),
        lightRadius = pxSize(spec.lightRadiusDp),
        causticColor = if (spec.causticAlpha > 0f) {
            Color.White.copy(alpha = spec.causticAlpha)
        } else {
            Color.Transparent
        },
        layout = Rect(0f, 0f, 0f, pxOffset(spec.causticBottomDp)),
        sdf = pxOffset(spec.causticSdfDp),
        smoothCorner = spec.smoothCorner,
    )
}

/** 顶栏的上一话/下一话：图标带极简文本标签以区分左侧返回键，无相邻章时置灰并保持 48dp 触区。 */
@Composable
internal fun ReaderHudChapterStep(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        CouiKitTheme.colorScheme.onSurface
    } else {
        CouiKitTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }
    Column(
        modifier = Modifier
            // 所在 HUD 面板本身已有凝光，这里不再叠第二层；只保留按压反馈
            .couiPressable(
                shape = CouiCapsuleShape,
                enabled = enabled,
                spotlight = null,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = tint)
        Text(
            text = label,
            kind = CouiTextKind.GroupFooter,
            color = tint,
            maxLines = 1,
        )
    }
}

/** 跳页弹窗加减微调按钮：视觉保持 32dp 圆形胶囊，外侧补足 48dp 触区。 */
@Composable
internal fun ReaderPageJumpStepper(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = Modifier
            .couiPressable(
                shape = CircleShape,
                backgroundColor = scheme.container,
                enabled = enabled,
                spotlight = if (enabled) SpotlightPreset.OpaqueSmall2 else null,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .size(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.3f),
        )
    }
}

/** 底栏动作：等分宽胶囊触区 + 选中态横向胶囊底衬。 */
@Composable
internal fun ReaderHudAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val tint = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.3f)
        active -> scheme.accent
        else -> scheme.onSurface
    }
    val containerColor = if (active && enabled) {
        scheme.accent.copy(alpha = CouiSelectedContainerAlpha)
    } else {
        Color.Transparent
    }
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            // 只在 HUD 面板内使用，光由面板统一给，项内不再叠第二层
            .couiPressable(
                shape = CouiCapsuleShape,
                backgroundColor = containerColor,
                enabled = enabled,
                spotlight = null,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = tint)
        Text(
            text = label,
            kind = CouiTextKind.GroupFooter,
            color = tint,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
        )
    }
}

/** HUD 顶栏：返回、标题、页码入口与上下话步进，外包一层顶部渐进模糊。 */
@Composable
internal fun BoxScope.ReaderTopHudBar(
    visible: Boolean,
    backdrop: CouiBackdrop,
    title: String,
    pageLabel: String,
    accent: Color,
    onBack: () -> Unit,
    onOpenPageJump: () -> Unit,
    prevChapter: Chapter?,
    nextChapter: Chapter?,
    onOpenChapter: (String) -> Unit,
) {
    ReaderTopAppBarSurface(
        visible = visible,
        backdrop = backdrop,
    ) {
        ReaderHudBar(
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .readableWidth(),
            height = ReaderHudTopHeight,
            placement = CouiReaderHudPlacement.TOP,
            diagnosticName = "readerHud/top",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // HUD 栏自带浮动材质与凝光，返回键不叠第二层凝光
                CouiTopBarPlainBackButton(onClick = onBack, spotlight = null)
                Text(
                    text = title,
                    kind = CouiTextKind.TopBar,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pageLabel,
                    kind = CouiTextKind.ItemValue,
                    color = accent,
                    modifier = Modifier
                        // 所在 HUD 面板本身已有凝光，这里不再叠第二层
                        .couiPressable(spotlight = null, minTouchTarget = CouiMinTouchTarget) { onOpenPageJump() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    maxLines = 1,
                )
                ReaderHudChapterStep(
                    icon = CouiIcons.Back,
                    label = "上话",
                    enabled = prevChapter != null,
                    onClick = { prevChapter?.let { onOpenChapter(it.id) } },
                )
                ReaderHudChapterStep(
                    icon = CouiIcons.Forward,
                    label = "下话",
                    enabled = nextChapter != null,
                    onClick = { nextChapter?.let { onOpenChapter(it.id) } },
                )
            }
        }
    }
}

/** HUD 底栏：等分槽位匹配 CouiCapsuleShape 横向胶囊指示器与触区。 */
@Composable
internal fun BoxScope.ReaderBottomHudBar(
    visible: Boolean,
    backdrop: CouiBackdrop,
    horizontal: Boolean,
    favorited: Boolean,
    liked: Boolean,
    onToggleMode: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenComments: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleLike: () -> Unit,
) {
    if (!visible) return
    ReaderHudBar(
        backdrop = backdrop,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = ReaderHudBarVerticalMargin)
            .fillMaxWidth()
            .readableWidth(),
        height = ReaderHudBottomHeight,
        placement = CouiReaderHudPlacement.BOTTOM,
        diagnosticName = "readerHud/bottom",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderHudAction(
                icon = if (horizontal) CouiIcons.Sort else CouiIcons.Book,
                label = if (horizontal) "直向" else "左右",
                modifier = Modifier.weight(1f),
                onClick = { onToggleMode() },
            )
            ReaderHudAction(
                icon = CouiIcons.List,
                label = "章节",
                modifier = Modifier.weight(1f),
                onClick = { onOpenChapters() },
            )
            ReaderHudAction(
                icon = CouiIcons.Comment,
                label = "评论",
                modifier = Modifier.weight(1f),
                onClick = { onOpenComments() },
            )
            ReaderHudAction(
                icon = CouiIcons.Folder,
                label = "收藏",
                modifier = Modifier.weight(1f),
                active = favorited,
                onClick = { onToggleFavorite() },
            )
            ReaderHudAction(
                icon = CouiIcons.Star,
                label = "喜欢",
                modifier = Modifier.weight(1f),
                active = liked,
                onClick = { onToggleLike() },
            )
        }
    }
}
