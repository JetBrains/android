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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.idea.gradle.structure.configurables.ui.RenderedComboBox
import com.android.tools.idea.gradle.structure.configurables.ui.properties.SimplePropertyEditor
import com.android.tools.idea.gradle.structure.configurables.variables.VARIABLES_VIEW
import com.android.tools.idea.tests.gui.framework.findByType
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.translations.TranslationsEditorFixture
import com.android.tools.idea.tests.gui.framework.robot
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.treeStructure.treetable.TreeTable
import org.fest.swing.data.TableCell
import org.fest.swing.driver.BasicJTableCellReader
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JListFixture
import org.fest.swing.fixture.JTableFixture
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.awt.Container
import java.awt.event.KeyEvent
import javax.swing.JTable

class VariablesPerspectiveConfigurableFixture(
  ideFrameFixture: IdeFrameFixture,
  container: Container
) : BasePerspectiveConfigurableFixture(ideFrameFixture, container) {

  fun clickAddSimpleValue() {
    clickToolButton("Add")
    chooseSimpleValue()
  }

  fun clickAddList() {
    clickToolButton("Add")
    chooseList()
  }

  fun clickAddMap() {
    clickToolButton("Add")
    chooseMap()
  }

  fun chooseSimpleValue() {
    chooseTypeByIndex(0 /* 0 Simple value */)
  }

  fun chooseList() {
    chooseTypeByIndex(1 /* 1 List */)
  }

  fun chooseMap() {
    chooseTypeByIndex(2 /* 2 Map */)
  }

  private fun chooseTypeByIndex(index: Int) {
    val listFixture = JListFixture(robot(), getList())
    listFixture.clickItem(index)  // Search by title does not work here.
  }

  private fun findTable(): JTableFixture =
    JTableFixture(robot(), robot().finder().findByType<TreeTable>(container))
      .also {
        it.replaceCellReader(object : BasicJTableCellReader() {
          override fun valueAt(table: JTable, row: Int, column: Int): String? =
            if (column == 0) GuiQuery.get { table.model.getValueAt(row, column).toString() }
            else
              table
                .prepareRenderer(table.getCellRenderer(row, column), row, column)
                .safeAs<SimpleColoredComponent>()
                ?.toString()
                .orEmpty()
        })
      }

  fun enterText(text: String) {
    waitForIdle() // Default implementation is buggy and may post events before really idle.
    robot().typeText(text)
    waitForIdle()
  }

  fun selectValue(value: String) {
    PropertyEditorFixture(
      ideFrameFixture,
      robot().finder().findByType<SimplePropertyEditor<*, *>.EditorWrapper>(container)
    ).selectItem(value)
  }

  fun tab() {
    waitForIdle()
    robot().type(9.toChar())
    waitForIdle()
  }

  fun shiftTab() {
    waitForIdle()
    robot().pressAndReleaseKey(KeyEvent.VK_TAB, KeyEvent.SHIFT_MASK)
    waitForIdle()
  }

  fun right() {
    waitForIdle()
    robot().pressAndReleaseKey(KeyEvent.VK_RIGHT, 0)
    waitForIdle()
  }

  fun up() {
    waitForIdle()
    robot().pressAndReleaseKey(KeyEvent.VK_UP, 0)
    waitForIdle()
  }

  fun down() {
    waitForIdle()
    robot().pressAndReleaseKey(KeyEvent.VK_DOWN, 0)
    waitForIdle()
  }

  fun editWithF2() {
    waitForIdle()
    robot().pressAndReleaseKey(KeyEvent.VK_F2, 0)
    waitForIdle()
  }

  fun editWithEnter() {
    waitForIdle()
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER, 0)
    waitForIdle()
  }

  fun enter() {
    waitForIdle()
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER, 0)
    waitForIdle()
  }

  fun expandAllWithStar() {
    waitForIdle()
    robot().pressAndReleaseKey(KeyEvent.VK_MULTIPLY, 0)
    waitForIdle()
  }

  fun left() {
    robot().pressAndReleaseKey(KeyEvent.VK_LEFT, 0)
  }

  fun selectCell(text: String) {
    findTable().let { table ->
      val targetCell = table.cell(text)
      if (targetCell.column() == 0) {
        // Workaround for a bug in swing-testing library.
        val adjacentCell = table.cell(TableCell.row(targetCell.row()).column(1))
        adjacentCell.select()
        editWithF2() // Release the editor if it is an editanle cell or do nothing.
        robot().pressAndReleaseKey(KeyEvent.VK_LEFT, 0)
      } else {
        targetCell.select()
      }
    }
  }

  fun contents(): List<Pair<String, String>> =
    findTable().contents().map { it[0] to it[1] }
}

fun ProjectStructureDialogFixture.selectVariablesConfigurable(): VariablesPerspectiveConfigurableFixture {
  selectConfigurable("Variables")
  return VariablesPerspectiveConfigurableFixture(
      ideFrameFixture,
      findConfigurable(VARIABLES_VIEW))
}
