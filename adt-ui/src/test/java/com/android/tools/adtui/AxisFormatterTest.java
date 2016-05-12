/*
 * Copyright (C) 2016 The Android Open Source Project
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

public class AxisFormatterTest extends TestCase {

  private MockAxisFormatter domain;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // maxMajorTicks = 5, maxMajorTicks = 10, switchThreshold = 5;
    domain = new MockAxisFormatter(5, 10, 5);
  }

  @Override
  public void tearDown() throws Exception {
    domain = null;
  }

  public void testGetInterval() throws Exception {
    // maxMinorTicks is 5 so the smallest possible interval is 100 / 5 = 20
    // The axis is in base 10 which has factors: {1, 5, 10}, so the interval
    // will get round up using the factor 5, which gives us 50.
    int interval = domain.getMinorInterval(100);
    assertEquals(50, interval);

    // maxMajorTicks is 10 so the smallest possible interval is 1000 / 10 = 100
    // This essentially matches the base factor 10, so this gives exactly 100.
    interval = domain.getMajorInterval(1000);
    assertEquals(100, interval);
  }

  public void testGetMultiplierIndex() throws Exception {
    // value is equal or less than the threshold to get to the next multiplier
    // so we are still in "mm" scale
    int index = domain.getMultiplierIndex(50, 5);
    assertEquals(0, index);
    assertEquals(1, domain.getMultiplier());
    assertEquals("mm", domain.getUnit(index));

    // value is greater than the first multiplier * threshold
    // jumps to "cm"
    index = domain.getMultiplierIndex(51, 5);
    assertEquals(1, index);
    assertEquals(10, domain.getMultiplier());
    assertEquals("cm", domain.getUnit(index));

    // value is greater than the second multiplier * threshold
    // jumps to "m"
    index = domain.getMultiplierIndex(5001, 5);
    assertEquals(2, index);
    assertEquals(1000, domain.getMultiplier());
    assertEquals("m", domain.getUnit(index));
  }
}