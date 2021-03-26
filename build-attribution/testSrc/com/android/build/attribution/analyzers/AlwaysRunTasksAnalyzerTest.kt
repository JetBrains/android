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
import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.PluginData
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.APP_WITH_BUILDSRC
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class AlwaysRunTasksAnalyzerTest {
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
    myProjectRule.load(SIMPLE_APPLICATION)

    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "app", SdkConstants.FN_BUILD_GRADLE), """
      class DummyTask extends DefaultTask {
          @TaskAction
          def run() {
          }
      }

      class DummyPlugin implements Plugin<Project> {
          void apply(Project project) {
               project.android.applicationVariants.all { variant ->
                  if (variant.name == "debug") {
                      DummyTask dummy = project.tasks.create("dummy", DummyTask)

                      DummyTask dummy2 = project.tasks.create("dummy2", DummyTask)
                      variant.mergeResourcesProvider.configure {
                          dependsOn(dummy2)
                      }
                      dummy2.dependsOn dummy
                      dummy2.outputs.upToDateWhen { false }
                  }
              }
          }
      }

      apply plugin: DummyPlugin
    """.trimIndent())
  }

  @Test
  fun testAlwaysRunTasksAnalyzer() {
    setUpProject()

    myProjectRule.invokeTasks("assembleDebug")

    val buildAttributionManager = myProjectRule.project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl

    val alwaysRunTasks = buildAttributionManager.analyzersProxy.getAlwaysRunTasks().sortedBy { it.taskData.taskName }

    assertThat(alwaysRunTasks).hasSize(2)

    assertThat(alwaysRunTasks[0].taskData.getTaskPath()).isEqualTo(":app:dummy")
    assertThat(alwaysRunTasks[0].taskData.taskType).isEqualTo("DummyTask")
    assertThat(alwaysRunTasks[0].taskData.originPlugin.toString()).isEqualTo("binary plugin DummyPlugin")
    assertThat(alwaysRunTasks[0].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS)

    assertThat(alwaysRunTasks[1].taskData.getTaskPath()).isEqualTo(":app:dummy2")
    assertThat(alwaysRunTasks[1].taskData.taskType).isEqualTo("DummyTask")
    assertThat(alwaysRunTasks[1].taskData.originPlugin.toString()).isEqualTo("binary plugin DummyPlugin")
    assertThat(alwaysRunTasks[1].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)
  }

  @Test
  fun testAlwaysRunTasksAnalyzerWithSuppressedWarning() {
    setUpProject()

    BuildAttributionWarningsFilter.getInstance(myProjectRule.project).suppressAlwaysRunTaskWarning("DummyTask", "DummyPlugin")

    myProjectRule.invokeTasks("assembleDebug")

    val buildAttributionManager = myProjectRule.project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl

    assertThat(buildAttributionManager.analyzersProxy.getAlwaysRunTasks()).isEmpty()
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
            dummy {
                id = 'dummyId'
                implementationClass = 'org.example.buildsrc.DummyPlugin'
            }
        }
    }
    """.trimIndent())

    FileUtil.writeToFile(
      FileUtils.join(File(myProjectRule.project.basePath!!), "buildSrc", "src", "main", "java", "org", "example", "buildsrc",
                     "DummyPlugin.java"), """
      package org.example.buildsrc;

      import org.gradle.api.Plugin;
      import org.gradle.api.Project;

      class DummyPlugin implements Plugin<Project> {
          @Override
          public void apply(Project project) {
              DummyTask task = project.getTasks().create("dummy", DummyTask.class);
              task.getOutputs().upToDateWhen((t) -> false);
          }
      }
    """.trimIndent())

    FileUtil.writeToFile(
      FileUtils.join(File(myProjectRule.project.basePath!!), "buildSrc", "src", "main", "java", "org", "example", "buildsrc",
                     "DummyTask.java"), """
      package org.example.buildsrc;

      import org.gradle.api.DefaultTask;
      import org.gradle.api.tasks.TaskAction;

      public class DummyTask extends DefaultTask {
          @TaskAction
          public void run() {
              // do nothing
          }
      }
    """.trimIndent())

    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "app", SdkConstants.FN_BUILD_GRADLE), """
      apply plugin: 'dummyId'

      afterEvaluate { project ->
          android.applicationVariants.all { variant ->
              def mergeResourcesTask = tasks.getByPath("merge${"$"}{variant.name.capitalize()}Resources")
              mergeResourcesTask.dependsOn 'dummy'
          }
      }
    """.trimIndent())

    myProjectRule.invokeTasks("assembleDebug")

    val buildAttributionManager = myProjectRule.project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl

    val alwaysRunTasks = buildAttributionManager.analyzersProxy.getAlwaysRunTasks()

    assertThat(alwaysRunTasks).hasSize(1)

    assertThat(alwaysRunTasks[0].taskData.getTaskPath()).isEqualTo(":app:dummy")
    assertThat(alwaysRunTasks[0].taskData.taskType).isEqualTo("org.example.buildsrc.DummyTask")
    assertThat(alwaysRunTasks[0].taskData.originPlugin.toString()).isEqualTo("buildSrc plugin dummyId")
    assertThat(alwaysRunTasks[0].taskData.originPlugin.pluginType).isEqualTo(PluginData.PluginType.BUILDSRC_PLUGIN)
    assertThat(alwaysRunTasks[0].rerunReason).isEqualTo(AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)
  }

  @Test
  fun testIgnoringDeleteTasks() {
    myProjectRule.load(SIMPLE_APPLICATION)

    FileUtil.appendToFile(FileUtils.join(File(myProjectRule.project.basePath!!), "app", SdkConstants.FN_BUILD_GRADLE), """
      task dummyDelete(type: Delete) { }
    """.trimIndent())

    myProjectRule.invokeTasks("dummyDelete")

    val buildAttributionManager = myProjectRule.project.getService(BuildAttributionManager::class.java) as BuildAttributionManagerImpl

    assertThat(buildAttributionManager.analyzersProxy.getAlwaysRunTasks()).isEmpty()
  }
}
