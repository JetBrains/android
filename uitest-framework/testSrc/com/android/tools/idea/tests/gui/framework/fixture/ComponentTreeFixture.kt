/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture

import com.android.tools.componenttree.treetable.TreeTableImpl
import com.intellij.openapi.util.Ref
import com.intellij.util.ui.tree.TreeUtil
import org.fest.swing.core.Robot
import org.fest.swing.data.TableCell
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JPopupMenuFixture
import org.fest.swing.fixture.JTableFixture
import org.fest.swing.timing.Wait
import javax.swing.JTable
import javax.swing.tree.TreeModel

/**
 * Fixture for the component tree in com.android.tools.componenttree.
 */
class ComponentTreeFixture(robot: Robot, table: JTable) : JTableFixture(robot, table) {

  val table: TreeTableImpl
    get() = target() as TreeTableImpl

  /**
   * Expand all nodes without creating selection events.
   */
  fun expandAll() {
    val done = Ref(false)
    GuiQuery.get { TreeUtil.expandAll(table.tree) { done.set(true) } }
    Wait.seconds(10).expecting("Tree to be expanded").until{ done.get() }
  }

  /**
   * Convenience shortcut.
   */
  fun selectRow(row: Int) {
    selectRows(row)
  }

  fun <T> selectedElements(): List<T> {
    @Suppress("UNCHECKED_CAST")
    return target().selectedRows.map { target().getValueAt(it, 0) as T }
  }

  fun showPopupMenuAt(row: Int, column: Int): JPopupMenuFixture =
    showPopupMenuAt(TableCell.row(row).column(column))

  /**
   * The rowCount in the model of the component tree.
   *
   * i.e. this count includes the nodes that are currently collapsed in the tree.
   */
  val modelRowCount: Int
    get() {
      val model = table.tree.model
      val root = model.root ?: return 0
      return treeSize(root, model)
    }

  private fun treeSize(node: Any, model: TreeModel): Int =
    1 + (0 until model.getChildCount(node)).sumBy { treeSize(model.getChild(node, it), model) }
}
