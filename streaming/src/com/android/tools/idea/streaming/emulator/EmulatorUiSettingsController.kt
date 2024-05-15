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
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.res.AppLanguageInfo
import com.android.tools.idea.res.AppLanguageService
import com.android.tools.idea.stats.AnonymizerUtil
import com.android.tools.idea.streaming.uisettings.data.AppLanguage
import com.android.tools.idea.streaming.uisettings.data.hasLimitedUiSettingsSupport
import com.android.tools.idea.streaming.uisettings.stats.UiSettingsStats
import com.android.tools.idea.streaming.uisettings.ui.FontScale
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsController
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.google.wireless.android.sdk.stats.DeviceInfo
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
private const val GESTURES_DIVIDER = "-- Gestures --"
private const val LIST_PACKAGES_DIVIDER = "-- List Packages --"
private const val ACCESSIBILITY_SERVICES_DIVIDER = "-- Accessibility Services --"
private const val ACCESSIBILITY_BUTTON_TARGETS_DIVIDER = "-- Accessibility Button Targets --"
private const val FONT_SCALE_DIVIDER = "-- Font Scale --"
private const val DENSITY_DIVIDER = "-- Density --"
private const val FOREGROUND_APPLICATION_DIVIDER = "-- Foreground Application --"
private const val APP_LANGUAGE_DIVIDER = "-- App Language --"

internal const val GESTURES_OVERLAY = "com.android.internal.systemui.navbar.gestural"
internal const val THREE_BUTTON_OVERLAY = "com.android.internal.systemui.navbar.threebutton"
internal const val ENABLED_ACCESSIBILITY_SERVICES = "enabled_accessibility_services"
internal const val ACCESSIBILITY_BUTTON_TARGETS = "accessibility_button_targets"
private const val TALKBACK_PACKAGE_NAME = "com.google.android.marvin.talkback"
private const val TALK_BACK_SERVICE_CLASS = "com.google.android.marvin.talkback.TalkBackService"
internal const val TALK_BACK_SERVICE_NAME = "$TALKBACK_PACKAGE_NAME/$TALK_BACK_SERVICE_CLASS"
private const val SELECT_TO_SPEAK_SERVICE_CLASS = "com.google.android.accessibility.selecttospeak.SelectToSpeakService"
internal const val SELECT_TO_SPEAK_SERVICE_NAME = "$TALKBACK_PACKAGE_NAME/$SELECT_TO_SPEAK_SERVICE_CLASS"
private const val PHYSICAL_DENSITY_PATTERN = "Physical density: (\\d+)"
private const val OVERRIDE_DENSITY_PATTERN = "Override density: (\\d+)"
private const val FOREGROUND_APPLICATION_PATTERN = "mFocusedApp=ActivityRecord.* .* (\\S*)/\\S* "
private const val APP_LANGUAGE_PATTERN = "Locales for (.+) for user \\d+ are \\[(.*)]"

internal const val POPULATE_COMMAND =
  "echo $DARK_MODE_DIVIDER; " +
  "cmd uimode night; " +
  "echo $GESTURES_DIVIDER; " +
  "cmd overlay list android | grep $GESTURES_OVERLAY\$; " +
  "echo $LIST_PACKAGES_DIVIDER; " +
  "pm list packages | grep package:$TALKBACK_PACKAGE_NAME\$; " +
  "echo $ACCESSIBILITY_SERVICES_DIVIDER; " +
  "settings get secure $ENABLED_ACCESSIBILITY_SERVICES; " +
  "echo $ACCESSIBILITY_BUTTON_TARGETS_DIVIDER; " +
  "settings get secure $ACCESSIBILITY_BUTTON_TARGETS; " +
  "echo $FONT_SCALE_DIVIDER; " +
  "settings get system font_scale; " +
  "echo $DENSITY_DIVIDER; " +
  "wm density; " +
  "echo $FOREGROUND_APPLICATION_DIVIDER; " +
  "dumpsys activity activities | grep mFocusedApp=ActivityRecord; "

internal const val POPULATE_LANGUAGE_COMMAND =
  "echo $APP_LANGUAGE_DIVIDER; " +
  "cmd locale get-app-locales %s; "  // Parameter: applicationId

internal const val FACTORY_RESET_COMMAND_FOR_LIMITED_DEVICE =
  "cmd uimode night no; " +
  "cmd locale set-app-locales %s --locales null; " +
  "settings delete secure $ENABLED_ACCESSIBILITY_SERVICES; " +
  "settings delete secure $ACCESSIBILITY_BUTTON_TARGETS; " +
  "settings put system font_scale 1; " // Parameters: applicationId

internal const val FACTORY_RESET_COMMAND =
  "cmd uimode night no; " +
  "cmd overlay enable $GESTURES_OVERLAY; " +
  "cmd locale set-app-locales %s --locales null; " +
  "settings delete secure $ENABLED_ACCESSIBILITY_SERVICES; " +
  "settings delete secure $ACCESSIBILITY_BUTTON_TARGETS; " +
  "settings put system font_scale 1; " +
  "wm density %d; "  // Parameters: applicationId, density

private fun EmulatorConfiguration.toDeviceInfo(serialNumber: String): DeviceInfo {
  return DeviceInfo.newBuilder()
    .setDeviceType(DeviceInfo.DeviceType.LOCAL_EMULATOR)
    .setAnonymizedSerialNumber(AnonymizerUtil.anonymizeUtf8(serialNumber))
    .setBuildApiLevelFull(AndroidVersion(api, null).apiStringWithExtension)
    .build()
}

/**
 * A controller for the UI settings for an Emulator,
 * that populates the model and reacts to changes to the model initiated by the UI.
 */
internal class EmulatorUiSettingsController(
  private val project: Project,
  private val deviceSerialNumber: String,
  model: UiSettingsModel,
  emulatorConfig: EmulatorConfiguration,
  parentDisposable: Disposable,
) : UiSettingsController(model, UiSettingsStats(emulatorConfig.toDeviceInfo(deviceSerialNumber))) {
  private val scope = AndroidCoroutineScope(parentDisposable)
  private val decimalFormat = DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US))
  private var readApplicationId = ""
  private var readPhysicalDensity = 160
  private val hasLimitedUiSettingsSupportForDevice = emulatorConfig.deviceType.hasLimitedUiSettingsSupport
  private var lastDarkMode = false
  private var lastGestureNavigation = false
  private var lastLocaleTag = ""
  private var lastTalkBack = false
  private var lastSelectToSpeak = false
  private var lastFontScale = FontScale.NORMAL.percent
  private var lastDensity = readPhysicalDensity

  override suspend fun populateModel() {
    val context = CommandContext(project)
    executeCommand(POPULATE_COMMAND, context)

    if (context.applicationId.isNotEmpty() && context.languageInfo.keys.contains(context.applicationId)) {
      executeCommand(POPULATE_LANGUAGE_COMMAND.format(context.applicationId), context)
    }
    processAccessibility(context.enabled, context.buttons)
    updateResetButton()

    // Assume all emulators have settable font scale and density.
    // We do not have any OEM system images for our emulators.
    model.fontScaleSettable.setFromController(true)
    model.screenDensitySettable.setFromController(true)
  }

  private suspend fun executeCommand(command: String, context: CommandContext) {
    val iterator = executeShellCommand(command).listIterator()
    while (iterator.hasNext()) {
      when (iterator.next()) {
        DARK_MODE_DIVIDER -> processDarkMode(iterator)
        GESTURES_DIVIDER -> processGestureNavigation(iterator)
        LIST_PACKAGES_DIVIDER -> processListPackages(iterator)
        ACCESSIBILITY_SERVICES_DIVIDER -> processAccessibilityServices(iterator, context.enabled)
        ACCESSIBILITY_BUTTON_TARGETS_DIVIDER -> processAccessibilityServices(iterator, context.buttons)
        FONT_SCALE_DIVIDER -> processFontScale(iterator)
        DENSITY_DIVIDER -> processScreenDensity(iterator)
        FOREGROUND_APPLICATION_DIVIDER -> processForegroundApplication(iterator, context)
        APP_LANGUAGE_DIVIDER -> processAppLanguage(iterator, context.languageInfo)
      }
    }
  }

  private fun processDarkMode(iterator: ListIterator<String>) {
    val isInDarkMode = iterator.hasNext() && iterator.next() == "Night mode: yes"
    model.inDarkMode.setFromController(isInDarkMode)
    lastDarkMode = isInDarkMode
  }

  private fun processGestureNavigation(iterator: ListIterator<String>) {
    var gestureOverlayInstalled = false
    var gestureNavigation = false
    if (iterator.hasNext()) {
      val line = iterator.next()
      if (line.startsWith(DIVIDER_PREFIX)) {
        iterator.previous()
      }
      else {
        gestureOverlayInstalled = true
        gestureNavigation = line == "[x] $GESTURES_OVERLAY"
      }
    }
    model.gestureOverlayInstalled.setFromController(gestureOverlayInstalled)
    model.gestureNavigation.setFromController(gestureNavigation)
    lastGestureNavigation = gestureNavigation
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

  private fun processFontScale(iterator: ListIterator<String>) {
    val fontScale = (if (iterator.hasNext()) iterator.next() else "1.0").toFloatOrNull() ?: 1f
    model.fontScaleInPercent.setFromController((fontScale * 100f + 0.5f).toInt())
    lastFontScale = model.fontScaleInPercent.value
  }

  private fun processScreenDensity(iterator: ListIterator<String>) {
    val physicalDensity = readDensity(iterator, PHYSICAL_DENSITY_PATTERN) ?: 160
    val overrideDensity = readDensity(iterator, OVERRIDE_DENSITY_PATTERN) ?: physicalDensity
    model.screenDensity.setFromController(overrideDensity)
    readPhysicalDensity = physicalDensity
    lastDensity = overrideDensity
  }

  private fun processAppLanguage(iterator: ListIterator<String>, info: Map<String, AppLanguageInfo>) {
    val appLanguageLine = (if (iterator.hasNext()) iterator.next() else "")
    if (appLanguageLine.startsWith(DIVIDER_PREFIX)) {
      iterator.previous()
      return
    }
    val match = Regex(APP_LANGUAGE_PATTERN).find(appLanguageLine) ?: return
    val applicationId = match.groupValues[1]
    val localeTag = match.groupValues[2].split(",").firstOrNull().takeIf { it != "null" } ?: ""
    val localeConfig = info[applicationId]?.localeConfig ?: return
    addLanguage(applicationId, localeConfig, localeTag)
    readApplicationId = applicationId
    lastLocaleTag = localeTag
  }

  private fun processForegroundApplication(iterator: ListIterator<String>, context: CommandContext) {
    val line = if (iterator.hasNext()) iterator.next() else return
    if (line.startsWith(DIVIDER_PREFIX)) {
      iterator.previous()
      return
    }
    val match = Regex(FOREGROUND_APPLICATION_PATTERN).find(line) ?: return
    context.applicationId = match.groupValues[1]
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
    lastTalkBack = model.talkBackOn.value
    lastSelectToSpeak = model.selectToSpeakOn.value
  }

  override fun setDarkMode(on: Boolean) {
    val darkMode = if (on) "yes" else "no"
    scope.launch { executeShellCommand("cmd uimode night $darkMode") }
    lastDarkMode = on
    updateResetButton()
  }

  override fun setGestureNavigation(on: Boolean) {
    val operation = if (on) "enable" else "disable"
    val opposite = if (!on) "enable" else "disable"
    scope.launch { executeShellCommand("cmd overlay $operation $GESTURES_OVERLAY; cmd overlay $opposite $THREE_BUTTON_OVERLAY") }
    lastGestureNavigation = on
    updateResetButton()
  }

  override fun setAppLanguage(applicationId: String, language: AppLanguage?) {
    if (applicationId.isNotEmpty()) {
      scope.launch { executeShellCommand("cmd locale set-app-locales %s --locales %s".format(applicationId, language?.tag)) }
      lastLocaleTag = language?.tag ?: ""
      updateResetButton()
    }
  }

  override fun setTalkBack(on: Boolean) {
    scope.launch { changeSecureSetting(ENABLED_ACCESSIBILITY_SERVICES, TALK_BACK_SERVICE_NAME, on) }
    lastTalkBack = on
    updateResetButton()
  }

  override fun setSelectToSpeak(on: Boolean) {
    scope.launch {
      changeSecureSetting(ENABLED_ACCESSIBILITY_SERVICES, SELECT_TO_SPEAK_SERVICE_NAME, on)
      changeSecureSetting(ACCESSIBILITY_BUTTON_TARGETS, SELECT_TO_SPEAK_SERVICE_NAME, on)
    }
    lastSelectToSpeak = on
    updateResetButton()
  }

  override fun setFontScale(percent: Int) {
    scope.launch { executeShellCommand("settings put system font_scale %s".format(decimalFormat.format(percent.toFloat() / 100f))) }
    lastFontScale = percent
    updateResetButton()
  }

  override fun setScreenDensity(density: Int) {
    scope.launch { executeShellCommand("wm density %d".format(density)) }
    lastDensity = density
    updateResetButton()
  }

  override fun reset() {
    scope.launch {
      if (hasLimitedUiSettingsSupportForDevice) {
        executeShellCommand(FACTORY_RESET_COMMAND_FOR_LIMITED_DEVICE.format(readApplicationId))
      }
      else {
        executeShellCommand(FACTORY_RESET_COMMAND.format(readApplicationId, readPhysicalDensity))
      }
      populateModel()
    }
  }

  private fun updateResetButton() {
    var isDefault = !lastDarkMode && lastLocaleTag.isEmpty() && !lastTalkBack && lastFontScale == FontScale.NORMAL.percent
    if (!hasLimitedUiSettingsSupportForDevice) {
      isDefault =
        isDefault &&
        lastGestureNavigation &&
        !lastSelectToSpeak &&
        lastDensity == readPhysicalDensity
    }
    model.differentFromDefault.setFromController(!isDefault)
  }

  private suspend fun changeSecureSetting(settingsName: String, serviceName: String, on: Boolean) {
    val settings = AccessibilityData()
    val lines = executeShellCommand("settings get secure $settingsName")
    settings.servicesLine = lines.singleOrNull() ?: "null"

    if (on) settings.services.add(serviceName) else settings.services.remove(serviceName)
    if (settings.services.isEmpty()) {
      executeShellCommand("settings delete secure $settingsName")
    }
    else {
      executeShellCommand("settings put secure $settingsName ${settings.servicesLine}")
    }
  }

  private suspend fun executeShellCommand(command: String): List<String> {
    val adb = AdbLibService.getSession(project).deviceServices
    val output = adb.shellAsLines(DeviceSelector.fromSerialNumber(deviceSerialNumber), command)
    return output.filterIsInstance<ShellCommandOutputElement.StdoutLine>().mapNotNull { it.contents.ifBlank { null } }.toList()
  }

  /**
   * Captured accessibility service names.
   */
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

  /**
   * Context for handling adb commands.
   */
  private class CommandContext(project: Project) {
    val enabled = AccessibilityData()
    val buttons = AccessibilityData()
    var applicationId = ""
    val languageInfo = AppLanguageService.getInstance(project).getAppLanguageInfo().associateBy { it.applicationId }
  }
}
