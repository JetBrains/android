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
package com.android.tools.idea.tests.gui.editing

import com.android.tools.idea.tests.gui.framework.BuildSpecificGuiTestRunner
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Wait
import org.hamcrest.core.IsInstanceOf.instanceOf
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*

@RunIn(TestGroup.EDITING)
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(BuildSpecificGuiTestRunner.Factory::class)
class AswbGotoDeclarationTest {
  @get:Rule
  val guiTest = GuiTestRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): List<TargetBuildSystem.BuildSystem> = Collections.singletonList(TargetBuildSystem.BuildSystem.BAZEL)
  }

  @Before
  fun setUp() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleBazelApplication", "java/com/foo/gallery/BUILD")
  }

  @Test
  @TargetBuildSystem(TargetBuildSystem.BuildSystem.BAZEL)
  fun gotoDeclaration_withExternalResources() {
    try {
      guiTest.ideFrame()
        .editor
        .open("../SimpleBazelApplication/java/com/foo/gallery/activities/MainActivity.java")
        .moveBetween("R.style", ".Base_Highlight")
        .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION)
      Wait.seconds(20).expecting("file to open")
        .until({guiTest.ideFrame().editor.currentFileName.equals("styles.xml")})
    } catch(e: Exception) {
      // Time out exception due to b/71820265, try-catch should be removed after fixing it.
      Assert.assertThat(e, instanceOf(WaitTimedOutError::class.java))
    }
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/76176149
  @Test
  @TargetBuildSystem(TargetBuildSystem.BuildSystem.BAZEL)
  fun gotoDeclaration_withLocalResources() {
    guiTest.ideFrame()
      .editor
      .open("../SimpleBazelApplication/java/com/foo/gallery/activities/MainActivity.java")
      .moveBetween("R.menu", ".settings")
      .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION)
    Wait.seconds(20).expecting("file to open")
      .until({guiTest.ideFrame().editor.currentFileName.equals("settings.xml")})
  }
}