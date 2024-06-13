/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.util

import com.intellij.openapi.Disposable
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * A utility that allows to schedule regular calls of [onTick] callback with a period of [period].
 * The calls do not start until [start] is called. [stop] will stop the calls. Call [start] again to
 * restart the calls without recreating the object.
 */
class ControllableTicker(
  private val onTick: () -> Unit,
  private val period: Duration,
  private val executorProvider: () -> ScheduledExecutorService = {
    Executors.newScheduledThreadPool(1)
  },
) : Disposable {
  private var executor: ScheduledExecutorService? = null

  @Synchronized
  fun start() {
    if (executor != null) {
      return
    }
    executor = executorProvider()
    executor?.scheduleAtFixedRate(onTick, 0, period.toNanos(), TimeUnit.NANOSECONDS)
  }

  @Synchronized
  fun stop() {
    executor?.let {
      it.shutdown()
      try {
        it.awaitTermination(period.toNanos(), TimeUnit.NANOSECONDS)
      } catch (e: InterruptedException) {}
    }
    executor = null
  }

  override fun dispose() {
    stop()
  }
}
