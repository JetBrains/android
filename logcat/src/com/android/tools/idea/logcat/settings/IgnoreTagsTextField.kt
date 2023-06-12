/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.settings

import com.android.tools.idea.logcat.LogcatToolWindowFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.ExpandableEditorSupport
import com.intellij.ui.TextFieldWithAutoCompletion.StringsCompletionProvider
import com.intellij.util.Function
import com.intellij.util.textCompletion.TextFieldWithCompletion
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent

/** An expandable text field containing tags.
 *
 * Autocomplete uses tags from all active Logcat panels.
 */
internal class IgnoreTagsTextField(tags: Set<String>) {
  private val project = ProjectManager.getInstance().defaultProject
  private val completionProvider = StringsCompletionProvider(loadTagsFromPanels(), null)
  val component = TextFieldWithCompletion(project, completionProvider, tags.joinToString(" "), true, true, false, true)
  private val expandedComponent = TextFieldWithCompletion(project, completionProvider, "", false, true, false, true)

  init {
    ExpandableSupport(component)
  }

  fun getIgnoredTags() = component.text.splitAndRemoveBlanks().toSet()

  private inner class ExpandableSupport(editor: EditorTextField)
    : ExpandableEditorSupport(editor, Function { it.splitAndRemoveBlanks() }, Function { it.joinToString(" ") }) {

    /**
     * This code is copied form [ExpandableEditorSupport.prepare] and fixes copyCaretPosition behavior.
     */
    @Suppress("UnstableApiUsage") // ExpandableSupport is marked @Internal
    override fun prepare(field: EditorTextField, onShow: Function<in String?, String?>): Content {
      val popup = createPopupEditor(field, onShow.`fun`(field.text)!!)
      val background = field.background
      popup.background = background
      popup.setOneLineMode(false)
      popup.preferredSize = Dimension(field.width, 5 * field.height)
      popup.addSettingsProvider { editor: EditorEx ->
        initPopupEditor(editor, background)
        copyCaretPosition(field.editor, editor)
      }
      return object : Content {
        override fun getContentComponent(): JComponent {
          return popup
        }

        override fun getFocusableComponent(): JComponent {
          return popup
        }

        override fun cancel(onHide: Function<in String, String>) {
          field.text = onHide.`fun`(popup.text)
          copyCaretPosition(popup.editor, field.editor)
          updateFieldFolding(field.editor as EditorEx)
        }
      }
    }

    override fun initPopupEditor(editor: EditorEx, background: Color?) {
      super.initPopupEditor(editor, background)
      editor.settings.isUseSoftWraps = true
    }

    override fun createPopupEditor(field: EditorTextField, text: String): EditorTextField {
      expandedComponent.text = text
      return expandedComponent
    }
  }
}

private fun loadTagsFromPanels(): List<String> =
  LogcatToolWindowFactory.logcatPresenters.flatMapTo(HashSet()) { it.getTags() }.filter { it.isNotBlank() }.map { "$it " }

private fun copyCaretPosition(source: Editor?, destination: Editor?) {
  val offset = source?.caretModel?.offset ?: return
  destination?.caretModel?.moveToOffset(offset)
}

private fun String.splitAndRemoveBlanks(): List<String> = split(' ').filter { it.isNotBlank() }
