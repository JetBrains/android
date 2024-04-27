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
package com.android.tools.idea.common.actions;

import com.android.tools.adtui.workbench.DetachedToolWindowManager;
import com.android.tools.adtui.workbench.WorkBenchManager;
import com.intellij.ide.actions.RestoreDefaultLayoutAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Restores the layout of tool windows.
 */
public class RestoreDefaultWindowLayoutAction extends AnAction implements DumbAware {
  private final RestoreDefaultLayoutAction myDelegate;

  public RestoreDefaultWindowLayoutAction() {
    super(ActionsBundle.messagePointer("action.RestoreDefaultLayout.text"), ActionsBundle.messagePointer("action.RestoreDefaultLayout.description"));
    myDelegate = new RestoreDefaultLayoutAction();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    myDelegate.actionPerformed(event);

    WorkBenchManager workBenchManager = WorkBenchManager.getInstance();
    workBenchManager.restoreDefaultLayout();

    Project project = event.getProject();
    if (project == null) {
      return;
    }
    DetachedToolWindowManager floatingToolWindowManager = DetachedToolWindowManager.getInstance(project);
    floatingToolWindowManager.restoreDefaultLayout();
  }

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    myDelegate.update(event);
  }
}
