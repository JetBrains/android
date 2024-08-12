/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.run.producers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;

/** Suppresses certain non-Blaze configuration producers in Blaze projects. */
public class NonBlazeProducerSuppressor implements StartupActivity {

  // PyTestsConfigurationProducer has internal visibility. We need to reference it to suppress it.
  @SuppressWarnings("KotlinInternal")
  private static final ImmutableList<Class<?>> PRODUCERS_TO_SUPPRESS =
      ImmutableList.of(
          com.jetbrains.python.run.PythonRunConfigurationProducer.class,
          com.jetbrains.python.testing.PyTestsConfigurationProducer.class,
          com.jetbrains.python.testing.PythonTestLegacyConfigurationProducer.class,
          com.jetbrains.python.testing.doctest.PythonDocTestConfigurationProducer.class,
          com.jetbrains.python.testing.nosetestLegacy.PythonNoseTestConfigurationProducer.class,
          com.jetbrains.python.testing.pytestLegacy.PyTestConfigurationProducer.class,
          com.jetbrains.python.testing.tox.PyToxConfigurationProducer.class,
          com.jetbrains.python.testing.unittestLegacy.PythonUnitTestConfigurationProducer.class);

  @Override
  public void runActivity(Project project) {
    if (Blaze.isBlazeProject(project)) {
      suppressProducers(project);
    }
  }

  @VisibleForTesting
  @SuppressWarnings("unchecked")
  static void suppressProducers(Project project) {
    RunConfigurationProducerService producerService =
        RunConfigurationProducerService.getInstance(project);
    PRODUCERS_TO_SUPPRESS.forEach(
        (klass) -> {
          if (RunConfigurationProducer.class.isAssignableFrom(klass)) {
            producerService.addIgnoredProducer((Class<RunConfigurationProducer<?>>) klass);
          }
        });
  }
}
