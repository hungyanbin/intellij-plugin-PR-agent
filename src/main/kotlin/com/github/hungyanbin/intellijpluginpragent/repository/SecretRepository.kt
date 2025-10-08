package com.github.hungyanbin.intellijpluginpragent.repository

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

class SecretRepository {

    private val anthropicApiKeyAttribute = CredentialAttributes(
        "IntelliJ Plugin PR Agent",
        "anthropic_api_key"
    )

    fun storeAnthropicApiKey(apiKey: String) {
        val credentials = Credentials("anthropic_api_key", apiKey)
        PasswordSafe.instance.set(anthropicApiKeyAttribute, credentials)
    }

    fun getAnthropicApiKey(): String? {
        return PasswordSafe.instance.getPassword(anthropicApiKeyAttribute)
    }

    fun clearAnthropicApiKey() {
        PasswordSafe.instance.set(anthropicApiKeyAttribute, null)
    }
}