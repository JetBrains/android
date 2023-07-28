/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.adblib.ddmlibcompatibility

import com.android.adblib.AdbServerChannelProvider
import com.android.adblib.AdbSession
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.testing.FakeAdbRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assert_
import com.intellij.testFramework.TestLoggerFactory
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.RuleChain

class ShellTest {
  private val fakeAdbRule = FakeAdbRule()
  private val closeables = CloseablesRule()

  @JvmField
  @Rule
  var exceptionRule: ExpectedException = ExpectedException.none()

  @get:Rule
  val ruleChain = RuleChain.outerRule(fakeAdbRule).around(closeables)!!

  @Test
  fun executeShellCommandShouldWork() {
    // Prepare
    fakeAdbRule.attachDevice("42", "Google", "Pix3l", "versionX", "29", "arm64-v8a")
    val device: IDevice = fakeAdbRule.bridge.devices.single()
    val receiver = ListReceiver()

    // Act
    executeShellCommand(fakeAdbRule.createAdbSession(closeables), device, "getprop", receiver)

    // Assert
    val expected = """# This is some build info
# This is more build info

[ro.build.version.release]: [versionX]
[ro.build.version.sdk]: [29]
[ro.product.cpu.abi]: [arm64-v8a]
[ro.product.manufacturer]: [Google]
[ro.product.model]: [Pix3l]
[ro.serialno]: [42]
"""
    assertThat(receiver.lines.joinToString("\n")).isEqualTo(expected)
    TestLoggerFactory.dumpLogToStdout("foo")
  }

  @Test
  @Throws(Exception::class)
  fun executeShellCommandShouldThrowIfInvalidCommand() {
    // Prepare
    fakeAdbRule.attachDevice("42", "Google", "Pix3l", "versionX", "29")
    val device: IDevice = fakeAdbRule.bridge.devices.single()
    val receiver = ListReceiver()

    // Act
    exceptionRule.expect(AdbCommandRejectedException::class.java)
    executeShellCommand(fakeAdbRule.createAdbSession(closeables), device, "foobarz", receiver)

    // Assert
    assert_().fail() // should not be reached
  }

  private class ListReceiver : MultiLineReceiver() {

    val lines = mutableListOf<String>()

    override fun processNewLines(lines: Array<out String>) {
      this.lines.addAll(lines)
    }

    override fun isCancelled() = false
  }

  // TODO: Remove when this file is moved under adblib-ddmlibcompatibility/
  fun FakeAdbRule.createAdbSession(closeables: CloseablesRule): AdbSession {
    val host = TestingAdbSessionHost()
    val channelProvider =
      AdbServerChannelProvider.createOpenLocalHost(host) { this.fakeAdbServerPort }
    return closeables.register(AdbSession.create(host, channelProvider))
  }
}
