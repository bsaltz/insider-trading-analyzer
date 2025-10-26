package com.github.bsaltz.insider.congress2.house.ptr

import org.springframework.data.annotation.Id

data class HousePtrOcrResult(
    @Id
    val id: Long? = null,
    val docId: String,
    val housePtrDownloadId: Long,
    val gcsUri: String,
)
