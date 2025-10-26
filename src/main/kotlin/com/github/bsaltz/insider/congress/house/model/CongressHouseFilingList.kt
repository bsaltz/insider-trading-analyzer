package com.github.bsaltz.insider.congress.house.model

import org.springframework.data.annotation.Id

data class CongressHouseFilingList(
    @Id
    val id: Long? = null,
    val year: Int,
    val etag: String,
)
