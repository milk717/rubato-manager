package me.milk717.rubatomanager.data.model

import com.google.gson.annotations.SerializedName

data class GithubFileResponse(
    val sha: String,
    val content: String,
    val encoding: String
)

data class GithubUpdateRequest(
    val message: String,
    val content: String,
    val sha: String,
    val branch: String = "main"
)

data class GithubUpdateResponse(
    val content: GithubContentInfo?,
    val commit: GithubCommitInfo
)

data class GithubContentInfo(
    val sha: String,
    val path: String
)

data class GithubCommitInfo(
    val sha: String,
    val message: String,
    @SerializedName("html_url")
    val htmlUrl: String
)
