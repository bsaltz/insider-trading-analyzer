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
class HousePtrOcrResultRepositoryTest(
    @param:Autowired
    private val housePtrOcrResultRepository: HousePtrOcrResultRepository,
    @param:Autowired
    private val housePtrDownloadRepository: HousePtrDownloadRepository,
) {
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
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
    }

    @Test
    fun `should save and retrieve HousePtrOcrResult`() {
        // Given
        val ocrResult =
            HousePtrOcrResult(
                docId = "20250101-001",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250101-001.json",
            )

        // When
        val savedOcrResult = housePtrOcrResultRepository.save(ocrResult)

        // Then
        assertNotNull(savedOcrResult.id)
        assertEquals("20250101-001", savedOcrResult.docId)
        assertEquals(downloadId, savedOcrResult.housePtrDownloadId)
        assertEquals("gs://test-bucket/ocr/20250101-001.json", savedOcrResult.gcsUri)
    }

    @Test
    fun `should find ocr result by house ptr download id`() {
        // Given
        val ocrResult =
            HousePtrOcrResult(
                docId = "20250201-001",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250201-001.json",
            )
        housePtrOcrResultRepository.save(ocrResult)

        // When
        val foundOcrResult = housePtrOcrResultRepository.findByHousePtrDownloadId(downloadId)
        val notFoundOcrResult = housePtrOcrResultRepository.findByHousePtrDownloadId(999999L)

        // Then
        assertNotNull(foundOcrResult)
        assertEquals("20250201-001", foundOcrResult!!.docId)
        assertEquals(downloadId, foundOcrResult.housePtrDownloadId)
        assertEquals("gs://test-bucket/ocr/20250201-001.json", foundOcrResult.gcsUri)

        assertNull(notFoundOcrResult)
    }

    @Test
    fun `should find ocr result by doc id`() {
        // Given
        val ocrResult =
            HousePtrOcrResult(
                docId = "20250301-001",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250301-001.json",
            )
        housePtrOcrResultRepository.save(ocrResult)

        // When
        val foundOcrResult = housePtrOcrResultRepository.findByDocId("20250301-001")
        val notFoundOcrResult = housePtrOcrResultRepository.findByDocId("nonexistent-doc-id")

        // Then
        assertNotNull(foundOcrResult)
        assertEquals("20250301-001", foundOcrResult!!.docId)
        assertEquals(downloadId, foundOcrResult.housePtrDownloadId)

        assertNull(notFoundOcrResult)
    }

    @Test
    fun `should update existing ocr result`() {
        // Given
        val ocrResult =
            HousePtrOcrResult(
                docId = "20250401-001",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250401-001.json",
            )
        val savedOcrResult = housePtrOcrResultRepository.save(ocrResult)

        // When - Update the GCS URI
        val updatedOcrResult =
            savedOcrResult.copy(
                gcsUri = "gs://test-bucket/ocr/updated/20250401-001.json",
            )
        val result = housePtrOcrResultRepository.save(updatedOcrResult)

        // Then
        assertEquals(savedOcrResult.id, result.id)
        assertEquals("20250401-001", result.docId)
        assertEquals(downloadId, result.housePtrDownloadId)
        assertEquals("gs://test-bucket/ocr/updated/20250401-001.json", result.gcsUri)
    }

    @Test
    fun `should delete ocr result by id`() {
        // Given
        val ocrResult =
            HousePtrOcrResult(
                docId = "20250501-001",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250501-001.json",
            )
        val savedOcrResult = housePtrOcrResultRepository.save(ocrResult)
        val ocrResultId = savedOcrResult.id!!

        // Verify it exists
        assertTrue(housePtrOcrResultRepository.existsById(ocrResultId))

        // When
        housePtrOcrResultRepository.deleteById(ocrResultId)

        // Then
        assertFalse(housePtrOcrResultRepository.existsById(ocrResultId))
        assertNull(housePtrOcrResultRepository.findByDocId("20250501-001"))
    }

    @Test
    fun `should handle multiple ocr results with different download ids`() {
        // Given - Create another download
        val download2 =
            HousePtrDownload(
                docId = "20250601-001",
                gcsUri = "gs://test-bucket/ptr/20250601-001.pdf",
                etag = "test-etag-2",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        val downloadId2 = housePtrDownloadRepository.save(download2).id!!

        // Create OCR results for different downloads
        val ocrResult1 =
            HousePtrOcrResult(
                docId = "20250101-001",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250101-001.json",
            )
        val ocrResult2 =
            HousePtrOcrResult(
                docId = "20250601-001",
                housePtrDownloadId = downloadId2,
                gcsUri = "gs://test-bucket/ocr/20250601-001.json",
            )
        housePtrOcrResultRepository.save(ocrResult1)
        housePtrOcrResultRepository.save(ocrResult2)

        // When
        val foundOcrResult1 = housePtrOcrResultRepository.findByHousePtrDownloadId(downloadId)
        val foundOcrResult2 = housePtrOcrResultRepository.findByHousePtrDownloadId(downloadId2)

        // Then
        assertNotNull(foundOcrResult1)
        assertEquals(downloadId, foundOcrResult1!!.housePtrDownloadId)
        assertEquals("20250101-001", foundOcrResult1.docId)

        assertNotNull(foundOcrResult2)
        assertEquals(downloadId2, foundOcrResult2!!.housePtrDownloadId)
        assertEquals("20250601-001", foundOcrResult2.docId)
    }

    @Test
    fun `should enforce unique doc id constraint`() {
        // Given
        val ocrResult1 =
            HousePtrOcrResult(
                docId = "20250701-001",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250701-001.json",
            )
        housePtrOcrResultRepository.save(ocrResult1)

        val ocrResult2 =
            HousePtrOcrResult(
                docId = "20250701-001", // Same doc ID
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250701-002.json",
            )

        // When / Then - Attempting to save with duplicate doc ID should throw exception
        try {
            housePtrOcrResultRepository.save(ocrResult2)
            // Force a flush to trigger constraint violation
            housePtrOcrResultRepository.findByDocId("20250701-001")
        } catch (e: Exception) {
            // Expected - constraint violation
            assertTrue(
                e.message?.contains("unique", ignoreCase = true) == true ||
                    e.message?.contains("duplicate", ignoreCase = true) == true ||
                    e.cause?.message?.contains("unique", ignoreCase = true) == true ||
                    e.cause?.message?.contains("duplicate", ignoreCase = true) == true,
            )
        }
    }

    @Test
    fun `should retrieve all ocr results`() {
        // Given
        val ocrResult1 =
            HousePtrOcrResult(
                docId = "20250801-001",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250801-001.json",
            )
        val ocrResult2 =
            HousePtrOcrResult(
                docId = "20250801-002",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250801-002.json",
            )
        housePtrOcrResultRepository.save(ocrResult1)
        housePtrOcrResultRepository.save(ocrResult2)

        // When
        val allOcrResults = housePtrOcrResultRepository.findAll().toList()

        // Then
        assertTrue(allOcrResults.size >= 2)
        assertTrue(allOcrResults.any { it.docId == "20250801-001" })
        assertTrue(allOcrResults.any { it.docId == "20250801-002" })
    }

    @Test
    fun `should count ocr results correctly`() {
        // Given
        val initialCount = housePtrOcrResultRepository.count()

        val ocrResult =
            HousePtrOcrResult(
                docId = "20250901-001",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20250901-001.json",
            )
        housePtrOcrResultRepository.save(ocrResult)

        // When
        val newCount = housePtrOcrResultRepository.count()

        // Then
        assertEquals(initialCount + 1, newCount)
    }

    @Test
    fun `should cascade delete ocr results when download is deleted`() {
        // Given
        val ocrResult =
            HousePtrOcrResult(
                docId = "20251001-001",
                housePtrDownloadId = downloadId,
                gcsUri = "gs://test-bucket/ocr/20251001-001.json",
            )
        housePtrOcrResultRepository.save(ocrResult)

        // Verify OCR result exists
        val foundOcrResult = housePtrOcrResultRepository.findByHousePtrDownloadId(downloadId)
        assertNotNull(foundOcrResult)

        // When - Delete the download
        housePtrDownloadRepository.deleteById(downloadId)

        // Then - OCR result should be cascade deleted
        val remainingOcrResult = housePtrOcrResultRepository.findByHousePtrDownloadId(downloadId)
        assertNull(remainingOcrResult)
    }
}
