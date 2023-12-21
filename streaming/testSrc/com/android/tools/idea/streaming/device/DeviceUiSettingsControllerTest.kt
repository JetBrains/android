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
import com.android.tools.idea.streaming.emulator.CUSTOM_DENSITY
import com.android.tools.idea.streaming.emulator.CUSTOM_FONT_SIZE
import com.android.tools.idea.streaming.uisettings.testutil.UiControllerListenerValidator
import com.android.tools.idea.streaming.uisettings.ui.FontSize
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
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

  private val model: UiSettingsModel by lazy { UiSettingsModel(Dimension(1344, 2992), 480) }
  private val device: FakeDevice by lazy { agentRule.connectDevice("Pixel 8", 34, Dimension(1080, 2280)) }
  private val agent: FakeScreenSharingAgent by lazy { device.agent }
  private val controller: DeviceUiSettingsController by lazy { createUiSettingsController() }

  @Test
  fun testReadDefaultValueWhenAttachingAfterInit() {
    controller.initAndWait()
    val listeners = UiControllerListenerValidator(model, customValues = true)
    listeners.checkValues(expectedChanges = 1, expectedCustomValues = false)
  }

  @Test
  fun testReadDefaultValueWhenAttachingBeforeInit() {
    val listeners = UiControllerListenerValidator(model, customValues = true)
    controller.initAndWait()
    listeners.checkValues(expectedChanges = 2, expectedCustomValues = false)
  }

  @Test
  fun testReadCustomValue() {
    agent.darkMode = true
    agent.talkBackInstalled = true
    agent.talkBackOn = true
    agent.selectToSpeakOn = true
    agent.fontSize = CUSTOM_FONT_SIZE
    agent.screenDensity = CUSTOM_DENSITY
    controller.initAndWait()
    val listeners = UiControllerListenerValidator(model, customValues = false)
    listeners.checkValues(expectedChanges = 1, expectedCustomValues = true)
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

  @Test
  fun testSetTalkBackOn() {
    agent.talkBackInstalled = true
    controller.initAndWait()
    model.talkBackOn.setFromUi(true)
    waitForCondition(10.seconds) { agent.talkBackOn }
  }

  @Test
  fun testSetTalkBackOff() {
    agent.talkBackInstalled = true
    agent.talkBackOn = true
    controller.initAndWait()
    model.talkBackOn.setFromUi(false)
    waitForCondition(10.seconds) { !agent.talkBackOn }
  }

  @Test
  fun testSetSelectToSpeakOn() {
    agent.talkBackInstalled = true
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(true)
    waitForCondition(10.seconds) { agent.selectToSpeakOn }
  }

  @Test
  fun testSetSelectToSpeakOff() {
    agent.talkBackInstalled = true
    agent.selectToSpeakOn = true
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(false)
    waitForCondition(10.seconds) { !agent.selectToSpeakOn }
  }

  @Test
  fun testSetFontSize() {
    controller.initAndWait()
    model.fontSizeIndex.setFromUi(0)
    waitForCondition(10.seconds) { agent.fontSize == 85 }
    model.fontSizeIndex.setFromUi(FontSize.values().size - 1)
    waitForCondition(10.seconds) { agent.fontSize == FontSize.values().last().percent }
  }

  @Test
  fun testSetDensity() {
    controller.initAndWait()
    model.screenDensityIndex.setFromUi(0)
    waitForCondition(10.seconds) { agent.screenDensity == 408 }
    model.screenDensityIndex.setFromUi(model.screenDensityMaxIndex.value)
    waitForCondition(10.seconds) { agent.screenDensity == 672 }
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
