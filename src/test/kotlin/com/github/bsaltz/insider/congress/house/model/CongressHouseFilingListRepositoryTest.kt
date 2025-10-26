package com.github.bsaltz.insider.congress.house.model

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.test.context.ActiveProfiles

@DataJdbcTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
class CongressHouseFilingListRepositoryTest(
    @param:Autowired
    private val congressHouseFilingListRepository: CongressHouseFilingListRepository,
) {
    @Test
    fun `should save and retrieve CongressHouseFilingList`() {
        // Given
        val filingList =
            CongressHouseFilingList(
                year = 2025,
                etag = "test-etag-12345",
            )

        // When
        val savedFilingList = congressHouseFilingListRepository.save(filingList)

        // Then
        assertNotNull(savedFilingList.id)
        assertEquals(2025, savedFilingList.year)
        assertEquals("test-etag-12345", savedFilingList.etag)

        // When retrieving by year
        val retrievedFilingList = congressHouseFilingListRepository.findOneByYear(2025)

        // Then
        assertNotNull(retrievedFilingList)
        assertEquals(savedFilingList.id, retrievedFilingList!!.id)
        assertEquals(2025, retrievedFilingList.year)
        assertEquals("test-etag-12345", retrievedFilingList.etag)

        // When retrieving by non-existent year
        val nonExistentFilingList = congressHouseFilingListRepository.findOneByYear(2024)

        // Then
        assertNull(nonExistentFilingList)
    }
}
