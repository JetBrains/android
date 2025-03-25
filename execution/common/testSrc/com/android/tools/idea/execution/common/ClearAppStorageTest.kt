/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.execution.common

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.NotificationRule
import com.google.common.truth.Truth
import com.intellij.testFramework.RuleChain
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.fail

/**
 * Tests for [clearAppStorage]
 */
class ClearAppStorageTest {
  val projectRule = AndroidProjectRule.inMemory()
  val notificationRule = NotificationRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain(projectRule, notificationRule)

  @Test
  fun appExists_success() {
    val device = mockDevice("com.company.application")
    clearAppStorage(projectRule.project, device, "com.company.application", RunStats(projectRule.project))

    verify(device).executeShellCommand(eq("pm clear com.company.application"), any())
  }

  @Test
  fun appExists_failure() {
    val device = mockDevice("com.company.application", clearAppStorageSuccess = false)
    clearAppStorage(projectRule.project, device, "com.company.application", RunStats(projectRule.project))

    verify(device).executeShellCommand(eq("pm clear com.company.application"), any())
    val notificationInfo = notificationRule.notifications.find { it.content == "Failed to clear app storage for com.company.application on device device1" }
    Truth.assertThat(notificationInfo).isNotNull()
  }

  @Test
  fun appDoesNotExists() {
    val device = mockDevice("com.company.application1")
    clearAppStorage(projectRule.project, device, "com.company.application", RunStats(projectRule.project))

    verify(device, never()).executeShellCommand(eq("pm clear com.company.application"), any())
  }
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
    whenever(mock.name).thenReturn("device1")

    receiver.addOutput(result.toByteArray(), 0, result.length)
    receiver.flush()
  }
  return mock
}
