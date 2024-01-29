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

import com.android.tools.idea.util.CommonAndroidUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.augment.ManifestClass;
import org.jetbrains.android.augment.ManifestInnerClass;
import org.jetbrains.android.augment.ResourceRepositoryInnerRClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link PsiElementFinder} for finding inner classes of R and Manifest classes, e.g. {@code R.string}.
 *
 * <p>As the top-level R class for a given package is either generated or augmented, new inner classes may be added by creating instances of
 * {@link ResourceRepositoryInnerRClass}. Both modes (generating the R class from scratch or augmenting an existing one) support retrieving such
 * inner class by calling {@link PsiClass#findInnerClassByName(String, boolean)}, so this is exactly what this {@link PsiElementFinder} does
 * if it suspects the class in question is an inner class of an R class.
 *
 * <p>This is used when trying to find the class using {@link JavaPsiFacade} and also by Kotlin IDE plugin when resolving references to
 * inner classes.
 *
 * @see ResourceRepositoryRClass
 * @see ResourceRepositoryInnerRClass
 * @see ManifestClass
 * @see ManifestInnerClass
 */
public class AndroidInnerClassFinder extends PsiElementFinder {

  public static final AndroidInnerClassFinder INSTANCE = new AndroidInnerClassFinder();

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
    if (project == null || !CommonAndroidUtil.getInstance().isAndroidProject(project)) {
      return PsiClass.EMPTY_ARRAY;
    }

    int lastDot = qualifiedName.lastIndexOf('.');
    if (lastDot < 0) {
      return PsiClass.EMPTY_ARRAY;
    }
    String shortName = qualifiedName.substring(lastDot + 1);
    String parentName = qualifiedName.substring(0, lastDot);

    if (shortName.isEmpty() || !(parentName.endsWith(".R") || parentName.endsWith(".Manifest"))) {
      return PsiClass.EMPTY_ARRAY;
    }
    List<PsiClass> result = new SmartList<>();
    for (PsiClass parentClass : JavaPsiFacade.getInstance(project).findClasses(parentName, scope)) {
      if (!(parentClass instanceof AndroidLightClassBase)) {
        continue;
      }
      ContainerUtil.addIfNotNull(result, parentClass.findInnerClassByName(shortName, false));
    }
    return result.isEmpty() ? PsiClass.EMPTY_ARRAY : result.toArray(PsiClass.EMPTY_ARRAY);
  }
}
