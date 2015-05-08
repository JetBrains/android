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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class BrShortNamesCache extends PsiShortNamesCache {
  private DataBindingUtil.LightBrClass myLightBrClass;
  private final AndroidFacet myFacet;

  public BrShortNamesCache(AndroidFacet facet) {
    myFacet = facet;
  }

  public boolean isMyScope(GlobalSearchScope scope) {
    return scope.isSearchInModuleContent(myFacet.getModule());
  }

  @NotNull
  @Override
  public PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    if (!isMyScope(scope)) {
      return PsiClass.EMPTY_ARRAY;
    }
    if (DataBindingUtil.BR.equals(name)) {
      return new PsiClass[]{getLightBrClass()};
    }
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String[] getAllClassNames() {
    return new String[]{DataBindingUtil.BR};
  }

  @Override
  public void getAllClassNames(@NotNull HashSet<String> dest) {
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
    if (!isMyScope(scope) || maxCount < 1) {
      return PsiField.EMPTY_ARRAY;
    }
    PsiField field = getLightBrClass().findFieldByName(name, false);
    if (field == null) {
      return PsiField.EMPTY_ARRAY;
    }
    return new PsiField[]{field};
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
    PsiField field = getLightBrClass().findFieldByName(name, false);
    if (field == null) {
      return PsiField.EMPTY_ARRAY;
    }
    return new PsiField[]{field};
  }

  @NotNull
  @Override
  public String[] getAllFieldNames() {
    return getLightBrClass().getAllFieldNames();
  }

  @Override
  public void getAllFieldNames(@NotNull HashSet<String> set) {
    for (String name : getAllFieldNames()) {
      set.add(name);
    }
  }

  private DataBindingUtil.LightBrClass getLightBrClass() {
    if (myLightBrClass == null) {
      myLightBrClass = DataBindingUtil.getOrCreateBrClassFor(myFacet);
    }
    return myLightBrClass;
  }
}
