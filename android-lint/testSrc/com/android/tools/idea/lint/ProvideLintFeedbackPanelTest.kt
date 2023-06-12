/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.AnalyticsSettingsData
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.lint.checks.ApiDetector
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.LintAction
import javax.swing.JButton
import org.jetbrains.android.AndroidTestCase

class ProvideLintFeedbackPanelTest : AndroidTestCase() {
  fun testFeedback() {
    val scheduler = VirtualTimeScheduler()
    val analyticsSettings = AnalyticsSettingsData()
    analyticsSettings.optedIn = true
    AnalyticsSettings.setInstanceForTest(analyticsSettings)
    val usageTracker = TestUsageTracker(scheduler)
    UsageTracker.setWriterForTest(usageTracker)

    try {
      // Click first button in the panel (False Positive)
      val panel = ProvideLintFeedbackPanel(project, ApiDetector.UNSUPPORTED.id)
      val component = panel.createCenterPanel()
      for (child in component!!.components) {
        if (child is JButton) {
          child.doClick()
          break
        }
      }

      val usages =
        usageTracker.usages.filter {
          it.studioEvent.kind == AndroidStudioEvent.EventKind.LINT_ACTION
        }
      assertThat(usages).hasSize(1)
      with(usages[0].studioEvent) {
        with(lintAction) {
          assertThat(issueId).isEqualTo("NewApi")
          assertThat(lintFeedback).isEqualTo(LintAction.LintFeedback.FALSE_POSITIVE)
        }
      }
    } finally {
      usageTracker.close()
      UsageTracker.cleanAfterTesting()
    }
  }
}
