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

import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.shellAsLines
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsController
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private const val DIVIDER_PREFIX = "-- "
private const val DARK_MODE_DIVIDER = "-- Dark Mode --"
private const val LIST_PACKAGES_DIVIDER = "-- List Packages --"
private const val ACCESSIBILITY_SERVICES_DIVIDER = "-- Accessibility Services --"
private const val ACCESSIBILITY_BUTTON_TARGETS_DIVIDER = "-- Accessibility Button Targets --"
private const val FONT_SIZE_DIVIDER = "-- Font Size --"
private const val DENSITY_DIVIDER = "-- Density --"

internal const val ENABLED_ACCESSIBILITY_SERVICES = "enabled_accessibility_services"
internal const val ACCESSIBILITY_BUTTON_TARGETS = "accessibility_button_targets"
private const val TALKBACK_PACKAGE_NAME = "com.google.android.marvin.talkback"
private const val TALK_BACK_SERVICE_CLASS = "com.google.android.marvin.talkback.TalkBackService"
internal const val TALK_BACK_SERVICE_NAME = "$TALKBACK_PACKAGE_NAME/$TALK_BACK_SERVICE_CLASS"
private const val SELECT_TO_SPEAK_SERVICE_CLASS = "com.google.android.accessibility.selecttospeak.SelectToSpeakService"
internal const val SELECT_TO_SPEAK_SERVICE_NAME = "$TALKBACK_PACKAGE_NAME/$SELECT_TO_SPEAK_SERVICE_CLASS"
private const val PHYSICAL_DENSITY_PATTERN = "Physical density: (\\d+)"
private const val OVERRIDE_DENSITY_PATTERN = "Override density: (\\d+)"

internal const val POPULATE_COMMAND =
  "echo $DARK_MODE_DIVIDER; " +
  "cmd uimode night; " +
  "echo $LIST_PACKAGES_DIVIDER; " +
  "pm list packages; " +
  "echo $ACCESSIBILITY_SERVICES_DIVIDER; " +
  "settings get secure $ENABLED_ACCESSIBILITY_SERVICES; " +
  "echo $ACCESSIBILITY_BUTTON_TARGETS_DIVIDER; " +
  "settings get secure $ACCESSIBILITY_BUTTON_TARGETS; " +
  "echo $FONT_SIZE_DIVIDER; " +
  "settings get system font_scale; " +
  "echo $DENSITY_DIVIDER; " +
  "wm density"

/**
 * A controller for the UI settings for an Emulator,
 * that populates the model and reacts to changes to the model initiated by the UI.
 */
internal class EmulatorUiSettingsController(
  private val project: Project,
  private val deviceSerialNumber: String,
  model: UiSettingsModel,
  parentDisposable: Disposable
) : UiSettingsController(model) {
  private val scope = AndroidCoroutineScope(parentDisposable)
  private val decimalFormat = DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US))

  override suspend fun populateModel() {
    executeShellCommand(POPULATE_COMMAND) {
      val iterator = it.listIterator()
      val enabled = AccessibilityData()
      val buttons = AccessibilityData()
      while (iterator.hasNext()) {
        when (iterator.next()) {
          DARK_MODE_DIVIDER -> processDarkMode(iterator)
          LIST_PACKAGES_DIVIDER -> processListPackages(iterator)
          ACCESSIBILITY_SERVICES_DIVIDER -> processAccessibilityServices(iterator, enabled)
          ACCESSIBILITY_BUTTON_TARGETS_DIVIDER -> processAccessibilityServices(iterator, buttons)
          FONT_SIZE_DIVIDER -> processFontSize(iterator)
          DENSITY_DIVIDER -> processScreenDensity(iterator)
        }
      }
      processAccessibility(enabled, buttons)
    }
  }

  private fun processDarkMode(iterator: ListIterator<String>) {
    val isInDarkMode = iterator.hasNext() && iterator.next() == "Night mode: yes"
    model.inDarkMode.setFromController(isInDarkMode)
  }

  private fun processListPackages(iterator: ListIterator<String>) {
    var talkBackInstalled = false
    val talkBackServiceLine = "package:$TALKBACK_PACKAGE_NAME"
    while (iterator.hasNext()) {
      val line = iterator.next()
      if (line.startsWith(DIVIDER_PREFIX)) {
        iterator.previous()
        break
      }
      talkBackInstalled = talkBackInstalled || (line == talkBackServiceLine)
    }
    model.talkBackInstalled.setFromController(talkBackInstalled)
  }

  private fun processFontSize(iterator: ListIterator<String>) {
    val fontSize = (if (iterator.hasNext()) iterator.next() else "1.0").toFloatOrNull() ?: 1f
    model.fontSizeInPercent.setFromController((fontSize * 100f + 0.5f).toInt())
  }

  private fun processScreenDensity(iterator: ListIterator<String>) {
    val physicalDensity = readDensity(iterator, PHYSICAL_DENSITY_PATTERN) ?: 160
    val overrideDensity = readDensity(iterator, OVERRIDE_DENSITY_PATTERN) ?: physicalDensity
    model.screenDensity.setFromController(overrideDensity)
  }

  private fun readDensity(iterator: ListIterator<String>, pattern: String): Int? {
    if (!iterator.hasNext()) {
      return null
    }
    val density = iterator.next()
    if (density.startsWith(DIVIDER_PREFIX)) {
      iterator.previous()
      return null
    }
    val match = Regex(pattern).find(density) ?: return null
    return match.groupValues[1].toIntOrNull()
  }

  private fun processAccessibilityServices(iterator: ListIterator<String>, data: AccessibilityData) {
    data.servicesLine = if (iterator.hasNext()) iterator.next() else "null"
  }

  private fun processAccessibility(enabled: AccessibilityData, buttonTarget: AccessibilityData) {
    model.talkBackOn.setFromController(enabled.services.contains(TALK_BACK_SERVICE_NAME))
    model.selectToSpeakOn.setFromController(enabled.services.contains(SELECT_TO_SPEAK_SERVICE_NAME) &&
                                            buttonTarget.services.contains(SELECT_TO_SPEAK_SERVICE_NAME))
  }

  override fun setDarkMode(on: Boolean) {
    val darkMode = if (on) "yes" else "no"
    scope.launch { executeShellCommand("cmd uimode night $darkMode") }
  }

  override fun setTalkBack(on: Boolean) {
    scope.launch { changeSecureSetting(ENABLED_ACCESSIBILITY_SERVICES, TALK_BACK_SERVICE_NAME, on) }
  }

  override fun setSelectToSpeak(on: Boolean) {
    scope.launch {
      changeSecureSetting(ENABLED_ACCESSIBILITY_SERVICES, SELECT_TO_SPEAK_SERVICE_NAME, on)
      changeSecureSetting(ACCESSIBILITY_BUTTON_TARGETS, SELECT_TO_SPEAK_SERVICE_NAME, on)
    }
  }

  override fun setFontSize(percent: Int) {
    scope.launch { executeShellCommand("settings put system font_scale %s".format(decimalFormat.format(percent.toFloat() / 100f))) }
  }

  override fun setScreenDensity(density: Int) {
    scope.launch { executeShellCommand("wm density %d".format(density)) }
  }

  private suspend fun changeSecureSetting(settingsName: String, serviceName: String, on: Boolean) {
    val settings = AccessibilityData()
    executeShellCommand("settings get secure $settingsName") {
      settings.servicesLine = it.singleOrNull() ?: "null"
    }
    if (on) settings.services.add(serviceName) else settings.services.remove(serviceName)
    if (settings.services.isEmpty()) {
      executeShellCommand("settings delete secure $settingsName")
    }
    else {
      executeShellCommand("settings put secure $settingsName ${settings.servicesLine}")
    }
  }

  private suspend fun executeShellCommand(command: String, commandProcessor: (lines: List<String>) -> Unit = {}) {
    val adb = AdbLibService.getSession(project).deviceServices
    val output = adb.shellAsLines(DeviceSelector.fromSerialNumber(deviceSerialNumber), command)
    val lines = output.filterIsInstance<ShellCommandOutputElement.StdoutLine>().mapNotNull { it.contents.ifBlank { null } }.toList()
    commandProcessor.invoke(lines)
  }

  private class AccessibilityData {
    val services = mutableSetOf<String>()

    var servicesLine: String
      get() = services.joinToString(":")
      set(value) {
        services.clear()
        if (value != "null") {
          services.addAll(value.split(":"))
        }
      }
  }
}
