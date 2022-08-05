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
package com.android.tools.idea.assistant;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Triggers the creation of the Developer Services side panel.
 */
public class OpenAssistSidePanelAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project thisProject = event.getProject();
    final String actionId = ActionManager.getInstance().getId(this);

    assert thisProject != null;
    openWindow(actionId, thisProject);
  }

  /**
   * Opens the assistant associated with the given actionId at the end of event thread
   */
  public final void openWindow(@NotNull String actionId, @NotNull Project project) {
    ApplicationManager.getApplication()
      .invokeLater(() -> project.getService(AssistantToolWindowService.class).openAssistant(actionId, null));
  }
}
