// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.CouiTopAppBarScrollBehavior
import com.itos.coui.topappbar.LargeTopAppBar
import com.itos.coui.topappbar.TopAppBarButtonGroup
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.couiPageExtraBottomDp
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.FabPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SegmentButton
import io.github.suqi8.coui.kmp.basic.SegmentButtonDefaults
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.ToolbarPosition
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchState
import io.github.suqi8.coui.kmp.basic.glowScrollOffsetPx
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.TopAppBarGlow
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

// 顶栏按钮尺寸与 52dp 的 COUI 顶栏配套；图标沿用 22dp，和主框架底栏图标同档。
private val CouiTopBarButtonSize = 40.dp
private val CouiTopBarIconSize = 22.dp

/** [CouiPageScaffold] 支持的顶栏版式。 */
enum class CouiTopBarStyle {
    /** 折叠大标题；当 `wide = true` 时切换为固定小顶栏。 */
    Large,

    /** 始终固定小顶栏。 */
    Small,

    /** ColorOS 设置首页风格：116dp 高度、40sp 大标题、24dp/18dp 边距。 */
    SettingsHome,
}

/** [CouiPageTopBar] 的色槽集合，与 `suqi8/coui` [TopAppBar] / [SmallTopAppBar] 对齐。 */
@Immutable
data class CouiPageTopBarColors(
    val backgroundColor: Color,
    val titleColor: Color,
    val largeTitleColor: Color,
    val subtitleColor: Color,
    val dividerColor: Color,
)

/**
 * 返回与当前页面模糊录制状态匹配的新版顶栏配色：
 * 有 [hasBackdrop] 时采用透明底配合 [TopAppBarGlow]（同时抑制底部分隔线），否则退回 [COUITheme.colorScheme.backgroundWithCard] 实底。
 */
@Composable
fun couiPageTopBarColors(
    hasBackdrop: Boolean = LocalPageBackdrop.current != null,
): CouiPageTopBarColors {
    val colors = COUITheme.colorScheme
    return CouiPageTopBarColors(
        backgroundColor = if (hasBackdrop) Color.Transparent else colors.backgroundWithCard,
        titleColor = colors.labelPrimary,
        largeTitleColor = colors.labelPrimary,
        subtitleColor = colors.labelSecondaryVariant,
        dividerColor = colors.divider,
    )
}

/** 记住带弹簧跟随的 COUI 17 顶栏折叠滚动行为。 */
@Composable
fun rememberCouiPageScrollBehavior(canScroll: () -> Boolean = { true }): ScrollBehavior =
    COUIScrollBehavior(canScroll = canScroll)

/** 判断当前窗口是否达到宽屏小顶栏阈值（840dp，或横屏 600dp）。 */
@Composable
fun isCouiWideWindow(): Boolean {
    val wide = LocalWideLayout.current
    if (wide.widthDp > 0 && wide.heightDp > 0) {
        return wide.widthDp >= 840 || (wide.widthDp >= 600 && wide.heightDp < wide.widthDp)
    }
    val size = LocalWindowInfo.current.containerDpSize
    return size.width >= 840.dp || (size.width >= 600.dp && size.height < size.width)
}

/** 列表通用滚动修饰符：叠加 COUI 触底触觉与纵向回弹。 */
fun Modifier.couiPageScroll(scrollBehavior: ScrollBehavior? = null): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical()
    .then(
        if (scrollBehavior != null) {
            Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        } else {
            Modifier
        },
    )

/**
 * 基于 `suqi8/coui` 2.1.0 的二级页面脚手架：折叠/固定顶栏 + 凝光模糊 + 底栏净空 + FAB + 延迟测量正文留白。
 *
 * 1. `nestedScroll` 只在脚手架根部挂一次；
 * 2. [content] 拿到的 [PaddingValues] 在测量期才读 `innerPadding`，跟随顶栏折叠无一帧滞后；
 * 3. 页内录制源通过 [LocalPageBackdrop]、[LocalPopupMaterialBlur] 与 [LocalMaterialBackdrop] 自动桥接。
 */
@Composable
fun CouiPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "返回",
    topBarStyle: CouiTopBarStyle = CouiTopBarStyle.Large,
    wide: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    topBarBottom: @Composable () -> Unit = {},
    topBarSearchState: TopAppBarSearchState? = null,
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    scrollBehavior: ScrollBehavior = rememberCouiPageScrollBehavior(),
    horizontalPadding: Dp? = 0.dp,
    extraBottom: Dp = couiPageExtraBottomDp(
        hasFab = floatingActionButton != null,
        imeOccupiesPage = rememberCouiImeOccupiesPage(),
    ).dp,
    extraTop: Dp = 0.dp,
    imePadding: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    CouiPageScaffold(
        topBar = { backdrop ->
            CouiPageTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                subtitle = subtitle,
                onBack = onBack,
                backContentDescription = backContentDescription,
                style = topBarStyle,
                wide = wide,
                actions = actions,
                searchState = topBarSearchState,
                bottomContent = topBarBottom,
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHostState = snackbarHostState,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        floatingToolbar = floatingToolbar,
        floatingToolbarPosition = floatingToolbarPosition,
        imePadding = imePadding,
    ) { innerPadding ->
        content(
            couiPageContentPadding(
                innerPadding = innerPadding,
                horizontalPadding = horizontalPadding,
                extraBottom = extraBottom,
                extraTop = extraTop,
            ),
        )
    }
}

/**
 * COUI 17 顶栏装配：当 [backdrop] 非空时叠加 [TopAppBarGlow] 凝光，空时自动切为实底。
 */
@Composable
fun CouiPageTopBar(
    title: String,
    scrollBehavior: ScrollBehavior?,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "返回",
    style: CouiTopBarStyle = CouiTopBarStyle.Large,
    wide: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    searchState: TopAppBarSearchState? = null,
    bottomContent: @Composable () -> Unit = {},
) {
    val colors = COUITheme.colorScheme
    val topBarColors = couiPageTopBarColors(hasBackdrop = backdrop != null)
    val glow: (@Composable BoxScope.() -> Unit)? = if (backdrop != null) {
        {
            TopAppBarGlow(
                backdrop = backdrop,
                scrollOffsetPx = scrollBehavior?.glowScrollOffsetPx() ?: 0f,
                isDark = colors.isDark,
                baseColor = colors.backgroundWithCard,
                enabled = true,
            )
        }
    } else {
        null
    }
    val navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            CouiPageTopBarBackButton(
                onClick = onBack,
                contentDescription = backContentDescription,
            )
        }
    }
    if (style == CouiTopBarStyle.Small || (wide && style == CouiTopBarStyle.Large)) {
        SmallTopAppBar(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            color = topBarColors.backgroundColor,
            titleColor = topBarColors.titleColor,
            subtitleColor = topBarColors.subtitleColor,
            dividerColor = topBarColors.dividerColor,
            scrollBehavior = scrollBehavior,
            glow = glow,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    } else {
        val home = style == CouiTopBarStyle.SettingsHome
        TopAppBar(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            color = topBarColors.backgroundColor,
            titleColor = topBarColors.titleColor,
            largeTitleColor = topBarColors.largeTitleColor,
            subtitleColor = topBarColors.subtitleColor,
            dividerColor = topBarColors.dividerColor,
            scrollBehavior = scrollBehavior,
            expandedHeight = if (home) 116.dp else TopAppBarDefaults.ExpandedHeight,
            largeTitleFontSize = if (home) 40.sp else TopAppBarDefaults.largeTitleFontSize(),
            titlePadding = if (home) 24.dp else Dp.Unspecified,
            largeTitleBottomMargin = if (home) 18.dp else Dp.Unspecified,
            glow = glow,
            navigationIcon = navigationIcon,
            actions = actions,
            searchState = searchState,
            bottomContent = bottomContent,
        )
    }
}

/**
 * 延迟读取 [innerPadding] 的页面正文留白：`suqi8/coui` 的 `Scaffold` 在测量阶段更新 `innerPadding`，
 * 这里仅做轻量代理，保证列表滚动与顶栏折叠同帧同步。
 */
@Composable
fun couiPageContentPadding(
    innerPadding: PaddingValues,
    horizontalPadding: Dp? = 0.dp,
    extraBottom: Dp = couiPageExtraBottomDp(
        hasFab = false,
        imeOccupiesPage = rememberCouiImeOccupiesPage(),
    ).dp,
    extraTop: Dp = 0.dp,
): PaddingValues {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return remember(innerPadding, navBottom, horizontalPadding, extraBottom, extraTop) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                horizontalPadding ?: innerPadding.calculateLeftPadding(layoutDirection)

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                horizontalPadding ?: innerPadding.calculateRightPadding(layoutDirection)

            override fun calculateTopPadding(): Dp = innerPadding.calculateTopPadding() + extraTop

            override fun calculateBottomPadding(): Dp =
                maxOf(innerPadding.calculateBottomPadding(), navBottom) + extraBottom
        }
    }
}

/** 新版 COUI 17 顶栏返回键：使用 [IconButton] 与 [COUIIcons.Back]。 */
@Composable
fun CouiPageTopBarBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "返回",
    tint: Color = COUITheme.colorScheme.labelPrimary,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = COUIIcons.Back,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/** 新版 COUI 17 顶栏动作按钮：使用 [IconButton] 与 [Icon]。 */
@Composable
fun CouiPageTopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = COUITheme.colorScheme.labelPrimary,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(CouiTopBarIconSize),
            tint = tint,
        )
    }
}

/** 新版 COUI 17 自定义工具栏标题组件（用于独立 HUD 或自绘标题栏）。 */
@Composable
fun CouiPageTopBarTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = COUITheme.colorScheme.labelPrimary,
    textAlign: TextAlign? = null,
) {
    val style = couiScaledStyle(COUITheme.textStyles.headlineS)
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = if (textAlign == null) style else style.copy(textAlign = textAlign),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 新版 COUI 17 顶栏下方的分段胶囊栏：基于官方 [SegmentButton]（带滑选弹簧、触觉与响应宽度）。 */
@Composable
fun CouiPageTopBarSegmentedTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    if (items.isEmpty()) return
    val clampedIndex = selectedIndex.coerceIn(0, items.lastIndex)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        SegmentButton(
            items = items,
            selectedIndex = clampedIndex,
            onSelectedIndexChange = onSelect,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            height = if (small) SegmentButtonDefaults.HeightSmall else SegmentButtonDefaults.Height,
            padding = if (small) SegmentButtonDefaults.PaddingSmall else SegmentButtonDefaults.Padding,
        )
    }
}

/**
 * 过渡期旧版二级页面骨架：保留供未迁移页面使用，Wave 3 移除。
 */
@Deprecated("Wave 3 removal")
@Suppress("DEPRECATION")
@Composable
fun CouiScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "返回",
    actions: (@Composable RowScope.() -> Unit)? = null,
    topBarBottom: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable BoxScope.() -> Unit)? = null,
    scrollBehavior: CouiTopAppBarScrollBehavior = rememberCouiScreenScrollBehavior(),
    horizontalPadding: Dp = 0.dp,
    extraBottom: Dp = couiPageExtraBottomDp(
        hasFab = floatingActionButton != null,
        imeOccupiesPage = rememberCouiImeOccupiesPage(),
    ).dp,
    content: @Composable (PaddingValues) -> Unit,
) {
    CouiAppScaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentBackdrop = null,
        topBar = {
            val topAppBar = @Composable {
                LargeTopAppBar(
                    title = { CouiTopBarTitle(text = title) },
                    navigationIcon = {
                        if (onBack != null) {
                            CouiTopBarBackButton(onClick = onBack, contentDescription = backContentDescription)
                        }
                    },
                    actions = {
                        val slot = actions
                        if (slot != null) {
                            TopAppBarButtonGroup { slot() }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = couiTopAppBarColors(),
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                topAppBar()
                if (topBarBottom != null) topBarBottom()
            }
        },
        floatingActionButton = floatingActionButton,
    ) { innerPadding ->
        content(couiScreenContentPadding(scrollBehavior, innerPadding, horizontalPadding, extraBottom))
    }
}

/**
 * 固定在顶栏下方的分段胶囊栏：委托至新版 [CouiPageTopBarSegmentedTabs]。
 */
@Composable
fun CouiTopBarSegmentedTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    CouiPageTopBarSegmentedTabs(
        items = items,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
        small = small,
        enabled = enabled,
    )
}

/**
 * 过渡期旧版折叠行为记忆函数，Wave 3 移除。
 */
@Deprecated("Wave 3 removal")
@Composable
fun rememberCouiScreenScrollBehavior(canScroll: () -> Boolean = { true }): CouiTopAppBarScrollBehavior =
    CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(canScroll)

/**
 * 过渡期旧版折叠留白换算，Wave 3 移除。
 */
@Deprecated("Wave 3 removal")
@Composable
fun couiScreenContentPadding(
    scrollBehavior: CouiTopAppBarScrollBehavior,
    innerPadding: PaddingValues,
    horizontalPadding: Dp? = 0.dp,
    extraBottom: Dp = couiPageExtraBottomDp(
        hasFab = false,
        imeOccupiesPage = rememberCouiImeOccupiesPage(),
    ).dp,
): PaddingValues {
    val density = LocalDensity.current
    return remember(scrollBehavior, innerPadding, density, horizontalPadding, extraBottom) {
        val corrected = scrollBehavior.state.contentPadding(innerPadding, density)
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                horizontalPadding ?: corrected.calculateLeftPadding(layoutDirection)

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                horizontalPadding ?: corrected.calculateRightPadding(layoutDirection)

            override fun calculateTopPadding(): Dp = corrected.calculateTopPadding()

            override fun calculateBottomPadding(): Dp = corrected.calculateBottomPadding() + extraBottom
        }
    }
}

/**
 * 在已有 [PaddingValues] 上追加统一的底部安全留白，供未使用 [couiPageContentPadding] 的页面复用。
 */
fun PaddingValues.couiWithBottomPadding(extra: Dp): PaddingValues = object : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        this@couiWithBottomPadding.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = this@couiWithBottomPadding.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        this@couiWithBottomPadding.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp =
        this@couiWithBottomPadding.calculateBottomPadding() + extra
}

/**
 * 顶栏返回键：已迁移至 `suqi8/coui` [IconButton]（沿用 [CouiIcons.Back] 兼容现有页布局）。
 */
@Composable
fun CouiTopBarBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "返回",
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = CouiIcons.Back,
            contentDescription = contentDescription,
            modifier = Modifier.size(CouiTopBarIconSize),
            tint = COUITheme.colorScheme.labelPrimary,
        )
    }
}

/**
 * 自绘圆键形态的返回键：40dp 圆形触区（外扩到 48dp 最小触区），供自带浮动材质的外壳使用（如阅读器 HUD）。
 */
@Composable
internal fun CouiTopBarPlainBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "返回",
) {
    Box(
        modifier = modifier
            .couiPressable(
                shape = CircleShape,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .size(CouiTopBarButtonSize)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CouiIcons.Back,
            contentDescription = contentDescription,
            modifier = Modifier.size(CouiTopBarIconSize),
            tint = COUITheme.colorScheme.labelPrimary,
        )
    }
}

/** 过渡期重载：兼容显式传入 `spotlight` 的未迁移调用方，Wave 3 移除。 */
@Deprecated("Wave 3 removal")
@Composable
internal fun CouiTopBarPlainBackButton(
    onClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") spotlight: SpotlightPreset?,
    modifier: Modifier = Modifier,
    contentDescription: String = "返回",
) = CouiTopBarPlainBackButton(
    onClick = onClick,
    modifier = modifier,
    contentDescription = contentDescription,
)

/**
 * 顶栏动作按钮：已迁移至 `suqi8/coui` [IconButton]。
 */
@Composable
fun CouiTopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(CouiTopBarIconSize),
            tint = COUITheme.colorScheme.labelPrimary,
        )
    }
}
