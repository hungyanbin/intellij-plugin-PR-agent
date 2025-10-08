package com.github.hungyanbin.intellijpluginpragent.toolWindow

import com.github.hungyanbin.intellijpluginpragent.repository.SecretRepository
import com.github.hungyanbin.intellijpluginpragent.service.AnthropicAPIService
import com.github.hungyanbin.intellijpluginpragent.service.BranchHistory
import com.github.hungyanbin.intellijpluginpragent.service.CreatePRRequest
import com.github.hungyanbin.intellijpluginpragent.service.GitCommandService
import com.github.hungyanbin.intellijpluginpragent.service.GitHubAPIService
import com.github.hungyanbin.intellijpluginpragent.service.GitHubRepository
import com.github.hungyanbin.intellijpluginpragent.utils.runOnUI
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class PRNotesPanel(private val project: Project) : JBPanel<JBPanel<*>>() {

    private val prNotesArea = JBTextArea()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val anthropicAPIService = AnthropicAPIService()
    private val gitCommandService = GitCommandService(project.basePath!!)
    private val secretRepository = SecretRepository()
    private val githubAPIService = GitHubAPIService()
    private var currentBranchHistory: BranchHistory? = null
    private var createPRButton: JButton
    private val prNotesText = MutableStateFlow("")
    private val isCreatePRButtonEnabled = MutableStateFlow(false)

    init {
        layout = BorderLayout()

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            val generateButton = JButton("Generate PR Notes").apply {
                addActionListener {
                    coroutineScope.launch(Dispatchers.IO) {
                        generatePRNotes()
                    }
                }
            }

            createPRButton = JButton("Create PR").apply {
                addActionListener {
                    coroutineScope.launch(Dispatchers.IO) {
                        createPullRequest()
                    }
                }
                isEnabled = false // Initially disabled until notes are generated
            }

            add(generateButton)
            add(createPRButton)
        }

        prNotesArea.apply {
            isEditable = false
            text = "Click 'Generate PR Notes' to create pull request notes using AI..."
        }

        val prScrollPane = JBScrollPane(prNotesArea).apply {
            preferredSize = Dimension(400, 300)
        }

        add(buttonPanel, BorderLayout.NORTH)
        add(prScrollPane, BorderLayout.CENTER)

        coroutineScope.launch {
            prNotesText.collect {
                runOnUI {
                    prNotesArea.text = it
                }
            }
        }

        coroutineScope.launch {
            isCreatePRButtonEnabled.collect {
                runOnUI {
                    createPRButton.isEnabled = it
                }
            }
        }

    }

    private suspend fun generatePRNotes() {
        val apiKey = secretRepository.getAnthropicApiKey() ?: ""

        if (apiKey.isEmpty()) {
            prNotesText.value = "Error: Please enter and apply your Anthropic API key in the Config tab"
            return
        }

        prNotesText.value = "Generating PR notes..."

        coroutineScope.launch {
            try {
                val branchHistory = gitCommandService.getBranchHistory()
                val fileDiff = gitCommandService.getFileDiff(
                    branchHistory.parentBranch.hash,
                    branchHistory.currentBranch.hash
                )

                val prPrompt = buildPRPrompt(branchHistory, fileDiff)
                val response = anthropicAPIService.callAnthropicAPI(apiKey, prPrompt)

                prNotesText.value = response
                isCreatePRButtonEnabled.value = true
                currentBranchHistory = branchHistory
            } catch (e: Exception) {
                prNotesText.value = "Error generating PR notes: ${e.message}"
            }
        }
    }

    private suspend fun createPullRequest() {
        val branchHistory = currentBranchHistory
        val githubPat = secretRepository.getGithubPat()
        val prNotes = prNotesArea.text

        if (branchHistory == null) {
            prNotesText.value = "Error: Please generate PR notes first"
            return
        }

        if (githubPat.isNullOrEmpty()) {
            prNotesText.value = "Error: Please enter and apply your GitHub PAT in the Config tab"
            return
        }

        if (prNotes.isEmpty() || prNotes.contains("Error:") || prNotes.contains("Click 'Generate")) {
            prNotesText.value = "Error: Please generate valid PR notes first"
            return
        }

        isCreatePRButtonEnabled.value = false
        prNotesText.value = "Creating pull request on GitHub..."

        coroutineScope.launch {
            try {
                // Check and push parent branch if it doesn't exist on remote
                prNotesText.value = "Checking branches on remote..."

                if (tryPushRemoteBranch(branchHistory)) return@launch

                // Push the current branch to remote
                prNotesText.value = "Pushing current branch ${branchHistory.currentBranch.name} to remote..."

                if (tryPushCurrentBranch(branchHistory)) return@launch

                prNotesText.value = "Creating pull request on GitHub..."

                val repository = getGithubRepository() ?: return@launch

                // Extract title from PR notes (first line or first heading)
                val title = extractTitleFromPRNotes(prNotes, branchHistory.currentBranch.name)

                val prRequest = CreatePRRequest(
                    title = title,
                    head = branchHistory.currentBranch.name,
                    base = branchHistory.parentBranch.name,
                    body = prNotes
                )

                githubAPIService.createPullRequest(githubPat, repository, prRequest)

                prNotesText.value = "✅ Pull request created successfully!\n\n$prNotes"
                isCreatePRButtonEnabled.value = true
            } catch (e: Exception) {
                prNotesText.value = "Error creating pull request: ${e.message}\n\n$prNotes"
                isCreatePRButtonEnabled.value = true
            }
        }
    }

    private suspend fun getGithubRepository(): GitHubRepository? {
        val remoteUrl = gitCommandService.getRemoteUrl()
        if (remoteUrl == null) {
            prNotesText.value = "Error: Could not determine GitHub repository from git remote"
            isCreatePRButtonEnabled.value = true
            return null
        }

        val repository = githubAPIService.parseGitHubRepository(remoteUrl)
        if (repository == null) {
            prNotesText.value = "Error: Could not parse GitHub repository from remote URL: $remoteUrl"
            isCreatePRButtonEnabled.value = true
            return null
        }

        return repository
    }

    private suspend fun tryPushCurrentBranch(branchHistory: BranchHistory): Boolean {
        val pushSuccess = gitCommandService.pushCurrentBranchToRemote(branchHistory.currentBranch.name)
        if (!pushSuccess) {
            prNotesText.value =
                    "Error: Failed to push current branch to remote. Please check your git credentials and try again."
            isCreatePRButtonEnabled.value = true
            return true
        }
        return false
    }

    private suspend fun tryPushRemoteBranch(branchHistory: BranchHistory): Boolean {
        val parentBranchExists = gitCommandService.checkBranchExistsOnRemote(branchHistory.parentBranch.name)
        if (!parentBranchExists) {
            prNotesText.value = "Pushing parent branch ${branchHistory.parentBranch.name} to remote..."

            val parentPushSuccess = gitCommandService.pushBranchToRemote(branchHistory.parentBranch.name)
            if (!parentPushSuccess) {
                prNotesText.value =
                    "Error: Failed to push parent branch ${branchHistory.parentBranch.name} to remote. Please check your git credentials and try again."
                isCreatePRButtonEnabled.value = true
                return true
            }
        }
        return false
    }

    private fun extractTitleFromPRNotes(prNotes: String, fallbackBranch: String): String {
        val lines = prNotes.split("\n")

        // Look for the first heading (# or ##)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim()
            }
            if (trimmed.startsWith("## ")) {
                return trimmed.substring(3).trim()
            }
        }

        // Look for the first non-empty line
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("```")) {
                return trimmed.take(100) // Limit title length
            }
        }

        // Fallback to branch name
        return fallbackBranch.replace("_", " ").replace("-", " ")
    }

    private fun buildPRPrompt(branchHistory: BranchHistory, fileDiff: String): String {
        return buildString {
            appendLine("Please generate comprehensive pull request notes based on the following git information:")
            appendLine()
            appendLine("## Branch Information")
            appendLine("- Current Branch: ${branchHistory.currentBranch.name}")
            appendLine("- Parent Branch: ${branchHistory.parentBranch.name}")
            appendLine()

            if (branchHistory.commits.isNotEmpty()) {
                appendLine("## Commits")
                branchHistory.commits.forEach { commit ->
                    appendLine("- ${commit.hash}: ${commit.description}")
                }
                appendLine()
            }

            appendLine("## Code Changes")
            appendLine("```")
            appendLine(fileDiff)
            appendLine("```")
            appendLine()
            appendLine("Please create a concise pull request description that reviewers can quickly scan. Include:")
            appendLine()
            appendLine("1. **Summary** (2-3 sentences): What changed and why")
            appendLine("2. **Key Changes** (bullet points): The main modifications made")
            appendLine("3. **Breaking Changes** (if any): What needs attention or migration")
            appendLine()
            appendLine("Keep it brief - reviewers should understand the PR in under 2 minutes. Avoid:")
            appendLine("- Detailed implementation explanations")
            appendLine("- Testing checklists")
            appendLine("- Code snippets (unless critical for understanding breaking changes)")
            appendLine("- Extensive architectural discussions")
            appendLine()
            appendLine("Focus on WHAT changed and WHY, not HOW it was implemented.")
            appendLine()
            appendLine("Format the response in markdown.")
        }
    }

    fun cleanup() {
        coroutineScope.cancel()
    }
}