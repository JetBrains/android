/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.task

import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.hookExecuteTasks
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Expect
import com.intellij.task.ProjectTaskManager
import org.jetbrains.annotations.SystemIndependent
import org.junit.Rule
import org.junit.Test
import java.io.File

class AndroidProjectTaskRunnerTest : GradleIntegrationTest {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath

  override fun getTestDataDirectoryWorkspaceRelativePath(): @SystemIndependent String = "tools/adt/idea/android/testData"
  override fun getAdditionalRepos(): Collection<File> = emptyList()

  @Test
  fun `build app module`() {
    val path = prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") { project ->
      val capturedRequests = project.hookExecuteTasks()
      val appModule = project.gradleModule(":app") ?: error(":app module not found")

      ProjectTaskManager.getInstance(project).build(appModule.getMainModule())

      expect.that(capturedRequests).hasSize(1)
      expect.that(capturedRequests.getOrNull(0)?.project).isSameAs(project)
      expect.that(capturedRequests.getOrNull(0)?.rootProjectPath).isEqualTo(path)
      expect.that(capturedRequests.getOrNull(0)?.gradleTasks).contains(":app:compileDebugSources")
    }
  }
}