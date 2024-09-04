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

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import kotlin.time.Duration

/**
 * Utility method that waits for the [StateFlow] to turn the [condition] to true or fails with the given message after the [timeout] has
 * expired.
 */
suspend fun <T> StateFlow<T>.awaitStatus(message: String? = null, timeout: Duration, condition: (T) -> Boolean) {
  try {
    withTimeout(timeout) {
      filter { condition(it) }
        .first()
    }
  } catch (e: TimeoutCancellationException) {
    Assert.fail("$message\nStatus: $value")
  }
}
/**
 * Waits for an element in the [Flow].
 */
suspend fun <T> Flow<T>.next(): T = take(1).single()
