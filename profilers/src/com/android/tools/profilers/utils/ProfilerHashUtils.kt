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
package com.android.tools.profilers.utils

import com.google.common.hash.Hashing
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Utilities for hashing files or paths in the profilers module. Extracted into a standalone class to decouple offline processing from
 * [com.android.tools.profilers.StudioProfilers].
 */
object ProfilerHashUtils {

  /**
   * Computes a stable, positive hash for a file's absolute path, suitable for use as an ID or cache key. Consistent with the hashing
   * behavior in StudioProfilers for imported files.
   */
  @JvmStatic
  fun hashTracePath(file: File): Long {
    var hash = normalizeHash(Hashing.sha256().hashString(file.absolutePath, StandardCharsets.UTF_8).asLong())

    // Avoid Long.MAX_VALUE which as the end timestamp means ongoing in transport pipeline.
    if (hash == Long.MAX_VALUE || hash == Long.MIN_VALUE || hash == Long.MIN_VALUE + 1) {
      hash /= 2
    }

    // Avoid negative values.
    if (hash < 0) {
      hash = -hash
    }

    // An IEEE 754 64 bit floating point number (which has 52 bits, plus 1 implied) can exactly represent integers with an absolute value
    // of less than or equal to 2^53. The largest hash is 2 ^ 63 - 1, in nanoseconds, which is 2 ^ 60 in microseconds. So we need a range
    // that's larger than 2 ^ (60 - 53) to guarantee the range's start and end are different in microseconds of double type. 1 second is
    // 10 ^ 6 microsecond that can satisfy this condition.
    val rangeNs = TimeUnit.SECONDS.toNanos(1)

    // Make sure (hash + rangeNs) as the end timestamp doesn't overflow.
    if (hash >= Long.MAX_VALUE - rangeNs) {
      hash -= rangeNs
    }

    return hash
  }

  /** This method will eliminate errors due to possible loss of precision during conversion. */
  private fun normalizeHash(originalHash: Long): Long {
    val hashMs: Double = TimeUnit.NANOSECONDS.toMicros(originalHash).toDouble()
    return TimeUnit.MICROSECONDS.toNanos(hashMs.toLong())
  }
}
