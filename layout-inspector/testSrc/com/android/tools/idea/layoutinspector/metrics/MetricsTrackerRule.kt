/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.metrics

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import org.junit.rules.ExternalResource

/**
 * Rule that sets up and tears down a [TestUsageTracker]
 *
 * TODO: move this to a common place
 */
class MetricsTrackerRule(scheduler: VirtualTimeScheduler = VirtualTimeScheduler()) : ExternalResource() {
  val testTracker: TestUsageTracker = TestUsageTracker(scheduler)

  override fun before() {
    UsageTracker.setWriterForTest(testTracker)
  }

  override fun after() {
    testTracker.close()
    UsageTracker.cleanAfterTesting()
  }
}