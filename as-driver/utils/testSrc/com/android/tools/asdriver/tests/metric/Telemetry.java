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

import java.nio.file.Path;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * A class that allows to extract metrics from the opentelemetry.json file. This file contains metrics collected by the IntelliJ platform,
 * but also provides a way to implement metrics collection on the Android Studio level.
 * <p>
 * Extracted metrics values are sent to Perfgate using the @{code MetricBenchmarkSender} instance.
 * </p>
 */
public class Telemetry {

  @NotNull final Path telemetryJsonFile;

  public Telemetry(@NotNull final Path telemetryJsonFile) {
    this.telemetryJsonFile = telemetryJsonFile;
  }

  public Stream<SpanElement> getChild(@NotNull String parentMetricName) {
    return new TelemetryParser(SpanFilter.Companion.nameEquals(parentMetricName)).getSpanElements(telemetryJsonFile, e -> !e.isWarmup)
      .stream();
  }

  /**
   * Collect values of the metric with a specified {@code childMetricName} that are children of non-warmup {@code parentMetricName} events.
   * <p>
   * This method is needed due to the way how IntelliJ platform sets isWarmup tags to spans, the tag will only be set to a particular kind
   * of event spans, but will not be propagated down to the child event spans. For example for the warmup findUsages call the tag will only
   * be specified for the event of a `findUsagesParent` kind, but won't be propagated to its child findUsages (or findUsages_firstUsage
   * which is a child of findUsages). So we need to process all the child events of the event kind that do contain the warmup tags, keep the
   * non-warmup ones and take their child event's of a kind we are interested in (event name is {@code childMetricName} in this case).
   *
   * @param parentMetricName the names of parent metric
   * @param childMetricName  the names of the metrics that will be obtained
   */
  public Stream<Long> getChild(@NotNull String parentMetricName,
                               @NotNull String childMetricName) {
    return getChild(parentMetricName).filter(it -> childMetricName.equals(it.name)).map(e -> e.duration);
  }

  /**
   * Collects values of the telemetry metrics with the given {@param metricName}.
   */
  public Stream<Long> get(@NotNull final String metricName) {
    return new FilteredTelemetryParser(SpanFilter.Companion.nameEquals(metricName),
                                       SpanFilter.Companion.none()).getSpanElements(telemetryJsonFile,
                                                                                    e -> !e.isWarmup)
      .stream().map(e -> e.duration);
  }
}
