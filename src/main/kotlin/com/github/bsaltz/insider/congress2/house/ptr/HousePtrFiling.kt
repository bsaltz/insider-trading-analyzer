package com.github.bsaltz.insider.congress2.house.ptr

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.data.annotation.Id
import java.time.Instant

data class HousePtrFiling(
    @Id
    val id: Long? = null,
    val housePtrOcrResultId: Long,
    val docId: String,
    val rawLlmResponse: String,
    val createdAt: Instant,
)
