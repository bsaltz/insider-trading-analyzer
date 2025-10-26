package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.repository.CrudRepository

interface CongressHouseFilingListRepository : CrudRepository<CongressHouseFilingList, Long> {
    fun findOneByYear(year: Int): CongressHouseFilingList?
}
