package com.github.bsaltz.insider.congress.house.model

import com.github.bsaltz.insider.vision.OcrParseResult
import com.github.bsaltz.insider.vision.OcrParseResultRepository
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit

@DataJdbcTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
class PeriodicTransactionReportOcrParseResultRepositoryTest(
    @param:Autowired
    private val periodicTransactionReportRepository: PeriodicTransactionReportRepository,
    @param:Autowired
    private val ocrParseResultRepository: OcrParseResultRepository,
    @param:Autowired
    private val ptrOcrRepository: PeriodicTransactionReportOcrParseResultRepository,
) {
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

    @Test
    fun `should save and retrieve PTR-OCR relationship`() {
        // Given - create parent entities first
        val ptr =
            PeriodicTransactionReport(
                docId = "20032062",
                filerFullName = "Hon. Robert B. Aderholt",
                filerStatus = FilerStatus.MEMBER,
                state = "AL",
                district = 4,
                fileSourceUrl = "https://test-source-url.com/sample.pdf",
            )
        val savedPtr = periodicTransactionReportRepository.save(ptr)

        val ocrResult =
            OcrParseResult(
                response =
                    """{"responses":[{"fullTextAnnotation":{"text":"Filing ID #20032062\\nFILER INFORMATION\\n""" +
                        """Name: Hon. Robert B. Aderholt"}}]}""",
                createdTime = now,
            )
        val savedOcrResult = ocrParseResultRepository.save(ocrResult)

        // Given - create join entity
        val ptrOcrJoin =
            PeriodicTransactionReportOcrParseResult(
                periodicTransactionReportId = savedPtr.id!!,
                ocrParseResultId = savedOcrResult.id!!,
                associatedAt = now,
                confidence = 0.95,
                notes = "High confidence match based on document ID",
            )

        // When
        val savedJoin = ptrOcrRepository.save(ptrOcrJoin)

        // Then
        assertNotNull(savedJoin.id)
        assertEquals(savedPtr.id, savedJoin.periodicTransactionReportId)
        assertEquals(savedOcrResult.id, savedJoin.ocrParseResultId)
        assertEquals(now, savedJoin.associatedAt)
        assertEquals(0.95, savedJoin.confidence)
        assertEquals("High confidence match based on document ID", savedJoin.notes)

        // When retrieving by PTR ID
        val joinsByPtr = ptrOcrRepository.findByPeriodicTransactionReportId(savedPtr.id!!)

        // Then
        assertEquals(1, joinsByPtr.size)
        val retrievedJoin = joinsByPtr[0]
        assertEquals(savedJoin.id, retrievedJoin.id)
        assertEquals(savedPtr.id, retrievedJoin.periodicTransactionReportId)
        assertEquals(savedOcrResult.id, retrievedJoin.ocrParseResultId)

        // When retrieving by OCR ID
        val joinsByOcr = ptrOcrRepository.findByOcrParseResultId(savedOcrResult.id!!)

        // Then
        assertEquals(1, joinsByOcr.size)
        assertEquals(savedJoin.id, joinsByOcr[0].id)
    }

    @Test
    fun `should find specific PTR-OCR relationship`() {
        // Given - create parent entities
        val ptr =
            PeriodicTransactionReport(
                docId = "20029060",
                filerFullName = "Hon. David J. Taylor",
                filerStatus = FilerStatus.MEMBER,
                state = "OH",
                district = 2,
                fileSourceUrl = "https://test-source-url.com/20029060.pdf",
            )
        val savedPtr = periodicTransactionReportRepository.save(ptr)

        val ocrResult =
            OcrParseResult(
                response = """{"responses":[{"fullTextAnnotation":{"text":"Complex document with multiple transactions"}}]}""",
                createdTime = now,
            )
        val savedOcrResult = ocrParseResultRepository.save(ocrResult)

        // Given - create join entity
        val ptrOcrJoin =
            PeriodicTransactionReportOcrParseResult(
                periodicTransactionReportId = savedPtr.id!!,
                ocrParseResultId = savedOcrResult.id!!,
                associatedAt = now,
                confidence = 0.87,
                notes = "Moderate confidence - complex document",
            )
        ptrOcrRepository.save(ptrOcrJoin)

        // When
        val specificJoin =
            ptrOcrRepository.findByPeriodicTransactionReportIdAndOcrParseResultId(
                savedPtr.id!!,
                savedOcrResult.id!!,
            )

        // Then
        assertNotNull(specificJoin)
        assertEquals(savedPtr.id, specificJoin!!.periodicTransactionReportId)
        assertEquals(savedOcrResult.id, specificJoin.ocrParseResultId)
        assertEquals(0.87, specificJoin.confidence)
        assertEquals("Moderate confidence - complex document", specificJoin.notes)

        // When looking for non-existent relationship
        val nonExistentJoin =
            ptrOcrRepository.findByPeriodicTransactionReportIdAndOcrParseResultId(
                99999L,
                savedOcrResult.id!!,
            )

        // Then
        assertNull(nonExistentJoin)
    }

    @Test
    fun `should handle multiple OCR results for same PTR`() {
        // Given - create PTR
        val ptr =
            PeriodicTransactionReport(
                docId = "MULTI_OCR_TEST",
                filerFullName = "Test Filer",
                filerStatus = FilerStatus.CANDIDATE,
                state = "NY",
                district = 1,
                fileSourceUrl = "https://test.com/multi.pdf",
            )
        val savedPtr = periodicTransactionReportRepository.save(ptr)

        // Given - create multiple OCR results
        val ocrResult1 =
            OcrParseResult(
                response = """{"responses":[{"fullTextAnnotation":{"text":"First OCR attempt"}}]}""",
                createdTime = now,
            )
        val savedOcrResult1 = ocrParseResultRepository.save(ocrResult1)

        val ocrResult2 =
            OcrParseResult(
                response = """{"responses":[{"fullTextAnnotation":{"text":"Second OCR attempt with better quality"}}]}""",
                createdTime = now.plusSeconds(60),
            )
        val savedOcrResult2 = ocrParseResultRepository.save(ocrResult2)

        // Given - create join entities
        val join1 =
            PeriodicTransactionReportOcrParseResult(
                periodicTransactionReportId = savedPtr.id!!,
                ocrParseResultId = savedOcrResult1.id!!,
                associatedAt = now,
                confidence = 0.65,
                notes = "First attempt - low quality",
            )
        val join2 =
            PeriodicTransactionReportOcrParseResult(
                periodicTransactionReportId = savedPtr.id!!,
                ocrParseResultId = savedOcrResult2.id!!,
                associatedAt = now.plusSeconds(60),
                confidence = 0.92,
                notes = "Second attempt - high quality",
            )

        // When
        ptrOcrRepository.save(join1)
        ptrOcrRepository.save(join2)

        // Then
        val joinsByPtr = ptrOcrRepository.findByPeriodicTransactionReportId(savedPtr.id!!)
        assertEquals(2, joinsByPtr.size)

        // Verify confidence values
        val confidences = joinsByPtr.mapNotNull { it.confidence }.sorted()
        assertEquals(listOf(0.65, 0.92), confidences)

        // Verify both OCR results can be found individually
        assertEquals(1, ptrOcrRepository.findByOcrParseResultId(savedOcrResult1.id!!).size)
        assertEquals(1, ptrOcrRepository.findByOcrParseResultId(savedOcrResult2.id!!).size)
    }

    @Test
    fun `should handle optional fields correctly`() {
        // Given - create parent entities
        val ptr =
            PeriodicTransactionReport(
                docId = "MINIMAL_TEST",
                filerFullName = "Minimal Filer",
                filerStatus = FilerStatus.OFFICER_OR_EMPLOYEE,
                state = "CA",
                district = 5,
                fileSourceUrl = "https://test.com/minimal.pdf",
            )
        val savedPtr = periodicTransactionReportRepository.save(ptr)

        val ocrResult =
            OcrParseResult(
                response = """{"responses":[{"fullTextAnnotation":{"text":"Minimal OCR result"}}]}""",
                createdTime = now,
            )
        val savedOcrResult = ocrParseResultRepository.save(ocrResult)

        // Given - create join entity with minimal required fields only
        val ptrOcrJoin =
            PeriodicTransactionReportOcrParseResult(
                periodicTransactionReportId = savedPtr.id!!,
                ocrParseResultId = savedOcrResult.id!!,
                associatedAt = now,
                confidence = null,
                notes = null,
            )

        // When
        val savedJoin = ptrOcrRepository.save(ptrOcrJoin)

        // Then
        assertNotNull(savedJoin.id)
        assertEquals(savedPtr.id, savedJoin.periodicTransactionReportId)
        assertEquals(savedOcrResult.id, savedJoin.ocrParseResultId)
        assertEquals(now, savedJoin.associatedAt)
        assertNull(savedJoin.confidence)
        assertNull(savedJoin.notes)

        // When retrieving
        val retrievedJoin =
            ptrOcrRepository.findByPeriodicTransactionReportIdAndOcrParseResultId(
                savedPtr.id!!,
                savedOcrResult.id!!,
            )

        // Then
        assertNotNull(retrievedJoin)
        assertNull(retrievedJoin!!.confidence)
        assertNull(retrievedJoin.notes)
    }

    @Test
    fun `should enforce unique constraint on PTR-OCR combination`() {
        // Given - create parent entities
        val ptr =
            PeriodicTransactionReport(
                docId = "UNIQUE_TEST",
                filerFullName = "Unique Test Filer",
                filerStatus = FilerStatus.MEMBER,
                state = "TX",
                district = 3,
                fileSourceUrl = "https://test.com/unique.pdf",
            )
        val savedPtr = periodicTransactionReportRepository.save(ptr)

        val ocrResult =
            OcrParseResult(
                response = """{"responses":[{"fullTextAnnotation":{"text":"Unique test OCR"}}]}""",
                createdTime = now,
            )
        val savedOcrResult = ocrParseResultRepository.save(ocrResult)

        // Given - create first join entity
        val firstJoin =
            PeriodicTransactionReportOcrParseResult(
                periodicTransactionReportId = savedPtr.id!!,
                ocrParseResultId = savedOcrResult.id!!,
                associatedAt = now,
                confidence = 0.8,
                notes = "First association",
            )
        ptrOcrRepository.save(firstJoin)

        // When & Then - attempting to create duplicate should fail
        val duplicateJoin =
            PeriodicTransactionReportOcrParseResult(
                periodicTransactionReportId = savedPtr.id!!,
                ocrParseResultId = savedOcrResult.id!!,
                associatedAt = now.plusSeconds(30),
                confidence = 0.9,
                notes = "Duplicate association attempt",
            )

        assertThrows(Exception::class.java) {
            ptrOcrRepository.save(duplicateJoin)
        }
    }
}
