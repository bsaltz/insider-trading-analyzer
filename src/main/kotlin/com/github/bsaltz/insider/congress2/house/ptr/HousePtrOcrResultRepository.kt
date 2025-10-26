package com.github.bsaltz.insider.congress2.house.ptr

import org.springframework.data.repository.CrudRepository

interface HousePtrOcrResultRepository : CrudRepository<HousePtrOcrResult, Long> {
    fun findByHousePtrDownloadId(housePtrDownloadId: Long): HousePtrOcrResult?
    fun findByDocId(docId: String): HousePtrOcrResult?
}
