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
package com.android.tools.idea.adblib

import com.android.adblib.AdbUsageTracker
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.tools.analytics.UsageTrackerRule
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AndroidAdbUsageTrackerTest {
  @get:Rule val usageTrackerRule = UsageTrackerRule()

  @Test
  fun basicTest() = runBlockingWithTimeout {
    val tracker = AndroidAdbUsageTracker()

    tracker.logUsage(AdbUsageTracker.Event(null, null))

    assertEquals(
      1,
      usageTrackerRule.usages.count {
        it.studioEvent.kind == AndroidStudioEvent.EventKind.ADB_USAGE_EVENT
      },
    )
  }
}
