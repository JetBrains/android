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

import com.android.tools.idea.rendering.DataBindingInfo;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.ProjectResourceRepository;
import com.google.common.collect.Maps;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * PsiElementFinder extensions that finds classes generated for layout files.
 */
public class DataBindingClassFinder extends PsiElementFinder {
  private final AndroidFacet myFacet;
  private final CachedValue<Map<String, PsiPackage>> myPackageCache;
  public DataBindingClassFinder(final AndroidFacet facet) {
    myFacet = facet;
    myPackageCache = CachedValuesManager.getManager(facet.getModule().getProject()).createCachedValue(
      new ResourceCacheValueProvider<Map<String, PsiPackage>>(facet) {
        @Override
        Map<String, PsiPackage> doCompute() {
          ProjectResourceRepository projectResources = ProjectResourceRepository.getProjectResources(myFacet, true);
          Map<String, DataBindingInfo> dataBindingResourceFiles = projectResources.getDataBindingResourceFiles();
          if (dataBindingResourceFiles == null) {
            return Maps.newHashMap();
          }
          Map<String, PsiPackage> result = Maps.newHashMap();
          for (DataBindingInfo info : dataBindingResourceFiles.values()) {
            result.put(info.getPackageName(), myFacet.getOrCreateDataBindingPsiPackage(info.getPackageName()));
          }
          return result;
        }

        @Override
        Map<String, PsiPackage> defaultValue() {
          return Maps.newHashMap();
        }
      }, false);
  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    LocalResourceRepository moduleResources = myFacet.getModuleResources(false);
    if (moduleResources == null) {
      return null;
    }
    Map<String, DataBindingInfo> dataBindingResourceFiles = moduleResources.getDataBindingResourceFiles();
    if (dataBindingResourceFiles == null) {
      return null;
    }
    DataBindingInfo dataBindingInfo = dataBindingResourceFiles.get(qualifiedName);
    if (dataBindingInfo == null) {
      return null;
    }
    return DataBindingUtil.getOrCreatePsiClass(myFacet, dataBindingInfo);
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    PsiClass aClass = findClass(qualifiedName, scope);
    if (aClass == null) {
      return PsiClass.EMPTY_ARRAY;
    }
    return new PsiClass[]{aClass};
  }

  @Nullable
  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    return myPackageCache.getValue().get(qualifiedName);
  }
}
