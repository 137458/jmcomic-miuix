// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.input.CouiHapticFeedback
import com.itos.coui.input.performCouiHapticFeedback
import com.itos.coui.layout.Scaffold
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.navigation.NavigationBar
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiTextStyle
import com.itos.coui.topappbar.CouiTopAppBarColors
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.alignToReadableContent
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding

/**
 * 由 [CouiAppScaffold] 提供的正文区采样源：仅当页面装配了带 [CouiBackdrop] 需求的顶栏扩展条
 * 或浮层时创建（API 34+），让库顶栏的同级扩展控件（`ChipTabRow` / `SegmentedControl`）可采样滚动正文。
 */
val LocalCouiAppBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }

/**
 * COUI 页面骨架：顶栏、底栏净空与 FAB 都在本页 Scaffold 装配。
 *
 * 底栏由主框架单实例绘制在页面之上，转场期不会随两个页面各组合一份；
 * 这里只按它公布的净空值在 bottomBar 槽位留出等高占位，
 * 让正文的 innerPadding 与 FAB 位置一起抬到底栏之上。
 */
@Composable
fun CouiAppScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable BoxScope.() -> Unit)? = null,
    contentBackdrop: CouiBackdrop? = null,
    alignTopBar: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    // 净空值不含系统导航栏 inset，占位也要让出同一条底部安全区
    val barClearance = LocalFloatingBarContentPadding.current
    // 库 Scaffold 的 windowInsets 只含系统栏，IME 避让按库要求由宿主缩小视口（Scaffold.kt:45）
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            CompositionLocalProvider(LocalCouiAppBackdrop provides contentBackdrop) {
                // 顶栏内容统一收进正文列：大屏上标题、返回键与正文左缘共线。
                // 满宽底色与毛玻璃采样层由各页顶栏自己铺在收窄层之外，不跟着内容列缩。
                if (alignTopBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alignToReadableContent(),
                    ) {
                        topBar()
                    }
                } else {
                    topBar()
                }
            }
        },
        bottomBar = {
            if (barClearance > 0.dp) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(barClearance)
                        .navigationBarsPadding(),
                )
            }
        },
        floatingActionButton = floatingActionButton?.let { fab ->
            // FAB 与正文同处限宽内容列：不跟着收窄时它贴在大屏窗口右下角，看着像脱离了页面。
            // 包裹盒必须撑满槽位：各页在 BoxScope 里用 align(BottomEnd) 定位，
            // 盒子一旦收缩成 FAB 大小，align 就退化到盒子左上角，FAB 会跑到页面左边。
            {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alignToReadableContent(),
                ) { fab() }
            }
        },
        content = { innerPadding ->
            if (contentBackdrop != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .couiBackdrop(contentBackdrop)
                        .background(CouiTheme.colorScheme.background),
                ) {
                    content(innerPadding)
                }
            } else {
                content(innerPadding)
            }
        },
    )
}

/** 可点件最小触摸目标：无障碍基线 48dp，低于它的可见按钮靠留白补足触区。 */
val CouiMinTouchTarget = 48.dp

/** 全 COUI 评论、回复与筛选输入区的统一圆角轮廓：比卡片更方，输入区不随皮肤随意变形。 */
val CouiCommentFieldShape = CouiShape(radius = 16.dp, cornerWeight = 3f)

/** 详情页操作按钮的统一圆角轮廓：主阅读按钮、功能瓦片与菜单行共用。 */
internal val CouiDetailButtonShape: CouiShape = CouiCommentFieldShape

/** 详情页主阅读大按钮的默认轮廓。 */
internal val CouiPrimaryActionDefaultShape: androidx.compose.ui.graphics.Shape = CouiDetailButtonShape

/**
 * 按压反馈 + 无涟漪点击：COUI 页面里所有自绘可点区域的统一入口。
 *
 * 反馈分三层：库的 [couiButtonFeedback] 给 hover/focus/press 蒙层，[couiPressMotion] 给原厂
 * 按压缩放（按面积与宽高比取 0.85–0.97，宽条走糖果曲线），按下瞬间再由
 * [performCouiHapticFeedback] 发一次 COUI 触觉。列表行只叠蒙层，
 * 整行随按下缩放会压过行内的滑选与长按，所以缩放由 [scaleOnPress] 显式打开。
 * 点击指示器置空，避免在实底卡片上再叠一层涟漪。语义角色由调用方决定，默认按钮。
 *
 * @param minTouchTarget 补足触区的最小边长；视觉内容仍按原尺寸居中，只有可点范围变大。
 * 本修饰符必须写在固定尺寸之前（`Modifier.couiPressable(...).size(40.dp)`）：写在 `.size()` 之后时，
 * 外层固定尺寸把子约束钉死，扩边会被 coerceAtMost 钳回原尺寸，触区等于视觉尺寸。
 * 胶囊或圆标的底色须通过 [backgroundColor] 传入（不可在调用点外侧先 `.clip().background()`），
 * 底色、裁剪、按压蒙层与凝光才会一起留在视觉尺寸内，同时点击命中仍覆盖完整 [minTouchTarget] 区域。
 * @param scaleOnPress 小型控件（标签、图标键、胶囊）打开原厂缩放；整行列表项保持关闭。
 * @param spotlight 凝光材质预设；传 null 关闭。默认取 [SpotlightPreset.OpaqueSmall2]：
 * 与库顶栏按钮同档的中性表面，落在不透明底上按 OVERLAY 叠光。
 * @param spotlightAboveContent 内容是不透明图像（封面、缩略图）时打开：光改画在内容之上，
 * 否则会被图像整个盖住；文字与图标为主的表面保持关闭，光落在底色与内容之间。
 */
@Composable
fun Modifier.couiPressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    shape: Shape? = null,
    backgroundColor: Color = Color.Unspecified,
    minTouchTarget: Dp = 0.dp,
    scaleOnPress: Boolean = false,
    spotlight: SpotlightPreset? = SpotlightPreset.OpaqueSmall2,
    spotlightAboveContent: Boolean = false,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    // 每次按住只发一次：滑动、长按都不会再触发，避免连续震动
    LaunchedEffect(interactionSource, view) {
        var down = false
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                if (!down) {
                    down = true
                    view.performCouiHapticFeedback(CouiHapticFeedback.Switch)
                }
            } else if (interaction is PressInteraction.Release || interaction is PressInteraction.Cancel) {
                down = false
            }
        }
    }
    val press = if (scaleOnPress) rememberCouiPressMotion() else null
    // 视觉表面全部留在 couiMinimumTouchTarget 内侧（后置）：底色只铺在视觉胶囊/圆标内，
    // 凝光落在底色之后、内容之前；放大比例只在视觉层施加，手势与点击检测放在触区外侧。
    val visualSurface = Modifier
        .then(
            if (press != null) {
                Modifier.graphicsLayer {
                    scaleX = press.scale.value
                    scaleY = press.scale.value
                }
            } else {
                Modifier
            },
        )
        .then(if (shape != null) Modifier.clip(shape) else Modifier)
        .then(if (backgroundColor != Color.Unspecified) Modifier.background(backgroundColor) else Modifier)
        .couiButtonFeedback(
            dark = CouiTheme.isDark,
            interactionSource = interactionSource,
            enabled = enabled,
            list = false,
        )
        .couiSpotlightLight(
            preset = spotlight,
            interactive = enabled,
            aboveContent = spotlightAboveContent,
        )
    val pressDetector = if (press != null) {
        Modifier.couiPressMotion(press = press, enabled = enabled, cancelOnConsumedMove = true)
    } else {
        Modifier
    }

    return this
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
        .then(pressDetector)
        .couiMinimumTouchTarget(minTouchTarget)
        .then(visualSurface)
}

/**
 * 凝光叠层：把库 Spotlight 的输入观察与绘制接到同一个节点上。
 *
 * 输入侧只在 Initial 阶段读指针位置、不消费事件；绘制侧默认在内容之前叠光，
 * 因此本修饰符必须紧跟在「不透明底色 + clip」之后、内容之前，且不可跨节点复用。
 * [color] 是凝光的取色，库的浮动节点传内容色（NavigationBar.kt:433），不传则用预设自带的白光。
 * [aboveContent] 把光改画在子内容之上：卡片这类表面由不透明画面（封面图）铺满，
 * 光画在内容之下会被整个盖住，只剩页边一条可见；库的弹窗按钮同样把光画在内容之上
 * （CouiDialogButton.kt:82-84 drawWithContent 里先 drawContent 再 drawLight）。
 * API < 33 时输入与绘制都是空实现（CouiSpotlight.kt:100-115），调用点不必写版本分支。
 */
@Composable
internal fun Modifier.couiSpotlightLight(
    preset: SpotlightPreset?,
    interactive: Boolean,
    color: Color? = null,
    aboveContent: Boolean = false,
): Modifier {
    if (preset == null) return this
    val (input, draw) = rememberCouiSpotlight(
        interactive = interactive,
        preset = preset,
        dark = CouiTheme.isDark,
        // 库的浮动节点把内容色一并交给凝光（NavigationBar.kt:433），这里保持同一口径
        color = color,
    )
    val layer = if (aboveContent) {
        Modifier.drawWithContent {
            drawContent()
            draw?.invoke(this)
        }
    } else {
        Modifier.drawBehind { draw?.invoke(this) }
    }
    return this.then(input).then(layer)
}

/**
 * 把可点区域补到 [target]：内层按原尺寸测量，外层报补足后的边长并居中放置。
 *
 * 触摸命中按布局节点的最终尺寸判定，因此触区随外层一起变大，可见内容不缩放。
 */
/**
 * 顶栏标题槽的标准写法。
 *
 * COUI 顶栏会把标题字号、字重与颜色下发到 [LocalCouiTextStyle]，折叠时再按
 * collapsedTitleSize/expandedTitleSize 的比值整体缩放这个样式。槽内若用 Compose 默认字号的
 * BasicText，作者拿不到这套字阶（详情页曾因此落回 16sp），所以统一走本函数。
 */
@Composable
fun CouiTopBarTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    // 大屏档把库下发的标题字阶整体放大，库内折叠比例的换算不受影响
    val style = couiScaledStyle(LocalCouiTextStyle.current)
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign == null) style else style.copy(textAlign = textAlign),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 顶栏底色取皮肤页面底色。
 *
 * 库默认背景是纯黑/纯白，深色下还会叠一层 40% 黑渐变（TopAppBarSurface），在自带绿调的
 * 页底上就是一条黑带；这里改用皮肤 background，让顶栏与正文同底。
 */
@Composable
fun couiTopAppBarColors(): CouiTopAppBarColors = CouiTopAppBarDefaults.colors(
    backgroundColor = CouiTheme.colorSchemeFor(CouiTheme.isDark).background,
    dark = CouiTheme.isDark,
)

private fun Modifier.couiMinimumTouchTarget(target: Dp): Modifier {
    if (target <= 0.dp) return this
    return layout { measurable, constraints ->
        val targetPx = target.roundToPx()
        val placeable = measurable.measure(constraints)
        val width = placeable.width
            .coerceAtLeast(targetPx)
            .coerceAtMost(constraints.maxWidth)
            .coerceAtLeast(constraints.minWidth)
        val height = placeable.height
            .coerceAtLeast(targetPx)
            .coerceAtMost(constraints.maxHeight)
            .coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2)
        }
    }
}

internal const val CouiProgressAnimMs = 250
internal const val CouiIndeterminateAnimMs = 1200

/**
 * 悬浮控件是否启用 COUI 原厂凝光玻璃材质：
 * 要求 API 34+ 硬件渲染链路且宿主已传入有效的 [CouiBackdrop]。
 */
internal fun couiFloatingSurfaceEnabled(sdkInt: Int, hasBackdrop: Boolean): Boolean =
    sdkInt >= CouiLibraryNavBarMinSdk && hasBackdrop

internal fun couiContourSurfaceEnabled(sdkInt: Int): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU

/**
 * COUI 原厂凝光轮廓材质修饰符：
 * - API 33+ 通过 [CouiContour] 运行时着色器在底色上叠加柔光边缘与内高光（同 `DialogButton`）；
 * - API 31–32 退化为纯底色（按需叠加极细描边）。
 */
@Composable
internal fun Modifier.couiContourSurface(
    shape: Shape,
    surfaceColor: Color,
    cornerRadius: Dp,
    cornerWeight: Float = 2f,
    style: CouiContourStyle = CouiContourStyle.Button,
    dark: Boolean = CouiTheme.isDark,
    emphasized: Boolean = false,
    edgeAlpha: Float = when {
        emphasized -> if (dark) 0.24f else 0.16f
        dark -> 0.14f
        else -> 0.05f
    },
    innerGlowAlpha: Float = when {
        emphasized -> if (dark) 0.10f else 0.06f
        dark -> 0.04f
        else -> 0.02f
    },
    edgeWidthDp: Float = if (emphasized) 2.5f else 2.2f,
    fallbackBorderColor: Color = Color.Transparent,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !couiContourSurfaceEnabled(Build.VERSION.SDK_INT)) {
        return this
            .clip(shape)
            .background(surfaceColor)
            .then(
                if (fallbackBorderColor.alpha > 0f) {
                    Modifier.border(0.5.dp, fallbackBorderColor, shape)
                } else {
                    Modifier
                },
            )
    }

    return couiContourSurface33(
        shape = shape,
        surfaceColor = surfaceColor,
        cornerRadius = cornerRadius,
        cornerWeight = cornerWeight,
        style = style,
        edgeAlpha = edgeAlpha,
        innerGlowAlpha = innerGlowAlpha,
        edgeWidthDp = edgeWidthDp,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.couiContourSurface33(
    shape: Shape,
    surfaceColor: Color,
    cornerRadius: Dp,
    cornerWeight: Float,
    style: CouiContourStyle,
    edgeAlpha: Float,
    innerGlowAlpha: Float,
    edgeWidthDp: Float,
): Modifier {
    val contourGlow = LocalCouiContourGlowEnabled.current
    val contour = remember(contourGlow, style) {
        if (contourGlow) CouiContour(floating = false, style = style) else null
    }
    val shaderPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val density = LocalDensity.current.density
    val cornerRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() }

    return this
        .clip(shape)
        .drawWithContent {
            drawRect(surfaceColor)
            if (contour != null && size.width > 0f && size.height > 0f) {
                val resolvedRadius = minOf(cornerRadiusPx, size.minDimension / 2f)
                val resolvedWeight = if (style == CouiContourStyle.Button && size.width == size.height) {
                    2f
                } else {
                    cornerWeight
                }
                shaderPaint.shader = contour.shader(
                    size = size,
                    density = density,
                    radiusPx = resolvedRadius,
                    cornerWeight = resolvedWeight,
                    surfaceColor = Color.Transparent,
                    innerGlowAlpha = innerGlowAlpha,
                    lineAlpha = edgeAlpha,
                    effectAlpha = 1f,
                    lineWidthPx = edgeWidthDp * density,
                )
                drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, shaderPaint)
            }
            drawContent()
        }
}

/**
 * 弹层内长列表的最大高度：手机断点以下保持既有设计值，大屏放宽到窗口可用高度。
 *
 * COUI-Kit 的弹层表面自己会按 632dp / 横屏 306dp 封顶（库内 heightIn），这里只把页面里
 * 写死的 420/320dp 抬到「窗口高度」这一档，让章节、分类这类长列表在平板上不再只占小半屏；
 * 矮横屏下仍由库的封顶生效，返回值偏大也无害（父约束会先收紧）。
 *
 * 判定用 contentMaxWidth 是否为 null（600dp 断点）而不是 dialogMaxHeight：后者在手机竖屏
 * 也会算出 0.6×高，直接放开会把手机上的弹层列表一并撑高，超出本次改造范围。
 */
@Composable
fun couiDialogListMaxHeight(compactMaxHeight: Dp): Dp {
    val wide = LocalWideLayout.current
    if (wide.contentMaxWidth == null) return compactMaxHeight
    val wideMax = wide.dialogMaxHeight ?: return compactMaxHeight
    return wideMax
        .coerceAtMost(CouiDialogDefaults.maxHeight)
        .coerceAtLeast(compactMaxHeight)
}
