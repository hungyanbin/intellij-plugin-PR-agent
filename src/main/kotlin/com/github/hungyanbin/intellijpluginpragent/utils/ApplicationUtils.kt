package com.github.hungyanbin.intellijpluginpragent.utils

import com.intellij.openapi.application.ApplicationManager

fun runOnUI(block: () -> Unit) {
    runOnUI {
        block()
    }
}