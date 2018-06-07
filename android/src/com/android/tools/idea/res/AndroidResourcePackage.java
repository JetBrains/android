/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res;

import com.android.SdkConstants;
import com.google.common.base.MoreObjects;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

/** A generated stub PsiPackage for generated R classes. */
public class AndroidResourcePackage extends PsiPackageImpl {
  public AndroidResourcePackage(PsiManager manager, String qualifiedName) {
    super(manager, qualifiedName);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).addValue(getQualifiedName()).toString();
  }

  /**
   * Finds classes with the given short name in this package in the given scope.
   *
   * <p>Naive implementation that just calls {@link JavaPsiFacade} which in turn will call {@link PsiElementFinder} implementations.
   * {@link PsiPackageImpl} maintains a cached copy of all classes in this package in the entire project (ignoring scope) and then filters
   * it using {@link PsiSearchScopeUtil#isInScope(SearchScope, PsiElement)}. This doesn't work in our case, because R classes in this
   * package are light and don't have corresponding {@link VirtualFile}s so {@link PsiSearchScopeUtil} always thinks they are in scope.
   *
   * <p>When delegating to {@link JavaPsiFacade}, the scope is handled in the implementations of {@link LightResourceClassService}.
   *
   * @see ProjectLightResourceClassService#getLightRClasses(String, GlobalSearchScope)
   */
  @NotNull
  @Override
  public PsiClass[] findClassByShortName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    if (SdkConstants.R_CLASS.equals(name)) {
      return JavaPsiFacade.getInstance(getProject()).findClasses(getQualifiedName() + ".R", scope);
    } else {
      return PsiClass.EMPTY_ARRAY;
    }
  }
}
