package com.jm3.app.miuix.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.SearchHeaderCollapseLogic
import com.jm3.app.miuix.ui.SearchHeaderCollapseState
import com.jm3.app.miuix.ui.SearchHeaderIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search

/** 向下滚动累计超过该净位移才开始收起，避免轻触与抖动误收起。 */
private val CollapseScrollThreshold = 24.dp

/** 向上滚动累计超过该净位移才开始展开；小于折叠阈值形成滞回，快速来回甩动不会反复翻转。 */
private val ExpandScrollThreshold = 16.dp

/** 折叠末段才开始淡出，前半程保持实心，避免文字被裁掉时出现半透明残影。 */
private const val CollapseFadeStartProgress = 0.7f

/** 吸附弹簧：阻尼略高、刚度中低，跟手松手后一次到位，不来回弹。 */
private val SearchHeaderSettleSpec = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * 可折叠搜索栏状态：滚动增量驱动 [progress]，焦点/搜索展开时锁定为展开态。
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
    /** 交给 `InputField` 观察聚焦；聚焦即锁定展开，键盘弹出期间不收起。 */
    val interactionSource = MutableInteractionSource()

    /** 展开态实测高度：折叠净空与位移都按它换算。首次测量为完整高度，之后只增不减适配字体缩放。 */
    private var expandedHeightPx by mutableIntStateOf(0)

    private var collapseState by mutableStateOf(SearchHeaderCollapseState())
    private var locked by mutableStateOf(false)
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
        if (locked || deltaPx == 0f || expandedHeightPx <= 0) return 0f
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
            locked = locked,
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

    internal fun setLocked(value: Boolean) {
        if (locked == value) return
        locked = value
        if (value) {
            // 聚焦/展开搜索时立即确定展开方向，视觉进度由弹簧补齐，键盘弹起期间保持可见。
            collapseState = SearchHeaderCollapseLogic.forceExpanded(collapseState)
            animateTo(0f)
        }
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
 * @param expanded 搜索框展开态；展开时与聚焦一样锁定为展开，防止结果页刚提交就收起。
 * @param listState 列表状态；回到顶部时强制展开，覆盖下拉刷新回弹与程序化滚动的尾部。
 */
@Composable
fun rememberCollapsibleSearchState(
    expanded: Boolean,
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
    val focused by state.interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused, expanded) {
        state.setLocked(focused || expanded)
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
 * 可折叠搜索栏插槽：按 [CollapsibleSearchState.progress] 在测量阶段收缩高度并裁剪，
 * 内容随之上移，不产生空位；末段淡出，避免半截文字硬裁。
 */
@Composable
fun CollapsibleSearchBar(
    state: CollapsibleSearchState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .collapseSlot(state)
            .onSizeChanged { state.onMeasuredHeight(it.height) },
    ) {
        content()
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

/** 在测量阶段收缩插槽：阅读 progress 只触发重新测量，滚动不重组。 */
private fun Modifier.collapseSlot(state: CollapsibleSearchState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val fullHeight = placeable.height
    val keepHeight = (fullHeight * (1f - state.progress)).roundToInt().coerceIn(0, fullHeight)
    layout(placeable.width, keepHeight) {
        // 内容整体上移被裁掉的高度：上沿滑入顶栏下方，下沿与后续页签贴合。
        placeable.placeWithLayer(0, keepHeight - fullHeight) {
            val fade = ((state.progress - CollapseFadeStartProgress) /
                (1f - CollapseFadeStartProgress)).coerceIn(0f, 1f)
            alpha = 1f - fade
        }
    }
}
