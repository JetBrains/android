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
import com.android.tools.adtui.model.stdui.EditingSupport
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference
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
  private val filteredModel = FilteringListModel(listModel)
  private var matcher = Matcher()
  private val condition = { element: String -> matcher.matches(element) }
  private var showBelow = true
  private var dataLoading = false
  private var dataLoaded = false
  private var lookupCancelled = false
  private var lastCompletionText = AtomicReference("")

  /**
   * Is the current value included in the top of the completion popup.
   *
   * Some fields allows custom values in addition to the supplied values from completions given by [EditingSupport].
   * An example is the user is typing "ma" and the completions include "match_parent". If the user types <enter>
   * should we commit "ma" or "match_parent" ?  The solution chosen is to include "ma" as the top choice in the
   * completions. Reference: b/148628592.
   */
  private var currentValueIncluded = false

  init {
    ui.createList(filteredModel as ListModel<String>, matcher, editor)
    ui.clickAction = { enter() }
    filteredModel.setFilter(condition)
  }

  val isVisible: Boolean
    get() = ui.visible

  fun showLookup(forText: String) {
    val support = editor.editorModel.editingSupport

    if (dataLoaded && (!support.alwaysRefreshCompletions || forText == lastCompletionText.get())) {
      updateFilter()
    }
    else {
      lookupCancelled = false
      lastCompletionText.set(forText)
      support.execution(Runnable {
        if (dataLoading) {
          // Start at most than 1 completion query
          return@Runnable
        }
        dataLoading = true
        var values: List<String> = emptyList()
        var done = false
        try {
          // If the text has changed while the completions were being generated, recompute them
          // before displaying them.
          while (!done) {
            val lastText = lastCompletionText.get()
            values = support.completion(lastText)
            done = (lastText == lastCompletionText.get())
          }
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
          currentValueIncluded = false
          if (values.isNotEmpty()) {
            val currentValue = editor.text
            if (support.allowCustomValues && currentValue.isNotEmpty()) {
              listModel.addElement(currentValue)
              currentValueIncluded = true
            }
            values.forEach { if (it != currentValue) listModel.addElement(it) }
          }
          updateFilter()
        })
      })
    }
  }

  private fun updateFilter() {
    val text = editor.text
    val oldSelectedValue = ui.selectedValue
    val isCurrentValueSelected = currentValueIncluded && ui.selectedIndex == 0
    if (currentValueIncluded && listModel.size() > 0) {
      listModel.set(0, text)
    }
    matcher.pattern = text
    filteredModel.refilter()
    val emptyListSize = if (currentValueIncluded) 1 else 0
    val hasMatchesToShow = filteredModel.size > emptyListSize
    when {
      hasMatchesToShow && !ui.visible -> display()
      !hasMatchesToShow && ui.visible -> hideLookup()
      hasMatchesToShow -> {
        restoreSelection(isCurrentValueSelected, oldSelectedValue)
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

  private fun restoreSelection(currentValueSelected: Boolean, oldSelectedItem: String?) {
    if (oldSelectedItem != null && !currentValueSelected) {
      ui.selectedValue = oldSelectedItem
    }
    if (ui.selectedIndex < 0 || currentValueSelected) {
      ui.selectedIndex = 0
    }
  }

  private fun hideLookup() {
    lookupCancelled = true
    showBelow = true
    ui.hide()
    ui.semiFocused = false
  }

  private fun display() {
    ui.updateLocation(computeLocation(), editor)
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
    ui.visibleRowCount = min(filteredModel.size, MAX_LOOKUP_LIST_HEIGHT)
    val popupSize = ui.popupSize
    val screenBounds = ui.screenBounds(editor)
    val editorBounds = ui.editorBounds(editor)
    val xPos = max(min(editorBounds.x, screenBounds.x + screenBounds.width - popupSize.width), screenBounds.x)
    val yPosAbove = editorBounds.y - popupSize.height
    val yPosBelow = editorBounds.y + editorBounds.height
    showBelow = when {
      !showBelow && yPosAbove > screenBounds.y -> false
      yPosBelow + popupSize.height < screenBounds.y + screenBounds.height -> true
      yPosAbove > screenBounds.y -> false
      else -> true
    }
    val point = if (showBelow) Point(xPos, yPosBelow) else Point(xPos, yPosAbove)
    point.translate(-editorBounds.x, -editorBounds.y)
    return point
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
  val visible: Boolean
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
  fun hide()
}

/**
 * Implementation of the popup using a [JPopupMenu].
 */
class DefaultLookupUI : LookupUI {
  private val renderer = MyLookupCellRenderer()
  private val list = JBList<String>()
  private val scrollPane = ScrollPaneFactory.createScrollPane(list, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER)
  private var popup: JBPopup? = null

  override var clickAction: () -> Unit = {}

  override val visible: Boolean
    get() = popup?.isVisible == true

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
    get() = list.preferredSize

  override fun createList(listModel: ListModel<String>, matcher: Matcher, editor: JComponent) {
    renderer.matcher = matcher
    list.model = listModel
    list.isFocusable = false
    list.cellRenderer = renderer
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.background = UIUtil.getTableBackground()
    list.accessibleContext.accessibleName = "Code Completion"
    list.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(event: MouseEvent) {
        clickAction()
      }
    })
    AccessibleContextUtil.setParent(list, editor)
  }

  private fun updateElementHeight() {
    val model = list.model
    list.fixedCellHeight = when {
      model.size > 0 -> renderer.getListCellRendererComponent(list, model.getElementAt(0), 0, false, false).preferredSize.height
      else -> DEFAULT_CELL_HEIGHT
    }
  }

  override fun updateLocation(location: Point, editor: JComponent) {
    val currentPopup = popup
    if (currentPopup == null || currentPopup.isDisposed) {
      popup = JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, list).createPopup()
      popup?.show(RelativePoint(editor, location))
    } else {
      currentPopup.setLocation(RelativePoint(editor, location).screenPoint)
      (currentPopup as? AbstractPopup)?.getPopupWindow()?.size = currentPopup.content.preferredSize
      currentPopup.setUiVisible(true)
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

  override fun hide() {
    popup?.setUiVisible(false)
  }

  /**
   * A [ListCellRenderer] which is able to display which characters match the current search criteria.
   */
  class MyLookupCellRenderer : SimpleColoredComponent(), ListCellRenderer<String> {
    private val foregroundAttributes = SimpleTextAttributes(STYLE_PLAIN, UIUtil.getLabelForeground())
    private val matchedAttributes = SimpleTextAttributes(STYLE_PLAIN, LookupCellRenderer.MATCHED_FOREGROUND_COLOR)

    var semiFocused = false
    var matcher: Matcher? = null

    /**
     * A [ListCellRenderer] which is able to display which characters match the current search criteria.
     */
    override fun getListCellRendererComponent(
      list: JList<out String>,
      value: String,
      index: Int,
      selected: Boolean,
      unused: Boolean
    ): Component {
      clear()
      font = list.font
      background = when {
        selected && semiFocused -> LookupCellRenderer.SELECTED_BACKGROUND_COLOR
        selected -> LookupCellRenderer.SELECTED_NON_FOCUSED_BACKGROUND_COLOR
        else -> LookupCellRenderer.BACKGROUND_COLOR
      }
      val ranges = matcher?.matchingFragments(value)
      if (ranges != null) {
        SpeedSearchUtil.appendColoredFragments(this, value, ranges, foregroundAttributes, matchedAttributes)
      }
      else {
        append(value, foregroundAttributes)
      }
      return this
    }
  }
}
