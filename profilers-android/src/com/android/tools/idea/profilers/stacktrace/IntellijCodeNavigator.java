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
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
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
 */
public final class IntellijCodeNavigator extends CodeNavigator {
  private final Project myProject;

  public IntellijCodeNavigator(@NotNull Project project, @NotNull FeatureTracker featureTracker) {
    super(featureTracker);
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
      if (location.getLineNumber() >= 0) {
        // There has been at least one case where the PsiManager could not find an inner class in
        // Kotlin code, which caused us to abort navigating. However, if we have the outer class
        // (which is easier for PsiManager to find) and a line number, that's enough information to
        // help us navigate. So, to be more robust against PsiManager error, we try one more time.
        psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myProject), location.getOuterClassName());
      }
    }

    if (psiClass == null) {
      return null;
    }
    else if (location.getLineNumber() >= 0) {
      // If the specified CodeLocation has a line number, navigatable is that line
      return new OpenFileDescriptor(myProject, psiClass.getNavigationElement().getContainingFile().getVirtualFile(),
                                    location.getLineNumber(), 0);
    }
    else if (location.getMethodName() != null && location.getSignature() != null) {
      // If it has both method name and signature, navigatable is the corresponding method
      PsiMethod method = findMethod(psiClass, location.getMethodName(), location.getSignature());
      return method != null ? method : psiClass;
    }
    else {
      // Otherwise, navigatable is the class
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
