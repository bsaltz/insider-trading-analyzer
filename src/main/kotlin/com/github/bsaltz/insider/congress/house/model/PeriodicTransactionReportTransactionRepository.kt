package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.repository.CrudRepository

interface PeriodicTransactionReportTransactionRepository : CrudRepository<PeriodicTransactionReportTransaction, Long> {
    fun findByPeriodicTransactionReportId(periodicTransactionReportId: Long): List<PeriodicTransactionReportTransaction>
}
