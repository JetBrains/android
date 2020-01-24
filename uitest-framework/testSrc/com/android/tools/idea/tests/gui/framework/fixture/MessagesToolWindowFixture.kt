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

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.fest.swing.core.Robot

private const val TOOL_WINDOW_ID = "Messages"

/**
 * Fixture for the Messages tool window in Studio.
 */
class MessagesToolWindowFixture(project: Project, robot: Robot) : ToolWindowFixture(TOOL_WINDOW_ID, project, robot) {

  companion object {

    /**
     * Find the Messages tool window if it is has been registered.
     */
    fun ifExists(project: Project, robot: Robot): MessagesToolWindowFixture? {
      if (ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) == null) {
        return null
      }
      return MessagesToolWindowFixture(project, robot)
    }
  }
}