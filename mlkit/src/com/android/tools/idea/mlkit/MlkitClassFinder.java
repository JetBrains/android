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
import com.android.tools.mlkit.MlkitNames;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used to find light model classes and packages based on their fully qualified names.
 */
public class MlkitClassFinder extends PsiElementFinder {
  private final Project myProject;

  public MlkitClassFinder(@NotNull Project project) {
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
    if (!StudioFlags.MLKIT_LIGHT_CLASSES.get() || !qualifiedName.contains(MlkitNames.PACKAGE_SUFFIX)) {
      return PsiClass.EMPTY_ARRAY;
    }

    Map<VirtualFile, MlModelMetadata> modelFileMap = new HashMap<>();
    String className = computeDataKey(qualifiedName);
    FileBasedIndex.getInstance().processValues(MlModelFileIndex.INDEX_ID, className, null, (file, value) -> {
      modelFileMap.put(file, value);
      return true;
    }, scope.intersectWith(MlModelFilesSearchScope.inProject(myProject)));

    List<PsiClass> lightClassList = new ArrayList<>();
    for (PsiClass lightModelClass : MlkitUtils.getLightModelClasses(myProject, modelFileMap)) {
      if (qualifiedName.equals(lightModelClass.getQualifiedName())) {
        lightClassList.add(lightModelClass);
      }
      else {
        for (PsiClass innerClass : lightModelClass.getInnerClasses()) {
          if (qualifiedName.equals(innerClass.getQualifiedName())) {
            lightClassList.add(innerClass);
          }
        }
      }
    }

    return lightClassList.toArray(PsiClass.EMPTY_ARRAY);
  }

  @NotNull
  private static String computeDataKey(@NotNull String qualifiedName) {
    // If it might inner class, then find second last element which matches data key.
    if (qualifiedName.endsWith(MlkitNames.OUTPUTS) || qualifiedName.endsWith(MlkitNames.INPUTS)) {
      String[] candidates = qualifiedName.split("\\.");
      if (candidates.length >= 2) {
        return candidates[candidates.length - 2];
      }
    }

    return StringUtil.getShortName(qualifiedName);
  }

  @Nullable
  @Override
  public PsiPackage findPackage(@NotNull String packageName) {
    if (!StudioFlags.MLKIT_LIGHT_CLASSES.get() || !packageName.endsWith(MlkitNames.PACKAGE_SUFFIX)) {
      return null;
    }

    String modulePackageName = StringUtil.substringBeforeLast(packageName, MlkitNames.PACKAGE_SUFFIX);
    boolean moduleFound = !ProjectSystemUtil.getProjectSystem(myProject)
      .getAndroidFacetsWithPackageName(myProject, modulePackageName, GlobalSearchScope.projectScope(myProject))
      .isEmpty();

    return moduleFound ? AndroidLightPackage.withName(packageName, myProject) : null;
  }
}
