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
package com.android.tools.idea.databinding;

import com.android.tools.idea.res.DataBindingInfo;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * PsiElementFinder extensions that finds classes generated for layout files.
 */
public class DataBindingClassFinder extends PsiElementFinder {
  private final DataBindingProjectComponent myComponent;
  public DataBindingClassFinder(DataBindingProjectComponent component) {
    myComponent = component;
  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (!isEnabled()) {
      return null;
    }
    for (AndroidFacet facet : myComponent.getDataBindingEnabledFacets()) {
      LocalResourceRepository moduleResources = ModuleResourceRepository.getOrCreateInstance(facet);
      Map<String, DataBindingInfo> dataBindingResourceFiles = moduleResources.getDataBindingResourceFiles();
      if (dataBindingResourceFiles == null) {
        continue;
      }
      DataBindingInfo dataBindingInfo = dataBindingResourceFiles.get(qualifiedName);
      if (dataBindingInfo == null) {
        continue;
      }
      VirtualFile file = dataBindingInfo.getPsiFile().getVirtualFile();
      if (file != null && scope.accept(file)) {
        return DataBindingUtil.getOrCreatePsiClass(dataBindingInfo);
      }
    }
    return null;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (!isEnabled()) {
      return PsiClass.EMPTY_ARRAY;
    }
    PsiClass aClass = findClass(qualifiedName, scope);
    if (aClass == null) {
      return PsiClass.EMPTY_ARRAY;
    }
    return new PsiClass[]{aClass};
  }

  @Nullable
  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    // data binding packages are found only if corresponding java packages does not exists. For those, we have DataBindingPackageFinder
    // which has a low priority.
    return null;
  }

  private boolean isEnabled() {
    return DataBindingUtil.inMemoryClassGenerationIsEnabled() && myComponent.hasAnyDataBindingEnabledFacet();
  }
}
