package com.github.hungyanbin.intellijpluginpragent.toolWindow

import com.github.hungyanbin.intellijpluginpragent.repository.SecretRepository
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton

class PRNotesPanel(private val project: Project) : JBPanel<JBPanel<*>>() {

    private val prNotesArea = JBTextArea()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val anthropicAPIService = AnthropicAPIService()
    private val gitCommandHelper = GitCommandHelper(project.basePath!!)
    private val secretRepository = SecretRepository()

    init {
        layout = BorderLayout()

        val generateButton = JButton("Generate PR Notes").apply {
            addActionListener { generatePRNotes() }
        }

        prNotesArea.apply {
            isEditable = false
            text = "Click 'Generate PR Notes' to create pull request notes using AI..."
        }

        val prScrollPane = JBScrollPane(prNotesArea).apply {
            preferredSize = Dimension(400, 300)
        }

        add(generateButton, BorderLayout.NORTH)
        add(prScrollPane, BorderLayout.CENTER)
    }

    private fun generatePRNotes() {
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

                ApplicationManager.getApplication().invokeLater {
                    prNotesArea.text = response
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    prNotesArea.text = "Error generating PR notes: ${e.message}"
                }
            }
        }
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