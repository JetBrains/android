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
package com.android.tools.idea.actions;

import com.android.tools.idea.editors.strings.StringResourceEditor;
import com.android.tools.idea.editors.strings.StringResourceEditorProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class OpenStringResourceEditorAction extends AnAction {
  public OpenStringResourceEditorAction() {
    super("Open Translations Editor", null, StringResourceEditor.ICON);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean show = false;
    final Project project = e.getProject();
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project != null && file != null) {
      show = StringResourceEditorProvider.canViewTranslations(project, file);
    }
    e.getPresentation().setEnabledAndVisible(show);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final Project project = e.getProject();
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(context);
    if (project == null || file == null) {
      return;
    }
    StringResourceEditorProvider.openEditor(project, file);
  }
}
