/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.build.attribution.getAgpAttributionFileDir
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.KeepTasksAsynchronousRule
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.executeCapturingLoggedErrors
import com.android.tools.idea.testing.gradleModule
import com.android.utils.FileUtils
import com.google.common.truth.Expect
import com.google.common.truth.Truth
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

class BuildCancellationTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val keepTasksAsynchronous: KeepTasksAsynchronousRule = KeepTasksAsynchronousRule(overrideKeepTasksAsynchronous = false)

  @Test
  @RunsInEdt
  fun `build cancellation`() {
    val simpleApplication = projectRule.prepareTestProject(AndroidCoreTestProject.BUILD_ANALYZER_CHECK_ANALYZERS)

    simpleApplication.open { project ->
      val root = simpleApplication.root
      root.resolve("settings.gradle").writeText("Thread.sleep(200); println('waiting!'); Thread.sleep(1_000); println('Done!')")

      fun buildEventHandler(event: BuildEvent) {
        (event as? OutputBuildEvent)?.let { println(event::class.java.name + " : " + it.message) }
        if ((event as? OutputBuildEvent)?.message?.contains("waiting!") == true) {
          val buildProgress = CoreProgressManager.getCurrentIndicators()
            .singleOrNull() { it.text.contains("Gradle Build Running") }
          buildProgress ?: throw AssertionError("Build Progress is null")
          buildProgress.cancel()
        }
      }

      val buildRequest = GradleBuildInvoker.Request.builder(project, root, ":app:compileDebugJavaWithJavac").build()
      val errors = executeCapturingLoggedErrors {
        invokeAndWaitIfNeeded {

          keepTasksAsynchronous.keepTasksAsynchronous()

          project.buildAndWait(eventHandler = ::buildEventHandler) { buildInvoker ->
            buildInvoker.executeTasks(buildRequest)
          }

          keepTasksAsynchronous.runTasksSynchronously()
        }
      }
      // Check no error logged
      Truth.assertThat(errors).isEmpty()

      // check file deleted
      val file = FileUtils.join(getAgpAttributionFileDir(buildRequest.data), SdkConstants.FD_BUILD_ATTRIBUTION)
      Truth.assertThat(file.exists()).isFalse()
    }
  }
}