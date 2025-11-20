package com.github.hungyanbin.pragent.toolWindow

import com.github.hungyanbin.pragent.service.BranchHistory
import java.io.File

class PromptTemplateRepository {
    private val storageFile = File(System.getProperty("user.home"), ".intellij-pr-agent/base-prompt.txt")

    init {
        // Ensure parent directory exists
        storageFile.parentFile?.mkdirs()
    }

    fun buildBasePrompt(branchHistory: BranchHistory, fileDiff: String): String {
        val commits = if (branchHistory.commits.isNotEmpty()) {
            """
            ## Commits
            ${branchHistory.commits.joinToString("\n") { "- ${it.hash}: ${it.description}" }}

            """.trimIndent()
        } else {
            ""
        }

        return """
            Please generate comprehensive pull request notes based on the following git information:

            ## Branch Information
            - Current Branch: ${branchHistory.currentBranch.name}
            - Parent Branch: ${branchHistory.parentBranch.name}

            $commits## Code Changes
            ```
            $fileDiff
            ```

            ${getBasePrompt()}

        """.trimIndent()
    }

    fun getBasePrompt(): String {
        // Try to load from disk first
        if (storageFile.exists()) {
            try {
                return storageFile.readText()
            } catch (e: Exception) {
                // If reading fails, fall back to default
                e.printStackTrace()
            }
        }

        // Return default prompt if file doesn't exist or reading fails
        return getDefaultBasePrompt()
    }

    fun updateBasePrompt(newPrompt: String) {
        try {
            storageFile.writeText(newPrompt)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun getDefaultBasePrompt(): String {
        return """
            Please create a concise pull request description that reviewers can quickly scan. Include:

            1. **Summary** (2-3 sentences): What changed and why
            2. **Key Changes** (bullet points): The main modifications made
            3. **Breaking Changes** (if any): What needs attention or migration

            Keep it brief - reviewers should understand the PR in under 2 minutes. Avoid:
            - Detailed implementation explanations
            - Testing checklists
            - Code snippets (unless critical for understanding breaking changes)
            - Extensive architectural discussions

            Focus on WHAT changed and WHY, not HOW it was implemented.
        """.trimIndent()
    }

    fun getClassDiagramPrompt(): String {
        return """
            4. **Class Diagrams**: Create a set of **concise and meaningful class diagrams** that illustrate the key architectural relationships introduced or modified in this pull request.

            **Objectives:**
            - Explain *what this change achieves conceptually* (not just which classes exist)
            - Show *how responsibilities are divided across layers* (e.g. View, ViewModel, Domain, Data)
            - Prefer clarity over completeness — use multiple small diagrams if needed rather than one large one

            **Guidelines:**
            1. Identify the **main purpose or feature** affected by this PR (e.g., "User authentication flow", "Image upload pipeline").
            2. Create **1–3 focused Mermaid class diagrams**, each addressing a single viewpoint or submodule if appropriate.
            3. Use **stereotype annotations** inside the class body to indicate the layer. Place `<<View>>`, `<<ViewModel>>`, `<<Domain>>`, or `<<Repository>>` as the first line inside the class braces. Example:
               ```
               class LoginActivity {
                   <<View>>
                   +login() void
               }```
               Do NOT use stereotypes after the class name like `class LoginActivity <<View>>`
            4. Show only the **public relationships** and **important methods or dependencies** that help the reader understand the flow of data or control.
            5. Exclude trivial helpers, framework classes, and generated code.

            **Output Format:**
            - Each diagram in a separate Mermaid block
            - Include a short descriptive caption before each diagram, like:
              "_Diagram 1: User login flow across View → ViewModel → Domain layers_"

        """.trimIndent()
    }

    fun getSequenceDiagramPrompt(): String {
        return """
            5. **Sequence Diagrams**: Create a set of **concise and meaningful sequence diagrams** that illustrate the key interactions introduced or modified in this pull request.

            **Objectives:**
            - Explain *what interaction flows this change introduces or modifies*
            - Show *how components communicate across layers* (e.g. View → ViewModel → Domain → Repository)
            - Prefer clarity over completeness — use multiple focused diagrams if needed rather than one complex one

            **Guidelines:**
            1. Identify the **main user flow or system interaction** affected by this PR (e.g., "User login process", "Data sync workflow").
            2. Create **1–3 focused Mermaid sequence diagrams**, each showing a single key interaction flow.
            3. Use **participant labels with layer indicators** like `View: LoginActivity`, `ViewModel: LoginViewModel`, `Repository: UserRepository` to show the architectural layer.
            4. Show only the **important messages and method calls** that help understand the interaction flow.
            5. Exclude trivial or internal implementation details that don't affect the understanding of the flow.

            **Output Format:**
            - Each diagram in a separate Mermaid block
            - Include a short descriptive caption before each diagram, like:
              "_Diagram 1: User login interaction flow from View to Repository_"

        """.trimIndent()
    }
}