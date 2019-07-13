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
package com.android.tools.idea.databinding.cache;

import com.android.SdkConstants;
import com.android.support.AndroidxName;
import com.android.tools.idea.databinding.DataBindingProjectComponent;
import com.android.tools.idea.databinding.finders.DataBindingComponentClassFinder;
import com.android.tools.idea.databinding.psiclass.LightDataBindingComponentClass;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
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
 * Cache that stores the DataBindingComponent instances associated with each module.
 *
 * See {@link LightDataBindingComponentClass}
 */
final class DataBindingComponentShortNamesCache extends PsiShortNamesCache {
  private final DataBindingProjectComponent myComponent;
  private static final String[] ourClassNames = new String[]{SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT};

  DataBindingComponentShortNamesCache(@NotNull Project project) {
    myComponent = project.getComponent(DataBindingProjectComponent.class);
  }

  @NotNull
  @Override
  public PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    Project project = scope.getProject();
    if (project == null || !check(name, scope)) {
      return PsiClass.EMPTY_ARRAY;
    }

    DataBindingComponentClassFinder classFinder = PsiElementFinder.EP.findExtensionOrFail(DataBindingComponentClassFinder.class, project);
    // we need to search for both old and new. Class finder knows which one to generate.
    final AndroidxName componentClass = SdkConstants.CLASS_DATA_BINDING_COMPONENT;
    final PsiClass[] support = classFinder.findClasses(componentClass.oldName(), scope);
    final PsiClass[] androidX = classFinder.findClasses(componentClass.newName(), scope);
    return ArrayUtil.mergeArrays(support, androidX);
  }

  private boolean check(String name, GlobalSearchScope scope) {
    return SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT.equals(name)
           && scope.getProject() != null
           && myComponent.getProject().equals(scope.getProject());
  }

  @NotNull
  @Override
  public String[] getAllClassNames() {
    return ourClassNames;
  }

  @Override
  public void getAllClassNames(@NotNull HashSet<String> dest) {
    dest.add(SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT);
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
