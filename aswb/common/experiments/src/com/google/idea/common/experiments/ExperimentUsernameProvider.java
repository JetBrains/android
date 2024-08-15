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
package com.google.idea.common.experiments;

import com.intellij.util.SystemProperties;
import javax.annotation.Nullable;

/**
 * Provides the current user's username for determining experiment values.
 *
 * <p>Allows developers to receive a specific user's experiments without breaking other IDE and
 * plugin code relying on the {@code user.name} system property.
 */
final class ExperimentUsernameProvider {
  private ExperimentUsernameProvider() {}

  private static final StringExperiment usernameOverride =
      new StringExperiment("experiment.username.override");

  @Nullable
  static String getUsername() {
    String override = usernameOverride.getValue();
    return override != null ? override : SystemProperties.getUserName();
  }

  static boolean isUsernameOverridden() {
    return usernameOverride.getValue() != null;
  }
}
