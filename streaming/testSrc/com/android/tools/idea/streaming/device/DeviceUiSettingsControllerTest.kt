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
package com.android.tools.idea.streaming.device

import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.streaming.uisettings.binding.TwoWayProperty
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import kotlin.time.Duration.Companion.seconds

class DeviceUiSettingsControllerTest {
  @get:Rule
  val agentRule = FakeScreenSharingAgentRule()

  @get:Rule
  val edtRule = EdtRule()

  private val project
    get() = agentRule.project

  private val testRootDisposable
    get() = agentRule.disposable

  private val model: UiSettingsModel by lazy { UiSettingsModel() }
  private val device: FakeDevice by lazy { agentRule.connectDevice("Pixel 8", 34, Dimension(1080, 2280)) }
  private val agent: FakeScreenSharingAgent by lazy { device.agent }
  private val controller: DeviceUiSettingsController by lazy { createUiSettingsController() }

  @Test
  fun testReadDefaultValueWhenAttachingAfterInit() {
    controller.initAndWait()
    val state = createAndAddListener(model.inDarkMode, true)
    assertThat(model.inDarkMode.value).isFalse()
    assertThat(state.changes).isEqualTo(1)
    assertThat(state.lastValue).isFalse()
  }

  @Test
  fun testReadDefaultValueWhenAttachingBeforeInit() {
    val state = createAndAddListener(model.inDarkMode, true)
    controller.initAndWait()
    assertThat(model.inDarkMode.value).isFalse()
    assertThat(state.changes).isEqualTo(2) // After addListener and after value read
    assertThat(state.lastValue).isFalse()
  }

  @Test
  fun testReadCustomValue() {
    agent.darkMode = true
    controller.initAndWait()
    val state = createAndAddListener(model.inDarkMode, false)
    assertThat(model.inDarkMode.value).isTrue()
    assertThat(state.changes).isEqualTo(1)
    assertThat(state.lastValue).isTrue()
  }

  @Test
  fun testSetNightModeOn() {
    controller.initAndWait()
    model.inDarkMode.setFromUi(true)
    waitForCondition(10.seconds) { agent.darkMode }
  }

  @Test
  fun testSetNightOff() {
    agent.darkMode = true
    controller.initAndWait()
    model.inDarkMode.setFromUi(false)
    waitForCondition(10.seconds) { !agent.darkMode }
  }

  private fun createUiSettingsController(): DeviceUiSettingsController =
    DeviceUiSettingsController(createDeviceController(device), model)

  private fun createDeviceController(device: FakeDevice): DeviceController {
    val view = createDeviceView(device)
    view.setBounds(0, 0, 600, 800)
    waitForFrame(view)
    return view.deviceController!!
  }

  private fun createDeviceView(device: FakeDevice): DeviceView {
    val deviceClient = DeviceClient(device.serialNumber, device.configuration, device.deviceState.cpuAbi)
    Disposer.register(testRootDisposable, deviceClient)
    return DeviceView(deviceClient, deviceClient, PRIMARY_DISPLAY_ID, UNKNOWN_ORIENTATION, project)
  }

  private data class ListenerState<T>(var changes: Int, var lastValue: T)

  private fun <T> createAndAddListener(property: TwoWayProperty<T>, initialValue: T): ListenerState<T> {
    val state = ListenerState(0, initialValue)
    property.addControllerListener(testRootDisposable) { newValue ->
      state.changes++
      state.lastValue = newValue
    }
    return state
  }

  private fun DeviceUiSettingsController.initAndWait() = runBlocking {
    populateModel()
  }

  private fun waitForFrame(view: DeviceView) {
    val ui = FakeUi(view)
    waitForCondition(10.seconds) {
      ui.render()
      view.isConnected && view.frameNumber > 0u
    }
  }
}
