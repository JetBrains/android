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
package com.android.tools.idea.execution.common.processhandler

import com.android.ddmlib.AndroidDebugBridge
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.sdklib.AndroidApiLevel
import com.android.tools.adblib.testutils.FakeAdbServerAdbLibRule
import com.android.tools.idea.execution.common.launchAndWaitForProcess
import com.intellij.testFramework.ProjectRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail
import org.junit.rules.RuleChain

@Ignore("FakeAdbTestRule hangs")
class AndroidProcessHandlerIntegrationTest {

  private val projectRule = ProjectRule()

  private var fakeAdbRule = FakeAdbServerAdbLibRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(fakeAdbRule)

  private val appId = "com.test.app"

  @Test
  fun callCustomTerminationCallback() {
    val deviceState = fakeAdbRule.connectDevice(
      "test_device_001",
      "test1",
      "test2",
      "model",
      AndroidApiLevel(26),
      DeviceState.HostConnectionType.USB)
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    deviceState.launchAndWaitForProcess(1234, 4321, appId, true)
    val callbackCalled = CountDownLatch(1)

    val handler = AndroidProcessHandler(appId, { callbackCalled.countDown() })

    handler.addTargetDevice(device)
    handler.startNotify()

    handler.destroyProcess()

    if (!callbackCalled.await(10, TimeUnit.SECONDS)) {
      fail("Termination callback is not called")
    }
  }

  @Test
  fun callForceStopIfCustomCallbackIsNotPassed() {
    val deviceState = fakeAdbRule.connectDevice(
      "test_device_001",
      "test1",
      "test2",
      "model",
      AndroidApiLevel(26),
      DeviceState.HostConnectionType.USB)
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    deviceState.launchAndWaitForProcess(1234, 4321, appId, true)
    val callbackCalled = CountDownLatch(1)

    deviceState.setActivityManager { args: List<String>, shellCommandOutput: ShellCommandOutput ->
      val wholeCommand = args.joinToString(" ")
      if (wholeCommand == "force-stop $appId") {
        callbackCalled.countDown()
      }
    }
    val handler = AndroidProcessHandler(appId)

    handler.addTargetDevice(device)
    handler.startNotify()

    handler.destroyProcess()

    if (!callbackCalled.await(10, TimeUnit.SECONDS)) {
      fail("device.forceStop is not called")
    }
  }
}