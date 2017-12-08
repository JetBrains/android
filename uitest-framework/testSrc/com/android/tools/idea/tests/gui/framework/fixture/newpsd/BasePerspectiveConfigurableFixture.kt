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
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture
import com.android.tools.idea.tests.gui.framework.fixture.ComboBoxActionFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testGuiFramework.fixtures.JBListPopupFixture
import com.intellij.ui.InplaceButton
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.TimedDeadzone
import org.fest.swing.core.MouseButton
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JTreeFixture
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Wait
import java.awt.Container
import java.awt.Point
import javax.swing.JButton
import javax.swing.JLabel

open class BasePerspectiveConfigurableFixture protected constructor(
    override val ideFrameFixture: IdeFrameFixture,
    override val container: Container
) : IdeFrameContainerFixture {

  fun minimizeModulesList() {
    val hideButton = finder().find(container, matcher<InplaceButton> { it.toolTipText == "Hide" })
    robot().pressMouse(hideButton, Point(3, 3), MouseButton.LEFT_BUTTON)
    Pause.pause(TimedDeadzone.DEFAULT.length.toLong())
    robot().releaseMouse(MouseButton.LEFT_BUTTON)
    robot().waitForIdle()
  }

  fun restoreModulesList() {
    val restoreButton = finder().find(container, matcher<ActionButton> { it.toolTipText == "Restore 'Modules' List" })
    robot().pressMouse(restoreButton, Point(3, 3), MouseButton.LEFT_BUTTON)
    Pause.pause(TimedDeadzone.DEFAULT.length.toLong())
    robot().releaseMouse(MouseButton.LEFT_BUTTON)
    robot().waitForIdle()
  }

  fun requireVisibleModuleSelector(block: TestBlock<ModuleSelectorFixture>) =
      block.runTestOn(requireVisibleModuleSelector())

  fun requireVisibleModuleSelector(): ModuleSelectorFixture {
    // Assert the module selector is visible.
    finder().find(container, matcher<JLabel> { it.text == "Modules" })

    return object : ModuleSelectorFixture {
      override fun requireModules(vararg moduleNames: String) {
        val tree = findModuleTree()

        fun childCount() = GuiQuery.get { tree.target().model.let { it.getChildCount(it.root) } }
        fun actualModuleNames() = moduleNames.indices.map { tree.valueAt(it) }

        assertThat(childCount()).named("Number of modules in the selector").isEqualTo(moduleNames.size)
        assertThat(actualModuleNames()).containsExactlyElementsIn(moduleNames.asIterable())
      }

      override fun requireSelectedModule(moduleName: String) {
        findModuleTree().requireSelection(moduleName)
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
                              matcher<ToolWindowHeader> { it.components.any { it is JLabel && it.text == "Modules" } })
                          .parent))
    }
  }

  fun requireMinimizedModuleSelector(block: TestBlock<ModuleSelectorFixture>) =
      block.runTestOn(requireMinimizedModuleSelector())

  fun requireMinimizedModuleSelector(): ModuleSelectorFixture {
    // Assert the module selector is minimized.
    finder().findByLabel<JButton>(container, "Module: ")

    return object : ModuleSelectorFixture {
      override fun requireSelectedModule(moduleName: String) {
        assertThat(findModuleComboBox().selectedItemText).isEqualTo(moduleName)
      }

      override fun requireModules(vararg moduleNames: String) {
        val comboBox = findModuleComboBox()
        comboBox.click()
        val listPopup = JBListPopupFixture.findListPopup(robot())
        for (moduleName in moduleNames) {
          listPopup.assertContainsAction(moduleName)
        }
        comboBox.click()
      }

      override fun selectModule(moduleName: String) {
        val comboBox = findModuleComboBox()
        comboBox.click()
        val listPopup = JBListPopupFixture.findListPopup(robot())
        listPopup.invokeAction(moduleName)
      }

      private fun findModuleComboBox() =
          ComboBoxActionFixture(
              robot(),
              finder().findByLabel<JButton>(container, "Module: "))
    }
  }
}


interface ModuleSelectorFixture {
  fun requireModules(vararg moduleNames: String)
  fun requireSelectedModule(moduleName: String)
  fun selectModule(moduleName: String)
}