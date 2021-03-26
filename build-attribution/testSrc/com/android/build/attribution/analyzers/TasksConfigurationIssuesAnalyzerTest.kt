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

import com.android.SdkConstants
import com.android.build.attribution.BuildAttributionManagerImpl
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class TasksConfigurationIssuesAnalyzerTest {
  @get:Rule
  val myProjectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.clearOverride()
  }

  private fun setUpProject() {
    myProjectRule.load(TestProjectPaths.SIMPLE_APPLICATION)

    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "app", SdkConstants.FN_BUILD_GRADLE), """
      abstract class DummyTask extends DefaultTask {
          @OutputDirectory
          abstract DirectoryProperty getOutputDir()

          @TaskAction
          def run() {
              // do nothing
          }
      }

      task dummy1(type: DummyTask) {
          outputDir = file("${"$"}buildDir/outputs/shared_output")
      }

      task dummy2(type: DummyTask) {
          outputDir = file("${"$"}buildDir/outputs/shared_output")
      }

      afterEvaluate { project ->
          android.applicationVariants.all { variant ->
              def mergeResourcesTask = tasks.getByPath("merge${"$"}{variant.name.capitalize()}Resources")
              mergeResourcesTask.dependsOn dummy1
              mergeResourcesTask.dependsOn dummy2
          }
          dummy2.dependsOn dummy1
      }
    """.trimIndent())
  }

  @Test
  fun testTasksConfigurationIssuesAnalyzer() {
    setUpProject()

    myProjectRule.invokeTasks("assembleDebug")

    val buildAttributionManager = myProjectRule.project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl

    assertThat(buildAttributionManager.analyzersProxy.getTasksSharingOutput()).hasSize(1)
    val tasksSharingOutput = buildAttributionManager.analyzersProxy.getTasksSharingOutput()[0]


    assertThat(tasksSharingOutput.outputFilePath).endsWith("app/build/outputs/shared_output")
    assertThat(tasksSharingOutput.taskList).hasSize(2)

    assertThat(tasksSharingOutput.taskList[0].getTaskPath()).isEqualTo(":app:dummy1")
    assertThat(tasksSharingOutput.taskList[0].taskType).isEqualTo("DummyTask")
    assertThat(tasksSharingOutput.taskList[0].originPlugin.toString()).isEqualTo("script :app:build.gradle")

    assertThat(tasksSharingOutput.taskList[1].getTaskPath()).isEqualTo(":app:dummy2")
    assertThat(tasksSharingOutput.taskList[1].taskType).isEqualTo("DummyTask")
    assertThat(tasksSharingOutput.taskList[1].originPlugin.toString()).isEqualTo("script :app:build.gradle")
  }
}