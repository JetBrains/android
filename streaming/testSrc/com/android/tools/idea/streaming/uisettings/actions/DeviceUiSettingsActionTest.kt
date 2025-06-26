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
package com.android.tools.idea.streaming.uisettings.actions

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.adblib.DevicePropertyNames
import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.executeAction
import com.android.tools.adtui.actions.updateAndGetActionPresentation
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.idea.streaming.device.DeviceClient
import com.android.tools.idea.streaming.device.DeviceDisplayPanel
import com.android.tools.idea.streaming.device.DeviceView
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.streaming.device.UNKNOWN_ORIENTATION
import com.android.tools.idea.streaming.uisettings.ui.APP_LANGUAGE_TITLE
import com.android.tools.idea.streaming.uisettings.ui.DARK_THEME_TITLE
import com.android.tools.idea.streaming.uisettings.ui.DENSITY_TITLE
import com.android.tools.idea.streaming.uisettings.ui.FONT_SCALE_TITLE
import com.android.tools.idea.streaming.uisettings.ui.GESTURE_NAVIGATION_TITLE
import com.android.tools.idea.streaming.uisettings.ui.SELECT_TO_SPEAK_TITLE
import com.android.tools.idea.streaming.uisettings.ui.TALKBACK_TITLE
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsDialog
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsPanel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.awt.Dimension
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JSlider
import kotlin.time.Duration.Companion.seconds

/** Tests for [DeviceUiSettingsAction]. */
@RunsInEdt
class DeviceUiSettingsActionTest {
  private val agentRule = FakeScreenSharingAgentRule()

  @get:Rule
  val ruleChain: RuleChain = RuleChain(agentRule, EdtRule(), HeadlessDialogRule())

  private val project
    get() = agentRule.project

  private val testRootDisposable
    get() = agentRule.disposable

  @After
  fun after() {
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }

  @Test
  fun testActionOnApi32Device() {
    val view = connectDeviceAndCreateView(32)
    val presentation = updateAndGetActionPresentation("android.streaming.ui.settings", view, project, ActionPlaces.TOOLBAR)
    assertThat(presentation.isVisible).isFalse()
  }

  @Test
  fun testActiveAction() {
    val view = connectDeviceAndCreateView()
    executeAction("android.streaming.ui.settings", view, project, ActionPlaces.TOOLBAR)
    val dialog = waitForDialog()
    assertThat(dialog.contentPanel.findDescendant<UiSettingsPanel>()).isNotNull()
  }

  @Test
  fun testWearControls() {
    val view = connectDeviceAndCreateView(isWear = true)
    executeAction("android.streaming.ui.settings", view, project, ActionPlaces.TOOLBAR)
    val dialog = waitForDialog()
    val panel = dialog.contentPanel
    assertThat(panel.findDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }).isNull()
    assertThat(panel.findDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == TALKBACK_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JSlider> { it.name == FONT_SCALE_TITLE }).isNotNull()

    assertThat(panel.findDescendant<JCheckBox> { it.name == GESTURE_NAVIGATION_TITLE }).isNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }).isNull()
    assertThat(panel.findDescendant<JSlider> { it.name == DENSITY_TITLE }).isNull()
  }

  @Test
  fun testDialogClosesWhenDialogLosesFocus() {
    val view = connectDeviceAndCreateView()
    executeAction("android.streaming.ui.settings", view, project, ActionPlaces.TOOLBAR)
    val dialog = waitForDialog()
    dialog.window.windowFocusListeners.forEach { it.windowLostFocus(mock()) }
    assertThat(dialog.isDisposed).isTrue()
  }

  @Test
  fun testDialogClosesWithParentDisposable() {
    val view = connectDeviceAndCreateView()
    executeAction("android.streaming.ui.settings", view, project, ActionPlaces.TOOLBAR)
    val dialog = waitForDialog()

    Disposer.dispose(view)
    assertThat(dialog.isDisposed).isTrue()
  }

  private fun waitForDialog(): DialogWrapper {
    waitForCondition(10.seconds) { findDialog() != null }
    return findDialog()!!
  }

  private fun findDialog() = findModelessDialog<UiSettingsDialog> { it.isShowing }

  private fun connectDeviceAndCreateView(apiLevel: Int = 33, isWear: Boolean = false): DeviceView {
    val device = agentRule.connectDevice(
      "Pixel 8",
      apiLevel,
      Dimension(1344, 2992),
      screenDensity = 480,
      additionalDeviceProperties = if (isWear) mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "watch") else emptyMap()
    )
    val view = createDeviceView(device, testRootDisposable)
    view.setBounds(0, 0, 600, 800)
    waitForConnection(view)
    return view
  }

  private fun createDeviceView(device: FakeDevice, parentDisposable: Disposable): DeviceView {
    val deviceClient = DeviceClient(device.serialNumber, device.configuration, device.deviceState.cpuAbi)
    Disposer.register(parentDisposable, deviceClient)
    val panel = DeviceDisplayPanel(parentDisposable, deviceClient, PRIMARY_DISPLAY_ID, UNKNOWN_ORIENTATION, project, false)
    return panel.displayView
  }

  private fun waitForConnection(view: DeviceView) {
    waitForCondition(2.seconds) { view.isConnected }
  }
}
