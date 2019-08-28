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
package com.android.tools.idea.databinding.finders;

import com.android.tools.idea.databinding.BindingLayout;
import com.android.tools.idea.databinding.BindingLayoutGroup;
import com.android.tools.idea.databinding.LayoutBindingProjectComponent;
import com.android.tools.idea.databinding.ModuleDataBinding;
import com.android.tools.idea.databinding.cache.ProjectResourceCachedValueProvider;
import com.android.tools.idea.databinding.cache.ResourceCacheValueProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A finder responsible for finding data binding packages missing in the app.
 *
 * Note that some packages used by databinding/viewbinding are already found by default finders,
 * and if we try to suggest our own copies, it can confuse the IntelliJ project structure tool
 * window, which thinks there are two packages with the same name.
 *
 * Therefore, this finder is registered with a reduced priority, so it will only suggest packages
 * that were not previously suggested, while data binding class finders are added with a higher
 * priority. See {@link BindingClassFinder}, {@link DataBindingComponentClassFinder} and
 * {@link BrClassFinder} for the class-focused finders.
 *
 * See also: https://issuetracker.google.com/37120280
 */
public class LayoutBindingPackageFinder extends PsiElementFinder {
  private final LayoutBindingProjectComponent myComponent;
  private final CachedValue<Map<String, PsiPackage>> myPackageCache;

  public LayoutBindingPackageFinder(@NotNull Project project) {
    myComponent = project.getComponent(LayoutBindingProjectComponent.class);
    myPackageCache = CachedValuesManager.getManager(project).createCachedValue(
      new ProjectResourceCachedValueProvider<Map<String, PsiPackage>, Set<String>>(myComponent) {

        @NotNull
        @Override
        protected Map<String, PsiPackage> merge(List<Set<String>> results) {
          Map<String, PsiPackage> merged = new HashMap<>();
          for (Set<String> result : results) {
            for (String qualifiedPackage : result) {
              if (!merged.containsKey(qualifiedPackage)) {
                merged.put(qualifiedPackage, myComponent.getOrCreatePsiPackage(qualifiedPackage));
              }
            }
          }
          return merged;
        }

        @Override
        protected ResourceCacheValueProvider<Set<String>> createCacheProvider(AndroidFacet facet) {
          return new ResourceCacheValueProvider<Set<String>>(facet, null) {
            @Override
            protected Set<String> doCompute() {
              Collection<BindingLayoutGroup> groups = ModuleDataBinding.getInstance(getFacet()).getBindingLayoutGroups();
              if (groups.isEmpty()) {
                return Collections.emptySet();
              }
              Set<String> result = new HashSet<>();
              for (BindingLayoutGroup group : groups) {
                for (BindingLayout layout : group.getLayouts()) {
                  result.add(layout.getPackageName());
                }
              }
              return result;
            }

            @Override
            protected Set<String> defaultValue() {
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
    return myPackageCache.getValue().get(qualifiedName);
  }
}
