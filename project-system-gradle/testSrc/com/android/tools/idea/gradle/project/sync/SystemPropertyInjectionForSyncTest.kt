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

import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SystemPropertyInjectionForSyncTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  /** A regression test for http://b/264933295. */
  @Test
  fun testJnaClasspathIsNotInjected() {
    val prepared = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)

    val buildFile = prepared.root.resolve("app").resolve("build.gradle")
    buildFile.writeText(
      buildFile.readText() + """
          if (System.getProperty("jna.classpath") != null) throw new RuntimeException("jna.classpath should not be injected") 
        """.trimIndent()
    )

    // Just make sure we sync successfully
    prepared.open {}
  }

  @Test
  fun testStudioVersionInjectedForSync() {
    val prepared = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)

    val buildFile = prepared.root.resolve("app").resolve("build.gradle")
    buildFile.writeText(
      buildFile.readText() + """
          if (!project.providers.gradleProperty("android.studio.version").isPresent()) {
            throw new RuntimeException("Studio version should be injected")
          }
        """.trimIndent()
    )

    val listener = object : ExternalSystemTaskNotificationListenerAdapter() {
      var successDetected = false
      var taskOutput = StringBuilder()

      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        taskOutput.append(text)
      }

      override fun onSuccess(id: ExternalSystemTaskId) {
        successDetected = true
      }
    }

    // Opening project makes sure we inject the version during sync
    prepared.open {
      // Running a task makes sure we inject the version during build
      AndroidGradleTaskManager().executeTasks(
        ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
        listOf("help"),
        project.basePath!!,
        null,
        null,
        listener
      )
    }
    if (!listener.successDetected) {
      expect.fail("Task should succeed, but it failed with:\n %s", listener.taskOutput.toString())
    }
  }
}