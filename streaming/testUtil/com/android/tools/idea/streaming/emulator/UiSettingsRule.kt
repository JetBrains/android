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
import com.android.adblib.testing.FakeAdbDeviceServices
import com.android.adblib.testing.FakeAdbSession
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.testing.TestAdbLibService
import com.android.tools.idea.res.AppLanguageInfo
import com.android.tools.idea.res.AppLanguageService
import com.android.tools.idea.testing.ProjectServiceRule
import com.android.tools.idea.testing.disposable
import com.intellij.testFramework.ProjectRule
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement

const val DEFAULT_FONT_SIZE = 100
const val CUSTOM_FONT_SIZE = 130
const val DEFAULT_DENSITY = 480
const val CUSTOM_DENSITY = 560
const val APPLICATION_ID1 = "com.example.test.process1"
const val APPLICATION_ID2 = "com.example.test.process2"

/**
 * Supplies fakes for UI settings tests
 */
class UiSettingsRule(emulatorPort: Int) : ExternalResource() {
  private val appLanguageServices = AppLanguageService { listOf(
    AppLanguageInfo(APPLICATION_ID1, setOf(LocaleQualifier("da"), LocaleQualifier("es"))),
    AppLanguageInfo(APPLICATION_ID2, setOf(LocaleQualifier("ru"))))
  }
  private val projectRule = ProjectRule()
  private val adbServiceRule = ProjectServiceRule(projectRule, AdbLibService::class.java, TestAdbLibService(FakeAdbSession()))
  private val appServiceRule = ProjectServiceRule(projectRule, AppLanguageService::class.java, appLanguageServices)

  val project
    get() = projectRule.project

  val testRootDisposable
    get() = projectRule.disposable

  val adb: FakeAdbDeviceServices
    get() = AdbLibService.getSession(project).deviceServices as FakeAdbDeviceServices

  val issuedChangeCommands: List<String>
    get() = adb.shellV2Requests.map { it.command }

  val emulatorSerialNumber = "emulator-$emulatorPort"
  val deviceSelector = DeviceSelector.fromSerialNumber(emulatorSerialNumber)

  override fun before() {
    configureAdbShellCommands()
  }

  private fun configureAdbShellCommands() =
    configureUiSettings()

  fun configureUiSettings(
    darkMode: Boolean = false,
    appLocales: Map<String, String> = mapOf(APPLICATION_ID1 to "", APPLICATION_ID2 to ""),
    applicationIdsInRequest: List<String> = appLocales.keys.toList(),
    talkBackInstalled: Boolean = false,
    talkBackOn: Boolean = false,
    selectToSpeakOn: Boolean = false,
    fontSize: Int = DEFAULT_FONT_SIZE,
    physicalDensity: Int = DEFAULT_DENSITY,
    overrideDensity: Int = DEFAULT_DENSITY
  ) {
    val overrideLine = if (physicalDensity != overrideDensity) "\n      Override density: $overrideDensity" else ""
    var command = POPULATE_COMMAND
    applicationIdsInRequest.forEach { command += POPULATE_LANGUAGE_COMMAND.format(it) }

    var response = """
      -- Dark Mode --
      Night mode: ${if (darkMode) "yes" else "no"}
      -- List Packages --
      package:com.google.some.package1
      ${if (talkBackInstalled) "package:com.google.android.marvin.talkback" else "package:com.google.some.package2"}
      package:com.google.some.package3
      -- Accessibility Services --
      ${formatAccessibilityServices(talkBackOn, selectToSpeakOn)}
      -- Accessibility Button Targets --
      ${formatAccessibilityServices(talkBackOn = false, selectToSpeakOn)}
      -- Font Size --
      ${(fontSize.toFloat() / 100f)}
      -- Density --
      Physical density: $physicalDensity$overrideLine
    """.trimIndent()
    appLocales.forEach { entry ->
      response += "\n-- App Language --"
      response += "\nLocales for ${entry.key} for user 0 are [${entry.value}]"
    }

    adb.configureShellCommand(deviceSelector, command, response)
    adb.configureShellCommand(deviceSelector, "settings get secure $ENABLED_ACCESSIBILITY_SERVICES",
                              formatAccessibilityServices(talkBackOn, selectToSpeakOn))
    adb.configureShellCommand(deviceSelector, "settings get secure $ACCESSIBILITY_BUTTON_TARGETS",
                              formatAccessibilityServices(talkBackOn = false, selectToSpeakOn))
  }

  private fun formatAccessibilityServices(talkBackOn: Boolean, selectToSpeakOn: Boolean): String = when {
    talkBackOn && selectToSpeakOn -> "$TALK_BACK_SERVICE_NAME:$SELECT_TO_SPEAK_SERVICE_NAME"
    talkBackOn && !selectToSpeakOn -> TALK_BACK_SERVICE_NAME
    !talkBackOn && selectToSpeakOn -> SELECT_TO_SPEAK_SERVICE_NAME
    else -> "null"
  }

  override fun apply(base: Statement, description: Description): Statement =
    projectRule.apply(adbServiceRule.apply(appServiceRule.apply(super.apply(base, description), description), description), description)
}
