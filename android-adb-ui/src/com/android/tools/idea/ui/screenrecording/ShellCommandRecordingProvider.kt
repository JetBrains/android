/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.ui.screenrecording

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.adblib.tools.ScreenRecordOptions
import com.android.adblib.tools.screenRecord
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.android.tools.idea.ui.util.getPhysicalDisplayId
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.delete
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

private val CMD_TIMEOUT = Duration.ofSeconds(2)

/** A [RecordingProvider] that uses the `screenrecord` shell command. */
internal class ShellCommandRecordingProvider(
  disposableParent: Disposable,
  serialNumber: String,
  private val remotePath: String,
  private val options: ScreenRecorderOptions,
  private val adbSession: AdbSession,
) : RecordingProvider {

  override val fileExtension: String = "mp4"
  private val device = DeviceSelector.fromSerialNumber(serialNumber)

  /** The coroutine that runs the [AdbDeviceServices.screenRecord] function. */
  private val recordingJob = AtomicReference<Job>()

  /** The [Deferred] used to stop or cancel the [AdbDeviceServices.screenRecord] call. */
  private val stopRecordingSignal = CompletableDeferred<Unit>()

  init {
    Disposer.register(disposableParent, this)
  }

  override suspend fun startRecording(): Deferred<Unit> {
    val result = CompletableDeferred<Unit>()
    Disposer.register(this) {
        result.completeExceptionally(createLostConnectionException())
    }

    val job = createCoroutineScope().launch {
      runCatching {
        val physicalDisplayId = when {
          options.displayId == PRIMARY_DISPLAY_ID -> 0L
          else -> adbSession.getPhysicalDisplayId(device, options.displayId)
        }
        val adbOptions = ScreenRecordOptions(
          physicalDisplayId = if (physicalDisplayId != 0L) physicalDisplayId else null,
          videoSize = if (options.width != 0 && options.height != 0) Dimension(options.width, options.height) else null,
          bitRateMbps = if (options.bitrateMbps != 0) options.bitrateMbps else null,
          timeLimitSec = if (options.timeLimitSec != 0) options.timeLimitSec else null
        )
        adbSession.deviceServices.screenRecord(device, remotePath, adbOptions, stopRecordingSignal)
      }.onSuccess {
        // The screen recording to `remotePath` was successful
        result.complete(Unit)
      }.onFailure { t ->
        // The screen recording failed for some reason, notify the caller
        result.completeExceptionally(t)
      }
    }
    recordingJob.set(job)
    return result
  }

  override fun stopRecording() {
    // Signal the screen recording to terminate, which will signal
    // the `startRecording` caller through the returned `Deferred`.
    stopRecordingSignal.complete(Unit)
  }

  override fun cancelRecording() {
    // Cancel the recording, which will signal the `startRecording` caller
    // the recording has been cancelled (through the returned `Deferred`)
    stopRecordingSignal.cancel()

    // Cancel the recording job, and delete temp. file
    val job = recordingJob.getAndSet(null) ?: return
    job.cancel()
    CoroutineScope(Dispatchers.IO).launch {
      adbSession.deviceServices.shellAsText(device, "rm $remotePath", commandTimeout = CMD_TIMEOUT)
    }
  }

  override suspend fun doesRecordingExist(): Boolean {
    //TODO: Check for `stderr` and `exitCode` to report errors
    val out = adbSession.deviceServices.shellAsText(device, "ls $remotePath", commandTimeout = CMD_TIMEOUT).stdout
    return out.trim() == remotePath
  }

  override suspend fun pullRecording(target: Path) {
    target.delete()
    adbSession.deviceServices.sync(device).use { sync ->
      try {
        adbSession.channelFactory.createNewFile(target).use {
          sync.recv(remotePath, it, progress = null)
        }
      }
      finally {
        adbSession.deviceServices.shellAsText(device, "rm $remotePath", commandTimeout = CMD_TIMEOUT)
      }
    }
  }

  override fun dispose() {
  }

  private fun createLostConnectionException() = RuntimeException(AndroidAdbUiBundle.message("screenrecord.error.disconnected"))
}
