package com.jm3.app.miuix.ui.effect

/**
 * 流光背景的配色与点位预设（HyperOS 3 一套，取自 localsend-miuix）。
 *
 * 每份预设给出 4 个光点的归一化坐标与半径（points，3 个一组）、
 * 三轮交替插值的 RGBA 颜色（colors1..3，4 个一组），以及噪声强度偏移。
 */
object BgEffectConfig {
    class Config(
        val points: FloatArray,
        val colors1: FloatArray,
        val colors2: FloatArray,
        val colors3: FloatArray,
        val colorInterpPeriod: Float,
        val lightOffset: Float,
        val saturateOffset: Float,
        val pointOffset: Float,
    )

    private val PHONE_LIGHT =
        Config(
            points = floatArrayOf(0.9f, 0.4f, 1.2f, 0.9f, 0.9f, 1.2f, 0.1f, 0.9f, 1.2f, 0.1f, 0.4f, 1.2f),
            colors1 = floatArrayOf(0.66f, 0.75f, 1.0f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.72f, 0.74f, 1.0f, 1.0f, 0.97f, 0.77f, 0.84f, 1.0f),
            colors2 = floatArrayOf(0.99f, 0.77f, 0.86f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.72f, 0.74f, 1.0f, 1.0f, 0.98f, 0.76f, 0.8f, 1.0f),
            colors3 = floatArrayOf(0.97f, 0.79f, 0.85f, 1.0f, 0.65f, 0.68f, 0.98f, 1.0f, 0.66f, 0.77f, 1.0f, 1.0f, 0.72f, 0.73f, 0.98f, 1.0f),
            colorInterpPeriod = 8.0f,
            lightOffset = 0.1f,
            saturateOffset = 0.2f,
            pointOffset = 0.4f,
        )

    private val PHONE_DARK =
        Config(
            points = floatArrayOf(0.9f, 0.4f, 1.2f, 0.9f, 0.9f, 1.2f, 0.1f, 0.9f, 1.2f, 0.1f, 0.4f, 1.2f),
            colors1 = floatArrayOf(0.0f, 0.64f, 0.96f, 0.5f, 0.11f, 0.16f, 0.83f, 0.5f, 0.14f, 0.18f, 0.55f, 0.5f, 0.66f, 0.26f, 0.62f, 0.4f),
            colors2 = floatArrayOf(0.07f, 0.15f, 0.79f, 0.5f, 0.62f, 0.21f, 0.67f, 0.5f, 0.06f, 0.25f, 0.84f, 0.5f, 0.0f, 0.2f, 0.78f, 0.5f),
            colors3 = floatArrayOf(0.58f, 0.3f, 0.74f, 0.4f, 0.27f, 0.18f, 0.6f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f, 0.12f, 0.16f, 0.7f, 0.6f),
            colorInterpPeriod = 8.0f,
            lightOffset = 0.0f,
            saturateOffset = 0.17f,
            pointOffset = 0.4f,
        )

    private val PAD_LIGHT =
        Config(
            points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
            colors1 = floatArrayOf(0.99f, 0.77f, 0.86f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.72f, 0.74f, 1.0f, 1.0f, 0.98f, 0.76f, 0.8f, 1.0f),
            colors2 = floatArrayOf(0.66f, 0.75f, 1.0f, 1.0f, 1.0f, 0.86f, 0.91f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.97f, 0.77f, 0.84f, 1.0f),
            colors3 = floatArrayOf(0.97f, 0.79f, 0.85f, 1.0f, 0.65f, 0.68f, 0.98f, 1.0f, 0.66f, 0.77f, 1.0f, 1.0f, 0.72f, 0.73f, 0.98f, 1.0f),
            colorInterpPeriod = 7.0f,
            lightOffset = 0.1f,
            saturateOffset = 0.2f,
            pointOffset = 0.2f,
        )

    private val PAD_DARK =
        Config(
            points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
            colors1 = floatArrayOf(0.66f, 0.26f, 0.62f, 0.4f, 0.06f, 0.25f, 0.84f, 0.5f, 0.0f, 0.64f, 0.96f, 0.5f, 0.14f, 0.18f, 0.55f, 0.5f),
            colors2 = floatArrayOf(0.07f, 0.15f, 0.79f, 0.5f, 0.11f, 0.16f, 0.83f, 0.5f, 0.06f, 0.25f, 0.84f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f),
            colors3 = floatArrayOf(0.58f, 0.3f, 0.74f, 0.5f, 0.11f, 0.16f, 0.83f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f, 0.27f, 0.18f, 0.6f, 0.6f),
            colorInterpPeriod = 7.0f,
            lightOffset = 0.0f,
            saturateOffset = 0.0f,
            pointOffset = 0.2f,
        )

    fun get(
        deviceType: DeviceType,
        isDark: Boolean,
    ): Config =
        when (deviceType) {
            DeviceType.PHONE -> if (isDark) PHONE_DARK else PHONE_LIGHT
            DeviceType.PAD -> if (isDark) PAD_DARK else PAD_LIGHT
        }
}
