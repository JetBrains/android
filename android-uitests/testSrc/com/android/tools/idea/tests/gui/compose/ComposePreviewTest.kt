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
package com.android.tools.idea.tests.gui.compose

import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.compose.getNotificationsFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.getSplitEditorFixture
import com.android.tools.idea.tests.gui.uibuilder.RenderTaskLeakCheckRule
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import junit.framework.TestCase.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class ComposePreviewTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule()
  @Rule
  @JvmField
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()

  @Test
  @Throws(Exception::class)
  fun testOpenPreview() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication")

    // Open the main compose activity and check that the preview is present
    val editor = guiTest.ideFrame().editor
    editor.open("app/src/main/java/google/simpleapplication/MainActivity.kt")

    guiTest.ideFrame().invokeMenuPath("Build", "Make Project")
      .waitForBuildToFinish(BuildMode.ASSEMBLE)
    val composePreview = editor.getSplitEditorFixture()

    composePreview
      .waitForRenderToFinish()
      .getNotificationsFixture()
      .assertNoNotifications()

    assertFalse(composePreview.hasRenderErrors())

    // Now let's make a change on the source code and check that the notification displays
    editor.typeText("Random modification")

    guiTest.robot().waitForIdle()

    composePreview
      .getNotificationsFixture()
      .waitForNotificationContains("out of date")
  }
}
