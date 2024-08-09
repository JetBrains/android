/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.streaming.uisettings

import com.android.adblib.AdbDeviceFailResponseException
import com.android.adblib.AdbDeviceServices
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.shellAsLines
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.streaming.uisettings.data.AppLanguage
import com.android.tools.idea.streaming.uisettings.data.DEFAULT_LANGUAGE
import com.android.tools.idea.streaming.uisettings.ui.APP_LANGUAGE_TITLE
import com.android.tools.idea.streaming.uisettings.ui.DARK_THEME_TITLE
import com.android.tools.idea.streaming.uisettings.ui.DEBUG_LAYOUT_TITLE
import com.android.tools.idea.streaming.uisettings.ui.DENSITY_TITLE
import com.android.tools.idea.streaming.uisettings.ui.FONT_SCALE_TITLE
import com.android.tools.idea.streaming.uisettings.ui.GESTURE_NAVIGATION_TITLE
import com.android.tools.idea.streaming.uisettings.ui.RESET_TITLE
import com.android.tools.idea.streaming.uisettings.ui.SELECT_TO_SPEAK_TITLE
import com.android.tools.idea.streaming.uisettings.ui.TALKBACK_TITLE
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsPanel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JSlider
import javax.swing.ListModel
import kotlin.time.Duration.Companion.seconds

internal const val APPLICATION_ID = "com.android.tools.languages"

/**
 * The UI settings to verify.
 */
private enum class Setting(
  val command: String,
  val defaultOutput: List<String>,
  val alternateDefaultOutput: List<String> = emptyList(),
  val trimFunction: (String) -> String? = ::trimValue
) {
  DARK_THEME("cmd uimode night", listOf("Night mode: no")),
  LOCALE("cmd locale get-app-locales $APPLICATION_ID", listOf("Locales for $APPLICATION_ID for user 0 are []")),
  FONT_SCALE("settings get system font_scale",  listOf("1.0"), emptyList(), ::trimFontScaleValue),
  DENSITY("wm density", listOf("Physical density: 480")),
  GESTURE("cmd overlay list android | grep -e gestural$ -e three",
          listOf("[ ] com.android.internal.systemui.navbar.threebutton", "[x] com.android.internal.systemui.navbar.gestural")),
  DEBUG_LAYOUT("getprop debug.layout", emptyList(), listOf("false")),
  TALKBACK("settings get secure enabled_accessibility_services", listOf("null")),
  BUTTON_TARGETS("settings get secure accessibility_button_targets", listOf("null")),
}

private fun trimValue(value: String): String? =
  value.trim().ifBlank { null }

private fun trimFontScaleValue(value: String): String? {
  return when (value) {
    "null" -> "1.0" // "null" is returned if the setting is deleted
    "1" -> "1.0"    // Some values are specified without a decimal point
    "2" -> "2.0"
    else -> trimValue(value)
  }
}

/**
 * Holds test methods shared between [EmulatorUiSettingsIntegrationTest] and [DeviceUiSettingsIntegrationTest].
 */
internal class UiSettingsTester(private val project: Project, deviceSerialNumber: String) {
  private val device = DeviceSelector.fromSerialNumber(deviceSerialNumber)
  private val adb: AdbDeviceServices
    get() = AdbLibService.getSession(project).deviceServices

  suspend fun waitForLanguagesAppToRun() {
    executeCommand("am start -n $APPLICATION_ID/$APPLICATION_ID.MainActivity")
    delayUntilCondition(200, 30.seconds) {
      executeCommand("dumpsys activity processes | grep top-activity").any { it.contains(":$APPLICATION_ID/") }
    }
  }

  suspend fun testSettings(panel: UiSettingsPanel) {
    checkInitialSettings()
    changeDarkMode(panel)
    changeLocale(panel)
    changeTalkback(panel)
    changeSelectToSpeak(panel)
    changeFontSize(panel)
    changeDensity(panel)
    changeGestureNavigation(panel)
    changeDebugLayout(panel)

    // Wait before calling/causing reset:
    waitForPreviousWriteToComplete()
  }

  suspend fun checkInitialSettings() {
    Setting.entries.forEach { waitForSetting(it, it.defaultOutput, it.alternateDefaultOutput) }
  }

  suspend fun reconnectToAdb() {
    delayUntilCondition(100, 90.seconds) {
      try {
        val result = executeCommand("echo 123")
        result.singleOrNull() == "123"
      }
      catch (ex: AdbDeviceFailResponseException) {
        false
      }
    }
  }

  fun resetSettings(panel: UiSettingsPanel) {
    val button = panel.getDescendant<JButton> { it.text == RESET_TITLE }
    button.doClick()
  }

  private suspend fun changeDarkMode(panel: UiSettingsPanel) {
    val checkBox = panel.getDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }
    assertThat(checkBox.isShowing).isTrue()
    assertThat(checkBox.isSelected).isFalse()

    waitForPreviousWriteToComplete()
    checkBox.doClick()
    waitForSetting(Setting.DARK_THEME, listOf("Night mode: yes"))

    waitForPreviousWriteToComplete()
    checkBox.doClick()
    waitForSetting(Setting.DARK_THEME, listOf("Night mode: no"))

    waitForPreviousWriteToComplete()
    checkBox.doClick()
    waitForSetting(Setting.DARK_THEME, listOf("Night mode: yes"))
  }

  private suspend fun changeLocale(panel: UiSettingsPanel) {
    val comboBox = panel.getDescendant<JComboBox<AppLanguage>> { it.name == APP_LANGUAGE_TITLE }
    assertThat(comboBox.isShowing).isTrue()
    assertThat(comboBox.selectedIndex).isEqualTo(0)
    checkLocaleModel(comboBox.model)

    waitForPreviousWriteToComplete()
    comboBox.selectedIndex = 1
    waitForSetting(Setting.LOCALE, listOf("Locales for $APPLICATION_ID for user 0 are [da]"))

    waitForPreviousWriteToComplete()
    comboBox.selectedIndex = 2
    waitForSetting(Setting.LOCALE, listOf("Locales for $APPLICATION_ID for user 0 are [it]"))

    waitForPreviousWriteToComplete()
    comboBox.selectedIndex = 3
    waitForSetting(Setting.LOCALE, listOf("Locales for $APPLICATION_ID for user 0 are [es]"))
  }

  private suspend fun changeTalkback(panel: UiSettingsPanel) {
    val checkBox = panel.getDescendant<JCheckBox> { it.name == TALKBACK_TITLE }
    assertThat(checkBox.isVisible).isTrue()
    assertThat(checkBox.isSelected).isFalse()

    waitForPreviousWriteToComplete()
    checkBox.doClick()
    waitForSetting(Setting.TALKBACK, listOf("com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"))
    waitForSetting(Setting.BUTTON_TARGETS, listOf("null"))
  }

  private suspend fun changeSelectToSpeak(panel: UiSettingsPanel) {
    val checkBox = panel.getDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }
    assertThat(checkBox.isShowing).isTrue()
    assertThat(checkBox.isSelected).isFalse()

    // Talkback is already on from changeTalkback
    waitForPreviousWriteToComplete()
    checkBox.doClick()
    waitForSetting(Setting.TALKBACK,
                   listOf("com.google.android.marvin.talkback/com.google.android.accessibility.selecttospeak.SelectToSpeakService:com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"),
                   listOf("com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService:com.google.android.marvin.talkback/com.google.android.accessibility.selecttospeak.SelectToSpeakService"))
    waitForSetting(Setting.BUTTON_TARGETS, listOf("com.google.android.marvin.talkback/com.google.android.accessibility.selecttospeak.SelectToSpeakService"))
  }

  private suspend fun changeFontSize(panel: UiSettingsPanel) {
    val slider = panel.getDescendant<JSlider> { it.name == FONT_SCALE_TITLE }
    assertThat(slider.value).isEqualTo(1)

    waitForPreviousWriteToComplete()
    slider.value = 0
    waitForSetting(Setting.FONT_SCALE, listOf("0.85"))

    waitForPreviousWriteToComplete()
    slider.value = 1
    waitForSetting(Setting.FONT_SCALE, listOf("1.0"))

    waitForPreviousWriteToComplete()
    slider.value = 2
    waitForSetting(Setting.FONT_SCALE, listOf("1.15"))

    waitForPreviousWriteToComplete()
    slider.value = 3
    waitForSetting(Setting.FONT_SCALE, listOf("1.3"))
    if (slider.maximum > 3) {
      waitForPreviousWriteToComplete()
      slider.value = 4
      waitForSetting(Setting.FONT_SCALE, listOf("1.5"))

      waitForPreviousWriteToComplete()
      slider.value = 5
      waitForSetting(Setting.FONT_SCALE, listOf("1.8"))

      waitForPreviousWriteToComplete()
      slider.value = 6
      waitForSetting(Setting.FONT_SCALE, listOf("2.0"))
    }
  }

  private suspend fun changeDensity(panel: UiSettingsPanel) {
    val slider = panel.getDescendant<JSlider> { it.name == DENSITY_TITLE }
    assertThat(slider.value).isEqualTo(1)
    assertThat(slider.maximum).isEqualTo(2)

    waitForPreviousWriteToComplete()
    slider.value = 0
    waitForSetting(Setting.DENSITY, listOf("Physical density: 480", "Override density: 408"))

    waitForPreviousWriteToComplete()
    slider.value = 1
    waitForSetting(Setting.DENSITY, listOf("Physical density: 480"))

    waitForPreviousWriteToComplete()
    slider.value = 0
    waitForSetting(Setting.DENSITY, listOf("Physical density: 480", "Override density: 408"))
  }

  private suspend fun changeGestureNavigation(panel: UiSettingsPanel) {
    val comboBox = panel.getDescendant<JComboBox<AppLanguage>> { it.name == GESTURE_NAVIGATION_TITLE }
    assertThat(comboBox.isShowing).isTrue()
    assertThat(comboBox.selectedIndex).isEqualTo(0)
    checkGestureModel(comboBox.model)

    waitForPreviousWriteToComplete()
    comboBox.selectedIndex = 1
    waitForSetting(Setting.GESTURE, listOf("[ ] com.android.internal.systemui.navbar.gestural", "[x] com.android.internal.systemui.navbar.threebutton"))

    waitForPreviousWriteToComplete()
    comboBox.selectedIndex = 0
    waitForSetting(Setting.GESTURE, listOf("[ ] com.android.internal.systemui.navbar.threebutton", "[x] com.android.internal.systemui.navbar.gestural"))

    waitForPreviousWriteToComplete()
    comboBox.selectedIndex = 1
    waitForSetting(Setting.GESTURE, listOf("[ ] com.android.internal.systemui.navbar.gestural", "[x] com.android.internal.systemui.navbar.threebutton"))
  }

  private suspend fun changeDebugLayout(panel: UiSettingsPanel) {
    val checkBox = panel.getDescendant<JCheckBox> { it.name == DEBUG_LAYOUT_TITLE }
    assertThat(checkBox.isVisible).isTrue()
    assertThat(checkBox.isSelected).isFalse()

    waitForPreviousWriteToComplete()
    checkBox.doClick()
    waitForSetting(Setting.DEBUG_LAYOUT, listOf("true"))

    waitForPreviousWriteToComplete()
    checkBox.doClick()
    waitForSetting(Setting.DEBUG_LAYOUT, listOf("false"))

    waitForPreviousWriteToComplete()
    checkBox.doClick()
    waitForSetting(Setting.DEBUG_LAYOUT, listOf("true"))
  }

  private fun checkLocaleModel(model: ListModel<AppLanguage>) {
    assertThat(model.size).isEqualTo(4)
    assertThat(model.getElementAt(0)).isEqualTo(DEFAULT_LANGUAGE)
    assertThat(model.getElementAt(1)).isEqualTo(AppLanguage(LocaleQualifier(null, "da", null, null), "Danish"))
    assertThat(model.getElementAt(2)).isEqualTo(AppLanguage(LocaleQualifier(null, "it", null, null), "Italian"))
    assertThat(model.getElementAt(3)).isEqualTo(AppLanguage(LocaleQualifier(null, "es", null, null), "Spanish"))
  }

  private fun checkGestureModel(model: ListModel<AppLanguage>) {
    assertThat(model.size).isEqualTo(2)
    assertThat(model.getElementAt(0)).isEqualTo(true) // Gestures
    assertThat(model.getElementAt(1)).isEqualTo(false) // Buttons
  }

  private suspend fun waitForSetting(setting: Setting, expected: List<String>, alternate: List<String> = listOf("ZZ-NO-MATCH-ZZ")) {
    var lastResult= emptyList<String>()
    try {
      delayUntilCondition(50, 10.seconds) {
        lastResult = executeCommand(setting.command, setting.trimFunction)
        lastResult == expected || lastResult == alternate
      }
    }
    catch (ex: TimeoutCancellationException) {
      throw RuntimeException("Timeout waiting for $setting to yield: ${expected.joinToString()}, was: ${lastResult.joinToString()}", ex)
    }
  }

  /**
   * Wait for previous write to complete.
   *
   * Without this wait this test gets a lof of failures like:
   * - Timeout waiting for DARK_THEME to yield: Night mode: no, was: Night mode: yes
   * - Timeout waiting for FONT_SCALE to yield: 1.0, was: 0.85
   * - Timeout waiting for DENSITY to yield: Physical density: 480, was: Physical density: 480, Override density: 408
   *
   * Theory: It is possible that a previous write transaction caused the new value to be visible to reads before the write transaction
   * has finished. After reading the value this test may cause another write transaction which may be overwritten by the previous
   * transaction.
   *
   * Since those writes happen in async calls in the production code we cannot programmatically wait for the transaction to finish.
   * Instead, wait a few seconds and hope any previous transactions are done.
   */
  private suspend fun waitForPreviousWriteToComplete() {
    delay(8.seconds)
  }

  private suspend fun executeCommand(command: String, trimFunction: (String) -> String? = ::trimValue): List<String> {
    val output = adb.shellAsLines(device, command)
    return output.filterIsInstance<ShellCommandOutputElement.StdoutLine>().mapNotNull { trimFunction(it.contents) }.toList()
  }
}
