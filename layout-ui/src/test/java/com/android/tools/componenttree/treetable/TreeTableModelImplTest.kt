/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.FQCN_BUTTON
import com.android.SdkConstants.FQCN_LINEAR_LAYOUT
import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.componenttree.util.Item
import com.android.tools.componenttree.util.ItemNodeType
import com.android.tools.componenttree.util.Style
import com.android.tools.componenttree.util.StyleNodeType
import com.android.tools.componenttree.util.StyleRenderer
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import javax.swing.SwingUtilities
import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreeSelectionModel

class TreeTableModelImplTest {
  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @get:Rule
  val edtTule = EdtRule()

  private val style1 = Style("style1")
  private val style2 = Style("style2")
  private val item1 = Item(FQCN_LINEAR_LAYOUT)
  private val item2 = Item(FQCN_TEXT_VIEW)
  private val item3 = Item(FQCN_BUTTON)
  private val count = NotificationCount()
  private val model = TreeTableModelImpl(listOf(), mapOf(Pair(Item::class.java, ItemNodeType()),
                                                         Pair(Style::class.java, StyleNodeType())), SwingUtilities::invokeLater)
  private val table = TreeTableImpl(
    model,
    contextPopup = { _, _, _, _ -> },
    doubleClick = {},
    painter = null,
    installKeyboardActions = {},
    treeSelectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION,
    installTreeSearch = false,
    autoScroll = false,
    expandAllOnRootChange = false,
    treeHeaderRenderer = null
  )
  private val selectionModel = table.treeTableSelectionModel

  @Before
  fun setUp() {
    item1.children.addAll(listOf(item2, item3))
    item2.parent = item1
    item3.parent = item1
    item2.children.add(style1)
    style1.parent = item2
    style1.children.add(style2)
    style2.parent = style1
  }

  @RunsInEdt
  @Test
  fun testRootNotification() {
    model.addTreeModelListener(count)
    model.treeRoot = item1
    UIUtil.dispatchAllInvocationEvents()
    assertThat(count.treeChanged).isEqualTo(1)
  }

  @Test
  fun testParent() {
    model.treeRoot = item1
    assertThat(model.parent(item1)).isNull()
    assertThat(model.parent(item2)).isEqualTo(item1)
    assertThat(model.parent(item3)).isEqualTo(item1)
    assertThat(model.parent(style1)).isEqualTo(item2)
    assertThat(model.parent(style2)).isEqualTo(style1)
  }

  @Test
  fun testChildren() {
    model.treeRoot = item1
    assertThat(model.children(item1)).containsExactly(item2, item3).inOrder()
    assertThat(model.children(item2)).containsExactly(style1)
    assertThat(model.children(item3)).isEmpty()
    assertThat(model.children(style1)).containsExactly(style2)
    assertThat(model.children(style2)).isEmpty()
  }

  @Test
  fun testToSearchString() {
    model.treeRoot = item1
    assertThat(model.toSearchString(item1)).isEqualTo(LINEAR_LAYOUT)
    assertThat(model.toSearchString(item2)).isEqualTo(TEXT_VIEW)
    assertThat(model.toSearchString(item3)).isEqualTo(BUTTON)
    assertThat(model.toSearchString(style1)).isEqualTo("style1")
    assertThat(model.toSearchString(style2)).isEqualTo("style2")
  }

  @Test
  fun testRenderer() {
    val itemRenderer = model.rendererOf(item1)
    val styleRenderer = model.rendererOf(style1)
    assertThat(itemRenderer).isInstanceOf(ViewTreeCellRenderer::class.java)
    assertThat(model.rendererOf(item2)).isSameAs(itemRenderer)
    assertThat(model.rendererOf(item3)).isSameAs(itemRenderer)
    assertThat(styleRenderer).isInstanceOf(StyleRenderer::class.java)
    assertThat(model.rendererOf(style2)).isSameAs(styleRenderer)
  }

  @Test
  fun testClearRenderer() {
    val itemRenderer1 = model.rendererOf(item1)
    val styleRenderer1 = model.rendererOf(style1)
    model.clearRendererCache()

    val itemRenderer2 = model.rendererOf(item1)
    val styleRenderer2 = model.rendererOf(style1)
    assertThat(itemRenderer2).isNotSameAs(itemRenderer1)
    assertThat(styleRenderer2).isNotSameAs(styleRenderer1)
    assertThat(model.rendererOf(item2)).isSameAs(itemRenderer2)
    assertThat(model.rendererOf(item3)).isSameAs(itemRenderer2)
    assertThat(model.rendererOf(style2)).isSameAs(styleRenderer2)
  }

  @RunsInEdt
  @Test
  fun testNoSelectionNotificationFromModel() {
    // setup
    var selectionChangeCount = 0
    var tableSelectionChangeCount = 0
    var treeSelectionChangeCount = 0
    var autoScrollCount = 0
    model.treeRoot = item1
    UIUtil.dispatchAllInvocationEvents()
    model.addTreeModelListener(count)
    selectionModel.addSelectionListener { selectionChangeCount++ }
    table.selectionModel.addListSelectionListener { tableSelectionChangeCount++ }
    table.tree.selectionModel.addTreeSelectionListener { treeSelectionChangeCount++ }
    selectionModel.addAutoScrollListener { autoScrollCount++ }

    // test
    selectionModel.currentSelection = listOf(item2)
    assertThat(selectionChangeCount).isEqualTo(0)
    assertThat(tableSelectionChangeCount).isEqualTo(1)
    assertThat(treeSelectionChangeCount).isEqualTo(1)
    assertThat(autoScrollCount).isEqualTo(1)
    assertThat(count.anyChanges()).isFalse()
    assertThat(table.rowCount).isEqualTo(3)
    assertThat(table.selectedRow).isEqualTo(1)
    assertThat(selectionModel.currentSelection).containsExactly(item2)
  }

  @RunsInEdt
  @Test
  fun testNoSelectionNotificationFromModelOfCollapsedNode() {
    // setup
    var selectionChangeCount = 0
    var tableSelectionChangeCount = 0
    var treeSelectionChangeCount = 0
    var autoScrollCount = 0
    model.treeRoot = item1
    UIUtil.dispatchAllInvocationEvents()
    model.addTreeModelListener(count)
    selectionModel.addSelectionListener { selectionChangeCount++ }
    table.selectionModel.addListSelectionListener { tableSelectionChangeCount++ }
    table.tree.selectionModel.addTreeSelectionListener { treeSelectionChangeCount++ }
    selectionModel.addAutoScrollListener { autoScrollCount++ }

    // test
    selectionModel.currentSelection = listOf(style2)
    assertThat(selectionChangeCount).isEqualTo(0)
    assertThat(tableSelectionChangeCount).isEqualTo(1)
    assertThat(treeSelectionChangeCount).isEqualTo(1)
    assertThat(autoScrollCount).isEqualTo(1)
    assertThat(count.anyChanges()).isFalse()
    assertThat(table.rowCount).isEqualTo(5)
    assertThat(table.selectedRow).isEqualTo(3)
    assertThat(selectionModel.currentSelection).containsExactly(style2)
  }

  @RunsInEdt
  @Test
  fun testSelectionNotificationFromTable() {
    var selectionChangeCount = 0
    var tableSelectionChangeCount = 0
    var treeSelectionChangeCount = 0
    var autoScrollCount = 0
    model.treeRoot = item1
    UIUtil.dispatchAllInvocationEvents()
    model.addTreeModelListener(count)
    selectionModel.addSelectionListener { selectionChangeCount++ }
    table.selectionModel.addListSelectionListener { tableSelectionChangeCount++ }
    table.tree.selectionModel.addTreeSelectionListener { treeSelectionChangeCount++ }
    selectionModel.addAutoScrollListener { autoScrollCount++ }

    // test
    table.selectionModel.setSelectionInterval(2, 2)
    assertThat(tableSelectionChangeCount).isEqualTo(1)
    assertThat(selectionChangeCount).isEqualTo(1)
    assertThat(treeSelectionChangeCount).isEqualTo(1)
    assertThat(autoScrollCount).isEqualTo(0)
    assertThat(count.anyChanges()).isFalse()
    assertThat(selectionModel.currentSelection).containsExactly(item3)
  }

  @Test
  fun testComputeDepth() {
    assertThat(model.computeDepth(item1)).isEqualTo(1)
    assertThat(model.computeDepth(item2)).isEqualTo(2)
    assertThat(model.computeDepth(item3)).isEqualTo(2)
    assertThat(model.computeDepth(style1)).isEqualTo(3)
    assertThat(model.computeDepth(style2)).isEqualTo(4)
  }

  private class NotificationCount : TreeTableModelImplListener {
    var inserted = 0
    var structureChanges = 0
    var columnDataChanged = 0
    var nodesChanged = 0
    var nodesRemoved = 0
    var treeChanged = 0

    fun anyChanges(): Boolean = inserted != 0 || structureChanges != 0 || nodesChanged != 0 || nodesRemoved != 0 || treeChanged != 0

    override fun treeNodesInserted(event: TreeModelEvent) {
      inserted++
    }

    override fun treeStructureChanged(e: TreeModelEvent?) {
      structureChanges++
    }

    override fun columnDataChanged() {
      columnDataChanged++
    }

    override fun treeNodesChanged(e: TreeModelEvent?) {
      nodesChanged++
    }

    override fun treeNodesRemoved(e: TreeModelEvent?) {
      nodesRemoved++
    }

    override fun treeChanged(event: TreeModelEvent) {
      treeChanged++
    }
  }
}
