package com.github.bsaltz.insider.congress2.house.ptr

import org.springframework.data.annotation.Id
import java.time.Instant

data class HousePtrDownload(
    @Id
    val id: Long? = null,
    val docId: String,
    val gcsUri: String,
    val etag: String,
    val parsed: Boolean,
    val parsedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
)
