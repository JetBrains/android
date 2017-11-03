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

import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class AxisFormatterTest {

  private MockAxisFormatter myFormatter;

  @Before
  public void setUp() throws Exception {
    // maxMajorTicks = 5, maxMajorTicks = 10, switchThreshold = 5;
    myFormatter = new MockAxisFormatter(5, 10, 5);
  }

  @Test
  public void testFormatterSeparator() {
    assertThat(myFormatter.getFormattedString(0, 100, true)).isEqualTo("100mm");
    MockAxisFormatter formatterWithSeparator = new MockAxisFormatter(5, 10, 5, true);
    assertThat(formatterWithSeparator.getFormattedString(0, 100, true)).isEqualTo("100 mm");
  }

  @Test
  public void testGetInterval() throws Exception {
    // maxMinorTicks is 5 so the smallest possible interval is 100 / 5 = 20
    // The axis is in base 10 which has factors: {1, 5, 10}, so the interval
    // will get round up using the factor 5, which gives us 50.
    long interval = myFormatter.getMinorInterval(100);
    assertThat(interval).isEqualTo(50);

    // maxMajorTicks is 10 so the smallest possible interval is 1000 / 10 = 100
    // This essentially matches the base factor 10, so this gives exactly 100.
    interval = myFormatter.getMajorInterval(1000);
    assertThat(interval).isEqualTo(100);
  }

  @Test
  public void testGetMultiplierIndex() throws Exception {
    // value is less than the threshold to get to the next multiplier
    // so we are still in "mm" scale
    int index = myFormatter.getMultiplierIndex(49, 5);
    assertThat(index).isEqualTo(0);
    assertThat(myFormatter.getMultiplier()).isEqualTo(1);
    assertThat(myFormatter.getUnit(index)).isEqualTo("mm");

    // value is greater than or equal to the first multiplier * threshold
    // jumps to "cm"
    index = myFormatter.getMultiplierIndex(50, 5);
    assertThat(index).isEqualTo(1);
    assertThat(myFormatter.getMultiplier()).isEqualTo(10);
    assertThat(myFormatter.getUnit(index)).isEqualTo("cm");

    // value is greater than the second multiplier * threshold
    // jumps to "m"
    index = myFormatter.getMultiplierIndex(5000, 5);
    assertThat(index).isEqualTo(2);
    assertThat(myFormatter.getMultiplier()).isEqualTo(1000);
    assertThat(myFormatter.getUnit(index)).isEqualTo("m");
  }
}