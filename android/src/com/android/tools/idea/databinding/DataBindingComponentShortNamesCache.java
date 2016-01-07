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


import com.android.SdkConstants;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * The ShortNamesCache for DataBinding. Note that this class does not implement method/field search methods since they are not useful for
 * DataBindingComponent.
 */
public class DataBindingComponentShortNamesCache extends PsiShortNamesCache {
  private DataBindingProjectComponent myComponent;
  private static final String[] ourClassNames = new String[]{SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT};
  private DataBindingComponentClassFinder myClassFinder;
  public DataBindingComponentShortNamesCache(DataBindingProjectComponent component, DataBindingComponentClassFinder componentClassFinder) {
    myComponent = component;
    myClassFinder = componentClassFinder;
  }

  @NotNull
  @Override
  public PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    if (!check(name, scope)) {
      return PsiClass.EMPTY_ARRAY;
    }
    return myClassFinder.findClasses(SdkConstants.CLASS_DATA_BINDING_COMPONENT, scope);
  }

  private boolean check(String name, GlobalSearchScope scope) {
    return SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT.equals(name) && myComponent.hasAnyDataBindingEnabledFacet() &&
           scope.getProject() != null && myComponent.getProject().equals(scope.getProject());
  }

  @NotNull
  @Override
  public String[] getAllClassNames() {
    if (myComponent.hasAnyDataBindingEnabledFacet()) {
      return ourClassNames;
    } else {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  @Override
  public void getAllClassNames(@NotNull HashSet<String> dest) {
    if (myComponent.hasAnyDataBindingEnabledFacet()) {
      dest.add(SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT);
    }
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
    return PsiField.EMPTY_ARRAY;
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
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String[] getAllFieldNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void getAllFieldNames(@NotNull HashSet<String> set) {

  }
}
