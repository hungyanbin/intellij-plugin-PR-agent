package com.github.hungyanbin.pragent.toolWindow

import com.github.hungyanbin.pragent.service.ErrorLogEntry
import com.github.hungyanbin.pragent.service.ErrorLogListener
import com.github.hungyanbin.pragent.service.ErrorLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*

/**
 * Debug Panel that displays error logs with navigation capabilities.
 *
 * Features:
 * - Browse through error logs with Previous/Next buttons
 * - View detailed error information (timestamp, context, message, stack trace, metadata)
 * - Copy stack traces to clipboard
 * - Clear all error logs with confirmation
 * - Automatically refreshes when new errors are logged
 */
class DebugPanel : JBPanel<JBPanel<*>>() {

    private val errorLogger = ErrorLogger.getInstance()
    private var currentErrorIndex = 0

    private val errorLogListener = object : ErrorLogListener {
        override fun onErrorLogsChanged() {
            // Update UI on EDT (Event Dispatch Thread)
            SwingUtilities.invokeLater {
                refreshDisplay()
            }
        }
    }

    // Header components
    private val errorCountLabel = JBLabel()
    private val clearButton = JButton("Clear All Logs")

    // Navigation components
    private val previousButton = JButton("< Previous")
    private val nextButton = JButton("Next >")
    private val positionLabel = JBLabel()

    // Error details components
    private val timestampLabel = JBLabel()
    private val operationLabel = JBLabel()
    private val operationPanel = JPanel(BorderLayout())
    private val errorMessageArea = JTextArea()
    private val stackTraceArea = JTextArea()
    private val copyButton = JButton("Copy")
    private val metadataArea = JTextArea()
    private val metadataPanel = JPanel(BorderLayout())

    // Empty state components
    private val emptyStatePanel = JPanel()
    private val contentPanel = JPanel()

    init {
        layout = BorderLayout()
        border = JBUI.Borders.empty(10)

        setupComponents()
        setupListeners()

        // Register listener to get notified of error log changes
        errorLogger.addListener(errorLogListener)

        refreshDisplay()
    }

    private fun setupComponents() {
        // Setup header panel
        val headerPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(10)

            add(errorCountLabel, BorderLayout.WEST)
            add(clearButton, BorderLayout.EAST)
        }

        // Setup navigation panel
        val navigationPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 5)).apply {
            border = JBUI.Borders.emptyBottom(10)

            add(previousButton)
            add(positionLabel)
            add(nextButton)
        }

        // Setup error details panel with GridBagLayout for better control
        val detailsPanel = JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.NORTHWEST
                weightx = 1.0
                gridx = 0
                insets = JBUI.insets(5)
            }

            // Timestamp section
            add(JBLabel("Occurred At:").apply {
                font = font.deriveFont(Font.BOLD)
            }, gbc.apply { gridy = 0 })

            timestampLabel.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            add(timestampLabel, gbc.apply { gridy = 1 })

            // Operation context section (conditionally visible)
            operationPanel.apply {
                add(JBLabel("Operation:").apply {
                    font = font.deriveFont(Font.BOLD)
                }, BorderLayout.NORTH)
                add(operationLabel, BorderLayout.CENTER)
            }
            add(operationPanel, gbc.apply { gridy = 2 })

            // Error message section
            add(JBLabel("Error Message:").apply {
                font = font.deriveFont(Font.BOLD)
            }, gbc.apply { gridy = 3 })

            errorMessageArea.apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                rows = 3
                background = this@DebugPanel.background
            }
            add(JBScrollPane(errorMessageArea).apply {
                preferredSize = JBUI.size(400, 60)
            }, gbc.apply { gridy = 4 })

            // Stack trace section
            val stackTraceHeaderPanel = JPanel(BorderLayout()).apply {
                add(JBLabel("Stack Trace:").apply {
                    font = font.deriveFont(Font.BOLD)
                }, BorderLayout.WEST)
                add(copyButton, BorderLayout.EAST)
            }
            add(stackTraceHeaderPanel, gbc.apply { gridy = 5 })

            stackTraceArea.apply {
                isEditable = false
                lineWrap = false
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                tabSize = 2
            }
            add(JBScrollPane(stackTraceArea).apply {
                preferredSize = JBUI.size(400, 200)
            }, gbc.apply {
                gridy = 6
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            })

            // Metadata section (conditionally visible)
            metadataPanel.apply {
                add(JBLabel("Additional Context:").apply {
                    font = font.deriveFont(Font.BOLD)
                }, BorderLayout.NORTH)

                metadataArea.apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                    rows = 3
                    font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                    background = this@DebugPanel.background
                }
                add(metadataArea, BorderLayout.CENTER)
            }
            add(metadataPanel, gbc.apply {
                gridy = 7
                weighty = 0.0
                fill = GridBagConstraints.HORIZONTAL
            })
        }

        // Setup content panel (shown when errors exist)
        contentPanel.layout = BorderLayout()
        contentPanel.add(navigationPanel, BorderLayout.NORTH)
        contentPanel.add(JBScrollPane(detailsPanel).apply {
            border = null
        }, BorderLayout.CENTER)

        // Setup empty state panel
        emptyStatePanel.layout = BoxLayout(emptyStatePanel, BoxLayout.Y_AXIS)
        emptyStatePanel.border = JBUI.Borders.empty(50)
        emptyStatePanel.add(Box.createVerticalGlue())
        emptyStatePanel.add(JBLabel("No errors logged yet").apply {
            font = font.deriveFont(Font.BOLD, 16f)
            alignmentX = CENTER_ALIGNMENT
        })
        emptyStatePanel.add(Box.createVerticalStrut(10))
        emptyStatePanel.add(JBLabel("Errors will appear here when they occur during plugin operations").apply {
            alignmentX = CENTER_ALIGNMENT
        })
        emptyStatePanel.add(Box.createVerticalGlue())

        // Add header and content/empty state to main panel
        add(headerPanel, BorderLayout.NORTH)
    }

    private fun setupListeners() {
        clearButton.addActionListener {
            handleClearAllLogs()
        }

        previousButton.addActionListener {
            if (currentErrorIndex > 0) {
                currentErrorIndex--
                refreshDisplay()
            }
        }

        nextButton.addActionListener {
            if (currentErrorIndex < errorLogger.getErrorCount() - 1) {
                currentErrorIndex++
                refreshDisplay()
            }
        }

        copyButton.addActionListener {
            copyStackTraceToClipboard()
        }

        // Keyboard shortcuts for navigation
        val inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW)
        val actionMap = actionMap

        inputMap.put(KeyStroke.getKeyStroke("LEFT"), "previous")
        inputMap.put(KeyStroke.getKeyStroke("control LEFT"), "previous")
        actionMap.put("previous", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (previousButton.isEnabled) {
                    previousButton.doClick()
                }
            }
        })

        inputMap.put(KeyStroke.getKeyStroke("RIGHT"), "next")
        inputMap.put(KeyStroke.getKeyStroke("control RIGHT"), "next")
        actionMap.put("next", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (nextButton.isEnabled) {
                    nextButton.doClick()
                }
            }
        })
    }

    /**
     * Refreshes the entire display based on current error logs.
     */
    fun refreshDisplay() {
        val errorCount = errorLogger.getErrorCount()

        // Update header
        errorCountLabel.text = if (errorCount == 1) {
            "1 error logged"
        } else {
            "$errorCount errors logged"
        }

        // Show/hide clear button and navigation based on error count
        clearButton.isVisible = errorCount > 0

        // Remove existing content/empty state panel
        if (componentCount > 1) {
            remove(getComponent(1))
        }

        if (errorCount == 0) {
            // Show empty state
            add(emptyStatePanel, BorderLayout.CENTER)
            currentErrorIndex = 0
        } else {
            // Ensure current index is valid
            if (currentErrorIndex >= errorCount) {
                currentErrorIndex = errorCount - 1
            }

            // Show content panel
            add(contentPanel, BorderLayout.CENTER)

            // Update navigation controls
            positionLabel.text = "Error ${currentErrorIndex + 1} of $errorCount"
            previousButton.isEnabled = currentErrorIndex > 0
            nextButton.isEnabled = currentErrorIndex < errorCount - 1

            // Update error details
            errorLogger.getErrorById(currentErrorIndex)?.let { entry ->
                displayErrorDetails(entry)
            }
        }

        revalidate()
        repaint()
    }

    private fun displayErrorDetails(entry: ErrorLogEntry) {
        // Timestamp
        timestampLabel.text = entry.getFormattedTimestamp()

        // Operation context (show/hide based on availability)
        if (entry.operationContext != null) {
            operationLabel.text = entry.operationContext
            operationPanel.isVisible = true
        } else {
            operationPanel.isVisible = false
        }

        // Error message
        errorMessageArea.text = entry.errorMessage
        errorMessageArea.caretPosition = 0

        // Stack trace
        stackTraceArea.text = entry.stackTrace
        stackTraceArea.caretPosition = 0

        // Metadata (show/hide based on availability)
        if (entry.metadata.isNotEmpty()) {
            metadataArea.text = entry.getFormattedMetadata()
            metadataArea.caretPosition = 0
            metadataPanel.isVisible = true
        } else {
            metadataPanel.isVisible = false
        }
    }

    private fun handleClearAllLogs() {
        val result = Messages.showYesNoDialog(
            this,
            "Are you sure you want to clear all error logs?",
            "Clear Error Logs",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            errorLogger.clearAllLogs()
            currentErrorIndex = 0
            refreshDisplay()
        }
    }

    private fun copyStackTraceToClipboard() {
        val stackTrace = stackTraceArea.text
        if (stackTrace.isNotBlank()) {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(stackTrace), null)

            // Show a brief feedback (optional)
            copyButton.text = "Copied!"
            Timer(1500) {
                copyButton.text = "Copy"
            }.apply {
                isRepeats = false
                start()
            }
        }
    }

    fun cleanup() {
        errorLogger.removeListener(errorLogListener)
    }
}
