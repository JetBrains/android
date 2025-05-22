/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.platform.performance.testing;

import static io.ktor.util.date.DateJvmKt.getTimeMillis;

import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.Metric;
import org.jetbrains.annotations.NotNull;

/**
 * Class that provides the ability to send metrics to per-test Perfgate benchmarks. All these per-test metrics are
 * organised under the `Android Studio Performance` perfgate project.
 */
public class PlatformPerformanceBenchmark {
  @NotNull
  private final Benchmark benchmark;
  private final long creationTimestampMs;

  public PlatformPerformanceBenchmark(@NotNull final String dashboardName) {
    String testDisplayNameNoWhitespaces = dashboardName.replace(' ', '_');

    benchmark = new Benchmark.Builder(testDisplayNameNoWhitespaces)
      .setProject("Android Studio Performance")
      .setDescription(String.format("Performance metrics collected during the execution of the %s test", dashboardName))
      .build();
    creationTimestampMs = getTimeMillis();
  }

  public void log(@NotNull final String metricName, long metricValue) {
    Metric metric = new Metric(metricName);
    metric.addSamples(benchmark, new Metric.MetricSample(creationTimestampMs, metricValue));
    metric.commit();
  }
}
