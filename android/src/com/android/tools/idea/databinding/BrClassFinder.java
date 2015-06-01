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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class BrClassFinder extends PsiElementFinder {
  private final DataBindingProjectComponent myComponent;
  private CachedValue<Map<String, PsiClass>> myClassByPackageCache;
  public BrClassFinder(DataBindingProjectComponent component) {
    myComponent = component;
    myClassByPackageCache = CachedValuesManager.getManager(component.getProject()).createCachedValue(
      new CachedValueProvider<Map<String, PsiClass>>() {
        @Nullable
        @Override
        public Result<Map<String, PsiClass>> compute() {
          Map<String, PsiClass> classes = new HashMap<String, PsiClass>();
          for (AndroidFacet facet : myComponent.getDataBindingEnabledFacets()) {
            if (facet.isDataBindingEnabled()) {
              classes.put(DataBindingUtil.getBrQualifiedName(facet), DataBindingUtil.getOrCreateBrClassFor(facet));
            }
          }
          return Result.create(classes, myComponent);
        }
      }, false);
  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (!myComponent.hasAnyDataBindingEnabledFacet() || !qualifiedName.endsWith(DataBindingUtil.BR)) {
      return null;
    }
    PsiClass psiClass = myClassByPackageCache.getValue().get(qualifiedName);
    if (psiClass == null) {
      return null;
    }
    PsiFile containingFile = psiClass.getContainingFile();
    if (containingFile == null) {
      return null;
    }
    VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    if (!scope.accept(virtualFile)) {
      return null;
    }
    return psiClass;
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
    if (!myComponent.hasAnyDataBindingEnabledFacet()) {
      return null;
    }
    for (AndroidFacet facet : myComponent.getDataBindingEnabledFacets()) {
      String generatedPackageName = DataBindingUtil.getGeneratedPackageName(facet);
      if (generatedPackageName.equals(qualifiedName)) {
        return myComponent.getOrCreateDataBindingPsiPackage(generatedPackageName);
      }
    }
    return null;
  }
}
