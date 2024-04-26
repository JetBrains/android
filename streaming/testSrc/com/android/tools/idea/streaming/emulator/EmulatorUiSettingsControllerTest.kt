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
package com.android.tools.idea.streaming.emulator

import com.android.adblib.testing.FakeAdbDeviceServices
import com.android.testutils.waitForCondition
import com.android.tools.analytics.LoggedUsage
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.streaming.uisettings.testutil.UiControllerListenerValidator
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind
import com.google.wireless.android.sdk.stats.DeviceInfo.DeviceType
import com.google.wireless.android.sdk.stats.UiDeviceSettingsEvent.OperationKind
import com.intellij.openapi.Disposable
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import kotlin.time.Duration.Companion.seconds

class EmulatorUiSettingsControllerTest {

  private val uiRule = UiSettingsRule()
  private val usageRule = UsageTrackerRule()

  @get:Rule
  val chain = RuleChain(uiRule, usageRule)

  private val testRootDisposable: Disposable
    get() = uiRule.testRootDisposable

  private val adb: FakeAdbDeviceServices
    get() = uiRule.adb

  private val lastIssuedChangeCommand: String?
    get() = uiRule.issuedChangeCommands.lastOrNull()

  private val antepenultimateChangeCommand: String?
    get() = uiRule.issuedChangeCommands.let { if (it.size > 2) it[it.size - 3] else null }

  private val usages: List<LoggedUsage>
    get() = usageRule.usages

  private val model: UiSettingsModel by lazy { UiSettingsModel(Dimension(1344, 2992), DEFAULT_DENSITY, 33) } // Pixel 8 Pro
  private val controller: EmulatorUiSettingsController by lazy { createController() }

  @Before
  fun before() {
    val deviceSelector = uiRule.emulatorDeviceSelector
    adb.configureShellCommand(deviceSelector, "cmd uimode night yes", "Night mode: yes")
    adb.configureShellCommand(deviceSelector, "cmd uimode night no", "Night mode: no")
    adb.configureShellCommand(deviceSelector, "cmd locale set-app-locales com.example.test.process --locales da", "")
    adb.configureShellCommand(deviceSelector, "cmd locale set-app-locales com.example.test.process --locales ", "")
    adb.configureShellCommand(deviceSelector, "settings put system font_scale 2", "")
    adb.configureShellCommand(deviceSelector, "settings put system font_scale 0.75", "")
    adb.configureShellCommand(deviceSelector, "wm density 408", "Physical density: 480\nOverride density: 408")
    adb.configureShellCommand(deviceSelector, "wm density 480", "Physical density: 480")
    adb.configureShellCommand(deviceSelector, "wm density 544", "Physical density: 480\nOverride density: 544")
    adb.configureShellCommand(deviceSelector, "settings put secure enabled_accessibility_services $TALK_BACK_SERVICE_NAME", "")
    adb.configureShellCommand(deviceSelector, "settings delete secure enabled_accessibility_services", "")
    adb.configureShellCommand(deviceSelector, "settings put secure enabled_accessibility_services $SELECT_TO_SPEAK_SERVICE_NAME", "")
    adb.configureShellCommand(deviceSelector, "settings put secure accessibility_button_targets ${SELECT_TO_SPEAK_SERVICE_NAME}", "")
    adb.configureShellCommand(deviceSelector, "settings delete secure accessibility_button_targets", "")
    adb.configureShellCommand(deviceSelector, "settings put secure enabled_accessibility_services " +
                                                     "$TALK_BACK_SERVICE_NAME:$SELECT_TO_SPEAK_SERVICE_NAME", "")
    adb.configureShellCommand(deviceSelector, "settings put secure enabled_accessibility_services " +
                                                     "$SELECT_TO_SPEAK_SERVICE_NAME:$TALK_BACK_SERVICE_NAME", "")
    adb.configureShellCommand(deviceSelector, FACTORY_RESET_COMMAND.format(APPLICATION_ID1, DEFAULT_DENSITY), "")
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
    uiRule.configureUiSettings(
      darkMode = true,
      gestureNavigation = true,
      applicationId = APPLICATION_ID1,
      appLocales = "da",
      talkBackInstalled = true,
      talkBackOn = true,
      selectToSpeakOn = true,
      fontSize = CUSTOM_FONT_SIZE,
      physicalDensity = DEFAULT_DENSITY,
      overrideDensity = CUSTOM_DENSITY
    )
    controller.initAndWait()
    val listeners = UiControllerListenerValidator(model, customValues = false, settable = false)
    listeners.checkValues(expectedChanges = 1, expectedCustomValues = true, expectedSettable = true)
  }

  @Test
  fun testGestureOverlayMissingAndTalkbackInstalled() {
    uiRule.configureUiSettings(gestureOverlayInstalled = false, talkBackInstalled = true)
    controller.initAndWait()
    assertThat(model.gestureOverlayInstalled.value).isFalse()
    assertThat(model.talkBackInstalled.value).isTrue()
  }

  @Test
  fun testSetNightModeOn() {
    controller.initAndWait()
    model.inDarkMode.setFromUi(true)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "cmd uimode night yes" }
    assertUsageEvent(OperationKind.DARK_THEME)
  }

  @Test
  fun testSetNightOff() {
    uiRule.configureUiSettings(darkMode = true)
    controller.initAndWait()
    model.inDarkMode.setFromUi(false)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "cmd uimode night no" }
    assertUsageEvent(OperationKind.DARK_THEME)
  }

  @Test
  fun testGestureNavigationOn() {
    controller.initAndWait()
    model.gestureNavigation.setFromUi(true)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "cmd overlay enable $GESTURES_OVERLAY" }
    assertUsageEvent(OperationKind.GESTURE_NAVIGATION)
  }

  @Test
  fun testGestureNavigationOff() {
    uiRule.configureUiSettings(gestureNavigation = true)
    controller.initAndWait()
    model.gestureNavigation.setFromUi(false)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "cmd overlay disable $GESTURES_OVERLAY" }
    assertUsageEvent(OperationKind.GESTURE_NAVIGATION)
  }

  @Test
  fun testSetAppLanguage() {
    controller.initAndWait()
    val appLanguage = model.appLanguage
    appLanguage.selection.setFromUi(appLanguage.getElementAt(1))
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "cmd locale set-app-locales $APPLICATION_ID1 --locales da" }
    appLanguage.selection.setFromUi(appLanguage.getElementAt(0))
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "cmd locale set-app-locales $APPLICATION_ID1 --locales " }
    assertUsageEvent(OperationKind.APP_LANGUAGE, OperationKind.APP_LANGUAGE)
  }

  @Test
  fun testSetTalkBackOn() {
    uiRule.configureUiSettings(talkBackInstalled = true)
    controller.initAndWait()
    model.talkBackOn.setFromUi(true)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "settings put secure enabled_accessibility_services $TALK_BACK_SERVICE_NAME" }
    assertUsageEvent(OperationKind.TALKBACK)
  }

  @Test
  fun testSetTalkBackOff() {
    uiRule.configureUiSettings(talkBackInstalled = true, talkBackOn = true)
    controller.initAndWait()
    model.talkBackOn.setFromUi(false)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "settings delete secure enabled_accessibility_services" }
    assertUsageEvent(OperationKind.TALKBACK)
  }

  @Test
  fun testSetTalkBackOnWithSelectToSpeakOn() {
    uiRule.configureUiSettings(talkBackInstalled = true, selectToSpeakOn = true)
    controller.initAndWait()
    model.talkBackOn.setFromUi(true)
    waitForCondition(10.seconds) {
      lastIssuedChangeCommand == "settings put secure enabled_accessibility_services $SELECT_TO_SPEAK_SERVICE_NAME:$TALK_BACK_SERVICE_NAME"
    }
    assertUsageEvent(OperationKind.TALKBACK)
  }

  @Test
  fun testSetTalkBackOffWithSelectToSpeakOn() {
    uiRule.configureUiSettings(talkBackInstalled = true, talkBackOn = true, selectToSpeakOn = true)
    controller.initAndWait()
    model.talkBackOn.setFromUi(false)
    waitForCondition(10.seconds) {
      lastIssuedChangeCommand == "settings put secure enabled_accessibility_services $SELECT_TO_SPEAK_SERVICE_NAME"
    }
    assertUsageEvent(OperationKind.TALKBACK)
  }

  @Test
  fun testSetSelectToSpeakOn() {
    uiRule.configureUiSettings(talkBackInstalled = true)
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(true)
    waitForCondition(10.seconds) {
      antepenultimateChangeCommand == "settings put secure enabled_accessibility_services $SELECT_TO_SPEAK_SERVICE_NAME" &&
      lastIssuedChangeCommand == "settings put secure accessibility_button_targets $SELECT_TO_SPEAK_SERVICE_NAME"
    }
    assertUsageEvent(OperationKind.SELECT_TO_SPEAK)
  }

  @Test
  fun testSetSelectToSpeakOff() {
    uiRule.configureUiSettings(talkBackInstalled = true, selectToSpeakOn = true)
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(false)
    waitForCondition(10.seconds) {
      antepenultimateChangeCommand == "settings delete secure enabled_accessibility_services" &&
      lastIssuedChangeCommand == "settings delete secure accessibility_button_targets"
    }
    assertUsageEvent(OperationKind.SELECT_TO_SPEAK)
  }

  @Test
  fun testSetSelectToSpeakOnWithTalkBackOn() {
    uiRule.configureUiSettings(talkBackInstalled = true, talkBackOn = true)
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(true)
    waitForCondition(10.seconds) {
      antepenultimateChangeCommand == "settings put secure enabled_accessibility_services " +
                                      "$TALK_BACK_SERVICE_NAME:$SELECT_TO_SPEAK_SERVICE_NAME" &&
      lastIssuedChangeCommand == "settings put secure accessibility_button_targets $SELECT_TO_SPEAK_SERVICE_NAME"
    }
    assertUsageEvent(OperationKind.SELECT_TO_SPEAK)
  }

  @Test
  fun testSetSelectToSpeakOffWithTalkBackOn() {
    uiRule.configureUiSettings(talkBackInstalled = true, talkBackOn = true, selectToSpeakOn = true)
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(false)
    waitForCondition(10.seconds) {
      antepenultimateChangeCommand == "settings put secure enabled_accessibility_services $TALK_BACK_SERVICE_NAME" &&
      lastIssuedChangeCommand == "settings delete secure accessibility_button_targets"
    }
    assertUsageEvent(OperationKind.SELECT_TO_SPEAK)
  }

  @Test
  fun testSetFontSize() {
    controller.initAndWait()
    model.fontSizeInPercent.setFromUi(200)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "settings put system font_scale 2" }
    model.fontSizeInPercent.setFromUi(75)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "settings put system font_scale 0.75" }
    assertUsageEvent(OperationKind.FONT_SIZE, OperationKind.FONT_SIZE)
  }

  @Test
  fun testSetScreenDensity() {
    controller.initAndWait()
    model.screenDensity.setFromUi(408)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "wm density 408" }
    model.screenDensity.setFromUi(480)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "wm density 480" }
    model.screenDensity.setFromUi(544)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "wm density 544" }
    assertUsageEvent(OperationKind.SCREEN_DENSITY, OperationKind.SCREEN_DENSITY, OperationKind.SCREEN_DENSITY)
  }

  @Test
  fun testReset() {
    uiRule.configureUiSettings(
      darkMode = true,
      applicationId = APPLICATION_ID1,
      appLocales = "da",
      talkBackInstalled = true,
      talkBackOn = true,
      selectToSpeakOn = true,
      fontSize = CUSTOM_FONT_SIZE,
      physicalDensity = DEFAULT_DENSITY,
      overrideDensity = CUSTOM_DENSITY
    )
    controller.initAndWait()
    adb.shellV2Requests.clear()
    model.resetAction()
    waitForCondition(10.seconds) { adb.shellV2Requests.size == 3 }
    val commands = adb.shellV2Requests.map { it.command }
    assertThat(commands).hasSize(3)
    assertThat(commands[0]).isEqualTo(FACTORY_RESET_COMMAND.format(APPLICATION_ID1, DEFAULT_DENSITY))
    assertThat(commands[1]).isEqualTo(POPULATE_COMMAND)
    assertThat(commands[2]).isEqualTo(POPULATE_LANGUAGE_COMMAND.format(APPLICATION_ID1))
    assertUsageEvent(OperationKind.RESET)
  }

  private fun createController() =
    EmulatorUiSettingsController(uiRule.project, uiRule.emulator.serialNumber, model, uiRule.emulatorConfiguration, testRootDisposable)

  private fun EmulatorUiSettingsController.initAndWait() = runBlocking {
    populateModel()
  }

  private fun assertUsageEvent(vararg operations: OperationKind) {
    for ((index, expected) in operations.withIndex()) {
      val event = usages[index].studioEvent
      assertThat(event.kind).isEqualTo(EventKind.UI_DEVICE_SETTINGS_EVENT)
      assertThat(event.deviceInfo.deviceType).isEqualTo(DeviceType.LOCAL_EMULATOR)
      assertThat(event.deviceInfo.buildApiLevelFull).isEqualTo("33")
      assertThat(event.uiDeviceSettingsEvent.operation).isEqualTo(expected)
    }
    assertThat(usages).hasSize(operations.size)
  }
}
