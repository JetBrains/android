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
package com.android.tools.idea.wear.dwf.analytics

import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.wear.wff.WFFVersion.WFFVersion2
import com.android.tools.wear.wff.WFFVersion.WFFVersion3
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind
import com.google.wireless.android.sdk.stats.DeclarativeWatchFaceEvent
import com.google.wireless.android.sdk.stats.DeclarativeWatchFaceEvent.Type.XML_SCHEMA_USED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeclarativeWatchFaceUsageTrackerTest {

  @get:Rule val usageTrackerRule = UsageTrackerRule()

  private val usageTracker = DeclarativeWatchFaceUsageTracker()

  @Test
  fun `tracks xml schema used events`() {
    usageTracker.trackXmlSchemaUsed(WFFVersion3, isFallback = false)
    usageTracker.trackXmlSchemaUsed(WFFVersion2, isFallback = true)

    val dwfEvents =
      usageTrackerRule.usages
        .filter { it.studioEvent.kind == EventKind.DECLARATIVE_WATCH_FACE_EVENT }
        .map { it.studioEvent.declarativeWatchFaceEvent }

    assertThat(dwfEvents)
      .containsExactly(
        DeclarativeWatchFaceEvent.newBuilder()
          .setType(XML_SCHEMA_USED)
          .setWffVersion(
            DeclarativeWatchFaceEvent.WFFVersion.newBuilder().setVersion("3").setIsFallback(false)
          )
          .build(),
        DeclarativeWatchFaceEvent.newBuilder()
          .setType(XML_SCHEMA_USED)
          .setWffVersion(
            DeclarativeWatchFaceEvent.WFFVersion.newBuilder().setVersion("2").setIsFallback(true)
          )
          .build(),
      )
  }
}
