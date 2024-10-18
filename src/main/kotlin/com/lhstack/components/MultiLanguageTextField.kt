package com.lhstack.components

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.LanguageTextField
import org.jetbrains.plugins.groovy.GroovyFileType

class MultiLanguageTextField(
    private val languageFileType: LanguageFileType,
    project: Project,
    value: String,
    private val isLineNumbersShown: Boolean = true,
    val viewer:Boolean = false
) :
    LanguageTextField(languageFileType.language, project, value, false), Disposable {


    companion object {
        fun groovy(project: Project, value: String, parent: Disposable, isViewer:Boolean = false): MultiLanguageTextField {
            return MultiLanguageTextField(GroovyFileType.GROOVY_FILE_TYPE, project, value,viewer = isViewer).apply {
                Disposer.register(parent, this)
            }
        }
    }

    init {
        border = null
    }

    override fun dispose() {
        editor?.let { EditorFactory.getInstance().releaseEditor(it) }
    }

    override fun createEditor(): EditorEx {
        val editorEx = EditorFactory.getInstance()
            .createEditor(document, project, fileType, this.viewer) as EditorEx
        editorEx.highlighter = HighlighterFactory.createHighlighter(project, fileType)
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(
            editorEx.document
        )
        if (psiFile != null) {
            DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, true)
//            if(psiFile is PsiJavaFile){
//                DaemonCodeAnalyzer.getInstance(project).setImportHintsEnabled(psiFile,true)
//            }else if(psiFile is GroovyFile){
//                DaemonCodeAnalyzer.getInstance(project).setImportHintsEnabled(psiFile,true)
//            }
        }
        editorEx.setBorder(null)
        val settings = editorEx.settings
        settings.additionalLinesCount = 0
        settings.additionalColumnsCount = 1
        settings.isLineNumbersShown = isLineNumbersShown
        settings.lineCursorWidth = 1
        settings.isLineMarkerAreaShown = false
        settings.setRightMargin(-1)
        return editorEx
    }
}