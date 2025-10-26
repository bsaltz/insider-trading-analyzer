package com.github.bsaltz.insider.congress2.house.ptr

import org.springframework.data.repository.CrudRepository

interface HousePtrDownloadRepository : CrudRepository<HousePtrDownload, Long> {
    fun findByDocId(docId: String): HousePtrDownload?
    fun findByParsed(parsed: Boolean): List<HousePtrDownload>
}
