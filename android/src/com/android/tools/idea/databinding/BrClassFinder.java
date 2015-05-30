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
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BrClassFinder extends PsiElementFinder {
  private final AndroidFacet myFacet;
  private String myBrName;
  private DataBindingUtil.LightBrClass myLightBrClass;
  private String myPackageName;

  public BrClassFinder(AndroidFacet facet) {
    myFacet = facet;
  }

  private String getBrName() {
    if (myBrName == null) {
      myBrName = DataBindingUtil.getBrQualifiedName(myFacet);
    }
    return myBrName;
  }

  private String getPackageName() {
    if (myPackageName == null) {
      myPackageName = DataBindingUtil.getGeneratedPackageName(myFacet);
    }
    return myPackageName;
  }

  public boolean isMyScope(GlobalSearchScope scope) {
    return scope.isSearchInModuleContent(myFacet.getModule());
  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (isMyScope(scope) && qualifiedName.equals(getBrName())) {
      return getLightBrClass();
    }
    return null;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (isMyScope(scope) && qualifiedName.equals(getBrName())) {
      return new PsiClass[]{getLightBrClass()};
    }
    return PsiClass.EMPTY_ARRAY;
  }

  private DataBindingUtil.LightBrClass getLightBrClass() {
    if (myLightBrClass == null) {
      myLightBrClass = DataBindingUtil.getOrCreateBrClassFor(myFacet);
    }
    return myLightBrClass;
  }

  @Nullable
  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    if (qualifiedName.equals(getPackageName())) {
      return myFacet.getOrCreateDataBindingPsiPackage(getPackageName());
    }
    return null;
  }
}
