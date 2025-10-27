package com.github.bsaltz.insider.congress2.house.filinglist

import org.springframework.data.repository.CrudRepository
import java.time.LocalDate

interface HouseFilingListRowRepository : CrudRepository<HouseFilingListRow, Long> {
    fun findByHouseFilingListId(houseFilingListId: Long): List<HouseFilingListRow>

    fun findByFilingDateBetween(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<HouseFilingListRow>

    fun findByDownloaded(downloaded: Boolean): List<HouseFilingListRow>

    fun findByFilingType(filingType: String): List<HouseFilingListRow>

    fun findByYear(year: Int): List<HouseFilingListRow>

    fun findByDocId(docId: String): HouseFilingListRow?

    fun deleteByHouseFilingListId(houseFilingListId: Long)
}
