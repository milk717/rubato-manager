package me.milk717.rubatomanager.data.repository

import android.util.Base64
import me.milk717.rubatomanager.data.api.GithubClient
import me.milk717.rubatomanager.data.model.GithubUpdateRequest
import me.milk717.rubatomanager.data.model.MemoData
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GithubRepository(
    private val owner: String,
    private val repo: String,
    private val filePath: String,
    private val token: String,
    private val branch: String = "main"
) {
    private val api = GithubClient.api
    private val authHeader = "Bearer $token"

    suspend fun getFileContent(): Result<String> {
        return getFileContentByPath(filePath)
    }

    suspend fun getFileContentByPath(path: String): Result<String> {
        return try {
            val fileResponse = api.getFileContent(
                token = authHeader,
                owner = owner,
                repo = repo,
                path = path
            )
            val content = decodeBase64(fileResponse.content)
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun appendMemo(memoData: MemoData): Result<String> {
        return try {
            // 1. Get current file content and SHA
            val fileResponse = api.getFileContent(
                token = authHeader,
                owner = owner,
                repo = repo,
                path = filePath
            )

            // 2. Decode existing content
            val existingContent = decodeBase64(fileResponse.content)

            // 3. Create new memo entry
            val newEntry = formatMemoEntry(memoData)

            // 4. Append new content
            val updatedContent = existingContent + newEntry

            // 5. Encode and update
            val encodedContent = encodeBase64(updatedContent)

            val updateRequest = GithubUpdateRequest(
                message = "Add memo: ${memoData.type} - ${getCurrentTimestamp()}",
                content = encodedContent,
                sha = fileResponse.sha,
                branch = branch
            )

            val response = api.updateFile(
                token = authHeader,
                owner = owner,
                repo = repo,
                path = filePath,
                request = updateRequest
            )

            Result.success(response.commit.htmlUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatMemoEntry(memoData: MemoData): String {
        val timestamp = getCurrentTimestamp()
        return """

###### $timestamp

```json
{
    "type": "${memoData.type}",
    "content": "${memoData.content.replace("\"", "\\\"")}"
}
```
"""
    }

    private fun getCurrentTimestamp(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return LocalDateTime.now().format(formatter)
    }

    private fun decodeBase64(encoded: String): String {
        val cleanedContent = encoded.replace("\n", "").replace("\r", "")
        val decodedBytes = Base64.decode(cleanedContent, Base64.DEFAULT)
        return String(decodedBytes, Charsets.UTF_8)
    }

    private fun encodeBase64(content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
