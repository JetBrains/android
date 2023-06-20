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

import com.android.tools.idea.tests.gui.framework.DialogContainerFixture
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.find
import com.android.tools.idea.tests.gui.framework.findByType
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.TableView
import org.fest.swing.core.Robot
import org.fest.swing.driver.BasicJTableCellReader
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JTableFixture
import org.fest.swing.fixture.JTextComponentFixture
import org.fest.swing.timing.Wait
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JTable

class AddLibraryDependencyDialogFixture private constructor(
  val container: JDialog,
  val robot: Robot
) : DialogContainerFixture {

  override fun target(): JDialog = container
  override fun robot(): Robot = robot
  override fun maybeRestoreLostFocus() = Unit

  fun findSearchQueryTextBox(): JTextComponentFixture =
    JTextComponentFixture(robot(), robot().finder().findByType<JBTextField>(container))

  fun findSearchButton(): JButtonFixture =
    JButtonFixture(robot(), robot().finder().find<JButton>(container) { it.text == "Search" })

  fun findArtifactsView(): JTableFixture =
    JTableFixture(robot(), robot().finder().find<TableView<*>>(container) { it.columnCount == 3 })

  fun findVersionsView(waitUntilNotEmpty: Boolean = false): JTableFixture =
    JTableFixture(robot(), robot().finder().find<TableView<*>>(container) { it.columnCount == 1 })
      .also {
        if (waitUntilNotEmpty) {
          Wait
            .seconds(10)
            .expecting("Search completed")
            .until {
              it.rowCount() > 0
            }
        }
        it.replaceCellReader(object : BasicJTableCellReader() {
          override fun valueAt(table: JTable, row: Int, column: Int): String? =
            (table
              .prepareRenderer(table.getCellRenderer(row, column), row, column)
              as? SimpleColoredComponent)
              ?.toString()
              .orEmpty()
        })
      }

  fun findConfigurationCombo(): JComboBoxFixture =
    EditorComboBoxFixture(robot(), robot().finder().findByName(container, "configuration", JComboBox::class.java, true))

  fun clickOk() = clickOkAndWaitDialogDisappear()

  fun clickCancel() = clickCancelAndWaitDialogDisappear()

  companion object {
    fun find(robot: Robot, title: String): AddLibraryDependencyDialogFixture {
      val dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog::class.java, title))
      return AddLibraryDependencyDialogFixture(dialog, robot)
    }
  }
}