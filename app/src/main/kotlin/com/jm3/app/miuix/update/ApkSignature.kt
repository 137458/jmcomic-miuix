package com.jm3.app.miuix.update

import java.security.MessageDigest

/**
 * 安装包签名身份比较。
 *
 * 用途只有一个：在拉起系统安装器之前，先比对「已安装本应用的签名」与「待装 APK 的签名」。
 * 签名不同时系统会以 INSTALL_FAILED_UPDATE_INCOMPATIBLE 拒绝安装，且本应用 allowBackup=false，
 * 用户按提示卸载重装会丢掉全部本地数据；提前拦下并给出原因，比让用户在系统安装器里撞墙更好。
 *
 * 只做身份相等判断，不做证书链与有效性校验：可信来源已经由下载摘要与服务端发布约束，这里防的是
 * 「装进来的包不是同一个发布者签的」。
 */
internal object ApkSignature {

    /** 证书 DER 字节的 SHA-256，小写 hex。 */
    fun certSha256(certBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xFF
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0F])
            }
        }
    }

    /**
     * 两边签名证书集合是否存在交集。
     *
     * 任一侧为空都判为不一致：拿不到签名就是「无法证明同源」，不能当作通过。
     */
    fun sameSigner(current: Set<String>, incoming: Set<String>): Boolean =
        current.isNotEmpty() && incoming.isNotEmpty() && incoming.any { it in current }

    private const val HEX_DIGITS = "0123456789abcdef"
}
