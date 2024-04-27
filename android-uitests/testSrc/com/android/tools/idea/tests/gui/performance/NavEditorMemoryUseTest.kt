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
package com.android.tools.idea.tests.gui.performance

import com.android.tools.idea.bleak.IgnoreList
import com.android.tools.idea.bleak.IgnoreListEntry
import com.android.tools.idea.bleak.StudioBleakOptions
import com.android.tools.idea.bleak.UseBleak
import com.android.tools.idea.bleak.runWithBleak
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
@RunIn(TestGroup.PERFORMANCE)
class NavEditorMemoryUseTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule().withTimeout(6, TimeUnit.MINUTES)

  /**
   * Opens and closes the designer tab for a navigation resource file.
   */
  @Test
  @UseBleak
  fun openAndCloseTab() {
    val ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("Navigation")
    guiTest.runWithBleak {
      ideFrame.editor
        .open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
        .getLayoutEditor()
        .waitForRenderToFinish()
      ideFrame.editor.close()
    }
  }

  /**
   * Adds a new destination to the navigation design surface from the add destination menu,
   * then deletes the new destination.
   */
  @Test
  @UseBleak
  fun addDestination() {
    val bleakOptions = StudioBleakOptions.defaultsWithAdditionalIgnoreList(
      IgnoreList(listOf(
        // Once we start the test, the KeyboardFocusManager will retain a reference to the current selected panel.
        // Since we do not close the editor, this makes sense and it is not a leak.
        IgnoreListEntry { it.leaktrace.referenceMatches(1, "com.intellij.ide.IdeKeyboardFocusManager", "focusOwner") },
      ))
    )
    val navSurface = guiTest.importProjectAndWaitForProjectSyncToFinish("Navigation")
      .editor
      .open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
      .layoutEditor
      .waitForRenderToFinish()
      .navSurface

    navSurface.click()

    runWithBleak(bleakOptions) {
      navSurface.openAddDestinationMenu()
        .waitForContents()
        .selectDestination("fragment_my")
      navSurface.waitForRenderToFinish()
      navSurface.click()
      guiTest.robot().waitForIdle()
      // Restore the state to the starting point
      guiTest.ideFrame().invokeMenuPath("Edit","Undo Add fragment_my")
    }
  }
}