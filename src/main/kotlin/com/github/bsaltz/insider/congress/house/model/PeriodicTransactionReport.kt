package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.annotation.Id

data class PeriodicTransactionReport(
    @Id
    val id: Long? = null,
    val docId: String,
    val filerFullName: String,
    val filerStatus: FilerStatus,
    val state: String,
    val district: Int,
    val fileSourceUrl: String,
)
