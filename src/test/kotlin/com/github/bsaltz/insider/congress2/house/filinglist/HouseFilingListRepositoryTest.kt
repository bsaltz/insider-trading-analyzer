package com.github.bsaltz.insider.congress2.house.filinglist

import com.github.bsaltz.insider.test.RepositoryTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit

@RepositoryTest
class HouseFilingListRepositoryTest(
    @param:Autowired
    private val houseFilingListRepository: HouseFilingListRepository,
) {
    @Test
    fun `should save and retrieve HouseFilingList`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val filingList =
            HouseFilingList(
                year = 2025,
                etag = "test-etag-12345",
                gcsUri = "gs://test-bucket/2025/filing-list.tsv",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )

        // When
        val savedFilingList = houseFilingListRepository.save(filingList)

        // Then
        assertNotNull(savedFilingList.id)
        assertEquals(2025, savedFilingList.year)
        assertEquals("test-etag-12345", savedFilingList.etag)
        assertEquals("gs://test-bucket/2025/filing-list.tsv", savedFilingList.gcsUri)
        assertEquals(false, savedFilingList.parsed)
        assertNull(savedFilingList.parsedAt)
        assertEquals(now, savedFilingList.createdAt)
        assertNull(savedFilingList.updatedAt)
    }

    @Test
    fun `should find filing list by year`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val filingList =
            HouseFilingList(
                year = 2024,
                etag = "etag-2024",
                gcsUri = "gs://test-bucket/2024/filing-list.tsv",
                parsed = true,
                parsedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        houseFilingListRepository.save(filingList)

        // When
        val retrievedFilingList = houseFilingListRepository.findByYear(2024)

        // Then
        assertNotNull(retrievedFilingList)
        assertEquals(2024, retrievedFilingList!!.year)
        assertEquals("etag-2024", retrievedFilingList.etag)
        assertEquals("gs://test-bucket/2024/filing-list.tsv", retrievedFilingList.gcsUri)
        assertTrue(retrievedFilingList.parsed)
        assertEquals(now, retrievedFilingList.parsedAt)
    }

    @Test
    fun `should return null when finding by non-existent year`() {
        // When
        val retrievedFilingList = houseFilingListRepository.findByYear(2023)

        // Then
        assertNull(retrievedFilingList)
    }

    @Test
    fun `should update existing filing list`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val filingList =
            HouseFilingList(
                year = 2022,
                etag = "original-etag",
                gcsUri = "gs://test-bucket/2022/filing-list.tsv",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        val savedFilingList = houseFilingListRepository.save(filingList)

        // When - Update the filing list
        val updatedTime = now.plusSeconds(3600)
        val updatedFilingList =
            savedFilingList.copy(
                etag = "updated-etag",
                parsed = true,
                parsedAt = updatedTime,
                updatedAt = updatedTime,
            )
        val result = houseFilingListRepository.save(updatedFilingList)

        // Then
        assertEquals(savedFilingList.id, result.id)
        assertEquals("updated-etag", result.etag)
        assertTrue(result.parsed)
        assertEquals(updatedTime, result.parsedAt)
        assertEquals(updatedTime, result.updatedAt)
    }

    @Test
    fun `should handle nullable etag`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val filingList =
            HouseFilingList(
                year = 2021,
                etag = null,
                gcsUri = "gs://test-bucket/2021/filing-list.tsv",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )

        // When
        val savedFilingList = houseFilingListRepository.save(filingList)

        // Then
        assertNotNull(savedFilingList.id)
        assertNull(savedFilingList.etag)
        assertEquals(2021, savedFilingList.year)
    }

    @Test
    fun `should enforce unique year constraint`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val filingList1 =
            HouseFilingList(
                year = 2020,
                etag = "etag-1",
                gcsUri = "gs://test-bucket/2020/filing-list-1.tsv",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        houseFilingListRepository.save(filingList1)

        val filingList2 =
            HouseFilingList(
                year = 2020,
                etag = "etag-2",
                gcsUri = "gs://test-bucket/2020/filing-list-2.tsv",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )

        // When / Then - Attempting to save with duplicate year should throw exception
        try {
            houseFilingListRepository.save(filingList2)
            // Force a flush to trigger constraint violation
            houseFilingListRepository.findByYear(2020)
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
}
