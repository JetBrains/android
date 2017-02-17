/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.apk.java;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class ClassFinder {
  @NotNull private final Project myProject;

  ClassFinder(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  PsiClass findClass(@NotNull ApkClass apkClass) {
    return findClass(apkClass.getFqn());
  }

  @Nullable
  PsiClass findClass(@NotNull String classFqn) {
    if (DumbService.getInstance(myProject).isDumb()) {
      // Index not ready.
      return null;
    }
    return JavaPsiFacade.getInstance(myProject).findClass(classFqn, GlobalSearchScope.allScope(myProject));
  }

  @NotNull
  List<String> findClasses(@NotNull VirtualFile file) {
    PsiJavaFile psiFile = findPsiFileFor(file);
    if (psiFile != null) {
      PsiClass[] classes = psiFile.getClasses();
      if (classes.length > 0) {
        return Arrays.stream(classes).map(PsiClass::getQualifiedName).collect(Collectors.toList());
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  String findPackage(@NotNull VirtualFile file) {
    PsiJavaFile psiFile = findPsiFileFor(file);
    return psiFile != null ? psiFile.getPackageName() : null;
  }

  @Nullable
  private PsiJavaFile findPsiFileFor(@NotNull VirtualFile file) {
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    return psiFile instanceof PsiJavaFile ? (PsiJavaFile)psiFile : null;
  }
}
