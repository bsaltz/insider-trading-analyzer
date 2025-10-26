package com.github.bsaltz.insider.congress2.house.filinglist

import org.springframework.data.annotation.Id
import java.time.Instant

data class HouseFilingList(
    @Id
    val id: Long? = null,
    val year: Int,
    val etag: String?,
    val gcsUri: String,
    val parsed: Boolean,
    val parsedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant?,
)
