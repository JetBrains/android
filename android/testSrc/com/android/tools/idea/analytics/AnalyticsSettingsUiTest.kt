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
package com.android.tools.idea.analytics

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.AnalyticsSettingsData
import com.android.tools.idea.IdeInfo
//import com.android.tools.idea.startup.AndroidStudioAnalyticsImpl
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ConsentOptionsProvider
import com.intellij.ide.gdpr.ConsentConfigurable
import com.intellij.openapi.components.service
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import javax.swing.JCheckBox
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Tests the data sharing checkbox in the settings/preferences ui.
 */
@RunsInEdt
class AnalyticsSettingsUiTest {
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(ApplicationRule()).around(EdtRule())

  @Test
  fun testSettingsUi() {
    if (!IdeInfo.getInstance().isAndroidStudio) return

    AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData())
    service<ConsentOptionsProvider>().isSendingUsageStatsAllowed = false

    val configurable = ConsentConfigurable()
    val component = configurable.createComponent()
    val ui = FakeUi(component)
    val checkbox = ui.getComponent<JCheckBox>()
    assertThat(checkbox.isSelected).isFalse()

    checkbox.isSelected = true
    configurable.apply()
    assertThat(AnalyticsSettings.optedIn).isTrue()

    checkbox.isSelected = false
    configurable.apply()
    assertThat(AnalyticsSettings.optedIn).isFalse()
  }

  @Test
  fun testConsistencyWithPlatformConsents() {
    // Background: we reuse the platform "Data Sharing" UI and consent ID, but we also have our own
    // mechanism for storing and querying the data-sharing consent (see AnalyticsSettings.optedIn).
    // That means there are two different places where the data-sharing consent gets stored:
    //
    //   1. ConsentOptions (platform) => $IDE_CONFIG_DIR/consentOptions/accepted
    //   2. AnalyticsSettings (Android plugin) => $USER_HOME/.android/analytics.settings
    //
    // It is important that these two stay in sync, otherwise the Data Sharing UI would not
    // correctly reflect the opt-in status on the Android plugin side.

    // Begin in a state where the platform consent as granted but the Studio consent as not.
    val platformConsentProvider = service<ConsentOptionsProvider>()
    platformConsentProvider.isSendingUsageStatsAllowed = true
    AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData())
    assertThat(AnalyticsSettings.optedIn).isFalse()

    // When AnalyticsSettings gets initialized (normally during app startup), its state should
    // reset to the platform source-of-truth. This behavior was established in 2018 with
    // http://ag/4768793 (Change I34cbf26df). One could imagine syncing the opt-in status
    // in the other direction, but the current behavior works well enough as long as Android Studio
    // is the only surface in which the opt-in status can be changed.

    //AndroidStudioAnalyticsImpl.getInstance().initializeAndroidStudioUsageTrackerAndPublisher()
    assertThat(AnalyticsSettings.optedIn).isTrue()

    // The Android plugin opt-in status should adapt to changes in the platform opt-in status.
    platformConsentProvider.isSendingUsageStatsAllowed = false
    assertThat(AnalyticsSettings.optedIn).isFalse()
  }
}
