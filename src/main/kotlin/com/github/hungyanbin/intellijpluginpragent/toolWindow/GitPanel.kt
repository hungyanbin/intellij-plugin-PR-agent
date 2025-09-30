package com.github.hungyanbin.intellijpluginpragent.toolWindow

import com.github.hungyanbin.intellijpluginpragent.utils.runOnUI
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton

class GitPanel(private val project: Project) : JBPanel<JBPanel<*>>() {

    private val gitHistoryArea = JBTextArea()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gitCommandHelper = GitCommandHelper(project.basePath!!)

    init {
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

    private fun loadGitHistory() {
        gitHistoryArea.text = "Loading git history..."

        coroutineScope.launch {
            try {
                val branchHistory = gitCommandHelper.getBranchHistory()

                val fileDiff = gitCommandHelper.getFileDiff(
                    branchHistory.parentBranch.hash,
                    branchHistory.currentBranch.hash
                )

                runOnUI {
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
                        appendLine(fileDiff)
                    }
                    gitHistoryArea.text = historyText
                }
            } catch (e: Exception) {
                runOnUI {
                    gitHistoryArea.text = "Error loading git history: ${e.message}"
                }
            }
        }
    }

    fun cleanup() {
        coroutineScope.cancel()
    }
}