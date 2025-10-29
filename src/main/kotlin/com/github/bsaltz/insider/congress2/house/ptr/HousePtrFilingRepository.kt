package com.github.bsaltz.insider.congress2.house.ptr

import org.springframework.data.repository.CrudRepository

interface HousePtrFilingRepository : CrudRepository<HousePtrFiling, Long> {
    fun findByHousePtrOcrResultId(housePtrOcrResultId: Long): List<HousePtrFiling>

    fun findByDocId(docId: String): List<HousePtrFiling>
}
