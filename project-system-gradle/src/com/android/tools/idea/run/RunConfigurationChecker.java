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

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.run.MakeBeforeRunTask;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProviderUtil;
import com.android.tools.idea.projectsystem.gradle.actions.FixAndroidRunConfigurationsAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

/**
 * Check that all {@link com.android.tools.idea.run.AndroidRunConfiguration AndroidRunConfigurations} of an Android project
 * contain the {@link MakeBeforeRunTask} pre-launch task. If the task is missing, add it back automatically and
 * show a notification.
 *
 * This is a workaround to bug <a href="http://issuetracker.google.com/76403139">76403139</a></a>.
 */
@Service
public final class RunConfigurationChecker  {
  @NotNull private final Project myProject;
  @NotNull private final AtomicBoolean myCheckPerformed = new AtomicBoolean();

  @SuppressWarnings("unused") // Instantiated by IDEA
  public RunConfigurationChecker(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public static RunConfigurationChecker getInstance(@NotNull Project project) {
    return project.getService(RunConfigurationChecker.class);
  }

  public void ensureRunConfigsInvokeBuild() {
    // Don't check if feature flag is disabled or if not running Android Studio
    if (!StudioFlags.FIX_ANDROID_RUN_CONFIGURATIONS_ENABLED.get()) {
      return;
    }
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      return;
    }
    // Perform check only once per project per session
    if (!myCheckPerformed.compareAndSet(false, true)) {
      return;
    }
    if (MakeBeforeRunTaskProviderUtil.getConfigurationsMissingBeforeRunTask(myProject).isEmpty()) {
      return;
    }
    // Invoke the action on the EDT thread
    ApplicationManager.getApplication().invokeLater(() -> {
      FixAndroidRunConfigurationsAction.perform(myProject);
    });
  }
}
