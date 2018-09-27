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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private class TestTimeProvider {
  var currentTime: Long

  init {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    calendar.set(2010, 6, 1, 14, 30, 0)
    currentTime = calendar.timeInMillis
  }

  fun addTime(value: Long, unit: TimeUnit) {
    currentTime += unit.toMillis(value)
  }
}

class EventsLimiterTest {
  private val timeProvider = TestTimeProvider()

  @Test
  fun tryAcquireSingleShot() {
    val limiter = EventsLimiter(3, TimeUnit.MINUTES.toMillis(1), true,
                                timeProvider::currentTime)
    assertFalse(limiter.tryAcquire())
    timeProvider.addTime(10, TimeUnit.SECONDS)
    assertFalse(limiter.tryAcquire())
    timeProvider.addTime(40, TimeUnit.SECONDS)
    assertTrue(limiter.tryAcquire())
    for (i in 1..20L) {
      timeProvider.addTime(i, TimeUnit.SECONDS)
      assertFalse(limiter.tryAcquire())
    }
  }

  @Test
  fun tryAcquireMultipleShots() {
    val limiter = EventsLimiter(3, TimeUnit.MINUTES.toMillis(1), false,
                                timeProvider::currentTime)
    for (i in 1..10) {
      assertFalse(limiter.tryAcquire())
      timeProvider.addTime(10, TimeUnit.SECONDS)
      assertFalse(limiter.tryAcquire())
      timeProvider.addTime(20, TimeUnit.SECONDS)
      assertTrue(limiter.tryAcquire())
      timeProvider.addTime(5, TimeUnit.SECONDS)
    }
  }

  @Test
  fun tryAcquireTooSlow() {
    val limiter = EventsLimiter(3, TimeUnit.MINUTES.toMillis(1), false,
                                timeProvider::currentTime)
    for (i in 1..10) {
      assertFalse(limiter.tryAcquire())
      timeProvider.addTime(65, TimeUnit.SECONDS)
    }
  }

  @Test
  fun tryAcquireGetsMoreFrequent() {
    val limiter = EventsLimiter(3, TimeUnit.MINUTES.toMillis(1), true,
                                timeProvider::currentTime)
    var delay = 120L
    while (delay > 0) {
      timeProvider.addTime(delay, TimeUnit.SECONDS)
      if (limiter.tryAcquire()) {
        break
      }
      delay -= 5
    }
    // First time it should fire: [...], Event, 30s, Event, 25s, Event
    assertEquals(25, delay)
  }

}