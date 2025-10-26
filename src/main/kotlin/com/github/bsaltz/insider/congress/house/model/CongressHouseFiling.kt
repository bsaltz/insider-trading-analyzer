package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.annotation.Id
import java.time.LocalDate

data class CongressHouseFiling(
    @Id
    val id: Long? = null,
    val congressHouseFilingListId: Long,
    val docId: String,
    val prefix: String,
    val last: String,
    val first: String,
    val suffix: String,
    val filingType: String,
    val stateDst: String,
    val year: Int,
    val filingDate: LocalDate,
    val etag: String? = null,
)
