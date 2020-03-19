/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.ResolveScopeEnlarger;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import java.util.ArrayList;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.caches.resolve.util.KotlinResolveScopeEnlarger;

/**
 * Provides additional scope for light model classes used by reference resolution and code completion.
 */
// TODO(b/146511259): handle test scope.
public class MlkitResolveScopeEnlarger extends ResolveScopeEnlarger {
  @Nullable
  @Override
  public SearchScope getAdditionalResolveScope(@NotNull VirtualFile file, Project project) {
    Module module = ModuleUtilCore.findModuleForFile(file, project);
    return module != null ? getAdditionalResolveScope(module) : null;
  }

  public static class MlkitKotlinResolveScopeEnlarger extends KotlinResolveScopeEnlarger {
    @Nullable
    @Override
    public SearchScope getAdditionalResolveScope(@NotNull Module module, boolean isTestScope) {
      return MlkitResolveScopeEnlarger.getAdditionalResolveScope(module);
    }
  }

  @Nullable
  private static SearchScope getAdditionalResolveScope(@NotNull Module module) {
    if (MlkitUtils.isMlModelBindingBuildFeatureEnabled(module)) {
      Collection<VirtualFile> virtualFiles = new ArrayList<>();
      for (PsiClass lightClass : MlkitModuleService.getInstance(module).getLightModelClassList()) {
        virtualFiles.add(lightClass.getContainingFile().getViewProvider().getVirtualFile());
      }
      return GlobalSearchScope.filesWithoutLibrariesScope(module.getProject(), virtualFiles);
    }

    return null;
  }
}
