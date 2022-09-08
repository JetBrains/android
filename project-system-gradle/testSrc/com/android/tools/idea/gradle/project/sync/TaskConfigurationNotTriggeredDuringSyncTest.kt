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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.junit.Rule
import org.junit.Test
import java.io.File

@RunsInEdt
class TaskConfigurationNotTriggeredDuringSyncTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testTasksAreNotConfiguredDuringSync() {
    // We do not allow Gradle tasks configuration by default on AS.
    assertThat(GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST).isEqualTo(true)

    val prepared = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)

    val failureMessage = "task should not be configured"
    val buildFile = prepared.root.resolve("app").resolve("build.gradle")
    buildFile.writeText(
      buildFile.readText() + """
          tasks.register("shouldNotBeConfigured", Test.class).configure {
              println($failureMessage)
          }
        """.trimIndent()
    )
    val outputLog = StringBuilder()
    prepared.open(updateOptions = { preparedProjectOptions -> preparedProjectOptions.copy(outputHandler = { outputLog.append(it) }) }) {}
    assertThat(outputLog.toString()).doesNotContain(failureMessage)
  }
}