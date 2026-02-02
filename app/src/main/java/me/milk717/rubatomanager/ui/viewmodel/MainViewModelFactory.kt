package me.milk717.rubatomanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModelFactory(
    private val geminiApiKey: String,
    private val githubToken: String,
    private val githubOwner: String,
    private val githubRepo: String,
    private val githubFilePath: String = "00_obsidian-meta/rubato-manager.md",
    private val githubBranch: String = "main"
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                geminiApiKey = geminiApiKey,
                githubToken = githubToken,
                githubOwner = githubOwner,
                githubRepo = githubRepo,
                githubFilePath = githubFilePath,
                githubBranch = githubBranch
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
