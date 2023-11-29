/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.resourceexplorer

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.ResourceExplorerFixture.Companion.find
import com.google.common.truth.Truth
import com.intellij.openapi.util.SystemInfo
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.KeyPressInfo
import org.fest.swing.core.MouseButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class OpenFileInResourceManagerTest {

  @Rule
  @JvmField
  val guiTest: GuiTestRule = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  /**
   * To verify resource manager - Open layout in Resource Manager
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: bded3387-abcd-4289-9ecf-82d6205f9d68
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. From the project tree, rightclick on the activity_main.xml file (Verify 1)
   *   2. Click ""Show In Resource Manager"" option (Verify 2)
   *   Verify:
   *   1. Check that "Show In Resource Manager" option appears
   *   2. The resource manager should open with the activity_main file selected
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  fun openFileInResourceManagerTest(){
    val ideFrame: IdeFrameFixture  = guiTest.importProjectAndWaitForProjectSyncToFinish("MultiAndroidModule")

    guiTest.waitForAllBackgroundTasksToBeCompleted()

    if (SystemInfo.isMac){
      // No support for rightclick in Mac. Load resource manager
      // using the keyboard shortcut ( Cmd + Shift + t)
      val keyPressInfo: KeyPressInfo = KeyPressInfo.keyCode(KeyEvent.VK_T)
        .modifiers(KeyEvent.VK_META, KeyEvent.VK_SHIFT)
      ideFrame.projectView
        .selectAndroidPane()
        .expand(30)
        .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "layout", "activity_main.xml")
        .pressAndReleaseKey(keyPressInfo)
    } else {
      ideFrame.projectView
        .selectAndroidPane()
        .expand(30)
        .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "layout", "activity_main.xml")
        .openFromContextualMenu({ find(guiTest.robot()) }, "Show In Resource Manager")
    }

    val resourceExplorerFixture = find(guiTest.robot())
    GuiTests.takeScreenshot(guiTest.robot(), "VerifyResourceManagerLoaded")
    val selectedTabIndex = resourceExplorerFixture.tabbedPane().target().selectedIndex
    Truth.assertThat(resourceExplorerFixture.tabbedPane().target().getTitleAt(selectedTabIndex)).matches("Layout")
  }
}