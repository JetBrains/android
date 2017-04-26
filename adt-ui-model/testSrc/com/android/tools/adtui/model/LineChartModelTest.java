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
package com.android.tools.adtui.model;

import com.android.tools.adtui.model.updater.Updater;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LineChartModelTest {

  @Test
  public void testSnapToDataMaxOnFirstUpdate() throws Exception {
    // Test that during the first update, the LineChart will immediately snap to the current data max instead of interpolating.
    Range xRange = new Range(0, 100);
    Range yRange = new Range(0, 50);
    DefaultDataSeries<Long> testSeries = new DefaultDataSeries<>();
    for (int i = 0; i < 101; i++) {
      testSeries.add(i, (long)i);
    }
    RangedContinuousSeries rangedSeries = new RangedContinuousSeries("test", xRange, yRange, testSeries);
    FakeTimer t = new FakeTimer();
    Updater updater = new Updater(t);

    LineChartModel model = new LineChartModel();
    model.add(rangedSeries);
    updater.register(model);

    assertEquals(50, yRange.getMax(), .0);  // before update.
    t.step();
    assertEquals(100, yRange.getMax(), 0);  // after update.
  }
}