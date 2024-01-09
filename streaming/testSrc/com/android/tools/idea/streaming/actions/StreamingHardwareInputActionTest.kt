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

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findAllDescendants
import com.android.tools.adtui.swing.popup.FakeJBPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.createTestEvent
import com.android.tools.idea.streaming.device.DeviceClient
import com.android.tools.idea.streaming.device.DeviceView
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.streaming.device.UNKNOWN_ORIENTATION
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.streaming.updateAndGetActionPresentation
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.RuleChain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JPanel

@RunWith(JUnit4::class)
class StreamingHardwareInputActionTest {

  private val emulatorViewRule = EmulatorViewRule()
  private val agentRule = FakeScreenSharingAgentRule()
  private val popupRule = JBPopupRule()

  @get:Rule
  val rule = RuleChain(emulatorViewRule, agentRule, popupRule)

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

    executeStreamingAction(action, view, project)
    val presentation = updateAndGetActionPresentation(action, view, project)

    assertThat(presentation.isEnabled).isTrue()
    assertThat(presentation.isVisible).isTrue()
    assertThat(Toggleable.isSelected(presentation)).isTrue()
  }

  @Test
  fun testRememberStateEmulator() {
    val action = StreamingHardwareInputAction()
    val view = emulatorViewRule.newEmulatorView(FakeEmulator::createPhoneAvd)

    executeStreamingAction(action, view, project)

    assertThat(action.isSelected(createTestEvent(view, project))).isTrue()
  }

  @Test
  fun testRememberStateDevice() {
    val action = StreamingHardwareInputAction()
    val view = createDeviceView(agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280)))

    executeStreamingAction(action, view, project)

    assertThat(action.isSelected(createTestEvent(view, project))).isTrue()
  }

  @Test
  fun testRememberStatePerDevice() {
    val action = StreamingHardwareInputAction()
    val view1 = emulatorViewRule.newEmulatorView(FakeEmulator::createPhoneAvd)
    val view2 = emulatorViewRule.newEmulatorView(FakeEmulator::createFoldableAvd)

    executeStreamingAction(action, view1, project)

    assertThat(action.isSelected(createTestEvent(view1, project))).isTrue()
    assertThat(action.isSelected(createTestEvent(view2, project))).isFalse()
  }

  @Test
  fun testTooltipHasTitleAndDescriptionLabels() {
    val action = ActionManager.getInstance().getAction(StreamingHardwareInputAction.ACTION_ID)
    val view = emulatorViewRule.newEmulatorView(FakeEmulator::createPhoneAvd)
    val presentation = updateAndGetActionPresentation(action, view, project)
    val popup = showPopup(presentation)
    val labels = popup.content.findAllDescendants<JLabel>().toList()

    assertThat(labels).comparingElementsUsing(LabelCorrespondence()).apply {
      contains("Hardware Input")
      contains("Enable transparent forwarding of keyboard and mouse events to the connected device")
    }
  }

  @Test
  fun testTooltipWithShortcutHasShortcutLabel() {
    KeymapManager.getInstance().activeKeymap.addShortcut(
        StreamingHardwareInputAction.ACTION_ID, KeyboardShortcut.fromString("control shift J"))
    val action = ActionManager.getInstance().getAction(StreamingHardwareInputAction.ACTION_ID)
    val view = emulatorViewRule.newEmulatorView(FakeEmulator::createPhoneAvd)
    val presentation = updateAndGetActionPresentation(action, view, project)
    val popup = showPopup(presentation)
    val labels = popup.content.findAllDescendants<JLabel>().toList()

    assertThat(labels).comparingElementsUsing(LabelCorrespondence()).contains("Ctrl+Shift+J")
  }

  private fun createDeviceView(device: FakeDevice): DeviceView {
    val deviceClient = DeviceClient(device.serialNumber, device.configuration, device.deviceState.cpuAbi)
    Disposer.register(testRootDisposable, deviceClient)
    return DeviceView(testRootDisposable, deviceClient, PRIMARY_DISPLAY_ID, UNKNOWN_ORIENTATION, project)
  }

  private fun showPopup(presentation: Presentation): FakeJBPopup<Unit> {
    val tooltip = presentation.getClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP)
    assertThat(tooltip).isNotNull()

    val button = JPanel().apply { setBounds(0, 0, 100, 100) }
    tooltip?.installOn(button)

    val ui = FakeUi(button)
    ui.mouse.moveTo(0, 0)

    return popupFactory.getNextPopup(2000, TimeUnit.MILLISECONDS)
  }

  private class LabelCorrespondence : Correspondence<JLabel, String>() {
    override fun toString(): String = "has the partial label text"

    override fun compare(actual: JLabel?, expected: String?): Boolean {
      return actual?.text?.contains(expected ?: return false) ?: false
    }
  }
}
