/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.asdriver.tests.metric;

import static io.ktor.util.date.DateJvmKt.getTimeMillis;

import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.Metric;
import com.google.common.math.Quantiles;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class MetricCollector {

  @NotNull
  private final Benchmark benchmark;
  private final Path telemetryJsonFile;

  public MetricCollector(@NotNull final String dashboardName, @NotNull final Path telemetryJsonFile) {
    this.telemetryJsonFile = telemetryJsonFile;
    String testDisplayNameNoWhitespaces = dashboardName.replace(' ', '_');

    benchmark = new Benchmark.Builder(testDisplayNameNoWhitespaces)
      .setProject("Android Studio Performance")
      .setDescription(String.format("Performance metrics collected during the execution of the %s test", dashboardName))
      .build();
  }

  /**
   * Collect values of the metric with the specified {@code metricName} for non-warmup events from the opentelemetry.json, compute multiple
   * aggregated values and report them to Perfgate.
   * The following aggregations will be computed:
   * <ul>
   * <li> duration of the first metric event occurrence
   * <li> median duration of the metric events
   * <li> max duration of the metric events
   * </ul>
   *
   * @param metricNames the names of the metrics that will be obtained and aggregated
   */
  public void collect(@NotNull final String... metricNames) {
    for (String metricName : metricNames) {
      doCollect(metricName);
    }
  }

  /**
   * Collect values of the metric with a specified {@code childMetricName} that are children of non-warmup {@code parentMetricName} events
   * from the opentelemetry.json, compute multiple aggregated values and report them to Perfgate.
   * The following aggregations will be computed:
   * <ul>
   * <li> duration of the first metric event occurrence
   * <li> median duration of the metric events
   * <li> max duration of the metric events
   * </ul>
   *
   * This method is needed due to the way how IntelliJ platform sets isWarmup tags to spans, the tag will only be set to a particular kind
   * of event spans, but will not be propagated down to the child event spans. For example for the warmup findUsages call the tag will only
   * be specified for the event of a `findUsagesParent` kind, but won't be propagated to its child findUsages (or findUsages_firstUsage
   * which is a child of findUsages). So we need to process all the child events of the event kind that do contain the warmup tags, keep the
   * non-warmup ones and take their child event's of a kind we are interested in (event name is {@code childMetricName} in this case).
   *
   * @param parentMetricName the names of parent metric
   * @param childMetricName the names of the metrics that will be obtained and aggregated
   */
  public void collectChildMetrics(@NotNull String parentMetricName, @NotNull String childMetricName) {
    List<Long> metricDurations =
      new TelemetryParser(SpanFilter.Companion.nameEquals(parentMetricName)).getSpanElements(telemetryJsonFile, e -> !e.isWarmup)
        .stream().filter(it -> childMetricName.equals(it.name)).map(e -> e.duration).toList();

    processMetricValues(metricDurations, childMetricName);
  }

  private void doCollect(@NotNull final String metricName) {
    List<Long> metricDurations =
      new FilteredTelemetryParser(SpanFilter.Companion.nameEquals(metricName),
                                  SpanFilter.Companion.none()).getSpanElements(telemetryJsonFile,
                                                                               e -> !e.isWarmup)
        .stream().map(e -> e.duration)
        .toList();
    processMetricValues(metricDurations, metricName);
  }

  private void processMetricValues(@NotNull final List<Long> metricValues, @NotNull final String metricName) {
    if (metricValues.isEmpty()) {
      return;
    }
    long timeStamp = getTimeMillis();

    // Duration of the first entry of the metric event
    Metric metric = new Metric(metricName + "_first");
    metric.addSamples(benchmark, new Metric.MetricSample(timeStamp, metricValues.get(0)));
    metric.commit();

    // Median duration of the metric events
    metric = new Metric(metricName + "_median");
    metric.addSamples(benchmark, new Metric.MetricSample(timeStamp, (long)Quantiles.median().compute(metricValues)));
    metric.commit();

    // Max duration of the metric events
    metric = new Metric(metricName + "_max");
    metric.addSamples(benchmark,
                      new Metric.MetricSample(timeStamp, metricValues.stream().max(java.util.Comparator.naturalOrder()).get()));
    metric.commit();
  }
}
