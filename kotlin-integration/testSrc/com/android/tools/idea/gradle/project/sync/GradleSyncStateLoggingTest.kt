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
package com.android.tools.idea.gradle.project.sync;

import com.android.testutils.TestUtils
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.wireless.android.sdk.stats.AndroidStudioEvent

class GradleSyncStateLoggingTest : AndroidGradleTestCase() {
  private val tracker = TestUsageTracker(AnalyticsSettings(), VirtualTimeScheduler())

  override fun setUp() {
    super.setUp()
    UsageTracker.setInstanceForTest(tracker)
  }

  fun testKotlinLogging() {
    loadProject(TestProjectPaths.UNIT_TESTING) // Conveniently, this project already has Kotlin setup in place.

    val proto = tracker.usages
      .map { it.studioEvent }
      .single { it.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED }
      .kotlinSupport

    assertEquals(TestUtils.getKotlinVersionForTests(), proto.kotlinSupportVersion)
    assertFalse(proto.hasAndroidKtxVersion())
    // TODO(b/71803185): test for KTX once we have in prebuilts and can sync a project that uses it.
  }
}
