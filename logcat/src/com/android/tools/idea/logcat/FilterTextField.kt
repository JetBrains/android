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
import com.android.tools.idea.logcat.filters.LogcatFilterParser
import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.AndroidProjectDetectorImpl
import com.android.tools.idea.logcat.util.LogcatUsageTracker
import com.android.tools.idea.logcat.util.ReschedulableTask
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.Type.FILTER_ADDED_TO_HISTORY
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JLabel

private const val MAX_HISTORY_SIZE = 20
private const val APPLY_FILTER_DELAY_MS = 100L

/**
 * A text field for the filter.
 */
internal class FilterTextField(
  project: Project,
  logcatPresenter: LogcatPresenter,
  private val filterParser: LogcatFilterParser,
  initialText: String,
  androidProjectDetector: AndroidProjectDetector = AndroidProjectDetectorImpl(),
  private val maxHistorySize: Int = MAX_HISTORY_SIZE,
) : BorderLayoutPanel() {
  @TestOnly
  internal val notifyFilterChangedTask = ReschedulableTask(AndroidCoroutineScope(logcatPresenter, uiThread))
  private val propertiesComponent: PropertiesComponent = PropertiesComponent.getInstance()
  private val documentChangedListeners = mutableListOf<DocumentListener>()
  private val textField = FilterEditorTextField(project, logcatPresenter, initialText, androidProjectDetector)
  private val historyButton = InlineButton(AllIcons.Actions.SearchWithHistory)
  private val clearButton = InlineButton(AllIcons.Actions.Close)

  var text: String
    get() = textField.text
    set(value) {
      textField.text = value
    }

  init {
    addToLeft(historyButton)
    addToCenter(textField)
    addToRight(clearButton)

    // Set a border around the text field and buttons. See EditorTextField#setBorder()
    border = BorderFactory.createCompoundBorder(UIUtil.getTextFieldBorder(),  JBUI.Borders.empty(2, 2, 2, 2))

    historyButton.apply {
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          showPopup()
        }
      })
      // The history icon needs a margin. These values make it look the same as the "Find in files" dialog for example.
      border = JBUI.Borders.empty(0, 5, 0, 4)
    }

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
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          if (e.keyCode == KeyEvent.VK_ENTER) {
            e.consume()
            addToHistory()
          }
        }
      })

      addFocusListener(object : FocusAdapter() {
        override fun focusLost(e: FocusEvent?) {
          addToHistory()
        }
      })
    }

    clearButton.apply {
      addMouseListener(object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
          clearButton.icon = AllIcons.Actions.CloseHovered
        }

        override fun mouseExited(e: MouseEvent) {
          clearButton.icon = AllIcons.Actions.Close
        }

        override fun mouseClicked(e: MouseEvent) {
          textField.text = ""
        }
      })
    }
  }

  @UiThread
  fun addDocumentListener(listener: DocumentListener) {
    documentChangedListeners.add(listener)
  }

  @TestOnly
  internal fun getEditorEx() = textField.editor as EditorEx

  @UiThread
  private fun showPopup() {
    addToHistory()
    JBPopupFactory.getInstance().createPopupChooserBuilder(propertiesComponent.getValues(HISTORY_PROPERTY_NAME)?.asList() ?: emptyList())
      .setMovable(false)
      .setRequestFocus(true)
      .setItemChosenCallback { textField.text = it }
      .createPopup()
      .showUnderneathOf(this)
  }

  private fun addToHistory() {
    val text = textField.text
    if (text.isEmpty()) {
      return
    }
    val history = propertiesComponent.getValues(HISTORY_PROPERTY_NAME)?.asList()?.toMutableList() ?: mutableListOf()
    history.remove(text)
    history.add(0, text)
    if (history.size > maxHistorySize) {
      history.removeLast()
    }
    propertiesComponent.setValues(HISTORY_PROPERTY_NAME, history.toTypedArray())
    LogcatUsageTracker.log(
      LogcatUsageEvent.newBuilder()
        .setType(FILTER_ADDED_TO_HISTORY)
        .setLogcatFilter(filterParser.getUsageTrackingEvent(text)))
  }

  private inner class FilterEditorTextField(
    project: Project,
    private val logcatPresenter: LogcatPresenter,
    text: String,
    private val androidProjectDetector: AndroidProjectDetector,
  ) : EditorTextField(project, LogcatFilterFileType) {
    public override fun createEditor(): EditorEx {
      return super.createEditor().apply {
        putUserData(TAGS_PROVIDER_KEY, logcatPresenter)
        putUserData(PACKAGE_NAMES_PROVIDER_KEY, logcatPresenter)
        putUserData(AndroidProjectDetector.KEY, androidProjectDetector)
        // Remove the line border but preserve the inner margins. See EditorTextField#setBorder()
        setBorder(JBUI.Borders.empty(2, 2, 2, 2))
      }
    }

    init {
      this.text = text
    }
  }

  private inner class InlineButton(icon: Icon) : JLabel(icon) {
    init {
      isOpaque = true
      background = textField.background
    }
  }

  companion object {
    @VisibleForTesting
    internal const val HISTORY_PROPERTY_NAME = "logcatFilterHistory"
  }
}
