/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;

public abstract class JUnitSupportTestCase extends GuiTestCase {
  /**
   * Sets the "Before Launch" tasks in the "JUnit Run Configuration" template.
   * @param taskName the name of the "Before Launch" task (e.g. "Make", "Gradle-aware Make")
   * @param project the project currently opened in the IDE.
   */
  protected void setJUnitBeforeRunTask(@NotNull String taskName, @NotNull Project project) {
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    ConfigurationType junitConfigurationType = runManager.getConfigurationType("JUnit");
    assertNotNull("Failed to find run configuration type 'JUnit'", junitConfigurationType);

    for (ConfigurationFactory configurationFactory : junitConfigurationType.getConfigurationFactories()) {
      RunnerAndConfigurationSettings template = runManager.getConfigurationTemplate(configurationFactory);
      RunConfiguration runConfiguration = template.getConfiguration();
      BeforeRunTaskProvider<BeforeRunTask>[] taskProviders = Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, project);

      BeforeRunTaskProvider targetProvider = null;
      for (BeforeRunTaskProvider<? extends BeforeRunTask> provider : taskProviders) {
        if (taskName.equals(provider.getName())) {
          targetProvider = provider;
          break;
        }
      }
      assertNotNull(String.format("Failed to find task provider '%1$s'", taskName), targetProvider);

      Key id = targetProvider.getId();
      assertNotNull(id);
      BeforeRunTask task = targetProvider.createTask(runConfiguration);
      assertNotNull(task);
      task.setEnabled(true);

      runManager.setBeforeRunTasks(runConfiguration, Collections.singletonList(task), false);
    }
  }
}
