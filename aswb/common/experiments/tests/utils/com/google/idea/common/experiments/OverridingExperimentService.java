/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import javax.annotation.Nullable;

/**
 * An implementation of {@link ExperimentService} that allows overriding responses from one instance
 * with responses from another instance.
 *
 * <p>This is useful in integration tests as it allows to override the default values with some
 * values from {@link MockExperimentService}.
 */
public class OverridingExperimentService implements ExperimentService {

  private final ExperimentService overrideWithService;
  private final ExperimentService delegate;

  public OverridingExperimentService(
      ExperimentService overrideWithService, ExperimentService delegate) {
    this.overrideWithService = overrideWithService;
    this.delegate = delegate;
  }

  @Override
  public boolean getExperiment(Experiment experiment, boolean defaultValue) {
    return overrideWithService.getExperiment(
        experiment, delegate.getExperiment(experiment, defaultValue));
  }

  @Nullable
  @Override
  public String getExperimentString(Experiment experiment, @Nullable String defaultValue) {
    return overrideWithService.getExperimentString(
        experiment, delegate.getExperimentString(experiment, defaultValue));
  }

  @Override
  public int getExperimentInt(Experiment experiment, int defaultValue) {
    return overrideWithService.getExperimentInt(
        experiment, delegate.getExperimentInt(experiment, defaultValue));
  }

  @Override
  public void startExperimentScope() {
    delegate.startExperimentScope();
    overrideWithService.startExperimentScope();
  }

  @Override
  public void endExperimentScope() {
    delegate.endExperimentScope();
    overrideWithService.startExperimentScope();
  }

  @Override
  public ImmutableMap<String, Experiment> getAllQueriedExperiments() {
    throw new UnsupportedOperationException("OverridingExperimentService#getAllQueriedExperiments");
  }

  @Override
  public void notifyExperimentsChanged() {
    delegate.notifyExperimentsChanged();
    overrideWithService.notifyExperimentsChanged();
  }

  @Override
  public List<ExperimentValue> getOverrides(String key) {
    ImmutableList.Builder<ExperimentValue> ret = ImmutableList.builder();
    ret.addAll(delegate.getOverrides(key));
    ret.addAll(overrideWithService.getOverrides(key));
    return ret.build();
  }
}
