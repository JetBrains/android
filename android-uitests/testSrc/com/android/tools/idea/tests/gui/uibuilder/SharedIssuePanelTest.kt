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
package com.android.tools.idea.tests.gui.uibuilder

import com.android.tools.idea.bleak.UseBleak
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.ProblemsPaneFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * UI test for the visualization tool window
 */
@RunWith(GuiTestRemoteRunner::class)
class SharedIssuePanelTest {
  @JvmField
  @Rule
  val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)

  @JvmField
  @Rule
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()

  private val title = "Layout and Qualifiers"

  @Test
  @UseBleak
  @RunIn(TestGroup.UNRELIABLE)
  @Throws(Exception::class)
  fun checkSharedIssuePanelVisibilityWithBleak() {
    val ideFixture = guiTest.importSimpleApplication()
    guiTest.runWithBleak { checkSharedIssuePanelVisibility(ideFixture.editor, ProblemsPaneFixture(ideFixture)) }
  }

  private fun checkSharedIssuePanelVisibility(editor: EditorFixture, problemsPane: ProblemsPaneFixture) {
    val layoutFile = "app/src/main/res/layout/frames.xml"
    val javaFile = "app/src/main/java/google/simpleapplication/MyActivity.java"

    problemsPane.activate()

    editor.open(layoutFile).layoutEditor.waitForRenderToFinish()
    assertThat(problemsPane.isTabSelected(title)).isTrue()

    editor.open(javaFile)
    assertThat(problemsPane.isTabExist(title)).isFalse()

    // Test switching back. The tab should appear but the selected tab is still "Current File" tab because the layout file is opened
    // already.
    editor.open(layoutFile)
    assertThat(problemsPane.isTabExist(title)).isTrue()

    editor.closeFile(layoutFile).closeFile(javaFile)
  }
}
