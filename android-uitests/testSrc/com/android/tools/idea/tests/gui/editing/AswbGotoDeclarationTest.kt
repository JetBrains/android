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
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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

  private fun EditorFixture.gotoDeclaration(resourceId: String): EditorFixture {
    select("($resourceId)")
    invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION)
    return this
  }

  private fun EditorFixture.waitUntilCurrentFileIs(waitTimeInSecond: Long, targetResourcePath: String): EditorFixture {
    Wait.seconds(waitTimeInSecond).expecting("Navigate to target resource file $targetResourcePath")
        .until { currentFile!!.canonicalPath == targetResourcePath }
    return this
  }

  /**
   * Open a file
   *
   * @param relativePath the project-relative path
   */
  private fun openFile(relativePath: String): EditorFixture {
    return guiTest.ideFrame().editor.open(relativePath)
  }

  @Test
  @TargetBuildSystem(TargetBuildSystem.BuildSystem.BAZEL)
  fun gotoDeclaration_withExternalResources() {
    val srcPath = "../SimpleBazelApplication/java/com/foo/gallery/activities/MainActivity.java"
    val targetResourcePath = guiTest.ideFrame()
        .findFileByRelativePath("../SimpleBazelApplication/java/com/foo/libs/res/res/values/styles.xml", true)!!
        .canonicalPath
    val resourceId = "R.style.Base_Highlight"
    try {
      openFile(srcPath).gotoDeclaration(resourceId).waitUntilCurrentFileIs(2, targetResourcePath!!)
    } catch(e: Exception) {
      // Time out exception due to b/71820265, try-catch should be removed after fixing it.
      Assert.assertThat(e, instanceOf(WaitTimedOutError::class.java))
    }
  }

  @Test
  @TargetBuildSystem(TargetBuildSystem.BuildSystem.BAZEL)
  fun gotoDeclaration_withLocalResources() {
    val srcPath = "../SimpleBazelApplication/java/com/foo/gallery/activities/MainActivity.java"
    val targetResourcePath = guiTest.ideFrame()
        .findFileByRelativePath("../SimpleBazelApplication/java/com/foo/gallery/activities/res/menu/settings.xml", true)!!
        .canonicalPath
    val resourceId = "R.menu.settings"
    openFile(srcPath).gotoDeclaration(resourceId).waitUntilCurrentFileIs(2, targetResourcePath!!)
  }
}