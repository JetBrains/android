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
package com.android.tools.idea.actions;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PsiFileAndLineNavigation implements NavigationItem {
  @NotNull private PsiFile myPsiFile;
  private int myLineNumber;

  public PsiFileAndLineNavigation(@NotNull PsiFile file, int lineNumber) {
    myPsiFile = file;
    myLineNumber = lineNumber;
  }

  @Nullable
  public static PsiFileAndLineNavigation[] wrappersForClassName(@NotNull Project project, @Nullable String className, int lineNumber) {
    if (className != null) {
      List<PsiFileAndLineNavigation> files = new ArrayList<PsiFileAndLineNavigation>();
      PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(className, GlobalSearchScope.allScope(project));
      for (PsiClass c : classes) {
        files.add(new PsiFileAndLineNavigation((PsiFile)c.getContainingFile().getNavigationElement(), lineNumber));
      }
      return files.toArray(new PsiFileAndLineNavigation[files.size()]);
    }

    return null;
  }

  @NotNull
  public PsiFile getPsiFile() {
    return myPsiFile;
  }

  @Nullable
  @Override
  public String getName() {
    return myPsiFile.getName();
  }

  @Nullable
  @Override
  public ItemPresentation getPresentation() {
    return myPsiFile.getPresentation();
  }

  @Override
  public void navigate(boolean requestFocus) {
    OpenFileDescriptor fileDescriptor = new OpenFileDescriptor(myPsiFile.getProject(), myPsiFile.getVirtualFile(), myLineNumber);
    fileDescriptor.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return myPsiFile.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return myPsiFile.canNavigateToSource();
  }
}