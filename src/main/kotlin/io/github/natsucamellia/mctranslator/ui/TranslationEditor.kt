package io.github.natsucamellia.mctranslator.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import io.github.natsucamellia.mctranslator.MyBundle
import io.github.natsucamellia.mctranslator.model.TranslationSet
import io.github.natsucamellia.mctranslator.services.TranslationService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

class TranslationEditor(private val project: Project, private val translationSet: TranslationSet) : JPanel(BorderLayout()) {

    private val tableModel = TranslationTableModel(translationSet)
    private val table = JBTable(tableModel).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        setShowGrid(false)
        intercellSpacing = Dimension(0, 0)
        TableSpeedSearch.installOn(this)
    }

    init {
        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    fun setTargetLanguage(langCode: String) {
        tableModel.setTargetLanguage(langCode)
    }

    fun setFilter(text: String) {
        tableModel.setFilter(text)
    }

    inner class TranslationTableModel(private val translationSet: TranslationSet) : AbstractTableModel() {
        private var targetLanguage: String = translationSet.languages.find { it != "en_us" } ?: "en_us"
        private var filterText: String = ""
        private var filteredEntries = translationSet.entries.toList()

        fun setTargetLanguage(langCode: String) {
            targetLanguage = langCode
            fireTableStructureChanged()
        }

        fun setFilter(text: String) {
            filterText = text
            applyFilter()
        }

        private fun applyFilter() {
            filteredEntries = if (filterText.isEmpty()) {
                translationSet.entries.toList()
            } else {
                translationSet.entries.filter { entry ->
                    entry.key.contains(filterText, ignoreCase = true) ||
                            (entry.translations["en_us"]?.contains(filterText, ignoreCase = true) == true) ||
                            (entry.translations[targetLanguage]?.contains(filterText, ignoreCase = true) == true)
                }
            }
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = filteredEntries.size
        override fun getColumnCount(): Int = 3

        override fun getColumnName(column: Int): String = when (column) {
            0 -> MyBundle.message("column.key")
            1 -> MyBundle.message("column.default")
            2 -> targetLanguage
            else -> ""
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val entry = filteredEntries[rowIndex]
            return when (columnIndex) {
                0 -> entry.key
                1 -> entry.translations["en_us"] ?: ""
                2 -> entry.translations[targetLanguage] ?: ""
                else -> null
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            return columnIndex > 0
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val entry = filteredEntries[rowIndex]
            val value = aValue as? String ?: return
            val langCode = when (columnIndex) {
                1 -> "en_us"
                2 -> targetLanguage
                else -> return
            }
            
            entry.translations[langCode] = value
            project.service<TranslationService>().updateTranslation(translationSet, entry.key, langCode, value)
        }
    }
}
