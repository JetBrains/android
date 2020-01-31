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
package com.android.tools.idea.tests.gui.naveditor

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.ResourceExplorerDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlDesignSurfaceFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.event.KeyEvent.VK_DELETE

@RunWith(GuiTestRemoteRunner::class)
class HostPanelTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Test
  fun testUpdateHostPanel() {
    val frame = guiTest.importProject("Navigation").waitForGradleProjectSyncToFinish()

    // Open file as XML and switch to design tab, wait for successful render
    val hostPanel = frame
      .editor
      .open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true)
      .waitForRenderToFinish()
      .hostPanel()
      .waitForHostList()

    assertThat(hostPanel.waitForHostList().components).isEmpty()

    frame
      .editor
      .open("app/src/main/res/layout/activity_main.xml")
      .getLayoutEditor(true)
      .dragComponentToSurface("Containers", "NavHostFragment")

    val dialog = ResourceExplorerDialogFixture.find(guiTest.robot())
    dialog.resourceExplorer.searchField.enterText("mobile_navigation")
    dialog.resourceExplorer.selectResource("mobile_navigation")
    dialog.clickOk()

    frame
      .editor
      .open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true)
      .waitForRenderToFinish()

    assertThat(hostPanel.waitForHostList().components).isNotEmpty()

    val nlFixture = frame
      .editor
      .open("app/src/main/res/layout/activity_main.xml")
      .getLayoutEditor(true)
      .surface as NlDesignSurfaceFixture

    nlFixture.findView("fragment", 0).click()
    guiTest.robot().pressAndReleaseKey(VK_DELETE, 0)

    frame
      .editor
      .open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true)
      .waitForRenderToFinish()

    assertThat(hostPanel.waitForHostList().components).isEmpty()
  }
}