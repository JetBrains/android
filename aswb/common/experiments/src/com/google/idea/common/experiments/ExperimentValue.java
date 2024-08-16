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

import com.google.auto.value.AutoValue;

/**
 * Represents a {@code value} for a given experiment of {@code key}, created by a given loader with
 * {@code id}
 */
@AutoValue
public abstract class ExperimentValue {

  public static ExperimentValue create(String id, String key, String value) {
    return new AutoValue_ExperimentValue(id, key, value);
  }

  public abstract String id();

  public abstract String key();

  public abstract String value();
}
