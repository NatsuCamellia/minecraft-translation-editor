package io.github.natsucamellia.mctranslator.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import io.github.natsucamellia.mctranslator.MyBundle
import io.github.natsucamellia.mctranslator.model.TranslationSet
import io.github.natsucamellia.mctranslator.services.TranslationService
import io.github.natsucamellia.mctranslator.ui.TranslationEditor
import javax.swing.JPanel
import javax.swing.SwingConstants

class TranslationEditorToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val translationEditorToolWindow = TranslationEditorToolWindow(project)
        val content = ContentFactory.getInstance().createContent(translationEditorToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class TranslationEditorToolWindow(private val project: Project) {
        private val translationService = project.service<TranslationService>()
        private val mainPanel = SimpleToolWindowPanel(true, true)

        init {
            refresh()
        }

        fun getContent(): JPanel = mainPanel

        private fun refresh() {
            mainPanel.removeAll()
            val sets = translationService.getTranslationSets()
            if (sets.isEmpty()) {
                mainPanel.setContent(JBLabel(MyBundle.message("label.no_translations"), SwingConstants.CENTER))
            } else {
                val set = sets.first()
                setupEditor(set)
            }
            mainPanel.revalidate()
            mainPanel.repaint()
        }

        private fun setupEditor(set: TranslationSet) {
            val editor = TranslationEditor(project, set)
            val languages = set.languages.toTypedArray().sortedArray()
            val initialTarget = languages.find { it != "en_us" } ?: "en_us"
            editor.setTargetLanguage(initialTarget)

            val searchField = SearchTextField().apply {
                addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
                    override fun textChanged(e: javax.swing.event.DocumentEvent) {
                        editor.setFilter(text)
                    }
                })
            }

            val content = panel {
                row {
                    cell(searchField).align(Align.FILL)
                    label(MyBundle.message("label.target_language"))
                    val langComboBox = comboBox(languages.toList())
                        .applyToComponent {
                            selectedItem = initialTarget
                            addActionListener {
                                val selectedLang = selectedItem as? String
                                if (selectedLang != null) {
                                    editor.setTargetLanguage(selectedLang)
                                }
                            }
                        }
                    button(MyBundle.message("button.add_language")) {
                        val langCode = Messages.showInputDialog(
                            project,
                            MyBundle.message("dialog.add_language.message"),
                            MyBundle.message("dialog.add_language.title"),
                            null
                        )
                        if (!langCode.isNullOrBlank()) {
                            translationService.addLanguage(set, langCode)
                            refresh()
                        }
                    }
                    button(MyBundle.message("button.remove_language")) {
                        val selectedLang = langComboBox.component.selectedItem as? String ?: return@button
                        if (selectedLang == "en_us") return@button

                        val result = Messages.showOkCancelDialog(
                            project,
                            MyBundle.message("dialog.remove_language.message", selectedLang),
                            MyBundle.message("dialog.remove_language.title"),
                            Messages.getQuestionIcon()
                        )

                        if (result == Messages.OK) {
                            translationService.removeLanguage(set, selectedLang)
                            refresh()
                        }
                    }
                }
                row {
                    cell(editor).align(Align.FILL)
                }.resizableRow()
            }

            mainPanel.setContent(content)
        }
    }
}
