/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.concurrency

import com.intellij.openapi.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

/** Version of Alarm that is scoped and uses suspension. */
class SuspendingAlarm(parentDisposable: Disposable, context: CoroutineContext = EmptyCoroutineContext) {
  private val coroutineScope: CoroutineScope

  init {
    coroutineScope = AndroidCoroutineScope(parentDisposable, context)
  }

  fun cancelAll() { coroutineScope.coroutineContext.cancelChildren() }

  private fun requestInternal(
    initialDelay: Duration, iterations: Long, period: Duration, request: suspend () -> Unit): Job {
    return coroutineScope.launch {
      delay(initialDelay)
      var i = 0L
      while (isActive) {
        request()
        if (++i >= iterations) return@launch
        delay(period)
      }
    }
  }

  /** Runs the [request] after [initialDelay]. */
  fun request(initialDelay: Duration, request: suspend () -> Unit) =
    requestInternal(initialDelay, 1, Duration.ZERO, request)


  /** Runs the [request] every [period] after waiting [initialDelay], up to [iterations] times. */
  fun requestRepeating(
    period: Duration,
    initialDelay: Duration = Duration.ZERO,
    iterations: Long = Long.MAX_VALUE, // > 292 years of nanoseconds, so effectively infinite.
    request: suspend () -> Unit) =
    requestInternal(initialDelay, iterations, period, request)
}
