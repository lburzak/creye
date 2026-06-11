package pl.lukaszburzak.creye.domain.change

/** Aggregate result of the change-detection pipeline (ADR-003 + ADR-004 stages). */
data class ChangeDetection(
    val comparison: ChangeComparison,
    val symbols: ChangedSymbols,
)
