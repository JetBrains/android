/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.testartifacts.junit;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Android implementation of {@link JUnitConfiguration} so some behaviors can be overridden.
 */
public class AndroidJUnitConfiguration extends JUnitConfiguration {
  public AndroidJUnitConfiguration(@NotNull String name,
                                   @NotNull Project project,
                                   @NotNull ConfigurationFactory configurationFactory) {
    super(name, project, configurationFactory);
  }

  @Override
  public SMTRunnerConsoleProperties createTestConsoleProperties(Executor executor) {
    return new AndroidJUnitConsoleProperties(this, executor);
  }
}
