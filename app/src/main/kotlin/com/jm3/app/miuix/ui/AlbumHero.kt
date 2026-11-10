package com.jm3.app.miuix.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jm3.core.coverCacheKeyText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.squircle.squircleClip

/**
 * 列表卡片与详情头图的矩形登记表 + 当前飞行状态。
 *
 * miuix-nav 的转场是 graphicsLayer 平移，没有 AnimatedVisibilityScope，Compose 原生
 * Modifier.sharedBounds 无处挂载；所以共享元素自己画：两端把矩形报到 [AlbumHeroHost]，
 * 覆盖层在 NavDisplay 之上按矩形插值画一张图；等它画出首帧（[markOverlayReady]）后，两端真实封面才隐藏。
 */
@Stable
class AlbumHeroHost {
    private val coverRects = mutableStateMapOf<String, HeroRect>()
    private val headerRects = mutableStateMapOf<String, HeroRect>()
    private val coverCorners = mutableStateMapOf<String, Float>()
    private val headerCorners = mutableStateMapOf<String, Float>()
    private val lastHeaderRects = mutableMapOf<String, HeroRect>()
    private val lastHeaderCorners = mutableMapOf<String, Float>()
    private val urls = mutableStateMapOf<String, String>()
    private val titles = mutableStateMapOf<String, String>()
    private var sequence = 0

    /**
     * 已经画出首帧的飞行 id。覆盖层拿到 Bitmap 之前两端封面必须留在原位：
     * 覆盖层空白 + 两端已隐藏 = 一帧空洞，用户看到的就是"封面闪一下"。
     */
    private var overlayReadyFlightId by mutableStateOf<Int?>(null)

    /**
     * 详情头图是否已画出首帧。
     *
     * 飞行落幕后覆盖层立刻消失，此时头图若还没拿到位图，露出的就是一帧空白封面（"闪一下"）。
     * 覆盖层要等到这里为 true 再收场；每次起飞前清掉，避免沿用上一次的旧结果。
     */
    private val headerReady = mutableStateMapOf<String, Boolean>()

    /** 当前这段飞行是否是反向（详情 → 列表）；反向进度由返回手势直接驱动，不走独立动画。 */
    private var backwardFlightId by mutableStateOf<Int?>(null)

    /** 反向飞行进度：0 = 停在详情头图，1 = 落回列表卡片。只由能读到手势进度的详情页写入。 */
    var backwardProgress by mutableFloatStateOf(0f)
        private set

    var state by mutableStateOf(HeroState())
        private set

    /** 已点开详情、等头图测量结果的待飞记录。 */
    val pending: HeroPending? get() = state.pending

    /** 正在播放的飞行；同一时刻最多一个。 */
    val flight: HeroPlan? get() = state.flight

    /** 这段飞行是否是反向飞行（详情页退回列表）。 */
    fun isBackwardFlight(id: Int): Boolean = backwardFlightId == id

    /** 由详情页按返回手势进度写入；只认当前反向飞行，迟到的手势回调不会改动别的飞行。 */
    fun setBackwardProgress(id: Int, progress: Float) {
        if (backwardFlightId != id) return
        backwardProgress = progress.coerceIn(0f, 1f)
    }

    fun rememberAlbum(albumId: String, title: String? = null, coverUrl: String? = null) {
        if (title != null) titles[albumId] = title
        if (coverUrl != null) urls[albumId] = coverUrl
    }

    fun registerCover(
        albumId: String,
        url: String?,
        rect: HeroRect,
        cornerDp: Float,
        title: String? = null,
    ) {
        if (!isFiniteHeroRect(rect)) return
        if (title != null) titles[albumId] = title
        if (coverRects[albumId] == rect && coverCorners[albumId] == cornerDp && (url == null || urls[albumId] == url)) return
        coverRects[albumId] = rect
        coverCorners[albumId] = cornerDp
        if (url != null) urls[albumId] = url
    }

    fun unregisterCover(albumId: String, rect: HeroRect?) {
        if (rect != null && coverRects[albumId] == rect) {
            coverRects.remove(albumId)
            coverCorners.remove(albumId)
            if (albumId !in headerRects) {
                urls.remove(albumId)
                titles.remove(albumId)
            }
        }
    }

    fun registerHeader(albumId: String, url: String?, rect: HeroRect, cornerDp: Float) {
        if (!isFiniteHeroRect(rect)) return
        lastHeaderRects[albumId] = rect
        lastHeaderCorners[albumId] = cornerDp
        if (headerRects[albumId] == rect && headerCorners[albumId] == cornerDp && (url == null || urls[albumId] == url)) return
        headerRects[albumId] = rect
        headerCorners[albumId] = cornerDp
        if (url != null) urls[albumId] = url
    }

    fun unregisterHeader(albumId: String, rect: HeroRect?) {
        if (rect != null && headerRects[albumId] == rect) {
            headerRects.remove(albumId)
            headerCorners.remove(albumId)
            if (albumId !in coverRects) {
                urls.remove(albumId)
                titles.remove(albumId)
            }
        }
    }

    fun coverRect(albumId: String): HeroRect? = coverRects[albumId]

    fun headerRect(albumId: String): HeroRect? = headerRects[albumId]

    fun coverUrl(albumId: String): String? = urls[albumId]

    fun albumTitle(albumId: String): String? = titles[albumId]

    /**
     * 覆盖层把首帧画出来后由它回调；只认当前飞行的 id，上一段飞行的迟到回调不会误隐藏封面。
     */
    fun markOverlayReady(flightId: Int) {
        if (flight?.id == flightId) overlayReadyFlightId = flightId
    }

    /** 详情头图画出首帧（或确认失败）后由头图自己回调；只认当前飞行的目标作品。 */
    fun markHeaderReady(albumId: String) {
        val target = flight?.albumId ?: pending?.albumId ?: return
        if (target == albumId) headerReady[albumId] = true
    }

    /** 头图首帧是否已就绪；没有飞行记录时当作就绪，覆盖层不会因此多等。 */
    fun isHeaderReady(albumId: String): Boolean = headerReady[albumId] == true

    /**
     * 列表封面在飞行期间要让位，否则覆盖层里的图和列表里的图会重影。
     * 只认 flight 不认 pending：等头图测量期间用户还停在列表上，提前隐藏会留一个空洞。
     * 而且必须等覆盖层画出首帧：覆盖层空白时提前隐藏同样是一帧空洞。
     */
    fun isCoverHidden(albumId: String): Boolean {
        val current = flight ?: return false
        if (current.albumId != albumId || overlayReadyFlightId != current.id) return false
        // 反向飞行回到起点（进度 0）时覆盖层不再绘制，两端封面必须自己显示，否则会留下一帧空洞
        if (backwardFlightId == current.id && backwardProgress <= 0f) return false
        return true
    }

    /**
     * 详情头图同理：待飞阶段与覆盖层未就绪时都必须可见，
     * 否则头图从未出现或覆盖层还没画出来的场景会留下空位。
     */
    fun isHeaderHidden(albumId: String): Boolean = isCoverHidden(albumId)

    /** 列表卡片点击时调用；拿不到卡片矩形就不飞，退化成普通页转场。 */
    fun startForward(
        albumId: String,
        url: String?,
        cornerDp: Float,
        title: String? = null,
    ): Boolean {
        val from = coverRects[albumId]
        if (from == null || !isFiniteHeroRect(from)) return false
        if (url != null) urls[albumId] = url
        if (title != null) titles[albumId] = title
        val resolvedUrl = url ?: urls[albumId]
        val oldPending = state.pending
        overlayReadyFlightId = null
        headerReady.remove(albumId)
        backwardFlightId = null
        backwardProgress = 0f
        state = state.startForward(
            id = ++sequence,
            albumId = albumId,
            url = resolvedUrl,
            from = from,
            cornerDp = cornerDp,
        )
        return state.pending != null && state.pending != oldPending
    }

    /** 详情头图测量完成后补上终点；返回 true 表示已起飞。 */
    fun resolvePending(): Boolean {
        val current = state.pending ?: return false
        val header = headerRects[current.albumId]
        val corner = headerCorners[current.albumId] ?: current.cornerFromDp
        state = state.resolveForward(headerRect = header, headerCornerDp = corner)
        if (state.flight != null) {
            backwardFlightId = null
            backwardProgress = 0f
        }
        return state.flight != null
    }

    fun cancelPending() {
        state = state.cancelPending()
    }

    /** 退栈时调用：详情头图飞回列表卡片。优先使用当前头图矩形，已被注销时降级使用最近一次测得的有效矩形。 */
    fun startBackward(albumId: String): Boolean {
        val hRect = headerRects[albumId] ?: lastHeaderRects[albumId]
        val hCorner = headerCorners[albumId] ?: lastHeaderCorners[albumId] ?: 0f
        val id = ++sequence
        overlayReadyFlightId = null
        state = state.startBackward(
            id = id,
            albumId = albumId,
            url = urls[albumId],
            headerRect = hRect,
            headerCornerDp = hCorner,
            coverRect = coverRects[albumId],
            coverCornerDp = coverCorners[albumId] ?: 0f,
        )
        if (state.flight?.id == id) {
            backwardFlightId = id
            backwardProgress = 0f
        }
        lastHeaderRects.remove(albumId)
        lastHeaderCorners.remove(albumId)
        return state.flight != null
    }

    fun finishFlight(id: Int) {
        state = state.finishFlight(id)
        // 只清自己那段飞行的就绪标记；id 不匹配说明这是过期回调，别动当前飞行的状态
        if (overlayReadyFlightId == id) overlayReadyFlightId = null
        if (backwardFlightId == id) {
            backwardFlightId = null
            backwardProgress = 0f
        }
    }

    /** 反向飞行被取消、回弹回静息后由详情页调用：放回 origin，保证下一次返回仍能起飞。 */
    fun restoreOrigin(albumId: String) {
        state = state.restoreOrigin(albumId)
    }
}

/** 由 Jm3App 提供；未提供时所有登记与飞行都退化成空操作。 */
val LocalAlbumHeroHost = staticCompositionLocalOf<AlbumHeroHost?> { null }

/** 详情页最外层根容器坐标系，用于消除 NavDisplay 进场动画（如 slideInFromRight）的瞬时矩阵位移 */
val LocalAlbumDetailRootCoordinates = staticCompositionLocalOf<LayoutCoordinates?> { null }

/**
 * 封面图片的统一内存缓存 Key：列表卡片、覆盖层形变飞行、详情页头图三端共享。
 *
 * Coil 默认算出来的 Key 带 coil#size extras，尺寸一变就是另一个条目；显式指定 Key 且不带 extras 时，
 * 内存缓存只接受"目标尺寸不大于缓存位图"的复用，放大会重新解码。所以三端必须同时满足
 * "同一个显式 Key" 与 "同一个解码尺寸"（见 [heroCoverSizePx]），缺一个就要重新解码出空白首帧。
 */
fun albumCoverMemoryKey(url: String?): MemoryCache.Key? {
    val key = albumCoverCacheKeyText(url) ?: return null
    return MemoryCache.Key(key)
}

/**
 * 把封面地址规范成缓存键文本：作品封面按车号归并，同一车号的不同线路地址落到同一个条目。
 *
 * 卡片的封面地址用列表首条的图片域名拼，详情页用该作品自己的域名拼；线路漂移后两者不再相等，
 * 缓存命中不了就要重新解码，用户看到的正是"进详情页时封面闪一下"。非作品封面地址原样使用。
 *
 * 规则在 :core 的 coverCacheKeyText（）与拼地址的 CoverResolver 同文件互逆；这里只保留 app 层入口名。
 */
internal fun albumCoverCacheKeyText(url: String?): String? = coverCacheKeyText(url)

/** 按屏幕密度换算封面解码尺寸（px）：固定 3:4，保证列表卡片、覆盖层、详情头图三端解码结果尺寸完全一致。 */
internal fun heroCoverSizePx(density: Float): IntSize = IntSize(
    width = (HERO_COVER_WIDTH_DP * density).roundToInt().coerceAtLeast(1),
    height = (HERO_COVER_HEIGHT_DP * density).roundToInt().coerceAtLeast(1),
)

/**
 * 构造统一配置的封面 ImageRequest：显式内存缓存 Key（纯 URL）+ 固定解码尺寸。
 *
 * crossfade 默认关闭：命中缓存后仍会淡入一次，正是"封面闪一下"的观感来源之一。
 */
fun buildAlbumCoverRequest(context: Context, url: String?, crossfade: Boolean = false): ImageRequest? {
    val clean = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val key = albumCoverMemoryKey(clean)
    val size = heroCoverSizePx(context.resources.displayMetrics.density)
    return ImageRequest.Builder(context)
        .data(clean)
        .size(size.width, size.height)
        .apply {
            if (key != null) {
                memoryCacheKey(key)
                placeholderMemoryCacheKey(key)
            }
        }
        .crossfade(crossfade)
        .build()
}

/**
 * 封面图片的唯一入口：列表卡片、详情头图、飞行覆盖层都走这里，
 * 从而落在同一个内存缓存条目上、按同一个尺寸解码，进详情页时不再重新解码。
 *
 * [onState] 供飞行覆盖层判断"首帧已画出"，用它给两端的隐藏逻辑做门控。
 *
 * 列表卡片保留默认淡入；详情头图与飞行覆盖层必须传 [crossfade] = false——它们的显隐由 hero
 * 转场自己控制，再叠一次淡入就是用户看到的"封面闪一下"。
 *
 * [skeleton] = true 时由本组件自己铺骨架底衬并接收加载状态：骨架屏与图片必须落在同一份修饰符链上，
 * 否则 hero 转场给卡片打的隐藏透明度盖不住调用方包在外面的那层灰底，飞行途中会留下灰块。
 * 状态按 [url] 记忆，同一条缓存命中不会因为重组把骨架屏重新点亮。
 */
@Composable
fun AlbumCoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    crossfade: Boolean = true,
    skeleton: Boolean = false,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    val context = LocalContext.current
    val request = remember(url, crossfade) { buildAlbumCoverRequest(context, url, crossfade) }
    // 状态无条件记忆：它只在自己的 url 变化时重置，多记住一份不会影响不铺骨架的调用点
    val skeletonState = rememberCoverSkeletonState(url)
    // 显式声明期望类型：骨架分支的 Lambda 末句是 `onState?.invoke`，返回值是 Unit?，
    // 没有 Unit 上下文时会被推成 (State) -> Unit?，与 AsyncImage 的参数类型不符。
    val chainedOnState: ((AsyncImagePainter.State) -> Unit)? = if (skeleton) {
        { state ->
            skeletonState.onImageState(state)
            onState?.invoke(state)
        }
    } else {
        onState
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = if (skeleton) modifier.coverSkeleton(skeletonState) else modifier,
        onState = chainedOnState,
    )
}

// 作品卡圆角契约：全部作品卡（网格卡、行卡、缩略图、角标）的圆角只从皮肤或本文件的常量取，
// 调用点禁止写 dp 字面量，否则不同作品的卡会重新长出各自的曲率。
//
// 卡片外圆角：Miuix Card 的内边距为 0，封面贴满卡片，裁出来的就是 Card 自身圆角。
// 运行期列表卡与详情头图一律取 LocalSkin.current.shapes.cardCorner（HyperOS 16dp、
// ColorOS 18dp），不保留单独的卡片圆角常量入口，避免调用点回退到固定默认值。

/** 卡内缩略封面（历史、收藏、追更的 48x64 小图）：比卡片外圆角小，避免小图被裁成胶囊。 */
const val HERO_THUMB_CORNER_DP = 8f

/** 封面上的角标胶囊：小圆角只用于角标，与卡片曲率无关。 */
const val HERO_BADGE_CORNER_DP = 6f

/**
 * 封面解码尺寸：与详情页头图的 144x192dp 对齐。
 * 列表卡片、覆盖层、详情头图都按这个尺寸解码，内存缓存里的同一张 Bitmap 才能在三端互相复用。
 */
internal const val HERO_COVER_WIDTH_DP = 144f
internal const val HERO_COVER_HEIGHT_DP = 192f

/** 覆盖层的绘制期变换：以落点矩形为固定尺寸时，把图层还原成插值矩形所需的缩放与平移。 */
internal data class HeroOverlayTransform(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
)

/**
 * 算出绘制期缩放与平移，替代在组合期逐帧重算尺寸与偏移。
 *
 * 两端同为 3:4，等比缩放就能精确复现 [lerpHeroRect] 的插值矩形：进度 0 落回起点矩形，
 * 进度 1 时缩放为 1、位移为 0，与终态完全重合；中间帧的宽度与左上角与逐帧尺寸一致。
 */
internal fun heroOverlayTransform(from: HeroRect, to: HeroRect, fraction: Float): HeroOverlayTransform {
    val rect = lerpHeroRect(from, to, fraction)
    val scale = if (to.width > 0f) rect.width / to.width else 1f
    return HeroOverlayTransform(
        scale = scale,
        translationX = rect.left - to.left,
        translationY = rect.top - to.top,
    )
}

/** 覆盖层：画在 NavDisplay 之上，按进度插值矩形与圆角。 */
@Composable
fun AlbumHeroOverlay(host: AlbumHeroHost, modifier: Modifier = Modifier) {
    val pending = host.pending
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        // 等头图测出矩形再起飞；等不到（网络慢/加载失败）就放弃，把封面恢复可见
        withTimeoutOrNull(HERO_PENDING_TIMEOUT_MS) {
            snapshotFlow { host.headerRect(pending.albumId) }.first { it != null }
        }
        if (!host.resolvePending()) host.cancelPending()
    }

    val flight = host.flight ?: return
    // 正向飞行自己播 tween；反向飞行的进度由详情页按返回手势写入，同一段形变正反两向。
    val backward = host.isBackwardFlight(flight.id)
    val progress = remember(flight.id) { Animatable(0f) }
    // 组合期只读「是否参与绘制」这个布尔：只在进出 0 时翻转一次，进度本身全部留给绘制期读取，
    // 反向手势逐帧变化时不会再触发覆盖层重组。
    val drawn by remember(flight.id, backward) {
        derivedStateOf { (if (backward) host.backwardProgress else progress.value) > 0f }
    }
    // 反向飞行只在离开起点后才参与绘制：进度 0 时覆盖层与详情头图完全重合，由头图自己显示即可。
    // 静止或回弹结束后覆盖层彻底退场，既不残留画面也不拦截触摸。
    if (backward && !drawn) return
    LaunchedEffect(flight.id, backward) {
        if (backward) return@LaunchedEffect
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = HERO_FLIGHT_DURATION_MS, easing = FastOutSlowInEasing),
        )
        // 落位后等头图首帧再收场：否则覆盖层消失的那一帧，头图还是空白的
        withTimeoutOrNull(HERO_HEADER_READY_TIMEOUT_MS) {
            snapshotFlow { host.isHeaderReady(flight.albumId) }.first { it }
        }
        host.finishFlight(flight.id)
    }

    // 反向飞行的收场时机由返回手势决定：取消时详情页直接 finishFlight，提交时页面销毁时收场
    val density = LocalDensity.current
    val title = host.albumTitle(flight.albumId)
    // 固定落在落点矩形上：飞行结束时覆盖层与详情头图完全重合，两端不必各用一份尺寸。
    // 圆角固定取落点值、由图层缩放带着走，圆角随进度收缩的量与尺寸收缩同源。
    val base = flight.to
    Box(modifier = modifier.fillMaxSize()) {
        // 覆盖层自己做形变，所以不叠 Coil 淡入（默认即关闭），否则会出现半透明重影
        AlbumCoverImage(
            url = flight.url,
            contentDescription = title,
            modifier = Modifier
                .offset { IntOffset(base.left.roundToInt(), base.top.roundToInt()) }
                .size(
                    width = with(density) { base.width.toDp() },
                    height = with(density) { base.height.toDp() },
                )
                .graphicsLayer {
                    // 在图层里读进度：逐帧只失效图层属性，组合与布局都不再参与
                    val fraction = if (backward) host.backwardProgress else progress.value
                    val transform = heroOverlayTransform(flight.from, flight.to, fraction)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = transform.translationX
                    translationY = transform.translationY
                }
                .squircleClip(flight.cornerToDp.dp),
            // 形变过程自己会放大缩小，再叠一次淡入会出现半透明重影
            crossfade = false,
            // 首帧画出后才允许两端封面让位，避免"覆盖层空白 + 两端已隐藏"的空洞帧
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) host.markOverlayReady(flight.id)
            },
        )
    }
}

/** 列表侧封面：登记根坐标矩形，飞行期间隐藏自己，同时预存标题与封面。 */
@Composable
fun Modifier.albumHeroCover(
    albumId: String,
    url: String?,
    title: String? = null,
    cornerDp: Float,
): Modifier {
    val host = LocalAlbumHeroHost.current ?: return this
    if (title != null || url != null) {
        host.rememberAlbum(albumId, title, url)
    }
    val registered = remember(host, albumId) { mutableStateOf<HeroRect?>(null) }
    DisposableEffect(host, albumId) {
        onDispose { host.unregisterCover(albumId, registered.value) }
    }
    return this
        .onGloballyPositioned { coordinates ->
            if (!coordinates.isAttached) return@onGloballyPositioned
            val position = coordinates.positionInRoot()
            val rect = HeroRect(
                left = position.x,
                top = position.y,
                width = coordinates.size.width.toFloat(),
                height = coordinates.size.height.toFloat(),
            )
            if (isFiniteHeroRect(rect)) {
                registered.value = rect
                host.registerCover(albumId, url, rect, cornerDp)
            }
        }
        .graphicsLayer { alpha = if (host.isCoverHidden(albumId)) 0f else 1f }
}

@Composable
fun Modifier.albumHeroCover(albumId: String, url: String?, cornerDp: Float): Modifier =
    albumHeroCover(albumId = albumId, url = url, title = null, cornerDp = cornerDp)

/**
 * 详情页的共享元素反向飞行：把导航层的退出进度接到 [AlbumHeroHost] 上。
 *
 * 预测性返回的 `relativeDepth` 与页面 alpha 同源（退出时 0 → -1）：手势移动多少、封面回飞多少；
 * 取消回弹时收场并把 origin 放回去，提交时随页面销毁收场。
 * 只有返回手势进行中（gesture 非空）或页面已标记出栈（[NavRole.Outgoing]）才算退出信号；
 * 进场（Incoming）与静止（Top）都不满足，避免进场结束后误起一段停在进度 0、此后不再收场的反向飞行。
 * 起飞/推进/收场的判定在 [planHeroBackwardCommand]，Miuix 与 COUI 两套皮肤共用这一份编排。
 */
@Composable
fun AlbumDetailHeroFlightEffect(albumId: String) {
    val heroHost = LocalAlbumHeroHost.current
    if (heroHost != null) {
        val navScope by rememberUpdatedState(LocalNavTransitionScope.current)
        LaunchedEffect(heroHost, albumId) {
            var startFailed = false
            snapshotFlow {
                val scope = navScope
                val exit = (-scope.relativeDepth).coerceIn(0f, 1f)
                exit to (scope.gesture != null || scope.role == NavRole.Outgoing)
            }.collect { (exit, popping) ->
                val ownFlightId = heroHost.flight
                    ?.takeIf { it.albumId == albumId && heroHost.isBackwardFlight(it.id) }
                    ?.id
                val hostBusy = heroHost.flight != null || heroHost.pending != null
                when (val command = planHeroBackwardCommand(exit, popping, startFailed, ownFlightId, hostBusy)) {
                    is HeroBackwardCommand.FinishOwnFlight -> {
                        heroHost.finishFlight(command.flightId)
                        heroHost.restoreOrigin(albumId)
                        startFailed = false
                    }

                    is HeroBackwardCommand.AdvanceOwnFlight ->
                        heroHost.setBackwardProgress(command.flightId, command.progress)

                    is HeroBackwardCommand.StartFlight -> {
                        if (heroHost.startBackward(albumId)) {
                            heroHost.flight
                                ?.takeIf { it.albumId == albumId && heroHost.isBackwardFlight(it.id) }
                                ?.let { heroHost.setBackwardProgress(it.id, command.progress) }
                        } else {
                            // 拿不到两端矩形就不飞，退化成普通页转场；startBackward 会移除 origin，这里放回去
                            startFailed = true
                            heroHost.restoreOrigin(albumId)
                        }
                    }

                    HeroBackwardCommand.ResetAttempt -> startFailed = false
                    HeroBackwardCommand.Idle -> Unit
                }
            }
        }
        DisposableEffect(heroHost, albumId) {
            onDispose {
                val flight = heroHost.flight
                if (flight != null && flight.albumId == albumId && heroHost.isBackwardFlight(flight.id)) {
                    heroHost.finishFlight(flight.id)
                }
            }
        }
    }
}

/** 详情侧头图：登记根坐标矩形，飞行期间隐藏自己。 */
@Composable
fun Modifier.albumHeroHeader(albumId: String, url: String?, cornerDp: Float): Modifier {
    val host = LocalAlbumHeroHost.current ?: return this
    val detailRoot = LocalAlbumDetailRootCoordinates.current
    val registered = remember(host, albumId) { mutableStateOf<HeroRect?>(null) }
    DisposableEffect(host, albumId) {
        onDispose { host.unregisterHeader(albumId, registered.value) }
    }
    return this
        .onGloballyPositioned { coordinates ->
            if (!coordinates.isAttached) return@onGloballyPositioned
            val position = if (detailRoot != null && detailRoot.isAttached) {
                // 处于 NavDisplay 页面转场（如 slideInFromRight）中时，positionInRoot() 会叠加进场位移导致起飞终点偏离；
                // 藉由详情根容器坐标消除同层级祖先变换，首帧即可测出页面静止时的真实屏幕目标矩形。
                detailRoot.localPositionOf(coordinates, Offset.Zero)
            } else {
                coordinates.positionInRoot()
            }
            val rect = HeroRect(
                left = position.x,
                top = position.y,
                width = coordinates.size.width.toFloat(),
                height = coordinates.size.height.toFloat(),
            )
            if (isFiniteHeroRect(rect)) {
                registered.value = rect
                host.registerHeader(albumId, url, rect, cornerDp)
            }
        }
        .graphicsLayer { alpha = if (host.isHeaderHidden(albumId)) 0f else 1f }
}
