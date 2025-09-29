package com.github.hungyanbin.intellijpluginpragent.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JPasswordField


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val toolWindow: ToolWindow) {

        private val apiKeyField = JPasswordField()
        private val inputField = JBTextField()
        private val resultArea = JBTextArea()
        private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val anthropicAPIService = AnthropicAPIService()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
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

        fun cleanup() {
            coroutineScope.cancel()
        }
    }


}
