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
import com.android.ddmlib.AdbDelegateUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AdbDelegateUsageEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent

class AdbDelegateUsageTrackerImpl
private constructor(session: AdbSession, private val sourceType: AdbDelegateUsageEvent.SourceType) :
  AdbDelegateUsageTracker {
  private val logger = adbLogger(session)

  override fun logUsage(method: AdbDelegateUsageTracker.Method, isException: Boolean) {
    val usageEventMethod =
      try {
        AdbDelegateUsageEvent.Method.valueOf(method.toString())
      } catch (e: IllegalArgumentException) {
        logger.warn(e, "$method not found in `AdbDelegateUsageEvent.Method` enum")
        return
      }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.ADB_DELEGATE_USAGE_EVENT)
        .setAdbDelegateUsageEvent(
          AdbDelegateUsageEvent.newBuilder()
            .setMethod(usageEventMethod)
            .setSourceType(sourceType)
            .setIsException(isException)
        )
    )
  }

  companion object {

    fun forAndroidDebugBridgeImpl(session: AdbSession): AdbDelegateUsageTracker {
      return AdbDelegateUsageTrackerImpl(session, AdbDelegateUsageEvent.SourceType.ADB_IMPL)
    }

    fun forAdbLibAndroidDebugBridge(session: AdbSession): AdbDelegateUsageTracker {
      return AdbDelegateUsageTrackerImpl(session, AdbDelegateUsageEvent.SourceType.ADBLIB_ADB)
    }
  }
}
