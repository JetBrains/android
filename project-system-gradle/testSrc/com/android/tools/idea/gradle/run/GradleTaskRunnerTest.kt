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

import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.collect.ArrayListMultimap
import com.google.common.truth.Expect
import com.intellij.openapi.application.runWriteActionAndWait
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path

@RunWith(JUnit4::class)
class GradleTaskRunnerTest : GradleIntegrationTest {

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun `successful and failed build`() {
    val projectRoot = prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") { project ->
      val appModule = project.gradleModule(":app")!!
      val gradleTaskRunner = GradleTaskRunner.newRunner(project)
      val tasksToRun = ArrayListMultimap.create<Path, String>().apply {
        put(projectRoot.toPath(), ":app:assembleDebug")
      }

      expect.that(
        gradleTaskRunner.run(
          arrayOf(appModule),
          tasksToRun,
          BuildMode.ASSEMBLE, listOf()
        )
      ).named("Successful build result").isTrue()

      // Set the activity file content to something that does not compile.
      runWriteActionAndWait {
        appModule
          .fileUnderGradleRoot("src/main/java/google/simpleapplication/MyActivity.java")!!
          .setBinaryContent("***THIS IS ERROR***".toByteArray())
      }

      expect.that(
        gradleTaskRunner.run(
          arrayOf(appModule),
          tasksToRun,
          BuildMode.ASSEMBLE, listOf()
        )
      ).named("Failed build result").isFalse()
    }
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @get:Rule
  var testName = TestName()

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf()
}