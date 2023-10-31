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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.idea.tests.gui.framework.DialogContainerFixture
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.findByType
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.openapi.ui.ComboBox
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JTextComponentFixture
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JDialog

class ExtractVariableFixture(
  val container: JDialog,
  val robot: Robot
): DialogContainerFixture {
  override fun target() = container
  override fun robot() = robot
  override fun maybeRestoreLostFocus() = Unit

  fun clickOk() = clickOkAndWaitDialogDisappear()

  fun clickCancel() = clickCancelAndWaitDialogDisappear()

  fun findName() = JTextComponentFixture(robot, "name")
  fun findValue(): JComboBoxFixture {
    val valueEditor = robot.finder().findByName("value") as Container
    return JComboBoxFixture(robot, robot.finder().findByType<ComboBox<*>>(valueEditor))
  }
  fun findScope() = JComboBoxFixture(robot, "scope")

  companion object {
    fun find(robot: Robot): ExtractVariableFixture {
      val dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog::class.java, "Extract Variable"))
      return ExtractVariableFixture(dialog, robot)
    }
  }


}
