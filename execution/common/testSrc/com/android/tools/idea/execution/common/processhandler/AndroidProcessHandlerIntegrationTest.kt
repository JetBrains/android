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
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.fakeadbserver.services.ServiceOutput
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.whenever
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail

class AndroidProcessHandlerIntegrationTest {

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  var fakeAdbRule: FakeAdbTestRule = FakeAdbTestRule()

  private fun createDevice(): IDevice {
    val mockDevice = mock(IDevice::class.java)
    whenever(mockDevice.version).thenReturn(AndroidVersion(26))
    whenever(mockDevice.isOnline).thenReturn(true)
    return mockDevice
  }

  @Test
  fun callCustomTerminationCallback() {
    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    FakeAdbTestRule.launchAndWaitForProcess(deviceState, false)
    val callbackCalled = CountDownLatch(1)

    val handler = AndroidProcessHandler(projectRule.project, FakeAdbTestRule.CLIENT_PACKAGE_NAME, { callbackCalled.countDown() })

    handler.addTargetDevice(device)
    handler.startNotify()

    handler.destroyProcess()

    if (!callbackCalled.await(10, TimeUnit.SECONDS)) {
      fail("Termination callback is not called")
    }
  }

  @Test
  fun callForceStopIfCustomCallbackIsNotPassed() {
    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    FakeAdbTestRule.launchAndWaitForProcess(deviceState, false)
    val callbackCalled = CountDownLatch(1)

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")
      if (("force-stop " + FakeAdbTestRule.CLIENT_PACKAGE_NAME).equals(wholeCommand)) {
        callbackCalled.countDown()
      }
    }
    val handler = AndroidProcessHandler(projectRule.project, FakeAdbTestRule.CLIENT_PACKAGE_NAME)

    handler.addTargetDevice(device)
    handler.startNotify()

    handler.destroyProcess()

    if (!callbackCalled.await(10, TimeUnit.SECONDS)) {
      fail("device.forceStop is not called")
    }
  }
}