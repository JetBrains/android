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

import com.android.tools.idea.emulator.EmulatorView
import com.android.tools.idea.emulator.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.intellij.openapi.project.Project
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.timing.Wait
import java.util.concurrent.TimeoutException
import javax.swing.JPanel

/**
 * Test fixture for manipulating the Emulator tool window.
 */
class EmulatorToolWindowFixture(project: Project, robot: Robot) : ToolWindowFixture(RUNNING_DEVICES_TOOL_WINDOW_ID, project, robot) {

  init {
    activate()
    waitUntilIsVisible()
  }

  constructor(ideFrame: IdeFrameFixture) : this(ideFrame.project, ideFrame.robot())

  fun waitForEmulatorViewToShow(wait: Wait): EmulatorView {
    var emulatorView: EmulatorView? = null
    wait.expecting("emulator view to show")
      .until {
        emulatorView = findEmulatorView()
        return@until emulatorView != null
      }
    return emulatorView ?: throw TimeoutException()
  }

  private fun findEmulatorView(): EmulatorView? {
    try {
      return myRobot.finder().findByType(contentPanel, EmulatorView::class.java)
    }
    catch (e: ComponentLookupException) {
      return null
    }
  }

  private val contentPanel: JPanel
    get() = myToolWindow.contentManager.component as JPanel
}