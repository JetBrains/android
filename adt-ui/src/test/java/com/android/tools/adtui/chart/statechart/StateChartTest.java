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

import static org.mockito.Mockito.*;

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

  private enum State {
    // We don't actually need any states.
  }
}
