/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp;

import com.android.tools.idea.instantapp.provision.ProvisionBeforeRunTaskProvider;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides services for {@link ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask}.
 * Methods are not static so they can be mocked.
 */
public class ProvistionTasks {
  /**
   * Create a {@link ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask} in each {@link AndroidRunConfigurationBase} if yet created.
   */
  public void addInstantAppProvisionTaskToRunConfigurations(@NotNull Project project) {
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    // Add ProvisionBeforeRunTask to android run configurations when it's not already there (see http://b/64572730)
    BeforeRunTaskProvider provisionTaskProvider = BeforeRunTaskProvider.getProvider(project, ProvisionBeforeRunTaskProvider.ID);

    if (provisionTaskProvider != null) {
      RunConfiguration[] runConfigurations = runManager.getAllConfigurations();
      for (RunConfiguration runConfiguration : runConfigurations) {
        if (runConfiguration instanceof AndroidRunConfigurationBase) {
          // Makes a copy so we don't modify the original one in case the method changes its implementation
          List<BeforeRunTask> beforeRunTasks = new ArrayList<>(runManager.getBeforeRunTasks(runConfiguration));
          if (runManager.getBeforeRunTasks(runConfiguration, ProvisionBeforeRunTaskProvider.ID).isEmpty()) {
            BeforeRunTask provisionTask = provisionTaskProvider.createTask(runConfiguration);
            if (provisionTask != null) {
              beforeRunTasks.add(provisionTask);
              runManager.setBeforeRunTasks(runConfiguration, beforeRunTasks, false);
            }
          }
        }
      }
    }
  }
}
