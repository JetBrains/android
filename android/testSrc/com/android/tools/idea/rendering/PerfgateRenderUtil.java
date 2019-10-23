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
package com.android.tools.idea.rendering;

import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.Metric;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO: Try converting to kt file later.
 */
public class PerfgateRenderUtil {

  public static final int NUMBER_OF_WARM_UP = 2;
  public static final int NUMBER_OF_SAMPLES = 40;
  public static final int MAX_PRUNED_SAMPLES = NUMBER_OF_SAMPLES / 4;

  public static final Benchmark sRenderTimeBenchMark = new Benchmark.Builder("DesignTools Render Time Benchmark")
    .setDescription("Base line for RenderTask inflate & render time (mean) after $NUMBER_OF_SAMPLES samples.")
    .build();

  public static final Benchmark sRenderMemoryBenchMark = new Benchmark.Builder("DesignTools Memory Usage Benchmark")
    .setDescription("Base line for RenderTask memory usage (mean) after $NUMBER_OF_SAMPLES samples.")
    .build();

  /**
   * Try to prune outliers based on standard deviation. No more than {@link #MAX_PRUNED_SAMPLES} samples will be pruned.
   */
  public static List<Metric.MetricSample> pruneOutliers(List<Metric.MetricSample> list) {
    float average = getAverage(list);
    float std = getStandardDeviation(list, average);

    float lowerBound = average - std;
    float upperBound = average + std;

    List<Metric.MetricSample> result = new ArrayList<>();
    for (Metric.MetricSample sample : list) {
      if (sample.getSampleData() >= lowerBound && sample.getSampleData() <= upperBound) {
        result.add(sample);
      }
    }

    // Too many samples pruned (i.e. data is widely spread). Return the raw list.
    if (result.size() < (list.size() - MAX_PRUNED_SAMPLES)) {
      return list;
    }
    return result;
  }

  private static float getStandardDeviation(List<Metric.MetricSample> list, float average) {
    float sum = 0;
    for (Metric.MetricSample sample : list) {
      sum += Math.pow(sample.getSampleData() - average, 2);
    }
    return (float) Math.sqrt(sum / list.size());
  }

  private static float getAverage(List<Metric.MetricSample> list) {
    float sum = 0;
    for (Metric.MetricSample sample : list) {
      sum += sample.getSampleData();
    }
    return sum / list.size();
  }
}
