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

import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.res.AppLanguageInfo
import com.android.tools.idea.res.AppLanguageService
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.streaming.emulator.APPLICATION_ID1
import com.android.tools.idea.streaming.emulator.APPLICATION_ID2
import com.android.tools.idea.streaming.emulator.CUSTOM_DENSITY
import com.android.tools.idea.streaming.emulator.CUSTOM_FONT_SCALE
import com.android.tools.idea.streaming.uisettings.testutil.UiControllerListenerValidator
import com.android.tools.idea.streaming.uisettings.ui.FontScale
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import kotlin.time.Duration.Companion.seconds

private const val API_LEVEL = 33

class DeviceUiSettingsControllerTest {
  @get:Rule
  val agentRule = FakeScreenSharingAgentRule()

  private val project
    get() = agentRule.project

  private val testRootDisposable
    get() = agentRule.disposable

  private val model: UiSettingsModel by lazy { UiSettingsModel(Dimension(1344, 2992), 480, API_LEVEL) }
  private val device: FakeDevice by lazy { agentRule.connectDevice("Pixel 8", API_LEVEL, Dimension(1080, 2280)) }
  private val agent: FakeScreenSharingAgent by lazy { device.agent }
  private val controller: DeviceUiSettingsController by lazy { createUiSettingsController() }

  @Before
  fun before() {
    agent.foregroundProcess = APPLICATION_ID1
    val appLanguageServices = AppLanguageService { listOf(
      AppLanguageInfo(APPLICATION_ID1, setOf(LocaleQualifier("da"), LocaleQualifier("ru"))),
      AppLanguageInfo(APPLICATION_ID2, setOf(LocaleQualifier("es"))))
    }
    project.registerOrReplaceServiceInstance(AppLanguageService::class.java, appLanguageServices, testRootDisposable)
  }

  @Test
  fun testReadDefaultValueWhenAttachingAfterInit() {
    controller.initAndWait()
    val listeners = UiControllerListenerValidator(model, customValues = true, settable = false)
    listeners.checkValues(expectedChanges = 1, expectedCustomValues = false, expectedSettable = true)
  }

  @Test
  fun testReadDefaultValueWhenAttachingBeforeInit() {
    val listeners = UiControllerListenerValidator(model, customValues = true, settable = false)
    controller.initAndWait()
    listeners.checkValues(expectedChanges = 2, expectedCustomValues = false, expectedSettable = true)
  }

  @Test
  fun testReadCustomValue() {
    agent.darkMode = true
    agent.gestureNavigation = false
    agent.appLocales = "da"
    agent.talkBackInstalled = true
    agent.talkBackOn = true
    agent.selectToSpeakOn = true
    agent.fontScale = CUSTOM_FONT_SCALE
    agent.screenDensity = CUSTOM_DENSITY
    controller.initAndWait()
    val listeners = UiControllerListenerValidator(model, customValues = false, settable = false)
    listeners.checkValues(expectedChanges = 1, expectedCustomValues = true, expectedSettable = true)
  }

  @Test
  fun testReadCustomValueWithoutFontScaleAndDensity() {
    agent.darkMode = true
    agent.gestureNavigation = false
    agent.appLocales = "da"
    agent.talkBackInstalled = true
    agent.talkBackOn = true
    agent.selectToSpeakOn = true
    agent.fontScaleSettable = false
    agent.fontScale = CUSTOM_FONT_SCALE
    agent.screenDensitySettable = false
    agent.screenDensity = CUSTOM_DENSITY
    controller.initAndWait()
    val listeners = UiControllerListenerValidator(model, customValues = false, settable = true)
    listeners.checkValues(expectedChanges = 1, expectedCustomValues = true, expectedSettable = false)
  }

  @Test
  fun testGestureOverlayMissingAndTalkbackInstalled() {
    agent.gestureOverlayInstalled = false
    agent.talkBackInstalled = true
    controller.initAndWait()
    assertThat(model.gestureOverlayInstalled.value).isFalse()
    assertThat(model.talkBackInstalled.value).isTrue()
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
  fun testGestureNavigationOn() {
    controller.initAndWait()
    model.gestureNavigation.setFromUi(true)
    waitForCondition(10.seconds) { agent.gestureNavigation }
  }

  @Test
  fun testGestureNavigationOff() {
    agent.gestureNavigation = true
    controller.initAndWait()
    model.gestureNavigation.setFromUi(false)
    waitForCondition(10.seconds) { !agent.gestureNavigation }
  }

  @Test
  fun testAppSetLanguage() {
    controller.initAndWait()
    val appLanguage = model.appLanguage
    appLanguage.selection.setFromUi(appLanguage.getElementAt(1))
    waitForCondition(10.seconds) { agent.appLocales == "da"}
    appLanguage.selection.setFromUi(appLanguage.getElementAt(0))
    waitForCondition(10.seconds) { agent.appLocales == ""}
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
  fun testSetFontScale() {
    controller.initAndWait()
    model.fontScaleIndex.setFromUi(0)
    waitForCondition(10.seconds) { agent.fontScale == 85 }
    model.fontScaleIndex.setFromUi(3)
    waitForCondition(10.seconds) { agent.fontScale == FontScale.LARGE_130.percent }
  }

  @Test
  fun testSetDensity() {
    controller.initAndWait()
    model.screenDensityIndex.setFromUi(0)
    waitForCondition(10.seconds) { agent.screenDensity == 408 }
    model.screenDensityIndex.setFromUi(model.screenDensityMaxIndex.value)
    waitForCondition(10.seconds) { agent.screenDensity == 672 }
  }

  private fun createUiSettingsController(): DeviceUiSettingsController {
    val view = createDeviceView(device)
    view.setBounds(0, 0, 600, 800)
    waitForCondition(10.seconds) { view.isConnected }
    return DeviceUiSettingsController(view.deviceController!!, view.deviceClient.deviceConfig, project, model)
  }

  private fun createDeviceView(device: FakeDevice): DeviceView {
    val deviceClient = DeviceClient(device.serialNumber, device.configuration, device.deviceState.cpuAbi)
    Disposer.register(testRootDisposable, deviceClient)
    val view = DeviceView(deviceClient, deviceClient, PRIMARY_DISPLAY_ID, UNKNOWN_ORIENTATION, project)
    view.size = Dimension(600, 800)
    waitForFrame(view)
    return view
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
