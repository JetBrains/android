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
package com.android.tools.idea.tests.gui.gradle

import com.android.SdkConstants
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests.refreshFiles
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(GuiTestRemoteRunner::class)
class MultiProjectSyncTest {

  @Rule
  @JvmField
  val guiTest = MultiProjectGuiTestRule()

  private lateinit var projectPath: File

  @Before
  fun setup() {
    projectPath = guiTest.prepareMultipleLinkedProjects()
  }

  @Test
  fun testMultipleLinkedProjects() {
    // This test reproduces issues like https://issuetracker.google.com/129612500
    withProjectOpenAndSynced("secondapp") {
      assertThat(moduleNames).containsExactly("My_Second_App", "app", "mysharedlibrary")
    }
    withProjectOpenAndSynced("firstapp") {
      assertThat(moduleNames).containsExactly("My_First_App", "app", "mysharedlibrary")
      // Modify the shared module's build file to cause sync on open.
      editor.open("../shared/mysharedlibrary/build.gradle").typeText("\n")
      invokeMenuPath("File", "Save All")
      invokeMenuPath("File", "Sync Project with Gradle Files")
      waitForGradleProjectSyncToFinish()
      assertThat(moduleNames).containsExactly("My_First_App", "app", "mysharedlibrary")
    }
    withProjectOpenAndSynced("secondapp") {
      assertThat(moduleNames).containsExactly("My_Second_App", "app", "mysharedlibrary")
    }
  }

  private fun withProjectOpenAndSynced(projectSubDir: String, action: IdeFrameFixture.() -> Unit) {
    guiTest.openProject(projectPath.resolve(projectSubDir)).run {
      waitForGradleProjectSyncToFinish()
      action()
      closeProject()
    }
  }

  class MultiProjectGuiTestRule : GuiTestRule() {
    fun prepareMultipleLinkedProjects(): File {
      val projectPath = copyProjectBeforeOpening("MultipleLinked")
      createGradleWrapper(projectPath.resolve("firstapp"), SdkConstants.GRADLE_LATEST_VERSION)
      createGradleWrapper(projectPath.resolve("secondapp"), SdkConstants.GRADLE_LATEST_VERSION)
      AndroidGradleTests.updateGradleVersions(projectPath)
      updateLocalProperties(projectPath.resolve("firstapp"))
      updateLocalProperties(projectPath.resolve("secondapp"))
      refreshFiles()
      return projectPath
    }
  }
}

