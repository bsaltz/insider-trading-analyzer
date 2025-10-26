package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.repository.CrudRepository

interface PeriodicTransactionReportOcrParseResultRepository : CrudRepository<PeriodicTransactionReportOcrParseResult, Long> {
    fun findByPeriodicTransactionReportId(periodicTransactionReportId: Long): List<PeriodicTransactionReportOcrParseResult>

    fun findByOcrParseResultId(ocrParseResultId: Long): List<PeriodicTransactionReportOcrParseResult>

    fun findByPeriodicTransactionReportIdAndOcrParseResultId(
        periodicTransactionReportId: Long,
        ocrParseResultId: Long,
    ): PeriodicTransactionReportOcrParseResult?
}
