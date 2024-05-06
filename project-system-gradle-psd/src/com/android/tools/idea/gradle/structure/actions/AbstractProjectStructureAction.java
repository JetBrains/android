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
package com.android.tools.idea.gradle.structure.actions;

import com.android.tools.idea.gradle.actions.AndroidStudioGradleAction;
import com.android.tools.idea.gradle.structure.AndroidProjectSettingsServiceImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractProjectStructureAction extends AndroidStudioGradleAction {
  public AbstractProjectStructureAction(@Nullable String text) {
    super(text);
  }

  protected AbstractProjectStructureAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    e.getPresentation().setEnabled(getTargetModule(e) != null);
  }

  @Override
  protected final void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    Module module = getTargetModule(e);
    if (module != null) {
      ProjectSettingsService service = ProjectSettingsService.getInstance(module.getProject());
      if (service instanceof AndroidProjectSettingsServiceImpl) {
        doPerform(module, ((AndroidProjectSettingsServiceImpl)service), e);
      }
    }
  }

  protected abstract Module getTargetModule(@NotNull AnActionEvent e);

  protected abstract void doPerform(@NotNull Module module,
                                    @NotNull AndroidProjectSettingsServiceImpl projectStructureService,
                                    @NotNull AnActionEvent e);
}
