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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.tools.idea.gradle.project.build.attribution.BasicBuildAttributionInfo
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.KeepTasksAsynchronousRule
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.gradleModule
import com.google.common.truth.Expect
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.gradle.tooling.events.ProgressEvent
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull

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
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")

    simpleApplication.open { project ->
      project.registerOrReplaceServiceInstance(BuildAttributionManager::class.java, object : BuildAttributionManager {
        override fun onBuildStart(request: GradleBuildInvoker.Request) = ProgressManager.checkCanceled()

        override fun onBuildSuccess(request: GradleBuildInvoker.Request): BasicBuildAttributionInfo {
          ProgressManager.checkCanceled()
          return BasicBuildAttributionInfo(null)
        }

        override fun onBuildFailure(request: GradleBuildInvoker.Request) = ProgressManager.checkCanceled()

        override fun openResultsTab() = ProgressManager.checkCanceled()

        override fun shouldShowBuildOutputLink(): Boolean = false

        override fun statusChanged(p0: ProgressEvent?) = ProgressManager.checkCanceled()
      }, projectRule.testRootDisposable)

      val root = simpleApplication.root
      root.resolve("settings.gradle").writeText("Thread.sleep(200); println('waiting!'); Thread.sleep(1_000); println('Done!')")

      fun buildEventHandler(event: BuildEvent) {
        (event as? OutputBuildEvent)?.let { println(it.message + " : " + event.javaClass) }
        if ((event as? OutputBuildEvent)?.message?.contains("waiting!") == true) {
          val buildProgress = CoreProgressManager.getCurrentIndicators()
            .singleOrNull() { it.text.contains("Gradle Build Running") }
          assertNotNull(buildProgress)
          buildProgress.cancel()
        }
      }

      invokeAndWaitIfNeeded {

        keepTasksAsynchronous.keepTasksAsynchronous()

        project.buildAndWait(eventHandler = ::buildEventHandler) { buildInvoker ->
          buildInvoker.compileJava(arrayOf(project.gradleModule(":app")!!), TestCompileType.NONE);
        }

        keepTasksAsynchronous.runTasksSynchronously()
      }
    }
  }
}