/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.model;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class RangedSeriesTest {

  @Test
  public void testGetSeriesUsingCache() {
    Range queryRange = new Range(0, 100);
    DefaultDataSeries<Long> testSeries = new DefaultDataSeries<>();
    for (int i = 0; i < 50; i++) {
      testSeries.add(i, (long)i);
    }
    RangedSeries<Long> rangedSeries = new RangedSeries<>(queryRange, testSeries);

    // Check that a first time a query is made, we always return the data from the underlying data series.
    assertThat(rangedSeries.getSeries()).hasSize(50);

    for (int i = 50; i < 100; i++) {
      testSeries.add(i, (long)i);
    }
    // Adding data along without updating the query range should keep utilizing the cached data.
    assertThat(rangedSeries.getSeries()).hasSize(50);

    // Updating the query range would fetch from the underlying data series again.
    queryRange.setMax(200);
    assertThat(rangedSeries.getSeries()).hasSize(100);
  }

  @Test
  public void testGetSeriesIgnoreCache() {
    Range queryRange = new Range(0, 100);
    DefaultDataSeries<Long> testSeries = new DefaultDataSeries<>();
    for (int i = 0; i < 50; i++) {
      testSeries.add(i, (long)i);
    }
    RangedSeries<Long> rangedSeries = new RangedSeries<>(queryRange, testSeries);

    // Check that a first time a query is made, we always return the data from the underlying data series.
    assertThat(rangedSeries.getSeries()).hasSize(50);

    // Always pull from the underlying data series and ignore the cache if the range is set to Long.MAX_VALUE.
    queryRange.setMax(Long.MAX_VALUE);
    for (int i = 50; i < 75; i++) {
      testSeries.add(i, (long)i);
    }
    assertThat(rangedSeries.getSeries()).hasSize(75);
    for (int i = 75; i < 100; i++) {
      testSeries.add(i, (long)i);
    }
    assertThat(rangedSeries.getSeries()).hasSize(100);
  }

  @Test
  public void testGetSeriesIgnoreCacheWhenInvalidated() {
    Range queryRange = new Range(0, 100);
    DefaultDataSeries<Long> testSeries = new DefaultDataSeries<>();
    for (int i = 0; i < 50; i++) {
      testSeries.add(i, (long)i);
    }
    RangedSeries<Long> rangedSeries = new RangedSeries<>(queryRange, testSeries);

    // Check that a first time a query is made, we always return the data from the underlying data series.
    assertThat(rangedSeries.getSeries()).hasSize(50);

    for (int i = 50; i < 100; i++) {
      testSeries.add(i, (long)i);
    }
    // Adding data along without updating the query range should keep utilizing the cached data.
    assertThat(rangedSeries.getSeries()).hasSize(50);

    // Invalidating the series would fetch from the underlying data series again.
    rangedSeries.invalidate();
    assertThat(rangedSeries.getSeries()).hasSize(100);
  }

}