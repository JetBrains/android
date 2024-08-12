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
package com.google.idea.blaze.android.compose;

import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;

/**
 * Compose status provider that can be toggled through a {@link BoolExperiment}. This is meant as a
 * means to force-enable compose support in case other detection methods fail
 */
public class ExperimentComposeStatusProvider implements ComposeStatusProvider {
  private static final BoolExperiment composeEnabled =
      new BoolExperiment("aswb.force.enable.compose", false);

  @Override
  public boolean composeEnabled(Project project) {
    return composeEnabled.getValue();
  }
}
