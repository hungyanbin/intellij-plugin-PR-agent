package com.github.hungyanbin.pragent.toolWindow

import com.github.hungyanbin.pragent.domain.LLMProvider
import com.github.hungyanbin.pragent.repository.SecretRepository
import com.github.hungyanbin.pragent.utils.runOnUI
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ItemEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JPasswordField

class ConfigPanel : JBPanel<JBPanel<*>>() {

    // LLM Provider Section
    private val llmProviderComboBox = JComboBox(LLMProvider.entries.toTypedArray())
    private val llmApiKeyField = JPasswordField()
    private val llmModelComboBox = JComboBox<String>()

    // GitHub Section
    private val githubPatField = JPasswordField()

    private val resultArea = JBTextArea()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val secretRepository = SecretRepository()

    init {
        layout = BorderLayout()

        val mainPanel = JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.NORTHWEST
                insets = Insets(5, 5, 5, 5)
            }

            // ============ LLM Provider Section ============
            gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3; gbc.weightx = 1.0
            add(JBLabel("LLM Provider Settings").apply {
                font = font.deriveFont(font.style or java.awt.Font.BOLD)
            }, gbc)

            // Provider dropdown
            gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0.0
            add(JBLabel("API Provider:"), gbc)
            gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0
            add(llmProviderComboBox, gbc)

            // LLM API Key
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0.0
            add(JBLabel("API Key:"), gbc)
            gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0
            add(llmApiKeyField, gbc)

            // Model selection
            gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0.0
            add(JBLabel("Model:"), gbc)
            gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0
            add(llmModelComboBox, gbc)

            // ============ GitHub PAT Section ============
            gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3; gbc.weightx = 1.0
            gbc.insets = Insets(20, 5, 5, 5) // Extra top margin for section spacing
            add(JBLabel("GitHub Settings").apply {
                font = font.deriveFont(font.style or java.awt.Font.BOLD)
            }, gbc)

            // GitHub PAT
            gbc.gridy = 5; gbc.gridwidth = 1; gbc.weightx = 0.0
            gbc.insets = Insets(5, 5, 5, 5) // Reset to normal margin
            add(JBLabel("GitHub PAT:"), gbc)
            gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0
            add(githubPatField, gbc)

            // ============ Apply Button ============
            gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 3
            gbc.insets = Insets(15, 5, 5, 5) // Extra top margin for button
            gbc.anchor = GridBagConstraints.CENTER
            gbc.fill = GridBagConstraints.NONE
            add(JButton("Apply").apply {
                addActionListener { applySettings() }
            }, gbc)
        }

        resultArea.apply {
            isEditable = false
            text = "Results will appear here..."
        }

        add(mainPanel, BorderLayout.NORTH)

        // Setup provider change listener
        setupProviderListener()

        // Initialize with default provider models
        updateModelOptions(LLMProvider.Anthropic)

        // Load saved credentials on initialization
        coroutineScope.launch {
            loadSavedCredentials()
        }
    }

    private fun setupProviderListener() {
        llmProviderComboBox.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                val selectedProvider = event.item as LLMProvider
                updateModelOptions(selectedProvider)
                loadApiKeyForProvider(selectedProvider)
            }
        }
    }

    private fun loadApiKeyForProvider(provider: LLMProvider) {
        coroutineScope.launch {
            val apiKey = secretRepository.getKeyByLLMProvider(provider)
            runOnUI {
                if (apiKey != null) {
                    llmApiKeyField.text = apiKey
                } else {
                    llmApiKeyField.text = ""
                }
            }
        }
    }

    private fun updateModelOptions(provider: LLMProvider) {
        val models = provider.modelMap.values.toTypedArray()

        llmModelComboBox.model = DefaultComboBoxModel(models)
        if (models.isNotEmpty()) {
            llmModelComboBox.selectedIndex = 0
        }
    }

    private fun applySettings() {
        val llmApiKey = String(llmApiKeyField.password)
        val llmProvider = llmProviderComboBox.selectedItem as LLMProvider
        val llmModel = llmModelComboBox.selectedItem as? String
        val githubPat = String(githubPatField.password)

        resultArea.text = "Saving credentials..."

        coroutineScope.launch {
            val messages = mutableListOf<String>()

            if (llmApiKey.isNotEmpty()) {
                secretRepository.storeKeyByLLMProvider(llmProvider, llmApiKey)
                messages.add("$llmProvider API key saved")
            }

            // Save LLM provider and model (non-sensitive data)
            secretRepository.storeLLMProvider(llmProvider.name)
            if (llmModel != null) {
                secretRepository.storeLLMModel(llmModel)
                messages.add("LLM provider and model saved")
            }

            if (githubPat.isNotEmpty()) {
                secretRepository.storeGithubPat(githubPat)
                messages.add("GitHub PAT saved")
            }

            runOnUI {
                if (messages.isNotEmpty()) {
                    resultArea.text = messages.joinToString(", ") + " successfully!"
                } else {
                    resultArea.text = "Please enter at least one credential before applying"
                }
            }
        }
    }

    private suspend fun loadSavedCredentials() {
        val savedApiKey = secretRepository.getKeyByCurrentLLMProvider()
        val savedGithubPat = secretRepository.getGithubPat()
        val savedProvider = secretRepository.getLLMProvider()
        val savedModel = secretRepository.getLLMModel()

        runOnUI {
            if (savedApiKey != null) {
                llmApiKeyField.text = savedApiKey
            }

            if (savedGithubPat != null) {
                githubPatField.text = savedGithubPat
            }

            // Restore saved LLM provider
            if (savedProvider != null) {
                val provider = LLMProvider.valueOf(savedProvider)
                llmProviderComboBox.selectedItem = provider
                updateModelOptions(provider)

                // Restore saved model after updating model options
                if (savedModel != null) {
                    llmModelComboBox.selectedItem = savedModel
                }
            }
        }
    }

    fun cleanup() {
        coroutineScope.cancel()
    }
}