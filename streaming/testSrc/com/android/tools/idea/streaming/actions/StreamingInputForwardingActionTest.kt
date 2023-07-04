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
package com.android.tools.idea.streaming.actions

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.createTestEvent
import com.android.tools.idea.streaming.device.DeviceClient
import com.android.tools.idea.streaming.device.DeviceView
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule
import com.android.tools.idea.streaming.device.UNKNOWN_ORIENTATION
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.streaming.updateAndGetActionPresentation
import com.android.tools.idea.testing.flags.override
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.testFramework.RuleChain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Dimension

@RunWith(JUnit4::class)
class StreamingInputForwardingActionTest {

  val emulatorViewRule = EmulatorViewRule()
  val agentRule = FakeScreenSharingAgentRule()

  @get:Rule
  val rule = RuleChain(emulatorViewRule, agentRule)

  private val project
    get() = agentRule.project

  private val testRootDisposable
    get() = agentRule.disposable

  @Before
  fun setUp() {
    StudioFlags.STREAMING_INPUT_FORWARDING_BUTTON.override(true, testRootDisposable)
  }

  @Test
  fun testUpdatePopulatePresentation() {
    val action = StreamingInputForwardingAction()
    val view = emulatorViewRule.newEmulatorView(FakeEmulator::createPhoneAvd)

    executeStreamingAction(action, view, project)
    val presentation = updateAndGetActionPresentation(action, view, project)

    assertThat(presentation.isEnabled).isTrue()
    assertThat(presentation.isVisible).isTrue()
    assertThat(Toggleable.isSelected(presentation)).isTrue()
  }

  @Test
  fun testRememberStateEmulator() {
    val action = StreamingInputForwardingAction()
    val view = emulatorViewRule.newEmulatorView(FakeEmulator::createPhoneAvd)

    executeStreamingAction(action, view, project)

    assertThat(action.isSelected(createTestEvent(view, project))).isTrue()
  }

  @Test
  fun testRememberStateDevice() {
    val action = StreamingInputForwardingAction()
    val view = createDeviceView(agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280)))

    executeStreamingAction(action, view, project)

    assertThat(action.isSelected(createTestEvent(view, project))).isTrue()
  }

  @Test
  fun testRememberStatePerDevice() {
    val action = StreamingInputForwardingAction()
    val view1 = emulatorViewRule.newEmulatorView(FakeEmulator::createPhoneAvd)
    val view2 = emulatorViewRule.newEmulatorView(FakeEmulator::createFoldableAvd)

    executeStreamingAction(action, view1, project)

    assertThat(action.isSelected(createTestEvent(view1, project))).isTrue()
    assertThat(action.isSelected(createTestEvent(view2, project))).isFalse()
  }

  private fun createDeviceView(device: FakeScreenSharingAgentRule.FakeDevice): DeviceView {
    return DeviceView(
        testRootDisposable,
        DeviceClient(testRootDisposable, device.serialNumber, device.handle, device.configuration, device.deviceState.cpuAbi, project),
        UNKNOWN_ORIENTATION,
        project)
  }
}
