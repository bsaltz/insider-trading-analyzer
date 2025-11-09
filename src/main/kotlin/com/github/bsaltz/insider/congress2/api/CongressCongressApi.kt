package com.github.bsaltz.insider.congress2.api

import com.github.bsaltz.insider.congress2.api.CongressApi.Companion.get
import org.springframework.stereotype.Component

@Component
class CongressCongressApi(
    private val congressApi: CongressApi,
) {
    fun getCongressList(): CongressListResponse = congressApi.get("/congress") ?: CongressListResponse(emptyList())

    fun getCongress(number: Int): CongressResponse? = congressApi.get("/congress/$number")

    fun getCurrentCongress(): CongressResponse? = congressApi.get("/congress/current")
}

data class CongressListResponse(
    val congresses: List<CongressListItem>,
)

data class CongressListItem(
    val endYear: String,
    val name: String,
    val startYear: String,
)

data class CongressResponse(
    val congress: Congress,
)

data class Congress(
    val endYear: String,
    val name: String,
    val startYear: String,
    val number: Int,
)
