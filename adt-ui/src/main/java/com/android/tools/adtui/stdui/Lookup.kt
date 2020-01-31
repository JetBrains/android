/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.stdui

import com.android.tools.adtui.model.stdui.CommonTextFieldModel
import com.android.tools.adtui.model.stdui.SelectiveFilteringListModel
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPopupMenu
import javax.swing.ListCellRenderer
import javax.swing.ListModel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min

private const val BORDER_WIDTH = 4
private const val DEFAULT_CELL_HEIGHT = 16
private const val MAX_LOOKUP_LIST_HEIGHT = 11

private fun Int.modulo(other: Int): Int {
  return (rem(other) + other).rem(other)
}

/**
 * A popup menu used to display completions while editing a [CommonTextField].
 */
class Lookup<out M : CommonTextFieldModel>(val editor: CommonTextField<M>, private val ui: LookupUI = DefaultLookupUI()) {
  private val listModel = DefaultListModel<String>()
  private val filteredModel = SelectiveFilteringListModel<String>(listModel)
  private var matcher = Matcher()
  private val condition = { element: String -> matcher.matches(element) }
  private var showBelow = true
  private var dataLoading = false
  private var dataLoaded = false
  private var lookupCancelled = false

  init {
    @Suppress("UNCHECKED_CAST")
    ui.createList(filteredModel as ListModel<String>, matcher, editor)
    ui.clickAction = { enter() }
    filteredModel.setFilter(condition)
  }

  val isVisible: Boolean
    get() = ui.visible

  fun showLookup() {
    if (dataLoaded) {
      updateFilter()
    }
    else {
      lookupCancelled = false
      val support = editor.editorModel.editingSupport
      support.execution(Runnable {
        if (dataLoading) {
          // Start at most than 1 completion query
          return@Runnable
        }
        dataLoading = true
        val values: List<String>
        try {
          values = support.completion()
        }
        finally {
          dataLoading = false
        }
        if (lookupCancelled) {
          // The lookup was cancelled while waiting for a result from the completion query
          return@Runnable
        }
        dataLoaded = true
        support.uiExecution(Runnable {
          listModel.clear()
          values.forEach { listModel.addElement(it) }
          updateFilter()
        })
      })
    }
  }

  private fun updateFilter() {
    val text = editor.text
    val oldSelectedValue = ui.selectedValue
    matcher.pattern = text
    filteredModel.perfectMatch = text
    filteredModel.refilter()
    val matchesToShow = filteredModel.size > 0
    when {
      matchesToShow && !ui.visible -> display()
      !matchesToShow && ui.visible -> hideLookup()
      matchesToShow -> {
        restoreSelection(oldSelectedValue)
        updateFrameBounds()
      }
    }
  }

  val enabled: Boolean
    get() = isVisible && filteredModel.size > 0

  fun selectFirst() {
    if (filteredModel.size > 0) {
      ui.semiFocused = true
      ui.selectedIndex = 0
    }
  }

  fun selectLast() {
    if (filteredModel.size > 0) {
      ui.semiFocused = true
      ui.selectedIndex = filteredModel.size - 1
    }
  }

  fun selectNextPage() {
    if (filteredModel.size > 0) {
      ui.semiFocused = true
      ui.selectedIndex = min(ui.selectedIndex + ui.visibleRowCount, filteredModel.size - 1)
    }
  }

  fun selectPreviousPage() {
    if (filteredModel.size > 0) {
      ui.semiFocused = true
      ui.selectedIndex = max(ui.selectedIndex - ui.visibleRowCount, 0)
    }
  }

  fun selectNext() {
    if (filteredModel.size > 0) {
      ui.semiFocused = true
      ui.selectedIndex = (ui.selectedIndex + 1).modulo(filteredModel.size)
    }
  }

  fun selectPrevious() {
    if (filteredModel.size > 0) {
      ui.semiFocused = true
      ui.selectedIndex = (ui.selectedIndex - 1).modulo(filteredModel.size)
    }
  }

  fun enter(): Boolean {
    if (!ui.visible) {
      return false
    }
    val value = ui.selectedValue ?: return false
    editor.text = value
    hideLookup()
    return true
  }

  fun escape(): Boolean {
    if (!ui.visible) {
      lookupCancelled = true
      return false
    }
    hideLookup()
    return true
  }

  fun close() {
    hideLookup()
    listModel.clear()
    dataLoaded = false
  }

  private fun restoreSelection(oldSelectedItem: String?) {
    if (oldSelectedItem != null) {
      ui.selectedValue = oldSelectedItem
    }
    if (ui.selectedIndex < 0) {
      ui.selectedIndex = 0
    }
  }

  private fun hideLookup() {
    lookupCancelled = true
    showBelow = true
    ui.visible = false
    ui.semiFocused = false
  }

  private fun display() {
    ui.updateLocation(computeLocation(), editor)
    ui.visible = true
    ui.selectedIndex = 0
  }

  private fun updateFrameBounds() {
    ui.updateLocation(computeLocation(), editor)
  }

  /**
   * Compute the location of the popup.
   *
   * The popup can be placed either above or below the editor.
   * Attempt to keep the popup on the same side of the editor i.e. don't jump up and down.
   * Also make sure there is room to the left if the popup is wide.
   */
  private fun computeLocation(): Point {
    ui.visibleRowCount = Math.min(filteredModel.size, MAX_LOOKUP_LIST_HEIGHT)
    val popupSize = ui.popupSize
    val screenBounds = ui.screenBounds(editor)
    val editorBounds = ui.editorBounds(editor)
    val xPos = max(min(editorBounds.x + BORDER_WIDTH, screenBounds.x + screenBounds.width - popupSize.width), screenBounds.x)
    val yPosAbove = editorBounds.y + BORDER_WIDTH - popupSize.height
    val yPosBelow = editorBounds.y + editorBounds.height - BORDER_WIDTH
    showBelow = when {
      !showBelow && yPosAbove > screenBounds.y -> false
      yPosBelow + popupSize.height < screenBounds.y + screenBounds.height -> true
      yPosAbove > screenBounds.y -> false
      else -> true
    }
    return if (showBelow) Point(xPos, yPosBelow) else Point(xPos, yPosAbove)
  }
}

class Matcher {
  private var internalMatcher: MinusculeMatcher? = null

  var pattern: String = ""
    set(value) {
      field = value
      internalMatcher = NameUtil.buildMatcher("*$value").build()

    }

  fun matches(element: String): Boolean {
    return internalMatcher?.matches(element) ?: true
  }

  fun matchingFragments(element: String): List<TextRange>? {
    return internalMatcher?.matchingFragments(element)
  }
}

/**
 * The UI abstraction for popup. This allows [Lookup] to be testable.
 */
interface LookupUI {
  var visible: Boolean
  var visibleRowCount: Int
  var selectedIndex: Int
  var selectedValue: String?
  var semiFocused: Boolean
  val popupSize: Dimension
  var clickAction: () -> Unit

  fun createList(listModel: ListModel<String>, matcher: Matcher, editor: JComponent)
  fun updateLocation(location: Point, editor: JComponent)
  fun screenBounds(editor: JComponent): Rectangle
  fun editorBounds(editor: JComponent): Rectangle
}

/**
 * Implementation of the popup using a [JPopupMenu].
 */
class DefaultLookupUI : LookupUI {
  private val popup = JPopupMenu()
  private val renderer = LookupCellRenderer()
  private val list = JBList<String>()

  override var clickAction: () -> Unit = {}

  override var visible: Boolean
    get() = popup.isVisible
    set(value) {
      popup.isVisible = value
    }

  override var visibleRowCount: Int
    get() = list.visibleRowCount
    set(value) {
      updateElementHeight()
      list.visibleRowCount = value
    }

  override var selectedIndex: Int
    get() = list.selectedIndex
    set(value) {
      list.selectedIndex = value
      list.ensureIndexIsVisible(value)
    }

  override var selectedValue: String?
    get() = list.selectedValue
    set(value) {
      list.setSelectedValue(value, true)
    }

  override var semiFocused: Boolean
    get() = renderer.semiFocused
    set(value) {
      renderer.semiFocused = value
    }

  override val popupSize: Dimension
    get() = popup.preferredSize

  override fun createList(listModel: ListModel<String>, matcher: Matcher, editor: JComponent) {
    val scrollPane = ScrollPaneFactory.createScrollPane(list, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER)
    popup.add(scrollPane)
    renderer.matcher = matcher
    list.model = listModel
    list.isFocusable = false
    list.cellRenderer = renderer
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.background = renderer.backgroundColor
    list.accessibleContext.accessibleName = "Code Completion"
    list.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(event: MouseEvent) {
        clickAction()
      }
    })
    AccessibleContextUtil.setParent(list as Component, editor)
  }

  private fun updateElementHeight() {
    val model = list.model
    list.fixedCellHeight = when {
      model.size > 0 -> renderer.getListCellRendererComponent(list, model.getElementAt(0), 0, false, false).preferredSize.height
      else -> DEFAULT_CELL_HEIGHT
    }
  }

  override fun updateLocation(location: Point, editor: JComponent) {
    val window = SwingUtilities.getWindowAncestor(popup)
    if (visible && window != null) {
      window.size = window.preferredSize
      if (window.location != location) {
        window.location = location
      }
    }
    else {
      popup.size = popup.preferredSize
      popup.location = location
    }
  }

  override fun screenBounds(editor: JComponent): Rectangle {
    val toolkit = Toolkit.getDefaultToolkit()
    val configuration = editor.graphicsConfiguration ?: return Rectangle(Point(0, 0), toolkit.screenSize)
    val screenBounds = configuration.bounds
    val screenInsets = toolkit.getScreenInsets(configuration)
    screenBounds.x += screenInsets.left
    screenBounds.y += screenInsets.top
    screenBounds.width -= screenInsets.left + screenInsets.right
    screenBounds.height -= screenInsets.top + screenInsets.bottom
    return screenBounds
  }

  override fun editorBounds(editor: JComponent): Rectangle {
    val topLeft = Point()
    SwingUtilities.convertPointToScreen(topLeft, editor)
    return Rectangle(topLeft.x, topLeft.y, editor.width, editor.height)
  }

  /**
   * A [ListCellRenderer] which is able to display which characters match the current search criteria.
   */
  class LookupCellRenderer : ColoredListCellRenderer<String>() {
    private val selectedFocusedBackgroundColor = JBColor(0x0052a4, 0x0052a4)
    private val selectedNonFocusedBackgroundColor = JBColor(0x6e8ea2, 0x55585a)
    private val selectedForegroundColor = JBColor(0xffffff, 0xffffff)
    private val filterForegroundColor = JBColor(0xb000b0, 0xd17ad6)
    private val filterAttributes = SimpleTextAttributes(STYLE_PLAIN, filterForegroundColor)
    private val selectedAttributes = SimpleTextAttributes(STYLE_PLAIN, selectedForegroundColor)

    val backgroundColor = JBColor(0xebf4fe, 0x313435)
    var semiFocused = false
    var matcher: Matcher? = null

    override fun customizeCellRenderer(list: JList<out String>, value: String, index: Int, selected: Boolean, hasFocus: Boolean) {
      background = when {
        selected && semiFocused -> selectedFocusedBackgroundColor
        selected -> selectedNonFocusedBackgroundColor
        else -> backgroundColor
      }
      val foregroundAttributes = if (selected) selectedAttributes else SimpleTextAttributes.REGULAR_ATTRIBUTES
      val ranges = matcher?.matchingFragments(value)
      if (ranges != null) {
        SpeedSearchUtil.appendColoredFragments(this, value, ranges, foregroundAttributes, filterAttributes)
      }
      else {
        append(value, foregroundAttributes)
      }
    }
  }
}
