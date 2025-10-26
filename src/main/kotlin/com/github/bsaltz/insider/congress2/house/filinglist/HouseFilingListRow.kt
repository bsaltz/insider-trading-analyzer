package com.github.bsaltz.insider.congress2.house.filinglist

import org.springframework.data.annotation.Id
import java.time.Instant
import java.time.LocalDate

data class HouseFilingListRow(
    @Id
    val id: Long? = null,
    val docId: String,
    val prefix: String,
    val last: String,
    val first: String,
    val suffix: String,
    val filingType: String,
    val stateDst: String,
    val year: Int,
    val filingDate: LocalDate,
    val downloaded: Boolean,
    val downloadedAt: Instant?,
    val rawRowData: String,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val houseFilingListId: Long,
)
