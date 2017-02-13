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
package com.android.tools.adtui.chart.linechart;

import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import org.junit.Test;

import java.awt.*;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class LineChartTest {

  @Test
  public void testNoRenderWithEmptyRange() throws Exception {
    // Ensures that if the LineChartModel hasn't had a chance to update and the yRange remains zero - then the LineChart would not render
    // any data.
    LineChartModel model = new LineChartModel();
    Range xRange = new Range(0, 10);
    Range yRange = new Range(0, 0);
    DefaultDataSeries<Long> testSeries = new DefaultDataSeries<>();
    for (int i = 0; i < 11; i++) {
      testSeries.add(i, (long)i);
    }
    RangedContinuousSeries rangedSeries = new RangedContinuousSeries("test", xRange, yRange, testSeries);
    model.add(rangedSeries);

    LineChart chart = new LineChart(model);
    chart.setSize(100, 100);
    Graphics2D fakeGraphics = mock(Graphics2D.class);
    when(fakeGraphics.create()).thenReturn(fakeGraphics);
    doThrow(new AssertionError()).when(fakeGraphics).draw(any(Shape.class));
    chart.paint(fakeGraphics);
  }

  @Test
  public void testRenderConfigNoWithData() throws Exception {
    LineChartModel model = new LineChartModel();
    DefaultDataSeries<Long> emptySeries = new DefaultDataSeries<>();
    DefaultDataSeries<Long> seriesWithData = new DefaultDataSeries<>();
    for (int i = 0; i < 11; i++) {
      seriesWithData.add(i, (long)i);
    }
    RangedContinuousSeries rangedEmptySeries = new RangedContinuousSeries("emptySeries", new Range(), new Range(), emptySeries);
    RangedContinuousSeries rangedSeriesWithData =
      new RangedContinuousSeries("seriesWithData", new Range(0, 10), new Range(0, 0), seriesWithData);
    model.add(rangedEmptySeries);
    model.add(rangedSeriesWithData);

    LineChart chart = new LineChart(model);
    chart.configure(rangedEmptySeries, new LineConfig(Color.BLACK));
    chart.configure(rangedSeriesWithData, new LineConfig(Color.WHITE));
    model.update(TimeUnit.SECONDS.toNanos(1));
    chart.setSize(100, 100);
    Graphics2D fakeGraphics = mock(Graphics2D.class);
    when(fakeGraphics.create()).thenReturn(fakeGraphics);
    chart.paint(fakeGraphics);

    // A line should be drawn for the seriesWithData, but nothing for the emptySeries.
    verify(fakeGraphics, times(1)).draw(any(Shape.class));
  }
}