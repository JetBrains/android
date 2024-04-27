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
package com.android.tools.idea.tests.gui.customview

import com.android.tools.idea.bleak.UseBleak
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.getSplitEditorFixture
import com.android.tools.idea.tests.gui.uibuilder.RenderTaskLeakCheckRule
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class CustomViewPreviewTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule()
  @Rule
  @JvmField
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()

  @Test
  @RunIn(TestGroup.UNRELIABLE)
  fun testOpenBuildAndClosePreview() {
    openBuildAndClosePreview(importProject())
  }

  @Test
  @UseBleak
  @RunIn(TestGroup.PERFORMANCE)
  fun testOpenBuildAndClosePreviewWithBleak() {
    val fixture = importProject()
    guiTest.runWithBleak { openBuildAndClosePreview(fixture) }
  }

  private fun importProject(): IdeFrameFixture =
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication")

  @Throws(Exception::class)
  private fun openBuildAndClosePreview(fixture: IdeFrameFixture) {
    val editor = fixture.editor
    val file = "app/src/main/java/google/simpleapplication/CustomViews.kt"

    editor.open(file)
    val multiRepresentationFixture = editor.getSplitEditorFixture().apply {
      setSplitMode()
      setRepresentation("Custom views")
      waitForRenderToFinish()
    }
    guiTest.robot().waitForIdle()
    fixture.invokeAndWaitForBuildAction("Build", "Make Project")

    multiRepresentationFixture.waitForRenderToFinish()
    guiTest.robot().waitForIdle()

    assertFalse(multiRepresentationFixture.hasRenderErrors())

    editor.closeFile(file)
  }
}