package com.github.hungyanbin.pragent.service

import com.intellij.util.xmlb.annotations.XCollection

/**
 * Immutable state class for persisting error logs using SerializablePersistentStateComponent.
 * This class is automatically serialized/deserialized to XML.
 */
data class ErrorLoggerState(
    @XCollection(style = XCollection.Style.v2)
    @JvmField val errorLogs: List<ErrorLogEntry> = emptyList()
)