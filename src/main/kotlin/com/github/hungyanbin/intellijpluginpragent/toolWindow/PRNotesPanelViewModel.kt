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

    private val _createPRButtonText = MutableStateFlow("Create PR")
    val createPRButtonText = _createPRButtonText.asStateFlow()

    private val _isGeneratePRButtonEnabled = MutableStateFlow(true)
    val isGeneratePRButtonEnabled: StateFlow<Boolean> = _isGeneratePRButtonEnabled.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _recentBranches = MutableStateFlow<List<String>>(emptyList())
    val recentBranches: StateFlow<List<String>> = _recentBranches.asStateFlow()

    private val _selectedBaseBranch = MutableStateFlow<String?>(null)
    val selectedBaseBranch: StateFlow<String?> = _selectedBaseBranch.asStateFlow()

    private val _selectedCompareBranch = MutableStateFlow<String?>(null)
    val selectedCompareBranch: StateFlow<String?> = _selectedCompareBranch.asStateFlow()

    private var existingPRText = ""
    private var existingPRNumber: Int? = null
    private var existingPRTitle: String? = null

    fun onBaseBranchSelected(branchName: String?) {
        _selectedBaseBranch.value = branchName
        checkForExistingPR()
    }

    fun onCompareBranchSelected(branchName: String?) {
        _selectedCompareBranch.value = branchName
        checkForExistingPR()
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

                val remoteUrl = gitCommandService.getRemoteUrl()
                val repository = remoteUrl?.let { githubAPIService.parseGitHubRepository(it) }

                if (repository == null) {
                    // Could not determine repository, skip check
                    return@launch
                }

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

    fun onGeneratePRNotesClicked(
        includeClassDiagram: Boolean = false,
        includeSequenceDiagram: Boolean = false
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
        includeClassDiagram: Boolean = false,
        includeSequenceDiagram: Boolean = false
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
        includeClassDiagram: Boolean = false,
        includeSequenceDiagram: Boolean = false
    ): String {
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

            if (includeClassDiagram) {
                appendLine("4. **Class Diagrams**: Create a set of **concise and meaningful class diagrams** that illustrate the key architectural relationships introduced or modified in this pull request.")
                appendLine()
                appendLine("**Objectives:**")
                appendLine("- Explain *what this change achieves conceptually* (not just which classes exist)")
                appendLine("- Show *how responsibilities are divided across layers* (e.g. View, ViewModel, Domain, Data)")
                appendLine("- Prefer clarity over completeness — use multiple small diagrams if needed rather than one large one")
                appendLine()
                appendLine("**Guidelines:**")
                appendLine("1. Identify the **main purpose or feature** affected by this PR (e.g., \"User authentication flow\", \"Image upload pipeline\").")
                appendLine("2. Create **1–3 focused Mermaid class diagrams**, each addressing a single viewpoint or submodule if appropriate.")
                appendLine("3. Use **stereotype annotations** inside the class body to indicate the layer. Place `<<View>>`, `<<ViewModel>>`, `<<Domain>>`, or `<<Repository>>` as the first line inside the class braces. Example:")
                appendLine("   ```")
                appendLine("   class LoginActivity {")
                appendLine("       <<View>>")
                appendLine("       +login() void")
                appendLine("   }```")
                appendLine("   Do NOT use stereotypes after the class name like `class LoginActivity <<View>>`")
                appendLine("4. Show only the **public relationships** and **important methods or dependencies** that help the reader understand the flow of data or control.")
                appendLine("5. Exclude trivial helpers, framework classes, and generated code.")
                appendLine()
                appendLine("**Output Format:**")
                appendLine("- Each diagram in a separate Mermaid block")
                appendLine("- Include a short descriptive caption before each diagram, like:")
                appendLine("  \"_Diagram 1: User login flow across View → ViewModel → Domain layers_\"")
                appendLine()
            }

            if (includeSequenceDiagram) {
                appendLine("5. **Sequence Diagrams**: Create a set of **concise and meaningful sequence diagrams** that illustrate the key interactions introduced or modified in this pull request.")
                appendLine()
                appendLine("**Objectives:**")
                appendLine("- Explain *what interaction flows this change introduces or modifies*")
                appendLine("- Show *how components communicate across layers* (e.g. View → ViewModel → Domain → Repository)")
                appendLine("- Prefer clarity over completeness — use multiple focused diagrams if needed rather than one complex one")
                appendLine()
                appendLine("**Guidelines:**")
                appendLine("1. Identify the **main user flow or system interaction** affected by this PR (e.g., \"User login process\", \"Data sync workflow\").")
                appendLine("2. Create **1–3 focused Mermaid sequence diagrams**, each showing a single key interaction flow.")
                appendLine("3. Use **participant labels with layer indicators** like `View: LoginActivity`, `ViewModel: LoginViewModel`, `Repository: UserRepository` to show the architectural layer.")
                appendLine("4. Show only the **important messages and method calls** that help understand the interaction flow.")
                appendLine("5. Exclude trivial or internal implementation details that don't affect the understanding of the flow.")
                appendLine()
                appendLine("**Output Format:**")
                appendLine("- Each diagram in a separate Mermaid block")
                appendLine("- Include a short descriptive caption before each diagram, like:")
                appendLine("  \"_Diagram 1: User login interaction flow from View to Repository_\"")
                appendLine()
            }

            appendLine("Format the response in markdown.")
        }
    }

    fun cleanup() {
        coroutineScope.cancel()
    }
}