package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.annotation.Id
import java.time.LocalDate

data class PeriodicTransactionReportTransaction(
    @Id
    val id: Long? = null,
    val periodicTransactionReportId: Long,
    val owner: Ownership?,
    val assetName: String,
    val assetType: String,
    val filingStatus: FilingStatus,
    val tradeType: TradeType,
    val amountRange: AmountRange,
    val tradeDate: LocalDate,
    val fileSourceUrl: String,
    val parsedDate: LocalDate,
)
