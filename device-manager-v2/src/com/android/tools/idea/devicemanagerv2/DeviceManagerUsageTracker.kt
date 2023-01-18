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
package com.android.tools.idea.devicemanagerv2

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceManagerEvent

internal object DeviceManagerUsageTracker {
  fun logEvent(event: DeviceManagerEvent) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.DEVICE_MANAGER)
        .setDeviceManagerEvent(event)
    )
  }
}
