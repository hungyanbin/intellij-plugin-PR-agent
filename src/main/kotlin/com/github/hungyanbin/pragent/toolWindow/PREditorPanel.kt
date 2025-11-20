package com.github.hungyanbin.pragent.toolWindow

import com.github.hungyanbin.pragent.repository.SecretRepository
import com.github.hungyanbin.pragent.service.ErrorLogger
import com.github.hungyanbin.pragent.utils.runOnUI
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
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
import javax.swing.JTextField

class PREditorPanel(private val project: Project) : JBPanel<JBPanel<*>>() {

    private val plainTextArea = JBTextArea()
    private val previewPanel = JBPanel<JBPanel<*>>().apply {
        layout = BorderLayout()
    }
    private var markdownPreviewPanel: MarkdownJCEFHtmlPanel? = null
    private val basePromptTextArea = JBTextArea().apply {
        isEditable = true
        lineWrap = true
        wrapStyleWord = true
        text = "Loading base prompt..."
    }
    private val tabbedPane = JBTabbedPane()
    private val statusLabel = JLabel(" ").apply {
        border = javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5)
    }
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val viewModel = PRNotesPanelViewModel(project.basePath!!)
    private val secretRepository = SecretRepository()
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
    private val titleTextField = JTextField().apply {
        text = ""
    }

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

            // Title field - Row 4
            gbc.gridx = 0
            gbc.gridy = 4
            gbc.gridwidth = 1
            gbc.weightx = 0.0
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.anchor = GridBagConstraints.WEST
            add(JLabel("Title:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            add(titleTextField, gbc)
        }

        plainTextArea.apply {
            isEditable = true
            lineWrap = true
            wrapStyleWord = true
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

        // Create Base Prompt tab with text area and update button
        val basePromptPanel = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()

            val basePromptScrollPane = JBScrollPane(basePromptTextArea).apply {
                preferredSize = Dimension(400, 300)
            }

            val updateButton = JButton("Update Base Prompt").apply {
                addActionListener {
                    viewModel.onUpdateBasePromptClicked(basePromptTextArea.text)
                }
            }

            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                add(updateButton)
            }

            add(basePromptScrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        tabbedPane.addTab("Plain Text", plainTextScrollPane)
        tabbedPane.addTab("Preview", previewPanel)
        tabbedPane.addTab("Base Prompt", basePromptPanel)

        // Initialize preview panel with appropriate message
        initializePreviewPanel()

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
            viewModel.compareBranches.collect { branches ->
                runOnUI {
                    compareBranchComboBox.removeAllItems()
                    branches.forEach { branch ->
                        compareBranchComboBox.addItem(branch)
                    }
                }
            }
        }

        coroutineScope.launch {
            viewModel.baseBranches.collect { branches ->
                runOnUI {
                    baseBranchComboBox.removeAllItems()
                    branches.forEach { branch ->
                        baseBranchComboBox.addItem(branch)
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
            viewModel.prNoteTitle.collect { title ->
                runOnUI {
                    titleTextField.text = title
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

        // Observe base prompt text
        coroutineScope.launch {
            viewModel.basePromptText.collect { text ->
                runOnUI {
                    basePromptTextArea.text = text
                }
            }
        }
    }

    private fun initializePreviewPanel() {
        // Show appropriate message on initialization based on available features
        if (!isMarkdownPluginAvailable() || !JBCefApp.isSupported()) {
            showMarkdownPluginNotAvailableMessage()
        }
    }

    private fun updateMarkdownPreview(markdownText: String) {
        try {
            // Check if markdown plugin is available
            if (!isMarkdownPluginAvailable()) {
                showMarkdownPluginNotAvailableMessage()
                return
            }

            // Create panel on first use
            if (markdownPreviewPanel == null) {
                val virtualFile = LightVirtualFile("preview.md", markdownText)
                val markdownPanel = MarkdownJCEFHtmlPanel(project, virtualFile)
                injectGithubAuthorization(markdownPanel)

                previewPanel.add(markdownPanel.component, BorderLayout.CENTER)
                markdownPreviewPanel = markdownPanel
            }

            // Update existing panel with new content
            val virtualFile = LightVirtualFile("preview.md", markdownText)
            val html = MarkdownUtil.generateMarkdownHtml(virtualFile, markdownText, project)
            markdownPreviewPanel?.setHtml(html, 0)
        } catch (e: Exception) {
            ErrorLogger.getInstance().logError("Failed to update markdown preview ${e.message}", e)
            // Fallback to plain text if markdown rendering fails
            val errorArea = JBTextArea("Failed to render markdown preview: ${e.message}")
            errorArea.isEditable = false
            previewPanel.removeAll()
            previewPanel.add(JBScrollPane(errorArea), BorderLayout.CENTER)
            previewPanel.revalidate()
            previewPanel.repaint()
        }
    }

    private fun isMarkdownPluginAvailable(): Boolean {
        return try {
            Class.forName("org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel")
            true
        } catch (e: Throwable) {
            ErrorLogger.getInstance().logError("[PREditorPanel] Markdown plugin not found: ${e.message}", e)
            false
        }
    }

    private fun showMarkdownPluginNotAvailableMessage() {
        val messageArea = JBTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            text = """
                Markdown Preview Unavailable

                The Markdown plugin is not installed or enabled.

                To enable preview:
                1. Help → Find Action (or Cmd+Shift+A on macOS)
                2. Type "Choose Boot Java Runtime for the IDE"
                3. Select the newest version with JCEF in the name
                4. Restart IDE
            """.trimIndent()
        }
        previewPanel.removeAll()
        previewPanel.add(JBScrollPane(messageArea), BorderLayout.CENTER)
        previewPanel.revalidate()
        previewPanel.repaint()
    }

    private fun injectGithubAuthorization(newPanel: MarkdownJCEFHtmlPanel) {
        try {
            val jcefBrowser = newPanel as? JBCefBrowser

            jcefBrowser?.jbCefClient?.addRequestHandler(object : CefRequestHandlerAdapter() {
                override fun getResourceRequestHandler(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    request: CefRequest?,
                    isNavigation: Boolean,
                    isDownload: Boolean,
                    requestInitiator: String?,
                    disableDefaultHandling: BoolRef?
                ): CefResourceRequestHandler {
                    return object : CefResourceRequestHandlerAdapter() {
                        override fun onBeforeResourceLoad(
                            browser: CefBrowser?,
                            frame: CefFrame?,
                            request: CefRequest?
                        ): Boolean {
                            request?.let { req ->
                                println("request: ${req.url}")
                                val url = req.url
                                // Add GitHub PAT for GitHub-hosted images
                                if (url.contains("github.com") || url.contains("githubusercontent.com")) {
                                    val githubPat = runBlocking { secretRepository.getGithubPat() }
                                    if (!githubPat.isNullOrEmpty()) {
                                        val headerMap = mutableMapOf<String, String>()
                                        req.getHeaderMap(headerMap)
                                        headerMap["Authorization"] = "token $githubPat"
                                        req.setHeaderMap(headerMap)
                                        println("Authorization added: ${headerMap}")
                                    }
                                }
                            }
                            return false // false means continue with the request
                        }
                    }
                }
            }, jcefBrowser.cefBrowser)
        } catch (e: Exception) {
            ErrorLogger.getInstance().logError("Failed to add request handler: ${e.message}", e)
        }
    }

    fun cleanup() {
        coroutineScope.cancel()
        viewModel.cleanup()
        markdownPreviewPanel?.let { Disposer.dispose(it) }
    }
}