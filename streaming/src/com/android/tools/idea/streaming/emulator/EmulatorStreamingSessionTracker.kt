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
package com.android.tools.idea.streaming.emulator

import com.android.tools.idea.streaming.core.StreamingSessionTracker
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DeviceMirroringSession

/** Mirroring session tracker for a virtual device. */
internal class EmulatorStreamingSessionTracker : StreamingSessionTracker() {

  override val deviceInfoProto: DeviceInfo
    get() = DeviceInfo.newBuilder().setDeviceType(DeviceInfo.DeviceType.LOCAL_EMULATOR).build()
  override val streamingSessionProto: DeviceMirroringSession
    @Synchronized get() {
      return DeviceMirroringSession.newBuilder().apply {
        deviceKind = DeviceMirroringSession.DeviceKind.VIRTUAL
        durationSec = sessionDurationSec
        if (firstFrameArrivalTime != 0L) {
          firstFrameDelayMillis = firstFrameArrivalTime - streamingStartTime
        }
      }.build()
    }

  private var firstFrameArrivalTime: Long = 0L

  @Synchronized fun firstFrameArrived() {
    firstFrameArrivalTime = System.currentTimeMillis()
  }

  @Synchronized override fun reset() {
    firstFrameArrivalTime = 0L
  }
}