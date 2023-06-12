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
package com.android.tools.idea.mlkit;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.AndroidLightPackage;
import com.android.tools.mlkit.MlNames;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used to find light model classes and packages based on their fully qualified names.
 */
public class MlClassFinder extends PsiElementFinder {
  private final Project myProject;

  public MlClassFinder(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    PsiClass[] lightClasses = findClasses(qualifiedName, scope);
    return lightClasses.length > 0 ? lightClasses[0] : null;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (!StudioFlags.ML_MODEL_BINDING.get() || !qualifiedName.contains(MlNames.PACKAGE_SUFFIX)) {
      return PsiClass.EMPTY_ARRAY;
    }

    String className = StringUtil.getShortName(qualifiedName);
    return
      MlProjectService.getInstance(myProject).getLightClassListByClassName(className).stream()
        .filter(lightClass -> PsiSearchScopeUtil.isInScope(scope, lightClass) && qualifiedName.equals(lightClass.getQualifiedName()))
        .toArray(PsiClass[]::new);
  }

  @Nullable
  @Override
  public PsiPackage findPackage(@NotNull String packageName) {
    if (!StudioFlags.ML_MODEL_BINDING.get() || !packageName.endsWith(MlNames.PACKAGE_SUFFIX)) {
      return null;
    }

    String modulePackageName = StringUtil.substringBeforeLast(packageName, MlNames.PACKAGE_SUFFIX);
    for (AndroidFacet facet : ProjectSystemUtil.getProjectSystem(myProject).getAndroidFacetsWithPackageName(myProject, modulePackageName)) {
      if (MlUtils.isMlModelBindingBuildFeatureEnabled(facet.getModule())) {
        return AndroidLightPackage.withName(packageName, myProject);
      }
    }

    return null;
  }
}
