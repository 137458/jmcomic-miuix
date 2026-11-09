package com.jm3.app.miuix.ui

import com.jm3.core.runCatchingSuspending
import com.jm3.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 搜索历史读写的统一入口：写入后重新读回最新快照交给屏幕，UI 只拿结果。
 *
 * 两套皮肤的首页都需要这一套“先切 IO、写完再读”的流程，共用一份实现，
 * 免得同一段逐行相同的代码在两处各自演化。读回失败时回落到调用方当前列表。
 */
internal suspend fun mutateSearchHistory(
    settings: SettingsRepository,
    current: () -> List<String>,
    block: suspend () -> Unit,
): List<String> = withContext(Dispatchers.IO) {
    runCatchingSuspending { block() }
    runCatchingSuspending { settings.searchHistory() }.getOrDefault(current())
}

/** 只读一次最新快照，读不到就回落到调用方当前列表。 */
internal suspend fun readSearchHistory(
    settings: SettingsRepository,
    current: () -> List<String>,
): List<String> = withContext(Dispatchers.IO) {
    runCatchingSuspending { settings.searchHistory() }.getOrDefault(current())
}
