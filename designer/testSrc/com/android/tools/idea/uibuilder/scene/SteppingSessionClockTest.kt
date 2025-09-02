/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene

import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.nanoseconds
import org.junit.Test

class SteppingSessionClockTest {
  @Test
  fun `time increments by step when not paused`() {
    val step = 100.nanoseconds
    val clock = SteppingSessionClock(step)

    assertEquals(100, clock.getTimeNanos())
    assertEquals(200, clock.getTimeNanos())
    assertEquals(300, clock.getTimeNanos())
  }

  @Test
  fun `time does not increment when paused`() {
    val step = 100.nanoseconds
    val clock = SteppingSessionClock(step)

    assertEquals(100, clock.getTimeNanos())
    clock.pause()
    assertEquals(100, clock.getTimeNanos())
    assertEquals(100, clock.getTimeNanos())
  }

  @Test
  fun `time resumes incrementing after resume`() {
    val step = 100.nanoseconds
    val clock = SteppingSessionClock(step)

    assertEquals(100, clock.getTimeNanos())
    clock.pause()
    assertEquals(100, clock.getTimeNanos())
    clock.resume()
    assertEquals(200, clock.getTimeNanos())
    assertEquals(300, clock.getTimeNanos())
  }
}
