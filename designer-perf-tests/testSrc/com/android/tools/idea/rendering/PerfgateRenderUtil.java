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
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.util.ThrowableComputable;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * TODO: Try converting to kt file later.
 */
class PerfgateRenderUtil {
  private static final int NUMBER_OF_WARM_UP = 2;
  private static final int NUMBER_OF_SAMPLES = 40;
  private static final int MAX_PRUNED_SAMPLES = NUMBER_OF_SAMPLES / 4;

  private static final Benchmark sRenderTimeBenchMark = new Benchmark.Builder("DesignTools Render Time Benchmark")
    .setDescription("Base line for RenderTask inflate & render time (mean) after $NUMBER_OF_SAMPLES samples.")
    .build();

  private static final Benchmark sRenderMemoryBenchMark = new Benchmark.Builder("DesignTools Memory Usage Benchmark")
    .setDescription("Base line for RenderTask memory usage (mean) after $NUMBER_OF_SAMPLES samples.")
    .build();

  /**
   * Try to prune outliers based on standard deviation. No more than {@link #MAX_PRUNED_SAMPLES} samples will be pruned.
   */
  static List<Metric.MetricSample> pruneOutliers(@NotNull List<Metric.MetricSample> list) {
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

  private static float getStandardDeviation(@NotNull List<Metric.MetricSample> list, float average) {
    float sum = 0;
    for (Metric.MetricSample sample : list) {
      sum += Math.pow(sample.getSampleData() - average, 2);
    }
    return (float)Math.sqrt(sum / list.size());
  }

  private static float getAverage(List<Metric.MetricSample> list) {
    float sum = 0;
    for (Metric.MetricSample sample : list) {
      sum += sample.getSampleData();
    }
    return sum / list.size();
  }

  static void computeAndRecordMetric(
    @NotNull String renderMetricName,
    @NotNull String memoryMetricName,
    @NotNull ThrowableComputable<PerfgateRenderMetric, ? extends Exception> computable) throws Exception {

    System.gc();

    // LayoutLib has a large static initialization that would trigger on the first render.
    // Warm up by inflating few times before measuring.
    for (int i = 0; i < NUMBER_OF_WARM_UP; i++) {
      computable.compute();
    }

    // baseline samples
    List<Metric.MetricSample> renderTimes = new ArrayList<>(NUMBER_OF_SAMPLES);
    List<Metric.MetricSample> memoryUsages = new ArrayList<>(NUMBER_OF_SAMPLES);
    for (int i = 0; i < NUMBER_OF_SAMPLES; i++) {
      PerfgateRenderMetric metric = computable.compute();

      renderTimes.add(metric.getRenderTimeMetricSample());
      memoryUsages.add(metric.getMemoryMetricSample());
    }

    Metric renderMetric = new Metric(renderMetricName);
    List<Metric.MetricSample> result = pruneOutliers(renderTimes);
    renderMetric.addSamples(sRenderTimeBenchMark, result.toArray(new Metric.MetricSample[0]));
    renderMetric.commit();

    // Let's start without pruning to see how bad it is.
    Metric memMetric = new Metric(memoryMetricName);
    memMetric.addSamples(sRenderMemoryBenchMark, memoryUsages.toArray(new Metric.MetricSample[0]));
    memMetric.commit();
  }

  @NotNull
  static PerfgateRenderMetric getInflateMetric(@NotNull RenderTask task,
                                               @NotNull ResultVerifier resultVerifier) {
    PerfgateRenderMetric renderMetric = new PerfgateRenderMetric();

    renderMetric.beforeTest();
    RenderResult result = Futures.getUnchecked(task.inflate());
    renderMetric.afterTest();

    resultVerifier.verify(result);
    return renderMetric;
  }

  @NotNull
  static PerfgateRenderMetric getRenderMetric(@NotNull RenderTask task,
                                              @NotNull ResultVerifier inflateVerifier,
                                              @NotNull ResultVerifier renderVerifier) {
    inflateVerifier.verify(Futures.getUnchecked(task.inflate()));
    PerfgateRenderMetric renderMetric = new PerfgateRenderMetric();

    renderMetric.beforeTest();
    RenderResult result = Futures.getUnchecked(task.render());
    renderMetric.afterTest();

    renderVerifier.verify(result);
    return renderMetric;
  }

  @NotNull
  static PerfgateRenderMetric getRenderMetric(@NotNull RenderTask task,
                                              @NotNull ResultVerifier resultVerifier) {
    return getRenderMetric(task, resultVerifier, resultVerifier);
  }

  /**
   * Interface to pass to {@link #getInflateMetric(RenderTask, ResultVerifier)} and {@link #getRenderMetric(RenderTask, ResultVerifier)} to
   * ensure the {@link RenderResult} is valid.
   */
  interface ResultVerifier {
    void verify(RenderResult result);
  }
}
