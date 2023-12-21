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
import com.android.tools.idea.streaming.uisettings.testutil.UiControllerListenerValidator
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.intellij.openapi.Disposable
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import kotlin.time.Duration.Companion.seconds

class EmulatorUiSettingsControllerTest {

  @get:Rule
  val rule = UiSettingsRule(emulatorPort = 4445)

  private val testRootDisposable: Disposable
    get() = rule.testRootDisposable

  private val adb: FakeAdbDeviceServices
    get() = rule.adb

  private val lastIssuedChangeCommand: String?
    get() = rule.issuedChangeCommands.lastOrNull()

  private val antepenultimateChangeCommand: String?
    get() = rule.issuedChangeCommands.let { if (it.size > 2) it[it.size - 3] else null }

  private val model: UiSettingsModel by lazy { UiSettingsModel(Dimension(1344, 2992), DEFAULT_DENSITY) } // Pixel 8 Pro
  private val controller: EmulatorUiSettingsController by lazy { createController() }

  @Before
  fun before() {
    adb.configureShellCommand(rule.deviceSelector, "cmd uimode night yes", "Night mode: yes")
    adb.configureShellCommand(rule.deviceSelector, "cmd uimode night no", "Night mode: no")
    adb.configureShellCommand(rule.deviceSelector, "settings put system font_scale 2", "")
    adb.configureShellCommand(rule.deviceSelector, "settings put system font_scale 0.75", "")
    adb.configureShellCommand(rule.deviceSelector, "wm density 408", "Physical density: 480\nOverride density: 408")
    adb.configureShellCommand(rule.deviceSelector, "wm density 480", "Physical density: 480")
    adb.configureShellCommand(rule.deviceSelector, "wm density 544", "Physical density: 480\nOverride density: 544")
    adb.configureShellCommand(rule.deviceSelector, "settings put secure enabled_accessibility_services $TALK_BACK_SERVICE_NAME", "")
    adb.configureShellCommand(rule.deviceSelector, "settings delete secure enabled_accessibility_services", "")
    adb.configureShellCommand(rule.deviceSelector, "settings put secure enabled_accessibility_services $SELECT_TO_SPEAK_SERVICE_NAME", "")
    adb.configureShellCommand(rule.deviceSelector, "settings put secure accessibility_button_targets ${SELECT_TO_SPEAK_SERVICE_NAME}", "")
    adb.configureShellCommand(rule.deviceSelector, "settings delete secure accessibility_button_targets", "")
    adb.configureShellCommand(rule.deviceSelector, "settings put secure enabled_accessibility_services " +
                                                   "$TALK_BACK_SERVICE_NAME:$SELECT_TO_SPEAK_SERVICE_NAME", "")
    adb.configureShellCommand(rule.deviceSelector, "settings put secure enabled_accessibility_services " +
                                                   "$SELECT_TO_SPEAK_SERVICE_NAME:$TALK_BACK_SERVICE_NAME", "")
  }

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
    rule.configureUiSettings(
      darkMode = true,
      talkBackInstalled = true,
      talkBackOn = true,
      selectToSpeakOn = true,
      fontSize = CUSTOM_FONT_SIZE,
      physicalDensity = DEFAULT_DENSITY,
      overrideDensity = CUSTOM_DENSITY
    )
    controller.initAndWait()
    val listeners = UiControllerListenerValidator(model, customValues = false)
    listeners.checkValues(expectedChanges = 1, expectedCustomValues = true)
  }

  @Test
  fun testSetNightModeOn() {
    controller.initAndWait()
    model.inDarkMode.setFromUi(true)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "cmd uimode night yes" }
  }

  @Test
  fun testSetNightOff() {
    rule.configureUiSettings(darkMode = true)
    controller.initAndWait()
    model.inDarkMode.setFromUi(false)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "cmd uimode night no" }
  }

  @Test
  fun testSetTalkBackOn() {
    rule.configureUiSettings(talkBackInstalled = true)
    controller.initAndWait()
    model.talkBackOn.setFromUi(true)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "settings put secure enabled_accessibility_services $TALK_BACK_SERVICE_NAME" }
  }

  @Test
  fun testSetTalkBackOff() {
    rule.configureUiSettings(talkBackInstalled = true, talkBackOn = true)
    controller.initAndWait()
    model.talkBackOn.setFromUi(false)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "settings delete secure enabled_accessibility_services" }
  }

  @Test
  fun testSetTalkBackOnWithSelectToSpeakOn() {
    rule.configureUiSettings(talkBackInstalled = true, selectToSpeakOn = true)
    controller.initAndWait()
    model.talkBackOn.setFromUi(true)
    waitForCondition(10.seconds) {
      lastIssuedChangeCommand == "settings put secure enabled_accessibility_services $SELECT_TO_SPEAK_SERVICE_NAME:$TALK_BACK_SERVICE_NAME"
    }
  }

  @Test
  fun testSetTalkBackOffWithSelectToSpeakOn() {
    rule.configureUiSettings(talkBackInstalled = true, talkBackOn = true, selectToSpeakOn = true)
    controller.initAndWait()
    model.talkBackOn.setFromUi(false)
    waitForCondition(10.seconds) {
      lastIssuedChangeCommand == "settings put secure enabled_accessibility_services $SELECT_TO_SPEAK_SERVICE_NAME"
    }
  }

  @Test
  fun testSetSelectToSpeakOn() {
    rule.configureUiSettings(talkBackInstalled = true)
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(true)
    waitForCondition(10.seconds) {
      antepenultimateChangeCommand == "settings put secure enabled_accessibility_services $SELECT_TO_SPEAK_SERVICE_NAME" &&
      lastIssuedChangeCommand == "settings put secure accessibility_button_targets $SELECT_TO_SPEAK_SERVICE_NAME"
    }
  }

  @Test
  fun testSetSelectToSpeakOff() {
    rule.configureUiSettings(talkBackInstalled = true, selectToSpeakOn = true)
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(false)
    waitForCondition(10.seconds) {
      antepenultimateChangeCommand == "settings delete secure enabled_accessibility_services" &&
      lastIssuedChangeCommand == "settings delete secure accessibility_button_targets"
    }
  }

  @Test
  fun testSetSelectToSpeakOnWithTalkBackOn() {
    rule.configureUiSettings(talkBackInstalled = true, talkBackOn = true)
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(true)
    waitForCondition(10.seconds) {
      antepenultimateChangeCommand == "settings put secure enabled_accessibility_services " +
                                      "$TALK_BACK_SERVICE_NAME:$SELECT_TO_SPEAK_SERVICE_NAME" &&
      lastIssuedChangeCommand == "settings put secure accessibility_button_targets $SELECT_TO_SPEAK_SERVICE_NAME"
    }
  }

  @Test
  fun testSetSelectToSpeakOffWithTalkBackOn() {
    rule.configureUiSettings(talkBackInstalled = true, talkBackOn = true, selectToSpeakOn = true)
    controller.initAndWait()
    model.selectToSpeakOn.setFromUi(false)
    waitForCondition(10.seconds) {
      antepenultimateChangeCommand == "settings put secure enabled_accessibility_services $TALK_BACK_SERVICE_NAME" &&
      lastIssuedChangeCommand == "settings delete secure accessibility_button_targets"
    }
  }

  @Test
  fun testSetFontSize() {
    controller.initAndWait()
    model.fontSizeInPercent.setFromUi(200)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "settings put system font_scale 2" }
    model.fontSizeInPercent.setFromUi(75)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "settings put system font_scale 0.75" }
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
  }

  private fun createController() =
    EmulatorUiSettingsController(rule.project, rule.emulatorSerialNumber, model, testRootDisposable)

  private fun EmulatorUiSettingsController.initAndWait() = runBlocking {
    populateModel()
  }
}
