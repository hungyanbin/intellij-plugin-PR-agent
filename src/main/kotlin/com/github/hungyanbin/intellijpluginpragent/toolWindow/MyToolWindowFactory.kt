package com.github.hungyanbin.intellijpluginpragent.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val project: Project, private val toolWindow: ToolWindow) {

        private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val anthropicAPIService = AnthropicAPIService()
        private val gitCommandHelper = GitCommandHelper(project.basePath!!)
        private val configPanel = ConfigPanel()
        private val gitPanel = GitPanel(project)

        fun getContent() = JBTabbedPane().apply {
            // Config Panel Tab
            addTab("Config", configPanel)

            // Git Panel Tab
            addTab("Git", gitPanel)

            // PR Notes Tab
            addTab("PR Notes", createPRNotesPanel())
        }

        private val prNotesArea = JBTextArea()

        private fun createPRNotesPanel() = JBPanel<JBPanel<*>>().apply {
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
            val apiKey = configPanel.getApiKey()

            if (apiKey.isEmpty()) {
                prNotesArea.text = "Error: Please enter your Anthropic API key in the Config tab"
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
            configPanel.cleanup()
            gitPanel.cleanup()
        }
    }


}
