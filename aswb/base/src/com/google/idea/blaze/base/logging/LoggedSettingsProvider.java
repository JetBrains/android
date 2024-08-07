/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.logging;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.idea.common.experiments.Experiment;
import com.google.idea.common.experiments.ExperimentService;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Map;

/** Provides project and application settings that should be logged. */
public interface LoggedSettingsProvider {

  ExtensionPointName<LoggedSettingsProvider> EP_NAME =
      new ExtensionPointName<>("com.google.idea.blaze.LoggedSettingsProvider");

  static Map<String, String> getExperiments() {
    return Maps.transformValues(
        ExperimentService.getInstance().getAllQueriedExperiments(), Experiment::getLogValue);
  }

  String getNamespace();

  default ImmutableMap<String, String> getProjectSettings(Project project) {
    return ImmutableMap.of();
  }

  default ImmutableMap<String, String> getApplicationSettings() {
    return ImmutableMap.of();
  }
}
