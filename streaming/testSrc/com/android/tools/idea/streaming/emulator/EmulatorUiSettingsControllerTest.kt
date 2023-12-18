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
    get() = rule.issuedChangeCommands.lastOrNull { it != "cmd uimode night" }

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
  }

  @Test
  fun testReadDefaultValueWhenAttachingAfterInit() {
    controller.initAndWait()
    val listeners = UiControllerListenerValidator(model, customValues = true, testRootDisposable)
    listeners.checkValues(expectedChanges = 1, expectedCustomValues = false)
  }

  @Test
  fun testReadDefaultValueWhenAttachingBeforeInit() {
    val listeners = UiControllerListenerValidator(model, customValues = true, testRootDisposable)
    controller.initAndWait()
    listeners.checkValues(expectedChanges = 2, expectedCustomValues = false)
  }

  @Test
  fun testReadCustomValue() {
    rule.configureUiSettings(darkMode = true, fontSize = CUSTOM_FONT_SIZE, overrideDensity = CUSTOM_DENSITY)
    controller.initAndWait()
    val listeners = UiControllerListenerValidator(model, customValues = false, testRootDisposable)
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
    waitForCondition(120.seconds) { lastIssuedChangeCommand == "cmd uimode night no" }
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
