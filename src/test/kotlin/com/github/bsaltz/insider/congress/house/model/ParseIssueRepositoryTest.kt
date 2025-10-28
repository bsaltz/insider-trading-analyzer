package com.github.bsaltz.insider.congress.house.model

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.junit.Ignore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit

@DataJdbcTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
@Disabled
class ParseIssueRepositoryTest(
    @param:Autowired
    private val parseIssueRepository: ParseIssueRepository,
    @param:Autowired
    private val periodicTransactionReportRepository: PeriodicTransactionReportRepository,
) {
    @Test
    fun `should save and retrieve ParseIssue`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val ptr =
            PeriodicTransactionReport(
                docId = "TEST_DOC_1",
                filerFullName = "Test Filer",
                filerStatus = FilerStatus.MEMBER,
                state = "CA",
                district = 1,
                fileSourceUrl = "https://test.com/doc1.pdf",
            )
        val savedPtr = periodicTransactionReportRepository.save(ptr)

        val issue =
            ParseIssue(
                docId = savedPtr.docId,
                severity = ParseIssueSeverity.WARNING,
                category = ParseIssueCategory.TRANSACTION_PARSING,
                message = "Failed to parse transaction amount",
                details = "Could not extract numeric value from 'approximately $1,000'",
                location = "transaction #3",
                createdAt = now,
            )

        // When
        val savedIssue = parseIssueRepository.save(issue)

        // Then
        assertNotNull(savedIssue.id)
        assertEquals("TEST_DOC_1", savedIssue.docId)
        assertEquals(ParseIssueSeverity.WARNING, savedIssue.severity)
        assertEquals(ParseIssueCategory.TRANSACTION_PARSING, savedIssue.category)
        assertEquals("Failed to parse transaction amount", savedIssue.message)
        assertEquals("Could not extract numeric value from 'approximately $1,000'", savedIssue.details)
        assertEquals("transaction #3", savedIssue.location)
        assertEquals(now, savedIssue.createdAt)
    }

    @Test
    fun `should find issues by docId`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val ptr =
            PeriodicTransactionReport(
                docId = "TEST_DOC_2",
                filerFullName = "Test Filer 2",
                filerStatus = FilerStatus.CANDIDATE,
                state = "TX",
                district = 2,
                fileSourceUrl = "https://test.com/doc2.pdf",
            )
        val savedPtr = periodicTransactionReportRepository.save(ptr)

        val issue1 =
            ParseIssue(
                docId = savedPtr.docId,
                severity = ParseIssueSeverity.WARNING,
                category = ParseIssueCategory.TRANSACTION_PARSING,
                message = "Warning 1",
                createdAt = now,
            )
        val issue2 =
            ParseIssue(
                docId = savedPtr.docId,
                severity = ParseIssueSeverity.ERROR,
                category = ParseIssueCategory.FILER_INFORMATION_PARSING,
                message = "Error 1",
                createdAt = now.plusSeconds(1),
            )

        parseIssueRepository.save(issue1)
        parseIssueRepository.save(issue2)

        // When
        val issues = parseIssueRepository.findByDocId("TEST_DOC_2")

        // Then
        assertEquals(2, issues.size)
        val docIds = issues.map { it.docId }.toSet()
        assertEquals(setOf("TEST_DOC_2"), docIds)
    }

    @Test
    fun `should find issues by docId and severity`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val ptr =
            PeriodicTransactionReport(
                docId = "TEST_DOC_3",
                filerFullName = "Test Filer 3",
                filerStatus = FilerStatus.OFFICER_OR_EMPLOYEE,
                state = "NY",
                district = 3,
                fileSourceUrl = "https://test.com/doc3.pdf",
            )
        val savedPtr = periodicTransactionReportRepository.save(ptr)

        val warning =
            ParseIssue(
                docId = savedPtr.docId,
                severity = ParseIssueSeverity.WARNING,
                category = ParseIssueCategory.TRANSACTION_PARSING,
                message = "Transaction warning",
                createdAt = now,
            )
        val error =
            ParseIssue(
                docId = savedPtr.docId,
                severity = ParseIssueSeverity.ERROR,
                category = ParseIssueCategory.DOCUMENT_STRUCTURE,
                message = "Structure error",
                createdAt = now.plusSeconds(1),
            )

        parseIssueRepository.save(warning)
        parseIssueRepository.save(error)

        // When
        val warnings = parseIssueRepository.findByDocIdAndSeverity("TEST_DOC_3", ParseIssueSeverity.WARNING)
        val errors = parseIssueRepository.findByDocIdAndSeverity("TEST_DOC_3", ParseIssueSeverity.ERROR)

        // Then
        assertEquals(1, warnings.size)
        assertEquals(ParseIssueSeverity.WARNING, warnings[0].severity)
        assertEquals("Transaction warning", warnings[0].message)

        assertEquals(1, errors.size)
        assertEquals(ParseIssueSeverity.ERROR, errors[0].severity)
        assertEquals("Structure error", errors[0].message)
    }

    @Test
    fun `should find issues by severity across all documents`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

        // Create two PTRs
        val ptr1 =
            periodicTransactionReportRepository.save(
                PeriodicTransactionReport(
                    docId = "DOC_A",
                    filerFullName = "Filer A",
                    filerStatus = FilerStatus.MEMBER,
                    state = "CA",
                    district = 1,
                    fileSourceUrl = "https://test.com/doca.pdf",
                ),
            )
        val ptr2 =
            periodicTransactionReportRepository.save(
                PeriodicTransactionReport(
                    docId = "DOC_B",
                    filerFullName = "Filer B",
                    filerStatus = FilerStatus.CANDIDATE,
                    state = "TX",
                    district = 2,
                    fileSourceUrl = "https://test.com/docb.pdf",
                ),
            )

        // Create issues with different severities
        parseIssueRepository.save(
            ParseIssue(
                docId = ptr1.docId,
                severity = ParseIssueSeverity.WARNING,
                category = ParseIssueCategory.TRANSACTION_PARSING,
                message = "Warning in Doc A",
                createdAt = now,
            ),
        )
        parseIssueRepository.save(
            ParseIssue(
                docId = ptr2.docId,
                severity = ParseIssueSeverity.WARNING,
                category = ParseIssueCategory.TRANSACTION_PARSING,
                message = "Warning in Doc B",
                createdAt = now.plusSeconds(1),
            ),
        )
        parseIssueRepository.save(
            ParseIssue(
                docId = ptr1.docId,
                severity = ParseIssueSeverity.ERROR,
                category = ParseIssueCategory.FILER_INFORMATION_PARSING,
                message = "Error in Doc A",
                createdAt = now.plusSeconds(2),
            ),
        )

        // When
        val allWarnings = parseIssueRepository.findBySeverity(ParseIssueSeverity.WARNING)
        val allErrors = parseIssueRepository.findBySeverity(ParseIssueSeverity.ERROR)

        // Then
        assertEquals(2, allWarnings.size)
        assertTrue(allWarnings.all { it.severity == ParseIssueSeverity.WARNING })

        assertEquals(1, allErrors.size)
        assertEquals(ParseIssueSeverity.ERROR, allErrors[0].severity)
        assertEquals("Error in Doc A", allErrors[0].message)
    }

    @Test
    fun `should find issues by category`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val ptr =
            periodicTransactionReportRepository.save(
                PeriodicTransactionReport(
                    docId = "CATEGORY_TEST",
                    filerFullName = "Category Test Filer",
                    filerStatus = FilerStatus.MEMBER,
                    state = "FL",
                    district = 5,
                    fileSourceUrl = "https://test.com/category.pdf",
                ),
            )

        parseIssueRepository.save(
            ParseIssue(
                docId = ptr.docId,
                severity = ParseIssueSeverity.WARNING,
                category = ParseIssueCategory.TRANSACTION_PARSING,
                message = "Transaction issue",
                createdAt = now,
            ),
        )
        parseIssueRepository.save(
            ParseIssue(
                docId = ptr.docId,
                severity = ParseIssueSeverity.ERROR,
                category = ParseIssueCategory.OCR_QUALITY,
                message = "OCR quality issue",
                createdAt = now.plusSeconds(1),
            ),
        )
        parseIssueRepository.save(
            ParseIssue(
                docId = ptr.docId,
                severity = ParseIssueSeverity.WARNING,
                category = ParseIssueCategory.TRANSACTION_PARSING,
                message = "Another transaction issue",
                createdAt = now.plusSeconds(2),
            ),
        )

        // When
        val transactionIssues = parseIssueRepository.findByCategory(ParseIssueCategory.TRANSACTION_PARSING)
        val ocrIssues = parseIssueRepository.findByCategory(ParseIssueCategory.OCR_QUALITY)

        // Then
        assertEquals(2, transactionIssues.size)
        assertTrue(transactionIssues.all { it.category == ParseIssueCategory.TRANSACTION_PARSING })

        assertEquals(1, ocrIssues.size)
        assertEquals(ParseIssueCategory.OCR_QUALITY, ocrIssues[0].category)
        assertEquals("OCR quality issue", ocrIssues[0].message)
    }

    @Test
    fun `should find issues by time range`() {
        // Given
        val baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val ptr =
            periodicTransactionReportRepository.save(
                PeriodicTransactionReport(
                    docId = "TIME_TEST",
                    filerFullName = "Time Test Filer",
                    filerStatus = FilerStatus.MEMBER,
                    state = "WA",
                    district = 7,
                    fileSourceUrl = "https://test.com/time.pdf",
                ),
            )

        // Create issues at different times
        parseIssueRepository.save(
            ParseIssue(
                docId = ptr.docId,
                severity = ParseIssueSeverity.WARNING,
                category = ParseIssueCategory.TRANSACTION_PARSING,
                message = "Early issue",
                createdAt = baseTime.minusSeconds(60), // 1 minute before
            ),
        )
        parseIssueRepository.save(
            ParseIssue(
                docId = ptr.docId,
                severity = ParseIssueSeverity.ERROR,
                category = ParseIssueCategory.FILER_INFORMATION_PARSING,
                message = "Middle issue",
                createdAt = baseTime, // exactly at base time
            ),
        )
        parseIssueRepository.save(
            ParseIssue(
                docId = ptr.docId,
                severity = ParseIssueSeverity.WARNING,
                category = ParseIssueCategory.DATA_VALIDATION,
                message = "Late issue",
                createdAt = baseTime.plusSeconds(60), // 1 minute after
            ),
        )

        // When - query for issues in a 30-second window around base time
        val startTime = baseTime.minusSeconds(30)
        val endTime = baseTime.plusSeconds(30)
        val issuesInRange = parseIssueRepository.findByCreatedAtBetween(startTime, endTime)

        // Then - should only find the middle issue
        assertEquals(1, issuesInRange.size)
        assertEquals("Middle issue", issuesInRange[0].message)
        assertEquals(baseTime, issuesInRange[0].createdAt)
    }

    @Test
    fun `should handle foreign key constraint with PTR deletion`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val ptr =
            PeriodicTransactionReport(
                docId = "DELETE_TEST",
                filerFullName = "Delete Test Filer",
                filerStatus = FilerStatus.MEMBER,
                state = "OR",
                district = 4,
                fileSourceUrl = "https://test.com/delete.pdf",
            )
        val savedPtr = periodicTransactionReportRepository.save(ptr)

        val issue =
            ParseIssue(
                docId = savedPtr.docId,
                severity = ParseIssueSeverity.ERROR,
                category = ParseIssueCategory.DOCUMENT_STRUCTURE,
                message = "Test issue for deletion",
                createdAt = now,
            )
        val savedIssue = parseIssueRepository.save(issue)

        // Verify issue exists
        val foundIssues = parseIssueRepository.findByDocId("DELETE_TEST")
        assertEquals(1, foundIssues.size)

        // When - delete the PTR (should cascade delete the issues)
        periodicTransactionReportRepository.deleteById(savedPtr.id!!)

        // Then - the parse issue should be deleted due to cascade
        val issuesAfterDeletion = parseIssueRepository.findByDocId("DELETE_TEST")
        assertTrue(issuesAfterDeletion.isEmpty())
    }
}
