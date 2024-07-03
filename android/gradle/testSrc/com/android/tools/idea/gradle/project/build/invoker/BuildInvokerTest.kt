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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.intellij.build.BuildViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicatorProvider
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Tests for making sure that [org.gradle.tooling.BuildAction] is run when passed to [GradleBuildInvoker].
 */
class BuildInvokerTest : AndroidGradleTestCase() {

  @Throws(Exception::class)
  fun testBuildWithBuildAction() {
    var enabled = false

    val lock = ReentrantLock()
    var buildFinishedEventReceived = lock.newCondition()
    var gradleBuildInvokedExecutedPostBuildTasks = false
    var gradleBuildStateBuildStartedNotificationReceived = false
    var gradleBuildStateBuildFinishedNotificationReceived = false

    // Replace BuildViewManager service before loading a project, but leave it inactive until later moment.
    IdeComponents(project).replaceProjectService(
      BuildViewManager::class.java,
      object : BuildViewManager(project) {
        override fun onEvent(buildId: Any, event: BuildEvent) {
          if (!enabled) return // Skip events until activated.
          when (event) {
            is StartBuildEvent -> {
              val gradleProgressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator()!!
              ApplicationManager.getApplication().invokeLater {
                // Cancel the build after running for 100ms.
                Thread.sleep(100)
                gradleProgressIndicator.cancel()
              }
            }
            is FinishBuildEvent -> {
              lock.withLock {
                buildFinishedEventReceived.signal()
              }
            }
          }
        }
      })

    loadProject(SIMPLE_APPLICATION)
    enabled = true

    // Subscribe to GradleBuildState notifications.
    GradleBuildState.subscribe(project, object : GradleBuildListener {
      override fun buildStarted(context: BuildContext) {
        gradleBuildStateBuildStartedNotificationReceived = true
      }

      override fun buildFinished(status: BuildStatus, context: BuildContext) {
        gradleBuildStateBuildFinishedNotificationReceived = true
      }
    })

    val invoker = GradleBuildInvoker.getInstance(project) as GradleBuildInvokerImpl

    // Run a slow build task which will run for 30 seconds unless cancelled.
    invoker
      .executeTasks(
        GradleBuildInvoker.Request.Builder(
          project = project,
          rootProjectPath = File(project.basePath!!),
          gradleTasks = listOf("assembleDebug")
        )
          .setMode(BuildMode.ASSEMBLE)
          .build(),
        SlowTestBuildAction()
      )
      .addListener({ gradleBuildInvokedExecutedPostBuildTasks = true }, directExecutor())
    lock.withLock {
      buildFinishedEventReceived.await(1, TimeUnit.SECONDS)
    }
    assertThat(gradleBuildStateBuildStartedNotificationReceived).isTrue()
    assertThat(gradleBuildStateBuildFinishedNotificationReceived).isTrue()
    assertThat(gradleBuildInvokedExecutedPostBuildTasks).isTrue()
  }
}
