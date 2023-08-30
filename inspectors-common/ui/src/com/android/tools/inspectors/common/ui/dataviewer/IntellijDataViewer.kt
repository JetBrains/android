/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.inspectors.common.ui.dataviewer

import com.android.tools.inspectors.common.ui.dataviewer.DataViewer.Style
import com.android.tools.inspectors.common.ui.dataviewer.DataViewer.Style.PRETTY
import com.android.tools.inspectors.common.ui.dataviewer.DataViewer.Style.RAW
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.util.ui.JBFont
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextArea
import javax.swing.SwingConstants
import kotlin.math.min

private const val RAW_VIEWER_MAX_STRING_LENGTH = 500
private val editorFactory = EditorFactory.getInstance()

class IntellijDataViewer private constructor(private val component: JComponent, private val style: Style) : DataViewer {
  override fun getComponent(): JComponent {
    return component
  }

  override fun getStyle(): Style {
    return style
  }

  companion object {
    /**
     * Create a data viewer that renders its content as is, without any attempt to clean it up.
     *
     *
     * Note: to prevent UI from being frozen by large text, the content will be truncated.
     */
    /**
     * Create an editable data viewer that renders its content as is, without any attempt to clean it up.
     *
     *
     * Note: to prevent UI from being frozen by large text, the content will be truncated.
     */
    @JvmOverloads
    fun createRawTextViewer(content: ByteArray, isEditable: Boolean = false): IntellijDataViewer {
      val textArea = JTextArea(content.decodeToString(0, min(content.size, RAW_VIEWER_MAX_STRING_LENGTH)))
      textArea.setLineWrap(true)
      textArea.isEditable = isEditable
      textArea.setBackground(null)
      textArea.setOpaque(false)
      return IntellijDataViewer(textArea, RAW)
    }

    /**
     * Create a data viewer that automatically formats the content it receives. In cases where it is
     * not able to do this, or it is not desirable to do this (e.g. plain text), it returns a
     * [RAW] viewer instead. Be sure to check [IntellijDataViewer.getStyle] if
     * this matters for your use-case.
     *
     * @param fileType An optional file type that can be associated with this content, which,
     * if provided, hints to the editor how it should format it.
     */
    fun createPrettyViewerIfPossible(
      project: Project,
      content: ByteArray,
      fileType: FileType?,
      formatted: Boolean,
      parentDisposable: Disposable
    ): IntellijDataViewer {
      return try {

        // We need to support documents with \r newlines in them (since network payloads can contain
        // data from any OS); however, Document will assert if it finds a \r as a line ending in its
        // content and the user will see a mysterious "NO PREVIEW" message without any information
        // on why. The Document class allows you to change a setting to allow \r, but this breaks
        // soft wrapping in the editor.
        val (document, style) = createDocument(project, content.decodeToString().replace("\r\n", "\n"), fileType, formatted)

        val editor = editorFactory.createViewer(document) as EditorEx
        editor.setCaretVisible(false)
        val settings = editor.settings
        settings.isLineNumbersShown = false
        settings.isLineMarkerAreaShown = false
        settings.isUseSoftWraps = true
        settings.setSoftMargins(emptyList())
        settings.isRightMarginShown = false
        settings.isFoldingOutlineShown = true
        CodeFoldingManager.getInstance(project).updateFoldRegions(editor)
        if (fileType != null) {
          editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType)
        }
        Disposer.register(parentDisposable) { editorFactory.releaseEditor(editor) }
        IntellijDataViewer(editor.component, style)
      } catch (e: Exception) {
        // Exceptions and AssertionErrors can be thrown by editorFactory.createDocument and editorFactory.createViewer
        createInvalidViewer()
      } catch (e: AssertionError) {
        createInvalidViewer()
      }
    }

    fun createInvalidViewer(): IntellijDataViewer {
      val component: JComponent = JLabel("No preview available", SwingConstants.CENTER)
      component.setFont(JBFont.label().asPlain())
      return IntellijDataViewer(component, Style.INVALID)
    }

    private fun createDocument(project: Project, content: String, fileType: FileType?, formatted: Boolean): Pair<Document, Style> {
      fun createRawDocument() = editorFactory.createDocument(content) to RAW

      val language = (fileType as? LanguageFileType)?.language ?: return createRawDocument()
      if (language === PlainTextLanguage.INSTANCE) {
        return createRawDocument()
      }

      val psiFile = PsiFileFactory.getInstance(project).createFileFromText(language, content) ?: return createRawDocument()

      if (formatted) {
        val processor = ReformatCodeProcessor(psiFile, false)
        processor.run()
      }
      val psiDocument = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return createRawDocument()
      return psiDocument to PRETTY
    }
  }
}
