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

import com.android.tools.idea.run.ExecutorIconProvider;
import com.android.tools.idea.run.LaunchOptionsProvider;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.common.collect.ImmutableMap;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import icons.StudioIcons;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class ProfileRunExecutor extends DefaultRunExecutor implements ExecutorIconProvider, LaunchOptionsProvider {
  public static final String PROFILER_LAUNCH_OPTION_KEY = "isProfiling";

  @NonNls public static final String EXECUTOR_ID = AndroidProfilerToolWindowFactory.ID;

  @NotNull
  @Override
  public Icon getIcon() {
    return StudioIcons.Shell.Toolbar.PROFILER;
  }

  @Override
  public Icon getDisabledIcon() {
    return StudioIcons.Shell.ToolWindows.ANDROID_PROFILER;
  }

  @Override
  public String getDescription() {
    return "Profile selected configuration";
  }

  @NotNull
  @Override
  public String getActionName() {
    return "Profile";
  }

  @NotNull
  @Override
  public String getId() {
    return EXECUTOR_ID;
  }

  @NotNull
  @Override
  public String getStartActionText() {
    return "Profile";
  }

  @Override
  public String getContextActionId() {
    return "ProfileRunClass";
  }

  @Override
  public String getHelpId() {
    return null;
  }

  public static Executor getProfileExecutorInstance() {
    return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID);
  }

  @Nullable
  @Override
  public Icon getExecutorIcon(@NotNull Project project, @NotNull Executor executor) {
    AndroidProfilerToolWindow profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerToolWindow(project);
    if (profilerToolWindow != null) {
      StudioProfilers profilers = profilerToolWindow.getProfilers();
      if (profilers != null) {
        Common.Session profilingSession = profilers.getSessionsManager().getProfilingSession();
        if (SessionsManager.isSessionAlive(profilingSession)) {
          return ExecutionUtil.getLiveIndicator(getIcon());
        }
      }
    }

    return getIcon();
  }

  @NotNull
  @Override
  public Map<String, Object> getLaunchOptions() {
    return ImmutableMap.of(PROFILER_LAUNCH_OPTION_KEY, true);
  }

  @Override
  public boolean isApplicable(@NotNull Project project) {
    return AndroidUtils.hasAndroidFacets(project);
  }
}
