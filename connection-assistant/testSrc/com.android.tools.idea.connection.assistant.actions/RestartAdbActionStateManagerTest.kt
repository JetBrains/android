/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.connection.assistant.actions

import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.ddmlib.AndroidDebugBridge
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.AndroidApiLevel
import com.android.tools.adblib.testutils.FakeAdbServerAdbLibRule
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.assistant.datamodel.DefaultActionState
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.mock

class RestartAdbActionStateManagerTest {
  private val projectRule = AndroidProjectRule.withSdk()
  private val fakeAdbRule = FakeAdbServerAdbLibRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(fakeAdbRule)

  private val unusedActionData: ActionData = mock()
  private val stateManager: RestartAdbActionStateManager = RestartAdbActionStateManager()

  @Test
  fun testDefaultStateBeforeInit() {
    assertEquals(DefaultActionState.IN_PROGRESS, stateManager.getState(projectRule.project, unusedActionData))
  }

  @Test
  @RunsInEdt
  fun testInitRegistersState() {
    stateManager.init(projectRule.project, unusedActionData)

    val state = stateManager.getState(projectRule.project, unusedActionData)
    assertNotNull(state)
  }

  @Test
  fun testConnectedDeviceNameAndVersionResolutionFromProperties() = runBlocking {
    val serialNumber = "device-1"
    fakeAdbRule.connectDevice(
      deviceId = serialNumber,
      manufacturer = "Google",
      deviceModel = "Pixel 8",
      release = "14",
      sdk = AndroidApiLevel(34),
      hostConnectionType = DeviceState.HostConnectionType.USB,
    )

    val bridge = AndroidDebugBridge.getBridge() ?: error("No bridge")
    yieldUntil { bridge.devices.any { it.serialNumber == serialNumber } }

    stateManager.init(projectRule.project, unusedActionData)

    // Wait until background coroutine resolves device name and version and updates state display
    yieldUntil {
      val display = stateManager.getStateDisplay(projectRule.project, unusedActionData, null)
      display?.body?.contains("google-pixel_8-device-1") == true && display.body?.contains("API 34") == true
    }

    val stateDisplay = stateManager.getStateDisplay(projectRule.project, unusedActionData, null)
    assertThat(stateDisplay).isNotNull()
    assertThat(stateDisplay!!.state).isEqualTo(CustomSuccessState)
    assertThat(stateDisplay.body).contains("google-pixel_8-device-1")
    assertThat(stateDisplay.body).contains("API 34")
  }
}
