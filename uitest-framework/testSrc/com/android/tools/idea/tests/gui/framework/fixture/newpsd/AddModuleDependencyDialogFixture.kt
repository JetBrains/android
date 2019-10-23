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

import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.IdeFrameContainerFixture
import com.android.tools.idea.tests.gui.framework.findByType
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import org.fest.swing.core.Robot
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.fixture.JTreeFixture
import javax.swing.JDialog
import javax.swing.JTree

class AddModuleDependencyDialogFixture private constructor(
  override val container: JDialog,
  override val ideFrameFixture: IdeFrameFixture
) : IdeFrameContainerFixture, ContainerFixture<JDialog> {

  override fun target(): JDialog = container
  override fun robot(): Robot = ideFrameFixture.robot()

  fun toggleModule(moduleName: String) {
    val tree = JTreeFixture(robot(), robot().finder().findByType<JTree>(container))
    tree.clickPath(moduleName)
  }

  fun clickOk() = clickOkAndWaitDialogDisappear()

  fun clickCancel() = clickCancelAndWaitDialogDisappear()

  companion object {
    fun find(ideFrameFixture: IdeFrameFixture, title: String): AddModuleDependencyDialogFixture {
      val dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog::class.java, title))
      return AddModuleDependencyDialogFixture(dialog, ideFrameFixture)
    }
  }
}