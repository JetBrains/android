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
package com.android.tools.idea.streaming.device

import com.android.tools.idea.streaming.core.StreamingSessionTracker
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DeviceMirroringSession

/** Mirroring session tracker for a physical device. */
internal class DeviceStreamingSessionTracker(private val deviceConfig: DeviceConfiguration) : StreamingSessionTracker() {

  override val deviceInfoProto: DeviceInfo
    get() = deviceConfig.deviceProperties.deviceInfoProto
  override val streamingSessionProto: DeviceMirroringSession
    get() {
      return DeviceMirroringSession.newBuilder().apply {
        deviceKind = DeviceMirroringSession.DeviceKind.PHYSICAL
        durationSec = sessionDurationSec
        if (agentPushEndTime != 0L) {
          agentPushTimeMillis = agentPushEndTime - agentPushStartTime
          if (firstFrameArrivalTime != 0L) {
            firstFrameDelayMillis = firstFrameArrivalTime - agentPushEndTime
          }
        }
      }.build()
    }

  private var agentPushStartTime: Long = 0L
  private var agentPushEndTime: Long = 0L
  private var firstFrameArrivalTime: Long = 0L

  @Synchronized fun agentPushStarted() {
    agentPushStartTime = System.currentTimeMillis()
  }

  @Synchronized fun agentPushEnded() {
    agentPushEndTime = System.currentTimeMillis()
  }

  @Synchronized fun videoFrameArrived() {
    if (firstFrameArrivalTime == 0L) {
      firstFrameArrivalTime = System.currentTimeMillis()
    }
  }

  @Synchronized override fun reset() {
    agentPushStartTime = 0L
    agentPushEndTime = 0L
    firstFrameArrivalTime = 0L
  }
}