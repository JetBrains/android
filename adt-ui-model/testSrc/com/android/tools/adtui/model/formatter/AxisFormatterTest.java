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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;

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
  public void testGetMultiplier() throws Exception {
    // value is less than the threshold to get to the next multiplier
    // so we are still in "mm" scale
    BaseAxisFormatter.Multiplier multiplier = myFormatter.getMultiplier(49, 5);
    assertThat(multiplier.index).isEqualTo(0);
    assertThat(multiplier.accumulation).isEqualTo(1);
    assertThat(myFormatter.getUnit(multiplier.index)).isEqualTo("mm");

    // value is greater than or equal to the first multiplier * threshold
    // jumps to "cm"
    multiplier = myFormatter.getMultiplier(50, 5);
    assertThat(multiplier.index).isEqualTo(1);
    assertThat(multiplier.accumulation).isEqualTo(10);
    assertThat(myFormatter.getUnit(multiplier.index)).isEqualTo("cm");

    // value is greater than the second multiplier * threshold
    // jumps to "m"
    multiplier = myFormatter.getMultiplier(5000, 5);
    assertThat(multiplier.index).isEqualTo(2);
    assertThat(multiplier.accumulation).isEqualTo(1000);
    assertThat(myFormatter.getUnit(multiplier.index)).isEqualTo("m");
  }

  @Test
  public void testGetFormattedStringByGlobalRange() {
    // global ranges with value of 0 are computing the correct unit
    // based off MockAxisFormatter MULTIPLIERS of [10, 100, 10] and with base 10
    // mapping of units should be of [0, 9] -> mm, [10, 999] -> cm, [999, inf) -> m
    assertThat(myFormatter.getFormattedString(0, 0, true)).isEqualTo("0mm");
    assertThat(myFormatter.getFormattedString(9, 0, true)).isEqualTo("0mm");
    assertThat(myFormatter.getFormattedString(10, 0, true)).isEqualTo("0cm");
    assertThat(myFormatter.getFormattedString(999, 0, true)).isEqualTo("0cm");
    assertThat(myFormatter.getFormattedString(1000, 0, true)).isEqualTo("0m");
    assertThat(myFormatter.getFormattedString(9999, 0, true)).isEqualTo("0m");

    // values within each globalRange range: [0, 9]
    // based on the globalRange value, the unit should stay mm agnostic of the value
    assertThat(myFormatter.getFormattedString(9, 0, true)).isEqualTo("0mm");
    assertThat(myFormatter.getFormattedString(9, 10, true)).isEqualTo("10mm");
    assertThat(myFormatter.getFormattedString(9, 100, true)).isEqualTo("100mm");
    assertThat(myFormatter.getFormattedString(9, 1000, true)).isEqualTo("1000mm");
    assertThat(myFormatter.getFormattedString(9, 10000, true)).isEqualTo("10000mm");

    // values within each globalRange range: [10, 999]
    // based on the globalRange value, the unit should stay cm agnostic of the value
    assertThat(myFormatter.getFormattedString(999, 0, true)).isEqualTo("0cm");
    assertThat(myFormatter.getFormattedString(999, 10, true)).isEqualTo("1cm");
    assertThat(myFormatter.getFormattedString(999, 100, true)).isEqualTo("10cm");
    assertThat(myFormatter.getFormattedString(999, 1000, true)).isEqualTo("100cm");
    assertThat(myFormatter.getFormattedString(999, 10000, true)).isEqualTo("1000cm");

    // values within each globalRange range: [999, inf)
    // based on the globalRange value, the unit should stay m agnostic of the value
    assertThat(myFormatter.getFormattedString(1000, 0, true)).isEqualTo("0m");
    assertThat(myFormatter.getFormattedString(1000, 10, true)).isEqualTo("0m");
    assertThat(myFormatter.getFormattedString(1000, 100, true)).isEqualTo("0.1m");
    assertThat(myFormatter.getFormattedString(1000, 1000, true)).isEqualTo("1m");
    assertThat(myFormatter.getFormattedString(1000, 10000, true)).isEqualTo("10m");
  }

  @Test
  public void testGetFormattedStringByValue() {
    // Passing in the value as the globalRange as well simulates the multiplier
    // determining the unit and conversion accumulation by value

    // based off MockAxisFormatter MULTIPLIERS of [10, 100, 10] and with base 10

    // the value falls in the range of [0, 9], mapping to mm unit
    // formatted string will evaluate value / 1 converting mm to mm
    assertThat(myFormatter.getFormattedString(9, 9, true)).isEqualTo("9mm");

    // the value falls in the range of [10, 999], mapping to cm unit
    // formatted string will evaluate value / 10 converting mm to cm
    assertThat(myFormatter.getFormattedString(999, 999, true)).isEqualTo("99.9cm");

    // the value falls in the range of [1000, inf), mapping to m unit
    // formatted string will evaluate value / 100 converting mm to m
    assertThat(myFormatter.getFormattedString(1000, 1000, true)).isEqualTo("1m");
  }
}