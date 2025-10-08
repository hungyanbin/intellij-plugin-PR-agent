package com.github.hungyanbin.intellijpluginpragent.toolWindow

import java.io.File

class GitCommandHelper(
    private val projectPath: String
) {

    suspend fun getBranchHistory(): BranchHistory {
        val currentBranch = getCurrentBranch()
        val parentBranch = getParentBranch(currentBranch)
        val commits = getCommitsSinceParent(currentBranch, parentBranch)

        return BranchHistory(
            commits = commits,
            currentBranch = currentBranch,
            parentBranch = parentBranch
        )
    }

    private suspend fun getCurrentBranch(): Branch {
        val branchName = executeGitCommand(listOf("git", "rev-parse", "--abbrev-ref", "HEAD")).trim()
        val hash = executeGitCommand(listOf("git", "rev-parse", "HEAD")).trim()
        return Branch(hash = hash, name = branchName)
    }

    private suspend fun getParentBranch(currentBranch: Branch): Branch {
        return try {
            val output = executeGitCommand(listOf("git", "log", "--oneline", "--decorate"))
            val lines = output.trim().split("\n")

            // Parse the log output to find branch references
            for (line in lines) {
                // Look for branch names in parentheses, excluding origin/ and HEAD
                val branchMatch = Regex("\\(([^)]+)\\)").find(line)
                if (branchMatch != null) {
                    val branchRefs = branchMatch.groupValues[1].split(",")
                    for (branchRef in branchRefs) {
                        val trimmedRef = branchRef.trim()
                        if (!trimmedRef.contains("HEAD") &&
                            !trimmedRef.startsWith("origin/") &&
                            trimmedRef != currentBranch.name &&
                            trimmedRef.isNotEmpty()) {
                            val hash = line.split(" ")[0] // Get hash from start of line
                            return Branch(hash = hash, name = trimmedRef)
                        }
                    }
                }
            }

            // Default fallback to main branch
            Branch(hash = "", name = "main")
        } catch (e: Exception) {
            Branch(hash = "", name = "main")
        }
    }

    private suspend fun getCommitsSinceParent(currentBranch: Branch, parentBranch: Branch): List<Commit> {
        return try {
            val output = executeGitCommand(listOf("git", "log", "${parentBranch.name}..${currentBranch.name}", "--oneline", "--reverse"))
            if (output.trim().isEmpty()) {
                emptyList()
            } else {
                output.trim().split("\n").map { line ->
                    val parts = line.split(" ", limit = 2)
                    Commit(
                        hash = parts[0],
                        description = if (parts.size > 1) parts[1] else ""
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun executeGitCommand(commands: List<String>): String {
        val processBuilder = ProcessBuilder(
            commands
        )
        processBuilder.directory(File(projectPath))
        val process = processBuilder.start()

        val output = process.inputStream.bufferedReader().readText()
        val errorOutput = process.errorStream.bufferedReader().readText()
        process.waitFor()

        return if (process.exitValue() == 0) {
            output
        } else {
            throw RuntimeException("Error ${errorOutput}")
        }
    }

    suspend fun getFileDiff(fromHash: String, toHash: String): String {
        return try {
            executeGitCommand(listOf("git", "diff", "${fromHash}..${toHash}"))
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun getRemoteUrl(): String? {
        return try {
            val output = executeGitCommand(listOf("git", "remote", "get-url", "origin"))
            output.trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun pushCurrentBranchToRemote(branchName: String): Boolean {
        return try {
            executeGitCommand(listOf("git", "push", "-u", "origin", branchName))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkBranchExistsOnRemote(branchName: String): Boolean {
        return try {
            val output = executeGitCommand(listOf("git", "ls-remote", "--heads", "origin", branchName))
            output.trim().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun pushBranchToRemote(branchName: String): Boolean {
        return try {
            executeGitCommand(listOf("git", "push", "origin", branchName))
            true
        } catch (e: Exception) {
            false
        }
    }
}

data class BranchHistory(
    val commits: List<Commit>,
    val currentBranch: Branch,
    val parentBranch: Branch
)

data class Commit(
    val hash: String,
    val description: String
)

data class Branch(
    val hash: String,
    val name: String
)