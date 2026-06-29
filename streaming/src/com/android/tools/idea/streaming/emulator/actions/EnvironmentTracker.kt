/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator.actions

import com.android.emulator.control.Environment
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.util.computeUserDataIfAbsent
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.util.Key
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class EnvironmentTracker(private val emulator: EmulatorController) {

  var environment: Environment?
    get() {
      _environment?.let {
        return it
      }
      val latch = CountDownLatch(1)
      val observer =
        object : EmptyStreamObserver<Environment>() {
          override fun onNext(message: Environment) {
            if (!message.environmentMap.isEmpty()) { // TODO: Don't ignore empty environment when b/528439369 is fixed.
              _environment = message
            }
            latch.countDown()
          }

          override fun onError(t: Throwable) {
            latch.countDown()
          }
        }
      emulator.getEnvironment(observer)
      try {
        latch.await(500, TimeUnit.MILLISECONDS)
      } catch (_: InterruptedException) {}
      return _environment
    }
    set(value) {
      _environment = value
    }

  @Volatile
  private var _environment: Environment? = null
    set(value) {
      field = value
      ActivityTracker.getInstance().inc()
    }

  companion object {
    private val KEY = Key<EnvironmentTracker>(EnvironmentTracker::class.java.name)

    fun forEmulator(emulator: EmulatorController): EnvironmentTracker? {
      if (emulator.connectionState != EmulatorController.ConnectionState.CONNECTED) return null
      return emulator.computeUserDataIfAbsent(KEY) { EnvironmentTracker(emulator) }
    }
  }
}

/**
 * Extracts the image file path from the emulator [Environment] message. Returns null if the environment mode is not an image-based mode,
 * e.g. if it is empty or a camera.
 */
internal fun Environment.getImagePath(): Path? {
  val mode = environmentMap["scene.mode"] ?: return null
  val pathStr =
    when {
      mode.startsWith("imagefile:") -> mode.removePrefix("imagefile:")
      mode.startsWith("image360:") -> mode.removePrefix("image360:")
      else -> return null
    }
  return try {
    Path.of(pathStr)
  } catch (_: InvalidPathException) {
    null
  }
}
