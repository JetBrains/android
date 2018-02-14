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
package com.android.tools.idea.profilers;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidLaunchTaskContributor;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTaskDurations;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link LaunchTask} to show the profiler tool window button on run/debug.
 */
public final class AndroidProfilerToolWindowTaskContributor implements AndroidLaunchTaskContributor {

  @NotNull
  @Override
  public LaunchTask getTask(@NotNull Module module, @NotNull String applicationId, @NotNull LaunchOptions launchOptions) {
    return new AndroidProfilerToolWindowLaunchTask(module.getProject());
  }

  public static final class AndroidProfilerToolWindowLaunchTask implements LaunchTask {
    @NotNull private final Project myProject;

    public AndroidProfilerToolWindowLaunchTask(@NotNull Project project) {
      myProject = project;
    }

    @NotNull
    @Override
    public String getDescription() {
      return "Presents the Android Profiler Tool Window";
    }

    @Override
    public int getDuration() {
      return LaunchTaskDurations.LAUNCH_ACTIVITY;
    }

    @Override
    public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
      ApplicationManager.getApplication().invokeLater(
        () -> {
          ToolWindow window = ToolWindowManagerEx.getInstanceEx(myProject).getToolWindow(AndroidProfilerToolWindowFactory.ID);
          if (window != null) {
            window.setShowStripeButton(true);
            AndroidProfilerToolWindow profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerTooWindow(myProject);
            if (profilerToolWindow != null) {
              profilerToolWindow.profileProject(myProject);
            }
          }
        });
      return true;
    }
  }
}