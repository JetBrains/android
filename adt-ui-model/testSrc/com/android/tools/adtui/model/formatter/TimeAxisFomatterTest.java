/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.model.formatter;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class TimeAxisFomatterTest {
  @Test
  public void fixedPointFormatterTest() {
    TimeAxisFormatter formatter = TimeAxisFormatter.DEFAULT;
    assertEquals("3m40s", formatter.getFixedPointFormattedString(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(220)));
    assertEquals("1m", formatter.getFixedPointFormattedString(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(60)));
    assertEquals("59s", formatter.getFixedPointFormattedString(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(59)));
    assertEquals("0s", formatter.getFixedPointFormattedString(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(0)));
    assertEquals("0s", formatter.getFixedPointFormattedString(TimeUnit.SECONDS.toMicros(1), TimeUnit.MILLISECONDS.toMicros(999)));
  }

  @Test
  public void formatTime() {
    TimeAxisFormatter formatter = TimeAxisFormatter.DEFAULT;

    // less than a millisecond
    assertEquals("00:00:00.000", formatter.getClockFormattedString(100));

    // seconds and milliseconds
    assertEquals("00:00:02.360", formatter.getClockFormattedString(2_360_000));

    // hours, minutes, seconds and milliseconds
    assertEquals("03:25:45.654", formatter.getClockFormattedString(12_345_654_321L));

    // exact seconds
    assertEquals("00:00:01.000", formatter.getClockFormattedString(1_000_000));

    // exact hours
    assertEquals("02:00:00.000", formatter.getClockFormattedString(7_200_000_000L));
  }

  @Test
  public void formatDuration() {
    TimeAxisFormatter formatter = TimeAxisFormatter.DEFAULT;

    assertEquals("1 h", formatter.getFormattedDuration(3_600_000_000L));
    assertEquals("1 h", formatter.getFormattedDuration(4_500_000_000L));
    assertEquals("1 m", formatter.getFormattedDuration(60000000));
    assertEquals("1 s", formatter.getFormattedDuration(1000000));
    assertEquals("1 ms", formatter.getFormattedDuration(1000));
    assertEquals("100 μs", formatter.getFormattedDuration(100));
  }
}
