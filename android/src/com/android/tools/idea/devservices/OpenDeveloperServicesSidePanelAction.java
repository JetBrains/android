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
package com.android.tools.idea.devservices;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;

/**
 * Triggers the creation of the Developer Services side panel.
 */
public final class OpenDeveloperServicesSidePanelAction extends AnAction {

  private static final String TOOL_WINDOW_TITLE = "Developer Services";

  @Override
  public void update(AnActionEvent event) {
    // Put the side panel behind a JVM flag value.
    // TODO(zhi):  Enable this when ready for general consumption.
    event.getPresentation().setEnabled(DeveloperServicesUtils.isEnabled());
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project thisProject = event.getProject();
    final String actionId = ActionManager.getInstance().getId(this);
    final String bundleName = event.getPresentation().getText();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        // TODO:  Figure out how to swap out content if a new Developer Service is triggered.
        DeveloperServicesToolWindowFactory factory = new DeveloperServicesToolWindowFactory(actionId, bundleName);
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(thisProject);
        ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_TITLE);

        if (toolWindow == null) {
          toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_TITLE, true, ToolWindowAnchor.RIGHT);
          factory.createToolWindowContent(thisProject, toolWindow);
        }
        // Always active the window, in case it was previously minimized.
        toolWindow.activate(null);
      }
    });
  }
}
