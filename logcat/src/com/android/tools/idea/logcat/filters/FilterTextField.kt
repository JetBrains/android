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
package com.android.tools.idea.logcat.filters

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.PACKAGE_NAMES_PROVIDER_KEY
import com.android.tools.idea.logcat.TAGS_PROVIDER_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.AndroidProjectDetectorImpl
import com.android.tools.idea.logcat.util.LogcatUsageTracker
import com.android.tools.idea.logcat.util.ReschedulableTask
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.Type.FILTER_ADDED_TO_HISTORY
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.CollectionListModel
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Color
import java.awt.Component
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import kotlin.math.min

private const val APPLY_FILTER_DELAY_MS = 100L

// TODO(b/220381322): Use icons from UX instead of these placeholders. The temporary icons need to be scaled down.
private val FAVORITE_ICON = AllIcons.Ide.FeedbackRating.scale()
private val FAVORITE_ON_ICON = AllIcons.Ide.FeedbackRatingOn.scale()
private val FAVORITE_FOCUSED_ICON = AllIcons.Ide.FeedbackRatingFocused.scale()
private val FAVORITE_FOCUSED_ON_ICON = AllIcons.Ide.FeedbackRatingFocusedOn.scale()

/**
 * A text field for the filter.
 */
internal class FilterTextField(
  project: Project,
  logcatPresenter: LogcatPresenter,
  private val filterParser: LogcatFilterParser,
  initialText: String,
  androidProjectDetector: AndroidProjectDetector = AndroidProjectDetectorImpl(),
) : BorderLayoutPanel(), FilterTextComponent {
  private val filterHistory = AndroidLogcatFilterHistory.getInstance()

  @TestOnly
  internal val notifyFilterChangedTask = ReschedulableTask(AndroidCoroutineScope(logcatPresenter, uiThread))
  private val documentChangedListeners = mutableListOf<DocumentListener>()
  private val textField = FilterEditorTextField(project, logcatPresenter, androidProjectDetector)
  private val historyButton = InlineButton(StudioIcons.Logcat.Toolbar.FILTER_HISTORY)
  private val clearButton = InlineButton(AllIcons.Actions.Close)
  private val favoriteButton = InlineButton(FAVORITE_ICON)

  private var isFavorite: Boolean = false
    set(value) {
      field = value
      favoriteButton.icon = if (isFavorite) FAVORITE_ON_ICON else FAVORITE_ICON
    }

  override var text: String
    get() = textField.text
    set(value) {
      textField.text = value
    }

  override val component: Component get() = this

  init {
    text = initialText
    isFavorite = filterHistory.favorites.contains(text)

    addToLeft(historyButton)
    addToCenter(textField)
    addToRight(JPanel().apply {
      background = textField.background
      isOpaque = true
      add(clearButton)
      add(favoriteButton)
    })

    // Set a border around the text field and buttons.
    // Using FilterTextFieldBorder (which is just a DarculaTextBorder) alone doesn't seem to work. It seems to need CompoundBorder.
    border = BorderFactory.createCompoundBorder(FilterTextFieldBorder(), JBUI.Borders.empty(1, 1, 1, 1))

    historyButton.apply {
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          showPopup()
        }
      })
      // The history icon needs a margin. These values make it look the same as the "Find in files" dialog for example.
      border = JBUI.Borders.empty(0, 5, 0, 4)
    }

    textField.apply {
      addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          isFavorite = false
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
      // The text field needs to be moved an extra pixel down to appear correctly.
      border = JBUI.Borders.customLine(background, 1, 0, 0, 0)
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

    favoriteButton.apply {
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          addToHistory()
          isFavorite = !isFavorite
          mouseEntered(e) // Setter for isFavorite will set the wrong icon (not hovered)
        }

        override fun mouseEntered(e: MouseEvent) {
          icon = if (isFavorite) FAVORITE_FOCUSED_ON_ICON else FAVORITE_FOCUSED_ICON
        }

        override fun mouseExited(e: MouseEvent) {
          icon = if (isFavorite) FAVORITE_ON_ICON else FAVORITE_ICON
        }
      })
    }
  }

  @UiThread
  override fun addDocumentListener(listener: DocumentListener) {
    documentChangedListeners.add(listener)
  }

  @TestOnly
  internal fun getEditorEx() = textField.editor as EditorEx

  @UiThread
  private fun showPopup() {
    addToHistory()
    PopupChooserBuilder(HistoryList(filterHistory))
      .setMovable(false)
      .setRequestFocus(true)
      .setItemChosenCallback {
        text = it.filter
        isFavorite = it.isFavorite
      }
      .setSelectedValue(FilterHistoryItem(text, isFavorite), true)
      .createPopup()
      .showUnderneathOf(this)
  }

  private fun addToHistory() {
    val text = textField.text
    if (text.isEmpty()) {
      return
    }
    filterHistory.add(text, isFavorite)
    // TODO(aalbert): Add isFavorite to tracking
    LogcatUsageTracker.log(
      LogcatUsageEvent.newBuilder()
        .setType(FILTER_ADDED_TO_HISTORY)
        .setLogcatFilter(filterParser.getUsageTrackingEvent(text)))
  }

  private inner class FilterTextFieldBorder : DarculaTextBorder() {
    override fun isFocused(c: Component?): Boolean = textField.editor?.contentComponent?.hasFocus() == true
  }

  private inner class FilterEditorTextField(
    project: Project,
    private val logcatPresenter: LogcatPresenter,
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

  private class HistoryList(filterHistory: AndroidLogcatFilterHistory) : JBList<FilterHistoryItem>() {
    init {
      val listModel = CollectionListModel(
        filterHistory.favorites.map { FilterHistoryItem(it, true) } + filterHistory.nonFavorites.map { FilterHistoryItem(it, false) })
      model = listModel
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          if (e.keyCode == KeyEvent.VK_DELETE) {
            filterHistory.remove(selectedValue.filter)
            val index = selectedIndex
            listModel.remove(index)
            selectedIndex = min(index, model.size - 1)
          }
        }
      })
      this.cellRenderer = HistoryListCellRenderer()
    }
  }

  private class HistoryListCellRenderer : ListCellRenderer<FilterHistoryItem> {
    override fun getListCellRendererComponent(
      list: JList<out FilterHistoryItem>,
      value: FilterHistoryItem,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
    ): Component {
      return BorderLayoutPanel().apply {
        val isFavorite = value.isFavorite
        addToLeft(JLabel(value.filter).apply {
          border = JBUI.Borders.empty(0, 3, 0, if (isFavorite) 0 else FAVORITE_ICON.iconWidth * 2)
          foreground = (if (isSelected) list.selectionForeground else list.foreground)
        })
        if (isFavorite) {
          addToRight(JLabel(FAVORITE_ON_ICON))
        }
        background = (if (isSelected) list.selectionBackground else list.background)
      }
    }
  }

  override fun getToolTipText(event: MouseEvent): String = LogcatBundle.message("logcat.filter.history.tooltip")

  private data class FilterHistoryItem(val filter: String, val isFavorite: Boolean)
}

// Under test environment, the icons are fakes and non-scalable.
private fun Icon.scale(): Icon = if (this is ScalableIcon) scale(0.5f) else this