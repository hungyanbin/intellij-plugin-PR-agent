package com.github.hungyanbin.pragent.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GitCommandService(
    private val projectPath: String
) {

    suspend fun getCurrentBranch(): Result<Branch> = runCatching {
        val branchName = executeGitCommand(listOf("git", "rev-parse", "--abbrev-ref", "HEAD")).getOrThrow().trim()
        val hash = executeGitCommand(listOf("git", "rev-parse", "HEAD")).getOrThrow().trim()
        Branch(hash = hash, name = branchName)
    }

    suspend fun getParentBranch(currentBranch: Branch): Result<Branch> = runCatching {
        val output = executeGitCommand(listOf("git", "log", "--oneline", "--decorate")).getOrElse {
            return Result.success(Branch(hash = "", name = "main"))
        }
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
                        return Result.success(Branch(hash = hash, name = trimmedRef))
                    }
                }
            }
        }

        // Default fallback to main branch
        Branch(hash = "", name = "main")
    }

    suspend fun getCommitsSinceParent(currentBranch: Branch, parentBranch: Branch): Result<List<Commit>> = runCatching {
        val output = executeGitCommand(listOf("git", "log", "${parentBranch.name}..${currentBranch.name}", "--oneline", "--reverse"))
            .getOrThrow()

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
    }

    private suspend fun executeGitCommand(commands: List<String>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder(
                commands
            )
            processBuilder.directory(File(projectPath))
            val process = processBuilder.start()

            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() == 0) {
                Result.success(output)
            } else {
                Result.failure(RuntimeException("Git command failed: $errorOutput"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileDiff(fromHash: String, toHash: String): Result<String> {
        return executeGitCommand(listOf("git", "diff", "$fromHash..$toHash"))
    }

    suspend fun getRemoteUrl(): Result<String> = runCatching {
        val output = executeGitCommand(listOf("git", "remote", "get-url", "origin")).getOrThrow()
        output.trim().takeIf { it.isNotEmpty() } ?: throw RuntimeException("Remote URL is empty")
    }

    suspend fun pushCurrentBranchToRemote(branchName: String): Boolean {
        return executeGitCommand(listOf("git", "push", "-u", "origin", branchName))
            .onFailure { ErrorLogger.getInstance().logError("Failed to push current branch to remote", it) }
            .isSuccess
    }

    suspend fun checkBranchExistsOnRemote(branchName: String): Boolean {
        return executeGitCommand(listOf("git", "ls-remote", "--heads", "origin", branchName))
            .onFailure { ErrorLogger.getInstance().logError("Failed to check branch exists on remote", it) }
            .map { it.trim().isNotEmpty() }
            .getOrDefault(false)
    }

    suspend fun pushBranchToRemote(branchName: String): Boolean {
        return executeGitCommand(listOf("git", "push", "origin", branchName))
            .onFailure { ErrorLogger.getInstance().logError("Failed to push branch to remote", it) }
            .isSuccess
    }

    suspend fun getRecentBranches(limit: Int = 10): Result<List<String>> = runCatching {
        val output = executeGitCommand(listOf("git", "for-each-ref", "--sort=-committerdate", "refs/heads/", "--format=%(refname:short)", "--count=$limit"))
            .getOrThrow()
        output.trim().split("\n").filter { it.isNotEmpty() }
    }

    suspend fun getBranchByName(branchName: String): Result<Branch> = runCatching {
        val hash = executeGitCommand(listOf("git", "rev-parse", branchName)).getOrThrow().trim()
        Branch(hash = hash, name = branchName)
    }

    suspend fun isBranchAheadOf(compareBranch: String, baseBranch: String): Result<Boolean> = runCatching {
        val output = executeGitCommand(listOf("git", "rev-list", "--count", "$baseBranch..$compareBranch")).getOrThrow()
        val commitCount = output.trim().toIntOrNull() ?: 0
        commitCount > 0
    }

    suspend fun fetchRemote(): Boolean {
        return executeGitCommand(listOf("git", "fetch", "origin"))
            .onFailure { ErrorLogger.getInstance().logError("Failed to fetch remote", it) }
            .isSuccess
    }

    suspend fun isLocalBranchBehindRemote(branchName: String): Result<Boolean> = runCatching {
        val localHash = executeGitCommand(listOf("git", "rev-parse", branchName)).getOrThrow().trim()
        val remoteHash = executeGitCommand(listOf("git", "rev-parse", "origin/$branchName")).getOrThrow().trim()

        if (localHash == remoteHash) {
            return@runCatching false
        }

        // Check if local branch is behind remote
        val behindCount = executeGitCommand(listOf("git", "rev-list", "--count", "$branchName..origin/$branchName")).getOrThrow().trim().toIntOrNull() ?: 0
        behindCount > 0
    }

    suspend fun pullBranch(branchName: String): Boolean {
        return runCatching {
            val currentBranch = getCurrentBranch().getOrThrow()

            // Checkout to the branch we want to pull
            executeGitCommand(listOf("git", "checkout", branchName)).getOrThrow()

            // Pull with --ff-only to ensure we only fast-forward, avoiding merge commits
            executeGitCommand(listOf("git", "pull", "--ff-only", "origin", branchName)).getOrThrow()

            // Checkout back to the original branch
            executeGitCommand(listOf("git", "checkout", currentBranch.name)).getOrThrow()

            true
        }
            .onFailure { ErrorLogger.getInstance().logError("Failed to pull branch", it) }
            .getOrDefault(false)
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