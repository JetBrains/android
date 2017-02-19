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
package com.android.tools.idea.apk.debugging;

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

import static com.android.SdkConstants.EXT_JAVA;

public class JavaFiles {
  public static boolean isJavaFile(@NotNull VirtualFile file) {
    return !file.isDirectory() && EXT_JAVA.equals(file.getExtension());
  }

  @Nullable
  public PsiClass findClass(@NotNull String classFqn, @NotNull Project project) {
    if (DumbService.getInstance(project).isDumb()) {
      // Index not ready.
      return null;
    }
    return JavaPsiFacade.getInstance(project).findClass(classFqn, GlobalSearchScope.allScope(project));
  }

  @NotNull
  public List<String> findClasses(@NotNull VirtualFile file, @NotNull Project project) {
    PsiJavaFile psiFile = findPsiFileFor(file, project);
    if (psiFile != null) {
      PsiClass[] classes = psiFile.getClasses();
      if (classes.length > 0) {
        return Arrays.stream(classes).map(PsiClass::getQualifiedName).collect(Collectors.toList());
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public String findPackage(@NotNull VirtualFile file, @NotNull Project project) {
    PsiJavaFile psiFile = findPsiFileFor(file, project);
    return psiFile != null ? psiFile.getPackageName() : null;
  }

  @Nullable
  private static PsiJavaFile findPsiFileFor(@NotNull VirtualFile file, @NotNull Project project) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    return psiFile instanceof PsiJavaFile ? (PsiJavaFile)psiFile : null;
  }
}
