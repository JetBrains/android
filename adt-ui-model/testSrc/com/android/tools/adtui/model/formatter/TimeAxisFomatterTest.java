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
}
