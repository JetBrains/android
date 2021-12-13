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
import com.android.flags.junit.SetFlagRule
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.laf.HeadlessTableUI
import com.android.tools.adtui.swing.laf.HeadlessTreeUI
import com.android.tools.componenttree.api.BadgeItem
import com.android.tools.componenttree.api.ComponentTreeBuildResult
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ContextPopupHandler
import com.android.tools.componenttree.api.DoubleClickHandler
import com.android.tools.componenttree.api.createIntColumnInfo
import com.android.tools.componenttree.util.Item
import com.android.tools.componenttree.util.ItemNodeType
import com.android.tools.componenttree.util.Style
import com.android.tools.componenttree.util.StyleNodeType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.options.advanced.AdvancedSettingType
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.awt.Point
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.RepaintManager
import javax.swing.ScrollPaneConstants

class TreeTableImplTest {
  private val appRule = ApplicationRule()
  private val flagRule = SetFlagRule(StudioFlags.USE_COMPONENT_TREE_TABLE, true)

  @get:Rule
  val chain = RuleChain.outerRule(appRule).around(EdtRule()).around(flagRule).around(IconLoaderRule())!!

  private val style1 = Style("style1")
  private val style2 = Style("style2")
  private val item1 = Item(SdkConstants.FQCN_LINEAR_LAYOUT)
  private val item2 = Item(SdkConstants.FQCN_TEXT_VIEW)
  private val item3 = Item(SdkConstants.FQCN_BUTTON)
  private val contextPopup = object : ContextPopupHandler {
    var popupInvokeCount = 0
      private set

    override fun invoke(component: JComponent, x: Int, y: Int) {
      popupInvokeCount++
    }
  }
  private val doubleClickHandler = object : DoubleClickHandler {
    var clickCount = 0
      private set

    override fun invoke() {
      clickCount++
    }
  }
  private val badgeItem = object : BadgeItem {
    var lastActionItem: Any? = null
    var lastPopupItem: Any? = null

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

    override fun getTooltipText(item: Any?): String = when (item) {
      item1 -> "LinearLayout tip"
      item2 -> "TextView tip"
      style1 -> "style1 tip"
      style2 -> "style2 tip"
      else -> ""
    }

    override fun performAction(item: Any) {
      lastActionItem = item
    }

    override fun showPopup(item: Any, component: JComponent, x: Int, y: Int) {
      lastPopupItem = item
    }
  }
  private val renderer = BadgeRenderer(badgeItem)

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
    }
    appRule.testApplication.registerService(AdvancedSettings::class.java, settings, appRule.testRootDisposable)
  }

  @After
  fun tearDown() {
    RepaintManager.setCurrentManager(null)
  }

  @RunsInEdt
  @Test
  fun testContextPopup() {
    val table = createTreeTable()
    setScrollPaneSize(table, 300, 800)
    val ui = FakeUi(table)
    ui.mouse.rightClick(10, 10)
    assertThat(contextPopup.popupInvokeCount).isEqualTo(1)
    assertThat(badgeItem.lastPopupItem).isNull()
  }

  @RunsInEdt
  @Test
  fun testBadgePopup() {
    val table = createTreeTable()
    setScrollPaneSize(table, 400, 700)
    val ui = FakeUi(table)
    ui.mouse.rightClick(390, 10)
    assertThat(contextPopup.popupInvokeCount).isEqualTo(0)
    assertThat(badgeItem.lastPopupItem).isEqualTo(item1)
  }

  @RunsInEdt
  @Test
  fun testBadgePopupWhenScrolled() {
    val table = createTreeTable()
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    val badgeCell = table.getCellRect(3, table.columnCount - 1, true)
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

  @RunsInEdt
  @Test
  fun testClickOnBadge() {
    val table = createTreeTable()
    setScrollPaneSize(table, 400, 700)
    val ui = FakeUi(table)
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    ui.mouse.click(390, 30)
    assertThat(badgeItem.lastActionItem).isEqualTo(item2)
  }

  @RunsInEdt
  @Test
  fun testClickOnBadgeWhenScrolled() {
    val table = createTreeTable()
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    val badgeCell = table.getCellRect(3, table.columnCount - 1, true)
    val scrollPane = setScrollPaneSize(table, 400, 20)
    scrollPane.viewport.viewPosition = Point(0, badgeCell.bottom - 20)
    UIUtil.dispatchAllInvocationEvents()
    val ui = FakeUi(table)
    ui.mouse.click(395, badgeCell.y + 5)
    assertThat(badgeItem.lastActionItem).isEqualTo(item3)
  }

  @RunsInEdt
  @Test
  fun testDoubleClick() {
    val tree = createTreeTable()
    setScrollPaneSize(tree, 400, 700)
    val ui = FakeUi(tree)
    ui.mouse.doubleClick(10, 10)
    assertThat(doubleClickHandler.clickCount).isEqualTo(1)
  }

  @RunsInEdt
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

  @RunsInEdt
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

  @RunsInEdt
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
    result.setColumnVisibility(1, false)
    assertThat(table.cellWidth(1)).isEqualTo(0)
    assertThat(table.cellWidth(2)).isEqualTo(c2Before)
    assertThat(table.cellWidth(3)).isEqualTo(badgeBefore)

    // hide badge column
    result.setColumnVisibility(3, false)
    assertThat(table.cellWidth(1)).isEqualTo(0)
    assertThat(table.cellWidth(2)).isEqualTo(c2Before)
    assertThat(table.cellWidth(3)).isEqualTo(0)

    // show c1 column
    result.setColumnVisibility(1, true)
    assertThat(table.cellWidth(1)).isEqualTo(c1Before)
    assertThat(table.cellWidth(2)).isEqualTo(c2Before)
    assertThat(table.cellWidth(3)).isEqualTo(0)

    // show badge column
    result.setColumnVisibility(3, true)
    assertThat(table.cellWidth(1)).isEqualTo(c1Before)
    assertThat(table.cellWidth(2)).isEqualTo(c2Before)
    assertThat(table.cellWidth(3)).isEqualTo(badgeBefore)
  }

  @RunsInEdt
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
    assertThat(table.badgeIconOf(2, 3)).isNull()

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

  private fun createTreeTable(): TreeTableImpl =
    createTree().focusComponent as TreeTableImpl

  private fun createTree(): ComponentTreeBuildResult {
    val result = createTreeWithScrollPane()
    val table = result.focusComponent as TreeTableImpl
    result.model.treeRoot = item1

    table.setUI(HeadlessTableUI())
    table.tree.setUI(HeadlessTreeUI())
    return result
  }

  private fun createTreeWithScrollPane(): ComponentTreeBuildResult {
    return ComponentTreeBuilder()
      .withNodeType(ItemNodeType())
      .withNodeType(StyleNodeType())
      .withColumn(createIntColumnInfo("c1", Item::column1))
      .withColumn(createIntColumnInfo("c2", Item::column2, getMax = { 6 }))
      .withBadgeSupport(badgeItem)
      .withContextMenu(contextPopup)
      .withDoubleClick(doubleClickHandler)
      .withoutTreeSearch()
      .withInvokeLaterOption { it.run() }
      .build()
  }

  private val Rectangle.bottom
    get() = y + height

  private fun JTable.cellWidth(columnIndex: Int) =
    getCellRect(0, columnIndex, true).width

  private fun JTable.badgeIconOf(row: Int, column: Int): Icon? {
    renderer.getTableCellRendererComponent(this, getValueAt(row, column), false, true, row, column)
    return renderer.icon
  }
}
