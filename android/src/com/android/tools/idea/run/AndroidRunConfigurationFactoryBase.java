/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.tools.idea.util.CommonAndroidUtil;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationSingletonPolicy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public abstract class AndroidRunConfigurationFactoryBase extends ConfigurationFactory {
  public AndroidRunConfigurationFactoryBase(@NotNull ConfigurationType type) {super(type);}

  @Override
  @NotNull
  public abstract String getId();

  @NotNull
  @Override
  public abstract RunConfiguration createTemplateConfiguration(@NotNull Project project);

  @NotNull
  @Override
  public RunConfigurationSingletonPolicy getSingletonPolicy() {
    return RunConfigurationSingletonPolicy.MULTIPLE_INSTANCE;
  }

  @Override
  public boolean isApplicable(@NotNull Project project) {
    return CommonAndroidUtil.getInstance().isAndroidProject(project);
  }

  @Override
  public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
    // Disable the default Make compile step for this run configuration type
    if (CompileStepBeforeRun.ID.equals(providerID)) {
      task.setEnabled(false);
    }
  }
}
