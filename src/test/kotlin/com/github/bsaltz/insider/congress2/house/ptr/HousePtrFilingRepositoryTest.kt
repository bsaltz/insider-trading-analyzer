package com.github.bsaltz.insider.congress2.house.ptr

import com.github.bsaltz.insider.test.RepositoryTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit

@RepositoryTest
class HousePtrFilingRepositoryTest(
    @param:Autowired
    private val housePtrFilingRepository: HousePtrFilingRepository,
    @param:Autowired
    private val housePtrOcrResultRepository: HousePtrOcrResultRepository,
    @param:Autowired
    private val housePtrDownloadRepository: HousePtrDownloadRepository,
) {
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
    private var ocrResultId: Long = 0
    private var downloadId: Long = 0

    @BeforeEach
    fun setup() {
        // Create parent download for foreign key relationship
        val download =
            HousePtrDownload(
                docId = "20250101-001",
                gcsUri = "gs://test-bucket/ptr/20250101-001.pdf",
                etag = "test-etag",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        downloadId = housePtrDownloadRepository.save(download).id!!

        // Create parent OCR result for foreign key relationship
        val ocrResult =
            HousePtrOcrResult(
                docId = "20250101-001",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250101-001.json",
            )
        ocrResultId = housePtrOcrResultRepository.save(ocrResult).id!!
    }

    @Test
    fun `should save and retrieve HousePtrFiling`() {
        // Given
        val filing =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId,
                docId = "20250101-001",
                rawLlmResponse = """{"transactions": [{"asset": "AAPL", "type": "PURCHASE"}]}""",
                createdAt = now,
            )

        // When
        val savedFiling = housePtrFilingRepository.save(filing)

        // Then
        assertNotNull(savedFiling.id)
        assertEquals(ocrResultId, savedFiling.housePtrOcrResultId)
        assertEquals("20250101-001", savedFiling.docId)
        assertTrue(savedFiling.rawLlmResponse.contains("transactions"))
        assertEquals(now, savedFiling.createdAt)
    }

    @Test
    fun `should find filings by house ptr ocr result id`() {
        // Given - Create multiple filings for the same OCR result
        val filing1 =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId,
                docId = "20250101-001",
                rawLlmResponse = """{"transactions": [{"asset": "AAPL"}]}""",
                createdAt = now,
            )
        val filing2 =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId,
                docId = "20250101-001",
                rawLlmResponse = """{"transactions": [{"asset": "GOOGL"}]}""",
                createdAt = now.plusSeconds(60),
            )
        housePtrFilingRepository.save(filing1)
        housePtrFilingRepository.save(filing2)

        // When
        val filings = housePtrFilingRepository.findByHousePtrOcrResultId(ocrResultId)

        // Then
        assertEquals(2, filings.size)
        assertTrue(filings.all { it.housePtrOcrResultId == ocrResultId })
        assertTrue(filings.any { it.rawLlmResponse.contains("AAPL") })
        assertTrue(filings.any { it.rawLlmResponse.contains("GOOGL") })
    }

    @Test
    fun `should return empty list when finding by non-existent ocr result id`() {
        // When
        val filings = housePtrFilingRepository.findByHousePtrOcrResultId(999999L)

        // Then
        assertTrue(filings.isEmpty())
    }

    @Test
    fun `should find filing by doc id`() {
        // Given
        val filing =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId,
                docId = "20250201-001",
                rawLlmResponse = """{"transactions": [{"asset": "MSFT", "type": "SALE"}]}""",
                createdAt = now,
            )
        housePtrFilingRepository.save(filing)

        // When
        val foundFiling = housePtrFilingRepository.findByDocId("20250201-001")
        val notFoundFiling = housePtrFilingRepository.findByDocId("nonexistent-doc-id")

        // Then
        assertNotNull(foundFiling)
        assertEquals("20250201-001", foundFiling!!.docId)
        assertEquals(ocrResultId, foundFiling.housePtrOcrResultId)
        assertTrue(foundFiling.rawLlmResponse.contains("MSFT"))

        assertNull(notFoundFiling)
    }

    @Test
    fun `should handle large raw LLM response`() {
        // Given
        val largeResponse =
            """
            {
                "transactions": [
                    ${(1..100).joinToString(",\n") { """{"asset": "STOCK$it", "type": "PURCHASE", "amount": "$1000-$5000"}""" }}
                ]
            }
            """.trimIndent()

        val filing =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId,
                docId = "20250301-001",
                rawLlmResponse = largeResponse,
                createdAt = now,
            )

        // When
        val savedFiling = housePtrFilingRepository.save(filing)

        // Then
        assertNotNull(savedFiling.id)
        assertTrue(savedFiling.rawLlmResponse.length > 1000)
        assertTrue(savedFiling.rawLlmResponse.contains("STOCK1"))
        assertTrue(savedFiling.rawLlmResponse.contains("STOCK100"))
    }

    @Test
    fun `should delete filing by id`() {
        // Given
        val filing =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId,
                docId = "20250401-001",
                rawLlmResponse = """{"transactions": []}""",
                createdAt = now,
            )
        val savedFiling = housePtrFilingRepository.save(filing)
        val filingId = savedFiling.id!!

        // Verify it exists
        assertTrue(housePtrFilingRepository.existsById(filingId))

        // When
        housePtrFilingRepository.deleteById(filingId)

        // Then
        assertFalse(housePtrFilingRepository.existsById(filingId))
        assertNull(housePtrFilingRepository.findByDocId("20250401-001"))
    }

    @Test
    fun `should handle multiple filings with different ocr result ids`() {
        // Given - Create another OCR result
        val download2 =
            HousePtrDownload(
                docId = "20250501-001",
                gcsUri = "gs://test-bucket/ptr/20250501-001.pdf",
                etag = "test-etag-2",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        val downloadId2 = housePtrDownloadRepository.save(download2).id!!

        val ocrResult2 =
            HousePtrOcrResult(
                docId = "20250501-001",
                housePtrDownloadId = downloadId2,
                gcsUri = "gs://test-bucket/ocr/20250501-001.json",
            )
        val ocrResultId2 = housePtrOcrResultRepository.save(ocrResult2).id!!

        // Create filings for different OCR results
        val filing1 =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId,
                docId = "20250101-001",
                rawLlmResponse = """{"transactions": [{"asset": "AAPL"}]}""",
                createdAt = now,
            )
        val filing2 =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId2,
                docId = "20250501-001",
                rawLlmResponse = """{"transactions": [{"asset": "TSLA"}]}""",
                createdAt = now,
            )
        housePtrFilingRepository.save(filing1)
        housePtrFilingRepository.save(filing2)

        // When
        val filings1 = housePtrFilingRepository.findByHousePtrOcrResultId(ocrResultId)
        val filings2 = housePtrFilingRepository.findByHousePtrOcrResultId(ocrResultId2)

        // Then
        assertEquals(1, filings1.size)
        assertEquals(ocrResultId, filings1[0].housePtrOcrResultId)
        assertTrue(filings1[0].rawLlmResponse.contains("AAPL"))

        assertEquals(1, filings2.size)
        assertEquals(ocrResultId2, filings2[0].housePtrOcrResultId)
        assertTrue(filings2[0].rawLlmResponse.contains("TSLA"))
    }

    @Test
    fun `should retrieve all filings`() {
        // Given
        val filing1 =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId,
                docId = "20250601-001",
                rawLlmResponse = """{"transactions": [{"asset": "NFLX"}]}""",
                createdAt = now,
            )
        val filing2 =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId,
                docId = "20250601-002",
                rawLlmResponse = """{"transactions": [{"asset": "AMZN"}]}""",
                createdAt = now.plusSeconds(120),
            )
        housePtrFilingRepository.save(filing1)
        housePtrFilingRepository.save(filing2)

        // When
        val allFilings = housePtrFilingRepository.findAll().toList()

        // Then
        assertTrue(allFilings.size >= 2)
        assertTrue(allFilings.any { it.rawLlmResponse.contains("NFLX") })
        assertTrue(allFilings.any { it.rawLlmResponse.contains("AMZN") })
    }

    @Test
    fun `should count filings correctly`() {
        // Given
        val initialCount = housePtrFilingRepository.count()

        val filing =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId,
                docId = "20250701-001",
                rawLlmResponse = """{"transactions": [{"asset": "FB"}]}""",
                createdAt = now,
            )
        housePtrFilingRepository.save(filing)

        // When
        val newCount = housePtrFilingRepository.count()

        // Then
        assertEquals(initialCount + 1, newCount)
    }

    @Test
    fun `should cascade delete filings when ocr result is deleted`() {
        // Given
        val filing =
            HousePtrFiling(
                housePtrOcrResultId = ocrResultId,
                docId = "20250801-001",
                rawLlmResponse = """{"transactions": [{"asset": "INTC"}]}""",
                createdAt = now,
            )
        housePtrFilingRepository.save(filing)

        // Verify filing exists
        val filings = housePtrFilingRepository.findByHousePtrOcrResultId(ocrResultId)
        assertTrue(filings.isNotEmpty())

        // When - Delete the OCR result
        housePtrOcrResultRepository.deleteById(ocrResultId)

        // Then - Filings should be cascade deleted
        val remainingFilings = housePtrFilingRepository.findByHousePtrOcrResultId(ocrResultId)
        assertTrue(remainingFilings.isEmpty())
    }
}
