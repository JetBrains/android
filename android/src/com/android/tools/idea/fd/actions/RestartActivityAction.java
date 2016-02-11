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
package com.android.tools.idea.fd.actions;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.InstantRunGradleUtils;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Action which restarts an activity in the running app
 */
public class RestartActivityAction extends AnAction {
  public RestartActivityAction() {
    super("Restart Activity", null, AndroidIcons.RunIcons.Restart);
  }

  @Override
  public void update(AnActionEvent e) {
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());

    if (module == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    if (!InstantRunSettings.isInstantRunEnabled()) {
      e.getPresentation().setEnabled(false);
      return;
    }

    AndroidGradleModel model = InstantRunGradleUtils.getAppModel(module);
    if (model == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    Project project = module.getProject();
    boolean enabled = InstantRunSettings.isInstantRunEnabled() &&
                      InstantRunGradleUtils.getIrSupportStatus(model, null).success &&
                      !getActiveSessions(project).isEmpty() &&
                      !isDebuggerPaused(project);
    e.getPresentation().setEnabled(enabled);
  }

  private static List<ProcessHandler> getActiveSessions(@Nullable Project project) {
    if (project == null) {
      return Collections.emptyList();
    }

    List<ProcessHandler> activeHandlers = Lists.newArrayList();
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      if (!handler.isProcessTerminated() && !handler.isProcessTerminating()) {
        activeHandlers.add(handler);
      }
    }
    return activeHandlers;
  }

  private static boolean isDebuggerPaused(@Nullable Project project) {
    if (project == null) {
      return false;
    }

    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    return session != null && !session.isStopped() && session.isPaused();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    if (module == null) {
      return;
    }
    restartActivity(module);
  }

  /** Restarts the activity associated with the given module */
  public static void restartActivity(@NotNull Module module) {
    Project project = module.getProject();
    for (IDevice device : InstantRunManager.findDevices(project)) {
      if (InstantRunManager.isAppInForeground(device, module)) {
        if (InstantRunSettings.isShowToastEnabled()) {
          InstantRunManager.showToast(device, module, "Activity Restarted");
        }
        InstantRunManager.restartActivity(device, module);
      }
    }
  }
}
