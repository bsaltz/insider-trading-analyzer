package com.github.bsaltz.insider.congress.house.model

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@DataJdbcTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
class CongressHouseFilingRepositoryTest(
    @param:Autowired
    private val congressHouseFilingListRepository: CongressHouseFilingListRepository,
    @param:Autowired
    private val congressHouseFilingRepository: CongressHouseFilingRepository,
) {
    @Test
    fun `should save and retrieve CongressHouseFiling`() {
        // Given - create parent filing list first
        val filingList =
            CongressHouseFilingList(
                year = 2025,
                etag = "test-etag-parent",
            )
        val savedFilingList = congressHouseFilingListRepository.save(filingList)

        // Given - create filing
        val filing =
            CongressHouseFiling(
                congressHouseFilingListId = savedFilingList.id!!,
                docId = "20032062",
                prefix = "Hon.",
                last = "Aderholt",
                first = "Robert B.",
                suffix = "",
                filingType = "P",
                stateDst = "AL04",
                year = 2025,
                filingDate = LocalDate.of(2025, 9, 10),
                etag = "test-etag-filing",
            )

        // When
        val savedFiling = congressHouseFilingRepository.save(filing)

        // Then
        assertNotNull(savedFiling.id)
        assertEquals(savedFilingList.id, savedFiling.congressHouseFilingListId)
        assertEquals("20032062", savedFiling.docId)
        assertEquals("Hon.", savedFiling.prefix)
        assertEquals("Aderholt", savedFiling.last)
        assertEquals("Robert B.", savedFiling.first)
        assertEquals("", savedFiling.suffix)
        assertEquals("P", savedFiling.filingType)
        assertEquals("AL04", savedFiling.stateDst)
        assertEquals(2025, savedFiling.year)
        assertEquals(LocalDate.of(2025, 9, 10), savedFiling.filingDate)
        assertEquals("test-etag-filing", savedFiling.etag)

        // When retrieving by docId
        val retrievedFiling = congressHouseFilingRepository.findOneByDocId("20032062")

        // Then
        assertNotNull(retrievedFiling)
        assertEquals(savedFiling.id, retrievedFiling!!.id)
        assertEquals("20032062", retrievedFiling.docId)
        assertEquals("Hon.", retrievedFiling.prefix)
        assertEquals("Aderholt", retrievedFiling.last)

        // When retrieving by non-existent docId
        val nonExistentFiling = congressHouseFilingRepository.findOneByDocId("99999999")

        // Then
        assertNull(nonExistentFiling)
    }
}
