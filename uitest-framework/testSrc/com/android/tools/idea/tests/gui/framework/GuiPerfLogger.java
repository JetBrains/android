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
package com.android.tools.idea.tests.gui.framework;

import com.android.tools.perflogger.BenchmarkLogger;
import com.android.tools.perflogger.BenchmarkLogger.Benchmark;
import com.android.tools.perflogger.BenchmarkLogger.MetricSample;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import javax.swing.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Instant;

/**
 * Helper class for logging UI test benchmark data.
 */
final class GuiPerfLogger extends TestWatcher {
  // TODO move these to a common location
  @NotNull private static final String UI_TEST_TIME_BENCHMARK = "Studio UI Tests Runtime";
  @NotNull private static final String UI_TEST_MEMORY_BENCHMARK = "Studio UI Tests Memory Usage";

  private final MemoryMXBean myMemoryMXBean = ManagementFactory.getMemoryMXBean();

  @NotNull private final BenchmarkLogger myBenchmarkLogger;
  private long myElapsedTime;
  @NotNull private Timer myTimer;

  @NotNull private final Benchmark myTimeBenchmark;
  @NotNull private final Benchmark myMemoryBenchmark;

  public GuiPerfLogger(@NotNull Description description) {
    myBenchmarkLogger = new BenchmarkLogger(description.getDisplayName());

    myTimeBenchmark = new Benchmark(UI_TEST_TIME_BENCHMARK);
    myMemoryBenchmark = new Benchmark(UI_TEST_MEMORY_BENCHMARK);
  }

  @Override
  protected void starting(Description description) {
    // log the approximate baseline memory before starting the test.
    System.gc();
    logHeapUsageSample();

    // log a sample every half a sec
    myTimer = new Timer(500, e -> logHeapUsageSample());
    myTimer.start();
    myElapsedTime = System.currentTimeMillis();
  }

  @Override
  protected void finished(Description description) {
    myTimer.stop();
    myElapsedTime = System.currentTimeMillis() - myElapsedTime;

    // log the approximate "after" memory.
    System.gc();
    logHeapUsageSample();

    // log the total run time of the test.
    myBenchmarkLogger.addSamples(myTimeBenchmark, new MetricSample(Instant.now().toEpochMilli(), myElapsedTime));

    myBenchmarkLogger.commit();
  }

  private void logHeapUsageSample() {
    myBenchmarkLogger
      .addSamples(myMemoryBenchmark, new MetricSample(Instant.now().toEpochMilli(), myMemoryMXBean.getHeapMemoryUsage().getUsed()));
  }
}