// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.coui.component.CouiAvatar
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiSectionTitleText
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.profile.ProfileSelector
import com.jm3.app.miuix.ui.profile.ProfileUiState
import com.jm3.app.miuix.ui.profile.options
import com.jm3.app.miuix.ui.profile.rememberProfileScaffoldState
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.route.AppRoutes
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.PagerFooterPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 输入行与卡片同源的圆角：由库的卡片半径派生，避免另写字面量。 */
private val ProfileInputShape = SquircleShape(
    cornerRadius = CouiLayoutTokens.CardCorner / 2,
    weight = CouiLayoutTokens.CardCornerWeight,
)

private val ProfileHeaderShape = SquircleShape(
    cornerRadius = CouiLayoutTokens.CardCorner,
    weight = 3f,
)

/**
 * COUI 个人资料页：顶部头图卡 + 四组资料行。
 *
 * 资料只从 [ScreenDeps.account] 与网关读写；未登录时不发请求，直接落到「请先登录」态，
 * 并把重新登录的入口经 [nav] 交给路由。枚举字段走 [OverlayDropdownPreference]，
 * 自由文本走卡片分组内的单行/多行输入行。
 */
@Composable
internal fun CouiProfileScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    nav: AppRoutes,
) {
    val state = rememberProfileScaffoldState(deps = deps, snackbarHostState = snackbarHostState)
    val user = state.user
    val fields = state.fields
    val saving = state.saving

    CouiTheme {
        val scrollBehavior = rememberCouiPageScrollBehavior()

        Box(modifier = Modifier.fillMaxSize()) {
            CouiPageScaffold(
                title = "个人资料",
                onBack = onBack,
                wide = isCouiWideWindow(),
                scrollBehavior = scrollBehavior,
            ) { contentPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .couiPageScroll()
                        .consumeWindowInsets(contentPadding),
                    contentPadding = contentPadding,
                    flingBehavior = rememberCOUIFlingBehavior(),
                ) {
                    when (val current = state.uiState) {
                        ProfileUiState.Loading -> item(key = "profile-loading") {
                            CouiLoadingGroup(
                                modifier = Modifier.padding(top = 8.dp),
                                text = "正在加载个人资料…",
                            )
                        }

                        is ProfileUiState.Failed -> item(key = "profile-failed") {
                            if (current.sessionExpired) {
                                CouiFeedLoginGroup(
                                    title = "登录已过期",
                                    message = current.message,
                                    onOpenLogin = nav::login,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            } else {
                                CouiErrorGroup(
                                    message = current.message,
                                    onRetry = { state.load() },
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }

                        ProfileUiState.Loaded -> profileLoadedItems(
                            username = user?.username.orEmpty(),
                            email = user?.email.orEmpty(),
                            photoUrl = user?.photoUrl,
                            fields = fields,
                            saving = saving,
                            onSave = state::save,
                        )
                    }
                }
            }

            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

/** 资料正文：头图卡 + 四组字段 + 保存行。 */
private fun LazyListScope.profileLoadedItems(
    username: String,
    email: String,
    photoUrl: String?,
    fields: MutableMap<String, String>,
    saving: Boolean,
    onSave: () -> Unit,
) {
    item(key = "profile-header") {
        ProfileHeader(username = username, email = email, photoUrl = photoUrl)
    }

    item(key = "profile-account") {
        PreferenceCategory(title = "账号信息")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                .padding(bottom = CouiLayoutTokens.GroupSpacing),
        ) {
            CardListGroup {
                item(key = "username") {
                    ProfileReadonlyRow(label = "用户名", value = username)
                }
                item(key = "email") {
                    ProfileReadonlyRow(label = "邮箱", value = email.ifEmpty { "未绑定" })
                }
            }
        }
    }

    item(key = "profile-basic") {
        PreferenceCategory(title = "基本信息")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                .padding(bottom = CouiLayoutTokens.GroupSpacing),
        ) {
            CardListGroup {
                item(key = "nickname") {
                    ProfileFieldRow(fields = fields, key = "nickname", label = "昵称")
                }
                item(key = "lastName") {
                    ProfileFieldRow(fields = fields, key = "lastName", label = "姓")
                }
                item(key = "firstName") {
                    ProfileFieldRow(fields = fields, key = "firstName", label = "名")
                }
                item(key = "birthday") {
                    ProfileFieldRow(fields = fields, key = "birthday", label = "生日", placeholder = "YYYY-MM-DD")
                }
                item(key = "relations") {
                    ProfileChoiceRow(fields = fields, selector = ProfileSelector.RELATIONS)
                }
                item(key = "sexuality") {
                    ProfileChoiceRow(fields = fields, selector = ProfileSelector.SEXUALITY)
                }
                item(key = "website") {
                    ProfileFieldRow(fields = fields, key = "website", label = "个人网站", placeholder = "https://")
                }
            }
        }
    }

    item(key = "profile-location") {
        PreferenceCategory(title = "位置信息")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                .padding(bottom = CouiLayoutTokens.GroupSpacing),
        ) {
            CardListGroup {
                item(key = "birthPlace") {
                    ProfileFieldRow(fields = fields, key = "birthPlace", label = "出生地")
                }
                item(key = "city") {
                    ProfileFieldRow(fields = fields, key = "city", label = "城市")
                }
                item(key = "country") {
                    ProfileFieldRow(fields = fields, key = "country", label = "国家")
                }
                item(key = "occupation") {
                    ProfileFieldRow(fields = fields, key = "occupation", label = "职业")
                }
                item(key = "company") {
                    ProfileFieldRow(fields = fields, key = "company", label = "公司")
                }
                item(key = "school") {
                    ProfileFieldRow(fields = fields, key = "school", label = "学校")
                }
            }
        }
    }

    item(key = "profile-other") {
        PreferenceCategory(title = "其他信息")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                .padding(bottom = CouiLayoutTokens.GroupSpacing),
        ) {
            CardListGroup {
                item(key = "aboutMe") {
                    ProfileFieldRow(fields = fields, key = "aboutMe", label = "关于我", multiline = true)
                }
                item(key = "infoHere") {
                    ProfileFieldRow(fields = fields, key = "infoHere", label = "其他信息", multiline = true)
                }
                item(key = "collections") {
                    ProfileFieldRow(fields = fields, key = "collections", label = "收藏类别", multiline = true)
                }
                item(key = "ideal") {
                    ProfileFieldRow(fields = fields, key = "ideal", label = "理想型", multiline = true)
                }
                item(key = "erogenic") {
                    ProfileFieldRow(fields = fields, key = "erogenic", label = "敏感区域", multiline = true)
                }
                item(key = "favorite") {
                    ProfileFieldRow(fields = fields, key = "favorite", label = "最喜欢", multiline = true)
                }
                item(key = "hate") {
                    ProfileFieldRow(fields = fields, key = "hate", label = "最讨厌", multiline = true)
                }
            }
        }
    }

    item(key = "profile-save") {
        val scheme = COUITheme.colorScheme
        PreferenceCategory(title = "保存")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
        ) {
            CardListGroup {
                item(key = "save") {
                    BasicComponent(
                        title = if (saving) "保存中…" else "保存修改",
                        titleColor = BasicComponentDefaults.titleColor(
                            color = scheme.primaryText,
                            disabledColor = scheme.disabledNeutral,
                        ),
                        endActions = {
                            if (saving) {
                                InfiniteProgressIndicator(
                                    size = 18.dp,
                                    strokeWidth = 2.dp,
                                    color = scheme.primary,
                                )
                            } else {
                                Icon(
                                    imageVector = CouiIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(CouiLayoutTokens.IconSize),
                                    tint = scheme.primaryText,
                                )
                            }
                        },
                        enabled = !saving,
                        onClick = onSave,
                    )
                }
            }
        }
        PagerFooterPreference(
            summary = if (saving) "正在提交…" else "资料只写入你自己的账号",
        )
    }
}

/** 头图：有头像就铺满，没有就用主题色渐变兜底；底部压一层暗色渐变保证文字可读。 */
@Composable
private fun ProfileHeader(username: String, email: String, photoUrl: String?) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
            .padding(top = 8.dp, bottom = CouiLayoutTokens.GroupSpacing)
            .height(176.dp)
            .clip(ProfileHeaderShape),
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(scheme.primary.copy(alpha = 0.85f), scheme.cardBackground),
                        ),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(CouiLayoutTokens.RowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CouiAvatar(url = photoUrl, size = 64.dp, name = username)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CouiSectionTitleText(
                    modifier = Modifier.fillMaxWidth(),
                    text = username.ifEmpty { "未命名" },
                    color = Color.White,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = email.ifEmpty { "未绑定邮箱" },
                    style = COUITheme.textStyles.bodyS,
                    color = Color.White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 只读行：服务端决定的值不给输入口，只摆出来。 */
@Composable
private fun ProfileReadonlyRow(label: String, value: String) {
    BasicComponent(
        title = label,
        summary = value,
    )
}

/** 自由文本输入行：标题在上、输入框在 [BasicComponent] 底部区域，多行字段给更高的触区。 */
@Composable
private fun ProfileFieldRow(
    fields: MutableMap<String, String>,
    key: String,
    label: String,
    placeholder: String = "",
    multiline: Boolean = false,
) {
    val scheme = COUITheme.colorScheme
    BasicComponent(
        title = label,
        bottomAction = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ProfileInputShape)
                    .background(scheme.labelPrimary.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = fields[key].orEmpty(),
                    onValueChange = { fields[key] = it },
                    textStyle = rememberCouiBodyTextStyle().copy(color = scheme.labelPrimary),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (multiline) 64.dp else 22.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (fields[key].isNullOrEmpty() && placeholder.isNotEmpty()) {
                                CouiBodyText(
                                    text = placeholder,
                                    color = scheme.labelSecondary.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Start,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        },
    )
}

/** 枚举字段行：基于 [OverlayDropdownPreference]，点开在触点弹出选项菜单。 */
@Composable
private fun ProfileChoiceRow(fields: MutableMap<String, String>, selector: ProfileSelector) {
    val options = selector.options()
    val current = fields[selector.field].orEmpty()
    val selectedIndex = options.indexOfFirst { it.first == current }.let { if (it >= 0) it else 0 }
    OverlayDropdownPreference(
        items = options.map { it.second },
        selectedIndex = selectedIndex,
        title = selector.title,
        summary = "点选后随资料一起保存",
        onSelectedIndexChange = { index ->
            val next = options.getOrNull(index)?.first ?: return@OverlayDropdownPreference
            if (next != current) fields[selector.field] = next
        },
    )
}
