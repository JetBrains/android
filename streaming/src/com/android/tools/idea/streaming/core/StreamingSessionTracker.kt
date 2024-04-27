/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DeviceMirroringSession

/** Accumulates data related to a mirroring session and logs a [AndroidStudioEvent.EventKind.DEVICE_MIRRORING_SESSION] event. */
internal abstract class StreamingSessionTracker {

  protected abstract val deviceInfoProto: DeviceInfo
  protected abstract val streamingSessionProto: DeviceMirroringSession

  protected val sessionDurationSec: Long
    @Synchronized get() {
      check(streamingStartTime != 0L)
      return (System.currentTimeMillis() - streamingStartTime) / 1000
    }
  protected var streamingStartTime: Long = 0L

  /** Records the start of a device streaming session. Has no effect if streaming is already active. */
  @Synchronized fun streamingStarted() {
    if (streamingStartTime == 0L) {
      reset()
      streamingStartTime = System.currentTimeMillis()
    }
  }

  /** Records the end of the current device streaming session. Has no effect if streaming is not active. */
  @Synchronized fun streamingEnded() {
    if (streamingStartTime == 0L) {
      return
    }

    val studioEvent = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DEVICE_MIRRORING_SESSION)
      .setDeviceInfo(deviceInfoProto)
      .setDeviceMirroringSession(streamingSessionProto)

    UsageTracker.log(studioEvent)
    streamingStartTime = 0
    reset()
  }

  /** Resets the metrics collector, so it can be reused for the next streaming session. */
  protected abstract fun reset()
}