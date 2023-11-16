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
package com.android.tools.idea.diagnostics.crash

import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

class ExceptionRateLimiter(
  private val maxEventsPerPeriod: Int = 10,
  private val periodMs: Long = TimeUnit.MINUTES.toMillis(10),
  private val allowancePerSignature: Int = 2,
  private val timeProvider: () -> Long = { TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) }
) {
  data class SignatureStatistics(var count: Int, var deniedSinceLastAllow: Int)

  private val signatureStats = HashMap<String, SignatureStatistics>()

  private var globalCount = 0

  private fun isPowerOfTwo(n: Int) = n and (n - 1) == 0
  private val queue = ArrayDeque<Long>(maxEventsPerPeriod)

  @Synchronized
  fun tryAcquireForSignature(sig: String): Permit {
    val stats = signatureStats.getOrPut(sig) { SignatureStatistics(0, 0) }
    globalCount++
    val count = ++stats.count

    val currentTimeMs = timeProvider()
    val evictedTimeMs = updateTimeQueue(currentTimeMs)

    // Allow first _allowance_ exceptions
    if (count <= allowancePerSignature) {
      return allow(stats)
    }

    // Allow reports that is a power of two per signature
    if (isPowerOfTwo(count)) {
      return allow(stats)
    }

    // Allow if rate limiter permits: maximum of maxEventsPerPeriod per periodMs
    if (evictedTimeMs == null || currentTimeMs - evictedTimeMs > periodMs) {
      return allow(stats)
    }
    return deny(stats)
  }

  /**
   * Added current timestamp to the queue, if queue is full, returns evicted timestamp.
   * Otherwise return <code>null</code>
   */
  private fun updateTimeQueue(currentTimeMs: Long): Long? {
    queue.addLast(currentTimeMs)
    if (queue.size <= maxEventsPerPeriod) {
      return null
    }
    return queue.removeFirst()
  }

  private fun allow(stats: SignatureStatistics): Permit {
    val deniedSinceLastAllow = stats.deniedSinceLastAllow
    stats.deniedSinceLastAllow = 0
    return Permit(PermissionType.ALLOW, deniedSinceLastAllow, globalCount, stats.count)
  }

  private fun deny(stats: SignatureStatistics): Permit {
    val deniedSinceLastAllow = ++stats.deniedSinceLastAllow
    return Permit(PermissionType.DENY, deniedSinceLastAllow, globalCount, stats.count)
  }

  enum class PermissionType {
    DENY,
    ALLOW
  }

  data class Permit(val permissionType: PermissionType,
                    val deniedSinceLastAllow: Int,
                    val globalExceptionCounter: Int,
                    val localExceptionCounter: Int)

}