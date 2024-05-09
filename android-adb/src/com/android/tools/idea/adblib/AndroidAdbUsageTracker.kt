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
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.connectedDeviceToDeviceInfo
import com.google.wireless.android.sdk.stats.AdbUsageEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent

class AndroidAdbUsageTracker : AdbUsageTracker {

  override suspend fun logUsage(event: AdbUsageTracker.Event) {
    val androidStudioEvent = AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.ADB_USAGE_EVENT)

    event.device?.let {
      androidStudioEvent.setDeviceInfo(connectedDeviceToDeviceInfo(it))
    }

    event.jdwpProcessPropertiesCollector?.let {
      androidStudioEvent.adbUsageEventBuilder.processPropertiesEventBuilder
        .setSuccess(it.isSuccess)
        .setPreviouslyFailedCount(it.previouslyFailedCount)

      val failureType = it.failureType?.toProtoEnum()
      if (failureType != null) {
        androidStudioEvent.adbUsageEventBuilder.processPropertiesEventBuilder.failureType = failureType
      }
      val previousFailureType = it.previousFailureType?.toProtoEnum()
      if (previousFailureType != null) {
        androidStudioEvent.adbUsageEventBuilder.processPropertiesEventBuilder.previousFailureType = previousFailureType
      }
    }

    UsageTracker.log(androidStudioEvent)
  }

  private fun AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.toProtoEnum(): AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType {
    return when (this) {
      AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.NO_RESPONSE ->
        AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType.NO_RESPONSE

      AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.CLOSED_CHANNEL_EXCEPTION ->
        AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType.CLOSED_CHANNEL_EXCEPTION

      AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.CONNECTION_CLOSED_ERROR ->
        AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType.CONNECTION_CLOSED_ERROR

      AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.IO_EXCEPTION ->
        AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType.IO_EXCEPTION

      AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.OTHER_ERROR ->
        AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType.OTHER_ERROR
    }
  }
}
