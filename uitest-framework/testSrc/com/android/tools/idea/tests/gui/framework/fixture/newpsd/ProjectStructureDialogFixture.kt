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

import com.android.tools.idea.structure.dialog.SidePanel
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.ui.navigation.Place
import org.fest.swing.core.Robot
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Wait
import java.awt.Container

import javax.swing.*
import java.util.Arrays

class ProjectStructureDialogFixture(
    override val container: JDialog,
    override val ideFrameFixture: IdeFrameFixture
) : IdeFrameContainerFixture, ContainerFixture<JDialog> {

  override fun target(): JDialog = container
  override fun robot(): Robot = ideFrameFixture.robot()

  fun clickOk(): IdeFrameFixture {
    clickOkAndWaitDialogDisappear()
    // Changing the project structure can cause a Gradle build and Studio re-indexing.
    return ideFrameFixture.waitForGradleProjectSyncToFinish()
  }

  fun clickCancel(): IdeFrameFixture {
    GuiTests.findAndClickCancelButton(this)
    Wait.seconds(10).expecting("dialog to disappear").until { !target().isShowing }
    return ideFrameFixture
  }

  fun clickOk(waitForSync: Wait): IdeFrameFixture {
    clickOkAndWaitDialogDisappear()
    return ideFrameFixture.waitForGradleProjectSyncToFinish(waitForSync)
  }

  private fun clickOkAndWaitDialogDisappear() {
    GuiTests.findAndClickOkButton(this)
    Wait.seconds(10).expecting("dialog to disappear").until { !target().isShowing }
  }

  fun selectConfigurable(viewName: String): ProjectStructureDialogFixture {
    val sidePanel = GuiTests.waitUntilFound(robot(), container, Matchers.byType(SidePanel::class.java))
    val sidePanelList = sidePanel.list
    val listFixture = JListFixture(robot(), sidePanelList)
    listFixture.replaceCellReader { list, index -> sidePanel.descriptor.getTextFor(list.model.getElementAt(index) as Place) }
    println(Arrays.toString(listFixture.contents()))
    listFixture.clickItem(viewName)
    return this
  }

  internal fun findConfigurable(name: String): Container =
      finder()
          .findByName(container, name) as Container


  companion object {
    fun find(ideFrameFixture: IdeFrameFixture): ProjectStructureDialogFixture {
      val dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog::class.java, "Project Structure"))
      return ProjectStructureDialogFixture(dialog, ideFrameFixture)
    }
  }
}

