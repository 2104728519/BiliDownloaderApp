
package com.example.bilidownloader.data.model

data class CandidateVideo(
    val info: RecommendItem,
    val subtitleData: ConclusionData?, // [修改] 改为可空，初始为 null
    val cid: Long
)