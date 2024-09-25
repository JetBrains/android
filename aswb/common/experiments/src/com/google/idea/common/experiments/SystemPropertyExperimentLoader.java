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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import java.util.Properties;

final class SystemPropertyExperimentLoader implements ExperimentLoader {
  private static final String BLAZE_EXPERIMENT_OVERRIDE = "blaze.experiment.";

  // Cache the properties at startup to avoid some synchronization inside the Properties object, in
  // case we do experiment checking on a hot code path. There's no reason to think people will be
  // changing these at runtime anyway.
  private static final ImmutableMap<String, String> properties = cacheSystemProperties();

  @Override
  public ImmutableMap<String, String> getExperiments() {
    return properties;
  }

  @Override
  public void initialize() {
    // Nothing to do.
  }

  @Override
  public String getId() {
    return "system property";
  }

  private static ImmutableMap<String, String> cacheSystemProperties() {
    Properties properties = System.getProperties();
    return properties.stringPropertyNames().stream()
        .filter(name -> name.startsWith(BLAZE_EXPERIMENT_OVERRIDE))
        .collect(
            toImmutableMap(
                name -> name.substring(BLAZE_EXPERIMENT_OVERRIDE.length()), System::getProperty));
  }
}
