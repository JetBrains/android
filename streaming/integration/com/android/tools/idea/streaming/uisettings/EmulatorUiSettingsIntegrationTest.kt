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

import com.android.tools.tests.IdeaTestSuiteBase
import com.intellij.testFramework.runInEdtAndGet
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(EmulatorUiSettingsIntegrationTest::class)
class EmulatorUiSettingsIntegrationTestSuite : IdeaTestSuiteBase()

/**
 * Integration test for the UI settings picker for emulators.
 */
internal class EmulatorUiSettingsIntegrationTest {
  @get:Rule
  val rule = UiSettingsIntegrationRule()

  /**
   * The test:
   * - Starts the test application: languages
   * - Opens the UI settings picker
   * - Tests various changed made in the picker UI
   * - Activates the reset button and checks that the UI settings are back to their default values
   */
  @Test
  fun testUiSettings() = runBlocking {
    val tester = UiSettingsTester(rule.project, rule.serialNumber)
    tester.waitForLanguagesAppToRun()
    val panel = runInEdtAndGet { rule.openUiSettings() }
    tester.testSettings(panel)

    // Use the reset button in the panel to reset emulator settings.
    tester.resetSettings(panel)
    tester.checkInitialSettings()
  }
}
