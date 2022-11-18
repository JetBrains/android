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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerContext;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidAppAndroidDebuggerInfoProvider implements AndroidDebuggerInfoProvider {

  @Override
  public boolean supportsProject(@NotNull Project project) {
    return GradleProjectInfo.getInstance(project).isBuildWithGradle();
  }

  @NotNull
  @Override
  public List<AndroidDebugger> getAndroidDebuggers(@NotNull RunConfiguration configuration) {
    if (configuration instanceof AndroidRunConfigurationBase) {
      AndroidDebuggerContext context = ((AndroidRunConfigurationBase)configuration).getAndroidDebuggerContext();
      return context.getAndroidDebuggers();
    }

    return Collections.emptyList();
  }

  @Nullable
  @Override
  public AndroidDebugger getSelectedAndroidDebugger(@NotNull RunConfiguration configuration) {
    if (configuration instanceof AndroidRunConfigurationBase) {
      AndroidDebuggerContext context = ((AndroidRunConfigurationBase)configuration).getAndroidDebuggerContext();
      return context.getAndroidDebugger();
    }

    return null;
  }

  @Nullable
  @Override
  public AndroidDebuggerState getSelectedAndroidDebuggerState(@NotNull RunConfiguration configuration) {
    if (configuration instanceof AndroidRunConfigurationBase) {
      AndroidDebuggerContext context = ((AndroidRunConfigurationBase)configuration).getAndroidDebuggerContext();
      return context.getAndroidDebuggerState();
    }

    return null;
  }
}
