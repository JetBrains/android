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
package com.android.tools.idea.device.explorer.files.fs

/**
 * Utility class used to ensure that progress report from a long running
 * activity is throttled to the given interval.
 *
 * <p>Usage:
 * <pre>
 *   myThrottledProgress = new ThrottledProgress(100); // 100 millis interval
 *   (...)
 *   void reportProgress() {
 *     if (myThrottledProgress.check()) {
 *       // Code to report progress here...
 *     }
 *   }
 * </pre>
 */
class ThrottledProgress(private val intervalNano: Long) {
  private var lastNotifyNanoTime: Long = 0

  @SuppressWarnings("unused")
  fun getIntervalMillis(): Long {
    return intervalNano / 1000000
  }

  /**
   * Returns `true` if caller should report progress, i.e. if the time elapsed
   * since the last time we returned `true` exceeds [.getIntervalMillis].
   */
  fun check(): Boolean {
    val currentNanoTime = System.nanoTime()
    if (currentNanoTime - lastNotifyNanoTime < intervalNano) {
      return false
    }
    lastNotifyNanoTime = currentNanoTime
    return true
  }
}