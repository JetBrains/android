/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.common.CodeLocation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;

public class IntellijProfilerServices implements IdeProfilerServices {
  @NotNull private final Project myProject;

  public IntellijProfilerServices(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean navigateToStackFrame(@NotNull CodeLocation codeLocation) {
    NavigatableStackFrame stackLine = new NavigatableStackFrame(myProject, codeLocation);
    Navigatable nav = stackLine.getNavigatable();
    if (nav != null && nav.canNavigate()) {
      nav.navigate(true);
      return true;
    }

    return false;
  }

  static final class NavigatableStackFrame {
    @NotNull private final Project myProject;
    @NotNull private final CodeLocation myCodeLocation;

    public NavigatableStackFrame(@NotNull Project project, @NotNull CodeLocation codeLocation) {
      myProject = project;
      myCodeLocation = codeLocation;
    }

    @Nullable
    public Navigatable getNavigatable() {
      VirtualFile file = findClassFile();
      if (file == null) return null;

      int lineNumber = myCodeLocation.getLine();
      if (lineNumber == -1) {
        return new OpenFileDescriptor(myProject, file);
      }
      else {
        return new OpenFileDescriptor(myProject, file, lineNumber, 0);
      }
    }

    @Nullable
    private VirtualFile findClassFile() {
      String className = myCodeLocation.getClassName();
      if (className == null) {
        return null;
      }

      PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
      if (psiClass == null) {
        return null;
      }
      return psiClass.getContainingFile().getVirtualFile();
    }
  }

  @NotNull
  @Override
  public Executor getProfilerExecutor() {
    return ApplicationManager.getApplication()::invokeLater;
  }
}
