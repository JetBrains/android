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

import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.InplaceButton
import com.intellij.util.ui.TimedDeadzone
import org.fest.swing.core.MouseButton
import org.fest.swing.timing.Pause
import java.awt.Container
import java.awt.Point
import javax.swing.JButton
import javax.swing.JLabel

open class BasePerspectiveConfigurableFixture protected constructor(
    override val ideFrameFixture: IdeFrameFixture,
    override val container: Container
) : IdeFrameContainerFixture {
  fun requireMinimizedModuleSelector() =
      finder().findByLabel(container, "Module: ", JButton::class.java)

  fun requireVisibleModuleSelector() =
      finder().find(container, matcher<JLabel> { it.text == "Modules" })


  fun minizeModulesList() {
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
}


