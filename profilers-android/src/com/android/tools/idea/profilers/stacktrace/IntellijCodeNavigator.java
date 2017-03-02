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
package com.android.tools.idea.profilers.stacktrace;

import com.android.tools.idea.profilers.TraceSignatureConverter;
import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.common.CodeNavigator;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link CodeNavigator} with logic to jump to code inside of an IntelliJ code editor.
 * How it works:
 *   - If the specified CodeLocation has a line number => navigates to the line.
 *   - If it doesn't have a line number, but has a method name and its signature => navigates to the corresponding method.
 *   - Otherwise, navigates to the class.
 */
public final class IntellijCodeNavigator extends CodeNavigator {
  private final Project myProject;

  public IntellijCodeNavigator(@NotNull Project project) {
    myProject = project;
  }

  @Override
  protected void handleNavigate(@NotNull CodeLocation location) {
    Navigatable nav = getNavigatable(location);
    if (nav != null) {
      nav.navigate(true);
    }
  }

  @Nullable
  private Navigatable getNavigatable(@NotNull CodeLocation location) {
    PsiClass psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myProject), location.getClassName());
    if (psiClass == null) {
      return null;
    }

    if (location.getLineNumber() >= 0) {
      return new OpenFileDescriptor(myProject, psiClass.getContainingFile().getVirtualFile(), location.getLineNumber(), 0);
    }
    else if (location.getMethodName() != null && location.getSignature() != null) {
      PsiMethod method = findMethod(psiClass, location.getMethodName(), location.getSignature());
      return method != null ? method : psiClass;
    }
    else {
      return psiClass;
    }
  }

  @Nullable
  private static PsiMethod findMethod(@NotNull PsiClass psiClass, @NotNull String methodName, @NotNull String signature) {
    for (PsiMethod method : psiClass.findMethodsByName(methodName, true)) {
      if (signature.equals(TraceSignatureConverter.getTraceSignature(method))) {
        return method;
      }
    }
    return null;
  }
}
