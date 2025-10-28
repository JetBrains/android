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
package com.google.idea.testing;

import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.google.idea.common.experiments.OverridingExperimentService;
import com.intellij.openapi.Disposable;

/**
 * A test rule that registers or replaces an experiment service instance and allows to override
 * experiment defaults.
 */
public class OverrideExperimentsRule extends TestRuleWithDisposableRoot {
  public final MockExperimentService mockService = new MockExperimentService();

  @Override
  protected void before(Disposable disposable) {
    final var delegate = ExperimentService.getInstance();
    final var experimentService = new OverridingExperimentService(mockService, delegate);
    ServiceHelper.registerApplicationService(
        ExperimentService.class, experimentService, disposable);
  }
}
