package com.github.hungyanbin.pragent.utils

import com.intellij.openapi.application.ApplicationManager

fun runOnUI(block: () -> Unit) {
    ApplicationManager.getApplication().invokeLater {
        block()
    }
}