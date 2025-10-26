package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.repository.CrudRepository
import java.time.Instant

interface ParseIssueRepository : CrudRepository<ParseIssue, Long> {
    fun findByDocId(docId: String): List<ParseIssue>

    fun findByDocIdAndSeverity(
        docId: String,
        severity: ParseIssueSeverity,
    ): List<ParseIssue>

    fun findBySeverity(severity: ParseIssueSeverity): List<ParseIssue>

    fun findByCategory(category: ParseIssueCategory): List<ParseIssue>

    fun findByCreatedAtBetween(
        startTime: Instant,
        endTime: Instant,
    ): List<ParseIssue>
}
