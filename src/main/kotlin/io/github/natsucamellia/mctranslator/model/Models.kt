package io.github.natsucamellia.mctranslator.model

import com.intellij.openapi.vfs.VirtualFile

data class TranslationEntry(
    val key: String,
    val translations: MutableMap<String, String> = mutableMapOf()
)

data class TranslationSet(
    val modId: String,
    val entries: MutableList<TranslationEntry> = mutableListOf(),
    val languages: MutableSet<String> = mutableSetOf(),
    val files: MutableMap<String, VirtualFile> = mutableMapOf()
)
