/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.tests.gui.framework.fixture.designer.getSplitEditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.isSplitEditor
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class ComposePreviewFlagDisabledTest {
  @get:Rule
  val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)

  @Before
  fun setUp() {
    StudioFlags.COMPOSE_PREVIEW.override(false)
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_PREVIEW.clearOverride()
  }

  @Test
  @RunIn(TestGroup.UNRELIABLE) // b/150637365
  fun noComposePreview() {
    val fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication")
    // Open the main compose activity and check that the preview is present
    val editor = fixture.editor
    val file = "app/src/main/java/google/simpleapplication/MainActivity.kt"
    editor.open(file)

    fixture.invokeMenuPath("Build", "Make Project")
      .waitForBuildToFinish(BuildMode.ASSEMBLE)

    // Either is not a split editor or the surface is not displaying
    assertTrue(!editor.isSplitEditor() || !editor.getSplitEditorFixture().hasDesignSurface)
  }
}