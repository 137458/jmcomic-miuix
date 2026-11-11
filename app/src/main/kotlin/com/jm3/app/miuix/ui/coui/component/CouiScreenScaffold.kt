// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.CouiTopAppBarScrollBehavior
import com.itos.coui.topappbar.LargeTopAppBar
import com.itos.coui.topappbar.TopAppBarButton
import com.itos.coui.topappbar.TopAppBarButtonGroup
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.couiPageExtraBottomDp
import com.jm3.app.miuix.ui.readableWidth
import top.yukonga.miuix.kmp.basic.Icon

// 顶栏按钮尺寸与 52dp 的 COUI 顶栏配套；图标沿用 22dp，和主框架底栏图标同档。
private val CouiTopBarButtonSize = 40.dp
private val CouiTopBarIconSize = 22.dp

/**
 * 二级页面骨架：折叠大标题顶栏 + 返回键 + 动作槽 + FAB，并把顶栏折叠位移换算成正文留白。
 *
 * 相比直接拼 [CouiAppScaffold] + [LargeTopAppBar]，这里收口三件事：
 * 1. nestedScroll 只在这里挂一次，页面不再各写一遍（漏挂的表现是滚动时顶栏不动）；
 * 2. [content] 拿到的是已按折叠状态修正、并带上左右内容边距的 [PaddingValues]，
 *    可直接交给 LazyColumn/LazyRow 的 contentPadding，也可用 Modifier.padding；
 *    拿到的必须是修正后的 padding：直接消费 Scaffold 原始 padding 会让正文在顶栏下方留一段死白；
 * 3. 返回键与动作按钮的尺寸、触区、着色统一，避免每页各写一套；[actions] 非空时统一包进
 *    [TopAppBarButtonGroup]，按钮组随顶栏折叠走共享位移。
 *
 * @param onBack 为空表示这是一级页面，不画返回键。
 * @param scrollBehavior 需要按列表滚动范围控制折叠时传入（见 [rememberCouiScreenScrollBehavior]）。
 * @param horizontalPadding 正文左右边距；铺满屏幕的内容（整幅栅格、全出血轮播）传 0.dp。
 */
@OptIn(ExperimentalLayoutApi::class)
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
        isImeVisible = WindowInsets.isImeVisible,
    ).dp,
    content: @Composable (PaddingValues) -> Unit,
) {
    val contentBackdrop = if (topBarBottom != null && Build.VERSION.SDK_INT >= 34) {
        rememberCouiBackdrop()
    } else {
        null
    }
    CouiAppScaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentBackdrop = contentBackdrop,
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
                        // 空槽不建组：组内是右对齐 Row，空组会多占一层布局与一次共享位移
                        val slot = actions
                        if (slot != null) {
                            TopAppBarButtonGroup { slot() }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = couiTopAppBarColors(),
                )
            }
            if (topBarBottom == null) {
                topAppBar()
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    topAppBar()
                    topBarBottom()
                }
            }
        },
        floatingActionButton = floatingActionButton,
    ) { innerPadding ->
        content(couiScreenContentPadding(scrollBehavior, innerPadding, horizontalPadding, extraBottom))
    }
}

/**
 * 固定在折叠大顶栏下方的分段胶囊栏：放置在 `topBar` 槽内以免随 `HorizontalPager` 横向滚走，
 * 并通过 [LocalCouiAppBackdrop] 让 API 34+ 原厂 [CouiSegmentedCapsules] 对正文区产生毛玻璃叠层。
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
    val backdrop = LocalCouiAppBackdrop.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .readableWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin, vertical = 6.dp),
    ) {
        CouiSegmentedCapsules(
            items = items,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            small = small,
            enabled = enabled,
            backdrop = backdrop,
        )
    }
}

/**
 * 记住可恢复的折叠行为；[canScroll] 读取正文的实际滚动范围，短内容时不会把顶栏折叠成收不回来的状态。
 * 库侧每帧用 SideEffect 更新同一份状态，因此传入普通 lambda 即可，不必记忆。
 */
@Composable
fun rememberCouiScreenScrollBehavior(canScroll: () -> Boolean = { true }): CouiTopAppBarScrollBehavior =
    CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(canScroll)

/**
 * 顶栏折叠后的正文留白：Scaffold 的 windowInsets 留白 + 顶栏折叠位移，再补上左右内容边距。
 * 只在页面正文内调用，不要再用返回的 padding 去平移整个视口。
 *
 * 返回值是活对象：顶部留白在测量期才读折叠位移。组合期把 calculateTopPadding() 算成字面量，
 * 会把首次组合那一帧的顶栏高度冻结住——顶栏之后折叠/展开，正文都不再跟随
 * （切 tab 回到页面时，大标题与搜索框就压在正文首项上）。
 *
 * @param horizontalPadding 正文左右边距，默认 0：页面内容一律顶到窗口边缘，网格卡的视觉内衬由卡片自身
 * （[CouiWorkCardTokens]）提供。传 null 才保留 Scaffold 自己的左右净空，仅供确实需要 inset 的整页内容使用。
 * @param extraBottom 页面在底栏净空之外补的尾部间隙。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun couiScreenContentPadding(
    scrollBehavior: CouiTopAppBarScrollBehavior,
    innerPadding: PaddingValues,
    horizontalPadding: Dp? = 0.dp,
    extraBottom: Dp = couiPageExtraBottomDp(
        hasFab = false,
        isImeVisible = WindowInsets.isImeVisible,
    ).dp,
): PaddingValues {
    val density = LocalDensity.current
    // 库侧已把顶部留白与折叠位移合在一起并把负值收敛到 0，这里只补左右边距，不重复做加减
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
 * 在已有 [PaddingValues] 上追加统一的底部安全留白，供未使用 [couiScreenContentPadding] 的页面复用。
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
 * 顶栏返回键：API 34 起用原厂顶栏按钮（自带浮动材质与凝光），低版本退回 40dp 自绘圆键。
 * 页面不要再自己画返回键，低版本与高版本的顶栏边距才对得上。
 * 必须在顶栏的 navigationIcon 槽内调用：原厂按钮要求顶栏效果作用域提供背景源。
 */
@Composable
fun CouiTopBarBackButton(
    onClick: () -> Unit,
    contentDescription: String = "返回",
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk) {
        CouiTopBarBackButton34(onClick = onClick, contentDescription = contentDescription, modifier = modifier)
    } else {
        CouiTopBarPlainBackButton(onClick = onClick, contentDescription = contentDescription, modifier = modifier)
    }
}

@RequiresApi(CouiLibraryNavBarMinSdk)
@Composable
private fun CouiTopBarBackButton34(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    TopAppBarButton(modifier = modifier, onClick = onClick) {
        Icon(
            imageVector = CouiIcons.Back,
            contentDescription = contentDescription,
            modifier = Modifier.size(CouiTopBarIconSize),
        )
    }
}

/**
 * 自绘圆键形态的返回键：40dp 圆形触区（外扩到 48dp 最小触区），图标取 COUI 图标集。
 * 供自带浮动材质的外壳使用（阅读器 HUD 栏）：那里不是 COUI 顶栏，没有背景采样源，
 * 直接用原厂按钮会在取 backdrop 时抛异常。
 */
@Composable
internal fun CouiTopBarPlainBackButton(
    onClick: () -> Unit,
    contentDescription: String = "返回",
    spotlight: SpotlightPreset? = SpotlightPreset.OpaqueSmall2,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .couiPressable(
                shape = CircleShape,
                spotlight = spotlight,
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
            tint = CouiTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 顶栏动作按钮：API 34 起用原厂顶栏按钮（自带浮动材质与触点点亮），低版本退回同等尺寸的自绘图标位。
 * 必须在顶栏的 actions 槽内调用：原厂按钮要求顶栏效果作用域提供背景源。
 */
@Composable
fun CouiTopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk) {
        CouiTopBarAction34(icon = icon, contentDescription = contentDescription, onClick = onClick)
    } else {
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
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(CouiTopBarIconSize),
                tint = CouiTheme.colorScheme.onSurface,
            )
        }
    }
}

@RequiresApi(CouiLibraryNavBarMinSdk)
@Composable
private fun CouiTopBarAction34(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    TopAppBarButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(CouiTopBarIconSize),
        )
    }
}
