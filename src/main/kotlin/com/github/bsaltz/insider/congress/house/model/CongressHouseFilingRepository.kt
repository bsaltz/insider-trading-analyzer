package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.repository.CrudRepository
import java.time.LocalDate

interface CongressHouseFilingRepository : CrudRepository<CongressHouseFiling, Long> {
    fun findOneByDocId(docId: String): CongressHouseFiling?

    fun findByFilingDateBetween(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CongressHouseFiling>
}
