package pl.lukaszburzak.creye.rendering

/**
 * User-tunable force-simulation parameters surfaced as sliders (ADR-013) and persisted
 * per project so a readable layout survives across sessions and graph rebuilds.
 */
data class ForceSettings(
    val gravity: Float = DEFAULT_GRAVITY,
    val attraction: Float = DEFAULT_ATTRACTION,
    val repulsion: Float = DEFAULT_REPULSION,
) {
    companion object {
        const val DEFAULT_GRAVITY = 0.0003f
        const val MAX_GRAVITY = 0.0010f
        const val DEFAULT_ATTRACTION = 0.0008f
        const val MAX_ATTRACTION = 0.0025f
        const val DEFAULT_REPULSION = 8_000f
        const val MAX_REPULSION = 12_000f

        val DEFAULT = ForceSettings()
    }
}
