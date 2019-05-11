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
package com.android.tools.idea.databinding;

import com.android.tools.idea.databinding.DataBindingProjectComponent;
import com.android.tools.idea.databinding.psiclass.LightBindingComponentClass;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A finder responsible for finding all the generated DataBindingComponents in this project.
 *
 * See {@link LightBindingComponentClass}
 *
 * TODO(b/129543644): This class cannot change its name or package location until we remove
 *  hardcoded references to it from the Kotlin plugin.
 *  Move back to: finders.BindingComponentCacheFinder
 */
public class DataBindingComponentClassFinder extends PsiElementFinder {
  private final DataBindingProjectComponent myComponent;
  private CachedValue<List<PsiClass>> myClasses;

  public DataBindingComponentClassFinder(final DataBindingProjectComponent component) {
    myComponent = component;
    myClasses = CachedValuesManager.getManager(component.getProject()).createCachedValue(
      () -> {
        List<PsiClass> classes = Lists.newArrayList();
        for (AndroidFacet facet : myComponent.getDataBindingEnabledFacets()) {
          if (facet.getConfiguration().isLibraryProject()) {
            continue;
          }
          classes.add(new LightBindingComponentClass(PsiManager.getInstance(component.getProject()), facet));
        }
        return CachedValueProvider.Result.create(classes, myComponent, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }, false);

  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull final GlobalSearchScope scope) {
    return Iterables.tryFind(myClasses.getValue(), input -> check(input, qualifiedName, scope)).orNull();
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull final GlobalSearchScope scope) {
    Iterable<PsiClass> filtered = Iterables.filter(myClasses.getValue(), input -> check(input, qualifiedName, scope));
    if (filtered.iterator().hasNext()) {
      return Iterables.toArray(filtered, PsiClass.class);
    }
    return PsiClass.EMPTY_ARRAY;
  }

  private static boolean check(@Nullable PsiClass psiClass, @NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return psiClass != null && psiClass.getProject() == scope.getProject() && qualifiedName.equals(psiClass.getQualifiedName());
  }
}
