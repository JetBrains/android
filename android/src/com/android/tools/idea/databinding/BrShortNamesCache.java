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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BrShortNamesCache extends PsiShortNamesCache {
  private final DataBindingProjectComponent myComponent;
  private CachedValue<String[]> myAllFieldNamesCache;
  private static final String[] BR_CLASS_NAME_LIST = new String[]{DataBindingUtil.BR};
  public BrShortNamesCache(DataBindingProjectComponent dataBindingProjectComponent) {
    myComponent = dataBindingProjectComponent;
    myAllFieldNamesCache = CachedValuesManager.getManager(myComponent.getProject()).createCachedValue(() -> {
      AndroidFacet[] facets = myComponent.getDataBindingEnabledFacets();
      String[] result;
      if (facets.length == 0) {
        result = ArrayUtil.EMPTY_STRING_ARRAY;
      } else {
        Set<String> allFields = Sets.newHashSet();
        for (AndroidFacet facet : facets) {
          LightBrClass brClass = DataBindingUtil.getOrCreateBrClassFor(facet);
          Collections.addAll(allFields, brClass.getAllFieldNames());
        }
        result = ArrayUtil.toStringArray(allFields);
      }
      return CachedValueProvider.Result.create(result, myComponent);
    }, false);
  }

  private boolean isMyScope(GlobalSearchScope scope) {
    if(!isEnabled()) {
      return false;
    }
    if (scope.getProject() == null) {
      return false;
    }
    return myComponent.getProject().equals(scope.getProject());
  }

  @NotNull
  @Override
  public PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    if (!isMyScope(scope)) {
      return PsiClass.EMPTY_ARRAY;
    }
    if (!DataBindingUtil.BR.equals(name)) {
      return PsiClass.EMPTY_ARRAY;
    }
    AndroidFacet[] facets = myComponent.getDataBindingEnabledFacets();
    return filterByScope(facets, scope);
  }

  @NotNull
  @Override
  public String[] getAllClassNames() {
    if (!isEnabled()) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    return BR_CLASS_NAME_LIST;
  }

  @Override
  public void getAllClassNames(@NotNull HashSet<String> dest) {
    if (!isEnabled()) {
      return;
    }
    dest.add(DataBindingUtil.BR);
  }

  @NotNull
  @Override
  public PsiMethod[] getMethodsByName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiField[] getFieldsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    PsiField[] fields = getFieldsByName(name, scope);
    if (fields.length > maxCount) {
      return PsiField.EMPTY_ARRAY;
    }
    return fields;
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull GlobalSearchScope scope,
                                        @NotNull Processor<PsiMethod> processor) {
    return true;
  }

  @NotNull
  @Override
  public String[] getAllMethodNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void getAllMethodNames(@NotNull HashSet<String> set) {

  }

  @NotNull
  @Override
  public PsiField[] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    if (!isMyScope(scope)) {
      return PsiField.EMPTY_ARRAY;
    }
    PsiClass[] psiClasses = filterByScope(myComponent.getDataBindingEnabledFacets(), scope);
    if (psiClasses.length == 0) {
      return PsiField.EMPTY_ARRAY;
    }
    List<PsiField> result = Lists.newArrayList();
    for (PsiClass psiClass : psiClasses) {
      PsiField field = psiClass.findFieldByName(name, false);
      if (field != null) {
        result.add(field);
      }
    }
    return result.toArray(new PsiField[result.size()]);
  }

  @NotNull
  @Override
  public String[] getAllFieldNames() {
    if (!isEnabled()) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    return myAllFieldNamesCache.getValue();
  }

  @Override
  public void getAllFieldNames(@NotNull HashSet<String> set) {
    Collections.addAll(set, getAllFieldNames());
  }

  private static PsiClass[] filterByScope(AndroidFacet[] facets, @NotNull GlobalSearchScope scope) {
    if (facets == null || facets.length == 0) {
      return PsiClass.EMPTY_ARRAY;
    }
    List<PsiClass> selected = Lists.newArrayList();
    for (AndroidFacet facet : facets) {
      if (scope.isSearchInModuleContent(facet.getModule())) {
        selected.add(DataBindingUtil.getOrCreateBrClassFor(facet));
      }
    }
    if (selected.isEmpty()) {
      return PsiClass.EMPTY_ARRAY;
    }
    return selected.toArray(new PsiClass[selected.size()]);
  }

  private boolean isEnabled() {
    return DataBindingUtil.inMemoryClassGenerationIsEnabled() && myComponent.hasAnyDataBindingEnabledFacet();
  }
}
