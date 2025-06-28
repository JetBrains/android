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
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.testutils.waitForCondition
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.testing.FakeAdbSessionRule
import com.android.tools.idea.res.AppLanguageInfo
import com.android.tools.idea.res.AppLanguageService
import com.android.tools.idea.testing.ProjectServiceRule
import com.android.tools.idea.testing.disposable
import com.intellij.testFramework.ProjectRule
import com.jetbrains.rd.util.forEachReversed
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.junit.rules.TestName
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

const val DEFAULT_FONT_SCALE = 100
const val CUSTOM_FONT_SCALE = 130
const val DEFAULT_DENSITY = 480
const val CUSTOM_DENSITY = 560
const val APPLICATION_ID1 = "com.example.test.app1"
const val APPLICATION_ID2 = "com.example.test.app2"

private val API_REGEX = "(.+)Api(\\d+)$".toRegex()

/**
 * Supplies fakes for UI settings tests
 */
class UiSettingsRule : ExternalResource() {
  private val appLanguageServices = AppLanguageService { context ->
    when (context.applicationId) {
      APPLICATION_ID1 -> AppLanguageInfo(APPLICATION_ID1, setOf(LocaleQualifier("da"), LocaleQualifier("ru")))
      APPLICATION_ID2 -> AppLanguageInfo(APPLICATION_ID2, setOf(LocaleQualifier("es")))
      else -> null
    }
  }
  private val nameRule = TestName()
  private val projectRule = ProjectRule()
  private val emulatorRule = FakeEmulatorRule()
  private val fakeAdbSessionRule = FakeAdbSessionRule(projectRule)
  private val appServiceRule = ProjectServiceRule(projectRule, AppLanguageService::class.java, appLanguageServices)

  val project
    get() = projectRule.project

  val testRootDisposable
    get() = projectRule.disposable

  val adb: FakeAdbDeviceServices
    get() = AdbLibService.getSession(project).deviceServices as FakeAdbDeviceServices

  val issuedChangeCommands: List<String>
    get() = adb.shellV2Requests.map { it.command }

  val emulator: FakeEmulator by lazy { createAndStartEmulator() }
  val controller: EmulatorController by lazy { getControllerOf(emulator) }
  val emulatorDeviceSelector: DeviceSelector by lazy { DeviceSelector.fromSerialNumber(emulator.serialNumber) }
  val emulatorConfiguration: EmulatorConfiguration by lazy { EmulatorConfiguration.readAvdDefinition(emulator.avdId, emulator.avdFolder) }

  override fun before() {
    configureAdbShellCommands()
  }

  private fun configureAdbShellCommands() =
    configureUiSettings()

  fun configureUiSettings(
    darkMode: Boolean = false,
    gestureOverlayInstalled: Boolean = true,
    gestureNavigation: Boolean = true,
    applicationId: String = APPLICATION_ID1,
    appLocales: String = "",
    talkBackInstalled: Boolean = false,
    talkBackOn: Boolean = false,
    selectToSpeakOn: Boolean = false,
    fontScale: Int = DEFAULT_FONT_SCALE,
    physicalDensity: Int = DEFAULT_DENSITY,
    overrideDensity: Int = DEFAULT_DENSITY,
    debugLayout: Boolean = false,
    deviceSelector: DeviceSelector = emulatorDeviceSelector
  ) {
    val overrideLine = if (physicalDensity != overrideDensity) "\n      Override density: $overrideDensity" else ""
    val command = POPULATE_COMMAND
    val response = """
      -- Dark Mode --
      Night mode: ${if (darkMode) "yes" else "no"}
      -- Gestures --
      ${if (gestureOverlayInstalled) "[${if (gestureNavigation) "x" else " "}] com.android.internal.systemui.navbar.gestural" else ""}
      -- List Packages --
      ${if (talkBackInstalled) "package:com.google.android.marvin.talkback" else ""}
      -- Accessibility Services --
      ${formatAccessibilityServices(talkBackOn, selectToSpeakOn)}
      -- Accessibility Button Targets --
      ${formatAccessibilityServices(talkBackOn = false, selectToSpeakOn)}
      -- Font Scale --
      ${(fontScale.toFloat() / 100f)}
      -- Density --
      Physical density: $physicalDensity$overrideLine
      -- Debug Layout --
      $debugLayout
      -- Foreground Application --
        mFocusedApp=ActivityRecord{64d5519 u0 $applicationId/com.example.test.MainActivity t8}
    """.trimIndent().replace("\n\n", "\n") // trim spaces and remove all empty lines
    adb.configureShellCommand(deviceSelector, command, response)

    adb.configureShellCommand(deviceSelector, POPULATE_LANGUAGE_COMMAND.format(applicationId), """
      -- App Language --
      Locales for $applicationId for user 0 are [$appLocales]"
    """.trimIndent())

    adb.configureShellCommand(deviceSelector, "settings get secure $ENABLED_ACCESSIBILITY_SERVICES",
                              formatAccessibilityServices(talkBackOn, selectToSpeakOn))
    adb.configureShellCommand(deviceSelector, "settings get secure $ACCESSIBILITY_BUTTON_TARGETS",
                              formatAccessibilityServices(talkBackOn = false, selectToSpeakOn))
  }

  private fun createAndStartEmulator(): FakeEmulator {
    val api = getApiLevelFromTestName()
    val deviceType = getDeviceTypeFromTestName()
    val avdFolder = createAvdFolder(deviceType, api)
    val emulator = emulatorRule.newEmulator(avdFolder)
    emulator.start()
    val emulatorController = getControllerOf(emulator)
    waitForCondition(5.seconds) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    return emulator
  }

  private fun createAvdFolder(deviceType: DeviceType, api: Int = 33): Path {
    return when (deviceType) {
      DeviceType.WEAR -> FakeEmulator.createWatchAvd(emulatorRule.avdRoot, androidVersion = AndroidVersion(api, 0))
      DeviceType.AUTOMOTIVE -> FakeEmulator.createAutomotiveAvd(emulatorRule.avdRoot, androidVersion = AndroidVersion(api, 0))
      DeviceType.XR -> FakeEmulator.createXrAvd(emulatorRule.avdRoot, androidVersion = AndroidVersion(api, 0))
      else -> FakeEmulator.createPhoneAvd(emulatorRule.avdRoot, androidVersion = AndroidVersion(api, 0))
    }
  }

  private fun getControllerOf(emulator: FakeEmulator): EmulatorController {
    val catalog = RunningEmulatorCatalog.getInstance()
    val emulators = runBlocking { catalog.updateNow().await() }
    val emulatorController = emulators.single { emulator.serialNumber == it.emulatorId.serialNumber }
    return emulatorController
  }

  private fun formatAccessibilityServices(talkBackOn: Boolean, selectToSpeakOn: Boolean): String = when {
    talkBackOn && selectToSpeakOn -> "$TALK_BACK_SERVICE_NAME:$SELECT_TO_SPEAK_SERVICE_NAME"
    talkBackOn && !selectToSpeakOn -> TALK_BACK_SERVICE_NAME
    !talkBackOn && selectToSpeakOn -> SELECT_TO_SPEAK_SERVICE_NAME
    else -> "null"
  }

  /**
   * A test can specify the device type by adding it to the end of the test method name before the API level.
   *
   * Examples:
   * - @Test fun testSomethingWear()
   * - @Test fun testAnotherWearApi33()
   */
  private fun getDeviceTypeFromTestName(): DeviceType {
      val name = nameRule.methodName
      val match = API_REGEX.matchEntire(name)
      val methodNameWithoutApiLevel = match?.groupValues[1] ?: name
      return when {
        methodNameWithoutApiLevel.endsWith("Wear") -> DeviceType.WEAR
        methodNameWithoutApiLevel.endsWith("Tv") -> DeviceType.TV
        methodNameWithoutApiLevel.endsWith("Automotive") -> DeviceType.AUTOMOTIVE
        methodNameWithoutApiLevel.endsWith("Desktop") -> DeviceType.DESKTOP
        methodNameWithoutApiLevel.endsWith("Xr") -> DeviceType.XR
        else -> DeviceType.HANDHELD
      }
    }

  /**
   * A test can specify the API level by adding it to the end of the test method name.
   *
   * Examples:
   * - @Test fun testSomethingApi33()
   * - @Test fun testAnotherApi36()
   */
  private fun getApiLevelFromTestName(): Int {
      val match = API_REGEX.matchEntire(nameRule.methodName)
      match?.groupValues[2]?.toIntOrNull()?.let { return it }
      return when (getDeviceTypeFromTestName()) {
        DeviceType.WEAR -> SdkVersionInfo.HIGHEST_KNOWN_API_WEAR
        DeviceType.TV -> SdkVersionInfo.HIGHEST_KNOWN_API_TV
        DeviceType.AUTOMOTIVE -> SdkVersionInfo.HIGHEST_KNOWN_API_AUTO
        DeviceType.DESKTOP -> SdkVersionInfo.HIGHEST_KNOWN_API_DESKTOP
        DeviceType.XR -> SdkVersionInfo.HIGHEST_KNOWN_API_XR
        else -> SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
      }
    }

  override fun apply(base: Statement, description: Description): Statement =
    apply(base, description, nameRule, projectRule, emulatorRule, fakeAdbSessionRule, appServiceRule)

  private fun apply(base: Statement, description: Description, vararg rules: TestRule): Statement {
    var statement = super.apply(base, description)
    rules.forEachReversed { statement = it.apply(statement, description) }
    return statement
  }
}
