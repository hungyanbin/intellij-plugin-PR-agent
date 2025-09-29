package com.github.hungyanbin.intellijpluginpragent.toolWindow

import com.github.hungyanbin.intellijpluginpragent.repository.SecretRepository
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JPasswordField

class ConfigPanel : JBPanel<JBPanel<*>>() {

    private val apiKeyField = JPasswordField()
    private val githubPatField = JPasswordField()
    private val inputField = JBTextField()
    private val resultArea = JBTextArea()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val anthropicAPIService = AnthropicAPIService()
    private val secretRepository = SecretRepository()

    init {
        layout = BorderLayout()

        val inputPanel = JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints()

            // API Key row
            gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
            add(JBLabel("Anthropic API Key:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            add(apiKeyField, gbc)

            // GitHub PAT row
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            add(JBLabel("GitHub PAT:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            add(githubPatField, gbc)

            // Apply button (spans both credential rows)
            gbc.gridx = 2; gbc.gridy = 0; gbc.gridheight = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            add(JButton("Apply").apply {
                addActionListener { applySettings() }
            }, gbc)

            // Prompt row
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridheight = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            add(JBLabel("Prompt:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            add(inputField, gbc)

            // Send button
            gbc.gridx = 2; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
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

        // Load saved credentials on initialization
        loadSavedCredentials()
    }

    private fun applySettings() {
        val apiKey = String(apiKeyField.password)
        val githubPat = String(githubPatField.password)
        val messages = mutableListOf<String>()

        if (apiKey.isNotEmpty()) {
            secretRepository.storeAnthropicApiKey(apiKey)
            messages.add("Anthropic API key saved")
        }

        if (githubPat.isNotEmpty()) {
            secretRepository.storeGithubPat(githubPat)
            messages.add("GitHub PAT saved")
        }

        if (messages.isNotEmpty()) {
            resultArea.text = messages.joinToString(", ") + " successfully!"
        } else {
            resultArea.text = "Please enter at least one credential before applying"
        }
    }

    private fun loadSavedCredentials() {
        val savedApiKey = secretRepository.getAnthropicApiKey()
        if (savedApiKey != null) {
            apiKeyField.text = savedApiKey
        }

        val savedGithubPat = secretRepository.getGithubPat()
        if (savedGithubPat != null) {
            githubPatField.text = savedGithubPat
        }
    }

    private fun sendRequest() {
        val apiKey = getStoredApiKey()
        val prompt = inputField.text

        if (apiKey.isEmpty()) {
            resultArea.text = "Error: Please enter and apply your Anthropic API key first"
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

    private fun getStoredApiKey(): String {
        return secretRepository.getAnthropicApiKey() ?: ""
    }

    fun cleanup() {
        coroutineScope.cancel()
    }
}