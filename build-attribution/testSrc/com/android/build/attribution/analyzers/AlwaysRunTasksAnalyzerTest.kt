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
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.BuildAttributionManagerImpl
import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.PluginData
import com.android.tools.idea.gradle.dsl.utils.FN_GRADLE_PROPERTIES
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.APP_WITH_BUILDSRC
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

class AlwaysRunTasksAnalyzerTest {
  @get:Rule
  val myProjectRule = AndroidGradleProjectRule()

  private fun setUpProject() {
    myProjectRule.load(SIMPLE_APPLICATION)

    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "app", SdkConstants.FN_BUILD_GRADLE), """
      class SampleTask extends DefaultTask {
          @TaskAction
          def run() {
          }
      }

      class SamplePlugin implements Plugin<Project> {
          void apply(Project project) {
               project.android.applicationVariants.all { variant ->
                  if (variant.name == "debug") {
                      SampleTask sample = project.tasks.create("sample", SampleTask)

                      SampleTask sample2 = project.tasks.create("sample2", SampleTask)
                      variant.mergeResourcesProvider.configure {
                          dependsOn(sample2)
                      }
                      sample2.dependsOn sample
                      sample2.outputs.upToDateWhen { false }
                  }
              }
          }
      }

      apply plugin: SamplePlugin
    """.trimIndent())
  }

  @Test
  fun testAlwaysRunTasksAnalyzer() {
    setUpProject()

    myProjectRule.invokeTasksRethrowingErrors("assembleDebug")

    val buildAnalyzerStorageManager = myProjectRule.project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()
    val alwaysRunTasks = results.getAlwaysRunTasks().sortedBy { it.taskData.taskName }

    assertThat(alwaysRunTasks).hasSize(2)

    assertThat(alwaysRunTasks[0].taskData.getTaskPath()).isEqualTo(":app:sample")
    assertThat(alwaysRunTasks[0].taskData.taskType).isEqualTo("SampleTask")
    assertThat(alwaysRunTasks[0].taskData.originPlugin.toString()).isEqualTo("binary plugin SamplePlugin")
    assertThat(alwaysRunTasks[0].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS)

    assertThat(alwaysRunTasks[1].taskData.getTaskPath()).isEqualTo(":app:sample2")
    assertThat(alwaysRunTasks[1].taskData.taskType).isEqualTo("SampleTask")
    assertThat(alwaysRunTasks[1].taskData.originPlugin.toString()).isEqualTo("binary plugin SamplePlugin")
    assertThat(alwaysRunTasks[1].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)
  }

  @Test
  fun testAlwaysRunTasksAnalyzerWithConfigurationCachedRun() {
    setUpProject()

    FileUtil.appendToFile(
      File(myProjectRule.project.basePath!!, FN_GRADLE_PROPERTIES),
      "org.gradle.unsafe.configuration-cache=true"
    )

    myProjectRule.invokeTasksRethrowingErrors("clean", "lintDebug")

    // configuration cached run

    myProjectRule.invokeTasksRethrowingErrors("clean", "lintDebug")

    val buildAnalyzerStorageManager = myProjectRule.project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()
    val alwaysRunTasks = results.getAlwaysRunTasks().sortedBy { it.taskData.taskName }

    // lint analysis runs on every task intentionally, it should be filtered out at this point even in a config-cached run
    assertThat(alwaysRunTasks).hasSize(2)

    assertThat(alwaysRunTasks[0].taskData.getTaskPath()).isEqualTo(":app:sample")
    assertThat(alwaysRunTasks[0].taskData.taskType).isEqualTo("SampleTask")
    assertThat(alwaysRunTasks[0].taskData.originPlugin.toString()).isEqualTo("unknown plugin")
    assertThat(alwaysRunTasks[0].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS)

    assertThat(alwaysRunTasks[1].taskData.getTaskPath()).isEqualTo(":app:sample2")
    assertThat(alwaysRunTasks[1].taskData.taskType).isEqualTo("SampleTask")
    assertThat(alwaysRunTasks[1].taskData.originPlugin.toString()).isEqualTo("unknown plugin")
    assertThat(alwaysRunTasks[1].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)
  }

  @Test
  @Ignore("b/179137380")
  fun testAlwaysRunTasksAnalyzerWithSuppressedWarning() {
    setUpProject()

    BuildAttributionWarningsFilter.getInstance(myProjectRule.project).suppressAlwaysRunTaskWarning("SampleTask", "SamplePlugin")

    myProjectRule.invokeTasksRethrowingErrors("assembleDebug")

    val buildAttributionManager = myProjectRule.project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl

    assertThat(buildAttributionManager.analyzersProxy.alwaysRunTasksAnalyzer.result.alwaysRunTasks).isEmpty()
  }

  @Test
  fun testAlwaysRunTasksForBuildSrcPlugin() {
    myProjectRule.load(APP_WITH_BUILDSRC)

    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "buildSrc", SdkConstants.FN_BUILD_GRADLE), """

    dependencies {
        implementation gradleApi()
    }

    apply plugin: "java-gradle-plugin"

    gradlePlugin {
        plugins {
            sample {
                id = 'sampleId'
                implementationClass = 'org.example.buildsrc.SamplePlugin'
            }
        }
    }
    """.trimIndent())

    FileUtil.writeToFile(
      FileUtils.join(File(myProjectRule.project.basePath!!), "buildSrc", "src", "main", "java", "org", "example", "buildsrc",
                     "SamplePlugin.java"), """
      package org.example.buildsrc;

      import org.gradle.api.Plugin;
      import org.gradle.api.Project;

      class SamplePlugin implements Plugin<Project> {
          @Override
          public void apply(Project project) {
              SampleTask task = project.getTasks().create("sample", SampleTask.class);
              task.getOutputs().upToDateWhen((t) -> false);
          }
      }
    """.trimIndent())

    FileUtil.writeToFile(
      FileUtils.join(File(myProjectRule.project.basePath!!), "buildSrc", "src", "main", "java", "org", "example", "buildsrc",
                     "SampleTask.java"), """
      package org.example.buildsrc;

      import org.gradle.api.DefaultTask;
      import org.gradle.api.tasks.TaskAction;

      public class SampleTask extends DefaultTask {
          @TaskAction
          public void run() {
              // do nothing
          }
      }
    """.trimIndent())

    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "app", SdkConstants.FN_BUILD_GRADLE), """
      apply plugin: 'sampleId'

      afterEvaluate { project ->
          android.applicationVariants.all { variant ->
              def mergeResourcesTask = tasks.getByPath("merge${"$"}{variant.name.capitalize()}Resources")
              mergeResourcesTask.dependsOn 'sample'
          }
      }
    """.trimIndent())

    myProjectRule.invokeTasksRethrowingErrors("assembleDebug")

    val buildAnalyzerStorageManager = myProjectRule.project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()
    val alwaysRunTasks = results.getAlwaysRunTasks()

    assertThat(alwaysRunTasks).hasSize(1)

    assertThat(alwaysRunTasks[0].taskData.getTaskPath()).isEqualTo(":app:sample")
    assertThat(alwaysRunTasks[0].taskData.taskType).isEqualTo("org.example.buildsrc.SampleTask")
    assertThat(alwaysRunTasks[0].taskData.originPlugin.toString()).isEqualTo("buildSrc plugin org.example.buildsrc.SamplePlugin")
    assertThat(alwaysRunTasks[0].taskData.originPlugin.pluginType).isEqualTo(PluginData.PluginType.BUILDSRC_PLUGIN)
    assertThat(alwaysRunTasks[0].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)
  }

  @Test
  fun testIgnoringDeleteTasks() {
    myProjectRule.load(SIMPLE_APPLICATION)

    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "app", SdkConstants.FN_BUILD_GRADLE), """
      task sampleDelete(type: Delete) { }
    """.trimIndent())

    myProjectRule.invokeTasksRethrowingErrors("assembleDebug")

    val buildAnalyzerStorageManager = myProjectRule.project.getService(BuildAnalyzerStorageManager::class.java)
    val results = buildAnalyzerStorageManager.getLatestBuildAnalysisResults()
    val alwaysRunTasks = results.getAlwaysRunTasks()
    assertThat(alwaysRunTasks.isEmpty())
  }
}
