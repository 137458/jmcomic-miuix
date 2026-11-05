package com.jm3.core

import com.jm3.core.model.CreatorAuthorWorks
import com.jm3.core.model.CreatorFilters
import com.jm3.core.model.CreatorMeta
import com.jm3.core.model.CreatorPage
import com.jm3.core.model.CreatorRelatedWork
import com.jm3.core.model.CreatorSponsor
import com.jm3.core.model.CreatorWork
import com.jm3.core.model.CreatorWorkMeta
import com.jm3.core.model.CreatorWorkPage
import io.github.jukomu.jmcomic.api.model.JmCreatorAuthorWorksPage
import io.github.jukomu.jmcomic.api.model.JmCreatorMeta
import io.github.jukomu.jmcomic.api.model.JmCreatorPage
import io.github.jukomu.jmcomic.api.model.JmCreatorRelatedWork
import io.github.jukomu.jmcomic.api.model.JmCreatorSponsor
import io.github.jukomu.jmcomic.api.model.JmCreatorWorkDetail
import io.github.jukomu.jmcomic.api.model.JmCreatorWorkInfo
import io.github.jukomu.jmcomic.api.model.JmCreatorWorkMeta
import io.github.jukomu.jmcomic.api.model.JmCreatorWorkPage

/** 接口 filters 是 Map<String, Object>，只取语言与来源两组字符串选项。 */
fun creatorFilters(filters: Map<String, Any?>?): CreatorFilters {
    fun read(key: String): List<String> = (filters?.get(key) as? List<*>)
        .orEmpty()
        .mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        .distinct()
    return CreatorFilters(languages = read("language"), sources = read("source"))
}

fun JmCreatorMeta.toCreatorMeta(covers: CoverResolver = CoverResolver.Unready): CreatorMeta = CreatorMeta(
    id = getId().orEmpty(),
    name = getName().orEmpty(),
    avatarUrl = covers.image(getAvatarImage()),
    backgroundUrl = covers.image(getBackgroundImage()),
    updateText = getUpdateDate()?.takeIf { it.isNotBlank() },
)

fun JmCreatorPage.toCreatorPage(covers: CoverResolver = CoverResolver.Unready): CreatorPage = CreatorPage(
    items = getList().orEmpty().map { it.toCreatorMeta(covers) },
    total = getTotal(),
)

fun JmCreatorWorkMeta.toCreatorWorkMeta(covers: CoverResolver = CoverResolver.Unready): CreatorWorkMeta = CreatorWorkMeta(
    id = getId().orEmpty(),
    title = getName().orEmpty(),
    coverUrl = covers.image(getWorkImage()),
    platform = getPlatformName()?.takeIf { it.isNotBlank() },
    workDate = getWorkDate()?.takeIf { it.isNotBlank() },
    authorName = getAuthorName()?.takeIf { it.isNotBlank() },
    authorId = getAuthorId()?.takeIf { it.isNotBlank() },
)

fun JmCreatorWorkPage.toCreatorWorkPage(covers: CoverResolver = CoverResolver.Unready): CreatorWorkPage = CreatorWorkPage(
    items = getList().orEmpty().map { it.toCreatorWorkMeta(covers) },
    total = getTotal(),
    filters = creatorFilters(getFilters()),
)

fun JmCreatorRelatedWork.toCreatorRelatedWork(covers: CoverResolver = CoverResolver.Unready): CreatorRelatedWork =
    CreatorRelatedWork(
        id = getId().orEmpty(),
        title = getWorkTitle().orEmpty(),
        coverUrl = covers.image(getWorkImage()),
        workDate = getWorkDate()?.takeIf { it.isNotBlank() },
        platform = getPlatformName()?.takeIf { it.isNotBlank() },
    )

fun JmCreatorSponsor.toCreatorSponsor(): CreatorSponsor = CreatorSponsor(
    name = getPlatformName().orEmpty(),
    url = getPlatformUrl()?.takeIf { it.isNotBlank() },
)

fun JmCreatorAuthorWorksPage.toCreatorAuthorWorks(covers: CoverResolver = CoverResolver.Unready): CreatorAuthorWorks =
    CreatorAuthorWorks(
        authorName = getAuthorName().orEmpty(),
        avatarUrl = covers.image(getAuthorAvatar()),
        backgroundUrl = covers.image(getBackgroundImage()),
        sponsors = getSponsors().orEmpty().map { it.toCreatorSponsor() },
        works = getRelatedWorks().orEmpty().map { it.toCreatorRelatedWork(covers) },
        filters = creatorFilters(getFilters()),
    )

/** 作品弹窗信息与整页详情合并成一个 UI 模型；任一侧字段缺失时用另一侧兜底。 */
fun toCreatorWork(
    info: JmCreatorWorkInfo?,
    detail: JmCreatorWorkDetail?,
    covers: CoverResolver = CoverResolver.Unready,
): CreatorWork = CreatorWork(
    id = detail?.getId().orEmpty(),
    title = info?.getWorkTitle().orEmpty().ifBlank { detail?.getName().orEmpty() },
    authorName = info?.getAuthorName()?.takeIf { it.isNotBlank() },
    workDate = info?.getWorkDate()?.takeIf { it.isNotBlank() }
        ?: detail?.getAdddt()?.takeIf { it.isNotBlank() },
    contentText = htmlToText(detail?.getContent()),
    images = detail?.getImages().orEmpty()
        .sortedBy { it.getPage() }
        .mapNotNull { covers.image(it.getImage()) },
    related = info?.getRelatedWorks().orEmpty().map { it.toCreatorRelatedWork(covers) },
)
