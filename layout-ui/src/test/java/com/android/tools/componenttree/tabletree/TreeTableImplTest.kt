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
package com.android.tools.componenttree.tabletree

import com.android.SdkConstants
import com.android.flags.junit.SetFlagRule
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.laf.HeadlessTableUI
import com.android.tools.adtui.swing.laf.HeadlessTreeUI
import com.android.tools.componenttree.api.BadgeItem
import com.android.tools.componenttree.api.ComponentTreeBuildResult
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ContextPopupHandler
import com.android.tools.componenttree.api.DoubleClickHandler
import com.android.tools.componenttree.treetable.TreeTableImpl
import com.android.tools.componenttree.treetable.TreeTableModelImpl
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Dimension
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JScrollPane
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

  @Before
  fun setUp() {
    item1.children.addAll(listOf(item2, item3))
    item2.parent = item1
    item3.parent = item1
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
    val scrollPane = setScrollPaneSize(table, 20, 20)
    scrollPane.viewport.viewPosition = Point(table.x + table.width - 20, table.y + table.height - 20)
    UIUtil.dispatchAllInvocationEvents()
    val ui = FakeUi(table)
    ui.mouse.rightClick(table.width - 5, table.height - 5)
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
    val ui = FakeUi(table)
    table.tree.expandRow(0)
    table.tree.expandRow(1)
    UIUtil.dispatchAllInvocationEvents()
    val bounds = table.getCellRect(table.rowCount - 1, 1, true)
    val right = bounds.maxX.toInt()
    val bottom = bounds.maxY.toInt()
    val scrollPane = setScrollPaneSize(table, 20, 20)
    scrollPane.viewport.viewPosition = Point(0, bottom - 20)
    table.size = Dimension(right, bottom)
    ui.mouse.click(right - 5, bottom - 10)
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
    val model = table.tableModel as TreeTableModelImpl
    model.treeRoot = hiddenRoot
    assertThat(table.rowCount).isEqualTo(1)
    table.updateUI()
    assertThat(table.rowCount).isEqualTo(1)
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

  private fun createTreeTable(): TreeTableImpl {
    val result = createTreeWithScrollPane()
    val table = (result.component as JScrollPane).viewport.view as TreeTableImpl
    result.model.treeRoot = item1

    table.setUI(HeadlessTableUI())
    table.tree.setUI(HeadlessTreeUI())
    return table
  }

  private fun createTreeWithScrollPane(): ComponentTreeBuildResult {
    return ComponentTreeBuilder()
      .withNodeType(ItemNodeType())
      .withNodeType(StyleNodeType())
      .withBadgeSupport(badgeItem)
      .withContextMenu(contextPopup)
      .withDoubleClick(doubleClickHandler)
      .withoutTreeSearch()
      .withInvokeLaterOption { it.run() }
      .build()
  }
}
