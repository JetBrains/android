/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python.search;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import javax.annotation.Nullable;

/**
 * Genfiles (and files in READONLY, etc.) are not included in the project scope. To resolve
 * references to these files, we need to either add them to the project scope (which would destroy
 * performance) or modify the default scope behavior, as done here.
 */
public class BlazePyUseScopeEnlarger extends UseScopeEnlarger {

  @Nullable
  @Override
  public SearchScope getAdditionalUseScope(PsiElement element) {
    if (!Blaze.isBlazeProject(element.getProject())) {
      return null;
    }
    if (isPyPackageOutsideProject(element) || isPyFileOutsideProject(element)) {
      return GlobalSearchScope.projectScope(element.getProject());
    }
    return null;
  }

  private static boolean isPyPackageOutsideProject(PsiElement element) {
    if (!(element instanceof PsiDirectory)) {
      return false;
    }
    PsiDirectory dir = (PsiDirectory) element;
    return dir.findFile(PyNames.INIT_DOT_PY) != null
        && !inProjectScope(dir.getProject(), dir.getVirtualFile());
  }

  private static boolean isPyFileOutsideProject(PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file instanceof PyFile
        && !inProjectScope(file.getProject(), file.getViewProvider().getVirtualFile());
  }

  private static boolean inProjectScope(Project project, VirtualFile virtualFile) {
    return GlobalSearchScope.projectScope(project).contains(virtualFile);
  }
}
