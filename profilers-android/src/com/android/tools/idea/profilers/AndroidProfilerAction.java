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

import com.android.tools.idea.run.editor.ProfilerState;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.Nullable;

/**
 * Action to open the Android Profiler tool window
 */
public class AndroidProfilerAction extends DumbAwareAction {
  protected AndroidProfilerAction() {
    super("Android Profiler");
    getTemplatePresentation().setVisible(ProfilerState.EXPERIMENTAL_PROFILING_FLAG_ENABLED);
  }

  @Override
  public void update(AnActionEvent e) {
  }

  @Override
  public void actionPerformed(@Nullable AnActionEvent e) {
    Project project = getEventProject(e);
    if (project == null) return;
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    final ToolWindow window = windowManager.getToolWindow(AndroidProfilerToolWindowFactory.ID);

    if (windowManager.isEditorComponentActive() || !AndroidProfilerToolWindowFactory.ID.equals(windowManager.getActiveToolWindowId())) {
      window.activate(null);
    }
  }
}
