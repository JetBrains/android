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
package com.android.tools.profilers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RelativeTimeConverterTest {
  @Test
  public void testIdentityConversion() {
    RelativeTimeConverter converter = new RelativeTimeConverter(0);
    assertEquals(1, converter.convertToRelativeTime(1));
    assertEquals(-1, converter.convertToAbsoluteTime(-1));
  }

  @Test
  public void testWithOffset() {
    final long OFFSET = 5;
    RelativeTimeConverter converter = new RelativeTimeConverter(OFFSET);
    assertEquals(OFFSET, converter.getDeviceStartTimeNs());
    assertEquals(0, converter.convertToRelativeTime(5));
    assertEquals(6, converter.convertToRelativeTime(11));
    assertEquals(-6, converter.convertToRelativeTime(-1));
    assertEquals(OFFSET, converter.convertToAbsoluteTime(0));
    assertEquals(-10, converter.convertToAbsoluteTime(-15));
    assertEquals(105, converter.convertToAbsoluteTime(100));
  }
}
