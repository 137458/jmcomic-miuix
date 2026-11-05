package com.jm3.core

import com.jm3.core.model.Jm3User
import com.jm3.core.model.UserProfile
import io.github.jukomu.jmcomic.api.model.JmUserProfile
import io.github.jukomu.jmcomic.api.model.JmUserInfo
import kotlin.math.roundToInt

fun JmUserInfo.toUser(): Jm3User = Jm3User(
    uid = uid,
    username = username,
    email = email,
    emailVerified = emailVerified,
    photoUrl = avatarUrl,
    coin = coin,
    albumFavorites = albumFavorites,
    maxAlbumFavorites = maxAlbumFavorites,
    level = level,
    levelName = levelName,
    nextLevelExp = nextLevelExp,
    currentExp = currentExp,
    expPercent = expPercent.roundToInt(),
)

/** 个人资料：空字段统一为 empty string，避免 UI 出现 null 分支。 */
fun JmUserProfile.toUserProfile(): UserProfile = UserProfile(
    username = username().orEmpty(),
    email = email().orEmpty(),
    nickname = nickname().orEmpty(),
    lastName = lastName().orEmpty(),
    firstName = firstName().orEmpty(),
    birthday = birthday().orEmpty(),
    relations = relations().orEmpty(),
    sexuality = sexuality().orEmpty(),
    website = website().orEmpty(),
    birthPlace = birthPlace().orEmpty(),
    city = city().orEmpty(),
    country = country().orEmpty(),
    occupation = occupation().orEmpty(),
    company = company().orEmpty(),
    school = school().orEmpty(),
    aboutMe = aboutMe().orEmpty(),
    infoHere = infoHere().orEmpty(),
    collections = collections().orEmpty(),
    ideal = ideal().orEmpty(),
    erogenic = erogenic().orEmpty(),
    favorite = favorite().orEmpty(),
    hate = hate().orEmpty(),
)
