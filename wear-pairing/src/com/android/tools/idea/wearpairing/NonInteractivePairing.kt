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
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.NullOutputReceiver
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.intellij.openapi.Disposable
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NonInteractivePairing
private constructor(
  private val parentDisposable: Disposable,
  private val phone: IDevice,
  private val watchAvdName: String,
  private val companionAppId: String,
  private val watchNodeId: String,
) : MultiLineReceiver(), Closeable {
  private lateinit var logReaderJob: Job
  private var emulatorActivityStarted = false
  private val _pairingState = MutableStateFlow(PairingState.UNKNOWN)
  val pairingState = _pairingState.asStateFlow()

  companion object {
    private val STATE_LOG_PATTERN = "\\[EMULATOR_PAIRING:([^]]+)]".toRegex()

    fun startPairing(
      parentDisposable: Disposable,
      phone: IDevice,
      watchAvdName: String,
      companionAppId: String,
      watchNodeId: String,
    ): NonInteractivePairing {
      val nonInteractivePairing =
        NonInteractivePairing(parentDisposable, phone, watchAvdName, companionAppId, watchNodeId)
      nonInteractivePairing.startPairing()
      return nonInteractivePairing
    }
  }

  private fun startPairing() {
    logReaderJob =
      AndroidCoroutineScope(parentDisposable).launch(Dispatchers.IO) {
        try {
          phone.executeShellCommand(
            "logcat -T 1",
            this@NonInteractivePairing,
            0,
            TimeUnit.MILLISECONDS,
          )
        } catch (e: Throwable) {
          _pairingState.value = PairingState.INTERNAL_ERROR
          throw e
        }
      }
  }

  override fun isCancelled(): Boolean {
    return !logReaderJob.isActive
  }

  override fun processNewLines(lines: Array<out String>) {
    if (!emulatorActivityStarted) {
      // To make sure we won't miss a log entry, we're going to start the activity after we have
      // started listening for logs.
      emulatorActivityStarted = true
      phone.executeShellCommand(
        "am start -n $companionAppId/.EmulatorActivity --es emulator-name \"$watchAvdName\" --es emulator-id \"$watchNodeId\"",
        NullOutputReceiver(),
      )
    }
    lines.forEach {
      STATE_LOG_PATTERN.find(it)?.groupValues?.get(1)?.let {
        _pairingState.value =
          try {
            PairingState.valueOf(it)
          } catch (ignore: IllegalArgumentException) {
            PairingState.UNKNOWN
          }
      }
    }
  }

  override fun close() {
    logReaderJob.cancel()
  }

  enum class PairingState {
    UNKNOWN,
    INTERNAL_ERROR,

    // Below cases correspond to the states reported by the companion app and should not be renamed.
    STARTED,
    CONSENT,
    PAIRING,
    SUCCESS,
    FAILURE,
    CANCELLED;

    fun hasFinished(): Boolean =
      when (this) {
        INTERNAL_ERROR,
        SUCCESS,
        FAILURE,
        CANCELLED -> true
        UNKNOWN,
        STARTED,
        CONSENT,
        PAIRING -> false
      }
  }
}
