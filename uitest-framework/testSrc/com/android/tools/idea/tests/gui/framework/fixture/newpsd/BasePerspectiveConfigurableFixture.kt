/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.findByLabel
import com.android.tools.idea.tests.gui.framework.findByType
import com.android.tools.idea.tests.gui.framework.finder
import com.android.tools.idea.tests.gui.framework.fixture.ComboBoxActionFixture
import com.android.tools.idea.tests.gui.framework.matcher
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.idea.tests.gui.framework.tryFind
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.InplaceButton
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.TimedDeadzone
import org.fest.swing.core.MouseButton
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.fixture.JTabbedPaneFixture
import org.fest.swing.fixture.JTreeFixture
import org.fest.swing.timing.Pause
import java.awt.Container
import java.awt.Point
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTabbedPane

open class BasePerspectiveConfigurableFixture protected constructor(
  val robot: Robot,
  val container: Container
) : ContainerFixture<Container> {

  override fun target(): Container = container
  override fun robot(): Robot = robot

  fun minimizeModulesList() {
    val hideButton =
      finder().find(
        finder().find(
          container,
          matcher<ToolWindowHeader> { toolWindowHeader ->
            !finder().findAll(toolWindowHeader, matcher<JLabel> { it.text == "Modules" }).isEmpty()
          }
        ),
        matcher<InplaceButton> { it.toolTipText == "Hide" })
    robot().pressMouse(hideButton, Point(3, 3), MouseButton.LEFT_BUTTON)
    try {
      Pause.pause(TimedDeadzone.DEFAULT.length.toLong() + 1)
    } finally {
      robot().releaseMouse(MouseButton.LEFT_BUTTON)
    }
  }

  fun restoreModulesList() {
    val restoreButton = finder().find(container, matcher<ActionButton> { it.accessibleContext.accessibleName == "Restore 'Modules' List" })
    robot().pressMouse(restoreButton, Point(3, 3), MouseButton.LEFT_BUTTON)
    try {
      Pause.pause(TimedDeadzone.DEFAULT.length.toLong() + 1)
    } finally {
      robot().releaseMouse(MouseButton.LEFT_BUTTON)
    }
  }

  fun isModuleSelectorMinimized() = tryFind { doFindModuleSelector() } == null

  fun findModuleSelector(): ModuleSelectorFixture =
      if (isModuleSelectorMinimized()) findMinimizedModuleSelector() else findVisibleModuleSelectorFixture()

  private fun findVisibleModuleSelectorFixture(): ModuleSelectorFixture {

    return object : ModuleSelectorFixture {

      override fun modules(): List<String> {
        val tree = findModuleTree()
        return GuiQuery.get {
          val treeModel = tree.target().model
          val count = treeModel.getChildCount(treeModel.root)
          (0 until count).map { tree.valueAt(it).orEmpty() }
        }!!
      }

      override fun selectedModule(): String {
        val tree = findModuleTree()
        return GuiQuery.get { tree.target().selectionPath.lastPathComponent.toString() }!!
      }

      override fun selectModule(moduleName: String) {
        findModuleTree().selectPath(moduleName)
      }

      private fun findModuleTree() =
          JTreeFixture(
              robot(),
              finder()
                  .findByType<Tree>(
                      root = finder()
                          .find(
                            container,
                            matcher<ToolWindowHeader> { toolWindowHeader -> toolWindowHeader.components.any { it is JLabel && it.text == "Modules" } })
                          .parent))
    }
  }

  private fun findMinimizedModuleSelector(): ModuleSelectorFixture {
    return object : ModuleSelectorFixture {
      override fun modules(): List<String> {
        val comboBox = findModuleComboBox()
        return comboBox.items()
      }

      override fun selectedModule(): String {
        val comboBox = findModuleComboBox()
        return comboBox.selectedItemText
      }

      override fun selectModule(moduleName: String) {
        val comboBox = findModuleComboBox()
        comboBox.selectItem(moduleName)
        robot().pressAndReleaseKey(KeyEvent.VK_ENTER)
      }

      private fun findModuleComboBox() =
          ComboBoxActionFixture(
              robot(),
              finder().findByLabel<JButton>(container, "Module: "))
    }
  }

  private fun doFindModuleSelector() = finder().find(container, matcher<JLabel> { it.text == "Modules" })
}

internal inline fun <T> BasePerspectiveConfigurableFixture.selectTab(tabName: String, fixtureBuilder: (JTabbedPane) -> T): T {
  val tabbedPane = GuiTests.waitUntilFound(robot(), container, Matchers.byType(JTabbedPane::class.java))
  JTabbedPaneFixture(robot(), tabbedPane).selectTab(tabName)
  return fixtureBuilder(tabbedPane)
}

interface ModuleSelectorFixture {
  fun modules(): List<String>
  fun selectedModule(): String
  fun selectModule(moduleName: String)
}
