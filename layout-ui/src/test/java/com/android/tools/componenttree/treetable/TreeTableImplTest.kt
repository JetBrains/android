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
package com.android.tools.componenttree.treetable

import com.android.SdkConstants
import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.laf.HeadlessTableUI
import com.android.tools.adtui.swing.laf.HeadlessTreeUI
import com.android.tools.componenttree.api.ComponentTreeBuildResult
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ContextPopupHandler
import com.android.tools.componenttree.api.DoubleClickHandler
import com.android.tools.componenttree.api.IconColumn
import com.android.tools.componenttree.api.createIntColumn
import com.android.tools.componenttree.util.Item
import com.android.tools.componenttree.util.ItemNodeType
import com.android.tools.componenttree.util.Style
import com.android.tools.componenttree.util.StyleNodeType
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettingType
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.TextTransferable
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import icons.StudioIcons
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runners.model.Statement
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.RepaintManager
import javax.swing.ScrollPaneConstants

@RunsInEdt
class TreeTableImplTest {
  private val disposableRule = DisposableRule()

  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @get:Rule
  val chain = RuleChain
    .outerRule(MockitoCleanerRule())
    .around(EdtRule())
    .around(IconLoaderRule())
    .around(disposableRule)!!

  private val style1 = Style("style1")
  private val style2 = Style("style2")
  private val item1 = Item(SdkConstants.FQCN_LINEAR_LAYOUT)
  private val item2 = Item(SdkConstants.FQCN_TEXT_VIEW)
  private val item3 = Item(SdkConstants.FQCN_BUTTON)
  private val item4 = Item(SdkConstants.FQCN_CHECK_BOX)
  private val contextPopup = object : ContextPopupHandler {
    var popupInvokeCount = 0
      private set
    var lastItem: Any? = null

    override fun invoke(item: Any, component: JComponent, x: Int, y: Int) {
      popupInvokeCount++
      lastItem = item
    }
  }
  private val doubleClickHandler = object : DoubleClickHandler {
    var clickCount = 0
      private set
    var lastItem: Any? = null

    override fun invoke(item: Any) {
      clickCount++
      lastItem = item
    }
  }
  private val badgeItem = object : IconColumn("b1") {
    var lastActionItem: Any? = null
    var lastActionComponent: JComponent? = null
    var lastActionBounds: Rectangle? = null
    var lastPopupItem: Any? = null

    override var leftDivider: Boolean = false

    override fun getIcon(item: Any): Icon? = when (item) {
      item1 -> StudioIcons.Common.ERROR
      item2 -> StudioIcons.Common.FILTER
      else -> null
    }

    override fun getHoverIcon(item: Any): Icon? = when (item) {
      item1 -> StudioIcons.LayoutEditor.Properties.VISIBLE
      item2 -> StudioIcons.LayoutEditor.Properties.INVISIBLE
      else -> null
    }

    override fun getTooltipText(item: Any): String = "Badge tooltip: $item".trim()

    override fun performAction(item: Any, component: JComponent, bounds: Rectangle) {
      lastActionItem = item
      lastActionComponent = component
      lastActionBounds = bounds
    }

    override fun showPopup(item: Any, component: JComponent, x: Int, y: Int) {
      lastPopupItem = item
    }
  }
  private val column2 = object {
    var lastActionItem: Item? = null
    var lastActionComponent: JComponent? = null
    var lastActionBounds: Rectangle? = null
    var lastPopupItem: Item? = null
    var lastPopupComponent: JComponent? = null

    fun performAction(item: Item, component: JComponent, bounds: Rectangle) {
      lastActionItem = item
      lastActionComponent = component
      lastActionBounds = bounds
    }

    @Suppress("UNUSED_PARAMETER")
    fun showPopup(item: Item, component: JComponent, x: Int, y: Int) {
      lastPopupItem = item
      lastPopupComponent = component
    }

    fun tooltip(item: Item): String {
      return "Column2 tooltip: ${item.tagName.substringAfterLast('.').trim()}"
    }
  }

  @Before
  fun setUp() {
    item1.add(item2, item3)
    item2.children.add(style1)
    style1.parent = item2
    style1.children.add(style2)
    style2.parent = style1

    val settings = object : AdvancedSettings() {
      override fun getSetting(id: String) = false
      override fun setSetting(id: String, value: Any, expectType: AdvancedSettingType) {}
      override fun getDefault(id: String) = false
    }
    ApplicationManager.getApplication().replaceService(AdvancedSettings::class.java, settings, disposableRule.disposable)
  }

  @After
  fun tearDown() {
    RepaintManager.setCurrentManager(null)
  }

  @Test
  fun testContextPopup() {
    val table = createTreeTable()
    setScrollPaneSize(table, 300, 800)
    val ui = FakeUi(table)
    ui.mouse.rightClick(10, 10)
    assertThat(contextPopup.popupInvokeCount).isEqualTo(1)
    assertThat(contextPopup.lastItem).isSameAs(item1)
    assertThat(badgeItem.lastPopupItem).isNull()
  }

  @Test
  fun testBadgePopup() {
    val table = createTreeTable()
    setScrollPaneSize(table, 400, 700)
    val ui = FakeUi(table)
    ui.mouse.rightClick(390, 10)
    assertThat(contextPopup.popupInvokeCount).isEqualTo(0)
    assertThat(badgeItem.lastPopupItem).isEqualTo(item1)
  }

  @Test
  fun testColumnPopup() {
    val table = createTreeTable()
    setScrollPaneSize(table, 400, 700)
    val ui = FakeUi(table)
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    val columnCell = table.getCellRect(2, 2, true)
    ui.mouse.rightClick(columnCell.x + 5, 30)
    assertThat(contextPopup.popupInvokeCount).isEqualTo(0)
    assertThat(column2.lastPopupItem).isEqualTo(item2)
    assertThat(column2.lastPopupComponent).isEqualTo(table)
  }

  @Test
  fun testBadgePopupWhenScrolled() {
    val table = createTreeTable()
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    val badgeCell = table.getCellRect(3, 3, true)
    val scrollPane = setScrollPaneSize(table, 400, 20)
    scrollPane.viewport.viewPosition = Point(0, badgeCell.bottom - 20)
    UIUtil.dispatchAllInvocationEvents()
    val ui = FakeUi(table)
    ui.mouse.rightClick(395, badgeCell.y + 5)
    assertThat(contextPopup.popupInvokeCount).isEqualTo(0)
    assertThat(doubleClickHandler.clickCount).isEqualTo(0)
    assertThat(badgeItem.lastPopupItem).isEqualTo(item3)
    assertThat(badgeItem.lastActionItem).isNull()
  }

  @Test
  fun testClickOnBadge() {
    val table = createTreeTable()
    setScrollPaneSize(table, 400, 700)
    val ui = FakeUi(table)
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    ui.mouse.click(390, 30)
    assertThat(badgeItem.lastActionItem).isEqualTo(item2)
    assertThat(badgeItem.lastActionComponent).isSameAs(table)
    assertThat(badgeItem.lastActionBounds).isEqualTo(table.getCellRect(1, 3, true))
  }

  @Test
  fun testClickOnBadgeWhenScrolled() {
    val table = createTreeTable()
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    val badgeCell = table.getCellRect(3, 3, true)
    val scrollPane = setScrollPaneSize(table, 400, 20)
    scrollPane.viewport.viewPosition = Point(0, badgeCell.bottom - 20)
    UIUtil.dispatchAllInvocationEvents()
    val ui = FakeUi(table)
    ui.mouse.click(395, badgeCell.y + 5)
    assertThat(badgeItem.lastActionItem).isEqualTo(item3)
    assertThat(badgeItem.lastActionComponent).isSameAs(table)
    assertThat(badgeItem.lastActionBounds).isEqualTo(table.getCellRect(3, 3, true))
  }

  @Test
  fun testClickOnColumn() {
    val table = createTreeTable()
    setScrollPaneSize(table, 400, 700)
    val ui = FakeUi(table)
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    val columnCell = table.getCellRect(2, 2, true)
    ui.mouse.click(columnCell.x + 5, 30)
    assertThat(column2.lastActionItem).isEqualTo(item2)
    assertThat(column2.lastActionComponent).isSameAs(table)
    assertThat(column2.lastActionBounds).isEqualTo(table.getCellRect(1, 2, true))
  }

  @Test
  fun testDoubleClick() {
    val tree = createTreeTable()
    setScrollPaneSize(tree, 400, 700)
    val ui = FakeUi(tree)
    ui.mouse.doubleClick(10, 10)
    assertThat(doubleClickHandler.clickCount).isEqualTo(1)
    assertThat(doubleClickHandler.lastItem).isSameAs(item1)
  }

  @Test
  fun testExpandKeys() {
    val table = createTreeTable()
    table.tree.showsRootHandles = true
    setScrollPaneSize(table, 400, 700)
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    table.setRowSelectionInterval(1, 1)
    val ui = FakeUi(table)
    ui.keyboard.setFocus(table)

    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(table.tree.isExpanded(1)).isFalse()
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(table.tree.isExpanded(1)).isTrue()

    table.setRowSelectionInterval(0, 0)
    ui.keyboard.pressAndRelease(KeyEvent.VK_SPACE)
    assertThat(table.tree.isExpanded(0)).isFalse()
    ui.keyboard.pressAndRelease(KeyEvent.VK_SPACE)
    assertThat(table.tree.isExpanded(0)).isTrue()
  }

  @Test
  fun testHiddenRootIsExpanded() {
    val table = createTreeTable()
    table.tree.isRootVisible = false
    table.tree.showsRootHandles = true
    val hiddenRoot = Item("hidden")
    hiddenRoot.children.add(item1)
    item1.parent = hiddenRoot
    table.tableModel.treeRoot = hiddenRoot
    assertThat(table.rowCount).isEqualTo(1)
    table.updateUI()
    assertThat(table.rowCount).isEqualTo(1)
  }

  @Test
  fun testIntColumnWidthIncreasedAfterColumnDataChanged() {
    val table = createTreeTable()
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    UIUtil.dispatchAllInvocationEvents()
    val c1Before = table.cellWidth(1)

    // increase the width of column1:
    item2.column1 = 12345678
    table.tableModel.columnDataChanged()
    UIUtil.dispatchAllInvocationEvents()
    val c1After = table.cellWidth(1)
    assertThat(c1After).isGreaterThan(c1Before)

    // decrease the width of column1:
    item2.column1 = 6
    table.tableModel.columnDataChanged()
    UIUtil.dispatchAllInvocationEvents()
    val c1Final = table.cellWidth(1)
    assertThat(c1Final).isLessThan(c1After)
    assertThat(c1Final).isEqualTo(c1Before)
  }

  @Test
  fun testHideColumns() {
    val result = createTree()
    val table = result.focusComponent as JTable
    result.tree.expandRow(0)
    result.tree.expandRow(1)
    UIUtil.dispatchAllInvocationEvents()
    val c1Before = table.cellWidth(1)
    val c2Before = table.cellWidth(2)
    val badgeBefore = table.cellWidth(3)
    assertThat(c1Before).isGreaterThan(0)
    assertThat(c2Before).isGreaterThan(0)
    assertThat(badgeBefore).isGreaterThan(0)

    // hide c1 column
    result.interactions.setColumnVisibility(1, false)
    assertThat(table.cellWidth(1)).isEqualTo(0)
    assertThat(table.cellWidth(2)).isEqualTo(c2Before)
    assertThat(table.cellWidth(3)).isEqualTo(badgeBefore)

    // hide badge column
    result.interactions.setColumnVisibility(3, false)
    assertThat(table.cellWidth(1)).isEqualTo(0)
    assertThat(table.cellWidth(2)).isEqualTo(c2Before)
    assertThat(table.cellWidth(3)).isEqualTo(0)

    // show c1 column
    result.interactions.setColumnVisibility(1, true)
    assertThat(table.cellWidth(1)).isEqualTo(c1Before)
    assertThat(table.cellWidth(2)).isEqualTo(c2Before)
    assertThat(table.cellWidth(3)).isEqualTo(0)

    // show badge column
    result.interactions.setColumnVisibility(3, true)
    assertThat(table.cellWidth(1)).isEqualTo(c1Before)
    assertThat(table.cellWidth(2)).isEqualTo(c2Before)
    assertThat(table.cellWidth(3)).isEqualTo(badgeBefore)
  }

  @Test
  fun testHideHeader() {
    val result = createTree()
    val table = result.focusComponent as TreeTableImpl
    val scrollPane = getScrollPane(table)

    // This will cause addNotify() to be called on the table:
    FakeUi(scrollPane, createFakeWindow = true)
    assertThat(table.tableHeader.isShowing).isFalse()

    // show the header
    result.interactions.setHeaderVisibility(true)
    assertThat(table.tableHeader.isShowing).isTrue()

    // Simulate minimize and restore of the component tree
    table.removeNotify()
    table.addNotify()
    assertThat(table.tableHeader.isShowing).isTrue()


    // hide the header
    result.interactions.setHeaderVisibility(false)
    assertThat(table.tableHeader.isShowing).isFalse()

    // Simulate minimize and restore of the component tree
    table.removeNotify()
    table.addNotify()
    assertThat(table.tableHeader.isShowing).isFalse()
    table.removeNotify()
  }

  @Test
  fun testHoverCell() {
    val table = createTreeTable()
    setScrollPaneSize(table, 300, 800)
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    val ui = FakeUi(table)
    UIUtil.dispatchAllInvocationEvents()
    val c1 = table.getCellRect(2, 1, true)
    val c2 = table.getCellRect(3, 2, true)
    val badge1 = table.getCellRect(0, 3, true)
    val badge2 = table.getCellRect(1, 3, true)
    val badge3 = table.getCellRect(2, 3, true)
    val manager: RepaintManager = mock()
    RepaintManager.setCurrentManager(manager)

    // move mouse over column c1 for style1 (row 2)
    ui.mouse.moveTo(c1.centerX.toInt(), c1.centerY.toInt())
    assertThat(table.hoverCell?.equalTo(2, 1)).isTrue()
    verifyNoInteractions(manager) // c1 is not a badge column

    // move mouse over column c2 for item3 (row 3)
    ui.mouse.moveTo(c2.centerX.toInt(), c2.centerY.toInt())
    assertThat(table.hoverCell?.equalTo(3, 2)).isTrue()
    verifyNoInteractions(manager) // c2 is not a badge column

    // move mouse over the tree column for item3 (row 3)
    ui.mouse.moveTo(5, c2.centerY.toInt())
    assertThat(table.hoverCell?.equalTo(3, 0)).isTrue()
    verifyNoInteractions(manager) // the tree is not a badge column

    // move mouse over the badge column below the last row in the table
    ui.mouse.moveTo(badge1.centerX.toInt(), 750)
    assertThat(table.hoverCell).isNull()
    verifyNoInteractions(manager) // mouse was moved below the last row

    // move mouse over the badge column for style1 (row 2)
    ui.mouse.moveTo(badge3.centerX.toInt(), badge3.centerY.toInt())
    assertThat(table.hoverCell?.equalTo(2, 3)).isTrue()
    verifyNoInteractions(manager) // badge in row 3 (style1) doesn't have a hoverIcon
    assertThat(table.badgeIconOf(2, 3)).isSameAs(EmptyIcon.ICON_16)

    // move mouse over the badge column for item1 (row 0)
    ui.mouse.moveTo(badge1.centerX.toInt(), badge1.centerY.toInt())
    assertThat(table.hoverCell?.equalTo(0, 3)).isTrue()
    verify(manager).addDirtyRegion(table, badge1.x, badge1.y, badge1.width, badge1.height)
    assertThat(table.badgeIconOf(0, 3)).isSameAs(StudioIcons.LayoutEditor.Properties.VISIBLE)
    assertThat(table.badgeIconOf(1, 3)).isSameAs(StudioIcons.Common.FILTER)

    // move mouse over the badge column for item2 (row 1)
    ui.mouse.moveTo(badge2.centerX.toInt(), badge2.centerY.toInt())
    assertThat(table.hoverCell?.equalTo(1, 3)).isTrue()
    verify(manager, times(2)).addDirtyRegion(table, badge1.x, badge1.y, badge1.width, badge1.height)
    verify(manager).addDirtyRegion(table, badge2.x, badge2.y, badge2.width, badge2.height)
    assertThat(table.badgeIconOf(0, 3)).isSameAs(StudioIcons.Common.ERROR)
    assertThat(table.badgeIconOf(1, 3)).isSameAs(StudioIcons.LayoutEditor.Properties.INVISIBLE)

    // move mouse outside the table
    ui.mouse.moveTo(table.width + 40, badge2.centerY.toInt())
    assertThat(table.hoverCell).isNull()
    verify(manager, times(2)).addDirtyRegion(table, badge2.x, badge2.y, badge2.width, badge2.height)
    assertThat(table.badgeIconOf(0, 3)).isSameAs(StudioIcons.Common.ERROR)
    assertThat(table.badgeIconOf(1, 3)).isSameAs(StudioIcons.Common.FILTER)
  }

  @Test
  fun testPreferredBadgeSize() {
    val table = createTreeTable()
    val renderer = table.getCellRenderer(0, 3)
    val component = renderer.getTableCellRendererComponent(table, item1, true, true, 0, 3)
    assertThat(component.preferredSize.width).isEqualTo(EmptyIcon.ICON_16.iconWidth + JBUIScale.scale(4))
    assertThat(table.columnModel.getColumn(3).width).isEqualTo(EmptyIcon.ICON_16.iconWidth + JBUIScale.scale(4))
  }

  @Test
  fun testPreferredBadgeSizeWithBadgeDivider() {
    badgeItem.leftDivider = true
    val table = createTreeTable()
    val renderer = table.getCellRenderer(0, 3)
    val component = renderer.getTableCellRendererComponent(table, item1, true, true, 0, 3)
    assertThat(component.preferredSize.width).isEqualTo(EmptyIcon.ICON_16.iconWidth + JBUIScale.scale(5))
    assertThat(table.columnModel.getColumn(3).width).isEqualTo(EmptyIcon.ICON_16.iconWidth + JBUIScale.scale(5))
  }

  @Test
  fun testTooltipText() {
    val table = createTreeTable()
    setScrollPaneSize(table, 400, 700)
    FakeUi(table)
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    assertThat(tooltipTextAt(table, 0, 0)).isNull()
    assertThat(tooltipTextAt(table, 0, 1)).isNull()
    assertThat(tooltipTextAt(table, 0, 2)).isEqualTo("Column2 tooltip: LinearLayout")
    assertThat(tooltipTextAt(table, 0, 3)).isEqualTo("Badge tooltip: android.widget.LinearLayout")
    assertThat(tooltipTextAt(table, 1, 0)).isNull()
    assertThat(tooltipTextAt(table, 1, 1)).isNull()
    assertThat(tooltipTextAt(table, 1, 2)).isEqualTo("Column2 tooltip: TextView")
    assertThat(tooltipTextAt(table, 1, 3)).isEqualTo("Badge tooltip: android.widget.TextView")
  }

  private fun tooltipTextAt(table: TreeTableImpl, row: Int, column: Int): String? {
    val cell = table.getCellRect(row, column, true)
    val event = MouseEvent(table, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, cell.centerX.toInt(), cell.centerY.toInt(),
                           0, false)
    return table.getToolTipText(event)
  }

  @Test
  fun testColumnColor() {
    val table = createTreeTable()
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    assertThat(foregroundOf(table, 1, isSelected = false, hasFocus = false)).isEqualTo(UIUtil.getTableForeground(false, false))
    assertThat(foregroundOf(table, 1, isSelected = true, hasFocus = false)).isEqualTo(UIUtil.getTableForeground(true, false))
    focusManager.focusOwner = table
    assertThat(foregroundOf(table, 1, isSelected = false, hasFocus = true)).isEqualTo(UIUtil.getTableForeground(false, true))
    assertThat(foregroundOf(table, 1, isSelected = true, hasFocus = true)).isEqualTo(UIUtil.getTableForeground(true, true))

    focusManager.clearFocusOwner()
    assertThat(foregroundOf(table, 2, isSelected = false, hasFocus = false)).isEqualTo(JBColor.lightGray)
    assertThat(foregroundOf(table, 2, isSelected = true, hasFocus = false)).isEqualTo(JBColor.lightGray)
    focusManager.focusOwner = table
    assertThat(foregroundOf(table, 2, isSelected = false, hasFocus = true)).isEqualTo(JBColor.lightGray)
    assertThat(foregroundOf(table, 2, isSelected = true, hasFocus = true)).isEqualTo(UIUtil.getTableForeground(true, true))
  }

  @Test
  fun testClickResultsInOnlyOneSelectionEvent() {
    val table = createTreeTable()
    var selectionEvents = 0
    table.treeTableSelectionModel.addSelectionListener { selectionEvents++ }
    setScrollPaneSize(table, 400, 700)
    val ui = FakeUi(table)
    table.tree.expandRow(0)
    table.tree.expandRow(1)

    val cell = table.getCellRect(0, 0, true)
    ui.mouse.click(cell.centerX.toInt(), cell.centerY.toInt())
    assertThat(selectionEvents).isEqualTo(1)
  }

  @Test
  fun testSelectionAndExpansionIsMaintainedOnUpdates() {
    val result = createTree()
    val table = result.focusComponent as TreeTableImpl
    val selectionModel = result.selectionModel
    val model = result.model
    var selections = 0
    selectionModel.addSelectionListener { selections++ }
    setScrollPaneSize(table, 400, 700)
    val ui = FakeUi(table)
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    val cell = table.getCellRect(2, 0, true)
    ui.mouse.click(cell.centerX.toInt(), cell.centerY.toInt())
    assertThat(table.treeTableSelectionModel.currentSelection).isEqualTo(listOf(style1))
    assertThat(selections).isEqualTo(1)
    assertThat(TreeUtil.collectExpandedPaths(table.tree).map { it.lastPathComponent }).containsExactly(item1, item2)

    // Simulate a model change.
    item1.add(item4)
    model.hierarchyChanged(null)

    // Make sure the selection is still intact and no further selection events were fired:
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(table.treeTableSelectionModel.currentSelection).isEqualTo(listOf(style1))
    assertThat(selections).isEqualTo(1)
    assertThat(TreeUtil.collectExpandedPaths(table.tree).map { it.lastPathComponent }).containsExactly(item1, item2)
  }

  @Test
  fun testFullExpansionOnRootUpdates() {
    val result = createTree {
      withExpandAllOnRootChange()
    }
    val table = result.focusComponent as TreeTableImpl
    assertThat(TreeUtil.collectExpandedPaths(table.tree).map { it.lastPathComponent }).containsExactly(item1, item2, style1)
    val model = result.model
    table.tree.collapseRow(1)
    assertThat(TreeUtil.collectExpandedPaths(table.tree).map { it.lastPathComponent }).containsExactly(item1)

    // Simulate a model change with no root change:
    model.treeRoot = item1

    // Make sure the expansions are still intact:
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(TreeUtil.collectExpandedPaths(table.tree).map { it.lastPathComponent }).containsExactly(item1)

    // Simulate a model change with a root change:
    model.treeRoot = item2

    // The tree should be fully expanded:
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(TreeUtil.collectExpandedPaths(table.tree).map { it.lastPathComponent }).containsExactly(item2, style1)
  }

  @RunsInEdt
  @Test
  fun testCreateTransferable() {
    val table = createTreeTable()
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    table.setRowSelectionInterval(1, 2) // item2 & style1
    val transferHandler = table.transferHandler as TreeTableImpl.TreeTableTransferHandler
    val transferable = transferHandler.createTransferableForTests(table)
    val str = transferable?.getTransferData(DataFlavor.stringFlavor) as? String
    assertThat(str).isEqualTo("(${item2.tagName},style:${style1.name})")
    assertThat(transferHandler.draggedItems).containsExactly(item2, style1)
  }

  @RunsInEdt
  @Test
  fun testFontHeight() {
    val table = createTreeTable()
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    assertThat(table.tree.rowHeight).isEqualTo(table.rowHeight)

    PortableUiFontRule(scale = 4f).apply(object : Statement() {
      override fun evaluate() {
        table.updateUI()
        assertThat(table.tree.rowHeight).isEqualTo(table.rowHeight)
      }
    }, mock()).evaluate()
  }

  private fun foregroundOf(table: JTable, column: Int, isSelected: Boolean, hasFocus: Boolean): Color {
    val renderer = table.getCellRenderer(0, column)
    val component = renderer.getTableCellRendererComponent(table, table.getValueAt(0, 2), isSelected, hasFocus, 0, column)
    return component.foreground
  }

  private fun setScrollPaneSize(table: TreeTableImpl, width: Int, height: Int): JScrollPane {
    val scrollPane = getScrollPane(table)
    scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
    scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
    scrollPane.setBounds(0, 0,
                         width + scrollPane.verticalScrollBar.preferredSize.width,
                         height + scrollPane.horizontalScrollBar.preferredSize.height)

    // This disables the "Show scroll bars when scrolling" option on Mac (for this test).
    scrollPane.verticalScrollBar.isOpaque = true

    scrollPane.doLayout()
    table.parent.doLayout()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    return scrollPane
  }

  private fun getScrollPane(table: TreeTableImpl): JScrollPane =
    table.parent.parent as JScrollPane

  private fun createTreeTable(customChange: ComponentTreeBuilder.() -> ComponentTreeBuilder = { this }): TreeTableImpl =
    createTree(customChange).focusComponent as TreeTableImpl

  private fun createTree(customChange: ComponentTreeBuilder.() -> ComponentTreeBuilder = { this }): ComponentTreeBuildResult {
    val result = createTreeWithScrollPane(customChange)
    val table = result.focusComponent as TreeTableImpl
    result.model.treeRoot = item1

    table.setUI(HeadlessTableUI())
    table.tree.setUI(HeadlessTreeUI())
    return result
  }

  private fun createTreeWithScrollPane(customChange: ComponentTreeBuilder.() -> ComponentTreeBuilder): ComponentTreeBuildResult {
    return ComponentTreeBuilder()
      .withNodeType(ItemNodeType())
      .withNodeType(StyleNodeType())
      .withColumn(createIntColumn("c1", Item::column1))
      .withColumn(createIntColumn("c2", Item::column2, maxInt = { 6 }, foreground = JBColor.lightGray, action = column2::performAction,
                                  popup = column2::showPopup, tooltip = column2::tooltip))
      .withBadgeSupport(badgeItem)
      .withContextMenu(contextPopup)
      .withDoubleClick(doubleClickHandler)
      .withoutTreeSearch()
      .withInvokeLaterOption { it.run() }
      .withMultipleSelection()
      .withDnD(::merge, deleteOriginOfInternalMove = false)
      .customChange()
      .build()
  }

  private fun merge(t1: Transferable, t2: Transferable): Transferable {
    val s1 = t1.getTransferData(DataFlavor.stringFlavor) as String
    val s2 = t2.getTransferData(DataFlavor.stringFlavor) as String
    return TextTransferable(StringBuffer("($s1,$s2)"))
  }

  private val Rectangle.bottom
    get() = y + height

  private fun JTable.cellWidth(columnIndex: Int) =
    getCellRect(0, columnIndex, true).width

  private fun JTable.badgeIconOf(row: Int, column: Int): Icon? {
    badgeItem.renderer!!.getTableCellRendererComponent(this, getValueAt(row, column), false, true, row, column)
    return badgeItem.renderer!!.icon
  }
}
