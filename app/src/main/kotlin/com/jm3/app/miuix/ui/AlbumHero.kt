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
import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.CardDefaults
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

    var state by mutableStateOf(HeroState())
        private set

    /** 已点开详情、等头图测量结果的待飞记录。 */
    val pending: HeroPending? get() = state.pending

    /** 正在播放的飞行；同一时刻最多一个。 */
    val flight: HeroPlan? get() = state.flight

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

    /**
     * 列表封面在飞行期间要让位，否则覆盖层里的图和列表里的图会重影。
     * 只认 flight 不认 pending：等头图测量期间用户还停在列表上，提前隐藏会留一个空洞。
     * 而且必须等覆盖层画出首帧：覆盖层空白时提前隐藏同样是一帧空洞。
     */
    fun isCoverHidden(albumId: String): Boolean {
        val current = flight ?: return false
        return current.albumId == albumId && overlayReadyFlightId == current.id
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
        overlayReadyFlightId = null
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
        // 只清自己那段飞行的就绪标记；id 不匹配说明这是过期回调，别动当前飞行的状态
        if (overlayReadyFlightId == id) overlayReadyFlightId = null
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
    val clean = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return MemoryCache.Key(clean)
}

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
 */
@Composable
fun AlbumCoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    crossfade: Boolean = true,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    val context = LocalContext.current
    val request = remember(url, crossfade) { buildAlbumCoverRequest(context, url, crossfade) }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onState = onState,
    )
}

/**
 * 作品卡圆角契约：全部作品卡（网格卡、行卡、缩略图、角标）的圆角只从这里取，
 * 调用点禁止写 dp 字面量，否则不同作品的卡会重新长出各自的曲率。
 *
 * 卡片外圆角：Miuix Card 的内边距为 0，封面贴满卡片，裁出来的就是 Card 自身圆角，
 * 所以这里直接取 CardDefaults.CornerRadius，库升级改默认值时卡片与飞行端不会错位。
 */
val HERO_CARD_CORNER_DP: Float = CardDefaults.CornerRadius.value

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
    val title = host.albumTitle(flight.albumId)
    val rect = lerpHeroRect(flight.from, flight.to, progress.value)
    val corner = lerpHeroCorner(flight.cornerFromDp, flight.cornerToDp, progress.value)
    Box(modifier = modifier.fillMaxSize()) {
        // 覆盖层自己做形变，所以不叠 Coil 淡入（默认即关闭），否则会出现半透明重影
        AlbumCoverImage(
            url = flight.url,
            contentDescription = title,
            modifier = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(
                    width = with(density) { rect.width.toDp() },
                    height = with(density) { rect.height.toDp() },
                )
                .squircleClip(corner.dp),
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
