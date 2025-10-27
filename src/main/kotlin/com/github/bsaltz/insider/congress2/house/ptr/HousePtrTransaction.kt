package com.github.bsaltz.insider.congress2.house.ptr

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository

data class HousePtrTransaction(
    @Id
    val id: Long? = null,
    val docId: String,
    val housePtrFilingId: Long,
    val owner: String,
    val asset: String,
    val transactionType: String,
    val transactionDate: String,
    val notificationDate: String,
    val amount: String,
    val certainty: Int,
    val additionalData: JsonNode,
)

interface HousePtrTransactionRepository : CrudRepository<HousePtrTransaction, Long> {
    fun findByHousePtrFilingId(housePtrFilingId: Long): List<HousePtrTransaction>

    fun findByDocId(docId: String): HousePtrTransaction?
}
