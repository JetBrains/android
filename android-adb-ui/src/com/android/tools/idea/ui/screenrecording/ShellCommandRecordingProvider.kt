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

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
  private val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
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

    val job = AndroidCoroutineScope(this).launch {
      try {
        val commandOutput = adbSession.deviceServices.shellAsText(deviceSelector, getScreenRecordCommand(options, remotePath))
        if (commandOutput.exitCode != 0) {
          throw RuntimeException("Screen recording terminated with exit code ${commandOutput.exitCode}")
        }
        result.complete(Unit)
      }
      catch (e: RecordingStoppedException) {
        result.complete(Unit)
      }
      catch (e: EOFException) {
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
      adbSession.deviceServices.shellAsText(deviceSelector, "rm $remotePath", commandTimeout = CMD_TIMEOUT)
    }
  }

  override suspend fun doesRecordingExist(): Boolean {
    //TODO: Check for `stderr` and `exitCode` to report errors
    val out = adbSession.deviceServices.shellAsText(deviceSelector, "ls $remotePath", commandTimeout = CMD_TIMEOUT).stdout
    return out.trim() == remotePath
  }

  override suspend fun pullRecording(target: Path) {
    adbSession.deviceServices.sync(deviceSelector).use { sync ->
      try {
        adbSession.channelFactory.createNewFile(target).use {
          sync.recv(remotePath, it, progress = null)
        }
      }
      finally {
        adbSession.deviceServices.shellAsText(deviceSelector, "rm $remotePath", commandTimeout = CMD_TIMEOUT)
      }
    }
  }

  override fun dispose() {
  }

  private fun createLostConnectionException() = RuntimeException(AndroidAdbUiBundle.message("screenrecord.error.disconnected"))

  companion object {
    // Note that this is very similar to EmulatorConsoleRecordingProvider.getRecorderOptions, but there
    // is no guarantee that the args will be the same in the future so best to keep separate versions.
    @VisibleForTesting
    internal fun getScreenRecordCommand(options: ScreenRecorderOptions, path: String): String {
      val sb = StringBuilder()
      sb.append("screenrecord")
      if (options.width > 0 && options.height > 0) {
        sb.append(" --size ")
        sb.append(options.width)
        sb.append('x')
        sb.append(options.height)
      }
      if (options.bitrateMbps > 0) {
        sb.append(" --bit-rate ")
        sb.append(options.bitrateMbps * 1000000)
      }
      if (options.timeLimitSec != 0) {
        sb.append(" --time-limit ")
        sb.append(options.timeLimitSec)
      }
      sb.append(' ')
      sb.append(path)
      return sb.toString()
    }
  }

  private class RecordingStoppedException : CancellationException()
}
