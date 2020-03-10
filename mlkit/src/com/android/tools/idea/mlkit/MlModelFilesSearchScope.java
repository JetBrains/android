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
package com.android.tools.idea.mlkit;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Restricts search to matching ml model files in ml resource folders.
 */
public class MlModelFilesSearchScope extends GlobalSearchScope {
  @NotNull
  public static GlobalSearchScope inProject(@NotNull Project project) {
    return new MlModelFilesSearchScope(project);
  }

  @NotNull
  public static GlobalSearchScope inModule(@NotNull Module module) {
    return module.getModuleScope(false).intersectWith(new MlModelFilesSearchScope(module.getProject()));
  }

  private MlModelFilesSearchScope(@NotNull Project project) {
    super(project);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return false;
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (getProject() == null) {
      return false;
    }

    Module module = ModuleUtilCore.findModuleForFile(file, getProject());
    return module != null && MlkitUtils.isModelFileInMlModelsFolder(module, file);
  }
}
