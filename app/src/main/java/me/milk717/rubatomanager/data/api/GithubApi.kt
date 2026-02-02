package me.milk717.rubatomanager.data.api

import me.milk717.rubatomanager.data.model.GithubFileResponse
import me.milk717.rubatomanager.data.model.GithubUpdateRequest
import me.milk717.rubatomanager.data.model.GithubUpdateResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

interface GithubApi {

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String
    ): GithubFileResponse

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun updateFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body request: GithubUpdateRequest
    ): GithubUpdateResponse
}
