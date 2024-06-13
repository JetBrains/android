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
package com.android.tools.idea.gradle.project.sync.runsGradle

import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class TaskConfigurationNotTriggeredDuringSyncTest {
  private var originalFlagValue : Boolean? = null

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Before
  fun saveOriginalFlagValue() {
    originalFlagValue = GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST
  }

  @After
  fun restoreFlag() {
    GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = originalFlagValue!!
  }

  @Test
  fun testTasksAreNotConfiguredDuringSync() {
    // We do not allow Gradle tasks configuration by default on AS.
    assertThat(GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST).isEqualTo(true)

    val prepared = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)

    val taskRegisteredMessage = "shouldNotBeConfigured task is registered"
    val projectsConfigured = "projects configured"
    val failureMessage = "task should not be configured"
    val taskRegistrationFailure = "lint task should not be registered"
    val buildFile = prepared.root.resolve("app").resolve("build.gradle")
    buildFile.appendText("""
      
          tasks.register("shouldNotBeConfigured", Test.class).configure {
            println("$failureMessage")
          }
          println("$taskRegisteredMessage")
          project.gradle.projectsEvaluated {
              println("$projectsConfigured")
              if (tasks.names.contains("lintDebug")) println("$taskRegistrationFailure")

          }
        """.trimIndent())
    val outputLog = StringBuilder()
    prepared.open(updateOptions = { preparedProjectOptions -> preparedProjectOptions.copy(outputHandler = { outputLog.append(it) }) }) {}
    assertThat(outputLog.toString()).contains(projectsConfigured)
    assertThat(outputLog.toString()).contains(taskRegisteredMessage)
    assertThat(outputLog.toString()).doesNotContain(failureMessage)
    assertThat(outputLog.toString()).doesNotContain(taskRegistrationFailure)
  }

  @Test
  fun testTasksAreConfiguredDuringSync() {
    GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = false

    val prepared = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)

    val taskRegisteredMessage = "shouldNotBeConfigured task is registered"
    val projectsConfigured = "projects configured"
    val failureMessage = "task should not be configured"
    val taskRegistrationFailure = "lint task should not be registered"
    val buildFile = prepared.root.resolve("app").resolve("build.gradle")
    buildFile.appendText("""
      
          tasks.register("shouldNotBeConfigured", Test.class).configure {
            println("$failureMessage")
          }
          println("$taskRegisteredMessage")
          project.gradle.projectsEvaluated {
              println("$projectsConfigured")
              if (tasks.names.contains("lintDebug")) println("$taskRegistrationFailure")

          }
        """.trimIndent())
    val outputLog = StringBuilder()
    prepared.open(updateOptions = { preparedProjectOptions -> preparedProjectOptions.copy(outputHandler = { outputLog.append(it) }) }) {}
    assertThat(outputLog.toString()).contains(projectsConfigured)
    assertThat(outputLog.toString()).contains(taskRegisteredMessage)
    assertThat(outputLog.toString()).contains(failureMessage)
    assertThat(outputLog.toString()).contains(taskRegistrationFailure)
  }

}