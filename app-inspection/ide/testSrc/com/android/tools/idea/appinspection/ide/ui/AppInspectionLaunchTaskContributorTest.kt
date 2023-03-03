/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.ui

import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.DeviceImpl
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.run.AndroidLaunchTasksProvider
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import kotlin.random.Random
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test

class AppInspectionLaunchTaskContributorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testRecentProcess() {
    val project = projectRule.project
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    fun env() =
      ExecutionEnvironmentBuilder.create(
          DefaultRunExecutor.getRunExecutorInstance(),
          AndroidRunConfiguration(
            projectRule.project,
            AndroidRunConfigurationType.getInstance().factory
          )
        )
        .build()
        .apply { executionId = Random.nextLong() }

    val applicationIdProvider: ApplicationIdProvider =
      object : ApplicationIdProvider {
        override fun getPackageName(): String = "com.example.p1"
        override fun getTestPackageName(): String? = null
      }

    val launchOptions = LaunchOptions.builder().setClearLogcatBeforeStart(false).build()

    val androidFacet = AndroidFacet.getInstance(projectRule.module)!!

    val handler1 = AndroidProcessHandler(project, "com.example.p1")

    run { // Start process "p1"
      val env = env()
      val launchTaskProvider =
        AndroidLaunchTasksProvider(
          env.runProfile as AndroidRunConfigurationBase,
          env,
          androidFacet,
          applicationIdProvider,
          mock(),
          launchOptions
        )

      val task1 =
        launchTaskProvider
          .getTasks(device)
          .filterIsInstance(AppInspectionLaunchTask::class.java)
          .single()

      val launchContext1 = LaunchContext(env, device, mock(), handler1, mock())
      task1.run(launchContext1)
      handler1.startNotify()
      project.messageBus
        .syncPublisher(ExecutionManager.EXECUTION_TOPIC)
        .processStarted(DefaultRunExecutor.EXECUTOR_ID, env, handler1)

      // Make sure that the process p1 is recorded as the recent process:
      assertThat(RecentProcess.get(project)!!.device).isSameAs(device)
      assertThat(RecentProcess.get(project)!!.packageName).isEqualTo("com.example.p1")
    }

    // Start process "p2"
    run {
      val env = env()
      val applicationIdProvider: ApplicationIdProvider =
        object : ApplicationIdProvider {
          override fun getPackageName(): String = "com.example.p2"
          override fun getTestPackageName(): String? = null
        }
      val launchTaskProvider2 =
        AndroidLaunchTasksProvider(
          env.runProfile as AndroidRunConfigurationBase,
          env,
          androidFacet,
          applicationIdProvider,
          mock(),
          launchOptions
        )
      val task2 =
        launchTaskProvider2
          .getTasks(device)
          .filterIsInstance(AppInspectionLaunchTask::class.java)
          .single()

      val handler2 = AndroidProcessHandler(project, "com.example.p2")
      val launchContext2 = LaunchContext(env, device, mock(), handler2, mock())
      task2.run(launchContext2)
      handler2.startNotify()
      project.messageBus
        .syncPublisher(ExecutionManager.EXECUTION_TOPIC)
        .processStarted(DefaultRunExecutor.EXECUTOR_ID, env, handler2)

      // Make sure that the process p2 is now recorded as the recent process:
      assertThat(RecentProcess.get(project)!!.device).isSameAs(device)
      assertThat(RecentProcess.get(project)!!.packageName).isEqualTo("com.example.p2")

      // Kill process p1 and check that the recent process is still p2:
      handler1.killProcess()
      assertThat(RecentProcess.get(project)!!.device).isSameAs(device)
      assertThat(RecentProcess.get(project)!!.packageName).isEqualTo("com.example.p2")
    }
  }
}
