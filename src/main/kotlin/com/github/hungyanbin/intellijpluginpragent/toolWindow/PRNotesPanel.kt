package com.github.hungyanbin.intellijpluginpragent.toolWindow

import com.github.hungyanbin.intellijpluginpragent.utils.runOnUI
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.*
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class PRNotesPanel(private val project: Project) : JBPanel<JBPanel<*>>() {

    private val plainTextArea = JBTextArea()
    private val previewPanel = JBPanel<JBPanel<*>>().apply {
        layout = BorderLayout()
    }
    private var markdownPreviewPanel: MarkdownJCEFHtmlPanel? = null
    private val tabbedPane = JBTabbedPane()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val viewModel = PRNotesPanelViewModel(project.basePath!!)
    private var createPRButton: JButton

    init {
        layout = BorderLayout()

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            val generateButton = JButton("Generate PR Notes").apply {
                addActionListener {
                    viewModel.onGeneratePRNotesClicked()
                }
            }

            createPRButton = JButton("Create PR").apply {
                addActionListener {
                    viewModel.onCreatePRClicked(plainTextArea.text)
                }
                isEnabled = false // Initially disabled until notes are generated
            }

            add(generateButton)
            add(createPRButton)
        }

        plainTextArea.apply {
            isEditable = true
            text = "Click 'Generate PR Notes' to create pull request notes using AI..."
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                    updateMarkdownPreview(text)
                }
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                    updateMarkdownPreview(text)
                }
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                    updateMarkdownPreview(text)
                }
            })
        }

        val plainTextScrollPane = JBScrollPane(plainTextArea).apply {
            preferredSize = Dimension(400, 300)
        }

        tabbedPane.addTab("Plain Text", plainTextScrollPane)
        tabbedPane.addTab("Preview", previewPanel)

        add(buttonPanel, BorderLayout.NORTH)
        add(tabbedPane, BorderLayout.CENTER)

        coroutineScope.launch {
            viewModel.prNotesText.collect { markdownText ->
                runOnUI {
                    plainTextArea.text = markdownText
                    updateMarkdownPreview(markdownText)
                }
            }
        }

        coroutineScope.launch {
            viewModel.isCreatePRButtonEnabled.collect {
                runOnUI {
                    createPRButton.isEnabled = it
                }
            }
        }

    }

    private fun updateMarkdownPreview(markdownText: String) {
        try {
            // Create panel on first use
            if (markdownPreviewPanel == null) {
                val virtualFile = LightVirtualFile("preview.md", markdownText)
                val newPanel = MarkdownJCEFHtmlPanel(project, virtualFile)

                previewPanel.add(newPanel.component, BorderLayout.CENTER)
                markdownPreviewPanel = newPanel
            }

            // Update existing panel with new content
            val virtualFile = LightVirtualFile("preview.md", markdownText)
            val html = MarkdownUtil.generateMarkdownHtml(virtualFile, markdownText, project)
            markdownPreviewPanel?.setHtml(html, 0)
        } catch (e: Exception) {
            // Fallback to plain text if markdown rendering fails
            val errorArea = JBTextArea("Failed to render markdown preview: ${e.message}")
            errorArea.isEditable = false
            previewPanel.removeAll()
            previewPanel.add(JBScrollPane(errorArea), BorderLayout.CENTER)
            previewPanel.revalidate()
            previewPanel.repaint()
        }
    }

    fun cleanup() {
        coroutineScope.cancel()
        viewModel.cleanup()
        markdownPreviewPanel?.let { Disposer.dispose(it) }
    }
}