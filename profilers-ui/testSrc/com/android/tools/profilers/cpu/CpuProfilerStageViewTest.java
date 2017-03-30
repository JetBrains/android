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
package com.android.tools.profilers.cpu;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CpuProfilerStageViewTest {

  @Test
  public void formatTime() {
    // less than a millisecond
    assertEquals("00:00:00.000", CpuProfilerStageView.formatTime(100));

    // seconds and milliseconds
    assertEquals("00:00:02.360", CpuProfilerStageView.formatTime(2_360_000));

    // hours, minutes, seconds and milliseconds
    assertEquals("03:25:45.654", CpuProfilerStageView.formatTime(12_345_654_321L));

    // exact seconds
    assertEquals("00:00:01.000", CpuProfilerStageView.formatTime(1_000_000));

    // exact hours
    assertEquals("02:00:00.000", CpuProfilerStageView.formatTime(7_200_000_000L));
  }
}
