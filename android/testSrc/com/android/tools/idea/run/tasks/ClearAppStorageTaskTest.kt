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
package com.android.tools.idea.run.tasks

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.NotificationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.RuleChain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import kotlin.test.fail

/**
 * Tests for [ClearAppStorageTask]
 */
class ClearAppStorageTaskTest {
  val executor = mock<Executor>()
  val printer = mock<ConsoleView>()
  val handler = mock<ProcessHandler>()
  val indicator = mock<ProgressIndicator>()
  val env = mock<ExecutionEnvironment>()

  val projectRule = AndroidProjectRule.inMemory()
  val notificationRule = NotificationRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain(projectRule, notificationRule)

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    Mockito.`when`(env.project).thenReturn(projectRule.project)
    Mockito.`when`(env.executor).thenReturn(executor)
  }


  @Test
  fun appExists_success() {
    val device = mockDevice("com.company.application")
    val task = ClearAppStorageTask("com.company.application")

    task.run(launchContext(device))

    verify(device).executeShellCommand(eq("pm clear com.company.application"), any())
  }

  @Test
  fun appExists_failure() {
    val device = mockDevice("com.company.application", clearAppStorageSuccess = false)
    val task = ClearAppStorageTask("com.company.application")

    task.run(launchContext(device))

    verify(device).executeShellCommand(eq("pm clear com.company.application"), any())
    val notificationInfo = notificationRule.notifications.find { it.content == "Failed to clear app storage for com.company.application on device device1" }
    assertThat(notificationInfo).isNotNull()
  }

  @Test
  fun appDoesNotExists() {
    val device = mockDevice("com.company.application1")
    val task = ClearAppStorageTask("com.company.application")

    task.run(launchContext(device))
    verify(device, never()).executeShellCommand(eq("pm clear com.company.application"), any())
  }

  private fun launchContext(device: IDevice): LaunchContext =
    LaunchContext(env, device, printer, handler, indicator)
}

private fun mockDevice(packageName: String, clearAppStorageSuccess: Boolean = true): IDevice {
  val mock = mock<IDevice>()
  whenever(mock.executeShellCommand(any(), any())).thenAnswer {
    val command = it.arguments[0] as String
    val receiver = it.arguments[1] as CollectingOutputReceiver
    val result = when {
      command == "pm clear $packageName" -> if (clearAppStorageSuccess) "Success" else "Failed"
      command.startsWith("pm list packages ") -> if (command.endsWith(" $packageName")) "package:$packageName" else ""
      else -> fail("""Command "$command" not setup in mock""")
    }
    whenever(mock.toString()).thenReturn("device1")

    receiver.addOutput(result.toByteArray(), 0, result.length)
    receiver.flush()
  }
  return mock
}
