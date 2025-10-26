package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.annotation.Id
import java.time.Instant

data class ParseIssue(
    @Id
    val id: Long? = null,
    val docId: String,
    val severity: ParseIssueSeverity,
    val category: ParseIssueCategory,
    val message: String,
    val details: String? = null,
    val location: String? = null, // e.g., "transaction #3", "FILER INFORMATION block"
    val createdAt: Instant,
)
