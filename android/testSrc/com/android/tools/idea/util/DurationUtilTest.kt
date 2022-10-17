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
package com.android.tools.idea.util

import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

class DurationUtilTest {
  @Test
  fun testLessThanMinute() {
    assertEquals("0 s 000 ms", Duration.ofNanos(42).toDisplayString())
    assertEquals("0 s 987 ms", Duration.ofMillis(987).toDisplayString())
    assertEquals("59 s 000 ms", Duration.ofSeconds(59).toDisplayString())
  }

  @Test
  fun testMinuteOrMore() {
    assertEquals("1 m 0 s 000 ms", Duration.ofSeconds(60).toDisplayString())
    assertEquals("1 m 0 s 000 ms", Duration.ofMinutes(1).toDisplayString())
    assertEquals("1 m 1 s 000 ms", Duration.ofSeconds(61).toDisplayString())
    assertEquals("3 m 0 s 000 ms", Duration.ofMinutes(3).toDisplayString())
  }
}