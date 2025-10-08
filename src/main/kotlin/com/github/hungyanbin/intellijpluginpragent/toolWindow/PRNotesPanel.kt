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
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel

class PRNotesPanel(private val project: Project) : JBPanel<JBPanel<*>>() {

    private val plainTextArea = JBTextArea()
    private val previewPanel = JBPanel<JBPanel<*>>().apply {
        layout = BorderLayout()
    }
    private var markdownPreviewPanel: MarkdownJCEFHtmlPanel? = null
    private val tabbedPane = JBTabbedPane()
    private val statusLabel = JLabel(" ").apply {
        border = javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5)
    }
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val viewModel = PRNotesPanelViewModel(project.basePath!!)
    private var createPRButton: JButton
    private var generateButton: JButton
    private val baseBranchComboBox = JComboBox<String>().apply {
        addActionListener {
            viewModel.onBaseBranchSelected(selectedItem as? String)
        }
    }
    private val compareBranchComboBox = JComboBox<String>().apply {
        addActionListener {
            viewModel.onCompareBranchSelected(selectedItem as? String)
        }
    }
    private val classDiagramCheckBox = JCheckBox("Class Diagram")
    private val sequenceDiagramCheckBox = JCheckBox("Sequence Diagram")

    init {
        layout = BorderLayout()

        // Create top panel with branch selectors and buttons
        val topPanel = JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                insets = Insets(5, 5, 5, 5)
                fill = GridBagConstraints.HORIZONTAL
            }

            // Base branch selector - Row 0
            gbc.gridx = 0
            gbc.gridy = 0
            gbc.weightx = 0.0
            add(JLabel("Base Branch:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            add(baseBranchComboBox, gbc)

            // Compare branch selector - Row 1
            gbc.gridx = 0
            gbc.gridy = 1
            gbc.weightx = 0.0
            add(JLabel("Compare Branch:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            add(compareBranchComboBox, gbc)

            // Checkboxes panel - Row 2
            gbc.gridx = 0
            gbc.gridy = 2
            gbc.gridwidth = 2
            gbc.weightx = 1.0
            gbc.fill = GridBagConstraints.NONE
            gbc.anchor = GridBagConstraints.WEST
            add(JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
                add(classDiagramCheckBox)
                add(sequenceDiagramCheckBox)
            }, gbc)

            // Buttons panel - Row 3
            gbc.gridx = 0
            gbc.gridy = 3
            gbc.gridwidth = 2
            gbc.weightx = 1.0
            gbc.fill = GridBagConstraints.NONE
            gbc.anchor = GridBagConstraints.WEST
            generateButton = JButton("Generate PR Notes").apply {
                addActionListener {
                    viewModel.onGeneratePRNotesClicked(
                        includeClassDiagram = classDiagramCheckBox.isSelected,
                        includeSequenceDiagram = sequenceDiagramCheckBox.isSelected
                    )
                }
            }

            createPRButton = JButton("Create PR").apply {
                addActionListener {
                    viewModel.onCreatePRClicked(plainTextArea.text)
                }
                isEnabled = false // Initially disabled until notes are generated
            }

            add(JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
                add(generateButton)
                add(createPRButton)
            }, gbc)
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

        // Create center panel with status label and tabbed pane
        val centerPanel = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            add(tabbedPane, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }

        // Create bottom panel with prompt input area and send button
        val promptTextArea = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            rows = 3
            text = "Enter prompt to modify PR content..."
        }

        val bottomPanel = JPanel(BorderLayout()).apply {
            border = javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5)

            val promptScrollPane = JBScrollPane(promptTextArea).apply {
                preferredSize = Dimension(400, 80)
            }

            val sendButton = JButton("Send").apply {
                addActionListener {
                    val prompt = promptTextArea.text
                    if (prompt.isNotBlank() && !prompt.contains("Enter prompt to modify")) {
                        viewModel.onModifyPRNotesClicked(prompt, classDiagramCheckBox.isSelected, sequenceDiagramCheckBox.isSelected)
                    }
                }
            }

            add(promptScrollPane, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        add(topPanel, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        subscribeViewModel()
    }

    private fun subscribeViewModel() {
        // Collect branch list and populate combo boxes
        coroutineScope.launch {
            viewModel.recentBranches.collect { branches ->
                runOnUI {
                    baseBranchComboBox.removeAllItems()
                    compareBranchComboBox.removeAllItems()
                    branches.forEach { branch ->
                        baseBranchComboBox.addItem(branch)
                        compareBranchComboBox.addItem(branch)
                    }
                }
            }
        }

        // Observe Generate button enabled state
        coroutineScope.launch {
            viewModel.isGeneratePRButtonEnabled.collect { isEnabled ->
                runOnUI {
                    generateButton.isEnabled = isEnabled
                }
            }
        }

        // Set default selected base branch
        coroutineScope.launch {
            viewModel.selectedBaseBranch.collect { branchName ->
                if (branchName != null) {
                    runOnUI {
                        baseBranchComboBox.selectedItem = branchName
                    }
                }
            }
        }

        // Set default selected compare branch
        coroutineScope.launch {
            viewModel.selectedCompareBranch.collect { branchName ->
                if (branchName != null) {
                    runOnUI {
                        compareBranchComboBox.selectedItem = branchName
                    }
                }
            }
        }

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

        coroutineScope.launch {
            viewModel.createPRButtonText.collect { text ->
                runOnUI {
                    createPRButton.text = text
                }
            }
        }

        // Observe status message
        coroutineScope.launch {
            viewModel.statusMessage.collect { message ->
                runOnUI {
                    statusLabel.text = if (message.isEmpty()) " " else message
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