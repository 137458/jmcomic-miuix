package com.jm3.app.miuix.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/**
 * 应用内安装已下载的 APK。
 *
 * 取舍一：走 FileProvider 而不是 file:// Uri。Android 7.0 起直接传 file:// 会抛
 * FileUriExposedException，本应用 minSdk 31，必然要用 content:// 加临时读授权。
 *
 * 取舍二：先判断 canRequestPackageInstalls()，未授权时打开系统"安装未知应用"设置页并返回
 * [Result.PermissionRequired]，而不是静默失败。安装未知来源属于受限权限，只能由用户在
 * 系统设置里授予，应用无法自行请求；把这条路径显式返回给界面，才能提示"先去授权"。
 *
 * 取舍三：返回 sealed [Result] 而不是抛异常，界面按分支决定提示与重试，
 * 与 core 的 UpdateCheckResult 风格一致。
 */
object ApkInstaller {

    /** 与 AndroidManifest.xml 中 provider 的 authorities 后缀保持一致 */
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    /** 安装动作的结果。[Launched] 只代表系统安装器被拉起，用户取消安装本层无从得知。 */
    sealed interface Result {

        /** 已拉起系统安装器 */
        data object Launched : Result

        /** 待装包的签名与本机已安装版本不是同一发布者，系统必然拒绝覆盖安装 */
        data class SignerMismatch(val message: String) : Result

        /** 已打开"安装未知应用"授权页；用户授权后需再次调用 [install] */
        data object PermissionRequired : Result

        /** 未能拉起安装流程，[message] 可直接展示 */
        data class Failed(val message: String) : Result
    }

    /** 是否已获得安装未知应用的权限；该 API 自 API 26 提供，低于当前 minSdk 31，无需版本判断。 */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * 安装 [file]。
     *
     * 内部会 startActivity，需在主线程调用；失败原因走返回值，不抛异常。
     */
    fun install(context: Context, file: File): Result {
        // isFile 同时覆盖"不存在"和"是目录"两种情况
        if (!file.isFile) return Result.Failed("安装包不存在，请重新下载")

        // 签名不一致时系统会拒绝覆盖安装，而本应用不允许备份，用户按提示卸载就会丢数据；
        // 在拉起安装器之前拦下，把「为什么装不上」直接说清楚。
        when (val check = signatureCheck(context, file)) {
            SignatureCheck.SameSigner -> Unit
            SignatureCheck.Unknown -> return Result.Failed("安装包信息无法解析，请重新下载")
            is SignatureCheck.Mismatch -> return Result.SignerMismatch(
                "安装包签名与当前应用不一致（发布者不同），已停止安装；如需换装须先卸载，本地数据会被清空",
            )
        }

        if (!canInstall(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                Result.PermissionRequired
            } catch (error: Exception) {
                Result.Failed("无法打开安装未知应用设置：${error.reason()}")
            }
        }

        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
            Result.Launched
        } catch (error: ActivityNotFoundException) {
            Result.Failed("无法启动安装器：${error.reason("系统中没有可处理安装包的应用")}")
        } catch (error: IllegalArgumentException) {
            // FileProvider 收到不在暴露目录内的文件时会抛 IllegalArgumentException
            Result.Failed("无法启动安装器：${error.reason("安装包不在可共享目录内")}")
        } catch (error: Exception) {
            Result.Failed("无法启动安装器：${error.reason()}")
        }
    }

    /** 签名比对结论。[Unknown] 表示拿不到签名信息，按「无法证明同源」处理。 */
    private sealed interface SignatureCheck {
        data object SameSigner : SignatureCheck
        data object Unknown : SignatureCheck
        data class Mismatch(val current: Set<String>, val incoming: Set<String>) : SignatureCheck
    }

    private fun signatureCheck(context: Context, file: File): SignatureCheck {
        val incoming = archiveSignerSha256(context, file) ?: return SignatureCheck.Unknown
        val current = installedSignerSha256(context) ?: return SignatureCheck.Unknown
        return if (ApkSignature.sameSigner(current, incoming)) {
            SignatureCheck.SameSigner
        } else {
            SignatureCheck.Mismatch(current = current, incoming = incoming)
        }
    }

    /** 已安装包的签名证书摘要集合；读不到返回 null。 */
    private fun installedSignerSha256(context: Context): Set<String>? = runCatching {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        signerDigests(info.signingInfo?.apkContentsSigners)
    }.getOrNull()

    /** 待装 APK 的签名证书摘要集合；解析失败返回 null。 */
    private fun archiveSignerSha256(context: Context, file: File): Set<String>? = runCatching {
        val info = context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        signerDigests(info?.signingInfo?.apkContentsSigners)
    }.getOrNull()

    private fun signerDigests(signatures: Array<android.content.pm.Signature>?): Set<String>? =
        signatures
            ?.mapNotNull { signature -> signature.toByteArray().let(ApkSignature::certSha256) }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }

    /** 异常信息可能为空，也可能是无意义的空串，统一兜底成可展示的原因。 */
    private fun Exception.reason(fallback: String? = null): String =
        message?.takeIf { it.isNotBlank() } ?: fallback ?: this.javaClass.simpleName
}
