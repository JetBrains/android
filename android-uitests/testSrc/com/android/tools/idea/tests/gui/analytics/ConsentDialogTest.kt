/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.analytics

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.consentFileExists
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class ConsentDialogTest {
  @get:Rule
  val guiTest = GuiTestRule().withTimeout(10, TimeUnit.SECONDS)

  // When running from IntelliJ, set -enable.android.analytics.consent.dialog.for.test=true
  // in the run configuration
  @Test
  fun testConsentDialog() {
    // This file is deleted in GuiTestThread.runTest before the test is run.
    assertThat(consentFileExists()).isTrue()
    assertThat(AnalyticsSettings.optedIn).isTrue()
  }
}