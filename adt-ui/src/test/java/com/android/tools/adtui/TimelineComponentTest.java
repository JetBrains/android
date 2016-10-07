/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.adtui;

import junit.framework.TestCase;

import static com.android.tools.adtui.TimelineComponent.formatTime;


public class TimelineComponentTest extends TestCase {

  public void testFormatTime() {
    assertEquals("0s", formatTime(0));
    assertEquals("42s", formatTime(42));
    assertEquals("59s", formatTime(59));
    assertEquals("1m 0s", formatTime(60));
    assertEquals("59m 59s", formatTime(3599));
    assertEquals("1h 0m 0s", formatTime(3600));
    assertEquals("23h 59m 59s", formatTime(86399));
    assertEquals("24h 0m 0s", formatTime(86400));
    assertEquals("277h 46m 40s", formatTime(1000000));
  }
}
