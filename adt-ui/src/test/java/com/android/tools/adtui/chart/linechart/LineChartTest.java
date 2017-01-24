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
    doThrow(new AssertionError()).when(fakeGraphics).fill(any(Shape.class));
    chart.paint(fakeGraphics);
  }
}