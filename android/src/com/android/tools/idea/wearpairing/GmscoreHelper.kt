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
package com.android.tools.idea.wearpairing

import com.android.ddmlib.IDevice
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val REFRESH_CONNECTION_COMMAND =
  "am broadcast -a com.google.android.gms.wearable.EMULATOR --es operation refresh-emulator-connection"
private const val GMS_PACKAGE = "com.google.android.gms"
private val LOG get() = logger<GmscoreHelper>()

object GmscoreHelper {
  suspend fun IDevice.refreshEmulatorConnection() {
    if (hasPairingFeature(PairingFeature.REFRESH_EMULATOR_CONNECTION, null)) {
      runShellCommand(REFRESH_CONNECTION_COMMAND)
    }
    else {
      restartGmsCore()
    }
  }

  private suspend fun IDevice.killGmsCore() {
    runCatching {
      val uptime = retrieveUpTime()
      // Killing gmsCore during cold boot will hang booting for a while, so skip it
      if (uptime > 120.0) {
        LOG.warn("[$name] Killing Google Play Services")
        executeShellCommand("am force-stop $GMS_PACKAGE")
      }
      else {
        LOG.warn("[$name] Skip killing Google Play Services (uptime = $uptime)")
      }
    }
  }

  private suspend fun IDevice.restartGmsCore() {
    killGmsCore()

    LOG.warn("[$name] Wait for Google Play Services re-start")
    val res = withTimeoutOrNull(30_000) {
      while (loadNodeID().isEmpty()) {
        // Restart in case it doesn't restart automatically
        executeShellCommand("am broadcast -a $GMS_PACKAGE.INITIALIZE")
        delay(1_000)
      }
      true
    }
    when (res) {
      true -> LOG.warn("[$name] Google Play Services started")
      else -> LOG.warn("[$name] Google Play Services never started")
    }
  }
}