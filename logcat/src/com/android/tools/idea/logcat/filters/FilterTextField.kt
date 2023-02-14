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
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.PACKAGE_NAMES_PROVIDER_KEY
import com.android.tools.idea.logcat.PROCESS_NAMES_PROVIDER_KEY
import com.android.tools.idea.logcat.TAGS_PROVIDER_KEY
import com.android.tools.idea.logcat.filters.FilterTextField.FilterHistoryItem.Item
import com.android.tools.idea.logcat.filters.FilterTextField.FilterHistoryItem.Separator
import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.AndroidProjectDetectorImpl
import com.android.tools.idea.logcat.util.LogcatUsageTracker
import com.android.tools.idea.logcat.util.ReschedulableTask
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.Type.FILTER_ADDED_TO_HISTORY
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.EditorTextField
import com.intellij.ui.GotItTooltip
import com.intellij.ui.GotItTooltip.Companion.BOTTOM_LEFT
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons.Logcat.Input.FAVORITE_FILLED
import icons.StudioIcons.Logcat.Input.FAVORITE_FILLED_HOVER
import icons.StudioIcons.Logcat.Input.FAVORITE_OUTLINE
import icons.StudioIcons.Logcat.Input.FAVORITE_OUTLINE_HOVER
import icons.StudioIcons.Logcat.Input.FILTER_HISTORY
import icons.StudioIcons.Logcat.Input.FILTER_HISTORY_DELETE
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_DELETE
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.net.URL
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.BoxLayout.LINE_AXIS
import javax.swing.BoxLayout.PAGE_AXIS
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.CENTER
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants.HORIZONTAL
import javax.swing.SwingConstants.VERTICAL
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.min

private const val APPLY_FILTER_DELAY_MS = 100L

private val blankIcon = EmptyIcon.ICON_16

// The text of the history dropdown item needs a little horizontal padding
private val historyItemLabelBorder = JBUI.Borders.empty(0, 3)

// The vertical separator between the clear & favorite icons needs a little padding
private val verticalSeparatorBorder = JBUI.Borders.empty(3)

// The inner editor component should have no border, but we need to preserve the inner margins. See EditorTextField#setBorder()
private val editorBorder = JBUI.Borders.empty(2)

// The history icon needs some padding. These values make it look the same as the "Find in files" dialog for example.
private val historyIconBorder = JBUI.Borders.empty(0, 5, 0, 4)

private val historyListSeparatorBorder = JBUI.Borders.empty(3)

private val namedFilterHistoryItemColor = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES

private const val GOT_IT_ID = "filter.tip"

private val deleteKeyCodes = arrayOf(VK_DELETE, VK_BACK_SPACE)

private val logcatFilterHelpUrl = URL(
  "https://developer.android.com/studio/preview/features" +
  "?utm_source=android-studio-2021-3-1&utm_medium=studio-assistant-preview#logcat-search")

private val filterHistoryItemBorder = JBUI.Borders.empty(0, 4)

/**
 * A text field for the filter.
 */
internal class FilterTextField(
  project: Project,
  private val logcatPresenter: LogcatPresenter,
  private val filterParser: LogcatFilterParser,
  initialText: String,
  androidProjectDetector: AndroidProjectDetector = AndroidProjectDetectorImpl(),
) : BorderLayoutPanel(), FilterTextComponent {
  private val filterHistory = AndroidLogcatFilterHistory.getInstance()

  @TestOnly
  internal val notifyFilterChangedTask = ReschedulableTask(AndroidCoroutineScope(logcatPresenter, uiThread))
  private val documentChangedListeners = mutableListOf<DocumentListener>()
  private val textField = FilterEditorTextField(project, logcatPresenter, androidProjectDetector)
  private val historyButton = InlineButton(FILTER_HISTORY)
  private val clearButton = JLabel(AllIcons.Actions.Close)
  private val favoriteButton = JLabel(FAVORITE_OUTLINE)
  private var filter: LogcatFilter? = filterParser.parse(initialText)

  private var isFavorite: Boolean = false
    set(value) {
      field = value
      favoriteButton.icon = if (isFavorite) FAVORITE_FILLED else FAVORITE_OUTLINE
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
    val buttonPanel = InlinePanel(clearButton, JSeparator(VERTICAL), favoriteButton)
    addToRight(buttonPanel)

    if (initialText.isEmpty()) {
      buttonPanel.isVisible = false
    }


    // Set a border around the text field and buttons.
    // Using FilterTextFieldBorder (which is just a DarculaTextBorder) alone doesn't seem to work. It seems to need CompoundBorder.
    border = BorderFactory.createCompoundBorder(FilterTextFieldBorder(), JBUI.Borders.empty(1))

    historyButton.apply {
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          showPopup()
        }
      })
      border = historyIconBorder
      toolTipText = LogcatBundle.message("logcat.filter.history.tooltip")
    }

    textField.apply {
      addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          filter = filterParser.parse(text)
          isFavorite = filterHistory.favorites.contains(text)
          filterHistory.mostRecentlyUsed = textField.text
          notifyFilterChangedTask.reschedule(APPLY_FILTER_DELAY_MS) {
            for (listener in documentChangedListeners) {
              listener.documentChanged(event)
            }
          }
          buttonPanel.isVisible = textField.text.isNotEmpty()
        }
      })
      addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          val hintText = getFilterHintText()
          GotItTooltip(GOT_IT_ID, hintText, logcatPresenter)
            .withBrowserLink(LogcatBundle.message("logcat.filter.got.it.link.text"), logcatFilterHelpUrl)
            .show(textField, BOTTOM_LEFT)
        }

        override fun focusLost(e: FocusEvent?) {
          addToHistory()
        }
      })
      // The text field needs to be moved an extra pixel down to appear correctly.
      border = JBUI.Borders.customLine(background, 1, 0, 0, 0)

      setShowPlaceholderWhenFocused(true)
      setPlaceholder(getFilterHintText())
      ApplicationManager.getApplication().messageBus.connect(logcatPresenter)
        .subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
          override fun activeKeymapChanged(keymap: Keymap?) {
            setPlaceholder(getFilterHintText())
          }
        })
    }

    clearButton.apply {
      toolTipText = LogcatBundle.message("logcat.filter.clear.tooltip")
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
      toolTipText = LogcatBundle.message("logcat.filter.tag.favorite.tooltip")
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          isFavorite = !isFavorite
          toolTipText = if (isFavorite) LogcatBundle.message("logcat.filter.untag.favorite.tooltip")
          else LogcatBundle.message("logcat.filter.tag.favorite.tooltip")
          addToHistory()
          mouseEntered(e) // Setter for isFavorite will set the wrong icon (not hovered)
        }

        override fun mouseEntered(e: MouseEvent) {
          icon = if (isFavorite) FAVORITE_FILLED_HOVER else FAVORITE_OUTLINE_HOVER
        }

        override fun mouseExited(e: MouseEvent) {
          icon = if (isFavorite) FAVORITE_FILLED else FAVORITE_OUTLINE
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
    val popupDisposable = Disposer.newDisposable("popupDisposable")

    addToHistory()
    val list: JList<FilterHistoryItem> = HistoryList(popupDisposable)
    JBPopupFactory.getInstance().createPopupChooserBuilder(listOf("foo", "bar"))
    val popup = PopupChooserBuilder(list)
      .setMovable(false)
      .setRequestFocus(true)
      .setItemChosenCallback {
        (it as? Item)?.let { item ->
          text = item.filter
          isFavorite = item.isFavorite
        }
      }
      .setSelectedValue(Item(text, isFavorite, count = null, filterParser), true)
      .createPopup()
    Disposer.register(popup, popupDisposable)

    popup.showUnderneathOf(this)
  }

  private fun addToHistory() {
    val text = textField.text
    if (text.isEmpty()) {
      return
    }
    filterHistory.add(filterParser, text, isFavorite)
    LogcatUsageTracker.log(
      LogcatUsageEvent.newBuilder()
        .setType(FILTER_ADDED_TO_HISTORY)
        .setLogcatFilter(filterParser.getUsageTrackingEvent(text)?.setIsFavorite(isFavorite)))
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
        putUserData(PROCESS_NAMES_PROVIDER_KEY, logcatPresenter)
        putUserData(AndroidProjectDetector.KEY, androidProjectDetector)
        setBorder(editorBorder)

        contentComponent.addKeyListener(object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_ENTER) {
              e.consume()
              addToHistory()
            }
          }
        })
        contentComponent.addMouseMotionListener(object : MouseAdapter() {
          override fun mouseMoved(e: MouseEvent) {
            contentComponent.toolTipText = editor?.let { editor ->
              val position = editor.xyToLogicalPosition(e.point)
              // The editor is in a single line, so we don't have to convert to an offset
              filter?.findFilterForOffset(position.column)?.displayText
            }
          }
        })
      }
    }

    // On theme change, copy the background from textField.
    override fun updateUI() {
      border = JBUI.Borders.customLine(background, 1, 0, 0, 0)
      super.updateUI()
    }
  }

  private inner class InlineButton(icon: Icon) : JLabel(icon) {
    init {
      isOpaque = true
    }

    // On theme change, copy the background from textField.
    override fun updateUI() {
      background = textField.background
      super.updateUI()
    }
  }

  private inner class InlinePanel(vararg children: JComponent) : JPanel(null) {
    init {
      layout = BoxLayout(this, LINE_AXIS)
      isOpaque = true
      border = verticalSeparatorBorder
      children.forEach { add(it) }
    }

    // On theme change, copy the background from textField.
    override fun updateUI() {
      background = textField.background
      super.updateUI()
    }
  }

  /**
   * It's hard (impossible?) to test the actual popup UI with the existing test framework, so we do the next best thing which is to test the
   * rendering from the JBList (HistoryList) directly.
   */
  @VisibleForTesting
  @UiThread
  internal inner class HistoryList(
    parentDisposable: Disposable,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : JBList<FilterHistoryItem>() {
    private val listModel = CollectionListModel<FilterHistoryItem>()
    private val inactiveColor = String.format("%06x", NamedColorUtil.getInactiveTextColor().rgb and 0xffffff)

    init {
      // The "count" field in FilterHistoryItem.Item takes time to calculate so initially, add all items with no count.
      val items = mutableListOf<FilterHistoryItem>().apply {
        addAll(filterHistory.favorites.map { Item(filter = it, isFavorite = true, count = null, filterParser) })
        if (filterHistory.favorites.isNotEmpty() && filterHistory.nonFavorites.isNotEmpty()) {
          add(Separator)
        }
        addAll(filterHistory.named.map { Item(filter = it, isFavorite = false, count = null, filterParser) })
        addAll(filterHistory.nonFavorites.map { Item(filter = it, isFavorite = false, count = null, filterParser) })
      }
      // Parse all the filters here while we're still in the EDT
      val filters = items.map { if (it is Item) filterParser.parse(it.filter) else null }
      model = listModel
      listModel.addAll(0, items)
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          val item = selectedValue as? Item
          if (item != null && e.keyCode in deleteKeyCodes) {
            deleteItem(selectedIndex)
          }
        }
      })
      cellRenderer = HistoryListCellRenderer()

      // In a background thread, calculate the count of all the items and update the model.
      AndroidCoroutineScope(parentDisposable, coroutineContext).launch {
        val application = ApplicationManager.getApplication()
        listModel.items.forEachIndexed { index, item ->
          if (item is Item) {
            launch {
              val count = application.runReadAction<Int> { logcatPresenter.countFilterMatches(filters[index]) }
              // Replacing an item in the model will remove the selection. Save the selected index, so we can restore it after.
              withContext(uiThread) {
                val selected = selectedIndex
                listModel.setElementAt(Item(item.filter, item.isFavorite, count, filterParser), index)
                if (selected >= 0) {
                  selectedIndex = selected
                }
              }
            }
          }
        }
      }

      val listener = MouseListener()
      addMouseListener(listener)
      addMouseMotionListener(listener)
    }

    override fun getToolTipText(event: MouseEvent): String? {
      val index = selectedIndex
      if (index < 0) return null
      val item = model.getElementAt(index) as? Item ?: return null
      val cellLocation = getCellBounds(index, index).location
      val favoriteIconBounds = item.getFavoriteIconBounds(cellLocation)
      val deleteIconBounds = item.getDeleteIconBounds(cellLocation)
      return when {
        favoriteIconBounds.contains(event.point) -> getFavoriteTooltip(item)
        deleteIconBounds.contains(event.point) -> LogcatBundle.message("logcat.filter.history.delete.tooltip", inactiveColor)
        else -> item.tooltip
      }
    }

    private fun getFavoriteTooltip(item: Item) = when (item.isFavorite) {
      true -> LogcatBundle.message("logcat.filter.untag.favorite.tooltip")
      false -> LogcatBundle.message("logcat.filter.tag.favorite.tooltip")
    }

    /**
     * Toggle the Favorite state of an item.
     *
     * This method does several things:
     * 1. Toggle [Item.isFavorite]
     * 2. Update [FilterTextField.filterHistory] by moving the [Item.filter] to its new collection
     * 3. If the item happens to be the current, item in the [FilterTextField.text] also toggle [FilterTextField.isFavorite]
     * 4. Force a paint
     */
    private fun toggleFavoriteItem(index: Int, bounds: Rectangle) {
      val item = model.getElementAt(index) as Item
      if (item.isFavorite) {
        item.isFavorite = false
        filterHistory.favorites.remove(item.filter)
        filterHistory.nonFavorites.add(item.filter)
      }
      else {
        item.isFavorite = true
        filterHistory.favorites.add(item.filter)
        filterHistory.nonFavorites.remove(item.filter)
      }
      if (item.filter == text) {
        isFavorite = item.isFavorite
      }
      paintImmediately(bounds)
    }

    fun deleteItem(index: Int) {
      val item = listModel.getElementAt(index) as? Item ?: return
      filterHistory.remove(item.filter)
      if (text == item.filter) {
        // If the deleted item is the current text, clear it. If not, it will just be added to the history which is annoying
        text = ""
      }
      listModel.remove(index)
      selectedIndex = min(index, model.size - 1)
    }

    /**
     * Track mouse events and manipulate the UI to reflect them. For example, toggling Favorite state.
     *
     * This allows us to detect mouse events on specific UI areas such as the favorite icon.
     *
     * We need this because the implementation of JList is such that the UI doesn't actually contain any components. Rather, a dummy
     * component (provided by the [ListCellRenderer.getListCellRendererComponent]) is used to draw directly onto the list
     * [java.awt.Graphics]. Mouse listeners on the item components are not actually triggered.
     *
     * When processing a mouse event, we map the event location in the list to the specific item. We use [JList.getSelectedIndex] to find
     * the item. This works because the item corresponding to the mouse event must be the selected item since the mouse is hovering on it.
     */
    inner class MouseListener : MouseAdapter() {
      private var hoveredFavoriteIndex: Int? = null

      override fun mouseReleased(event: MouseEvent) {
        if (event.button == BUTTON1 && event.modifiersEx == 0) {
          val index = selectedIndex
          val item = model.getElementAt(index) as? Item ?: return
          val cellLocation = getCellBounds(index, index).location
          val favoriteIconBounds = item.getFavoriteIconBounds(cellLocation)
          val deleteIconBounds = item.getDeleteIconBounds(cellLocation)
          var consume = true
          when {
            favoriteIconBounds.contains(event.point) -> toggleFavoriteItem(index, favoriteIconBounds)
            deleteIconBounds.contains(event.point) -> deleteItem(index)
            else -> consume = false
          }
          if (consume) {
            event.consume()
          }
        }
      }

      override fun mouseMoved(event: MouseEvent) {
        val index = selectedIndex
        val item = model.getElementAt(index) as? Item

        if (item == null) {
          hoveredFavoriteIndex?.setIsHoveredFavorite(false)
          return
        }
        val cellLocation = getCellBounds(index, index).location
        val favoriteIconBounds = item.getFavoriteIconBounds(cellLocation)
        val hoveredIndex = when {
          favoriteIconBounds.contains(event.point) -> index
          else -> null
        }

        if (hoveredIndex != hoveredFavoriteIndex) {
          hoveredFavoriteIndex?.setIsHoveredFavorite(false)
          hoveredIndex?.setIsHoveredFavorite(true)
          hoveredFavoriteIndex = hoveredIndex
          paintImmediately(favoriteIconBounds)
        }
      }

      private fun Int.setIsHoveredFavorite(value: Boolean) {
        val item = model.getElementAt(this) as? Item ?: return
        item.isFavoriteHovered = value
      }
    }
  }


  private class HistoryListCellRenderer : ListCellRenderer<FilterHistoryItem> {
    override fun getListCellRendererComponent(
      list: JList<out FilterHistoryItem>,
      value: FilterHistoryItem,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean,
    ): Component = value.getComponent(isSelected, list)
  }

  /**
   * See [HistoryList] for why this is VisibleForTesting
   */
  @VisibleForTesting
  internal sealed class FilterHistoryItem {
    class Item(
      val filter: String,
      var isFavorite: Boolean,
      val count: Int?,
      private val filterParser: LogcatFilterParser,
    ) : FilterHistoryItem() {

      var isFavoriteHovered: Boolean = false

      val tooltip: String?

      fun getFavoriteIconBounds(offset: Point): Rectangle = favoriteLabel.bounds + offset

      fun getDeleteIconBounds(offset: Point): Rectangle = deleteLabel.bounds + offset

      private val favoriteLabel = JLabel()

      private val filterLabel = SimpleColoredComponent().apply {
        border = historyItemLabelBorder
      }

      private val countLabel = JLabel().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
        border = historyItemLabelBorder
      }

      private val deleteLabel = JLabel()

      private val component = JPanel(null).apply {
        layout = GroupLayout(this).apply {
          setHorizontalGroup(
            createSequentialGroup()
              .addComponent(favoriteLabel)
              .addComponent(filterLabel)
              .addComponent(countLabel)
              .addComponent(deleteLabel))
          setVerticalGroup(
            createParallelGroup(CENTER)
              .addComponent(favoriteLabel)
              .addComponent(filterLabel)
              .addComponent(countLabel)
              .addComponent(deleteLabel))
          border = filterHistoryItemBorder
        }
      }

      init {
        val filterName = filterParser.parse(filter)?.filterName
        if (filterName != null) {
          val history = AndroidLogcatFilterHistory.getInstance().items
          // If there is more than one Item with the same filterName, show the name and the filter.
          val sameName = history.count { filterParser.parse(it)?.filterName == filterName }
          filterLabel.append(filterName, namedFilterHistoryItemColor)
          val filterWithoutName = filterParser.removeFilterNames(filter)
          tooltip = if (sameName > 1) {
            filterLabel.append(": $filterWithoutName")
            null
          }
          else {
            filterWithoutName
          }
        }
        else {
          tooltip = null
          filterLabel.append(filter)
        }

      }

      override fun getComponent(isSelected: Boolean, list: JList<out FilterHistoryItem>): JComponent {
        // This can be mico optimized, but it's more readable like this
        favoriteLabel.icon = when {
          isFavoriteHovered && isFavorite -> ColoredIconGenerator.generateWhiteIcon(FAVORITE_FILLED)
          isFavoriteHovered && !isFavorite -> ColoredIconGenerator.generateWhiteIcon(FAVORITE_OUTLINE)
          !isFavoriteHovered && isFavorite -> FAVORITE_FILLED
          else -> blankIcon
        }

        deleteLabel.icon = if (isSelected) FILTER_HISTORY_DELETE else blankIcon

        countLabel.text = when (count) {
          null -> " ".repeat(3)
          in 0..99 -> "% 2d ".format(count)
          else -> "99+"
        }
        val (foreground, background) = when {
          isSelected -> Pair(list.selectionForeground, list.selectionBackground)
          else -> Pair(list.foreground, list.background)
        }
        filterLabel.foreground = foreground
        deleteLabel.foreground = foreground
        component.background = background
        countLabel.foreground = if (isSelected) foreground else SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor

        return component
      }

      // Items have unique text, so we only need to check the "filter" field. We MUST ignore the "count" field because we do not yet know
      // the count when we set the selected item.
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Item

        if (filter != other.filter) return false

        return true
      }

      // Items have unique text, so we only need to check the "filter" field
      override fun hashCode(): Int {
        return filter.hashCode()
      }
    }

    object Separator : FilterHistoryItem() {
      // A standalone JSeparator here will change the background of the separator when it is selected. Wrapping it with a JPanel
      // suppresses that behavior for some reason.
      private val component = JPanel(null).apply {
        // A JSeparator relies on the layout to get a non-zero size. a FlowLayout (the default) doesn't work.
        layout = BoxLayout(this, PAGE_AXIS)
        border = historyListSeparatorBorder
        add(JSeparator(HORIZONTAL))
      }

      override fun getComponent(isSelected: Boolean, list: JList<out FilterHistoryItem>): JComponent {
        component.background = list.background
        return component
      }
    }

    abstract fun getComponent(isSelected: Boolean, list: JList<out FilterHistoryItem>): JComponent
  }
}

private fun getFilterHintText(): String {
  return when (val shortcut = KeymapManager.getInstance().activeKeymap.getShortcuts(IdeActions.ACTION_CODE_COMPLETION).firstOrNull()) {
    null -> LogcatBundle.message("logcat.filter.hint.no.shortcut")
    else -> LogcatBundle.message("logcat.filter.hint", KeymapUtil.getShortcutText(shortcut))
  }
}

private operator fun Rectangle.plus(point: Point): Rectangle {
  return Rectangle(x + point.x, y + point.y, width, height)
}
