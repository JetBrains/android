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
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.util.computeUserDataIfAbsent
import com.android.utils.throwIfCancellation
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class EnvironmentTracker(private val emulator: EmulatorController) {

  private val _environmentFlow = MutableStateFlow<Environment?>(null)
  val environmentFlow: StateFlow<Environment?> = _environmentFlow.asStateFlow()

  init {
    emulator.createCoroutineScope().launch {
      _environmentFlow.subscriptionCount.collect { count ->
        if (count > 0 && _environmentFlow.value == null) {
          launch(Dispatchers.IO) { fetchEnvironmentSync() }
        }
      }
    }
  }

  var environment: Environment?
    get() {
      if (_environmentFlow.value == null) {
        fetchEnvironmentSync()
      }
      return _environmentFlow.value
    }
    set(value) {
      updateEnvironment(value)
    }

  private fun fetchEnvironmentSync() {
    val latch = CountDownLatch(1)
    val observer =
      object : EmptyStreamObserver<Environment>() {
        override fun onNext(message: Environment) {
          updateEnvironment(message)
          latch.countDown()
        }

        override fun onError(t: Throwable) {
          latch.countDown()
        }
      }
    try {
      emulator.getEnvironment(observer)
    } catch (e: Throwable) {
      e.throwIfCancellation()
      if (emulator.connectionState == EmulatorController.ConnectionState.CONNECTED && !emulator.isShuttingDown) {
        thisLogger().error(e)
      }
    }
    try {
      latch.await(500, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {}
  }

  private fun updateEnvironment(value: Environment?) {
    _environmentFlow.value = value
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
 * Extracts the file path from the emulator [Environment] message. Returns null if the environment mode does not contain a file path, e.g.
 * if it is empty or a camera.
 */
internal fun Environment.getEnvironmentFile(): Path? {
  val mode = environmentMap["scene.mode"] ?: return null
  val pathStr =
    when {
      mode.startsWith("imagefile:") -> mode.removePrefix("imagefile:")
      mode.startsWith("image360:") -> mode.removePrefix("image360:")
      mode.startsWith("mesh3d:") -> mode.removePrefix("mesh3d:")
      mode.startsWith("videofile:") -> mode.removePrefix("videofile:")
      else -> return null
    }
  return try {
    Path.of(pathStr)
  } catch (_: InvalidPathException) {
    null
  }
}
