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
package com.android.tools.idea.gradle.project.build.output.integration.runsGradleBuild

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.buildAndWait
import com.google.common.truth.Truth.assertThat
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * This test aims to verify Build Output processing infrastructure in case of general unknown failures in gradle build.
 * This is achieved by introducing an artificial failure to the build (e.g. a failed task) and then triggering it.
 *
 * The following scenarios are covered:
 * - single task failures
 * - multiple tasks failures
 * The following would be nice but not currently covered as requires different setup (expecting sync failures):
 * - error in buildscript
 * - error in plugin application
 * - error in DSL
 */
class GradleFailureOutputParsersIntegrationTest : BuildOutputIntegrationTestBase()  {
  @Test
  fun testSingleFailedTask() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.root.resolve("app").resolve(SdkConstants.FN_BUILD_GRADLE).appendText("""
${failedTaskRegistration("failingTask1")}
tasks.register("runFailingTasks") {
    dependsOn("failingTask1")
}
""".trimIndent())
    preparedProject.openAndBuildWithFailingTasks(withStacktrace = false) { buildEvents, outputsMap ->
      val bowStateDump = buildOutputWindowStateDump(buildEvents, outputsMap)
      assertThat(bowStateDump.keys).containsExactly(
        "root",
        "root > [Task :app:failingTask1]",
        "root > [Task :app:failingTask1] > ERROR:'java.lang.Exception: Failing failingTask1'",
        "root > 'failed'"
      )
      bowStateDump["root > [Task :app:failingTask1] > ERROR:'java.lang.Exception: Failing failingTask1'"].let { output ->
        assertThat(output).startsWith("""
          Build file '$projectRoot/app/build.gradle' line: 43
  
          Execution failed for task ':app:failingTask1'.
          > java.lang.Exception: Failing failingTask1
  
          * Try:
          > Run with --stacktrace option to get the stack trace.
          > Run with --debug option to get more log output.
          > Run with --scan to get full insights.
          > Get more help at https://help.gradle.org.
        """.trimIndent())
        assertThat(output).doesNotContain("BUILD FAILED in ")
      }
      bowStateDump["root > [Task :app:failingTask1]"].let { output ->
        assertThat(output).doesNotContain("BUILD FAILED")
      }
    }
  }

  @Test
  fun testTwoFailedTasks() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.root.resolve("app").resolve(SdkConstants.FN_BUILD_GRADLE).appendText("""
${failedTaskRegistration("failingTask1")}
${failedTaskRegistration("failingTask2")}
tasks.register("runFailingTasks") {
    dependsOn("failingTask1", "failingTask2")
}
""".trimIndent())
    preparedProject.openAndBuildWithFailingTasks(withStacktrace = false) { buildEvents, outputsMap ->
      val bowStateDump = buildOutputWindowStateDump(buildEvents, outputsMap)
      assertThat(bowStateDump.keys).containsExactly(
        "root",
        "root > [Task :app:failingTask1]",
        "root > [Task :app:failingTask1] > ERROR:'java.lang.Exception: Failing failingTask1'",
        "root > [Task :app:failingTask2]",
        "root > [Task :app:failingTask2] > ERROR:'java.lang.Exception: Failing failingTask2'",
        "root > 'failed'"
      )
      bowStateDump["root > [Task :app:failingTask1] > ERROR:'java.lang.Exception: Failing failingTask1'"].let { output ->
        assertThat(output).isEqualTo("""
          Build file '$projectRoot/app/build.gradle' line: 43
  
          Execution failed for task ':app:failingTask1'.
          > java.lang.Exception: Failing failingTask1
  
          * Try:
          > Run with --stacktrace option to get the stack trace.
          > Run with --debug option to get more log output.
          > Run with --scan to get full insights.
          > Get more help at https://help.gradle.org.
        """.trimIndent())
      }
      bowStateDump["root > [Task :app:failingTask2] > ERROR:'java.lang.Exception: Failing failingTask2'"].let { output ->
        assertThat(output).isEqualTo("""
          Build file '$projectRoot/app/build.gradle' line: 51
  
          Execution failed for task ':app:failingTask2'.
          > java.lang.Exception: Failing failingTask2
  
          * Try:
          > Run with --stacktrace option to get the stack trace.
          > Run with --debug option to get more log output.
          > Run with --scan to get full insights.
          > Get more help at https://help.gradle.org.
        """.trimIndent())
      }
      bowStateDump["root > [Task :app:failingTask1]"].let { output ->
        assertThat(output).doesNotContain("BUILD FAILED")
      }
      bowStateDump["root > [Task :app:failingTask2]"].let { output ->
        // TODO (b/414343360): broken until fix is ready in the platform's GradleOutputDispatcherFactory code
        //assertThat(output).doesNotContain("BUILD FAILED")
      }
    }
  }

  @Test
  fun testSingleFailedTaskWithStacktrace() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.root.resolve("app").resolve(SdkConstants.FN_BUILD_GRADLE).appendText("""
${failedTaskRegistration("failingTask1")}
tasks.register("runFailingTasks") {
    dependsOn("failingTask1")
}
""".trimIndent())
    preparedProject.openAndBuildWithFailingTasks(withStacktrace = true) { buildEvents, outputsMap ->
      val bowStateDump = buildOutputWindowStateDump(buildEvents, outputsMap)
      assertThat(bowStateDump.keys).containsExactly(
        "root",
        "root > [Task :app:failingTask1]",
        "root > [Task :app:failingTask1] > ERROR:'java.lang.Exception: Failing failingTask1'",
        "root > 'failed'"
      )
    }
  }

  @Test
  fun testTwoFailedTasksWithStacktrace() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.root.resolve("app").resolve(SdkConstants.FN_BUILD_GRADLE).appendText("""
${failedTaskRegistration("failingTask1")}
${failedTaskRegistration("failingTask2")}
tasks.register("runFailingTasks") {
    dependsOn("failingTask1", "failingTask2")
}
""".trimIndent())
    preparedProject.openAndBuildWithFailingTasks(withStacktrace = true) { buildEvents, outputsMap ->
      val bowStateDump = buildOutputWindowStateDump(buildEvents, outputsMap)
      assertThat(bowStateDump.keys).containsExactly(
        "root",
        "root > [Task :app:failingTask1]",
        "root > [Task :app:failingTask1] > ERROR:'java.lang.Exception: Failing failingTask1'",
        "root > [Task :app:failingTask2]",
        "root > [Task :app:failingTask2] > ERROR:'java.lang.Exception: Failing failingTask2'",
        "root > 'failed'"
      )
    }
  }

  @Test
  fun testThreeFailedTasks() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.root.resolve("app").resolve(SdkConstants.FN_BUILD_GRADLE).appendText("""
${failedTaskRegistration("failingTask1")}
${failedTaskRegistration("failingTask2")}
${failedTaskRegistration("failingTask3")}
tasks.register("runFailingTasks") {
    dependsOn("failingTask1", "failingTask2", "failingTask3")
}
""".trimIndent())
    preparedProject.openAndBuildWithFailingTasks(withStacktrace = false) { buildEvents, outputsMap ->
      val bowStateDump = buildOutputWindowStateDump(buildEvents, outputsMap)
      assertThat(bowStateDump.keys).containsExactly(
        "root",
        "root > [Task :app:failingTask1]",
        "root > [Task :app:failingTask1] > ERROR:'java.lang.Exception: Failing failingTask1'",
        "root > [Task :app:failingTask2]",
        "root > [Task :app:failingTask2] > ERROR:'java.lang.Exception: Failing failingTask2'",
        "root > [Task :app:failingTask3]",
        "root > [Task :app:failingTask3] > ERROR:'java.lang.Exception: Failing failingTask3'",
        "root > 'failed'"
      )
      bowStateDump["root > [Task :app:failingTask1] > ERROR:'java.lang.Exception: Failing failingTask1'"].let { output ->
        assertThat(output).isEqualTo("""
          Build file '$projectRoot/app/build.gradle' line: 43
  
          Execution failed for task ':app:failingTask1'.
          > java.lang.Exception: Failing failingTask1
  
          * Try:
          > Run with --stacktrace option to get the stack trace.
          > Run with --debug option to get more log output.
          > Run with --scan to get full insights.
          > Get more help at https://help.gradle.org.
        """.trimIndent())
      }
      bowStateDump["root > [Task :app:failingTask2] > ERROR:'java.lang.Exception: Failing failingTask2'"].let { output ->
        assertThat(output).isEqualTo("""
          Build file '$projectRoot/app/build.gradle' line: 51
  
          Execution failed for task ':app:failingTask2'.
          > java.lang.Exception: Failing failingTask2
  
          * Try:
          > Run with --stacktrace option to get the stack trace.
          > Run with --debug option to get more log output.
          > Run with --scan to get full insights.
          > Get more help at https://help.gradle.org.
        """.trimIndent())
      }
      bowStateDump["root > [Task :app:failingTask3] > ERROR:'java.lang.Exception: Failing failingTask3'"].let { output ->
        assertThat(output).isEqualTo("""
          Build file '$projectRoot/app/build.gradle' line: 59
  
          Execution failed for task ':app:failingTask3'.
          > java.lang.Exception: Failing failingTask3
  
          * Try:
          > Run with --stacktrace option to get the stack trace.
          > Run with --debug option to get more log output.
          > Run with --scan to get full insights.
          > Get more help at https://help.gradle.org.
        """.trimIndent())
      }
      bowStateDump["root > [Task :app:failingTask1]"].let { output ->
        assertThat(output).doesNotContain("BUILD FAILED")
      }
      bowStateDump["root > [Task :app:failingTask2]"].let { output ->
        assertThat(output).doesNotContain("BUILD FAILED")
      }
      bowStateDump["root > [Task :app:failingTask3]"].let { output ->
        // TODO (b/414343360): broken until fix is ready in the platform's GradleOutputDispatcherFactory code
        //assertThat(output).doesNotContain("BUILD FAILED")
      }
    }
  }

  private fun failedTaskRegistration(name: String) = """
tasks.register("$name") {
    doLast {
        println("Hello from $name!")
        throw new Exception("Failing $name")
    }
}
  """

  private fun PreparedTestProject.openAndBuildWithFailingTasks(
    withStacktrace: Boolean,
    verification: PreparedTestProject.Context.(List<BuildEvent>, Map<String, List<String>>) -> Unit
  ) = openAndBuildWithFailingTasks(
    tasks = listOf("runFailingTasks"),
    withStacktrace = withStacktrace,
    verification = verification
  )

  private fun  buildOutputWindowStateDump(
    buildEvents: List<BuildEvent>,
    outputsMap: Map<String, List<String>>
  ): Map<String, String?> {
    return (buildEvents.map { it.toFullPathWithMessage() to it.description }
      +
      outputsMap.map { it.key to it.value.joinToString(separator = "") })
      .toMap()
  }
}