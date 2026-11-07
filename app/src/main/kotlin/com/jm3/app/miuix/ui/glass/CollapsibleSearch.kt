package com.jm3.app.miuix.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm3.app.miuix.ui.EdgeFadeWidth
import com.jm3.app.miuix.ui.SearchHeaderCollapseLogic
import com.jm3.app.miuix.ui.SearchHeaderCollapseState
import com.jm3.app.miuix.ui.SearchHeaderIntent
import com.jm3.app.miuix.ui.edgeFadeMask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 向下滚动累计超过该净位移才开始收起，避免轻触与抖动误收起。 */
private val CollapseScrollThreshold = 24.dp

/** 向上滚动累计超过该净位移才开始展开；小于折叠阈值形成滞回，快速来回甩动不会反复翻转。 */
private val ExpandScrollThreshold = 16.dp

/** 收起进度过该阈值即释放输入焦点：此时插槽开始明显收缩，输入框不该继续持有光标。 */
private const val CollapseFocusReleaseProgress = 0.02f

/** 吸附弹簧：阻尼略高、刚度中低，跟手松手后一次到位，不来回弹。 */
private val SearchHeaderSettleSpec = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * 可折叠搜索栏状态：滚动增量驱动 [progress]。
 *
 * 不因聚焦锁定展开：键盘弹出时继续下滑也要能收起搜索栏；收起时的焦点释放
 * 由 [CollapsibleSearchBar] 统一处理，页面只负责跳转前主动收键盘。
 *
 * 进度为 0（完整可见）到 1（完全收起）。状态读取只发生在布局与绘制阶段，
 * 滚动过程只触发重新测量与重绘，不触发页面重组。
 */
@Stable
class CollapsibleSearchState internal constructor(
    private val scope: CoroutineScope,
    private val collapseThresholdPx: Float,
    private val expandThresholdPx: Float,
) {
    /** 交给 `InputField` 观察聚焦；[CollapsibleSearchBar] 用它判断收起时是否需要释放焦点。 */
    val interactionSource = MutableInteractionSource()

    /** 展开态实测高度：折叠净空与位移都按它换算。首次测量为完整高度，之后只增不减适配字体缩放。 */
    private var expandedHeightPx by mutableIntStateOf(0)

    private var collapseState by mutableStateOf(SearchHeaderCollapseState())
    private var animationJob: Job? = null

    /** 当前折叠进度，布局阶段读取。 */
    var progress by mutableFloatStateOf(0f)
        private set

    /** 内容顶部应让出的折叠高度，[GlassScaffold] 用它抵消搜索栏收起后的空白。 */
    val collapsedHeightPx: Float get() = progress * expandedHeightPx

    /** 完全收起后才显示再唤入口，半折叠状态不闪图标。 */
    private val collapsedFlag = derivedStateOf {
        collapseState.intent == SearchHeaderIntent.Collapsed && progress >= 0.95f
    }
    val isCollapsed: Boolean get() = collapsedFlag.value

    internal fun onMeasuredHeight(heightPx: Int) {
        if (heightPx > expandedHeightPx) expandedHeightPx = heightPx
    }

    internal fun onScrollDelta(deltaPx: Float): Float {
        if (deltaPx == 0f || expandedHeightPx <= 0) return 0f
        animationJob?.cancel()
        // 吸附动画被打断时，以当前视觉进度为准继续累计，避免状态跳回动画起点。
        val before = progress
        if (collapseState.progress != before) {
            collapseState = collapseState.copy(progress = before)
        }
        val next = SearchHeaderCollapseLogic.onScroll(
            state = collapseState,
            deltaPx = deltaPx,
            heightPx = expandedHeightPx.toFloat(),
            locked = false,
            collapseThresholdPx = collapseThresholdPx,
            expandThresholdPx = expandThresholdPx,
        )
        collapseState = next
        progress = next.progress
        // 返回搜索栏实际移动的像素：只消费这部分，剩余滚动继续交给列表。
        val consumed = -(next.progress - before) * expandedHeightPx
        return if (deltaPx < 0f) consumed.coerceIn(deltaPx, 0f) else consumed.coerceIn(0f, deltaPx)
    }

    internal fun settleAfterScroll(velocityPxPerSecond: Float) {
        val target = SearchHeaderCollapseLogic.settleTarget(
            state = collapseState,
            velocityPxPerSecond = velocityPxPerSecond,
            locked = false,
        )
        val intent = SearchHeaderCollapseLogic.intentForTarget(target)
        collapseState = collapseState.copy(
            progress = progress,
            intent = intent,
            collapseDebtPx = 0f,
            expandDebtPx = 0f,
        )
        animateTo(target)
    }

    /** 再唤入口、回到列表顶部时手动展开。 */
    fun expand() {
        collapseState = SearchHeaderCollapseLogic.forceExpanded(collapseState)
        animateTo(0f)
    }

    private fun animateTo(target: Float) {
        animationJob?.cancel()
        animationJob = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                animationSpec = SearchHeaderSettleSpec,
            ) { value, _ ->
                progress = value
            }
            val intent = SearchHeaderCollapseLogic.intentForTarget(target)
            collapseState = collapseState.copy(progress = target, intent = intent)
        }
    }
}

/**
 * 创建可折叠搜索栏状态。
 *
 * @param listState 列表状态；回到顶部时强制展开，覆盖下拉刷新回弹与程序化滚动的尾部。
 */
@Composable
fun rememberCollapsibleSearchState(
    listState: LazyListState? = null,
): CollapsibleSearchState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state = remember(density) {
        CollapsibleSearchState(
            scope = scope,
            collapseThresholdPx = with(density) { CollapseScrollThreshold.roundToPx().toFloat() },
            expandThresholdPx = with(density) { ExpandScrollThreshold.roundToPx().toFloat() },
        )
    }
    LaunchedEffect(listState, state) {
        val lazyState = listState ?: return@LaunchedEffect
        snapshotFlow { lazyState.firstVisibleItemIndex to lazyState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index == 0 && offset == 0) state.expand()
            }
    }
    return state
}

/**
 * 可折叠搜索栏插槽：按 [CollapsibleSearchState.progress] 在测量阶段收缩高度，
 * 内容随之上移并整体淡出，不产生空位，也不在收起途中留下一条硬切边。
 */
@Composable
fun CollapsibleSearchBar(
    state: CollapsibleSearchState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focused by state.interactionSource.collectIsFocusedAsState()
    // 搜索栏被滚走时释放焦点并落键盘：只收高度不清焦点的话，输入框隐形但仍持有焦点，
    // 键盘会悬在半空，任何一次状态刷新还会把光标拉回原位置。
    LaunchedEffect(state, focused) {
        if (!focused) return@LaunchedEffect
        snapshotFlow { state.progress > CollapseFocusReleaseProgress }
            .distinctUntilChanged()
            .collect { collapsing ->
                if (collapsing) {
                    focusManager.clearFocus()
                    keyboard?.hide()
                }
            }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            // 羽化层必须在收缩层外侧：它按插槽的实际高度铺遮罩，放在内侧会拿到展开前的整高
            .collapseEdgeFade(state)
            .collapseSlot(state)
            .onSizeChanged { state.onMeasuredHeight(it.height) },
    ) {
        content()
    }
}

/**
 * 顶栏搜索输入框：最小 40dp 高、16sp、圆角胶囊，背后铺一层 [GlassRole.SearchField] 玻璃。
 *
 * 高度只设下限：字体放大到 1.5–2.0 倍时由内容撑开，不裁字；折叠行程按实测高度换算，
 * 展开高度变化不会导致收起到展开的距离与实际高度不一致。
 *
 * 玻璃放在 InputField 之后绘制（Box 内先测量后叠层），InputField 自身底色改为透明，
 * 文字、占位符与光标直接落在 0.72 底衬上；降级时底衬依次抬到 0.88 与不透明。
 */
@Composable
fun GlassSearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    interactionSource: MutableInteractionSource,
    label: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    glassSurface(
                        backdrop = LocalGlassBackdrop.current,
                        role = GlassRole.SearchField,
                        tint = MiuixTheme.colorScheme.surfaceContainer,
                        shape = CircleShape,
                    ),
                ),
        )
        InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            interactionSource = interactionSource,
            label = label,
            trailingIcon = trailingIcon,
            textStyle = TextStyle(fontSize = 16.sp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        )
    }
}

/** 折叠后的再唤入口：放进 `GlassTopAppBar` 的 actions，完全收起后才出现。 */
@Composable
fun CollapsibleSearchAction(
    state: CollapsibleSearchState,
    modifier: Modifier = Modifier,
) {
    if (!state.isCollapsed) return
    IconButton(onClick = state::expand, modifier = modifier) {
        Icon(imageVector = MiuixIcons.Search, contentDescription = "展开搜索栏")
    }
}

/**
 * 消费搜索栏折叠所需的滚动位移，剩余位移继续交给列表；因此搜索栏收起时内容会与它同步上移，
 * 不会出现列表再滚一遍的双倍位移。挂在列表本身上（下拉刷新节点内侧），保证到顶回拉时
 * 先展开搜索栏，不会在展开前误触发刷新。
 *
 * 下拉刷新的向下位移在搜索栏已展开时不会被消费，刷新手势与原有顶栏模糊淡入均保持不变。
 */
@Composable
fun Modifier.collapsibleSearchScroll(state: CollapsibleSearchState): Modifier {
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val consumedY = state.onScrollDelta(available.y)
                return Offset(0f, consumedY)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                state.settleAfterScroll(available.y)
                return Velocity.Zero
            }
        }
    }
    return nestedScroll(connection)
}

/**
 * 在测量阶段收缩插槽：阅读 progress 只触发重新测量，滚动不重组。
 *
 * 只缩高度会在搜索框中间拦腰切一刀，因此同时按进度整体淡出：
 * 被裁掉的部分已经接近透明，收起过程读作「向上滑出」而不是「被剪掉」。
 */
private fun Modifier.collapseSlot(state: CollapsibleSearchState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val fullHeight = placeable.height
    val keepHeight = (fullHeight * (1f - state.progress)).roundToInt().coerceIn(0, fullHeight)
    layout(placeable.width, keepHeight) {
        // 内容整体上移被裁掉的高度：上沿滑入顶栏下方，下沿与后续页签贴合。
        placeable.placeWithLayer(0, keepHeight - fullHeight) {
            alpha = (1f - state.progress).coerceIn(0f, 1f)
        }
    }
}

/**
 * 裁切线的上缘羽化：羽化带宽度随进度从 0 增长到 [EdgeFadeWidth]，
 * 展开完成时归零，完全展开的搜索框顶边保持清晰；遮罩底座与列表/弹窗羽化共用 [edgeFadeMask]。
 */
private fun Modifier.collapseEdgeFade(state: CollapsibleSearchState): Modifier = edgeFadeMask {
    val progress = state.progress
    if (progress <= 0f || size.height <= 0f) {
        Brush.verticalGradient(0f to Color.Black, 1f to Color.Black)
    } else {
        val band = (EdgeFadeWidth.toPx() * progress).coerceAtMost(size.height)
        Brush.verticalGradient(
            0f to Color.Transparent,
            (band / size.height).coerceIn(0f, 1f) to Color.Black,
            1f to Color.Black,
        )
    }
}
