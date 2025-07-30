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
package com.android.tools.idea.play

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gservices.DevServiceDeprecationInfoBuilder
import com.android.tools.idea.gservices.DevServicesDeprecationStatus
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo
import com.google.wireless.android.sdk.stats.PlayPolicyInsightsUsageEvent
import com.google.wireless.android.sdk.stats.PlayPolicyInsightsUsageEvent.PlayPolicyInsightsUsageEventType
import com.google.wireless.android.sdk.stats.PlayPolicyInsightsUsageEventKt.serviceDeprecationInfo
import com.google.wireless.android.sdk.stats.androidStudioEvent
import com.google.wireless.android.sdk.stats.playPolicyInsightsUsageEvent

fun PlayPolicyInsightsUsageEvent.track() {
  UsageTracker.log(
    androidStudioEvent {
      kind = AndroidStudioEvent.EventKind.PLAY_POLICY_INSIGHTS_USAGE_EVENT
      playPolicyInsightsUsageEvent = this@track
    }
  )
}

fun trackDeprecation(
  status: DevServicesDeprecationStatus,
  userNotified: Boolean? = null,
  userClickedMoreInfo: Boolean? = null,
  userClickedUpdate: Boolean? = null,
) {
  playPolicyInsightsUsageEvent {
      type = PlayPolicyInsightsUsageEventType.SERVICE_DEPRECATION
      serviceDeprecationInfo = serviceDeprecationInfo {
        devServiceDeprecationInfo =
          DevServiceDeprecationInfoBuilder(
            status,
            DevServiceDeprecationInfo.DeliveryType.PANEL,
            userNotified,
            userClickedMoreInfo,
            userClickedUpdate,
          )
      }
    }
    .track()
}
