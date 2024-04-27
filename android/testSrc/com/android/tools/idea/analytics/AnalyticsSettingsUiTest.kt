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
import com.android.tools.idea.startup.AndroidStudioAnalyticsImpl
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
//import com.intellij.analytics.AndroidStudioAnalytics
import com.intellij.ide.gdpr.ConsentConfigurable
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import javax.swing.JCheckBox

/**
 * Tests the data sharing checkbox in the settings/preferences ui.
 */
@RunsInEdt
class AnalyticsSettingsUiTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @Test
  fun testSettingsUi() {
    if (!IdeInfo.getInstance().isAndroidStudio) return
    //AndroidStudioAnalytics.initialize(AndroidStudioAnalyticsImpl())
    //AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
    //  optedIn = false
    //})

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
}
