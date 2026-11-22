// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.FoldableNotes
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.InlineCodeText
import com.jm3.app.miuix.ui.NotesFold
import com.jm3.app.miuix.ui.NotesPageViewport
import com.jm3.app.miuix.ui.UpdateAvailableDialog
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiCircleActionButton
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiHeaderCapsuleAction
import com.jm3.app.miuix.ui.coui.component.CouiInstallProgressButton
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBar
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBarAction
import com.jm3.app.miuix.ui.coui.component.LocalCouiBlurEnabled
import com.jm3.app.miuix.ui.coui.component.couiContourSurfaceEnabled
import com.jm3.app.miuix.ui.coui.component.couiFloatingSurfaceEnabled
import com.jm3.app.miuix.ui.coui.component.couiPageContentPadding
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.component.rememberMaterialBackdropProvider
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.copyTextToClipboard
import com.jm3.app.miuix.ui.formatUpdateSize
import com.jm3.app.miuix.ui.loadAppIcon
import com.jm3.app.miuix.ui.loadCurrentVersion
import com.jm3.app.miuix.ui.parseInlineNotes
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.update.UpdateNoBrowserMessage
import com.jm3.app.miuix.ui.update.rememberUpdateScaffoldState
import com.jm3.app.miuix.ui.updateBrowserFallbackUrl
import com.jm3.app.miuix.ui.updateDialogDownloadDetail
import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.MarkdownBlock
import com.jm3.core.update.MarkdownBlocks
import com.jm3.core.update.ReleaseAsset
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateCenter
import com.jm3.core.update.UpdateChecker
import com.jm3.core.update.UpdateOutcome
import com.jm3.core.update.UpdateProgress
import com.jm3.core.update.UpdatePrompt
import com.jm3.core.update.VersionText
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.squircleBorder
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 更新说明的块间距，与更新弹窗保持同一节奏。 */
private val NotesBlockSpacing = 6.dp

/** 说明项前缀列的最小宽度：让多位数序号与项目符号不挤在一起。 */
private val NotesPrefixMinWidth = 20.dp

/** 已是最新版本且更新说明较长时默认预览的块数（对标 OShin 折叠说明）。 */
private const val CHANGELOG_PREVIEW_BLOCK_LIMIT = 3

private val HeroIconContainerShape = SquircleShape(cornerRadius = 14.dp, weight = 3f)
private val HeroIconContainerSize = 52.dp
private val HeroIconGlyphSize = 36.dp

/**
 * COUI 检查更新页：光感主卡 + 说明/设置/信息分组 + 底部填充进度操作栏。
 *
 * 检查逻辑原样复用 [UpdateCenter] 与 [UpdatePrompt]，新版本弹窗复用共享的 [UpdateAvailableDialog]；
 * 底部操作栏采用两遍裁剪反白胶囊进度按钮（对标 `COUIInstallLoadProgress`）并随下载态附带取消/重下圆键。
 */
@Composable
internal fun CouiUpdateScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember(context) { loadCurrentVersion(context) }
    val appName = remember(context) {
        context.applicationInfo.loadLabel(context.packageManager).toString()
    }

    var showPreviewDialog by rememberSaveable { mutableStateOf(false) }
    var previewSimulating by remember { mutableStateOf(false) }
    var previewDownloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    val scaffoldState = rememberUpdateScaffoldState(
        deps = deps,
        snackbarHostState = snackbarHostState,
        currentVersion = currentVersion,
        openReleaseFailure = { UpdateNoBrowserMessage },
        onPrompt = {
            showPreviewDialog = false
            previewSimulating = false
        },
    )
    val downloadState by scaffoldState.downloadState

    val state = scaffoldState.outcome
    val release = scaffoldState.release
    val hasNewVersion = scaffoldState.hasNewVersion
    val latestVersion = scaffoldState.latestVersion
    val ignored = scaffoldState.ignored

    LaunchedEffect(previewSimulating) {
        if (!previewSimulating) {
            previewDownloadState = UpdateDownloadState.Idle
            return@LaunchedEffect
        }
        val previewAsset = couiPreviewUpdateRelease(currentVersion, release).apk
        val total = (previewAsset?.size ?: 24L * 1024 * 1024).coerceAtLeast(1L)
        val steps = 15
        val perStepDelayMs = 180L
        for (step in 1..steps) {
            if (!isActive || !previewSimulating) return@LaunchedEffect
            val downloaded = (total * step) / steps
            val speed = (total / steps) * (1000L / perStepDelayMs)
            previewDownloadState = UpdateDownloadState.Downloading(
                downloaded = downloaded,
                total = total,
                speedBytesPerSec = speed,
            )
            delay(perStepDelayMs)
        }
        previewSimulating = false
        previewDownloadState = UpdateDownloadState.Idle
        showPreviewDialog = false
        snackbarHostState.showSnackbar("预览下载演示完成（未写入文件）")
    }

    fun copyCurrentVersion() {
        copyTextToClipboard(
            context = context,
            scope = scope,
            snackbarHostState = snackbarHostState,
            label = "版本号",
            value = currentVersion,
            successMessage = "已复制版本号 v$currentVersion",
        )
    }

    val ignoreSummary = when {
        state == null -> "正在检查更新…"
        latestVersion == null -> "尚未获取远端版本"
        ignored -> "已忽略" + latestVersion + "，不再弹出该版本提示"
        !hasNewVersion -> "当前没有需要忽略的新版本"
        else -> "忽略" + latestVersion + " 的更新提示"
    }

    val blocks = remember(release?.notes) {
        release?.notes?.let(MarkdownBlocks::parse).orEmpty()
    }
    var notesUserToggled by rememberSaveable(release?.tagName) { mutableStateOf(false) }
    var notesExpanded by rememberSaveable(release?.tagName) {
        mutableStateOf(couiChangelogDefaultExpanded(state, blocks.size))
    }
    LaunchedEffect(state, release?.tagName, blocks.size) {
        if (!notesUserToggled) {
            notesExpanded = couiChangelogDefaultExpanded(state, blocks.size)
        }
    }

    CouiTheme {
        val scrollBehavior = rememberCouiPageScrollBehavior()
        val wide = isCouiWideWindow()

        CouiPageScaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = { backdrop ->
                CouiPageTopBar(
                    title = "检查更新",
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    onBack = onBack,
                    wide = wide,
                    actions = {
                        CouiPageTopBarAction(
                            icon = CouiIcons.Refresh,
                            contentDescription = "重新检查",
                            onClick = { if (state != null) scaffoldState.recheck() },
                        )
                    },
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlassSnackbarHost(
                        state = snackbarHostState,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UpdateBottomActionBar(
                        state = state,
                        downloadState = downloadState,
                        onRecheck = scaffoldState::recheck,
                        onOpenRelease = scaffoldState::openRelease,
                        onStartDownload = scaffoldState::startDownload,
                        onCancelDownload = scaffoldState::cancelDownload,
                        onResetDownload = scaffoldState::resetDownload,
                        onInstall = scaffoldState::install,
                    )
                }
            },
        ) { innerPadding ->
            val contentPadding = couiPageContentPadding(innerPadding, extraBottom = 0.dp)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .couiPageScroll()
                    .consumeWindowInsets(contentPadding),
                contentPadding = contentPadding,
                flingBehavior = rememberCOUIFlingBehavior(),
            ) {
                item(key = "update-hero") {
                    UpdateHeroCard(
                        appName = appName,
                        currentVersion = currentVersion,
                        state = state,
                        downloadState = downloadState,
                        onOpenRelease = scaffoldState::openRelease,
                    )
                }
                if (release != null) {
                    item(key = "update-notes") {
                        UpdateNotesGroup(
                            title = changelogTitle(state),
                            release = release,
                            blocks = blocks,
                            expanded = notesExpanded,
                            onToggleExpanded = {
                                notesUserToggled = true
                                notesExpanded = !notesExpanded
                            },
                            onOpenRelease = scaffoldState::openRelease,
                        )
                    }
                }
                item(key = "update-settings") {
                    UpdateSettingsGroup(
                        autoCheckUpdate = scaffoldState.autoCheck,
                        ignored = ignored,
                        ignoreSummary = ignoreSummary,
                        ignoreEnabled = scaffoldState.ignoreEnabled,
                        onAutoCheckChange = scaffoldState::setAutoCheck,
                        onIgnoreToggle = { scaffoldState.latestVersion?.let(scaffoldState::toggleIgnored) },
                    )
                }
                item(key = "update-meta") {
                    UpdateMetaGroup(
                        currentVersion = currentVersion,
                        state = state,
                        release = release,
                        onCopyVersion = ::copyCurrentVersion,
                        onOpenRelease = { scaffoldState.openRelease(UpdateChecker.RELEASES_PAGE_URL) },
                    )
                }
                item(key = "update-debug") {
                    UpdateDebugGroup(
                        hasNewVersion = hasNewVersion,
                        onTestDialog = {
                            if (hasNewVersion) {
                                showPreviewDialog = false
                                previewSimulating = false
                                scaffoldState.showDialog()
                            } else {
                                previewSimulating = false
                                previewDownloadState = UpdateDownloadState.Idle
                                showPreviewDialog = true
                            }
                        },
                    )
                }
            }
        }

        val realRelease = scaffoldState.dialogRelease
        val usePreview = realRelease == null
        val previewRelease = remember(currentVersion, release) {
            couiPreviewUpdateRelease(currentVersion = currentVersion, remoteRelease = release)
        }
        val activeTarget = if (usePreview) {
            if (showPreviewDialog) previewRelease else null
        } else {
            realRelease
        }
        val dialogRelease = rememberRetainedTarget(activeTarget)
        val dialogVisible = if (usePreview) showPreviewDialog else scaffoldState.dialogVisible
        if (dialogRelease != null) {
            UpdateAvailableDialog(
                show = dialogVisible,
                currentVersion = currentVersion,
                release = dialogRelease,
                downloadState = if (usePreview) previewDownloadState else downloadState,
                onDismiss = {
                    if (usePreview) {
                        showPreviewDialog = false
                        previewSimulating = false
                    } else {
                        scaffoldState.dismissDialog()
                    }
                },
                onDownload = {
                    if (usePreview) {
                        previewSimulating = true
                    } else {
                        scaffoldState.startDownload(dialogRelease)
                    }
                },
                onOpenRelease = {
                    if (usePreview) {
                        showPreviewDialog = false
                        previewSimulating = false
                    } else {
                        scaffoldState.dismissDialog()
                    }
                    scaffoldState.openRelease(dialogRelease.htmlUrl)
                },
                onIgnore = { version ->
                    if (usePreview) {
                        showPreviewDialog = false
                        previewSimulating = false
                        scope.launch { snackbarHostState.showSnackbar("预览模式：未写入忽略版本") }
                    } else {
                        scaffoldState.dismissDialog()
                        if (scaffoldState.ignoredVersion != version) scaffoldState.toggleIgnored(version)
                    }
                },
                onCancelDownload = {
                    if (usePreview) {
                        previewSimulating = false
                    } else {
                        scaffoldState.cancelDownload()
                    }
                },
            )
        }
    }
}

/**
 * 参考 ColorOS 软件更新页的顶部流体曲线玻璃视觉主卡：
 * 光感弧面玻璃底纹 + 应用图标 + Release 状态胶囊 + 大字态结论 + 软件版本对照 + 下载实时速率。
 */
@Composable
private fun UpdateHeroCard(
    appName: String,
    currentVersion: String,
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
    onOpenRelease: (String) -> Unit,
) {
    val context = LocalContext.current
    val dark = COUITheme.isDark
    val scheme = COUITheme.colorScheme
    val iconSizePx = with(LocalDensity.current) { HeroIconGlyphSize.roundToPx() }
    val appIcon = remember(context, iconSizePx) { loadAppIcon(context, iconSizePx) }
    val checking = state == null
    val hasFailed = state is UpdateOutcome.Failed
    val isAvailable = state is UpdateOutcome.Available

    val badgeIcon = when {
        checking -> CouiIcons.Refresh
        isAvailable -> CouiIcons.Download
        hasFailed -> CouiIcons.Info
        else -> CouiIcons.Check
    }
    val headlineText = couiUpdateHeadline(state)
    val versionLine = couiUpdateVersionLine(currentVersion, state)
    val subtitleText = when {
        state is UpdateOutcome.Available -> couiUpdateReleaseMetaLine(state.release)
        state is UpdateOutcome.UpToDate -> couiUpdateReleaseMetaLine(state.release)
            .ifBlank { "当前已安装公开发布的最新版本" }
        state is UpdateOutcome.Failed -> state.message
        else -> "正在连接 GitHub Releases 获取版本信息"
    }

    val spinAngle = if (checking) {
        val transition = rememberInfiniteTransition(label = "coui-update-check-spin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
            ),
            label = "coui-update-check-angle",
        )
        angle
    } else {
        0f
    }

    val statusTint = if (hasFailed) scheme.error else scheme.primary
    val canBlurHero = LocalCouiBlurEnabled.current &&
        couiContourSurfaceEnabled(Build.VERSION.SDK_INT) &&
        isRuntimeShaderSupported()
    val heroCardBg = scheme.cardBackground
    val heroBackdrop = if (canBlurHero) {
        rememberLayerBackdrop {
            drawRect(heroCardBg)
            drawContent()
        }
    } else {
        null
    }
    val heroMaterialProvider = rememberMaterialBackdropProvider(heroBackdrop)
    val borderColor = if (isAvailable || hasFailed) {
        statusTint.copy(alpha = if (dark) 0.36f else 0.24f)
    } else {
        scheme.divider.copy(alpha = if (dark) 0.55f else 0.45f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
            .padding(
                top = CouiLayoutTokens.CategoryTopSpacing,
                bottom = CouiLayoutTokens.GroupSpacing,
            )
            .squircleBorder(
                width = if (isAvailable || hasFailed) 1.dp else 0.5.dp,
                color = borderColor,
                cornerRadius = CouiLayoutTokens.CardCorner,
                weight = CouiLayoutTokens.CardCornerWeight,
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            UpdateHeroBackdrop(
                cardColor = heroCardBg,
                statusTint = statusTint,
                dark = dark,
                backdrop = heroBackdrop,
                modifier = Modifier.matchParentSize(),
            )
            CompositionLocalProvider(LocalMaterialBackdrop provides heroMaterialProvider) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (appIcon != null) {
                        Box(
                            modifier = Modifier
                                .size(HeroIconContainerSize)
                                .clip(HeroIconContainerShape)
                                .background(scheme.labelPrimary.copy(alpha = if (dark) 0.08f else 0.04f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = appName,
                                modifier = Modifier.size(HeroIconGlyphSize),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(statusTint.copy(alpha = if (dark) 0.18f else 0.11f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(15.dp)
                                .graphicsLayer { rotationZ = spinAngle },
                            tint = statusTint,
                        )
                        Text(
                            text = "$appName Release",
                            style = COUITheme.textStyles.bodyXS,
                            color = statusTint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicText(
                        text = headlineText,
                        style = COUITheme.textStyles.displayXS.copy(
                            color = scheme.labelPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(scheme.labelPrimary.copy(alpha = if (dark) 0.08f else 0.05f))
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = versionLine,
                            style = COUITheme.textStyles.bodyM,
                            color = scheme.labelSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (subtitleText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = subtitleText,
                            style = COUITheme.textStyles.bodyS,
                            color = scheme.labelSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val apkAsset = if (isAvailable) state.release.apk else null
                    if (apkAsset != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = apkAsset.name,
                            style = COUITheme.textStyles.bodyXS,
                            color = scheme.labelSecondary.copy(alpha = 0.84f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isAvailable && downloadState is UpdateDownloadState.Downloading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(scheme.primary.copy(alpha = if (dark) 0.16f else 0.10f))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = updateDialogDownloadDetail(downloadState),
                                style = COUITheme.textStyles.bodyXS,
                                color = scheme.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else if (isAvailable && downloadState is UpdateDownloadState.Failed) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CouiBodyText(
                            text = downloadState.message,
                            color = scheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        CouiHeaderCapsuleAction(
                            text = "用浏览器下载",
                            trailingIcon = CouiIcons.Forward,
                            tinted = true,
                            onClick = { onOpenRelease(updateBrowserFallbackUrl(state.release)) },
                        )
                    } else if (hasFailed) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CouiHeaderCapsuleAction(
                            text = "在浏览器打开 Releases 页",
                            trailingIcon = CouiIcons.Forward,
                            tinted = true,
                            onClick = { onOpenRelease(UpdateChecker.RELEASES_PAGE_URL) },
                        )
                    }
                }
            }
        }
    }
}

/** COUI 顶部状态卡底纹：基于主题卡片底色叠加柔和的状态色径向微光，并为前景胶囊按钮提供同源采样背景。 */
@Composable
private fun UpdateHeroBackdrop(
    cardColor: Color,
    statusTint: Color,
    dark: Boolean,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val backdropModifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
    Box(
        modifier = modifier
            .then(backdropModifier)
            .background(cardColor)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            statusTint.copy(alpha = if (dark) 0.15f else 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = maxOf(size.width, size.height) * 0.85f,
                    ),
                )
            },
    )
}

/**
 * 底部悬浮主操作栏：API 34+ 通过 [LocalMaterialBackdrop] 为 [CouiInstallProgressButton] 与
 * [CouiCircleActionButton] 提供 COUI 17 实时材质模糊，滚过底栏的内容透过玻璃自然流动。
 */
@Composable
private fun UpdateBottomActionBar(
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
    onRecheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onStartDownload: (ReleaseAsset) -> Unit,
    onCancelDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onInstall: (File) -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val mode = couiUpdateActionBarMode(state, downloadState)
    val label = couiUpdateButtonLabel(state, downloadState)
    val progressFraction = couiUpdateProgressFraction(downloadState)
    val isDownloading = mode == CouiUpdateActionBarMode.DOWNLOADING
    val enabled = mode != CouiUpdateActionBarMode.CHECKING && !isDownloading
    val materialBackdrop = LocalMaterialBackdrop.current
    val hasFloatingSurface = couiFloatingSurfaceEnabled(
        sdkInt = Build.VERSION.SDK_INT,
        hasBackdrop = materialBackdrop != null,
    )

    val containerModifier = if (hasFloatingSurface) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        scheme.backgroundWithCard.copy(alpha = 0f),
                        scheme.backgroundWithCard.copy(alpha = 0.94f),
                        scheme.backgroundWithCard,
                    ),
                ),
            )
    }

    Box(
        modifier = containerModifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        // API 33 上 isRuntimeShaderSupported() 为真，但悬浮底栏门控仍为 false（底栏绘制不透明渐变遮罩）；
        // 显式清空 LocalMaterialBackdrop，避免子按钮渲染为透明毛玻璃盖在实色遮罩上。
        CompositionLocalProvider(
            LocalMaterialBackdrop provides if (hasFloatingSurface) materialBackdrop else null,
        ) {
            Row(
                modifier = Modifier
                    .readableWidth()
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CouiInstallProgressButton(
                    text = label,
                    enabled = enabled,
                    progress = progressFraction,
                    isProgressActive = isDownloading,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when (mode) {
                            CouiUpdateActionBarMode.READY_TO_INSTALL -> {
                                (downloadState as? UpdateDownloadState.Done)?.let { onInstall(it.file) }
                            }
                            CouiUpdateActionBarMode.DOWNLOAD_FAILED -> {
                                val asset = (state as? UpdateOutcome.Available)?.release?.apk
                                if (asset != null) onStartDownload(asset) else onResetDownload()
                            }
                            CouiUpdateActionBarMode.AVAILABLE_IDLE -> {
                                val release = (state as? UpdateOutcome.Available)?.release ?: return@CouiInstallProgressButton
                                val asset = release.apk
                                if (asset != null) onStartDownload(asset) else onOpenRelease(release.htmlUrl)
                            }
                            CouiUpdateActionBarMode.CHECK_FAILED,
                            CouiUpdateActionBarMode.UP_TO_DATE,
                            -> onRecheck()
                            CouiUpdateActionBarMode.CHECKING,
                            CouiUpdateActionBarMode.DOWNLOADING,
                            -> Unit
                        }
                    },
                )
                if (isDownloading) {
                    CouiCircleActionButton(
                        icon = CouiIcons.Close,
                        contentDescription = "取消下载",
                        onClick = onCancelDownload,
                    )
                } else if (mode == CouiUpdateActionBarMode.READY_TO_INSTALL) {
                    CouiCircleActionButton(
                        icon = CouiIcons.Refresh,
                        contentDescription = "重新下载",
                        onClick = onResetDownload,
                    )
                }
            }
        }
    }
}

/** 更新日志卡：标题栏提供「发布详情」胶囊，长说明底部通过分隔线 + [BasicComponent] 行展开收起。 */
@Composable
private fun UpdateNotesGroup(
    title: String,
    release: ReleaseInfo,
    blocks: List<MarkdownBlock>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val collapsible = couiChangelogCollapsible(blocks.size)
    val previewLimit = couiChangelogPreviewLimit(blocks.size, expanded)
    val hiddenCount = couiChangelogCollapsedHiddenCount(blocks.size)

    CouiGroupTitle(
        title = title,
        topSpacing = 0.dp,
        actions = {
            if (release.htmlUrl.isNotBlank()) {
                CouiHeaderCapsuleAction(
                    text = "发布详情",
                    trailingIcon = CouiIcons.Forward,
                    tinted = false,
                    onClick = { onOpenRelease(release.htmlUrl) },
                )
            }
        },
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
            .padding(bottom = CouiLayoutTokens.GroupSpacing),
    ) {
        FoldableNotes(
            fold = if (collapsible) {
                NotesFold(
                    expanded = expanded,
                    label = if (expanded) "收起更新说明" else "展开全部更新说明",
                    onToggle = onToggleExpanded,
                )
            } else {
                null
            },
            spec = NotesPageViewport,
            modifier = Modifier.animateContentSize(),
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = CouiLayoutTokens.RowPadding,
                            vertical = CouiLayoutTokens.ContentPadding + 2.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(NotesBlockSpacing),
                ) {
                    Text(
                        text = release.name.ifBlank { "版本特性说明" },
                        style = COUITheme.textStyles.headlineXS,
                        color = scheme.labelPrimary,
                    )
                    if (previewLimit != 0) {
                        UpdateNotes(
                            markdown = release.notes,
                            previewLimit = previewLimit,
                        )
                    }
                }
            },
            footer = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = CouiLayoutTokens.RowPadding))
                    BasicComponent(
                        title = if (expanded) "收起更新说明" else "展开全部更新说明",
                        titleColor = BasicComponentDefaults.titleColor(color = scheme.primaryText),
                        summary = if (!expanded) {
                            if (previewLimit == 0) {
                                "共 ${blocks.size} 段内容已收起"
                            } else {
                                "还有 $hiddenCount 段内容未展开"
                            }
                        } else {
                            null
                        },
                        endActions = {
                            Icon(
                                imageVector = if (expanded) CouiIcons.ChevronUp else CouiIcons.ChevronDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = scheme.primaryText,
                            )
                        },
                        onClick = onToggleExpanded,
                    )
                }
            },
        )
    }
}

/** 更新说明正文：按块与行内 Markdown 解析，超链接可点击打开、行内代码以圆角胶囊渲染。 */
@Composable
private fun UpdateNotes(
    markdown: String,
    previewLimit: Int? = null,
) {
    val scheme = COUITheme.colorScheme
    val blocks = remember(markdown) { MarkdownBlocks.parse(markdown) }
    if (blocks.isEmpty()) {
        Text(
            text = "该版本未提供更新说明。",
            style = COUITheme.textStyles.bodyXS,
            color = scheme.labelSecondary,
        )
        return
    }
    val visibleBlocks = if (previewLimit != null) blocks.take(previewLimit) else blocks
    val bodyStyle = rememberCouiBodyTextStyle()
    visibleBlocks.forEach { block ->
        when (block) {
            is MarkdownBlock.Heading -> {
                val styled = rememberCouiNotesInline(block.text)
                val style = if (block.level <= 2) COUITheme.textStyles.headlineXS else COUITheme.textStyles.bodyL
                CouiNotesInlineText(
                    text = styled,
                    style = style,
                )
            }
            is MarkdownBlock.Bullet -> NotesLine(prefix = "·", text = block.text)
            is MarkdownBlock.Ordered -> NotesLine(prefix = block.number.toString() + ".", text = block.text)
            is MarkdownBlock.Divider -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CouiLayoutTokens.DividerThickness)
                    .background(scheme.divider),
            )
            is MarkdownBlock.Paragraph -> CouiNotesInlineText(
                text = rememberCouiNotesInline(block.text),
                style = bodyStyle,
            )
        }
    }
}

@Composable
private fun NotesLine(prefix: String, text: String) {
    val scheme = COUITheme.colorScheme
    val styled = rememberCouiNotesInline(text)
    val bodyStyle = rememberCouiBodyTextStyle()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.width(NotesPrefixMinWidth)) {
            CouiBodyText(text = prefix, color = scheme.labelSecondary)
        }
        CouiNotesInlineText(
            text = styled,
            style = bodyStyle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CouiNotesInlineText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val dark = COUITheme.isDark
    val scheme = COUITheme.colorScheme
    InlineCodeText(
        text = text,
        style = style,
        color = scheme.labelPrimary,
        codeColor = scheme.labelPrimary.copy(alpha = 0.90f),
        pillBackground = scheme.labelPrimary.copy(alpha = if (dark) 0.14f else 0.065f),
        pillBorder = scheme.divider.copy(alpha = if (dark) 0.50f else 0.42f),
        modifier = modifier,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun rememberCouiNotesInline(text: String): AnnotatedString {
    val scheme = COUITheme.colorScheme
    val dark = COUITheme.isDark
    val codeBackground = scheme.labelPrimary.copy(alpha = if (dark) 0.14f else 0.08f)
    return remember(text, scheme.primaryText, codeBackground) {
        couiInlineNotes(
            text = text,
            accentColor = scheme.primaryText,
            codeBackground = codeBackground,
        )
    }
}

/** 更新设置卡：自动检查与忽略版本开关。 */
@Composable
private fun UpdateSettingsGroup(
    autoCheckUpdate: Boolean,
    ignored: Boolean,
    ignoreSummary: String,
    ignoreEnabled: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onIgnoreToggle: () -> Unit,
) {
    PreferenceCategory(title = "更新设置")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
            .padding(bottom = CouiLayoutTokens.GroupSpacing),
    ) {
        CardListGroup {
            item(key = "auto_check") {
                SwitchPreference(
                    checked = autoCheckUpdate,
                    onCheckedChange = onAutoCheckChange,
                    title = "自动检查更新",
                    summary = "每次启动应用时自动检查最新版本",
                )
            }
            item(key = "ignore_version") {
                SwitchPreference(
                    checked = ignored,
                    onCheckedChange = { onIgnoreToggle() },
                    title = "忽略此版本",
                    summary = ignoreSummary,
                    enabled = ignoreEnabled,
                )
            }
        }
    }
}

/** 版本信息与外部仓库入口。 */
@Composable
private fun UpdateMetaGroup(
    currentVersion: String,
    state: UpdateOutcome?,
    release: ReleaseInfo?,
    onCopyVersion: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val remoteVersionText = when {
        state == null -> "检测中…"
        release != null -> release.version
        state is UpdateOutcome.Failed -> "获取失败"
        else -> "—"
    }
    PreferenceCategory(title = "版本信息")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
            .padding(bottom = CouiLayoutTokens.GroupSpacing),
    ) {
        CardListGroup {
            item(key = "current_version") {
                BasicComponent(
                    title = "当前版本",
                    summary = "点击复制版本号",
                    endActions = {
                        Text(
                            text = currentVersion,
                            style = COUITheme.textStyles.bodyM,
                            color = scheme.labelTertiary,
                        )
                    },
                    onClick = onCopyVersion,
                )
            }
            item(key = "remote_version") {
                BasicComponent(
                    title = "远端最新版本",
                    endActions = {
                        Text(
                            text = remoteVersionText,
                            style = COUITheme.textStyles.bodyM,
                            color = scheme.labelTertiary,
                        )
                    },
                )
            }
            item(key = "github_releases") {
                ArrowPreference(
                    title = "GitHub Releases",
                    summary = "在浏览器查看完整发布说明",
                    onClick = onOpenRelease,
                )
            }
        }
    }
}

/** 调试预览组：提供一键验证更新弹窗、折叠日志与模拟下载进度的入口。 */
@Composable
private fun UpdateDebugGroup(
    hasNewVersion: Boolean,
    onTestDialog: () -> Unit,
) {
    PreferenceCategory(title = "调试工具")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
            .padding(bottom = CouiLayoutTokens.GroupSpacing),
    ) {
        CardListGroup {
            item(key = "test_dialog") {
                ArrowPreference(
                    title = "测试更新弹窗",
                    summary = if (hasNewVersion) {
                        "重新弹出当前新版本更新弹窗"
                    } else {
                        "使用示例版本与发布说明预览更新弹窗"
                    },
                    onClick = onTestDialog,
                )
            }
        }
    }
}

/** 更新说明卡的标题：带版本号，让用户一眼看到看的是哪一版；区分本地领先与常规最新。 */
private fun changelogTitle(state: UpdateOutcome?): String = couiUpdateChangelogTitle(state)

internal enum class CouiUpdateActionBarMode {
    CHECKING,
    DOWNLOADING,
    READY_TO_INSTALL,
    DOWNLOAD_FAILED,
    AVAILABLE_IDLE,
    CHECK_FAILED,
    UP_TO_DATE,
}

internal fun couiUpdateHeadline(state: UpdateOutcome?): String = when (state) {
    null -> "正在检测更新"
    is UpdateOutcome.Available -> "发现新版本"
    is UpdateOutcome.Failed -> "检查更新失败"
    is UpdateOutcome.UpToDate -> if (state.localAhead) "当前版本已领先" else "已是最新版本"
}

internal fun couiUpdateVersionLine(currentVersion: String, state: UpdateOutcome?): String = when (state) {
    is UpdateOutcome.Available -> "软件版本  v$currentVersion → v${state.release.version}"
    is UpdateOutcome.UpToDate -> if (state.localAhead) {
        "本地 v$currentVersion · 远端 v${state.release.version}"
    } else {
        "软件版本  v$currentVersion"
    }
    else -> "软件版本  v$currentVersion"
}

internal fun couiUpdateChangelogTitle(state: UpdateOutcome?): String = when (state) {
    is UpdateOutcome.Available -> "更新说明（${state.release.version}）"
    is UpdateOutcome.UpToDate -> if (state.localAhead) {
        "最新公开发布说明（${state.release.version}）"
    } else {
        "当前版本说明（${state.release.version}）"
    }
    else -> "更新说明"
}

internal fun couiUpdateProgressFraction(downloadState: UpdateDownloadState): Float? = when (downloadState) {
    is UpdateDownloadState.Downloading ->
        if (downloadState.total > 0L) UpdateProgress.fraction(downloadState.downloaded, downloadState.total) else null
    is UpdateDownloadState.Done -> 1f
    else -> 0f
}

internal fun couiUpdateActionBarMode(
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
): CouiUpdateActionBarMode = when {
    state == null -> CouiUpdateActionBarMode.CHECKING
    downloadState is UpdateDownloadState.Downloading -> CouiUpdateActionBarMode.DOWNLOADING
    downloadState is UpdateDownloadState.Done -> CouiUpdateActionBarMode.READY_TO_INSTALL
    downloadState is UpdateDownloadState.Failed -> CouiUpdateActionBarMode.DOWNLOAD_FAILED
    state is UpdateOutcome.Available -> CouiUpdateActionBarMode.AVAILABLE_IDLE
    state is UpdateOutcome.Failed -> CouiUpdateActionBarMode.CHECK_FAILED
    else -> CouiUpdateActionBarMode.UP_TO_DATE
}

internal fun couiInlineNotes(
    text: String,
    accentColor: Color,
    codeBackground: Color,
): AnnotatedString = parseInlineNotes(
    text = text,
    codeColor = accentColor,
    linkColor = accentColor,
    codeBackground = codeBackground,
)

internal fun couiChangelogDefaultExpanded(
    state: UpdateOutcome?,
    blockCount: Int,
): Boolean = when (state) {
    is UpdateOutcome.Available -> true
    is UpdateOutcome.UpToDate -> blockCount <= CHANGELOG_PREVIEW_BLOCK_LIMIT
    else -> false
}

internal fun couiChangelogCollapsible(blockCount: Int): Boolean =
    blockCount > 0

internal fun couiChangelogPreviewLimit(
    blockCount: Int,
    expanded: Boolean,
): Int? = when {
    expanded -> null
    blockCount > CHANGELOG_PREVIEW_BLOCK_LIMIT -> 2
    else -> 0
}

internal fun couiChangelogCollapsedHiddenCount(blockCount: Int): Int =
    (blockCount - (couiChangelogPreviewLimit(blockCount, expanded = false) ?: blockCount)).coerceAtLeast(0)

internal fun couiUpdateButtonLabel(
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
): String = when {
    state == null -> "正在检测更新…"
    downloadState is UpdateDownloadState.Downloading -> {
        if (downloadState.total > 0L) {
            val pct = UpdateProgress.percent(downloadState.progress)
            "正在下载… $pct%"
        } else {
            "正在下载… ${formatUpdateSize(downloadState.downloaded)}"
        }
    }
    downloadState is UpdateDownloadState.Done -> "下载完成，立即安装"
    downloadState is UpdateDownloadState.Failed -> "重试下载"
    state is UpdateOutcome.Available -> if (state.release.apk != null) "下载并安装" else "前往 Releases 下载"
    state is UpdateOutcome.Failed -> "重新检查更新"
    else -> "检查更新"
}

internal fun couiUpdateReleaseMetaLine(release: ReleaseInfo): String = buildList {
    if (release.publishedDate.isNotBlank()) add("发布于 " + release.publishedDate)
    release.apk?.takeIf { it.size > 0L }?.let { add(formatUpdateSize(it.size)) }
}.joinToString(" | ")

internal fun couiNextPreviewVersion(currentVersion: String): String {
    val normalized = VersionText.normalized(currentVersion)
    if (normalized.isBlank()) return "1.0.1"
    val core = normalized.substringBefore('-').trim()
    val parts = core.split('.').mapNotNull { it.toIntOrNull() }
    return when {
        parts.size >= 3 -> "${parts[0]}.${parts[1]}.${parts[2] + 1}"
        parts.size == 2 -> "${parts[0]}.${parts[1]}.1"
        parts.size == 1 -> "${parts[0]}.0.1"
        else -> "$normalized-preview"
    }
}

internal fun couiPreviewUpdateRelease(
    currentVersion: String,
    remoteRelease: ReleaseInfo?,
): ReleaseInfo {
    val previewVersion = couiNextPreviewVersion(currentVersion)
    val fallbackAsset = ReleaseAsset(
        name = "jm3-v$previewVersion-universal.apk",
        size = 24L * 1024 * 1024,
        downloadUrl = UpdateChecker.RELEASES_PAGE_URL,
    )
    val sampleNotes = """
        ### 界面与交互优化
        - 检查更新主按钮接入 **COUI 凝光玻璃** 悬浮材质与跟手光效
        - 新增 `CouiInstallProgressButton` 双遍裁剪反白进度与不定态流光
        - 优化更新日志展开/收起行与弹窗内 `忽略此版本` 交互质感

        ### 修复与稳定性
        - 修复总长度未知时下载进度显示 `0%` 的问题
        - 完整发布记录请见 [GitHub Releases](${UpdateChecker.RELEASES_PAGE_URL})
    """.trimIndent()
    val baseNotes = remoteRelease?.notes?.trim().orEmpty()
    val previewNotes = when {
        baseNotes.lines().count { it.isNotBlank() } >= 6 -> baseNotes
        baseNotes.isNotBlank() -> "$baseNotes\n\n$sampleNotes"
        else -> sampleNotes
    }
    return ReleaseInfo(
        tagName = "v$previewVersion",
        name = "预览版本 v$previewVersion",
        notes = previewNotes,
        htmlUrl = remoteRelease?.htmlUrl?.takeIf { it.isNotBlank() } ?: UpdateChecker.RELEASES_PAGE_URL,
        publishedAt = remoteRelease?.publishedAt?.takeIf { it.isNotBlank() } ?: "2026-11-12T12:00:00Z",
        assets = remoteRelease?.assets?.takeIf { it.any { asset -> asset.name.endsWith(".apk", ignoreCase = true) } }
            ?: listOf(fallbackAsset),
    )
}
