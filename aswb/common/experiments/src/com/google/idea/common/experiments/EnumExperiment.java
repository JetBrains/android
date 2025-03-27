/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

import com.intellij.openapi.diagnostic.Logger;
import com.jgoodies.common.base.Strings;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** String-valued experiment. */
public class EnumExperiment<T extends Enum<?>> extends Experiment {
  private final static Logger logger = Logger.getInstance(EnumExperiment.class);

  public final String defaultStringValue;
  private final Map<String, T> values;
  private final T defaultValue;

  public EnumExperiment(String key, T defaultValue) {
    super(key);
    this.defaultValue = defaultValue;
    this.defaultStringValue = defaultValue.name().toLowerCase(Locale.ROOT);
    //noinspection unchecked
    values = Arrays.stream(defaultValue.getDeclaringClass().getEnumConstants()).collect(toImmutableMap(it -> it.name().toLowerCase(Locale.ROOT), it -> (T)it));
  }

  public T getValue() {
    String experimentString = ExperimentService.getInstance().getExperimentString(this, null);
    T value = experimentString != null ? values.get(experimentString.toLowerCase(Locale.ROOT)) : null;
    if (value == null && Strings.isNotEmpty(experimentString)){
      logger.error(String.format("Experiment %s: unknown value: %s", getKey(), experimentString), new Throwable());
    }
    return Optional.ofNullable(value).orElse(defaultValue);
  }

  @Override
  public String getLogValue() {
    return String.valueOf(getValue());
  }

  @Override
  public String getRawDefault() {
    return defaultStringValue;
  }
}
