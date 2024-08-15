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

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Experiment class. */
public abstract class Experiment {
  private final String key;

  Experiment(String key) {
    this.key = key;
  }

  public String getKey() {
    return key;
  }

  /** Returns a string representation of the experiment value for logging. */
  public abstract String getLogValue();

  public String renderValue(String value) {
    return value;
  }

  @Nullable
  public abstract String getRawDefault();

  public boolean isOverridden(List<ExperimentValue> values) {
    return values.stream().map(ExperimentValue::value).collect(Collectors.toSet()).size() > 1;
  }
}
