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
import com.android.tools.idea.tests.gui.framework.DialogContainerFixture
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.finder
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixupWaiting
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.idea.tests.gui.framework.waitForIdle
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Wait
import java.awt.Container
import javax.swing.JDialog

class ProjectStructureDialogFixture(
  val container: JDialog,
  private val ideFrameFixture: IdeFrameFixture
) : DialogContainerFixture {

  private val robot = ideFrameFixture.robot().fixupWaiting()
  override fun target(): JDialog = container
  override fun robot(): Robot = robot

  override fun maybeRestoreLostFocus() {
    ideFrameFixture.requestFocusIfLost()
  }

  fun clickOk() {
    // Changing the project structure can cause a Gradle build and Studio re-indexing.
    waitForSyncToFinish { clickOkAndWaitDialogDisappear() }
  }

  fun clickOkExpectConfirmation(): ErrorsReviewConfirmationDialogFixture {
    GuiTests.findAndClickOkButton(this)
    // Changing the project structure can cause a Gradle build and Studio re-indexing.
    return ErrorsReviewConfirmationDialogFixture.find(robot, "Problems Found")
  }

  fun clickCancel(): IdeFrameFixture {
    clickCancelAndWaitDialogDisappear()
    return ideFrameFixture
  }

  fun clickOk(waitForSync: Wait) {
    // Changing the project structure can cause a Gradle build and Studio re-indexing.
    ideFrameFixture.actAndWaitForGradleProjectSyncToFinish(waitForSync) { clickOkAndWaitDialogDisappear() }.also { waitForIdle() }
  }

  fun waitForSyncToFinish(actions: () -> Unit) {
    ideFrameFixture.actAndWaitForGradleProjectSyncToFinish { actions() }.also { waitForIdle() }
  }

  fun selectConfigurable(viewName: String): ProjectStructureDialogFixture {
    val sidePanel = GuiTests.waitUntilFound(robot(), container, Matchers.byType(SidePanel::class.java))
    val sidePanelList = sidePanel.list
    val listFixture = JListFixture(robot(), sidePanelList)
    listFixture.replaceCellReader { list, index -> sidePanel.descriptor.getTextFor(list.model.getElementAt(index) as SidePanel.PlaceData) }
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

fun IdeFrameFixture.openPsd(): ProjectStructureDialogFixture {
  waitAndInvokeMenuPath("File", "Project Structure...")
  return ProjectStructureDialogFixture.find(this)
}

internal fun DialogContainerFixture.clickOkAndWaitDialogDisappear() {
  GuiTests.findAndClickOkButton(this)
  waitForDialogToClose()
}

internal fun DialogContainerFixture.clickCancelAndWaitDialogDisappear() {
  GuiTests.findAndClickCancelButton(this)
  waitForDialogToClose()
}

internal fun DialogContainerFixture.clickButtonAndWaitDialogDisappear(text: String) {
  GuiTests.findAndClickButton(this, text)
  waitForDialogToClose()
}

fun DialogContainerFixture.waitForDialogToClose() {
  Wait
    .seconds(10)
    .expecting("dialog to disappear")
    .until { !target().isShowing }
  waitForIdle()
  maybeRestoreLostFocus()
}
