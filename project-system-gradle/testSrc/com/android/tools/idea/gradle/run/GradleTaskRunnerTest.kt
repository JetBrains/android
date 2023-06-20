/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.run

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.android.tools.idea.testing.gradleModule
import com.google.common.truth.Expect
import com.intellij.openapi.application.runWriteActionAndWait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GradleTaskRunnerTest {

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun `successful and failed build`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      val appModule = project.gradleModule(":app")!!
      val tasksToRun =
        mapOf(preparedProject.root.toPath() to listOf(":app:assembleDebug"))

      expect.that(
        GradleTaskRunner.run(
          project,
          arrayOf(appModule),
          tasksToRun,
          BuildMode.ASSEMBLE, listOf()
        ).isBuildSuccessful
      ).named("Successful build result").isTrue()

      // Set the activity file content to something that does not compile.
      runWriteActionAndWait {
        appModule
          .fileUnderGradleRoot("src/main/java/google/simpleapplication/MyActivity.java")!!
          .setBinaryContent("***THIS IS ERROR***".toByteArray())
      }

      expect.that(
        GradleTaskRunner.run(
          project,
          arrayOf(appModule),
          tasksToRun,
          BuildMode.ASSEMBLE, listOf()
        )
          .isBuildSuccessful.let { !it }
      ).named("Failed build result").isTrue()
    }
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()
}