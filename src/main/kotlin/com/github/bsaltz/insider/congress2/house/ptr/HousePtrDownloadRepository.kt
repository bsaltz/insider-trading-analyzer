package com.github.bsaltz.insider.congress2.house.ptr

import org.springframework.data.repository.CrudRepository

interface HousePtrDownloadRepository : CrudRepository<HousePtrDownload, Long> {
    fun findByDocId(docId: String): HousePtrDownload?

    fun findByParsed(parsed: Boolean): List<HousePtrDownload>

    /**
     * Find all downloads matching any of the provided document IDs.
     * Useful for batch queries to avoid N+1 query problems.
     */
    fun findByDocIdIn(docIds: List<String>): List<HousePtrDownload>
}
