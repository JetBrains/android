/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.stats

import java.util.*
import java.util.concurrent.TimeUnit

class EventsLimiter(private val eventCount : Int,
                    private val periodMs : Long,
                    private val manualReset : Boolean,
                    private val timeProvider : () -> Long = { TimeUnit.NANOSECONDS.toMillis(System.nanoTime())})
{
  private val LOCK = Object()

  private var queue = ArrayDeque<Long>(eventCount)
  private var disabled = false

  fun tryAcquire() : Boolean {
    synchronized(LOCK) {
      if (disabled) {
        return false
      }
      val currentTimeMs = timeProvider()
      queue.addLast(currentTimeMs)
      if (queue.size < eventCount) {
        return false
      }
      val timeMs = queue.removeFirst()
      val durationMs = currentTimeMs - timeMs
      if (durationMs < 0) {
        // Likely, system clock has been manually changed. Ignore.
        return false
      }
      if (currentTimeMs - timeMs >= periodMs) {
        return false
      }
      if (manualReset) {
        disabled = true
      }
      queue.clear()
      return true
    }
  }

  fun disable(): Boolean {
    synchronized(LOCK) {
      val result = disabled
      disabled = true
      queue.clear()
      return result
    }
  }

  fun reset() {
    synchronized(LOCK) {
      disabled = false
      queue.clear()
    }
  }
}
