package com.github.bsaltz.insider.congress2.api

import com.github.bsaltz.insider.congress2.api.CongressApi.Companion.get
import org.springframework.stereotype.Component

@Component
class CongressMemberApi(
    private val congressApi: CongressApi,
) {
    fun getMemberList(
        offset: Int = 0,
        limit: Int = 25,
    ): MemberListResponse =
        congressApi.get(
            "/member",
            parameters = mapOf("offset" to offset.toString(), "limit" to limit.toString()),
        ) ?: MemberListResponse(emptyList())

    fun getMember(bioguideId: String): MemberResponse? = congressApi.get("/member/$bioguideId")

    fun getMemberListForCongress(
        congress: Int,
        offset: Int = 0,
        limit: Int = 25,
    ): MemberListResponse =
        congressApi.get(
            "/member/congress/$congress",
            parameters = mapOf("offset" to offset.toString(), "limit" to limit.toString())
        ) ?: MemberListResponse(emptyList())
}

data class MemberListResponse(
    val members: List<MemberListItem>,
)

data class MemberListItem(
    val bioguideId: String,
    val district: String?,
    val name: String,
    val state: String,
)

data class MemberResponse(
    val member: Member,
)

data class Member(
    val bioguideId: String,
    val district: String?,
    val invertedOrderName: String,
    val state: String,
    val cosponsoredLegislation: CountSummary,
    val sponsoredLegislation: CountSummary,
    val leadership: List<MemberLeadershipItem>,
)

data class CountSummary(
    val count: Long,
)

data class MemberLeadershipItem(
    val congress: Int,
    val type: String,
)
