package com.github.hungyanbin.pragent.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Watches git repository for changes by monitoring .git/HEAD and .git/index files.
 * Emits events when git status changes (commits, checkouts, etc.)
 */
class GitStatusWatcher(private val projectBasePath: String) {
    private val _gitChangeEvents = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val gitChangeEvents: SharedFlow<Unit> = _gitChangeEvents.asSharedFlow()

    private var watcherJob: Job? = null
    private var lastHeadModified: Long = 0
    private var lastIndexModified: Long = 0

    /**
     * Start watching for git changes
     */
    fun startWatching(scope: CoroutineScope) {
        stopWatching()

        val gitDir = File(projectBasePath, ".git")
        if (!gitDir.exists()) {
            return
        }

        val headFile = File(gitDir, "HEAD")
        val indexFile = File(gitDir, "index")

        // Initialize last modified times
        lastHeadModified = if (headFile.exists()) headFile.lastModified() else 0
        lastIndexModified = if (indexFile.exists()) indexFile.lastModified() else 0

        watcherJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    var changed = false

                    // Check HEAD file (changes on branch switch, checkout, etc.)
                    if (headFile.exists()) {
                        val currentHeadModified = headFile.lastModified()
                        if (currentHeadModified != lastHeadModified) {
                            lastHeadModified = currentHeadModified
                            changed = true
                        }
                    }

                    // Check index file (changes on commits, staging, etc.)
                    if (indexFile.exists()) {
                        val currentIndexModified = indexFile.lastModified()
                        if (currentIndexModified != lastIndexModified) {
                            lastIndexModified = currentIndexModified
                            changed = true
                        }
                    }

                    if (changed) {
                        _gitChangeEvents.tryEmit(Unit)
                    }

                    // Poll every 2 seconds
                    delay(2000)
                } catch (e: Exception) {
                    ErrorLogger.getInstance().logError("failed to watch git status: ${e.message}", e)
                    // Ignore errors and continue watching
                }
            }
        }
    }

    /**
     * Stop watching for git changes
     */
    fun stopWatching() {
        watcherJob?.cancel()
        watcherJob = null
    }
}