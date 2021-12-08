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
package com.android.tools.idea.logcat

import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.android.tools.idea.logcat.filters.parser.LogcatFilterLanguage
import com.android.tools.idea.logcat.util.MostRecentlyAddedSet
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.ComboboxEditorTextField
import com.intellij.ui.EditorComboBox
import com.intellij.ui.PopupMenuListenerAdapter
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import javax.swing.event.PopupMenuEvent

private const val MAX_HISTORY_SIZE = 20

/**
 * A text field for the filter.
 *
 * TODO(aalbert): Add `Clear Text` button (x).
 */
internal class FilterTextField(
  project: Project,
  private val logcatPresenter: LogcatPresenter,
  initialText: String,
  maxHistorySize: Int = MAX_HISTORY_SIZE,
) : EditorComboBox(createDocument(project, initialText), project, LogcatFilterFileType) {

  private val propertiesComponent: PropertiesComponent = PropertiesComponent.getInstance()
  private val history = MostRecentlyAddedSet<String>(maxHistorySize).apply {
    addAll(propertiesComponent.getValues(HISTORY_PROPERTY_NAME) ?: emptyArray())
    if (initialText.isNotEmpty()) {
      add(initialText)
    }
  }

  init {
    setHistory(history.reversed().toTypedArray())
    if (initialText.isEmpty()) {
      selectedItem = null
    }
    addPopupMenuListener(object : PopupMenuListenerAdapter() {
      override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
        addHistoryItem()
      }
    })
  }

  public override fun createEditorTextField(
    document: Document,
    project: Project,
    fileType: FileType,
    isViewer: Boolean,
  ): ComboboxEditorTextField = EditorTextFieldWithUserData(document, project, logcatPresenter)

  // Registering a KeyListener doesn't seem to work.
  @VisibleForTesting
  public override fun processKeyBinding(ks: KeyStroke, e: KeyEvent, condition: Int, pressed: Boolean): Boolean {
    if (e.keyCode == KeyEvent.VK_ENTER && pressed) {
      addHistoryItem()
    }

    return super.processKeyBinding(ks, e, condition, pressed)
  }

  private fun addHistoryItem() {
    if (text.isNotEmpty()) {
      history.add(text)
      setHistory(history.reversed().toTypedArray())
      propertiesComponent.setValues(HISTORY_PROPERTY_NAME, history.toTypedArray())
    }
  }

  companion object {
    @VisibleForTesting
    internal const val HISTORY_PROPERTY_NAME = "logcatFilterHistory"
  }
}

private class EditorTextFieldWithUserData(document: Document, project: Project, val logcatPresenter: LogcatPresenter)
  : ComboboxEditorTextField(document, project, LogcatFilterFileType) {
  public override fun createEditor(): EditorEx {
    return super.createEditor().apply {
      putUserData(TAGS_PROVIDER_KEY, logcatPresenter)
      putUserData(PACKAGE_NAMES_PROVIDER_KEY, logcatPresenter)
    }
  }
}

private fun createDocument(project: Project, text: String): Document {
  val psiFile: PsiFile = PsiFileFactory.getInstance(project).createFileFromText(LogcatFilterLanguage, text)
  return PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: throw IllegalStateException("Should not happen")
}
