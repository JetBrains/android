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
package com.android.tools.idea.adb.wireless

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.WIFI_PAIRING_EVENT
import com.google.wireless.android.sdk.stats.ApiVersion
import com.google.wireless.android.sdk.stats.WifiPairingEvent
import com.google.wireless.android.sdk.stats.WifiPairingEvent.PairingMethod
import com.google.wireless.android.sdk.stats.WifiPairingEvent.PairingResult.FAILURE
import com.google.wireless.android.sdk.stats.WifiPairingEvent.PairingResult.SUCCESS

/** Tracks usages of Wi-Fi Pairing invocations */
object WifiPairingUsageTracker {
  fun trackSuccess(
    adbVersion: String,
    pairingMethod: PairingMethod,
    deviceApi: String?,
    deviceCodeName: String?,
    elapsedTimeMs: Long,
  ) {
    logEvent(
      WifiPairingEvent.newBuilder()
        .setAdbVersion(adbVersion)
        .setPairingMethod(pairingMethod)
        .setDeviceApiVersion(
          ApiVersion.newBuilder()
            .setApiLevel(deviceApi?.toLongOrNull() ?: -1)
            .setCodename(deviceCodeName ?: "Unknown")
        )
        .setPairingResult(SUCCESS)
        .setElapsedTimeMs(elapsedTimeMs)
    )
  }

  fun trackFailure(
    adbVersion: String,
    pairingMethod: PairingMethod,
    exception: Throwable,
    elapsedTimeMs: Long,
  ) {
    logEvent(
      WifiPairingEvent.newBuilder()
        .setAdbVersion(adbVersion)
        .setPairingMethod(pairingMethod)
        .setPairingResult(FAILURE)
        .addAllExceptionClassNames(getClassNames(exception))
        .setElapsedTimeMs(elapsedTimeMs)
    )
  }

  private fun logEvent(event: WifiPairingEvent.Builder) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder().setKind(WIFI_PAIRING_EVENT).setWifiPairingEvent(event)
    )
  }
}

private fun getClassNames(exception: Throwable): List<String> {
  var e: Throwable? = exception
  return buildList {
    while (e != null) {
      add(e::class.java.simpleName)
      e = e.cause
    }
  }
}
