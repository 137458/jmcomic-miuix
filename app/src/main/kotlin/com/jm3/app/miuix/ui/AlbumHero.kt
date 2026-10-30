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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.squircle.squircleClip

/**
 * 列表卡片与详情头图的矩形登记表 + 当前飞行状态。
 *
 * miuix-nav 的转场是 graphicsLayer 平移，没有 AnimatedVisibilityScope，Compose 原生
 * Modifier.sharedBounds 无处挂载；所以共享元素自己画：两端把矩形报到 [AlbumHeroHost]，
 * 覆盖层在 NavDisplay 之上按矩形插值画一张图，两端真实封面在飞行期间隐藏。
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
    private var sequence = 0

    var state by mutableStateOf(HeroState())
        private set

    /** 已点开详情、等头图测量结果的待飞记录。 */
    val pending: HeroPending? get() = state.pending

    /** 正在播放的飞行；同一时刻最多一个。 */
    val flight: HeroPlan? get() = state.flight

    fun registerCover(albumId: String, url: String?, rect: HeroRect, cornerDp: Float) {
        if (!isFiniteHeroRect(rect)) return
        if (coverRects[albumId] == rect && coverCorners[albumId] == cornerDp && (url == null || urls[albumId] == url)) return
        coverRects[albumId] = rect
        coverCorners[albumId] = cornerDp
        if (url != null) urls[albumId] = url
    }

    fun unregisterCover(albumId: String, rect: HeroRect?) {
        if (rect != null && coverRects[albumId] == rect) {
            coverRects.remove(albumId)
            coverCorners.remove(albumId)
            if (albumId !in headerRects) urls.remove(albumId)
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
            if (albumId !in coverRects) urls.remove(albumId)
        }
    }

    fun coverRect(albumId: String): HeroRect? = coverRects[albumId]

    fun headerRect(albumId: String): HeroRect? = headerRects[albumId]

    /**
     * 列表封面在飞行期间要让位，否则覆盖层里的图和列表里的图会重影。
     * 只认 flight 不认 pending：等头图测量期间用户还停在列表上，提前隐藏会留一个空洞。
     */
    fun isCoverHidden(albumId: String): Boolean = flight?.albumId == albumId

    /** 详情头图只在真正起飞后隐藏：起飞前隐藏的话，头图从未出现的场景会留下空位。 */
    fun isHeaderHidden(albumId: String): Boolean = flight?.albumId == albumId

    /** 列表卡片点击时调用；拿不到卡片矩形就不飞，退化成普通页转场。 */
    fun startForward(albumId: String, url: String?, cornerDp: Float): Boolean {
        val from = coverRects[albumId]
        if (from == null || !isFiniteHeroRect(from)) return false
        if (url != null) urls[albumId] = url
        val resolvedUrl = url ?: urls[albumId]
        val oldPending = state.pending
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
        return state.flight != null
    }

    fun cancelPending() {
        state = state.cancelPending()
    }

    /** 退栈时调用：详情头图飞回列表卡片。优先使用当前头图矩形，已被注销时降级使用最近一次测得的有效矩形。 */
    fun startBackward(albumId: String): Boolean {
        val hRect = headerRects[albumId] ?: lastHeaderRects[albumId]
        val hCorner = headerCorners[albumId] ?: lastHeaderCorners[albumId] ?: 0f
        state = state.startBackward(
            id = ++sequence,
            albumId = albumId,
            url = urls[albumId],
            headerRect = hRect,
            headerCornerDp = hCorner,
            coverRect = coverRects[albumId],
            coverCornerDp = coverCorners[albumId] ?: 0f,
        )
        lastHeaderRects.remove(albumId)
        lastHeaderCorners.remove(albumId)
        return state.flight != null
    }

    fun finishFlight(id: Int) {
        state = state.finishFlight(id)
    }
}

/** 由 Jm3App 提供；未提供时所有登记与飞行都退化成空操作。 */
val LocalAlbumHeroHost = staticCompositionLocalOf<AlbumHeroHost?> { null }

/**
 * 卡片内封面被裁出来的圆角：Miuix Card 的内边距为 0，封面贴满卡片，圆角就是 Card 的默认圆角 16dp。
 * CardDefaults.cornerRadius 在 0.9.4 里是 internal（字节码 getCornerRadius-D9Ej5fM 带值类改写后缀），
 * 外部取不到，只能照抄这个数字；库若改默认圆角，这里跟着改。
 */
const val HERO_CARD_CORNER_DP = 16f
const val MIUIX_CARD_CORNER_DP = HERO_CARD_CORNER_DP
const val HERO_SEARCH_ROW_CORNER_DP = 6f
const val HERO_HISTORY_ROW_CORNER_DP = 8f
const val HERO_FAVORITE_ROW_CORNER_DP = 6f

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
    val progress = remember(flight.id) { Animatable(0f) }
    LaunchedEffect(flight.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = HERO_FLIGHT_DURATION_MS, easing = FastOutSlowInEasing),
        )
        host.finishFlight(flight.id)
    }

    val density = LocalDensity.current
    val context = LocalContext.current
    val rect = lerpHeroRect(flight.from, flight.to, progress.value)
    val corner = lerpHeroCorner(flight.cornerFromDp, flight.cornerToDp, progress.value)
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(flight.url)
                // 覆盖层自己做形变，再叠一次 Coil 淡入会出现半透明重影
                .crossfade(false)
                .placeholderMemoryCacheKey(flight.url?.let { coil3.memory.MemoryCache.Key(it) })
                .memoryCacheKey(flight.url?.let { coil3.memory.MemoryCache.Key(it) })
                .size(coil3.size.Size.ORIGINAL)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(
                    width = with(density) { rect.width.toDp() },
                    height = with(density) { rect.height.toDp() },
                )
                .squircleClip(corner.dp),
        )
    }
}

/** 列表侧封面：登记根坐标矩形，飞行期间隐藏自己。 */
@Composable
fun Modifier.albumHeroCover(albumId: String, url: String?, cornerDp: Float): Modifier {
    val host = LocalAlbumHeroHost.current ?: return this
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

/** 详情侧头图：登记根坐标矩形，飞行期间隐藏自己。 */
@Composable
fun Modifier.albumHeroHeader(albumId: String, url: String?, cornerDp: Float): Modifier {
    val host = LocalAlbumHeroHost.current ?: return this
    val registered = remember(host, albumId) { mutableStateOf<HeroRect?>(null) }
    DisposableEffect(host, albumId) {
        onDispose { host.unregisterHeader(albumId, registered.value) }
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
                host.registerHeader(albumId, url, rect, cornerDp)
            }
        }
        .graphicsLayer { alpha = if (host.isHeaderHidden(albumId)) 0f else 1f }
}
