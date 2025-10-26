package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface PeriodicTransactionReportRepository : CrudRepository<PeriodicTransactionReport, Long> {
    fun findByDocId(docId: String): PeriodicTransactionReport?

    @Query(
        """
        SELECT ptr.*
        FROM periodic_transaction_report ptr
        WHERE NOT EXISTS (
            SELECT 1
            FROM periodic_transaction_report_ocr_parse_result ptrocr
            WHERE ptrocr.periodic_transaction_report_id = ptr.id
        )
        ORDER BY ptr.id
        """,
    )
    fun findPeriodicTransactionReportsWithoutOcrParseResults(): List<PeriodicTransactionReport>
}
