package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.jm3.app.miuix.ui.components.ActionCell
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.MinTouchTarget
import com.jm3.app.miuix.ui.glass.GlassRole
import com.jm3.app.miuix.ui.glass.GlassSmallTopAppBar
import com.jm3.app.miuix.ui.glass.glassSurface
import com.jm3.core.model.Chapter
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 阅读器顶栏与底栏：沉浸页不折叠的玻璃顶栏，以及底部悬浮工具栏的覆盖层。 */

/**
 * 阅读页顶栏：书名 + 上一话/页码/下一话。页码按钮只负责打开跳页器，
 * 读数由调用方在点击瞬间取，避免把旧页码带进选择器。
 */
@Composable
internal fun ReaderTopBar(
    title: String,
    pageLabel: String,
    visible: Boolean,
    prevChapter: Chapter?,
    nextChapter: Chapter?,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenPagePicker: () -> Unit,
) {
    if (visible) {
        // 阅读页顶栏不折叠：大标题在长书名下会吃掉三行，与看图抢空间
        GlassSmallTopAppBar(
            title = title,
            navigationIcon = { BackButton(onBack = onBack) },
            // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
            color = Color.Transparent,
            actions = {
                IconButton(onClick = { prevChapter?.let { onOpenChapter(it.id) } }, enabled = prevChapter != null) {
                    Icon(imageVector = MiuixIcons.ChevronBackward, contentDescription = "上一话")
                }
                MinTouchTarget(
                    onClick = {
                        onOpenPagePicker()
                    },
                ) {
                    TextButton(
                        text = pageLabel,
                        onClick = {
                            onOpenPagePicker()
                        },
                        minWidth = 0.dp,
                        minHeight = 36.dp,
                    )
                }
                IconButton(onClick = { nextChapter?.let { onOpenChapter(it.id) } }, enabled = nextChapter != null) {
                    Icon(imageVector = MiuixIcons.ChevronForward, contentDescription = "下一话")
                }
            },
        )
    }
}

/** 阅读器底部悬浮工具栏：模式切换/目录/评论/收藏/喜欢（原版 ReadNav 底部动作行）。 */
@Composable
internal fun ReaderToolbar(
    horizontal: Boolean,
    favorited: Boolean,
    liked: Boolean,
    backdrop: LayerBackdrop?,
    onToggleMode: () -> Unit,
    onChapters: () -> Unit,
    onComments: () -> Unit,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
) {
    // 三态降级交给 glassSurface 的 Toolbar 角色：液态档是 22dp 透镜 + 0.78 底衬，
    // 无着色器退 22dp 模糊，再无采样源退实色；形状与 FloatingToolbar 的胶囊保持一致
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .glassSurface(
                backdrop = backdrop,
                role = GlassRole.Toolbar,
                tint = MiuixTheme.colorScheme.surfaceContainer,
                shape = remember { RoundedCornerShape(50.dp) },
            ),
    ) {
        // 底衬与高光由 glassSurface 画在内容之下，这里不能再盖一层半透明底色
        FloatingToolbar(
            color = Color.Transparent,
            outSidePadding = PaddingValues(0.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionCell(
                    icon = if (horizontal) MiuixIcons.VerticalSplit else MiuixIcons.HorizontalSplit,
                    label = if (horizontal) "直向阅读" else "左右阅读",
                    onClick = onToggleMode,
                    modifier = Modifier.width(66.dp),
                )
                ActionCell(icon = MiuixIcons.ListView, label = "章节", onClick = onChapters, modifier = Modifier.width(66.dp))
                ActionCell(icon = MiuixIcons.Messages, label = "评论", onClick = onComments, modifier = Modifier.width(66.dp))
                ActionCell(
                    icon = if (favorited) MiuixIcons.FolderFill else MiuixIcons.Folder,
                    label = "收藏",
                    active = favorited,
                    onClick = onFavorite,
                    modifier = Modifier.width(66.dp),
                )
                ActionCell(
                    icon = if (liked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                    label = "喜欢",
                    active = liked,
                    onClick = onLike,
                    modifier = Modifier.width(66.dp),
                )
            }
        }
    }
}

/** 底栏覆盖层：GlassScaffold 没有 floatingToolbar 槽，改为内容层底部居中，位置与原先一致。 */
@Composable
internal fun ReaderToolbarOverlay(
    visible: Boolean,
    horizontal: Boolean,
    favorited: Boolean,
    liked: Boolean,
    toolbarBackdrop: LayerBackdrop?,
    bottomInset: Dp,
    onToggleMode: () -> Unit,
    onChapters: () -> Unit,
    onComments: () -> Unit,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
) {
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInset + 4.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ReaderToolbar(
                horizontal = horizontal,
                favorited = favorited,
                liked = liked,
                backdrop = toolbarBackdrop,
                onToggleMode = onToggleMode,
                onChapters = onChapters,
                onComments = onComments,
                onFavorite = onFavorite,
                onLike = onLike,
            )
        }
    }
}

