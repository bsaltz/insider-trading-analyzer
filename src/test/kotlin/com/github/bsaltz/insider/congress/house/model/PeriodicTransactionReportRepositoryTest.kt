package com.github.bsaltz.insider.congress.house.model

import com.github.bsaltz.insider.vision.OcrParseResult
import com.github.bsaltz.insider.vision.OcrParseResultRepository
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit

@DataJdbcTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
class PeriodicTransactionReportRepositoryTest(
    @param:Autowired
    private val periodicTransactionReportRepository: PeriodicTransactionReportRepository,
    @param:Autowired
    private val ocrParseResultRepository: OcrParseResultRepository,
    @param:Autowired
    private val ptrOcrRepository: PeriodicTransactionReportOcrParseResultRepository,
) {
    @Test
    fun `should save and retrieve PeriodicTransactionReport`() {
        // Given
        val report =
            PeriodicTransactionReport(
                docId = "20032062",
                filerFullName = "Hon. Robert B. Aderholt",
                filerStatus = FilerStatus.MEMBER,
                state = "AL",
                district = 4,
                fileSourceUrl = "https://test-source-url.com/sample.pdf",
            )

        // When
        val savedReport = periodicTransactionReportRepository.save(report)

        // Then
        assertNotNull(savedReport.id)
        assertEquals("20032062", savedReport.docId)
        assertEquals("Hon. Robert B. Aderholt", savedReport.filerFullName)
        assertEquals(FilerStatus.MEMBER, savedReport.filerStatus)
        assertEquals("AL", savedReport.state)
        assertEquals(4, savedReport.district)
        assertEquals("https://test-source-url.com/sample.pdf", savedReport.fileSourceUrl)

        // When retrieving by ID
        val retrievedReport = periodicTransactionReportRepository.findById(savedReport.id!!)

        // Then
        assertTrue(retrievedReport.isPresent)
        val report1 = retrievedReport.get()
        assertEquals(savedReport.id, report1.id)
        assertEquals("20032062", report1.docId)
        assertEquals("Hon. Robert B. Aderholt", report1.filerFullName)
        assertEquals(FilerStatus.MEMBER, report1.filerStatus)
        assertEquals("AL", report1.state)
        assertEquals(4, report1.district)
        assertEquals("https://test-source-url.com/sample.pdf", report1.fileSourceUrl)
    }

    @Test
    fun `should handle different filer statuses`() {
        // Given
        val memberReport =
            PeriodicTransactionReport(
                docId = "1001",
                filerFullName = "John Member",
                filerStatus = FilerStatus.MEMBER,
                state = "TX",
                district = 1,
                fileSourceUrl = "https://example.com/member.pdf",
            )

        val candidateReport =
            PeriodicTransactionReport(
                docId = "1002",
                filerFullName = "Jane Candidate",
                filerStatus = FilerStatus.CANDIDATE,
                state = "CA",
                district = 2,
                fileSourceUrl = "https://example.com/candidate.pdf",
            )

        // When
        val savedMember = periodicTransactionReportRepository.save(memberReport)
        val savedCandidate = periodicTransactionReportRepository.save(candidateReport)

        // Then
        assertEquals(FilerStatus.MEMBER, savedMember.filerStatus)
        assertEquals(FilerStatus.CANDIDATE, savedCandidate.filerStatus)
        assertNotEquals(savedMember.id, savedCandidate.id)
    }

    @Test
    fun `should find PTRs without OCR parse results using native query`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

        // Given - create multiple PTRs
        val ptrWithoutOcr1 =
            PeriodicTransactionReport(
                docId = "NO_OCR_1",
                filerFullName = "Filer Without OCR 1",
                filerStatus = FilerStatus.MEMBER,
                state = "NY",
                district = 1,
                fileSourceUrl = "https://test.com/no-ocr-1.pdf",
            )
        val savedPtrWithoutOcr1 = periodicTransactionReportRepository.save(ptrWithoutOcr1)

        val ptrWithoutOcr2 =
            PeriodicTransactionReport(
                docId = "NO_OCR_2",
                filerFullName = "Filer Without OCR 2",
                filerStatus = FilerStatus.CANDIDATE,
                state = "CA",
                district = 5,
                fileSourceUrl = "https://test.com/no-ocr-2.pdf",
            )
        val savedPtrWithoutOcr2 = periodicTransactionReportRepository.save(ptrWithoutOcr2)

        val ptrWithOcr =
            PeriodicTransactionReport(
                docId = "WITH_OCR",
                filerFullName = "Filer With OCR",
                filerStatus = FilerStatus.OFFICER_OR_EMPLOYEE,
                state = "TX",
                district = 3,
                fileSourceUrl = "https://test.com/with-ocr.pdf",
            )
        val savedPtrWithOcr = periodicTransactionReportRepository.save(ptrWithOcr)

        // Given - create OCR result and associate it with one PTR
        val ocrResult =
            OcrParseResult(
                response = """{"responses":[{"fullTextAnnotation":{"text":"OCR result for PTR"}}]}""",
                createdTime = now,
            )
        val savedOcrResult = ocrParseResultRepository.save(ocrResult)

        val ptrOcrJoin =
            PeriodicTransactionReportOcrParseResult(
                periodicTransactionReportId = savedPtrWithOcr.id!!,
                ocrParseResultId = savedOcrResult.id!!,
                associatedAt = now,
                confidence = 0.9,
                notes = "Test association",
            )
        ptrOcrRepository.save(ptrOcrJoin)

        // When - query for PTRs without OCR results
        val ptrsWithoutOcr = periodicTransactionReportRepository.findPeriodicTransactionReportsWithoutOcrParseResults()

        // Then - should return only the PTRs without OCR associations
        assertEquals(2, ptrsWithoutOcr.size)

        val docIdsWithoutOcr = ptrsWithoutOcr.map { it.docId }.toSet()
        assertTrue(docIdsWithoutOcr.contains("NO_OCR_1"))
        assertTrue(docIdsWithoutOcr.contains("NO_OCR_2"))
        assertFalse(docIdsWithoutOcr.contains("WITH_OCR"))

        // Verify the returned entities are complete
        val firstPtrWithoutOcr = ptrsWithoutOcr.find { it.docId == "NO_OCR_1" }!!
        assertEquals(savedPtrWithoutOcr1.id, firstPtrWithoutOcr.id)
        assertEquals("Filer Without OCR 1", firstPtrWithoutOcr.filerFullName)
        assertEquals(FilerStatus.MEMBER, firstPtrWithoutOcr.filerStatus)
        assertEquals("NY", firstPtrWithoutOcr.state)
        assertEquals(1, firstPtrWithoutOcr.district)

        val secondPtrWithoutOcr = ptrsWithoutOcr.find { it.docId == "NO_OCR_2" }!!
        assertEquals(savedPtrWithoutOcr2.id, secondPtrWithoutOcr.id)
        assertEquals("Filer Without OCR 2", secondPtrWithoutOcr.filerFullName)
        assertEquals(FilerStatus.CANDIDATE, secondPtrWithoutOcr.filerStatus)
        assertEquals("CA", secondPtrWithoutOcr.state)
        assertEquals(5, secondPtrWithoutOcr.district)
    }

    @Test
    fun `should return empty list when all PTRs have OCR parse results`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

        // Given - create PTR
        val ptr =
            PeriodicTransactionReport(
                docId = "ALL_HAVE_OCR",
                filerFullName = "Filer With OCR",
                filerStatus = FilerStatus.MEMBER,
                state = "FL",
                district = 7,
                fileSourceUrl = "https://test.com/all-have-ocr.pdf",
            )
        val savedPtr = periodicTransactionReportRepository.save(ptr)

        // Given - create OCR result and associate it
        val ocrResult =
            OcrParseResult(
                response = """{"responses":[{"fullTextAnnotation":{"text":"All PTRs have OCR"}}]}""",
                createdTime = now,
            )
        val savedOcrResult = ocrParseResultRepository.save(ocrResult)

        val ptrOcrJoin =
            PeriodicTransactionReportOcrParseResult(
                periodicTransactionReportId = savedPtr.id!!,
                ocrParseResultId = savedOcrResult.id!!,
                associatedAt = now,
            )
        ptrOcrRepository.save(ptrOcrJoin)

        // When - query for PTRs without OCR results
        val ptrsWithoutOcr = periodicTransactionReportRepository.findPeriodicTransactionReportsWithoutOcrParseResults()

        // Then - should return empty list
        assertTrue(ptrsWithoutOcr.isEmpty())
    }

    @Test
    fun `should return all PTRs when none have OCR parse results`() {
        // Given - create multiple PTRs without any OCR associations
        val ptr1 =
            PeriodicTransactionReport(
                docId = "NONE_HAVE_OCR_1",
                filerFullName = "Filer 1",
                filerStatus = FilerStatus.MEMBER,
                state = "WA",
                district = 2,
                fileSourceUrl = "https://test.com/none-1.pdf",
            )
        val savedPtr1 = periodicTransactionReportRepository.save(ptr1)

        val ptr2 =
            PeriodicTransactionReport(
                docId = "NONE_HAVE_OCR_2",
                filerFullName = "Filer 2",
                filerStatus = FilerStatus.CANDIDATE,
                state = "OR",
                district = 4,
                fileSourceUrl = "https://test.com/none-2.pdf",
            )
        val savedPtr2 = periodicTransactionReportRepository.save(ptr2)

        // When - query for PTRs without OCR results
        val ptrsWithoutOcr = periodicTransactionReportRepository.findPeriodicTransactionReportsWithoutOcrParseResults()

        // Then - should return all PTRs
        assertEquals(2, ptrsWithoutOcr.size)

        val docIds = ptrsWithoutOcr.map { it.docId }.toSet()
        assertTrue(docIds.contains("NONE_HAVE_OCR_1"))
        assertTrue(docIds.contains("NONE_HAVE_OCR_2"))

        // Verify they are ordered by ID
        val ids = ptrsWithoutOcr.map { it.id!! }
        assertEquals(ids.sorted(), ids)
    }
}
