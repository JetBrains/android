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

import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.*;

public class LineChartModelTest {
  @Test
  public void testSnapToDataMaxOnFirstUpdate() {
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

    LineChartModel model = new LineChartModel(newDirectExecutorService());
    model.add(rangedSeries);
    updater.register(model);

    assertEquals(50, yRange.getMax(), .0);  // before update.
    t.step();
    assertEquals(100, yRange.getMax(), 0);  // after update.
  }

  @Test
  public void testNoUpdateOnZeroYValue() {
    Range xRange = new Range(0, 10);
    Range yRange = new Range(0, 0);
    DefaultDataSeries<Long> testSeries = new DefaultDataSeries<>();
    for (int i = 0; i < 11; i++) {
      testSeries.add(i, 0L);
    }
    RangedContinuousSeries rangedSeries = new RangedContinuousSeries("test", xRange, yRange, testSeries);
    FakeTimer t = new FakeTimer();
    Updater updater = new Updater(t);

    LineChartModel model = new LineChartModel();
    model.add(rangedSeries);
    updater.register(model);

    assertEquals(0, yRange.getMax(), 0);  // before update.
    t.step();
    assertEquals(0, yRange.getMax(), 0);  // after update.
  }

  @Test
  public void testNoUpdateOnNoLargerThanCurrentRangeMaxData() {
    // See max to Long.MAX_VALUE to make sure we are querying the newly added data, to prevent RangedSeries from caching the data.
    Range xRange = new Range(0, Long.MAX_VALUE);
    Range yRange = new Range(0, 0);
    DefaultDataSeries<Long> testSeries = new DefaultDataSeries<>();

    RangedContinuousSeries rangedSeries = new RangedContinuousSeries("test", xRange, yRange, testSeries);
    FakeTimer t = new FakeTimer();
    Updater updater = new Updater(t);

    LineChartModel model = new LineChartModel();
    model.add(rangedSeries);
    updater.register(model);

    t.step(); // Get past first update.
    assertEquals(0, yRange.getMax(), 0);

    // Check that even after the first update, we should not be updating.
    AspectObserver observer = new AspectObserver();
    boolean[] updated = {false};
    model.addDependency(observer).onChange(LineChartModel.Aspect.LINE_CHART, () -> updated[0] = true);
    t.step();
    assertFalse(updated[0]);

    // Check that if we add a sample whose value is not greater than the current range, we don't update.
    testSeries.add(0, 0L);
    t.step();
    assertFalse(updated[0]);

    // Check that if we add a sample whose value is smaller than the current range's, we don't update the max value
    // (even although min is not updated as well).
    testSeries.add(1, -1L);
    t.step();
    assertFalse(updated[0]);
  }

  @Test
  public void testNegativeRanges() {
    // See max to Long.MAX_VALUE to make sure we are querying the newly added data, to prevent RangedSeries from caching the data.
    Range xRange = new Range(0, Long.MAX_VALUE);
    Range yRange = new Range(-10, -5);
    DefaultDataSeries<Long> testSeries = new DefaultDataSeries<>();

    RangedContinuousSeries rangedSeries = new RangedContinuousSeries("test", xRange, yRange, testSeries);
    FakeTimer t = new FakeTimer();
    Updater updater = new Updater(t);

    LineChartModel model = new LineChartModel(newDirectExecutorService());
    model.add(rangedSeries);
    updater.register(model);

    t.step(); // Get past first update.
    assertEquals(-5, yRange.getMax(), 0);

    // Check that even after the first update, we should not be updating.
    AspectObserver observer = new AspectObserver();
    boolean[] updated = {false};
    model.addDependency(observer).onChange(LineChartModel.Aspect.LINE_CHART, () -> updated[0] = true);
    t.step();
    assertFalse(updated[0]);

    // Check that if we add a sample whose value is not greater than the current range, we don't update.
    testSeries.add(0, -5L);
    t.step();
    assertFalse(updated[0]);
    assertEquals(-5, yRange.getMax(), 0);

    // Check that if we add a sample whose value is smaller than the current range's, we don't update the max value
    // (even although min is not updated as well).
    testSeries.add(1, -10L);
    t.step();
    assertFalse(updated[0]);

    // Check that we do update once something larger comes along.
    testSeries.add(1, 7L);
    t.step();
    assertTrue(updated[0]);
    // TODO(b/80503984) We can't assert range max is at 7L since it needs many steps to interpolate.
  }
}
