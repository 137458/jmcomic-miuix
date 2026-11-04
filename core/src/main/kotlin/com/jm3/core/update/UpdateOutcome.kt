package com.jm3.core.update

/**
 * 一次检查更新的结论：界面要展示的状态与"要不要打扰用户"在这里一次性算完。
 *
 * 取舍：把 [UpdatePrompt.shouldNotify] 的结果作为 [Available.notify] 带在结果里，而不是让调用方
 * 各自再判一次。两处调用点（启动期静默检查、更新页手动检查）原先各写一套判断，页面那套只判了
 * "已忽略"却照样记下"已提示"，于是同一版本在启动期弹过之后进页面还会再弹一次。
 */
sealed interface UpdateOutcome {

    /**
     * 远端有新版本。
     *
     * [notify] 为 false 表示只更新状态、不弹窗：本版本已被忽略，或本进程已经提示过一次。
     * 写"已提示"记录也必须以此为准，否则会把下次启动的提醒吞掉。
     */
    data class Available(val release: ReleaseInfo, val notify: Boolean) : UpdateOutcome

    /**
     * 远端不高于本地，仍带 [release] 用于展示发布说明。
     *
     * [localAhead] 为 true 表示本地版本更高（装了开发版/预发布版），此时不该反向提示"降级"。
     */
    data class UpToDate(val release: ReleaseInfo, val localAhead: Boolean) : UpdateOutcome

    /** 检查失败，[message] 可直接展示给用户。 */
    data class Failed(val failure: UpdateFailure, val message: String) : UpdateOutcome
}

/** 提示策略需要的本地状态：调用方从设置里读出来的纯值，:core 不碰持久化。 */
data class UpdatePromptState(
    /** 用户点过"忽略此版本"的版本号，未忽略为 null */
    val ignoredVersion: String? = null,
    /** 已经弹过窗的版本号，冷启动前为 null */
    val promptedVersion: String? = null,
)

/**
 * 检查更新的唯一入口：网络传输由注入的 [check] 承担，决策（版本关系 → 界面态 → 要不要弹窗）
 * 全部留在这里，因此两处调用点只需要各自处理"拿到的结论怎么显示"。
 *
 * [check] 默认用 [UpdateChecker]，测试注入替身即可不联网。
 */
class UpdateCenter(
    private val check: suspend (localVersion: String) -> UpdateCheckResult = UpdateChecker()::check,
) {

    /**
     * 检查一次并给出结论。
     *
     * 一次调用最多发一次请求；调用方应已经在 IO 线程（ENGINEERING.md 二、1）。
     */
    suspend fun evaluate(localVersion: String, prompt: UpdatePromptState): UpdateOutcome =
        when (val result = check(localVersion)) {
            is UpdateCheckResult.Success -> when (result.relation) {
                VersionRelation.NEWER -> UpdateOutcome.Available(
                    release = result.release,
                    notify = UpdatePrompt.shouldNotify(
                        latestVersion = result.release.version,
                        ignoredVersion = prompt.ignoredVersion,
                        promptedVersion = prompt.promptedVersion,
                    ),
                )

                VersionRelation.SAME -> UpdateOutcome.UpToDate(result.release, localAhead = false)
                VersionRelation.OLDER -> UpdateOutcome.UpToDate(result.release, localAhead = true)
            }

            is UpdateCheckResult.Failed -> UpdateOutcome.Failed(result.failure, result.message)
        }

    /**
     * 启动期的静默检查：返回本次该弹窗的新版本，null 表示不该有任何界面动作。
     *
     * 未开启自动检查时直接返回 null，请求都不发——启动路径上不该为关掉的开关付一次网络往返。
     * 调用方拿到非 null 后应先记下"已提示"，再弹窗。
     */
    suspend fun promptForStartup(
        autoCheckEnabled: Boolean,
        localVersion: String,
        prompt: UpdatePromptState,
    ): ReleaseInfo? {
        if (!autoCheckEnabled) return null
        val outcome = evaluate(localVersion, prompt)
        return (outcome as? UpdateOutcome.Available)?.takeIf { it.notify }?.release
    }
}
