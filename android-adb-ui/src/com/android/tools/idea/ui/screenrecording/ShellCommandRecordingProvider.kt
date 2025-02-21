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
import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.android.tools.idea.ui.util.getPhysicalDisplayId
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.delete
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import java.io.EOFException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

private val CMD_TIMEOUT = Duration.ofSeconds(2)

/**
 * A [RecordingProvider] that uses the `screenrecord` shell command.
 */
internal class ShellCommandRecordingProvider(
  disposableParent: Disposable,
  serialNumber: String,
  private val remotePath: String,
  private val options: ScreenRecorderOptions,
  private val adbSession: AdbSession,
) : RecordingProvider {

  override val fileExtension: String = "mp4"
  private val device = DeviceSelector.fromSerialNumber(serialNumber)
  private val recordingJob = AtomicReference<Job>()

  init {
    Disposer.register(disposableParent, this)
  }

  override suspend fun startRecording(): Deferred<Unit> {
    val result = CompletableDeferred<Unit>()
    Disposer.register(this) {
      if (recordingJob.getAndSet(null) != null) {
        result.completeExceptionally(createLostConnectionException())
      }
    }

    val job = createCoroutineScope().launch {
      try {
        val physicalDisplayId = when {
          options.displayId == PRIMARY_DISPLAY_ID -> 0L
          else -> adbSession.getPhysicalDisplayId(device, options.displayId)
        }
        val commandOutput = adbSession.deviceServices.shellAsText(device, getScreenRecordCommand(physicalDisplayId, options, remotePath))
        if (commandOutput.exitCode != 0) {
          throw RuntimeException("Screen recording terminated with exit code ${commandOutput.exitCode}. Try to reduce video resolution.")
        }
        result.complete(Unit)
      }
      catch (_: RecordingStoppedException) {
        result.complete(Unit)
      }
      catch (_: EOFException) {
        result.completeExceptionally(createLostConnectionException())
      }
      catch (e: Throwable) {
        result.completeExceptionally(e)
      }
    }
    recordingJob.set(job)
    return result
  }

  override fun stopRecording() {
    recordingJob.getAndSet(null)?.cancel(RecordingStoppedException())
  }

  override fun cancelRecording() {
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

  companion object {
    @VisibleForTesting
    internal fun getScreenRecordCommand(physicalDisplayId: Long, options: ScreenRecorderOptions, path: String): String {
      val buf = StringBuilder("screenrecord")
      if (physicalDisplayId != 0L) {
        buf.append(" --display-id ").append(physicalDisplayId)
      }
      if (options.width > 0 && options.height > 0) {
        buf.append(" --size ").append(options.width).append('x').append(options.height)
      }
      if (options.bitrateMbps > 0) {
        buf.append(" --bit-rate ").append(options.bitrateMbps * 1000000)
      }
      if (options.timeLimitSec != 0) {
        buf.append(" --time-limit ").append(options.timeLimitSec)
      }
      buf.append(' ').append(path)
      return buf.toString()
    }
  }

  private class RecordingStoppedException : CancellationException()
}
