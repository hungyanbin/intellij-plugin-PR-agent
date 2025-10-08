package com.github.hungyanbin.intellijpluginpragent.toolWindow

import com.github.hungyanbin.intellijpluginpragent.repository.SecretRepository
import com.github.hungyanbin.intellijpluginpragent.service.AgentService
import com.github.hungyanbin.intellijpluginpragent.service.BranchHistory
import com.github.hungyanbin.intellijpluginpragent.service.CreatePRRequest
import com.github.hungyanbin.intellijpluginpragent.service.GitCommandService
import com.github.hungyanbin.intellijpluginpragent.service.GitHubAPIService
import com.github.hungyanbin.intellijpluginpragent.service.GitHubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PRNotesPanelViewModel(private val projectBasePath: String) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val agentService = AgentService()
    private val gitCommandService = GitCommandService(projectBasePath)
    private val secretRepository = SecretRepository()
    private val githubAPIService = GitHubAPIService()
    private var currentBranchHistory: BranchHistory? = null

    private val _prNotesText = MutableStateFlow("Click 'Generate PR Notes' to create pull request notes using AI...")
    val prNotesText: StateFlow<String> = _prNotesText.asStateFlow()

    private val _isCreatePRButtonEnabled = MutableStateFlow(false)
    val isCreatePRButtonEnabled: StateFlow<Boolean> = _isCreatePRButtonEnabled.asStateFlow()

    private val _recentBranches = MutableStateFlow<List<String>>(emptyList())
    val recentBranches: StateFlow<List<String>> = _recentBranches.asStateFlow()

    private val _selectedBaseBranch = MutableStateFlow<String?>(null)
    val selectedBaseBranch: StateFlow<String?> = _selectedBaseBranch.asStateFlow()

    private val _selectedCompareBranch = MutableStateFlow<String?>(null)
    val selectedCompareBranch: StateFlow<String?> = _selectedCompareBranch.asStateFlow()

    fun onBaseBranchSelected(branchName: String?) {
        _selectedBaseBranch.value = branchName
    }

    fun onCompareBranchSelected(branchName: String?) {
        _selectedCompareBranch.value = branchName
    }

    init {
        loadRecentBranches()
        loadDefaultBranches()
    }

    private fun loadRecentBranches() {
        coroutineScope.launch {
            try {
                val branches = gitCommandService.getRecentBranches(10)
                _recentBranches.value = branches
            } catch (e: Exception) {
                _recentBranches.value = emptyList()
            }
        }
    }

    private fun loadDefaultBranches() {
        coroutineScope.launch {
            try {
                val currentBranch = gitCommandService.getCurrentBranch()
                val parentBranch = gitCommandService.getParentBranch(currentBranch)

                _selectedBaseBranch.value = parentBranch.name
                _selectedCompareBranch.value = currentBranch.name
            } catch (e: Exception) {
                // Ignore errors, branches will remain unselected
            }
        }
    }

    fun onGeneratePRNotesClicked() {
        coroutineScope.launch {
            generatePRNotes()
        }
    }

    fun onCreatePRClicked(currentPRNotes: String) {
        coroutineScope.launch {
            createPullRequest(currentPRNotes)
        }
    }

    private suspend fun generatePRNotes() {
        val apiKey = secretRepository.getAnthropicApiKey() ?: ""

        if (apiKey.isEmpty()) {
            _prNotesText.value = "Error: Please enter and apply your Anthropic API key in the Config tab"
            return
        }

        val baseBranchName = _selectedBaseBranch.value
        val compareBranchName = _selectedCompareBranch.value

        if (baseBranchName == null || compareBranchName == null) {
            _prNotesText.value = "Error: Please select both base and compare branches"
            return
        }

        if (baseBranchName == compareBranchName) {
            _prNotesText.value = "Error: Base branch and compare branch cannot be the same"
            return
        }

        _prNotesText.value = "Generating PR notes..."

        try {
            // Get branch info for selected branches
            val baseBranch = gitCommandService.getBranchByName(baseBranchName)
            val compareBranch = gitCommandService.getBranchByName(compareBranchName)

            // Validate that compare branch is ahead of base branch
            if (!gitCommandService.isBranchAheadOf(compareBranchName, baseBranchName)) {
                _prNotesText.value = "Error: Compare branch '$compareBranchName' is not ahead of base branch '$baseBranchName'. Please check your branch selection."
                return
            }

            // Get commits between branches
            val commits = gitCommandService.getCommitsSinceParent(compareBranch, baseBranch)

            val branchHistory = BranchHistory(
                commits = commits,
                currentBranch = compareBranch,
                parentBranch = baseBranch
            )

            val fileDiff = gitCommandService.getFileDiff(
                baseBranch.hash,
                compareBranch.hash
            )

            val prPrompt = buildPRPrompt(branchHistory, fileDiff)
            val response = agentService.generatePRNotes(apiKey, prPrompt)

            _prNotesText.value = response
            _isCreatePRButtonEnabled.value = true
            currentBranchHistory = branchHistory
        } catch (e: Exception) {
            _prNotesText.value = "Error generating PR notes: ${e.message}"
        }
    }

    private suspend fun createPullRequest(prNotes: String) {
        val branchHistory = currentBranchHistory
        val githubPat = secretRepository.getGithubPat()

        if (branchHistory == null) {
            _prNotesText.value = "Error: Please generate PR notes first"
            return
        }

        if (githubPat.isNullOrEmpty()) {
            _prNotesText.value = "Error: Please enter and apply your GitHub PAT in the Config tab"
            return
        }

        if (prNotes.isEmpty() || prNotes.contains("Error:") || prNotes.contains("Click 'Generate")) {
            _prNotesText.value = "Error: Please generate valid PR notes first"
            return
        }

        _isCreatePRButtonEnabled.value = false
        _prNotesText.value = "Creating pull request on GitHub..."

        try {
            // Check and push parent branch if it doesn't exist on remote
            _prNotesText.value = "Checking branches on remote..."

            if (tryPushRemoteBranch(branchHistory)) return

            // Push the current branch to remote
            _prNotesText.value = "Pushing current branch ${branchHistory.currentBranch.name} to remote..."

            if (tryPushCurrentBranch(branchHistory)) return

            _prNotesText.value = "Creating pull request on GitHub..."

            val repository = getGithubRepository() ?: return

            // Extract title from PR notes (first line or first heading)
            val title = extractTitleFromPRNotes(prNotes, branchHistory.currentBranch.name)

            val prRequest = CreatePRRequest(
                title = title,
                head = branchHistory.currentBranch.name,
                base = branchHistory.parentBranch.name,
                body = prNotes
            )

            githubAPIService.createPullRequest(githubPat, repository, prRequest)

            _prNotesText.value = "✅ Pull request created successfully!\n\n$prNotes"
            _isCreatePRButtonEnabled.value = true
        } catch (e: Exception) {
            _prNotesText.value = "Error creating pull request: ${e.message}\n\n$prNotes"
            _isCreatePRButtonEnabled.value = true
        }
    }

    private suspend fun getGithubRepository(): GitHubRepository? {
        val remoteUrl = gitCommandService.getRemoteUrl()
        if (remoteUrl == null) {
            _prNotesText.value = "Error: Could not determine GitHub repository from git remote"
            _isCreatePRButtonEnabled.value = true
            return null
        }

        val repository = githubAPIService.parseGitHubRepository(remoteUrl)
        if (repository == null) {
            _prNotesText.value = "Error: Could not parse GitHub repository from remote URL: $remoteUrl"
            _isCreatePRButtonEnabled.value = true
            return null
        }

        return repository
    }

    private suspend fun tryPushCurrentBranch(branchHistory: BranchHistory): Boolean {
        val pushSuccess = gitCommandService.pushCurrentBranchToRemote(branchHistory.currentBranch.name)
        if (!pushSuccess) {
            _prNotesText.value =
                "Error: Failed to push current branch to remote. Please check your git credentials and try again."
            _isCreatePRButtonEnabled.value = true
            return true
        }
        return false
    }

    private suspend fun tryPushRemoteBranch(branchHistory: BranchHistory): Boolean {
        val parentBranchExists = gitCommandService.checkBranchExistsOnRemote(branchHistory.parentBranch.name)
        if (!parentBranchExists) {
            _prNotesText.value = "Pushing parent branch ${branchHistory.parentBranch.name} to remote..."

            val parentPushSuccess = gitCommandService.pushBranchToRemote(branchHistory.parentBranch.name)
            if (!parentPushSuccess) {
                _prNotesText.value =
                    "Error: Failed to push parent branch ${branchHistory.parentBranch.name} to remote. Please check your git credentials and try again."
                _isCreatePRButtonEnabled.value = true
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