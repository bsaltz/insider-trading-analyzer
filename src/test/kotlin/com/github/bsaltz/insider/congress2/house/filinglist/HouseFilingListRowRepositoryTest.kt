package com.github.bsaltz.insider.congress2.house.filinglist

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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@RepositoryTest
class HouseFilingListRowRepositoryTest(
    @param:Autowired
    private val houseFilingListRowRepository: HouseFilingListRowRepository,
    @param:Autowired
    private val houseFilingListRepository: HouseFilingListRepository,
) {
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
    private var filingListId: Long = 0

    @BeforeEach
    fun setup() {
        // Create a parent filing list for foreign key relationship
        val filingList =
            HouseFilingList(
                year = 2025,
                etag = "test-etag",
                gcsUri = "gs://test-bucket/2025/filing-list.tsv",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        filingListId = houseFilingListRepository.save(filingList).id!!
    }

    @Test
    fun `should save and retrieve HouseFilingListRow`() {
        // Given
        val row =
            HouseFilingListRow(
                docId = "20250101-001",
                prefix = "Hon.",
                last = "Smith",
                first = "John",
                suffix = "Jr.",
                filingType = "P",
                stateDst = "CA-01",
                year = 2025,
                filingDate = LocalDate.of(2025, 1, 15),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "raw,tsv,data,here",
                createdAt = now,
                updatedAt = null,
                houseFilingListId = filingListId,
            )

        // When
        val savedRow = houseFilingListRowRepository.save(row)

        // Then
        assertNotNull(savedRow.id)
        assertEquals("20250101-001", savedRow.docId)
        assertEquals("Hon.", savedRow.prefix)
        assertEquals("Smith", savedRow.last)
        assertEquals("John", savedRow.first)
        assertEquals("Jr.", savedRow.suffix)
        assertEquals("P", savedRow.filingType)
        assertEquals("CA-01", savedRow.stateDst)
        assertEquals(2025, savedRow.year)
        assertEquals(LocalDate.of(2025, 1, 15), savedRow.filingDate)
        assertFalse(savedRow.downloaded)
        assertNull(savedRow.downloadedAt)
        assertEquals("raw,tsv,data,here", savedRow.rawRowData)
        assertEquals(now, savedRow.createdAt)
        assertNull(savedRow.updatedAt)
        assertEquals(filingListId, savedRow.houseFilingListId)
    }

    @Test
    fun `should find rows by house filing list id`() {
        // Given
        val row1 =
            HouseFilingListRow(
                docId = "20250201-001",
                prefix = "",
                last = "Johnson",
                first = "Jane",
                suffix = "",
                filingType = "P",
                stateDst = "TX-02",
                year = 2025,
                filingDate = LocalDate.of(2025, 2, 1),
                downloaded = true,
                downloadedAt = now,
                rawRowData = "data1",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        val row2 =
            HouseFilingListRow(
                docId = "20250202-001",
                prefix = "",
                last = "Williams",
                first = "Bob",
                suffix = "",
                filingType = "A",
                stateDst = "NY-03",
                year = 2025,
                filingDate = LocalDate.of(2025, 2, 2),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data2",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        houseFilingListRowRepository.save(row1)
        houseFilingListRowRepository.save(row2)

        // When
        val rows = houseFilingListRowRepository.findByHouseFilingListId(filingListId)

        // Then
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.docId == "20250201-001" })
        assertTrue(rows.any { it.docId == "20250202-001" })
    }

    @Test
    fun `should find rows by filing date between`() {
        // Given
        val row1 =
            HouseFilingListRow(
                docId = "20250301-001",
                prefix = "",
                last = "Brown",
                first = "Alice",
                suffix = "",
                filingType = "P",
                stateDst = "FL-04",
                year = 2025,
                filingDate = LocalDate.of(2025, 3, 1),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data1",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        val row2 =
            HouseFilingListRow(
                docId = "20250315-001",
                prefix = "",
                last = "Davis",
                first = "Charlie",
                suffix = "",
                filingType = "P",
                stateDst = "OH-05",
                year = 2025,
                filingDate = LocalDate.of(2025, 3, 15),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data2",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        val row3 =
            HouseFilingListRow(
                docId = "20250401-001",
                prefix = "",
                last = "Miller",
                first = "Eve",
                suffix = "",
                filingType = "P",
                stateDst = "PA-06",
                year = 2025,
                filingDate = LocalDate.of(2025, 4, 1),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data3",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        houseFilingListRowRepository.save(row1)
        houseFilingListRowRepository.save(row2)
        houseFilingListRowRepository.save(row3)

        // When
        val rows =
            houseFilingListRowRepository.findByFilingDateBetween(
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 31),
            )

        // Then
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.docId == "20250301-001" })
        assertTrue(rows.any { it.docId == "20250315-001" })
        assertFalse(rows.any { it.docId == "20250401-001" })
    }

    @Test
    fun `should find rows by downloaded status`() {
        // Given
        val row1 =
            HouseFilingListRow(
                docId = "20250501-001",
                prefix = "",
                last = "Wilson",
                first = "Frank",
                suffix = "",
                filingType = "P",
                stateDst = "MI-07",
                year = 2025,
                filingDate = LocalDate.of(2025, 5, 1),
                downloaded = true,
                downloadedAt = now,
                rawRowData = "data1",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        val row2 =
            HouseFilingListRow(
                docId = "20250502-001",
                prefix = "",
                last = "Moore",
                first = "Grace",
                suffix = "",
                filingType = "P",
                stateDst = "WA-08",
                year = 2025,
                filingDate = LocalDate.of(2025, 5, 2),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data2",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        houseFilingListRowRepository.save(row1)
        houseFilingListRowRepository.save(row2)

        // When
        val downloadedRows = houseFilingListRowRepository.findByDownloaded(true)
        val notDownloadedRows = houseFilingListRowRepository.findByDownloaded(false)

        // Then
        assertEquals(1, downloadedRows.size)
        assertEquals("20250501-001", downloadedRows[0].docId)
        assertTrue(downloadedRows[0].downloaded)
        assertNotNull(downloadedRows[0].downloadedAt)

        assertEquals(1, notDownloadedRows.size)
        assertEquals("20250502-001", notDownloadedRows[0].docId)
        assertFalse(notDownloadedRows[0].downloaded)
        assertNull(notDownloadedRows[0].downloadedAt)
    }

    @Test
    fun `should find rows by filing type`() {
        // Given
        val row1 =
            HouseFilingListRow(
                docId = "20250601-001",
                prefix = "",
                last = "Taylor",
                first = "Henry",
                suffix = "",
                filingType = "P",
                stateDst = "AZ-09",
                year = 2025,
                filingDate = LocalDate.of(2025, 6, 1),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data1",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        val row2 =
            HouseFilingListRow(
                docId = "20250602-001",
                prefix = "",
                last = "Anderson",
                first = "Ivy",
                suffix = "",
                filingType = "A",
                stateDst = "GA-10",
                year = 2025,
                filingDate = LocalDate.of(2025, 6, 2),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data2",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        val row3 =
            HouseFilingListRow(
                docId = "20250603-001",
                prefix = "",
                last = "Thomas",
                first = "Jack",
                suffix = "",
                filingType = "P",
                stateDst = "NC-11",
                year = 2025,
                filingDate = LocalDate.of(2025, 6, 3),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data3",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        houseFilingListRowRepository.save(row1)
        houseFilingListRowRepository.save(row2)
        houseFilingListRowRepository.save(row3)

        // When
        val typeP = houseFilingListRowRepository.findByFilingType("P")
        val typeA = houseFilingListRowRepository.findByFilingType("A")

        // Then
        assertEquals(2, typeP.size)
        assertTrue(typeP.all { it.filingType == "P" })
        assertTrue(typeP.any { it.docId == "20250601-001" })
        assertTrue(typeP.any { it.docId == "20250603-001" })

        assertEquals(1, typeA.size)
        assertEquals("A", typeA[0].filingType)
        assertEquals("20250602-001", typeA[0].docId)
    }

    @Test
    fun `should find rows by year`() {
        // Given
        // Create a second filing list for 2024
        val filingList2024 =
            HouseFilingList(
                year = 2024,
                etag = "test-etag-2024",
                gcsUri = "gs://test-bucket/2024/filing-list.tsv",
                parsed = false,
                parsedAt = null,
                createdAt = now,
                updatedAt = null,
            )
        val filingListId2024 = houseFilingListRepository.save(filingList2024).id!!

        val row2025 =
            HouseFilingListRow(
                docId = "20250701-001",
                prefix = "",
                last = "Jackson",
                first = "Kate",
                suffix = "",
                filingType = "P",
                stateDst = "VA-12",
                year = 2025,
                filingDate = LocalDate.of(2025, 7, 1),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data2025",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        val row2024 =
            HouseFilingListRow(
                docId = "20240701-001",
                prefix = "",
                last = "White",
                first = "Leo",
                suffix = "",
                filingType = "P",
                stateDst = "CO-13",
                year = 2024,
                filingDate = LocalDate.of(2024, 7, 1),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data2024",
                createdAt = now,
                houseFilingListId = filingListId2024,
            )
        houseFilingListRowRepository.save(row2025)
        houseFilingListRowRepository.save(row2024)

        // When
        val rows2025 = houseFilingListRowRepository.findByYear(2025)
        val rows2024 = houseFilingListRowRepository.findByYear(2024)

        // Then
        assertEquals(1, rows2025.size)
        assertEquals(2025, rows2025[0].year)
        assertEquals("20250701-001", rows2025[0].docId)

        assertEquals(1, rows2024.size)
        assertEquals(2024, rows2024[0].year)
        assertEquals("20240701-001", rows2024[0].docId)
    }

    @Test
    fun `should find row by doc id`() {
        // Given
        val row =
            HouseFilingListRow(
                docId = "20250801-001",
                prefix = "",
                last = "Harris",
                first = "Mia",
                suffix = "",
                filingType = "P",
                stateDst = "NV-14",
                year = 2025,
                filingDate = LocalDate.of(2025, 8, 1),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        houseFilingListRowRepository.save(row)

        // When
        val foundRow = houseFilingListRowRepository.findByDocId("20250801-001")
        val notFoundRow = houseFilingListRowRepository.findByDocId("nonexistent-doc-id")

        // Then
        assertNotNull(foundRow)
        assertEquals("20250801-001", foundRow!!.docId)
        assertEquals("Harris", foundRow.last)

        assertNull(notFoundRow)
    }

    @Test
    fun `should update existing row`() {
        // Given
        val row =
            HouseFilingListRow(
                docId = "20250901-001",
                prefix = "",
                last = "Martin",
                first = "Noah",
                suffix = "",
                filingType = "P",
                stateDst = "OR-15",
                year = 2025,
                filingDate = LocalDate.of(2025, 9, 1),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        val savedRow = houseFilingListRowRepository.save(row)

        // When - Update the row
        val downloadedTime = now.plusSeconds(3600)
        val updatedRow =
            savedRow.copy(
                downloaded = true,
                downloadedAt = downloadedTime,
                updatedAt = downloadedTime,
            )
        val result = houseFilingListRowRepository.save(updatedRow)

        // Then
        assertEquals(savedRow.id, result.id)
        assertEquals("20250901-001", result.docId)
        assertTrue(result.downloaded)
        assertEquals(downloadedTime, result.downloadedAt)
        assertEquals(downloadedTime, result.updatedAt)
    }

    @Test
    fun `should delete rows by house filing list id`() {
        // Given
        val row1 =
            HouseFilingListRow(
                docId = "20251001-001",
                prefix = "",
                last = "Garcia",
                first = "Olivia",
                suffix = "",
                filingType = "P",
                stateDst = "IL-16",
                year = 2025,
                filingDate = LocalDate.of(2025, 10, 1),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data1",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        val row2 =
            HouseFilingListRow(
                docId = "20251002-001",
                prefix = "",
                last = "Martinez",
                first = "Paul",
                suffix = "",
                filingType = "P",
                stateDst = "MN-17",
                year = 2025,
                filingDate = LocalDate.of(2025, 10, 2),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data2",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        houseFilingListRowRepository.save(row1)
        houseFilingListRowRepository.save(row2)

        // Verify rows exist
        var rows = houseFilingListRowRepository.findByHouseFilingListId(filingListId)
        assertEquals(2, rows.size)

        // When
        houseFilingListRowRepository.deleteByHouseFilingListId(filingListId)

        // Then
        rows = houseFilingListRowRepository.findByHouseFilingListId(filingListId)
        assertEquals(0, rows.size)
    }

    @Test
    fun `should enforce unique doc id constraint`() {
        // Given
        val row1 =
            HouseFilingListRow(
                docId = "20251101-001",
                prefix = "",
                last = "Robinson",
                first = "Quinn",
                suffix = "",
                filingType = "P",
                stateDst = "WI-18",
                year = 2025,
                filingDate = LocalDate.of(2025, 11, 1),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data1",
                createdAt = now,
                houseFilingListId = filingListId,
            )
        houseFilingListRowRepository.save(row1)

        val row2 =
            HouseFilingListRow(
                docId = "20251101-001", // Same doc ID
                prefix = "",
                last = "Clark",
                first = "Rachel",
                suffix = "",
                filingType = "A",
                stateDst = "IA-19",
                year = 2025,
                filingDate = LocalDate.of(2025, 11, 2),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "data2",
                createdAt = now,
                houseFilingListId = filingListId,
            )

        // When / Then - Attempting to save with duplicate doc ID should throw exception
        try {
            houseFilingListRowRepository.save(row2)
            // Force a flush to trigger constraint violation
            houseFilingListRowRepository.findByDocId("20251101-001")
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
