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

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTestRunnerFactory
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import com.google.common.collect.ImmutableList
import org.fest.swing.timing.Wait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(GuiTestRunnerFactory::class)
class AswbGotoDeclarationTest {
  @get:Rule
  val guiTest = GuiTestRule()

  companion object {
    @Parameterized.Parameters
    @JvmStatic
    fun data(): Iterable<*> {
      return ImmutableList.of<TargetBuildSystem.BuildSystem>(TargetBuildSystem.BuildSystem.BAZEL)
    }
  }

  @Parameterized.Parameter
  var myBuildSystem: TargetBuildSystem.BuildSystem? = null

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
   * @param relativePath the project-relative path (with /, not File.separator, as the path separator)
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
    openFile(srcPath).gotoDeclaration(resourceId).waitUntilCurrentFileIs(2, targetResourcePath!!)
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