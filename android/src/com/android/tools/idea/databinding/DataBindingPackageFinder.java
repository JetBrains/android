/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * This element finder has minimum priority and only finds packages that are missing in the app.
 * See {@link DataBindingClassFinder}, {@link DataBindingComponentClassFinder} and {@link BrClassFinder} for actual classes.
 */
public class DataBindingPackageFinder extends PsiElementFinder {
  private final DataBindingProjectComponent myComponent;
  private final CachedValue<Map<String, PsiPackage>> myPackageCache;


  public DataBindingPackageFinder(final DataBindingProjectComponent component) {
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
              LocalResourceRepository moduleResources = ModuleResourceRepository.getOrCreateInstance(getFacet());
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
    return null;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    if (!isEnabled()) {
      return null;
    }
    return myPackageCache.getValue().get(qualifiedName);
  }

  private boolean isEnabled() {
    return DataBindingUtil.inMemoryClassGenerationIsEnabled() && myComponent.hasAnyDataBindingEnabledFacet();
  }
}
