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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.android.tools.idea.logcat.util.MostRecentlyAddedSet
import com.android.tools.idea.logcat.util.ReschedulableTask
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EditorTextField
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ComboBoxEditor
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.KeyStroke
import javax.swing.event.PopupMenuEvent

private const val MAX_HISTORY_SIZE = 20
private const val APPLY_FILTER_DELAY_MS = 100L

/**
 * A text field for the filter.
 */
internal class FilterTextField(
  project: Project,
  logcatPresenter: LogcatPresenter,
  initialText: String,
  maxHistorySize: Int = MAX_HISTORY_SIZE,
) : ComboBox<String>() {

  private val textField = FilterEditorTextField(project, logcatPresenter, initialText)
  private val propertiesComponent: PropertiesComponent = PropertiesComponent.getInstance()
  private val history = MostRecentlyAddedSet<String>(maxHistorySize).apply {
    addAll(propertiesComponent.getValues(HISTORY_PROPERTY_NAME) ?: emptyArray())
    if (initialText.isNotEmpty()) {
      add(initialText)
    }
  }
  private val documentChangedListeners = mutableListOf<DocumentListener>()

  @TestOnly
  internal val notifyFilterChangedTask = ReschedulableTask(AndroidCoroutineScope(logcatPresenter, uiThread))

  var text: String
    get() = textField.text
    set(value) {
      textField.text = value
    }

  init {
    setEditable(true)
    setEditor(FilterComboBoxEditor(textField))

    setHistory()
    if (initialText.isEmpty()) {
      selectedItem = null
    }
    addPopupMenuListener(object : PopupMenuListenerAdapter() {
      override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
        addHistoryItem()
      }
    })

    textField.apply {
      addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          notifyFilterChangedTask.reschedule(APPLY_FILTER_DELAY_MS) {
            for (listener in documentChangedListeners) {
              listener.documentChanged(event)
            }
          }
        }
      })
      addFocusListener(object : FocusAdapter() {
        override fun focusLost(e: FocusEvent?) {
          addHistoryItem()
        }
      })
    }
  }

  @UiThread
  fun addDocumentListener(listener: DocumentListener) {
    documentChangedListeners.add(listener)
  }

  // Registering a KeyListener doesn't seem to work.
  @VisibleForTesting
  public override fun processKeyBinding(ks: KeyStroke, e: KeyEvent, condition: Int, pressed: Boolean): Boolean {
    if (e.keyCode == KeyEvent.VK_ENTER && pressed) {
      addHistoryItem()
    }

    return super.processKeyBinding(ks, e, condition, pressed)
  }

  internal fun getEditorEx() = textField.editor as EditorEx

  private fun addHistoryItem() {
    if (text.isNotEmpty()) {
      history.add(text)
      setHistory()
      propertiesComponent.setValues(HISTORY_PROPERTY_NAME, history.toTypedArray())
    }
  }

  private fun setHistory() {
    model = DefaultComboBoxModel(history.reversed().toTypedArray())
  }

  companion object {
    @VisibleForTesting
    internal const val HISTORY_PROPERTY_NAME = "logcatFilterHistory"
  }
}

private class FilterEditorTextField(project: Project, val logcatPresenter: LogcatPresenter, text: String)
  : EditorTextField(project, LogcatFilterFileType) {
  public override fun createEditor(): EditorEx {
    return super.createEditor().apply {
      putUserData(TAGS_PROVIDER_KEY, logcatPresenter)
      putUserData(PACKAGE_NAMES_PROVIDER_KEY, logcatPresenter)
    }
  }

  init {
    this.text = text
  }
}

private class FilterComboBoxEditor(private val textField: EditorTextField) : ComboBoxEditor {
  private val panel = object : BorderLayoutPanel() {
    override fun getBackground() = textField.background
  }

  private val clearButton = JLabel(AllIcons.Actions.Close)

  init {
    panel.addToCenter(textField)
    panel.addToRight(clearButton)

    clearButton.addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        clearButton.icon = AllIcons.Actions.CloseHovered
      }

      override fun mouseExited(e: MouseEvent) {
        clearButton.icon = AllIcons.Actions.Close
      }

      override fun mouseClicked(e: MouseEvent) {
        item = ""
      }
    })
  }

  override fun getEditorComponent() = panel

  override fun setItem(item: Any?) {
    textField.text = item as? String ?: ""
  }

  override fun getItem() = textField.text

  override fun selectAll() {
    textField.selectAll()
  }

  override fun addActionListener(listener: ActionListener?) {
  }

  override fun removeActionListener(listener: ActionListener?) {
  }
}