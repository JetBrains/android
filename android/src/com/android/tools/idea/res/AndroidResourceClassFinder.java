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

import com.google.common.base.Strings;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Provides dynamic Android Resource classes (class R {...} ). */
public class AndroidResourceClassFinder extends PsiElementFinder {
  static final String INTERNAL_R_CLASS_SHORTNAME = ".R";

  @NotNull
  private LightResourceClassService myService;

  public AndroidResourceClassFinder(@NotNull LightResourceClassService lightResourceClassService) {
    myService = lightResourceClassService;
  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    PsiClass[] result = findClasses(qualifiedName, scope);
    return result.length > 0 ? result[0] : null;
  }

  @NotNull
  @Override
  public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    String targetPackageName = psiPackage.getQualifiedName();
    if (Strings.isNullOrEmpty(targetPackageName)) {
      return PsiClass.EMPTY_ARRAY;
    }
    return findClasses(targetPackageName + INTERNAL_R_CLASS_SHORTNAME, scope);
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (!qualifiedName.endsWith(INTERNAL_R_CLASS_SHORTNAME)) {
      return PsiClass.EMPTY_ARRAY;
    }
    List<PsiClass> result = myService.getLightRClasses(qualifiedName, scope);
    return result.toArray(PsiClass.EMPTY_ARRAY);
  }
}
