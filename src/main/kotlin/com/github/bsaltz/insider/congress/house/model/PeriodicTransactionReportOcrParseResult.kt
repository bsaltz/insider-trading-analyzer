package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.annotation.Id
import java.time.Instant

data class PeriodicTransactionReportOcrParseResult(
    @Id
    val id: Long? = null,
    val periodicTransactionReportId: Long,
    val ocrParseResultId: Long,
    val associatedAt: Instant,
    val confidence: Double? = null,
    val notes: String? = null,
)
