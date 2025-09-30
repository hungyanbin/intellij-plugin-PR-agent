package com.github.hungyanbin.intellijpluginpragent.toolWindow

import com.github.hungyanbin.intellijpluginpragent.repository.SecretRepository
import com.github.hungyanbin.intellijpluginpragent.service.CreatePRRequest
import com.github.hungyanbin.intellijpluginpragent.service.GitHubAPIService
import com.github.hungyanbin.intellijpluginpragent.service.GitHubRepository
import com.github.hungyanbin.intellijpluginpragent.utils.runOnUI
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class PRNotesPanel(private val project: Project) : JBPanel<JBPanel<*>>() {

    private val prNotesArea = JBTextArea()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val anthropicAPIService = AnthropicAPIService()
    private val gitCommandHelper = GitCommandHelper(project.basePath!!)
    private val secretRepository = SecretRepository()
    private val githubAPIService = GitHubAPIService()
    private var currentBranchHistory: BranchHistory? = null
    private var createPRButton: JButton

    init {
        layout = BorderLayout()

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            val generateButton = JButton("Generate PR Notes").apply {
                addActionListener {
                    coroutineScope.launch {
                        generatePRNotes()
                    }
                }
            }

            createPRButton = JButton("Create PR").apply {
                addActionListener {
                    coroutineScope.launch {
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
    }

    private suspend fun generatePRNotes() {
        val apiKey = secretRepository.getAnthropicApiKey() ?: ""

        if (apiKey.isEmpty()) {
            prNotesArea.text = "Error: Please enter and apply your Anthropic API key in the Config tab"
            return
        }

        prNotesArea.text = "Generating PR notes..."

        coroutineScope.launch {
            try {
                val branchHistory = gitCommandHelper.getBranchHistory()
                val fileDiff = gitCommandHelper.getFileDiff(
                    branchHistory.parentBranch.hash,
                    branchHistory.currentBranch.hash
                )

                val prPrompt = buildPRPrompt(branchHistory, fileDiff)
                val response = anthropicAPIService.callAnthropicAPI(apiKey, prPrompt)

                runOnUI {
                    currentBranchHistory = branchHistory
                    prNotesArea.text = response
                    createPRButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUI {
                    prNotesArea.text = "Error generating PR notes: ${e.message}"
                }
            }
        }
    }

    private suspend fun createPullRequest() {
        val branchHistory = currentBranchHistory
        val githubPat = secretRepository.getGithubPat()
        val prNotes = prNotesArea.text

        if (branchHistory == null) {
            prNotesArea.text = "Error: Please generate PR notes first"
            return
        }

        if (githubPat.isNullOrEmpty()) {
            prNotesArea.text = "Error: Please enter and apply your GitHub PAT in the Config tab"
            return
        }

        if (prNotes.isEmpty() || prNotes.contains("Error:") || prNotes.contains("Click 'Generate")) {
            prNotesArea.text = "Error: Please generate valid PR notes first"
            return
        }

        createPRButton.isEnabled = false
        prNotesArea.text = "Creating pull request on GitHub..."

        coroutineScope.launch {
            try {
                // Check and push parent branch if it doesn't exist on remote
                runOnUI {
                    prNotesArea.text = "Checking branches on remote..."
                }

                if (tryPushRemoteBranch(branchHistory)) return@launch

                // Push the current branch to remote
                runOnUI {
                    prNotesArea.text = "Pushing current branch ${branchHistory.currentBranch.name} to remote..."
                }

                if (tryPushCurrentBranch(branchHistory)) return@launch

                runOnUI {
                    prNotesArea.text = "Creating pull request on GitHub..."
                }

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

                runOnUI {
                    prNotesArea.text = "✅ Pull request created successfully!\n\n$prNotes"
                    createPRButton.isEnabled = true
                }

            } catch (e: Exception) {
                runOnUI {
                    prNotesArea.text = "Error creating pull request: ${e.message}\n\n$prNotes"
                    createPRButton.isEnabled = true
                }
            }
        }
    }

    private suspend fun getGithubRepository(): GitHubRepository? {
        val remoteUrl = gitCommandHelper.getRemoteUrl()
        if (remoteUrl == null) {
            runOnUI {
                prNotesArea.text = "Error: Could not determine GitHub repository from git remote"
                createPRButton.isEnabled = true
            }
            return null
        }

        val repository = githubAPIService.parseGitHubRepository(remoteUrl)
        if (repository == null) {
            runOnUI {
                prNotesArea.text = "Error: Could not parse GitHub repository from remote URL: $remoteUrl"
                createPRButton.isEnabled = true
            }
            return null
        }

        return repository
    }

    private suspend fun tryPushCurrentBranch(branchHistory: BranchHistory): Boolean {
        val pushSuccess = gitCommandHelper.pushCurrentBranchToRemote(branchHistory.currentBranch.name)
        if (!pushSuccess) {
            runOnUI {
                prNotesArea.text =
                    "Error: Failed to push current branch to remote. Please check your git credentials and try again."
                createPRButton.isEnabled = true
            }
            return true
        }
        return false
    }

    private suspend fun tryPushRemoteBranch(branchHistory: BranchHistory): Boolean {
        val parentBranchExists = gitCommandHelper.checkBranchExistsOnRemote(branchHistory.parentBranch.name)
        if (!parentBranchExists) {
            runOnUI {
                prNotesArea.text = "Pushing parent branch ${branchHistory.parentBranch.name} to remote..."
            }

            val parentPushSuccess = gitCommandHelper.pushBranchToRemote(branchHistory.parentBranch.name)
            if (!parentPushSuccess) {
                runOnUI {
                    prNotesArea.text =
                        "Error: Failed to push parent branch ${branchHistory.parentBranch.name} to remote. Please check your git credentials and try again."
                    createPRButton.isEnabled = true
                }
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
            appendLine("Please create a well-structured pull request description that includes:")
            appendLine("1. A clear summary of what was changed")
            appendLine("2. The motivation or reasoning behind the changes")
            appendLine("3. Any important implementation details")
            appendLine("4. Testing considerations (if applicable)")
            appendLine("5. Any breaking changes or migration notes (if applicable)")
            appendLine()
            appendLine("Format the response in markdown.")
        }
    }

    fun cleanup() {
        coroutineScope.cancel()
    }
}