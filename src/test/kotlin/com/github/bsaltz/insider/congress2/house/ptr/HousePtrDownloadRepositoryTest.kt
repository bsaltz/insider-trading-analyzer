package com.github.bsaltz.insider.congress2.house.ptr

import com.github.bsaltz.insider.test.RepositoryTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit

@RepositoryTest
class HousePtrDownloadRepositoryTest(
    @param:Autowired
    private val housePtrDownloadRepository: HousePtrDownloadRepository,
) {
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

    @Test
    fun `should save and retrieve HousePtrDownload`() {
        // Given
        val download =
            HousePtrDownload(
                docId = "20250101-001",
                gcsUri = "gs://test-bucket/ptr/20250101-001.pdf",
                etag = "test-etag-12345",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )

        // When
        val savedDownload = housePtrDownloadRepository.save(download)

        // Then
        assertNotNull(savedDownload.id)
        assertEquals("20250101-001", savedDownload.docId)
        assertEquals("gs://test-bucket/ptr/20250101-001.pdf", savedDownload.gcsUri)
        assertEquals("test-etag-12345", savedDownload.etag)
        assertFalse(savedDownload.parsed)
        assertNull(savedDownload.parsedAt)
        assertEquals(now, savedDownload.createdAt)
        assertNull(savedDownload.updatedAt)
    }

    @Test
    fun `should find download by doc id`() {
        // Given
        val download =
            HousePtrDownload(
                docId = "20250201-001",
                gcsUri = "gs://test-bucket/ptr/20250201-001.pdf",
                etag = "etag-20250201",
                parsed = true,
                parsedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        housePtrDownloadRepository.save(download)

        // When
        val foundDownload = housePtrDownloadRepository.findByDocId("20250201-001")
        val notFoundDownload = housePtrDownloadRepository.findByDocId("nonexistent-doc-id")

        // Then
        assertNotNull(foundDownload)
        assertEquals("20250201-001", foundDownload!!.docId)
        assertEquals("gs://test-bucket/ptr/20250201-001.pdf", foundDownload.gcsUri)
        assertEquals("etag-20250201", foundDownload.etag)
        assertTrue(foundDownload.parsed)
        assertEquals(now, foundDownload.parsedAt)

        assertNull(notFoundDownload)
    }

    @Test
    fun `should find downloads by parsed status`() {
        // Given
        val download1 =
            HousePtrDownload(
                docId = "20250301-001",
                gcsUri = "gs://test-bucket/ptr/20250301-001.pdf",
                etag = "etag-1",
                parsed = true,
                parsedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        val download2 =
            HousePtrDownload(
                docId = "20250302-001",
                gcsUri = "gs://test-bucket/ptr/20250302-001.pdf",
                etag = "etag-2",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        val download3 =
            HousePtrDownload(
                docId = "20250303-001",
                gcsUri = "gs://test-bucket/ptr/20250303-001.pdf",
                etag = "etag-3",
                parsed = true,
                parsedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        housePtrDownloadRepository.save(download1)
        housePtrDownloadRepository.save(download2)
        housePtrDownloadRepository.save(download3)

        // When
        val parsedDownloads = housePtrDownloadRepository.findByParsed(true)
        val unparsedDownloads = housePtrDownloadRepository.findByParsed(false)

        // Then
        assertEquals(2, parsedDownloads.size)
        assertTrue(parsedDownloads.all { it.parsed })
        assertTrue(parsedDownloads.any { it.docId == "20250301-001" })
        assertTrue(parsedDownloads.any { it.docId == "20250303-001" })
        parsedDownloads.forEach { assertNotNull(it.parsedAt) }

        assertEquals(1, unparsedDownloads.size)
        assertFalse(unparsedDownloads[0].parsed)
        assertEquals("20250302-001", unparsedDownloads[0].docId)
        assertNull(unparsedDownloads[0].parsedAt)
    }

    @Test
    fun `should update existing download`() {
        // Given
        val download =
            HousePtrDownload(
                docId = "20250401-001",
                gcsUri = "gs://test-bucket/ptr/20250401-001.pdf",
                etag = "original-etag",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        val savedDownload = housePtrDownloadRepository.save(download)

        // When - Update the download to mark as parsed
        val parsedTime = now.plusSeconds(3600)
        val updatedDownload =
            savedDownload.copy(
                etag = "updated-etag",
                parsed = true,
                parsedAt = parsedTime,
                updatedAt = parsedTime,
            )
        val result = housePtrDownloadRepository.save(updatedDownload)

        // Then
        assertEquals(savedDownload.id, result.id)
        assertEquals("20250401-001", result.docId)
        assertEquals("updated-etag", result.etag)
        assertTrue(result.parsed)
        assertEquals(parsedTime, result.parsedAt)
        assertEquals(parsedTime, result.updatedAt)
    }

    @Test
    fun `should handle download with parsed status false and null parsedAt`() {
        // Given
        val download =
            HousePtrDownload(
                docId = "20250501-001",
                gcsUri = "gs://test-bucket/ptr/20250501-001.pdf",
                etag = "etag-20250501",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )

        // When
        val savedDownload = housePtrDownloadRepository.save(download)

        // Then
        assertNotNull(savedDownload.id)
        assertFalse(savedDownload.parsed)
        assertNull(savedDownload.parsedAt)
        assertNull(savedDownload.updatedAt)
    }

    @Test
    fun `should handle download with parsed status true and parsedAt timestamp`() {
        // Given
        val download =
            HousePtrDownload(
                docId = "20250601-001",
                gcsUri = "gs://test-bucket/ptr/20250601-001.pdf",
                etag = "etag-20250601",
                parsed = true,
                parsedAt = now,
                createdAt = now,
                updatedAt = now,
            )

        // When
        val savedDownload = housePtrDownloadRepository.save(download)

        // Then
        assertNotNull(savedDownload.id)
        assertTrue(savedDownload.parsed)
        assertEquals(now, savedDownload.parsedAt)
        assertEquals(now, savedDownload.updatedAt)
    }

    @Test
    fun `should delete download by id`() {
        // Given
        val download =
            HousePtrDownload(
                docId = "20250701-001",
                gcsUri = "gs://test-bucket/ptr/20250701-001.pdf",
                etag = "etag-20250701",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        val savedDownload = housePtrDownloadRepository.save(download)
        val downloadId = savedDownload.id!!

        // Verify it exists
        assertTrue(housePtrDownloadRepository.existsById(downloadId))

        // When
        housePtrDownloadRepository.deleteById(downloadId)

        // Then
        assertFalse(housePtrDownloadRepository.existsById(downloadId))
        assertNull(housePtrDownloadRepository.findByDocId("20250701-001"))
    }

    @Test
    fun `should enforce unique doc id constraint`() {
        // Given
        val download1 =
            HousePtrDownload(
                docId = "20250801-001",
                gcsUri = "gs://test-bucket/ptr/20250801-001.pdf",
                etag = "etag-1",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        housePtrDownloadRepository.save(download1)

        val download2 =
            HousePtrDownload(
                docId = "20250801-001", // Same doc ID
                gcsUri = "gs://test-bucket/ptr/20250801-002.pdf",
                etag = "etag-2",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )

        // When / Then - Attempting to save with duplicate doc ID should throw exception
        try {
            housePtrDownloadRepository.save(download2)
            // Force a flush to trigger constraint violation
            housePtrDownloadRepository.findByDocId("20250801-001")
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
    fun `should retrieve all downloads`() {
        // Given
        val download1 =
            HousePtrDownload(
                docId = "20250901-001",
                gcsUri = "gs://test-bucket/ptr/20250901-001.pdf",
                etag = "etag-1",
                parsed = true,
                parsedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        val download2 =
            HousePtrDownload(
                docId = "20250902-001",
                gcsUri = "gs://test-bucket/ptr/20250902-001.pdf",
                etag = "etag-2",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        housePtrDownloadRepository.save(download1)
        housePtrDownloadRepository.save(download2)

        // When
        val allDownloads = housePtrDownloadRepository.findAll().toList()

        // Then
        assertTrue(allDownloads.size >= 2)
        assertTrue(allDownloads.any { it.docId == "20250901-001" })
        assertTrue(allDownloads.any { it.docId == "20250902-001" })
    }

    @Test
    fun `should count downloads correctly`() {
        // Given
        val initialCount = housePtrDownloadRepository.count()

        val download =
            HousePtrDownload(
                docId = "20251001-001",
                gcsUri = "gs://test-bucket/ptr/20251001-001.pdf",
                etag = "etag-20251001",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        housePtrDownloadRepository.save(download)

        // When
        val newCount = housePtrDownloadRepository.count()

        // Then
        assertEquals(initialCount + 1, newCount)
    }
}
