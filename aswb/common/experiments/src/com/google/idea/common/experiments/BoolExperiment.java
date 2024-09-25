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

/** Boolean-valued experiment. */
public class BoolExperiment extends Experiment {
  private final boolean defaultValue;

  public BoolExperiment(String key, boolean defaultValue) {
    super(key);
    this.defaultValue = defaultValue;
  }

  public boolean getValue() {
    return ExperimentService.getInstance().getExperiment(this, defaultValue);
  }

  @Override
  public String getLogValue() {
    return String.valueOf(getValue());
  }

  @Override
  public String getRawDefault() {
    return defaultValue ? "1" : "0";
  }

  @Override
  public String renderValue(String value) {
    return "1".equals(value) ? "true" : "false";
  }
}
