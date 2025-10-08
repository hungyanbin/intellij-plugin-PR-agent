package com.github.hungyanbin.intellijpluginpragent.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GitCommandService(
    private val projectPath: String
) {

    suspend fun getCurrentBranch(): Branch {
        val branchName = executeGitCommand(listOf("git", "rev-parse", "--abbrev-ref", "HEAD")).trim()
        val hash = executeGitCommand(listOf("git", "rev-parse", "HEAD")).trim()
        return Branch(hash = hash, name = branchName)
    }

    suspend fun getParentBranch(currentBranch: Branch): Branch {
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

    suspend fun getCommitsSinceParent(currentBranch: Branch, parentBranch: Branch): List<Commit> {
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

    private suspend fun executeGitCommand(commands: List<String>): String = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(
            commands
        )
        processBuilder.directory(File(projectPath))
        val process = processBuilder.start()

        val output = process.inputStream.bufferedReader().readText()
        val errorOutput = process.errorStream.bufferedReader().readText()
        process.waitFor()

        if (process.exitValue() == 0) {
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

    suspend fun getRecentBranches(limit: Int = 10): List<String> {
        return try {
            val output = executeGitCommand(listOf("git", "for-each-ref", "--sort=-committerdate", "refs/heads/", "--format=%(refname:short)", "--count=$limit"))
            output.trim().split("\n").filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getBranchByName(branchName: String): Branch {
        val hash = executeGitCommand(listOf("git", "rev-parse", branchName)).trim()
        return Branch(hash = hash, name = branchName)
    }

    suspend fun isBranchAheadOf(compareBranch: String, baseBranch: String): Boolean {
        return try {
            val output = executeGitCommand(listOf("git", "rev-list", "--count", "$baseBranch..$compareBranch"))
            val commitCount = output.trim().toIntOrNull() ?: 0
            commitCount > 0
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