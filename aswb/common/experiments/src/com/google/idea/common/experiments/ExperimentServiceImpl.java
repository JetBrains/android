/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.common.experiments;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.common.util.MorePlatformUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import com.intellij.util.Alarm.ThreadToUse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import kotlinx.coroutines.CoroutineScope;


/**
 * An experiment service that delegates to {@link ExperimentLoader ExperimentLoaders}, in a specific
 * order.
 *
 * <p>It will check system properties first, then an experiment file in the user's home directory,
 * then finally all files specified by the system property blaze.experiments.file.
 */
public class ExperimentServiceImpl implements ApplicationComponent, ExperimentService {

  private static final Logger logger = Logger.getInstance(ExperimentServiceImpl.class);

  private static final Duration REFRESH_FREQUENCY = Duration.ofMinutes(5);

  private final Alarm alarm;
  private final List<ExperimentLoader> services;
  private final Supplier<String> channelSupplier;
  private final AtomicInteger experimentScopeCounter = new AtomicInteger(0);

  private volatile Map<String, String> experiments = ImmutableMap.of();
  private volatile Map<String, List<ExperimentValue>> overrides = ImmutableMap.of();
  private final Map<String, Experiment> queriedExperiments = new ConcurrentHashMap<>();

  ExperimentServiceImpl() {
    this(MorePlatformUtils::getIdeChannel, ExperimentLoader.EP_NAME.getExtensions());
  }

  @VisibleForTesting
  ExperimentServiceImpl(CoroutineScope scope, ExperimentLoader... loaders) {
    this(scope, MorePlatformUtils::getIdeChannel, loaders);
  }

  @VisibleForTesting
  ExperimentServiceImpl(Supplier<String> channelSupplier, ExperimentLoader... loaders) {
    this(null, channelSupplier, loaders);
  }

  @VisibleForTesting
  ExperimentServiceImpl(@Nullable CoroutineScope scope, Supplier<String> channelSupplier,
      ExperimentLoader... loaders) {
    services = ImmutableList.copyOf(loaders);
    this.channelSupplier = channelSupplier;
    // Bypass unregistered application service AlarmSharedCoroutineScopeHolder. It's a private service which hard to mock
    this.alarm = new Alarm(ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication(), null,
        scope);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      refreshExperiments();
    }
  }

  @Override
  public void initComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      services.forEach(ExperimentLoader::initialize);
    }
    // refresh experiments synchronously; some callers require a valid initial state on startup
    refreshExperiments();

    // then kick off the periodic refresh task
    scheduleRefresh(REFRESH_FREQUENCY);
  }

  @Nullable
  private String getExperiment(Experiment experiment) {
    queriedExperiments.putIfAbsent(experiment.getKey(), experiment);
    if (experiments.containsKey(experiment.getKey())) {
      return experiments.get(experiment.getKey());
    }
    String channelKey = channelSupplier.get() + "." + experiment.getKey();
    if (experiments.containsKey(channelKey)) {
      return experiments.get(channelKey);
    }
    return null;
  }

  @Override
  public boolean getExperiment(Experiment experiment, boolean defaultValue) {
    String property = getExperiment(experiment);
    return property != null ? property.equals("1") : defaultValue;
  }

  @Override
  public String getExperimentString(Experiment experiment, @Nullable String defaultValue) {
    String property = getExperiment(experiment);
    return property != null ? property : defaultValue;
  }

  @Override
  public int getExperimentInt(Experiment experiment, int defaultValue) {
    String property = getExperiment(experiment);
    try {
      return property != null ? Integer.parseInt(property.trim()) : defaultValue;
    } catch (NumberFormatException e) {
      logger.warn("Could not parse int for experiment: " + experiment.getKey(), e);
      return defaultValue;
    }
  }

  @Override
  public void startExperimentScope() {
    if (experimentScopeCounter.getAndIncrement() == 0) {
      // synchronously update experiments and keep them fixed for the duration of the scope
      refreshExperiments();
    }
  }

  @Override
  public void endExperimentScope() {
    int counter = experimentScopeCounter.decrementAndGet();
    logger.assertTrue(counter >= 0);
    if (counter <= 0 && ApplicationManager.getApplication().isUnitTestMode()) {
      refreshExperiments();
    }
  }

  private synchronized void scheduleRefresh(Duration delay) {
    if (alarm.isDisposed()) {
      return;
    }
    // don't allow multiple pending requests
    alarm.cancelAllRequests();

    alarm.addRequest(
        () -> {
          try {
            if (experimentScopeCounter.get() <= 0) {
              refreshExperiments();
            }
          } finally {
            scheduleRefresh(REFRESH_FREQUENCY);
          }
        },
        delay.toMillis());
  }

  @Override
  public void notifyExperimentsChanged() {
    scheduleRefresh(Duration.ZERO);
  }

  private void refreshExperiments() {
    List<ExperimentValue> values =
        services.stream()
            .flatMap(
                service ->
                    service.getExperiments().entrySet().stream()
                        .map(
                            e -> ExperimentValue.create(service.getId(), e.getKey(), e.getValue())))
            .collect(Collectors.toUnmodifiableList());

    experiments =
        values.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    ExperimentValue::key, ExperimentValue::value, (first, second) -> first));

    overrides =
        ImmutableMap.copyOf(
            values.stream()
                .collect(
                    Collectors.groupingBy(ExperimentValue::key, Collectors.toUnmodifiableList())));
  }

  @Override
  public ImmutableMap<String, Experiment> getAllQueriedExperiments() {
    return ImmutableMap.copyOf(queriedExperiments);
  }

  @Override
  public List<ExperimentValue> getOverrides(String key) {
    return overrides.get(key);
  }
}
