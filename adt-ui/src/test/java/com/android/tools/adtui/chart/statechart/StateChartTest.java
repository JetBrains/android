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
package com.android.tools.adtui.chart.statechart;

import com.android.tools.adtui.model.*;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.awt.*;
import java.util.EnumMap;
import java.util.HashMap;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

public class StateChartTest {

  @Test
  public void emptyStateChartShouldNotThrowException() {
    StateChartModel<State> model = new StateChartModel<>();
    DataSeries<State> dataSeries = (range) -> ImmutableList.of();

    model.addSeries(new RangedSeries<>(new Range(0, 100), dataSeries));
    EnumMap<State, Color> colors = new EnumMap<>(State.class);
    StateChart<State> stateChart = new StateChart<>(model, colors);
    stateChart.setSize(100, 100);

    Graphics2D fakeGraphics = mock(Graphics2D.class);
    when(fakeGraphics.create()).thenReturn(fakeGraphics);
    stateChart.paint(fakeGraphics);
  }

  @Test
  public void testStateChartTextConverter() {
    StateChartModel<Integer> model = new StateChartModel<>();
    DataSeries<Integer> dataSeries = (range) -> ImmutableList.of(new SeriesData<>(0,1),
                                                                 new SeriesData<>(1000, 2));

    model.addSeries(new RangedSeries<>(new Range(0, 100), dataSeries));
    StateChart<Integer> stateChart = new StateChart<>(model, (state) -> Color.BLACK, (value) -> "123");
    stateChart.setSize(100, 100);
    stateChart.setRenderMode(StateChart.RenderMode.TEXT);

    Graphics2D fakeGraphics = mock(Graphics2D.class);
    when(fakeGraphics.create()).thenReturn(fakeGraphics);
    stateChart.paint(fakeGraphics);
    verify(fakeGraphics, times(1)).drawString(eq("123"), anyFloat(), anyFloat());
  }

  @Test
  public void testStateChartWithDefaultTextConverterUsesToString() {
    StateChartModel<ToStringTestClass> model = new StateChartModel<>();
    DataSeries<ToStringTestClass> dataSeries = (range) -> ImmutableList.of(
      new SeriesData<>(0, new ToStringTestClass("Test")),
      new SeriesData<>(1000, new ToStringTestClass("Test2")));

    model.addSeries(new RangedSeries<>(new Range(0, 100), dataSeries));
    StateChart<ToStringTestClass> stateChart = new StateChart<>(model, (state) -> Color.BLACK);
    stateChart.setSize(100, 100);
    stateChart.setRenderMode(StateChart.RenderMode.TEXT);

    Graphics2D fakeGraphics = mock(Graphics2D.class);
    when(fakeGraphics.create()).thenReturn(fakeGraphics);
    stateChart.paint(fakeGraphics);
    verify(fakeGraphics, times(1)).drawString(eq("Test"), anyFloat(), anyFloat());
  }

  private static class ToStringTestClass {
    private String myString;
    public ToStringTestClass(String string) {
      myString = string;
    }

    @Override
    public String toString() {
      return myString;
    }
  }

  @Test
  public void testLargeValuesGetOverlappedAsOne() {
    StateChartModel<Long> model = new StateChartModel<>();
    DataSeries<Long> dataSeries = (range) -> ImmutableList.of(
      new SeriesData<>(100, 0L),
      new SeriesData<>(101,1L),
      new SeriesData<>(105, 2L)
    );

    HashMap<Long, Color> colorMap = new HashMap<>();
    colorMap.put(0L, Color.RED);
    colorMap.put(1L, Color.GREEN);
    colorMap.put(2L, Color.BLUE);

    model.addSeries(new RangedSeries<>(new Range(0, Long.MAX_VALUE), dataSeries));
    StateChart<Long> stateChart = new StateChart<>(model, colorMap::get);
    stateChart.setSize(100, 100);
    Graphics2D fakeGraphics = mock(Graphics2D.class);
    when(fakeGraphics.create()).thenReturn(fakeGraphics);
    stateChart.paint(fakeGraphics);

    // Because between 0 -> Max Long, values 100 and 101 are so close we end up with a floating point
    // rounding error when creating the rectangle. As such we end up creating two rectangles
    // on top of each ohter and storing them in our map of rectangles to values.
    // This means we do not draw the first value instead we throw it out and draw only
    // the second value.
    // As such we expect 2 rectangles one with the color GREEN, the other with the color BLUE.
    verify(fakeGraphics, times(2)).fill(any());
    verify(fakeGraphics, times(1)).setColor(Color.GREEN);
    verify(fakeGraphics, times(1)).setColor(Color.BLUE);
    verify(fakeGraphics, times(0)).setColor(Color.RED);
  }

  private enum State {
    // We don't actually need any states.
  }
}
