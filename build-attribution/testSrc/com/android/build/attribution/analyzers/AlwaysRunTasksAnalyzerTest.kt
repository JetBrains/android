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
package com.android.build.attribution.analyzers

import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.BuildAttributionManagerImpl
import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.getSuccessfulResult
import com.android.tools.idea.gradle.dsl.utils.FN_GRADLE_PROPERTIES
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

class AlwaysRunTasksAnalyzerTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testAlwaysRunTasksAnalyzer() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BUILD_ANALYZER_CHECK_ANALYZERS)

    FileUtil.appendToFile(
      File(preparedProject.root, FN_GRADLE_PROPERTIES),
      "org.gradle.unsafe.configuration-cache=true"
    )

    preparedProject.runTest {
      invokeTasks("clean", "lintDebug")

      val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
      val results = buildAnalyzerStorageManager.getSuccessfulResult()
      var alwaysRunTasks = results.getAlwaysRunTasks().sortedBy { it.taskData.taskName }

      assertThat(alwaysRunTasks).hasSize(3)

      assertThat(alwaysRunTasks[0].taskData.getTaskPath()).isEqualTo(":app:alwaysRunningBuildSrcTask")
      assertThat(alwaysRunTasks[0].taskData.taskType).isEqualTo("org.example.buildsrc.AlwaysRunningBuildSrcTask")
      assertThat(alwaysRunTasks[0].taskData.originPlugin.toString()).isEqualTo("buildSrc plugin org.example.buildsrc.AlwaysRunningBuildSrcPlugin")
      assertThat(alwaysRunTasks[0].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)

      assertThat(alwaysRunTasks[1].taskData.getTaskPath()).isEqualTo(":app:alwaysRunningTask")
      assertThat(alwaysRunTasks[1].taskData.taskType).isEqualTo("AlwaysRunTask")
      assertThat(alwaysRunTasks[1].taskData.originPlugin.toString()).isEqualTo("binary plugin AlwaysRunTasksPlugin")
      assertThat(alwaysRunTasks[1].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS)

      assertThat(alwaysRunTasks[2].taskData.getTaskPath()).isEqualTo(":app:alwaysRunningTask2")
      assertThat(alwaysRunTasks[2].taskData.taskType).isEqualTo("AlwaysRunTask")
      assertThat(alwaysRunTasks[2].taskData.originPlugin.toString()).isEqualTo("binary plugin AlwaysRunTasksPlugin")
      assertThat(alwaysRunTasks[2].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)


      // configuration cached run
      invokeTasks("clean", "lintDebug")

      alwaysRunTasks =
        buildAnalyzerStorageManager.getSuccessfulResult().getAlwaysRunTasks().sortedBy { it.taskData.taskName }

      // lint analysis runs on every task intentionally, it should be filtered out at this point even in a config-cached run
      assertThat(alwaysRunTasks).hasSize(3)

      assertThat(alwaysRunTasks[0].taskData.getTaskPath()).isEqualTo(":app:alwaysRunningBuildSrcTask")
      assertThat(alwaysRunTasks[0].taskData.taskType).isEqualTo("org.example.buildsrc.AlwaysRunningBuildSrcTask")
      assertThat(alwaysRunTasks[0].taskData.originPlugin.toString()).isEqualTo("unknown plugin")
      assertThat(alwaysRunTasks[0].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)

      assertThat(alwaysRunTasks[1].taskData.getTaskPath()).isEqualTo(":app:alwaysRunningTask")
      assertThat(alwaysRunTasks[1].taskData.taskType).isEqualTo("AlwaysRunTask")
      assertThat(alwaysRunTasks[1].taskData.originPlugin.toString()).isEqualTo("unknown plugin")
      assertThat(alwaysRunTasks[1].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS)

      assertThat(alwaysRunTasks[2].taskData.getTaskPath()).isEqualTo(":app:alwaysRunningTask2")
      assertThat(alwaysRunTasks[2].taskData.taskType).isEqualTo("AlwaysRunTask")
      assertThat(alwaysRunTasks[2].taskData.originPlugin.toString()).isEqualTo("unknown plugin")
      assertThat(alwaysRunTasks[2].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)
    }
  }

  @Test
  @Ignore("b/179137380")
  fun testAlwaysRunTasksAnalyzerWithSuppressedWarning() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BUILD_ANALYZER_CHECK_ANALYZERS)

    preparedProject.runTest {
      BuildAttributionWarningsFilter.getInstance(project).suppressAlwaysRunTaskWarning("AlwaysRunningTask", "AlwaysRunTasksPlugin")

      invokeTasks("assembleDebug")

      val buildAttributionManager = project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl
      assertThat(buildAttributionManager.analyzersProxy.alwaysRunTasksAnalyzer.result.alwaysRunTasks).isEmpty()
    }
  }
}
