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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.awt.*;
import java.util.EnumMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

  private enum State {
    // We don't actually need any states.
  }
}
