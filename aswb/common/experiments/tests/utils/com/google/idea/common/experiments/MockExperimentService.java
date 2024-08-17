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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Used for tests. */
public class MockExperimentService implements ExperimentService {

  private final Map<String, Object> experiments = new HashMap<>();

  @Override
  public void startExperimentScope() {}

  @Override
  public void endExperimentScope() {}

  public void setExperimentRaw(String key, Object value) {
    experiments.put(key, value);
  }

  public void setExperiment(BoolExperiment experiment, boolean value) {
    experiments.put(experiment.getKey(), value);
  }

  @Override
  public boolean getExperiment(Experiment experiment, boolean defaultValue) {
    if (experiments.containsKey(experiment.getKey())) {
      return (Boolean) experiments.get(experiment.getKey());
    }
    return defaultValue;
  }

  public void setExperimentString(StringExperiment experiment, String value) {
    experiments.put(experiment.getKey(), value);
  }

  @Override
  @Nullable
  public String getExperimentString(Experiment experiment, @Nullable String defaultValue) {
    if (experiments.containsKey(experiment.getKey())) {
      return experiments.get(experiment.getKey()).toString();
    }
    return defaultValue;
  }

  public void setFeatureRolloutExperiment(FeatureRolloutExperiment experiment, int percentage) {
    experiments.put(experiment.getKey(), percentage);
  }

  public void setExperimentInt(IntExperiment experiment, int value) {
    experiments.put(experiment.getKey(), value);
  }

  @Override
  public int getExperimentInt(Experiment experiment, int defaultValue) {
    if (experiments.containsKey(experiment.getKey())) {
      return (Integer) experiments.get(experiment.getKey());
    }
    return defaultValue;
  }

  @Override
  public ImmutableMap<String, Experiment> getAllQueriedExperiments() {
    throw new UnsupportedOperationException("MockExperimentService#getAllQueriedExperiments");
  }

  @Override
  public void notifyExperimentsChanged() {}

  @Override
  public List<ExperimentValue> getOverrides(String key) {
    return ImmutableList.of(ExperimentValue.create("loader", key, experiments.get(key).toString()));
  }
}
