package com.github.hungyanbin.pragent.toolWindow

import com.github.hungyanbin.pragent.repository.SecretRepository
import com.github.hungyanbin.pragent.service.AgentService
import com.github.hungyanbin.pragent.service.BranchHistory
import com.github.hungyanbin.pragent.service.CreatePRRequest
import com.github.hungyanbin.pragent.service.GitCommandService
import com.github.hungyanbin.pragent.service.GitHubAPIService
import com.github.hungyanbin.pragent.service.GitHubRepository
import com.github.hungyanbin.pragent.service.GitStatusWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PRNotesPanelViewModel(projectBasePath: String) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val agentService = AgentService()
    private val gitCommandService = GitCommandService(projectBasePath)
    private val secretRepository = SecretRepository()
    private val githubAPIService = GitHubAPIService()
    private val promptTemplateRepository = PromptTemplateRepository()
    private val gitStatusWatcher = GitStatusWatcher(projectBasePath)
    private var currentBranchHistory: BranchHistory? = null

    private val _prNotesText = MutableStateFlow("")
    val prNotesText: StateFlow<String> = _prNotesText.asStateFlow()

    private val _isCreatePRButtonEnabled = MutableStateFlow(false)
    val isCreatePRButtonEnabled: StateFlow<Boolean> = _isCreatePRButtonEnabled.asStateFlow()

    private val _createPRButtonText = MutableStateFlow("Create PR")
    val createPRButtonText = _createPRButtonText.asStateFlow()

    private val _isGeneratePRButtonEnabled = MutableStateFlow(true)
    val isGeneratePRButtonEnabled: StateFlow<Boolean> = _isGeneratePRButtonEnabled.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val recentBranches = MutableStateFlow<List<String>>(emptyList())

    val compareBranches: Flow<List<String>> = recentBranches.map { branches ->
        if (branches.isEmpty()) return@map emptyList()

        val currentBranch = gitCommandService.getCurrentBranch()

        buildList {
            add(currentBranch.name)
            branches.forEach { branch ->
                if (branch != currentBranch.name) {
                    add(branch)
                }
            }
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    val baseBranches: StateFlow<List<String>> = recentBranches.map { branches ->
        if (branches.isEmpty()) return@map emptyList()

        val currentBranch = gitCommandService.getCurrentBranch()
        val parentBranch = gitCommandService.getParentBranch(currentBranch)

        buildList {
            val defaultBranchName = getGithubDefaultBranchName()
            // 1. Add parent branch first
            add(parentBranch.name)
            // 2. Add default branch second (if different from parent)
            if (defaultBranchName != null && defaultBranchName != parentBranch.name) {
                add(defaultBranchName)
            }
            // 3. Add recent branches (excluding parent and default branch)
            branches.forEach { branch ->
                if (branch != parentBranch.name && branch != defaultBranchName) {
                    add(branch)
                }
            }
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    private val _selectedBaseBranch = MutableStateFlow<String?>(null)
    val selectedBaseBranch: StateFlow<String?> = _selectedBaseBranch.asStateFlow()

    private val _selectedCompareBranch = MutableStateFlow<String?>(null)
    val selectedCompareBranch: StateFlow<String?> = _selectedCompareBranch.asStateFlow()

    private val _basePromptText = MutableStateFlow("")
    val basePromptText: StateFlow<String> = _basePromptText.asStateFlow()

    private var existingPRText = ""
    private var existingPRNumber: Int? = null
    private var existingPRTitle: String? = null

    init {
        loadRecentBranches()
        loadBasePrompt()
        setupGitStatusWatcher()
    }

    private fun setupGitStatusWatcher() {
        // Start watching for git changes
        gitStatusWatcher.startWatching(coroutineScope)

        // Subscribe to git change events
        coroutineScope.launch {
            gitStatusWatcher.gitChangeEvents.collect {
                // Reload branches when git status changes
                loadRecentBranches()

                // Check for existing PR again in case branch changed
                checkForExistingPR()
            }
        }
    }

    fun onBaseBranchSelected(branchName: String?) {
        _selectedBaseBranch.value = branchName
        checkForExistingPR()
    }

    fun onCompareBranchSelected(branchName: String?) {
        _selectedCompareBranch.value = branchName
        checkForExistingPR()
    }

    fun onGeneratePRNotesClicked(
        includeClassDiagram: Boolean,
        includeSequenceDiagram: Boolean
    ) {
        coroutineScope.launch {
            generatePRNotes(includeClassDiagram, includeSequenceDiagram)
        }
    }

    fun onCreatePRClicked(currentPRNotes: String) {
        coroutineScope.launch {
            if (existingPRText.isNotEmpty()) {
                updatePullRequest(currentPRNotes)
            } else {
                createPullRequest(currentPRNotes)
            }
        }
    }

    fun onModifyPRNotesClicked(
        userPrompt: String,
        includeClassDiagram: Boolean,
        includeSequenceDiagram: Boolean
    ) {
        coroutineScope.launch {
            modifyPRNotes(userPrompt, includeClassDiagram, includeSequenceDiagram)
        }
    }

    fun onUpdateBasePromptClicked(newBasePrompt: String) {
        if (newBasePrompt.isBlank()) {
            _statusMessage.value = "Error: Base prompt cannot be empty"
            return
        }

        promptTemplateRepository.updateBasePrompt(newBasePrompt)
        _basePromptText.value = newBasePrompt
        _statusMessage.value = "Base prompt updated successfully"
    }

    fun cleanup() {
        gitStatusWatcher.stopWatching()
        coroutineScope.cancel()
        githubAPIService.close()
    }

    private suspend fun updatePullRequest(currentPRNotes: String) {
        val prNumber = existingPRNumber ?: return
        val title = existingPRTitle ?: return
        val githubPat = secretRepository.getGithubPat()

        if (githubPat.isNullOrEmpty()) {
            _statusMessage.value = "Error: Please enter and apply your GitHub PAT in the Config tab"
            return
        }

        if (currentPRNotes.isEmpty() || currentPRNotes.contains("Error:") || currentPRNotes.contains("Click 'Generate")) {
            _statusMessage.value = "Error: Please generate valid PR notes first"
            return
        }

        _isCreatePRButtonEnabled.value = false
        _statusMessage.value = "Updating pull request on GitHub..."

        try {
            val repository = getGithubRepository() ?: return

            githubAPIService.updatePullRequest(githubPat, repository, prNumber, title, currentPRNotes)

            _statusMessage.value = "✅ Pull request updated successfully!"
            _isCreatePRButtonEnabled.value = true
        } catch (e: Exception) {
            _statusMessage.value = "Error updating pull request: ${e.message}"
            _isCreatePRButtonEnabled.value = true
        }
    }

    private suspend fun generatePRNotes(
        includeClassDiagram: Boolean,
        includeSequenceDiagram: Boolean
    ) {
        val apiKey = secretRepository.getAnthropicApiKey() ?: ""

        if (apiKey.isEmpty()) {
            _statusMessage.value = "Error: Please enter and apply your Anthropic API key in the Config tab"
            return
        }

        val baseBranchName = _selectedBaseBranch.value
        val compareBranchName = _selectedCompareBranch.value

        if (baseBranchName == null || compareBranchName == null) {
            _statusMessage.value = "Error: Please select both base and compare branches"
            return
        }

        if (baseBranchName == compareBranchName) {
            _statusMessage.value = "Error: Base branch and compare branch cannot be the same"
            return
        }

        _statusMessage.value = "Generating PR notes..."

        try {
            // Fetch latest changes from remote
            _statusMessage.value = "Fetching latest changes from remote..."
            if (!gitCommandService.fetchRemote()) {
                _statusMessage.value = "Warning: Failed to fetch from remote. Continuing with local state..."
            }

            // Check if base branch is behind remote and pull if necessary
            if (gitCommandService.isLocalBranchBehindRemote(baseBranchName)) {
                _statusMessage.value = "Base branch '$baseBranchName' is behind remote. Pulling latest changes..."
                if (!gitCommandService.pullBranch(baseBranchName)) {
                    _statusMessage.value = "Error: Failed to pull base branch '$baseBranchName'. The branch may have diverged from remote. Please update it manually with 'git pull origin $baseBranchName' and try again."
                    return
                }
            }

            // Get branch info for selected branches
            val baseBranch = gitCommandService.getBranchByName(baseBranchName)
            val compareBranch = gitCommandService.getBranchByName(compareBranchName)

            // Validate that compare branch is ahead of base branch
            if (!gitCommandService.isBranchAheadOf(compareBranchName, baseBranchName)) {
                _statusMessage.value = "Error: Compare branch '$compareBranchName' is not ahead of base branch '$baseBranchName'. Please check your branch selection."
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

            val prPrompt = buildPRPrompt(branchHistory, fileDiff, includeClassDiagram, includeSequenceDiagram)
            val response = agentService.generatePRNotes(apiKey, prPrompt)

            _prNotesText.value = response
            _isCreatePRButtonEnabled.value = true
            _statusMessage.value = "PR notes generated successfully"
            currentBranchHistory = branchHistory
        } catch (e: Exception) {
            _statusMessage.value = "Error generating PR notes: ${e.message}"
        }
    }

    private suspend fun createPullRequest(prNotes: String) {
        val branchHistory = currentBranchHistory
        val githubPat = secretRepository.getGithubPat()

        if (branchHistory == null) {
            _statusMessage.value = "Error: Please generate PR notes first"
            return
        }

        if (githubPat.isNullOrEmpty()) {
            _statusMessage.value = "Error: Please enter and apply your GitHub PAT in the Config tab"
            return
        }

        if (prNotes.isEmpty() || prNotes.contains("Error:") || prNotes.contains("Click 'Generate")) {
            _statusMessage.value = "Error: Please generate valid PR notes first"
            return
        }

        _isCreatePRButtonEnabled.value = false
        _statusMessage.value = "Creating pull request on GitHub..."

        try{
            // Check and push parent branch if it doesn't exist on remote
            _statusMessage.value = "Checking branches on remote..."

            if (tryPushRemoteBranch(branchHistory)) return

            // Push the current branch to remote
            _statusMessage.value = "Pushing current branch ${branchHistory.currentBranch.name} to remote..."

            if (tryPushCurrentBranch(branchHistory)) return

            _statusMessage.value = "Creating pull request on GitHub..."

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

            _statusMessage.value = "✅ Pull request created successfully!"
            _isCreatePRButtonEnabled.value = true
        } catch (e: Exception) {
            _statusMessage.value = "Error creating pull request: ${e.message}"
            _isCreatePRButtonEnabled.value = true
        }
    }

    private suspend fun getGithubRepository(): GitHubRepository? {
        val remoteUrl = gitCommandService.getRemoteUrl()
        if (remoteUrl == null) {
            _statusMessage.value = "Error: Could not determine GitHub repository from git remote"
            _isCreatePRButtonEnabled.value = true
            return null
        }

        val repository = githubAPIService.parseGitHubRepository(remoteUrl)
        if (repository == null) {
            _statusMessage.value = "Error: Could not parse GitHub repository from remote URL: $remoteUrl"
            _isCreatePRButtonEnabled.value = true
            return null
        }

        return repository
    }

    private suspend fun tryPushCurrentBranch(branchHistory: BranchHistory): Boolean {
        val pushSuccess = gitCommandService.pushCurrentBranchToRemote(branchHistory.currentBranch.name)
        if (!pushSuccess) {
            _statusMessage.value =
                "Error: Failed to push current branch to remote. Please check your git credentials and try again."
            _isCreatePRButtonEnabled.value = true
            return true
        }
        return false
    }

    private suspend fun tryPushRemoteBranch(branchHistory: BranchHistory): Boolean {
        val parentBranchExists = gitCommandService.checkBranchExistsOnRemote(branchHistory.parentBranch.name)
        if (!parentBranchExists) {
            _statusMessage.value = "Pushing parent branch ${branchHistory.parentBranch.name} to remote..."

            val parentPushSuccess = gitCommandService.pushBranchToRemote(branchHistory.parentBranch.name)
            if (!parentPushSuccess) {
                _statusMessage.value =
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

    private fun buildPRPrompt(
        branchHistory: BranchHistory,
        fileDiff: String,
        includeClassDiagram: Boolean,
        includeSequenceDiagram: Boolean
    ): String {
        return buildString {
            append(promptTemplateRepository.buildBasePrompt(branchHistory, fileDiff))

            if (includeClassDiagram) {
                append(promptTemplateRepository.getClassDiagramPrompt())
            }

            if (includeSequenceDiagram) {
                append(promptTemplateRepository.getSequenceDiagramPrompt())
            }

            appendLine("Format the response in markdown.")
        }
    }

    private suspend fun modifyPRNotes(
        userPrompt: String,
        includeClassDiagram: Boolean,
        includeSequenceDiagram: Boolean
    ) {
        val apiKey = secretRepository.getAnthropicApiKey() ?: ""

        if (apiKey.isEmpty()) {
            _statusMessage.value = "Error: Please enter and apply your Anthropic API key in the Config tab"
            return
        }

        if (userPrompt.isBlank()) {
            _statusMessage.value = "Error: Please enter a prompt to modify PR notes"
            return
        }

        _statusMessage.value = "Modifying PR notes..."

        try {
            // Get or create BranchHistory from cache
            val branchHistory = currentBranchHistory ?: run {
                val baseBranchName = _selectedBaseBranch.value
                val compareBranchName = _selectedCompareBranch.value

                if (baseBranchName == null || compareBranchName == null) {
                    _statusMessage.value = "Error: Please select branches first"
                    return
                }

                val baseBranch = gitCommandService.getBranchByName(baseBranchName)
                val compareBranch = gitCommandService.getBranchByName(compareBranchName)
                val commits = gitCommandService.getCommitsSinceParent(compareBranch, baseBranch)

                BranchHistory(
                    commits = commits,
                    currentBranch = compareBranch,
                    parentBranch = baseBranch
                ).also {
                    currentBranchHistory = it
                }
            }

            val fileDiff = gitCommandService.getFileDiff(
                branchHistory.parentBranch.hash,
                branchHistory.currentBranch.hash
            )

            val basePRPrompt = buildPRPrompt(branchHistory, fileDiff, includeClassDiagram, includeSequenceDiagram)
            val currentPRContent = _prNotesText.value

            val combinedPrompt = """
                $basePRPrompt

                Current PR Notes:
                ```
                $currentPRContent
                ```

                User Request:
                $userPrompt

                Please modify the PR notes according to the user's request while maintaining the overall structure and clarity.
            """.trimIndent()

            val response = agentService.generatePRNotes(apiKey, combinedPrompt)

            _prNotesText.value = response
            _statusMessage.value = "PR notes modified successfully"
        } catch (e: Exception) {
            _statusMessage.value = "Error modifying PR notes: ${e.message}"
        }
    }

    private fun checkForExistingPR() {
        val baseBranch = _selectedBaseBranch.value
        val compareBranch = _selectedCompareBranch.value

        // Only check if both branches are selected and different
        if (baseBranch == null || compareBranch == null || baseBranch == compareBranch) {
            return
        }

        coroutineScope.launch {
            try {
                val githubPat = secretRepository.getGithubPat()
                if (githubPat.isNullOrEmpty()) {
                    // No GitHub PAT configured, skip check
                    return@launch
                }

                val repository = getGithubRepository() ?: return@launch

                val existingPR = githubAPIService.findExistingPullRequest(
                    githubPat,
                    repository,
                    compareBranch,
                    baseBranch
                )

                if (existingPR != null) {
                    val prText = buildString {
                        if (!existingPR.body.isNullOrEmpty()) {
                            appendLine(existingPR.body)
                        } else {
                            appendLine("(No description)")
                        }
                    }
                    _prNotesText.value = prText
                    existingPRText = prText
                    existingPRNumber = existingPR.number
                    existingPRTitle = existingPR.title

                    // If PR is closed, disable both buttons
                    // If PR is open, keep buttons enabled so user can modify and update
                    if (existingPR.state == "closed") {
                        _statusMessage.value = "PR is closed"
                        _isCreatePRButtonEnabled.value = false
                        _isGeneratePRButtonEnabled.value = false
                    } else {
                        // PR is open, enable buttons for modification
                        _isGeneratePRButtonEnabled.value = true
                        _isCreatePRButtonEnabled.value = true
                        _createPRButtonText.value = "Update PR"
                        _statusMessage.value = "Find existing PR, click Update button to update existing description"
                    }
                } else {
                    // No existing PR found, show default message and enable Generate button
                    _statusMessage.value = "Click 'Generate PR Notes' to create pull request notes using AI..."
                    _prNotesText.value = ""
                    _isCreatePRButtonEnabled.value = false
                    _isGeneratePRButtonEnabled.value = true
                    _createPRButtonText.value = "Create PR"
                    existingPRText = ""
                    existingPRNumber = null
                    existingPRTitle = null
                }
            } catch (e: Exception) {
                // Silently fail - just don't update the text
            }
        }
    }

    private suspend fun getGithubDefaultBranchName(): String? {
        return try {
            val githubPat = secretRepository.getGithubPat() ?: return null
            val repository = getGithubRepository() ?: return null

            githubAPIService.getDefaultBranch(githubPat, repository)
        } catch (e: Exception) {
            // Ignore errors, will fallback to branches list
            null
        }
    }

    private fun loadBasePrompt() {
        _basePromptText.value = promptTemplateRepository.getBasePrompt()
    }

    private fun loadRecentBranches() {
        coroutineScope.launch {
            recentBranches.value = gitCommandService.getRecentBranches(10)
        }
    }
}