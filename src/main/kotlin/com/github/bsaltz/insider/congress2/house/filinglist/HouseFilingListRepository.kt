package com.github.bsaltz.insider.congress2.house.filinglist

import org.springframework.data.repository.CrudRepository

interface HouseFilingListRepository : CrudRepository<HouseFilingList, Long> {
    fun findByYear(year: Int): HouseFilingList?
}
