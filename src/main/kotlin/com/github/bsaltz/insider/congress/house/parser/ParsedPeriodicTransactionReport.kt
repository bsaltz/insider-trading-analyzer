package com.github.bsaltz.insider.congress.house.parser

import com.github.bsaltz.insider.congress.house.model.FilerStatus
import com.github.bsaltz.insider.congress.house.model.PeriodicTransactionReport

data class ParsedPeriodicTransactionReport(
    val docId: String,
    val filerFullName: String,
    val filerStatus: FilerStatus,
    val state: String,
    val district: Int,
    val fileSourceUrl: String,
    val transactions: List<ParsedPeriodicTransactionReportTransaction>,
) {
    fun toPtrEntity(): PeriodicTransactionReport =
        PeriodicTransactionReport(
            docId = docId,
            filerFullName = filerFullName,
            filerStatus = filerStatus,
            state = state,
            district = district,
            fileSourceUrl = fileSourceUrl,
        )
}
