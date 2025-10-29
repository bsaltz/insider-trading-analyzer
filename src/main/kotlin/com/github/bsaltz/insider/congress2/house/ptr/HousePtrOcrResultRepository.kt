package com.github.bsaltz.insider.congress2.house.ptr

import org.springframework.data.repository.CrudRepository

interface HousePtrOcrResultRepository : CrudRepository<HousePtrOcrResult, Long> {
    fun findByHousePtrDownloadId(housePtrDownloadId: Long): HousePtrOcrResult?

    fun findByDocId(docId: String): HousePtrOcrResult?

    /**
     * Find all OCR results matching any of the provided document IDs.
     * Useful for batch queries to avoid N+1 query problems.
     */
    fun findByDocIdIn(docIds: List<String>): List<HousePtrOcrResult>
}
