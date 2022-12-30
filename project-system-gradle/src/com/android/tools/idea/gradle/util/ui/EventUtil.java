/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.util.ui;

import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EventUtil {
  @Nullable
  public static Module getSelectedAndroidModule(@NotNull AnActionEvent e) {
    Module module = getSelectedGradleModule(e);
    if (module != null && AndroidFacet.getInstance(module) != null) {
      return module;
    }
    return null;
  }

  @Nullable
  public static Module getSelectedGradleModule(@NotNull AnActionEvent e) {
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
}
