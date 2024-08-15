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

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Reads experiments. */
public interface ExperimentService {

  static ExperimentService getInstance() {
    return ApplicationManager.getApplication().getComponent(ExperimentService.class);
  }

  /** Returns an experiment if it exists, else defaultValue */
  boolean getExperiment(Experiment experiment, boolean defaultValue);

  /** Returns a string-valued experiment if it exists, else defaultValue. */
  @Nullable
  String getExperimentString(Experiment experiment, @Nullable String defaultValue);

  /** Returns an int-valued experiment if it exists, else defaultValue. */
  int getExperimentInt(Experiment experiment, int defaultValue);

  /** Starts an experiment scope. During an experiment scope, experiments won't be reloaded. */
  void startExperimentScope();

  /** Ends an experiment scope. */
  void endExperimentScope();

  /** Returns all experiments queried through this service. */
  ImmutableMap<String, Experiment> getAllQueriedExperiments();

  /** Triggers an asynchronous refresh of the cached experiments. */
  void notifyExperimentsChanged();

  /** Returns a report of experiments for bug reports. */
  List<ExperimentValue> getOverrides(String key);

  /** Returns the overrides in a log line format */
  default String getOverridesLog(Experiment ex) {
    ArrayList<ExperimentValue> overrides = new ArrayList<>();
    List<ExperimentValue> values = getOverrides(ex.getKey());
    if (values != null) {
      overrides.addAll(values);
    }
    String def = ex.getRawDefault();
    if (def != null) {
      overrides.add(ExperimentValue.create("default", ex.getKey(), def));
    }

    if (ex.isOverridden(overrides)) {
      List<String> details =
          overrides.stream()
              .map(value -> String.format("%s [%s]", ex.renderValue(value.value()), value.id()))
              .collect(Collectors.toList());
      return String.format("%s: %s", ex.getKey(), String.join(", ", details));
    }
    return "";
  }

  /** Returns the overrides logs of all queried experiments */
  default String getOverridesLog() {
    String report = "";
    for (Experiment ex : getAllQueriedExperiments().values()) {
      String overrideLog = getOverridesLog(ex);
      if (!overrideLog.isBlank()) {
        report += overrideLog + "\n";
      }
    }
    return report;
  }
}
