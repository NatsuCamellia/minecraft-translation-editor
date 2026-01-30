package io.github.natsucamellia.mctranslator.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import io.github.natsucamellia.mctranslator.model.TranslationEntry
import io.github.natsucamellia.mctranslator.model.TranslationSet

@Service(Service.Level.PROJECT)
class TranslationService(private val project: Project) {

    fun getTranslationSets(): List<TranslationSet> {
        val sets = mutableMapOf<String, TranslationSet>()

        val mainFiles = FilenameIndex.getVirtualFilesByName("en_us.json", GlobalSearchScope.projectScope(project)) +
                FilenameIndex.getVirtualFilesByName("en_us.lang", GlobalSearchScope.projectScope(project))

        for (file in mainFiles) {
            val langDir = file.parent ?: continue
            if (langDir.name != "lang") continue
            val assetsDir = langDir.parent ?: continue
            val modId = assetsDir.name

            val set = sets.getOrPut(modId) { TranslationSet(modId) }
            
            langDir.children.forEach { langFile ->
                if (langFile.extension == "json" || langFile.extension == "lang") {
                    val langCode = langFile.nameWithoutExtension
                    set.languages.add(langCode)
                    set.files[langCode] = langFile
                    parseFile(langFile, langCode, set)
                }
            }
        }

        return sets.values.toList()
    }

    private fun parseFile(file: VirtualFile, langCode: String, set: TranslationSet) {
        val content = String(file.contentsToByteArray(), file.charset)
        if (file.extension == "json") {
            parseJson(content, langCode, set)
        } else {
            parseLang(content, langCode, set)
        }
    }

    private fun parseJson(content: String, langCode: String, set: TranslationSet) {
        val regex = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"")
        regex.findAll(content).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            addTranslation(set, key, langCode, value)
        }
    }

    private fun parseLang(content: String, langCode: String, set: TranslationSet) {
        content.lines().forEach { line ->
            if (line.contains("=") && !line.startsWith("#")) {
                val parts = line.split("=", limit = 2)
                val key = parts[0].trim()
                val value = parts[1].trim()
                addTranslation(set, key, langCode, value)
            }
        }
    }

    fun updateTranslation(set: TranslationSet, key: String, langCode: String, newValue: String) {
        val file = set.files[langCode] ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runWriteCommandAction
            val text = document.text
            val newText = if (file.extension == "json") {
                updateJsonText(text, key, newValue)
            } else {
                updateLangText(text, key, newValue)
            }
            document.setText(newText)

            PsiDocumentManager.getInstance(project).getPsiFile(document)?.let { psiFile ->
                CodeStyleManager.getInstance(project).reformat(psiFile)
            }

            FileDocumentManager.getInstance().saveDocument(document)
        }
    }

    fun addLanguage(set: TranslationSet, langCode: String) {
        val enFile = set.files["en_us"] ?: return
        val dir = enFile.parent ?: return
        val extension = enFile.extension
        val fileName = "$langCode.$extension"

        if (dir.findChild(fileName) != null) return

        WriteCommandAction.runWriteCommandAction(project) {
            val newFile = dir.createChildData(this, fileName)
            val initialContent = if (extension == "json") "{}" else ""
            newFile.setBinaryContent(initialContent.toByteArray())

            set.languages.add(langCode)
            set.files[langCode] = newFile
        }
    }

    fun removeLanguage(set: TranslationSet, langCode: String) {
        if (langCode == "en_us") return
        val file = set.files[langCode] ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            file.delete(this)
            set.languages.remove(langCode)
            set.files.remove(langCode)
        }
    }

    private fun updateJsonText(text: String, key: String, newValue: String): String {
        val regex = Regex("(\"$key\"\\s*:\\s*\")([^\"]*)(\")")
        if (regex.containsMatchIn(text)) {
            return regex.replaceFirst(text, "$1$newValue$3")
        } else {
            val lastBraceIndex = text.lastIndexOf('}')
            if (lastBraceIndex != -1) {
                val before = text.substring(0, lastBraceIndex)
                val after = text.substring(lastBraceIndex)
                val comma = if (before.trim().endsWith("{")) "" else ","
                return "$before$comma\n  \"$key\": \"$newValue\"\n$after"
            }
            return text
        }
    }

    private fun updateLangText(text: String, key: String, newValue: String): String {
        val lines = text.lines().toMutableList()
        val index = lines.indexOfFirst { it.startsWith("$key=") || it.startsWith("$key =") }
        if (index != -1) {
            lines[index] = "$key=$newValue"
        } else {
            // Append if not found?
            lines.add("$key=$newValue")
        }
        return lines.joinToString("\n")
    }

    private fun addTranslation(set: TranslationSet, key: String, langCode: String, value: String) {
        var entry = set.entries.find { it.key == key }
        if (entry == null) {
            entry = TranslationEntry(key)
            set.entries.add(entry)
        }
        entry.translations[langCode] = value
    }
}
