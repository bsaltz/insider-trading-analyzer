package com.github.bsaltz.insider.vision

import org.springframework.data.annotation.Id
import java.time.Instant

data class OcrParseResult(
    @Id
    val id: Long? = null,
    val response: String,
    val createdTime: Instant,
)
