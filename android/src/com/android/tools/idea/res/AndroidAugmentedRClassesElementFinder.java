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

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An element finder which finds inner classes within augmented R classes.
 */
public class AndroidAugmentedRClassesElementFinder extends PsiElementFinder {

  public static final AndroidAugmentedRClassesElementFinder INSTANCE = new AndroidAugmentedRClassesElementFinder();

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    PsiClass[] classes = findClasses(qualifiedName, scope);
    return classes.length == 0 ? null : classes[0];
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    Project project = scope.getProject();
    if (project == null || !ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return PsiClass.EMPTY_ARRAY;
    }

    int lastDot = qualifiedName.lastIndexOf('.');
    if (lastDot < 0) {
      return PsiClass.EMPTY_ARRAY;
    }
    String shortName = qualifiedName.substring(lastDot + 1);
    String parentName = qualifiedName.substring(0, lastDot);

    if (shortName.isEmpty() || !parentName.endsWith(".R")) {
      return PsiClass.EMPTY_ARRAY;
    }
    List<PsiClass> result = new SmartList<PsiClass>();
    for (PsiClass parentClass : JavaPsiFacade.getInstance(project).findClasses(parentName, scope)) {
      ContainerUtil.addIfNotNull(result, parentClass.findInnerClassByName(shortName, false));
    }
    return result.isEmpty() ? PsiClass.EMPTY_ARRAY : result.toArray(PsiClass.EMPTY_ARRAY);
  }
}
