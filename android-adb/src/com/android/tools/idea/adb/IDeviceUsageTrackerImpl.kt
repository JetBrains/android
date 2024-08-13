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
package com.android.tools.idea.adb

import com.android.adblib.AdbSession
import com.android.adblib.adbLogger
import com.android.ddmlib.IDeviceUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.IDeviceUsageEvent

class IDeviceUsageTrackerImpl
private constructor(session: AdbSession, private val sourceType: IDeviceUsageEvent.SourceType) :
  IDeviceUsageTracker {

  private val logger = adbLogger(session)

  override fun logUsage(method: IDeviceUsageTracker.Method, isException: Boolean) {
    val usageEventMethod =
      try {
        IDeviceUsageEvent.Method.valueOf(method.toString())
      } catch (e: IllegalArgumentException) {
        logger.warn(e, "$method not found in `IDeviceUsageEvent.Method` enum")
        return
      }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.I_DEVICE_USAGE_EVENT)
        .setIDeviceUsageEvent(
          IDeviceUsageEvent.newBuilder()
            .setMethod(usageEventMethod)
            .setSourceType(sourceType)
            .setIsException(isException)
        )
    )
  }

  companion object {

    fun forDeviceImpl(session: AdbSession): IDeviceUsageTracker {
      return IDeviceUsageTrackerImpl(session, IDeviceUsageEvent.SourceType.DEVICE_IMPL)
    }

    fun forAdblibIDeviceWrapper(session: AdbSession): IDeviceUsageTracker {
      return IDeviceUsageTrackerImpl(session, IDeviceUsageEvent.SourceType.ADBLIB_I_DEVICE_WRAPPER)
    }
  }
}
