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

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.tools.adtui.actions.createTestEvent
import com.android.tools.adtui.actions.executeAction
import com.android.tools.adtui.actions.updateAndGetActionPresentation
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findAllDescendants
import com.android.tools.adtui.swing.popup.FakeJBPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.streaming.core.StreamingDeviceId
import com.android.tools.idea.streaming.core.extractText
import com.android.tools.idea.streaming.device.DeviceClient
import com.android.tools.idea.streaming.device.DeviceDisplayPanel
import com.android.tools.idea.streaming.device.DeviceView
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.streaming.device.UNKNOWN_ORIENTATION
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.testutil.newEmulatorView
import com.google.common.truth.Truth.assertThat
import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [StreamingHardwareInputAction]. */
@RunWith(JUnit4::class)
@RunsInEdt
class StreamingHardwareInputActionTest {

  private val emulatorViewRule = EmulatorViewRule()
  private val agentRule = FakeScreenSharingAgentRule()
  private val popupRule = JBPopupRule()

  @get:Rule val rule = RuleChain(emulatorViewRule, agentRule, popupRule, EdtRule())

  private val project
    get() = agentRule.project

  private val testRootDisposable
    get() = agentRule.disposable

  private val popupFactory = popupRule.fakePopupFactory

  @Before
  fun setUp() {
    Registry.get("ide.tooltip.initialReshowDelay").setValue(0, testRootDisposable)
  }

  @Test
  fun testUpdatePopulatePresentation() {
    val action = StreamingHardwareInputAction()
    val view = emulatorViewRule.newEmulatorView(FakeEmulator::createPhoneAvd)

    executeAction(action, view, project)
    val presentation = updateAndGetActionPresentation(action, view, project)

    assertThat(presentation.isEnabled).isTrue()
    assertThat(presentation.isVisible).isTrue()
    assertThat(Toggleable.isSelected(presentation)).isTrue()
  }

  @Test
  fun testRememberStateEmulator() {
    val action = StreamingHardwareInputAction()
    val view = emulatorViewRule.newEmulatorView(FakeEmulator::createPhoneAvd)

    executeAction(action, view, project)

    assertThat(action.isSelected(createTestEvent(view, project))).isTrue()
  }

  @Test
  fun testRememberStateDevice() {
    val action = StreamingHardwareInputAction()
    val view = createDeviceView(agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280)))
    assertThat(action.isSelected(createTestEvent(view, project))).isFalse()

    executeAction(action, view, project)

    assertThat(action.isSelected(createTestEvent(view, project))).isTrue()
  }

  @Test
  fun testRememberStatePerDevice() {
    val action = StreamingHardwareInputAction()
    val view1 = emulatorViewRule.newEmulatorView(FakeEmulator::createPhoneAvd)
    val view2 = emulatorViewRule.newEmulatorView(FakeEmulator::createFoldableAvd)
    assertThat(action.isSelected(createTestEvent(view1, project))).isFalse()
    assertThat(action.isSelected(createTestEvent(view2, project))).isFalse()

    executeAction(action, view1, project)

    assertThat(action.isSelected(createTestEvent(view1, project))).isTrue()
    assertThat(action.isSelected(createTestEvent(view2, project))).isFalse()
  }

  @Test
  fun testHardwareInputStateStorage() {
    val hardwareInputStateStorage = project.service<HardwareInputStateStorage>()
    val deviceId = StreamingDeviceId.ofPhysicalDevice("123456")
    assertThat(hardwareInputStateStorage.isHardwareInputEnabled(deviceId)).isFalse()
    hardwareInputStateStorage.setHardwareInputEnabled(deviceId, true)
    assertThat(hardwareInputStateStorage.isHardwareInputEnabled(deviceId)).isTrue()
    hardwareInputStateStorage.setHardwareInputEnabled(deviceId, true)
    assertThat(hardwareInputStateStorage.isHardwareInputEnabled(deviceId)).isTrue()
    hardwareInputStateStorage.setHardwareInputEnabled(deviceId, false)
    assertThat(hardwareInputStateStorage.isHardwareInputEnabled(deviceId)).isFalse()
  }

  @Test
  fun testSerialization() {
    val storage = HardwareInputStateStorage()
    val deviceId1 = StreamingDeviceId.ofPhysicalDevice("device1")
    val deviceId2 = StreamingDeviceId.ofPhysicalDevice("device2")
    storage.setHardwareInputEnabled(deviceId1, true)
    storage.setHardwareInputEnabled(deviceId2, true)

    val element = serialize(storage, createElementIfEmpty = true)!!
    val deserialized = deserialize<HardwareInputStateStorage>(element)
    assertThat(deserialized.isHardwareInputEnabled(deviceId1)).isTrue()
    assertThat(deserialized.isHardwareInputEnabled(deviceId2)).isTrue()

    storage.setHardwareInputEnabled(deviceId1, false)
    val element2 = serialize(storage, createElementIfEmpty = true)!!
    val deserialized2 = deserialize<HardwareInputStateStorage>(element2)
    assertThat(deserialized2.isHardwareInputEnabled(deviceId1)).isFalse()
    assertThat(deserialized2.isHardwareInputEnabled(deviceId2)).isTrue()
  }

  @Test
  fun testPruning() {
    val storage = HardwareInputStateStorage()
    val now = System.currentTimeMillis()

    val deviceId1 = StreamingDeviceId.ofPhysicalDevice("device1")
    val deviceId2 = StreamingDeviceId.ofPhysicalDevice("device2")
    val deviceId3 = StreamingDeviceId.ofPhysicalDevice("device3")

    storage.serializedEnabledDevices =
      mapOf(
        "PhysicalDevice::serial=device1" to now - 61.days.inWholeMilliseconds, // Should be pruned.
        "PhysicalDevice::serial=device2" to now - 10.days.inWholeMilliseconds, // Should be kept.
        "PhysicalDevice::serial=device3" to now - 70.days.inWholeMilliseconds, // Should be pruned.
      )

    storage.getState()

    assertThat(storage.isHardwareInputEnabled(deviceId1)).isFalse()
    assertThat(storage.isHardwareInputEnabled(deviceId2)).isTrue()
    assertThat(storage.isHardwareInputEnabled(deviceId3)).isFalse()
  }

  @Test
  fun testPruningMaxSize() {
    val storage = HardwareInputStateStorage()
    val now = System.currentTimeMillis()

    val devicesMap = mutableMapOf<String, Long>()
    for (i in 0 until 105) {
      devicesMap["PhysicalDevice::serial=device$i"] = now - (105 - i) * 1000
    }
    storage.serializedEnabledDevices = devicesMap

    storage.getState()

    for (i in 0 until 5) {
      val deviceId = StreamingDeviceId.ofPhysicalDevice("device$i")
      assertThat(storage.isHardwareInputEnabled(deviceId)).isFalse()
    }
    for (i in 5 until 105) {
      val deviceId = StreamingDeviceId.ofPhysicalDevice("device$i")
      assertThat(storage.isHardwareInputEnabled(deviceId)).isTrue()
    }
  }

  @Test
  fun testTooltipHasTitleAndDescriptionLabels() {
    val action = ActionManager.getInstance().getAction(StreamingHardwareInputAction.ACTION_ID)
    val view = emulatorViewRule.newEmulatorView(FakeEmulator::createPhoneAvd)
    val presentation = updateAndGetActionPresentation(action, view, project)
    val popup = showPopup(presentation)
    val labels = popup.content.findAllDescendants<JLabel>().toList()
    assertThat(labels.map { extractText(it.text) })
      .containsExactly("Hardware Input", "Enable transparent forwarding of keyboard and mouse events to the device")
  }

  private fun createDeviceView(device: FakeDevice): DeviceView {
    val deviceClient = DeviceClient(device.handle.id, device.serialNumber, device.configuration, device.deviceState.cpuAbi)
    Disposer.register(testRootDisposable, deviceClient)
    val panel = DeviceDisplayPanel(testRootDisposable, deviceClient, PRIMARY_DISPLAY_ID, UNKNOWN_ORIENTATION, project, false)
    return panel.displayView
  }

  private fun showPopup(presentation: Presentation): FakeJBPopup<Unit> {
    val tooltip = presentation.getClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP)
    assertThat(tooltip).isNotNull()

    val button = JPanel().apply { setBounds(0, 0, 100, 100) }
    tooltip?.installOn(button)

    val ui = FakeUi(button)
    ui.mouse.moveTo(0, 0)

    return popupFactory.getNextPopup(2.seconds)
  }
}
