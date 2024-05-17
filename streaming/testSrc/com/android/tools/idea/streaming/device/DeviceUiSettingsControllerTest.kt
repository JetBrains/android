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
    val appLanguageServices = AppLanguageService { _, applicationId ->
      when (applicationId) {
        APPLICATION_ID1 -> AppLanguageInfo(APPLICATION_ID1, setOf(LocaleQualifier("da"), LocaleQualifier("ru")))
        APPLICATION_ID2 -> AppLanguageInfo(APPLICATION_ID2, setOf(LocaleQualifier("es")))
        else -> null
      }
    }
    project.registerOrReplaceServiceInstance(AppLanguageService::class.java, appLanguageServices, testRootDisposable)
  }

  @Test
  fun testReadDefaultValueWhenAttachingAfterInit() {
    controller.initAndWait()
    agent.setOriginalValues()
    val listeners = UiControllerListenerValidator(model, customValues = true, settable = false)
    listeners.checkValues(expectedChanges = 1, expectedCustomValues = false, expectedSettable = true)
    assertThat(model.differentFromDefault.value).isFalse()
  }

  @Test
  fun testReadDefaultValueWhenAttachingBeforeInit() {
    val listeners = UiControllerListenerValidator(model, customValues = true, settable = false)
    controller.initAndWait()
    agent.setOriginalValues()
    listeners.checkValues(expectedChanges = 2, expectedCustomValues = false, expectedSettable = true)
    assertThat(model.differentFromDefault.value).isFalse()
  }

  @Test
  fun testReadCustomValue() {
    agent.setOriginalValues()
    agent.currentUiSettings.darkMode = true
    agent.currentUiSettings.gestureNavigation = false
    agent.currentUiSettings.appLocales = "da"
    agent.talkBackInstalled = true
    agent.currentUiSettings.talkBackOn = true
    agent.currentUiSettings.selectToSpeakOn = true
    agent.currentUiSettings.fontScale = CUSTOM_FONT_SCALE
    agent.currentUiSettings.screenDensity = CUSTOM_DENSITY
    agent.currentUiSettings.debugLayout = true
    controller.initAndWait()
    val listeners = UiControllerListenerValidator(model, customValues = false, settable = false)
    listeners.checkValues(expectedChanges = 1, expectedCustomValues = true, expectedSettable = true)
    assertThat(model.differentFromDefault.value).isTrue()
  }

  @Test
  fun testReadCustomValueWithoutFontScaleAndDensity() {
    agent.currentUiSettings.darkMode = true
    agent.currentUiSettings.gestureNavigation = false
    agent.currentUiSettings.appLocales = "da"
    agent.talkBackInstalled = true
    agent.currentUiSettings.talkBackOn = true
    agent.currentUiSettings.selectToSpeakOn = true
    agent.fontScaleSettable = false
    agent.currentUiSettings.fontScale = CUSTOM_FONT_SCALE
    agent.screenDensitySettable = false
    agent.currentUiSettings.screenDensity = CUSTOM_DENSITY
    agent.currentUiSettings.debugLayout = true
    agent.setOriginalValues()
    controller.initAndWait()
    val listeners = UiControllerListenerValidator(model, customValues = false, settable = true)
    listeners.checkValues(expectedChanges = 1, expectedCustomValues = true, expectedSettable = false)
    assertThat(model.differentFromDefault.value).isFalse()
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
    agent.setOriginalValues()
    controller.initAndWait()
    model.inDarkMode.setFromUi(true)
    waitForCondition(10.seconds) { agent.currentUiSettings.darkMode }
    waitForCondition(10.seconds) { model.differentFromDefault.value }
  }

  @Test
  fun testSetNightOff() {
    agent.setOriginalValues()
    agent.currentUiSettings.darkMode = true
    controller.initAndWait()
    model.inDarkMode.setFromUi(false)
    waitForCondition(10.seconds) { !agent.currentUiSettings.darkMode }
    waitForCondition(10.seconds) { !model.differentFromDefault.value }
  }

  @Test
  fun testGestureNavigationOn() {
    agent.currentUiSettings.gestureNavigation = false
    agent.setOriginalValues()
    controller.initAndWait()
    model.gestureNavigation.setFromUi(true)
    waitForCondition(10.seconds) { agent.currentUiSettings.gestureNavigation }
    waitForCondition(10.seconds) { model.differentFromDefault.value }
  }

  @Test
  fun testGestureNavigationOff() {
    agent.currentUiSettings.gestureNavigation = true
    agent.setOriginalValues()
    controller.initAndWait()
    model.gestureNavigation.setFromUi(false)
    waitForCondition(10.seconds) { !agent.currentUiSettings.gestureNavigation }
    waitForCondition(10.seconds) { model.differentFromDefault.value }
  }

  @Test
  fun testAppSetLanguage() {
    agent.setOriginalValues()
    controller.initAndWait()
    val appLanguage = model.appLanguage
    appLanguage.selection.setFromUi(appLanguage.getElementAt(1))
    waitForCondition(10.seconds) { agent.currentUiSettings.appLocales == "da"}
    waitForCondition(10.seconds) { model.differentFromDefault.value }
    appLanguage.selection.setFromUi(appLanguage.getElementAt(0))
    waitForCondition(10.seconds) { agent.currentUiSettings.appLocales == ""}
    waitForCondition(10.seconds) { !model.differentFromDefault.value }
  }

  @Test
  fun testSetTalkBackOn() {
    agent.talkBackInstalled = true
    agent.setOriginalValues()
    controller.initAndWait()
    model.talkBackOn.setFromUi(true)
    waitForCondition(10.seconds) { agent.currentUiSettings.talkBackOn }
    waitForCondition(10.seconds) { model.differentFromDefault.value }
  }

  @Test
  fun testSetTalkBackOff() {
    agent.setOriginalValues()
    agent.talkBackInstalled = true
    agent.currentUiSettings.talkBackOn = true
    controller.initAndWait()
    model.talkBackOn.setFromUi(false)
    waitForCondition(10.seconds) { !agent.currentUiSettings.talkBackOn }
    waitForCondition(10.seconds) { !model.differentFromDefault.value }
  }

  @Test
  fun testSetSelectToSpeakOn() {
    agent.setOriginalValues()
    agent.talkBackInstalled = true
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(true)
    waitForCondition(10.seconds) { agent.currentUiSettings.selectToSpeakOn }
    waitForCondition(10.seconds) { model.differentFromDefault.value }
  }

  @Test
  fun testSetSelectToSpeakOff() {
    agent.setOriginalValues()
    agent.talkBackInstalled = true
    agent.currentUiSettings.selectToSpeakOn = true
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(false)
    waitForCondition(10.seconds) { !agent.currentUiSettings.selectToSpeakOn }
    waitForCondition(10.seconds) { !model.differentFromDefault.value }
  }

  @Test
    fun testSetFontScale() {
    agent.setOriginalValues()
    controller.initAndWait()
    model.fontScaleIndex.setFromUi(0)
    waitForCondition(10.seconds) { agent.currentUiSettings.fontScale == 85 }
    waitForCondition(10.seconds) { model.differentFromDefault.value }
    model.fontScaleIndex.setFromUi(1)
    waitForCondition(10.seconds) { agent.currentUiSettings.fontScale == 100 }
    waitForCondition(10.seconds) { !model.differentFromDefault.value }
    model.fontScaleIndex.setFromUi(3)
    waitForCondition(10.seconds) { agent.currentUiSettings.fontScale == FontScale.LARGE_130.percent }
    waitForCondition(10.seconds) { model.differentFromDefault.value }
  }

  @Test
  fun testSetDensity() {
    agent.setOriginalValues()
    controller.initAndWait()
    model.screenDensityIndex.setFromUi(0)
    waitForCondition(10.seconds) { agent.currentUiSettings.screenDensity == 408 }
    waitForCondition(10.seconds) { model.differentFromDefault.value }
    model.screenDensityIndex.setFromUi(1)
    waitForCondition(10.seconds) { agent.currentUiSettings.screenDensity == 480 }
    waitForCondition(10.seconds) { !model.differentFromDefault.value }
    model.screenDensityIndex.setFromUi(model.screenDensityMaxIndex.value)
    waitForCondition(10.seconds) { agent.currentUiSettings.screenDensity == 672 }
    waitForCondition(10.seconds) { model.differentFromDefault.value }
  }

  @Test
  fun testSetDebugLayout() {
    agent.setOriginalValues()
    controller.initAndWait()
    model.debugLayout.setFromUi(true)
    waitForCondition(10.seconds) { agent.currentUiSettings.debugLayout }
    waitForCondition(10.seconds) { model.differentFromDefault.value }
    model.debugLayout.setFromUi(false)
    waitForCondition(10.seconds) { !agent.currentUiSettings.debugLayout }
    waitForCondition(10.seconds) { !model.differentFromDefault.value }
  }

  private fun createUiSettingsController(): DeviceUiSettingsController {
    val view = createDeviceView(device)
    view.setBounds(0, 0, 600, 800)
    waitForCondition(10.seconds) { view.isConnected }
    return DeviceUiSettingsController(view.deviceController!!, device.serialNumber, view.deviceClient.deviceConfig, project, model, view)
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
