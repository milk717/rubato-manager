package me.milk717.rubatomanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MemoData(
    val type: String,
    val content: String
) {
    companion object {
        const val TYPE_DAILY_NOTE = "dailynote"
        const val TYPE_PLAIN = "plain"
    }
}
