package com.github.hungyanbin.pragent.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object PRTemplateUtils {

    private val TEMPLATE_LOCATIONS = listOf(
        ".github/pull_request_template.md",
        ".github/PULL_REQUEST_TEMPLATE.md",
        "docs/pull_request_template.md",
        "PULL_REQUEST_TEMPLATE.md",
        "pull_request_template.md"
    )

    /**
     * Attempts to find and read the PR template file from common locations.
     * Returns the template content if found, null otherwise.
     */
    fun findAndReadPRTemplate(project: Project): String? {
        val baseDir = project.baseDir ?: return null

        for (location in TEMPLATE_LOCATIONS) {
            val templateFile = findFileByRelativePath(baseDir, location)
            if (templateFile != null && templateFile.exists()) {
                return try {
                    String(templateFile.contentsToByteArray(), Charsets.UTF_8)
                } catch (e: Exception) {
                    // If reading fails, continue to next location
                    e.printStackTrace()
                    continue
                }
            }
        }

        return null
    }

    /**
     * Finds a file by relative path, handling both nested and root paths.
     */
    private fun findFileByRelativePath(baseDir: VirtualFile, relativePath: String): VirtualFile? {
        val parts = relativePath.split("/")
        var current: VirtualFile? = baseDir

        for (part in parts) {
            current = current?.findChild(part)
            if (current == null) break
        }

        return current
    }
}