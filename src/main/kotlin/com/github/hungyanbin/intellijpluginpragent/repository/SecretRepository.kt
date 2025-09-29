package com.github.hungyanbin.intellijpluginpragent.repository

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

class SecretRepository {

    private val anthropicApiKeyAttribute = CredentialAttributes(
        "IntelliJ Plugin PR Agent",
        "anthropic_api_key"
    )

    private val githubPatAttribute = CredentialAttributes(
        "IntelliJ Plugin PR Agent",
        "github_pat"
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

    fun storeGithubPat(pat: String) {
        val credentials = Credentials("github_pat", pat)
        PasswordSafe.instance.set(githubPatAttribute, credentials)
    }

    fun getGithubPat(): String? {
        return PasswordSafe.instance.getPassword(githubPatAttribute)
    }

    fun clearGithubPat() {
        PasswordSafe.instance.set(githubPatAttribute, null)
    }
}