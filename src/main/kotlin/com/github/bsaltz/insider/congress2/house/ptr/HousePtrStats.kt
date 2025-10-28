package com.github.bsaltz.insider.congress2.house.ptr

/**
 * Statistics for House PTR processing for a specific year.
 *
 * @property year The year for which statistics are gathered
 * @property totalFilingListRows Total number of filing list rows for the year
 * @property typeFilingListRows Number of filing list rows with filing type 'P'
 * @property downloadsCompleted Number of PDFs successfully downloaded
 * @property ocrResultsCompleted Number of OCR results generated
 * @property filingsCompleted Number of filings successfully parsed by LLM
 * @property transactionsExtracted Total number of transactions extracted from filings
 */
data class HousePtrStats(
    val year: Int,
    val totalFilingListRows: Int,
    val typePFilingListRows: Int,
    val downloadsCompleted: Int,
    val ocrResultsCompleted: Int,
    val filingsCompleted: Int,
    val transactionsExtracted: Int,
) {
    /**
     * Format the statistics as a human-readable string.
     */
    fun format(): String =
        buildString {
            appendLine("Statistics for House PTR Processing - Year $year")
            appendLine("=" + "=".repeat(50))
            appendLine()
            appendLine("Filing List:")
            appendLine("  Total filing list rows:        $totalFilingListRows")
            appendLine("  Type 'P' rows (processable):   $typePFilingListRows")
            appendLine()
            appendLine("Processing Progress:")
            appendLine(
                "  PDFs downloaded:               $downloadsCompleted / $typePFilingListRows (${percentage(
                    downloadsCompleted,
                    typePFilingListRows,
                )})",
            )
            appendLine(
                "  OCR results completed:         $ocrResultsCompleted / $downloadsCompleted (${percentage(
                    ocrResultsCompleted,
                    downloadsCompleted,
                )})",
            )
            appendLine(
                "  Filings parsed:                $filingsCompleted / $ocrResultsCompleted (${percentage(
                    filingsCompleted,
                    ocrResultsCompleted,
                )})",
            )
            appendLine()
            appendLine("Results:")
            appendLine("  Transactions extracted:        $transactionsExtracted")
            if (filingsCompleted > 0) {
                val avgTransactionsPerFiling = transactionsExtracted.toDouble() / filingsCompleted
                appendLine("  Avg transactions per filing:   ${"%.1f".format(avgTransactionsPerFiling)}")
            }
            appendLine()
            appendLine("Overall Progress:")
            appendLine(
                "  End-to-end completion:         $filingsCompleted / $typePFilingListRows (${percentage(
                    filingsCompleted,
                    typePFilingListRows,
                )})",
            )
        }

    private fun percentage(
        numerator: Int,
        denominator: Int,
    ): String =
        if (denominator == 0) {
            "N/A"
        } else {
            "${"%.1f".format(numerator.toDouble() / denominator * 100)}%"
        }
}
