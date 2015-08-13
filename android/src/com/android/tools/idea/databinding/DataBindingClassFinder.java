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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PsiElementFinder extensions that finds classes generated for layout files.
 */
public class DataBindingClassFinder extends PsiElementFinder {
  private final CachedValue<Map<String, PsiPackage>> myPackageCache;
  private final DataBindingProjectComponent myComponent;
  public DataBindingClassFinder(DataBindingProjectComponent component) {
    myComponent = component;
    myPackageCache = CachedValuesManager.getManager(myComponent.getProject()).createCachedValue(
      new ProjectResourceCachedValueProvider<Map<String, PsiPackage>, Set<String>>(myComponent) {

        @NotNull
        @Override
        protected Map<String, PsiPackage> merge(List<Set<String>> results) {
          Map<String, PsiPackage> merged = Maps.newHashMap();
          for (Set<String> result : results) {
            for (String qualifiedPackage : result) {
              if (!merged.containsKey(qualifiedPackage)) {
                merged.put(qualifiedPackage, myComponent.getOrCreateDataBindingPsiPackage(qualifiedPackage));
              }
            }
          }
          return merged;
        }

        @Override
        ResourceCacheValueProvider<Set<String>> createCacheProvider(AndroidFacet facet) {
          return new ResourceCacheValueProvider<Set<String>>(facet) {
            @Override
            Set<String> doCompute() {
              LocalResourceRepository moduleResources = getFacet().getModuleResources(false);
              if (moduleResources == null) {
                return Collections.emptySet();
              }
              Map<String, DataBindingInfo> dataBindingResourceFiles = moduleResources.getDataBindingResourceFiles();
              if (dataBindingResourceFiles == null) {
                return Collections.emptySet();
              }
              Set<String> result = Sets.newHashSet();
              for (DataBindingInfo info : dataBindingResourceFiles.values()) {
                result.add(info.getPackageName());
              }
              return result;
            }

            @Override
            Set<String> defaultValue() {
              return Collections.emptySet();
            }
          };
        }
      }, false);
  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (!myComponent.hasAnyDataBindingEnabledFacet()) {
      return null;
    }
    for (AndroidFacet facet : myComponent.getDataBindingEnabledFacets()) {
      LocalResourceRepository moduleResources = facet.getModuleResources(false);
      if (moduleResources == null) {
        continue;
      }
      Map<String, DataBindingInfo> dataBindingResourceFiles = moduleResources.getDataBindingResourceFiles();
      if (dataBindingResourceFiles == null) {
        continue;
      }
      DataBindingInfo dataBindingInfo = dataBindingResourceFiles.get(qualifiedName);
      if (dataBindingInfo == null) {
        continue;
      }
      return DataBindingUtil.getOrCreatePsiClass(dataBindingInfo);
    }
    return null;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (!myComponent.hasAnyDataBindingEnabledFacet()) {
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
    if (!myComponent.hasAnyDataBindingEnabledFacet()) {
      return null;
    }
    return myPackageCache.getValue().get(qualifiedName);
  }
}
