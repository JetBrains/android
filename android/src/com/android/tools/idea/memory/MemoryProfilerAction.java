/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.memory;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import icons.AndroidIcons;

public class MemoryProfilerAction extends AnAction {

  public MemoryProfilerAction() {
    super(MemoryProfilingToolWindowFactory.ID);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(MemoryProfilingToolWindowFactory.ID);
    if (toolWindow == null) {
      toolWindow = toolWindowManager.registerToolWindow(MemoryProfilingToolWindowFactory.ID, false, ToolWindowAnchor.BOTTOM);
      toolWindow.setIcon(AndroidIcons.AndroidToolWindow);
      new MemoryProfilingToolWindowFactory().createToolWindowContent(project, toolWindow);
    }
    toolWindow.show(null);
  }
}
