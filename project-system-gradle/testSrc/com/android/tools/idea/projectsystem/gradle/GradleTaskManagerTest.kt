/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestDef
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.google.common.truth.Truth
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

data class GradleTaskManagerTest(
  override val name: String,
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
  val test: (Project) -> Unit
) : SyncedProjectTestDef {

  companion object {
    val tests: List<GradleTaskManagerTest> = listOf(
      GradleTaskManagerTest(
        name = "simpleApplication gradle task manager",
        testProject = TestProject.SIMPLE_APPLICATION,
      ) { project ->
        val id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, project)
        val settings = GradleProjectSystemUtil.getOrCreateGradleExecutionSettings(project)
        val sb = StringBuilder()
        val listener = object : ExternalSystemTaskNotificationListener {
          override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
            sb.append(text)
          }
        }
        GradleTaskManager().executeTasks(id, listOf("tasks"), project.basePath!!, settings, null, listener)
        val output = sb.toString()
        Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        Truth.assertThat(output).contains("wrapper - Generates Gradle wrapper files.") // canary output
        Truth.assertThat(output).doesNotContain("FAILURE")
      }
    )
  }

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun runTest(root: File, project: Project) {
    test(project)
  }
}