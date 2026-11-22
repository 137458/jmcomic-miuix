package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiSelectedContainerAlpha
import com.jm3.app.miuix.ui.coui.component.LocalCouiBlurEnabled
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceColor
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.shouldUseCouiBlur
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.core.model.Chapter
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.FloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.FloatingToolbarDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.TopAppBarGlow
import io.github.suqi8.coui.kmp.blur.TopAppBarGlowDefaults
import io.github.suqi8.coui.kmp.blur.ViewBlurDefaults
import io.github.suqi8.coui.kmp.blur.couiViewBlur
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.squircleBorder
import io.github.suqi8.coui.kmp.theme.COUIRoundCorner
import io.github.suqi8.coui.kmp.theme.COUITheme

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

/** HUD 胶囊形状：用于底栏外壳、动作槽与章节步进按钮。 */
private val ReaderHudCapsuleShape = SquircleShape(
    cornerRadius = COUIRoundCorner.Full,
    weight = COUIRoundCorner.CapsuleWeight,
)

/**
 * 漫画与小说阅读器共用的 COUI 17 顶部 HUD 顶栏：
 * 基于 [SmallTopAppBar] 与 [TopAppBarGlow] 渐进凝光模糊（受 [LocalCouiBlurEnabled] 与 [isRuntimeShaderSupported] 门控）。
 */
@Composable
internal fun BoxScope.ReaderTopAppBarShell(
    visible: Boolean,
    backdrop: LayerBackdrop?,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (!visible) return
    val colors = COUITheme.colorScheme
    val blurActive = shouldUseCouiBlur(
        blurEnabled = LocalCouiBlurEnabled.current,
        hasBackdrop = backdrop != null,
        runtimeShaderSupported = isRuntimeShaderSupported(),
    )
    val barColor = if (blurActive) {
        Color.Transparent
    } else {
        colors.backgroundWithCard.copy(alpha = ReaderHudFallbackAlpha)
    }
    val thresholdPx = with(LocalDensity.current) { TopAppBarGlowDefaults.AlphaScrollThreshold.toPx() }
    val glow: (@Composable BoxScope.() -> Unit)? = if (blurActive && backdrop != null) {
        {
            TopAppBarGlow(
                backdrop = backdrop,
                scrollOffsetPx = thresholdPx,
                isDark = colors.isDark,
                baseColor = colors.backgroundWithCard,
                enabled = true,
            )
        }
    } else {
        null
    }
    SmallTopAppBar(
        title = title,
        modifier = modifier.align(Alignment.TopCenter).fillMaxWidth(),
        subtitle = subtitle,
        color = barColor,
        titleColor = colors.labelPrimary,
        subtitleColor = colors.labelSecondaryVariant,
        dividerColor = colors.divider,
        showDivider = false,
        glow = glow,
        navigationIcon = {
            CouiPageTopBarBackButton(onClick = onBack)
        },
        actions = actions,
    )
}

/**
 * HUD 浮动底栏的统一胶囊外壳：
 * 当 [LocalCouiBlurEnabled] 开启且 [isRuntimeShaderSupported] 满足时，优先通过 [LocalMaterialBackdrop]
 * 或 [Modifier.couiViewBlur] 启用 COUI 17 底栏材质模糊（`TYPE_FRAMEWORK_BOTTOM_BAR`）；
 * 否则回退为半透明 [Card] 胶囊（保留投影与柔和描边）。
 */
@Composable
internal fun ReaderHudBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    height: Dp = ReaderHudBottomHeight,
    content: @Composable () -> Unit,
) {
    val colors = COUITheme.colorScheme
    val dark = colors.isDark
    val capsuleShape = remember { ReaderHudCapsuleShape }
    val blurActive = shouldUseCouiBlur(
        blurEnabled = LocalCouiBlurEnabled.current,
        hasBackdrop = backdrop != null,
        runtimeShaderSupported = isRuntimeShaderSupported(),
    )
    val materialBackdrop = if (blurActive) LocalMaterialBackdrop.current else null
    val blurModifier = when {
        !blurActive -> Modifier
        materialBackdrop != null -> remember(materialBackdrop, dark, capsuleShape) {
            materialBackdrop.materialBackdrop(
                FloatingNavigationBarDefaults.backdropSpec(dark),
                capsuleShape,
            )
        }
        backdrop != null -> Modifier.couiViewBlur(
            backdrop = backdrop,
            style = ViewBlurDefaults.frameworkBottomBar(dark),
            shape = capsuleShape,
        )
        else -> Modifier
    }
    val fallbackColor = colors.bar.copy(alpha = ReaderHudFallbackAlpha)
    val borderColor = colors.labelPrimary.copy(alpha = if (dark) 0.14f else 0.08f)

    Box(
        modifier = modifier
            .heightIn(min = height)
            .dropShadow(
                shape = capsuleShape,
                shadow = Shadow(
                    radius = FloatingToolbarDefaults.ShadowElevation,
                    color = Color.Black,
                    alpha = if (dark) 0.45f else 0.16f,
                ),
            )
            .then(blurModifier)
            .pointerInput(Unit) {
                detectTapGestures { /* 吞掉胶囊内部空白区点按，避免误触收起 HUD */ }
            },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .squircleBorder(
                    width = 0.5.dp,
                    color = borderColor,
                    cornerRadius = COUIRoundCorner.Full,
                    weight = COUIRoundCorner.CapsuleWeight,
                ),
            cornerRadius = COUIRoundCorner.Full,
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(
                color = if (blurActive) Color.Transparent else fallbackColor,
                contentColor = colors.labelPrimary,
                pressedColor = if (blurActive) Color.Transparent else fallbackColor,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = height),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

/** 顶栏的上一话/下一话：图标带极简文本标签以区分左侧返回键，无相邻章时置灰并保持 48dp 触区。 */
@Composable
internal fun ReaderHudChapterStep(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = COUITheme.colorScheme
    val tint = if (enabled) {
        colors.labelPrimary
    } else {
        colors.labelPrimary.copy(alpha = 0.3f)
    }
    Column(
        modifier = Modifier
            .couiPressable(
                shape = ReaderHudCapsuleShape,
                enabled = enabled,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = tint)
        Text(
            text = label,
            style = COUITheme.textStyles.bodyXS,
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
    val colors = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .couiPressable(
                shape = CircleShape,
                backgroundColor = couiDialogSurfaceColor(elevated = true),
                enabled = enabled,
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
            tint = if (enabled) colors.labelPrimary else colors.labelPrimary.copy(alpha = 0.3f),
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
    val colors = COUITheme.colorScheme
    val tint = when {
        !enabled -> colors.labelPrimary.copy(alpha = 0.3f)
        active -> colors.primaryText
        else -> colors.labelPrimary
    }
    val containerColor = if (active && enabled) {
        colors.primary.copy(alpha = CouiSelectedContainerAlpha)
    } else {
        Color.Transparent
    }
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .couiPressable(
                shape = ReaderHudCapsuleShape,
                backgroundColor = containerColor,
                enabled = enabled,
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
            style = COUITheme.textStyles.bodyXS,
            color = tint,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
        )
    }
}

/** HUD 顶栏：返回、标题、页码入口与上下话步进，基于 [SmallTopAppBar] 与 [TopAppBarGlow] 渐进模糊。 */
@Composable
internal fun BoxScope.ReaderTopHudBar(
    visible: Boolean,
    backdrop: LayerBackdrop?,
    title: String,
    pageLabel: String,
    onBack: () -> Unit,
    onOpenPageJump: () -> Unit,
    prevChapter: Chapter?,
    nextChapter: Chapter?,
    onOpenChapter: (String) -> Unit,
) {
    ReaderTopAppBarShell(
        visible = visible,
        backdrop = backdrop,
        title = title,
        onBack = onBack,
    ) {
        Text(
            text = pageLabel,
            style = COUITheme.textStyles.buttonM,
            color = COUITheme.colorScheme.primaryText,
            modifier = Modifier
                .couiPressable(
                    shape = ReaderHudCapsuleShape,
                    minTouchTarget = CouiMinTouchTarget,
                    onClick = onOpenPageJump,
                )
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

/** HUD 底栏：等分槽位匹配超椭圆胶囊指示器与触区。 */
@Composable
internal fun BoxScope.ReaderBottomHudBar(
    visible: Boolean,
    backdrop: LayerBackdrop?,
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
