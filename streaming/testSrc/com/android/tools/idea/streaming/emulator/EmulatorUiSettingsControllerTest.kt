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
import com.android.tools.idea.streaming.uisettings.binding.TwoWayProperty
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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

  private val model: UiSettingsModel by lazy { UiSettingsModel() }
  private val controller: EmulatorUiSettingsController by lazy { createController() }

  @Before
  fun before() {
    adb.configureShellCommand(rule.deviceSelector, "cmd uimode night yes", "Night mode: yes")
    adb.configureShellCommand(rule.deviceSelector, "cmd uimode night no", "Night mode: no")
    simulateDarkTheme(false)
  }

  @Test
  fun testReadDefaultValueWhenAttachingAfterInit() {
    controller.initAndWait()
    val state = createAndAddListener(model.inDarkMode, true)
    assertThat(model.inDarkMode.value).isFalse()
    assertThat(state.changes).isEqualTo(1)
    assertThat(state.lastValue).isFalse()
  }

  @Test
  fun testReadDefaultValueWhenAttachingBeforeInit() {
    val state = createAndAddListener(model.inDarkMode, true)
    controller.initAndWait()
    assertThat(model.inDarkMode.value).isFalse()
    assertThat(state.changes).isEqualTo(2) // After addListener and after value read
    assertThat(state.lastValue).isFalse()
  }

  @Test
  fun testReadCustomValue() {
    simulateDarkTheme(true)
    controller.initAndWait()
    val state = createAndAddListener(model.inDarkMode, false)
    assertThat(model.inDarkMode.value).isTrue()
    assertThat(state.changes).isEqualTo(1)
    assertThat(state.lastValue).isTrue()
  }

  @Test
  fun testSetNightModeOn() {
    controller.initAndWait()
    model.inDarkMode.setFromUi(true)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "cmd uimode night yes" }
  }

  @Test
  fun testSetNightOff() {
    simulateDarkTheme(true)
    controller.initAndWait()
    model.inDarkMode.setFromUi(false)
    waitForCondition(10.seconds) { lastIssuedChangeCommand == "cmd uimode night no" }
  }

  private fun createController() =
    EmulatorUiSettingsController(rule.project, rule.emulatorSerialNumber, model, testRootDisposable)

  private fun simulateDarkTheme(on: Boolean) {
    val state = if (on) "yes" else "no"
    adb.configureShellCommand(rule.deviceSelector, "cmd uimode night", "Night mode: $state")
  }

  private data class ListenerState<T>(var changes: Int, var lastValue: T)

  private fun <T> createAndAddListener(property: TwoWayProperty<T>, initialValue: T): ListenerState<T> {
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
