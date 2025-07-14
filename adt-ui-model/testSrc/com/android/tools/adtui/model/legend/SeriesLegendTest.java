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
package com.android.tools.adtui.model.legend;

import static org.junit.Assert.assertEquals;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Interpolatable;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.formatter.MockAxisFormatter;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class SeriesLegendTest {

  @Test
  public void legendNameIsProperlyGot() {
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), new DefaultDataSeries<>());
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), new Range(0, 100));
    assertEquals("test", legend.getName());
  }

  @Test
  public void legendValueIsNaGivenNoData() {
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), new DefaultDataSeries<>());
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), new Range(0, 100));
    assertEquals(SeriesLegend.UNAVAILABLE_MESSAGE, legend.getValue());
  }

  @Test
  public void legendValueIsNaGivenFailedPredicate() {
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), new DefaultDataSeries<>());
    SeriesLegend legend = new SeriesLegend(series,
                                           new MockAxisFormatter(1, 1, 1),
                                           new Range(0, 100),
                                           series.getName(),
                                           Interpolatable.SegmentInterpolator,
                                           range -> false);
    assertEquals(SeriesLegend.UNAVAILABLE_MESSAGE, legend.getValue());
  }

  @Test
  public void legendValueGotFromMatchedTime() {
    TestDataSeries dataSeries = new TestDataSeries(ImmutableList.of(
      new SeriesData<>(100, 123L)));
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), dataSeries);
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), new Range(0, 100));
    assertEquals("12.3cm", legend.getValue());
  }

  @Test
  public void cachedLegendShowsPreviousValue() {
    TestDataSeries dataSeries = new TestDataSeries(ImmutableList.of(new SeriesData<>(100, 123L)));
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 200), new Range(0, 200), dataSeries);
    Range r = new Range(0, 100);
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), r);
    legend.setCachingLastValue(true);
    assertEquals("12.3cm", legend.getValue());
    r.setMax(200);
    dataSeries.clear();
    assertEquals("12.3cm", legend.getValue());
  }

  @Test
  public void uncachedLegendShowsUnavailable() {
    TestDataSeries dataSeries = new TestDataSeries(ImmutableList.of(new SeriesData<>(100, 123L)));
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 200), new Range(0, 200), dataSeries);
    Range r = new Range(0, 100);
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), r);
    assertEquals("12.3cm", legend.getValue());
    r.setMax(200);
    dataSeries.clear();
    Truth.assertThat(legend.isCachingLastValue()).isFalse();
    assertEquals(SeriesLegend.UNAVAILABLE_MESSAGE, legend.getValue());
  }

  @Test
  public void legendValueIsClosestRightGivenNoPreviousData() {
    TestDataSeries dataSeries = new TestDataSeries(ImmutableList.of(
      new SeriesData<>(100, 333L), new SeriesData<>(110, 444L)));
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), dataSeries);
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), new Range(0, 99));
    assertEquals("33.3cm", legend.getValue());
  }

  @Test
  public void legendValueIsClosestLeftGivenNoLaterData() {
    TestDataSeries dataSeries = new TestDataSeries(ImmutableList.of(
      new SeriesData<>(0L, 111L), new SeriesData<>(10, 222L)));
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), dataSeries);
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), new Range(1, 100));
    assertEquals("22.2cm", legend.getValue());
  }

  @Test
  public void legendValueIsInterpolatedWhenNoExactMatch() {
    TestDataSeries dataSeries = new TestDataSeries(ImmutableList.of(
      new SeriesData<>(0L, 150L), new SeriesData<>(2, 50L)));
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), dataSeries);
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), new Range(1, 1));
    assertEquals("10cm", legend.getValue());
  }

  @Test
  public void legendValueForSteppedLine() {
    TestDataSeries dataSeries = new TestDataSeries(ImmutableList.of(
      new SeriesData<>(0L, 150L), new SeriesData<>(2, 50L)));
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), dataSeries);
    SeriesLegend legend = new SeriesLegend(series, new SingleUnitAxisFormatter(1, 5, 1, ""), new Range(1, 1),
                                           Interpolatable.SteppedLineInterpolator);
    assertEquals("150", legend.getValue());
  }

  @Test
  public void legendValueForRoundedSegmentInterpolation() {
    TestDataSeries dataSeries = new TestDataSeries(ImmutableList.of(
      new SeriesData<>(0L, 150L), new SeriesData<>(3, 50L)));
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), dataSeries);
    SeriesLegend legend = new SeriesLegend(series, new SingleUnitAxisFormatter(1, 5, 1, ""), new Range(1, 1),
                                           Interpolatable.RoundedSegmentInterpolator);
    assertEquals("117", legend.getValue());
  }

  private static class TestDataSeries implements DataSeries<Long> {

    @NotNull List<SeriesData<Long>> myDataList;

    public TestDataSeries(@NotNull List<SeriesData<Long>> data) {
      myDataList = data;
    }

    @Override
    public List<SeriesData<Long>> getDataForRange(Range range) {
      return myDataList;
    }

    public void clear() {
      myDataList = Collections.emptyList();
    }
  }
}
