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
package com.google.services.firebase.insights

import java.util.concurrent.TimeoutException
import kotlinx.coroutines.delay

internal suspend fun waitForCondition(timeoutMs: Long = 500, condition: () -> Boolean) {
  val waitIntervalMs = 50L
  var index = 0

  while (index * waitIntervalMs < timeoutMs) {
    if (condition()) return
    index++
    delay(waitIntervalMs)
  }
  throw TimeoutException()
}
