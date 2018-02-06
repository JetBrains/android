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
package com.android.tools.idea.tests.gui.framework.bazel.fixture

import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import javax.swing.JMenuItem

class BazelConsoleToolWindowFixture(project: Project, robot: Robot) :  ToolWindowFixture("Blaze Console", project, robot) {
  val content: Content = UIUtil.invokeAndWaitIfNeeded(Computable<Content> {
    assert(!contents.isEmpty())
    contents[0]
  })

  fun hasSyncStarted() = try {
    myRobot
      .finder()
      .findByType(content.component, EditorComponentImpl::class.java, true)
      .text
      .contains("Syncing project:")
  } catch (e: ComponentLookupException) {
    false
  }

  fun clearBazelConsole() {
    activateAndWaitUntilIsVisible()
    myRobot.rightClick(content.component)
    myRobot.click(GuiTests.waitUntilShowing(myRobot, null, Matchers.byText(JMenuItem::class.java, "Clear All")))
  }
}