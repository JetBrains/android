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
package com.android.tools.idea.gradle.task.runsGradle

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.hookExecuteTasks
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Rule
import org.junit.Test

class AndroidGradleTaskManagerTest {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun `app assembleDebug from root and app`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      val path = preparedProject.root
      val capturedRequests = project.hookExecuteTasks()
      val facadeManager = ExternalSystemFacadeManager.getInstance()
      val facade = facadeManager.getFacade(project, path.absolutePath, GradleConstants.SYSTEM_ID)
      val taskManager = facade.taskManager

      val externalSystemTaskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
      run {
        // 1) This is a common form used by Android Studio etc.
        val projectPath = path.absolutePath
        val settings = GradleExecutionSettings().apply {
          tasks = listOf(":app:assembleDebug")
        }
        taskManager.executeTasks(projectPath, externalSystemTaskId, settings)
      }
      run {
        // 2) This is a way in which tasks are invoked from the Gradle tool window and from Gradle run configurations, if configured this way.
        val projectPath = path.resolve("app").absolutePath
        val settings = GradleExecutionSettings().apply {
          tasks = listOf("assembleDebug")
        }
        taskManager.executeTasks(projectPath, externalSystemTaskId, settings)
      }

      expect.that(capturedRequests).hasSize(2)

      expect.that(capturedRequests.getOrNull(0)?.taskId).isSameAs(externalSystemTaskId)
      expect.that(capturedRequests.getOrNull(0)?.project).isSameAs(project)
      expect.that(capturedRequests.getOrNull(0)?.rootProjectPath).isEqualTo(path)
      expect.that(capturedRequests.getOrNull(0)?.gradleTasks).isEqualTo(listOf(":app:assembleDebug"))

      expect.that(capturedRequests.getOrNull(1)?.taskId).isSameAs(externalSystemTaskId)
      expect.that(capturedRequests.getOrNull(1)?.project).isSameAs(project)
      expect.that(capturedRequests.getOrNull(1)?.rootProjectPath).isEqualTo(path.resolve("app"))
      expect.that(capturedRequests.getOrNull(1)?.gradleTasks).isEqualTo(listOf("assembleDebug"))
    }
  }
}