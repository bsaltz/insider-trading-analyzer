package com.github.bsaltz.insider.congress.house.parser

import com.github.bsaltz.insider.congress.house.model.AmountRange
import com.github.bsaltz.insider.congress.house.model.FilingStatus
import com.github.bsaltz.insider.congress.house.model.Ownership
import com.github.bsaltz.insider.congress.house.model.PeriodicTransactionReportTransaction
import com.github.bsaltz.insider.congress.house.model.TradeType
import java.time.LocalDate

data class ParsedPeriodicTransactionReportTransaction(
    val owner: Ownership?,
    val assetName: String,
    val assetType: String,
    val filingStatus: FilingStatus,
    val tradeType: TradeType,
    val amountRange: AmountRange,
    val tradeDate: LocalDate,
    val fileSourceUrl: String,
    val parsedDate: LocalDate = LocalDate.now(),
) {
    fun toTransactionEntity(
        periodicTransactionReportId: Long,
    ): PeriodicTransactionReportTransaction =
        PeriodicTransactionReportTransaction(
            periodicTransactionReportId = periodicTransactionReportId,
            owner = owner,
            assetName = assetName,
            assetType = assetType,
            filingStatus = filingStatus,
            tradeType = tradeType,
            amountRange = amountRange,
            tradeDate = tradeDate,
            fileSourceUrl = fileSourceUrl,
            parsedDate = parsedDate,
        )
}
