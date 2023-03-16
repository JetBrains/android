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
import com.android.adblib.tools.EmulatorConsole
import com.android.adblib.tools.localConsoleAddress
import com.android.adblib.tools.openEmulatorConsole
import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.exists
import com.intellij.util.io.move
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

private const val SERIAL_NUMBER_PREFIX = "emulator-"

/**
 * A [RecordingProvider] that uses [EmulatorConsole].
 */
internal class EmulatorConsoleRecordingProvider(
  disposableParent: Disposable,
  private val serialNumber: String,
  private val localPath: Path,
  private val options: ScreenRecorderOptions,
  private val adbSession: AdbSession,
) : RecordingProvider {

  override val fileExtension = "webm"
  private lateinit var emulatorConsole: EmulatorConsole
  private val recordingHandle = AtomicReference<CompletableDeferred<Unit>>()

  init {
    Disposer.register(disposableParent, this)
  }

  override suspend fun startRecording(): Deferred<Unit> {
    val handle = CompletableDeferred<Unit>()
    Disposer.register(this) {
      recordingHandle.getAndSet(null)?.completeExceptionally(
          RuntimeException(AndroidAdbUiBundle.message("screenrecord.error.disconnected")))
    }

    emulatorConsole = adbSession.openEmulatorConsole(localConsoleAddress(serialNumber.getEmulatorPort()))
    emulatorConsole.startScreenRecording(localPath, *getRecorderOptions(options))

    recordingHandle.set(handle)
    return handle
  }

  override fun stopRecording() {
    val handle = recordingHandle.getAndSet(null) ?: return
    CoroutineScope(Dispatchers.IO).launch {
      try {
        emulatorConsole.stopScreenRecording()
        handle.complete(Unit)
      }
      catch (e: Throwable) {
        handle.completeExceptionally(e)
      }
    }
  }

  override fun cancelRecording() {
    val handle = recordingHandle.getAndSet(null) ?: return
    CoroutineScope(Dispatchers.IO).launch {
      try {
        emulatorConsole.stopScreenRecording()
      }
      finally {
        handle.cancel()
        Files.deleteIfExists(localPath)
      }
    }
  }

  override suspend fun doesRecordingExist(): Boolean = localPath.exists()

  override suspend fun pullRecording(target: Path) {
    withContext(Dispatchers.IO) {
      localPath.move(target)
    }
  }

  override fun dispose() {
    emulatorConsole.close()
  }

  companion object {
    // Note that this is very similar to ShellCommandRecordingProvider.getScreenRecordCommand, but there
    // is no guarantee that the options will be the same in the future so best to keep separate versions.
    @VisibleForTesting
    internal fun getRecorderOptions(options: ScreenRecorderOptions): Array<String> {
      val args = mutableListOf<String>()
      if (options.width > 0 && options.height > 0) {
        args.add("--size")
        args.add("${options.width}x${options.height}")
      }
      if (options.bitrateMbps > 0) {
        args.add("--bit-rate")
        args.add((options.bitrateMbps * 1000000).toString())
      }
      if (options.timeLimitSec != 0) {
        args.add("--time-limit")
        args.add(options.timeLimitSec.toString())
      }
      return args.toTypedArray()
    }
  }
}

private fun String.getEmulatorPort(): Int {
  if (!startsWith(SERIAL_NUMBER_PREFIX)) {
    throw IllegalArgumentException("Not an emulator serial number: $this")
  }
  try {
    return substring(SERIAL_NUMBER_PREFIX.length).toInt()
  }
  catch (e: NumberFormatException) {
    throw IllegalArgumentException("Not an emulator serial number: $this")
  }
}
