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
package com.android.tools.idea.logcat.util

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

/** A delayed task that can be replaced by another task before it is executed. */
internal class ReschedulableTask(private val coroutineScope: CoroutineScope) {
  private val job = AtomicReference<Job?>()

  /**
   * Schedule a task for execution with a given delay. If a task has already been scheduled, try to
   * cancel it before executing the new one.
   */
  fun reschedule(delayMs: Long, task: () -> Unit) {
    job
      .getAndSet(
        coroutineScope.launch {
          delay(delayMs)
          task()
        }
      )
      ?.cancel()
  }

  @TestOnly
  suspend fun await() {
    job.get()?.join()
  }
}
