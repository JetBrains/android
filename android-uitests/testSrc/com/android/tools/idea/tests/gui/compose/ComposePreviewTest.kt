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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.compose.getNotificationsFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.getSplitEditorFixture
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.UseBleak
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.runWithBleak
import com.android.tools.idea.tests.gui.uibuilder.RenderTaskLeakCheckRule
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import junit.framework.TestCase.assertFalse
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class ComposePreviewTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)
  @Rule
  @JvmField
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()

  @Before
  fun setUp() {
    StudioFlags.COMPOSE_PREVIEW.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_PREVIEW.override(false)
  }

  @Test
  @Throws(Exception::class)
  fun testOpenAndClosePreview() {
    openAndClosePreview(guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication"))
  }

  @Test
  @UseBleak
  @RunIn(TestGroup.PERFORMANCE)
  @Throws(Exception::class)
  fun testOpenAndClosePreviewWithBleak() {
    val fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication")
    runWithBleak { openAndClosePreview(fixture) }
  }

  @Throws(Exception::class)
  private fun openAndClosePreview(fixture: IdeFrameFixture) {
    // Open the main compose activity and check that the preview is present
    val editor = fixture.editor
    val file = "app/src/main/java/google/simpleapplication/MainActivity.kt"
    editor.open(file)

    fixture.invokeMenuPath("Build", "Make Project")
      .waitForBuildToFinish(BuildMode.ASSEMBLE)
    val composePreview = editor.getSplitEditorFixture()

    composePreview
      .waitForRenderToFinish()
      .getNotificationsFixture()
      .assertNoNotifications()

    assertFalse(composePreview.hasRenderErrors())

    // Now let's make a change on the source code and check that the notification displays
    val modification = "Random modification"
    editor.typeText(modification)

    guiTest.robot().waitForIdle()

    composePreview
      .getNotificationsFixture()
      .waitForNotificationContains("out of date")

    // Undo modifications and close editor to return to the initial state
    editor.select("(${modification})")
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE)
    editor.closeFile(file)
  }
}
