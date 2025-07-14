/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.run;

import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.google.common.collect.Lists;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class MakeBeforeRunTaskProviderUtil {
  public static void ensureMakeBeforeRunTaskInConfigurationTemplate(@NotNull Project project) {
    RunManager runManager = RunManagerEx.getInstance(project);
    RunConfiguration configuration = runManager.getConfigurationTemplate(new AndroidRunConfigurationType().getFactory()).getConfiguration();
    if (configuration.getBeforeRunTasks().isEmpty()) {
      configuration.setBeforeRunTasks(Lists.newArrayList(new MakeBeforeRunTaskProvider().createTask(configuration)));
    }
  }
}
