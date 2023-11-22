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
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Banner.WARNING_BACKGROUND
import javax.swing.BorderFactory
import javax.swing.GroupLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants
import kotlin.math.min

private const val RAW_VIEWER_MAX_STRING_LENGTH = 500
private const val MAX_SIZE_TO_FORMAT = 100_000
private val logger = Logger.getInstance(IntellijDataViewer::class.java)

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
      bytes: ByteArray,
      fileType: FileType?,
      formatted: Boolean,
      parentDisposable: Disposable,
      maxSizeToFormat: Int = MAX_SIZE_TO_FORMAT,
    ): IntellijDataViewer {
      return try {
        val content = bytes.toContent()
        var showNotification = false
        var style = PRETTY

        val virtualFile = when {
          !formatted || fileType == null || fileType == PlainTextFileType.INSTANCE -> createFile(fileType, content).also { style = RAW }
          content.length > maxSizeToFormat -> createFile(fileType, content).also { showNotification = true }
          else -> createFormattedFile(project, fileType, content)
        }

        val textEditor = PsiAwareTextEditorProvider().createEditor(project, virtualFile) as TextEditor
        configureEditor(textEditor.editor as EditorEx)
        CodeFoldingManager.getInstance(project).updateFoldRegions(textEditor.editor)
        Disposer.register(parentDisposable, textEditor)

        val component = if (showNotification) getComponentWithNotification(textEditor.editor) else textEditor.editor.component
        IntellijDataViewer(component, style)

      } catch (e: Throwable) {
        // Exceptions and AssertionErrors can be thrown by editorFactory.createDocument and editorFactory.createViewer
        logger.warn("Failed to create pretty viewer", e)
        createInvalidViewer()
      }
    }

    fun createInvalidViewer(): IntellijDataViewer {
      val component: JComponent = JLabel("No preview available", SwingConstants.CENTER)
      component.setFont(JBFont.label().asPlain())
      return IntellijDataViewer(component, Style.INVALID)
    }
  }
}

/** Wrap the `Editor.component` with a panel that adds a notification banner above it */
private fun getComponentWithNotification(editor: Editor): JComponent {
  val banner = EditorNotificationPanel(WARNING_BACKGROUND).apply {
    border = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 1, 1, 0, 1), border)
    text = "Response was not auto-formatted because it was too large."
    createActionLabel("Reformat now") {
      val project = editor.project ?: return@createActionLabel logger.warn("Editor with no project")
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        ?: return@createActionLabel logger.warn("Missing PsiFile")
      reformatPsiFile(project, psiFile)
      isVisible = false
    }
  }

  return JPanel(null).apply {
    layout = GroupLayout(this).apply {
      setVerticalGroup(
        createSequentialGroup()
          .addComponent(banner)
          .addComponent(editor.component)
      )
      setHorizontalGroup(
        createParallelGroup(GroupLayout.Alignment.CENTER)
          .addComponent(banner)
          .addComponent(editor.component)
      )
    }
  }
}

private fun createFile(fileType: FileType?, text: String) = LightVirtualFile("tmp-file", fileType, text)

private fun createFormattedFile(project: Project, fileType: FileType, content: String): VirtualFile {
  val psiFileFactory = PsiFileFactory.getInstance(project)
  val psiFile = psiFileFactory.createFileFromText("file", fileType, content)
  reformatPsiFile(project, psiFile)
  return psiFile.virtualFile ?: createFile(fileType, psiFile.text)
}

private fun reformatPsiFile(project: Project, psiFile: PsiFile) {
  val runnable = Runnable { CodeStyleManager.getInstance(project).reformat(psiFile) }
  val application = ApplicationManager.getApplication()
  if (application.isWriteAccessAllowed) {
    runnable.run()
  } else {
    WriteCommandAction.runWriteCommandAction(project, runnable)
  }
}

/**
 * We need to support documents with \r newlines in them (since network payloads can contain
 * data from any OS); however, Document will assert if it finds a \r as a line ending in its
 * content and the user will see a mysterious "NO PREVIEW" message without any information
 * on why. The Document class allows you to change a setting to allow \r, but this breaks
 * soft wrapping in the editor.
 */
private fun ByteArray.toContent() = decodeToString().replace("\r\n", "\n")

private fun configureEditor(editor: EditorEx) {
  editor.setCaretVisible(false)
  val settings = editor.settings
  editor.isViewer = true
  settings.isLineNumbersShown = false
  settings.isLineMarkerAreaShown = false
  settings.isUseSoftWraps = true
  settings.setSoftMargins(emptyList())
  settings.isRightMarginShown = false
  settings.isFoldingOutlineShown = true
}
