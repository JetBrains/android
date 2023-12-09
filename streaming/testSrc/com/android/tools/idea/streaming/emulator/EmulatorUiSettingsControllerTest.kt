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
import com.android.tools.idea.streaming.uisettings.binding.ReadOnlyProperty
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.google.common.truth.Truth.assertThat
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

  private val model: UiSettingsModel by lazy { UiSettingsModel(Dimension(1344, 2992), 480) } // Pixel8 Pro
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
    val darkMode = createAndAddListener(model.inDarkMode, true)
    val fontSize = createAndAddListener(model.fontSizeInPercent, 100)
    val density = createAndAddListener(model.screenDensity, 160)
    checkInitialValues(changes = 1, darkMode, fontSize, density)
  }

  @Test
  fun testReadDefaultValueWhenAttachingBeforeInit() {
    val darkMode = createAndAddListener(model.inDarkMode, true)
    val fontSize = createAndAddListener(model.fontSizeInPercent, 100)
    val density = createAndAddListener(model.screenDensity, 160)
    controller.initAndWait()
    checkInitialValues(changes = 2, darkMode, fontSize, density)
  }

  private fun checkInitialValues(
    changes: Int,
    darkMode: ListenerState<Boolean>,
    fontSize: ListenerState<Int>,
    density: ListenerState<Int>
  ) {
    assertThat(model.inDarkMode.value).isFalse()
    assertThat(darkMode.changes).isEqualTo(changes)
    assertThat(darkMode.lastValue).isFalse()
    assertThat(model.fontSizeInPercent.value).isEqualTo(100)
    assertThat(fontSize.changes).isEqualTo(changes)
    assertThat(fontSize.lastValue).isEqualTo(100)
    assertThat(model.screenDensity.value).isEqualTo(480)
    assertThat(density.changes).isEqualTo(changes)
    assertThat(density.lastValue).isEqualTo(480)
  }

  @Test
  fun testReadCustomValue() {
    rule.configureUiSettings(darkMode = true, fontSize = 85, physicalDensity = 480, overrideDensity = 544)
    controller.initAndWait()
    val darkMode = createAndAddListener(model.inDarkMode, false)
    val fontSize = createAndAddListener(model.fontSizeInPercent, 100)
    val density = createAndAddListener(model.screenDensity, 160)
    assertThat(model.inDarkMode.value).isTrue()
    assertThat(darkMode.changes).isEqualTo(1)
    assertThat(darkMode.lastValue).isTrue()
    assertThat(model.fontSizeInPercent.value).isEqualTo(85)
    assertThat(fontSize.changes).isEqualTo(1)
    assertThat(fontSize.lastValue).isEqualTo(85)
    assertThat(model.screenDensity.value).isEqualTo(544)
    assertThat(density.changes).isEqualTo(1)
    assertThat(density.lastValue).isEqualTo(544)
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

  private data class ListenerState<T>(var changes: Int, var lastValue: T)

  private fun <T> createAndAddListener(property: ReadOnlyProperty<T>, initialValue: T): ListenerState<T> {
    val state = ListenerState(0, initialValue)
    property.addControllerListener(testRootDisposable) { newValue ->
      state.changes++
      state.lastValue = newValue
    }
    return state
  }

  private fun EmulatorUiSettingsController.initAndWait() = runBlocking {
    populateModel()
  }
}
