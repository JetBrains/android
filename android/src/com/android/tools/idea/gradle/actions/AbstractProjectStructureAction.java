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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT;

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
      if (service instanceof AndroidProjectSettingsService) {
        doPerform(module, ((AndroidProjectSettingsService)service), e);
      }
    }
  }

  protected abstract Module getTargetModule(@NotNull AnActionEvent e);

  @Nullable
  protected static Module getSelectedAndroidModule(@NotNull AnActionEvent e) {
    Module module = getSelectedGradleModule(e);
    if (module != null && AndroidFacet.getInstance(module) != null) {
      return module;
    }
    return null;
  }

  @Nullable
  protected static Module getSelectedGradleModule(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Module module = MODULE_CONTEXT.getData(dataContext);
    if (isGradleModule(module)) {
      return module;
    }
    Project project = e.getProject();
    if (project != null) {
      VirtualFile file = VIRTUAL_FILE.getData(dataContext);
      if (file != null) {
        ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(project);
        module = fileIndex.getModuleForFile(file);
        if (isGradleModule(module)) {
          return module;
        }
      }
    }
    return null;
  }

  private static boolean isGradleModule(@Nullable Module module) {
    return module != null && GradleFacet.isAppliedTo(module);
  }

  protected abstract void doPerform(@NotNull Module module,
                                    @NotNull AndroidProjectSettingsService projectStructureService,
                                    @NotNull AnActionEvent e);
}
