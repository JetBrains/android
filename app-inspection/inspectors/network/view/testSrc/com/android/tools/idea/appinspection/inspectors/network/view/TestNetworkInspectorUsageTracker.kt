/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent.NetworkInspectorEvent
import com.intellij.openapi.Disposable

class TestNetworkInspectorUsageTracker : Disposable {
  private val tracker: TestUsageTracker = TestUsageTracker(VirtualTimeScheduler())

  init {
    UsageTracker.setWriterForTest(tracker)
  }

  fun verifyLatestEvent(consumer: (NetworkInspectorEvent) -> Unit) {
    tracker.usages
      .asSequence()
      .map { it.studioEvent }
      .filter { it.kind == AndroidStudioEvent.EventKind.APP_INSPECTION }
      .map { it.appInspectionEvent }
      .filter { it.hasNetworkInspectorEvent() }
      .map { it.networkInspectorEvent }
      .last()
      .let { consumer(it) }
  }

  override fun dispose() {
    tracker.close()
    UsageTracker.cleanAfterTesting()
  }
}
