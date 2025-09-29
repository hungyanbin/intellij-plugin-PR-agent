package com.github.hungyanbin.intellijpluginpragent.toolWindow

import java.io.File

class GitCommandHelper(
    private val projectPath: String
) {

    suspend fun getBranchHistory(): BranchHistory {
        println("projectPath: $projectPath")
        val currentBranch = getCurrentBranch()
        println("currentBranch: $currentBranch")
        val parentBranch = getParentBranch(currentBranch)
        println("parentBranch: $parentBranch")
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

    //66aa3429be (HEAD -> feat_multipage_player_preview_14, origin/feat_multipage_player_preview_14) feat: stop the player when opening the duration picker
    //1d4b129af3 feat: introduce PageAnimationFactory.Config to generate evaluator with different constraint
    //366dd23d48 feat: calculate evaluatorMap if collage has pageDuration
    //028e16fcf6 refactor: refactor PageAnimationFactory
    //94c7ad4dad feat: add unit test for PageAnimationFactory
    //f5b7a09978 (feat_multipage_player_preview_13) fix: MultiPagePlayer is not being recycled when activity is destroyed
    //72c0be0eb0 feat: apply page duration to json after change from duration picker
    //bae28f3799 feat: make collage model behaviors correct with new pageDuration property
    //1f0ca042a4 feat: implement command for pageDuration
    //522df001f2 feat: Add page_duration to collage structure
    private suspend fun getParentBranch(currentBranch: Branch): Branch {
        return try {
            val output = executeGitCommand(listOf("git", "log", "--oneline", "--decorate"))
            val lines = output.trim().split("\n")

            // Parse the log output to find branch references
            for (line in lines) {
                // Look for branch names in parentheses, excluding origin/ and HEAD
                println("line: $line")
                val branchMatch = Regex("\\(([^,)]+)\\)").findAll(line)
                for (match in branchMatch) {
                    println("match: $match")
                    val branchRef = match.groupValues[1]
                    if (!branchRef.contains("HEAD") &&
                        !branchRef.contains("origin/") &&
                        branchRef != currentBranch.name &&
                        branchRef.trim().isNotEmpty()) {
                        val parentBranchName = branchRef.trim()
                        val hash = line.split(" ")[0] // Get hash from start of line
                        return Branch(hash = hash, name = parentBranchName)
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