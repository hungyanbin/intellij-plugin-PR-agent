package com.github.hungyanbin.intellijpluginpragent.toolWindow

import com.github.weisj.jsvg.br
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JPasswordField


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

        private val apiKeyField = JPasswordField()
        private val inputField = JBTextField()
        private val resultArea = JBTextArea()
        private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val anthropicAPIService = AnthropicAPIService()
        private val gitCommandHelper = GitCommandHelper(project.basePath!!)

        fun getContent() = JBTabbedPane().apply {
            // Config Panel Tab
            addTab("Config", createConfigPanel())

            // Git Panel Tab
            addTab("Git", createGitPanel())
        }

        private fun createConfigPanel() = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()

            val inputPanel = JPanel(GridBagLayout()).apply {
                val gbc = GridBagConstraints()

                // API Key row
                gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
                add(JBLabel("API Key:"), gbc)
                gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
                add(apiKeyField, gbc)

                // Prompt row
                gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
                add(JBLabel("Prompt:"), gbc)
                gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
                add(inputField, gbc)

                // Send button
                gbc.gridx = 2; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
                add(JButton("Send to Claude").apply {
                    addActionListener { sendRequest() }
                }, gbc)
            }

            resultArea.apply {
                isEditable = false
                text = "Results will appear here..."
            }

            val scrollPane = JBScrollPane(resultArea).apply {
                preferredSize = Dimension(400, 300)
            }

            add(inputPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }

        private val gitHistoryArea = JBTextArea()

        private fun createGitPanel() = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()

            val refreshButton = JButton("Refresh Git History").apply {
                addActionListener { loadGitHistory() }
            }

            gitHistoryArea.apply {
                isEditable = false
                text = "Click 'Refresh Git History' to load commit history..."
            }

            val gitScrollPane = JBScrollPane(gitHistoryArea).apply {
                preferredSize = Dimension(400, 300)
            }

            add(refreshButton, BorderLayout.NORTH)
            add(gitScrollPane, BorderLayout.CENTER)
        }

        private fun sendRequest() {
            val apiKey = String(apiKeyField.password)
            val prompt = inputField.text

            if (apiKey.isEmpty()) {
                resultArea.text = "Error: Please enter your Anthropic API key"
                return
            }

            if (prompt.isEmpty()) {
                resultArea.text = "Error: Please enter a prompt"
                return
            }

            resultArea.text = "Sending request to Claude..."

            coroutineScope.launch {
                try {
                    val response = anthropicAPIService.callAnthropicAPI(apiKey, prompt)
                    ApplicationManager.getApplication().invokeLater {
                        resultArea.text = response
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        resultArea.text = "Error: ${e.message}"
                    }
                }
            }
        }

        private fun loadGitHistory() {
            gitHistoryArea.text = "Loading git history..."

            coroutineScope.launch {
                try {
                    val branchHistory = gitCommandHelper.getBranchHistory()

                    val fileDiff = gitCommandHelper.getFileDiff(
                        branchHistory.parentBranch.hash,
                        branchHistory.currentBranch.hash
                    )

                    ApplicationManager.getApplication().invokeLater {
                        val historyText = buildString {
                            appendLine("Branch History:")
                            appendLine("Current Branch: ${branchHistory.currentBranch.name}")
                            appendLine("Parent Branch: ${branchHistory.parentBranch.name}")
                            appendLine()

                            if (branchHistory.commits.isNotEmpty()) {
                                appendLine("Commits since ${branchHistory.parentBranch.name}:")
                                branchHistory.commits.forEach { commit ->
                                    appendLine("${commit.hash} ${commit.description}")
                                }
                            } else {
                                appendLine("No new commits since ${branchHistory.parentBranch.name}")
                            }

                            appendLine("File diff: ")
                            appendLine("$fileDiff")
                        }
                        gitHistoryArea.text = historyText
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        gitHistoryArea.text = "Error loading git history: ${e.message}"
                    }
                }
            }
        }

        fun cleanup() {
            coroutineScope.cancel()
        }
    }


}
