/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.HighlightingStats
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.IdeInfo
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.EditorFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// This test should ideally live in the same module as HighlightingStats, but it cannot currently
// because it needs AndroidStudioInitializer to be active from intellij.android.core.
@RunWith(JUnit4::class)
class HighlightingStatsTest : BasePlatformTestCase() {

  @Test
  fun testSimple() {
    // Highlighting latency tracking relies on a platform patch only available in Studio.
    Assume.assumeTrue(IdeInfo.getInstance().isAndroidStudio)

    val usageTracker = TestUsageTracker(VirtualTimeScheduler())
    val getHighlightingEvents = {
      HighlightingStats.getInstance().reportHighlightingStats() // Flush pending histogram.
      usageTracker.usages.filter { usage ->
        usage.studioEvent.kind == AndroidStudioEvent.EventKind.EDITOR_HIGHLIGHTING_STATS
      }
    }
    UsageTracker.setWriterForTest(usageTracker)
    try {
      // The latency histogram should be empty initially.
      assertThat(getHighlightingEvents()).isEmpty()

      myFixture.configureByText("Main.kt", "fun main() {}")
      myFixture.checkHighlighting()

      // The latency histogram should have 1 datapoint for the Kotlin file.
      val events = getHighlightingEvents()
      assertThat(events).hasSize(1)
      val stats = events.single().studioEvent.editorHighlightingStats
      assertThat(stats.byFileTypeList).hasSize(1)
      assertThat(stats.byFileTypeList.single().fileType).isEqualTo(EditorFileType.KOTLIN)

      // No new events should be logged now that the latency histogram is empty again.
      assertThat(getHighlightingEvents()).hasSize(1)
    }
    finally {
      UsageTracker.cleanAfterTesting()
    }
  }
}
