/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Reads experiment values from the resource file attached to the class. */
public class DefaultValuesExperimentLoader implements ExperimentLoader {

  private static final Logger logger = Logger.getInstance(FileExperimentLoader.class);

  private final String resourceName;
  private volatile ImmutableMap<String, String> experiments = ImmutableMap.of();

  public DefaultValuesExperimentLoader() {
    this("experiment.properties");
  }

  public DefaultValuesExperimentLoader(String resourceName) {
    this.resourceName = resourceName;
  }

  @Override
  public String getId() {
    return resourceName;
  }

  @Override
  public ImmutableMap<String, String> getExperiments() {
    return experiments;
  }

  @Override
  public void initialize() {
    try (InputStream fis = Resources.getResource(this.getClass(), resourceName).openStream();
        BufferedInputStream bis = new BufferedInputStream(fis)) {
      Properties properties = new Properties();
      properties.load(bis);
      experiments = ImmutableMap.copyOf(Maps.fromProperties(properties));
    } catch (IOException e) {
      logger.warn("Could not load experiments from resource file: " + resourceName, e);
    }
  }
}
