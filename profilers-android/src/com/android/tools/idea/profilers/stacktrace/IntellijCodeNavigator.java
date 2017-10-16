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
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.Processor;
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

  @Override
  public boolean isNavigatable(@NotNull CodeLocation location) {
    return getNavigatable(location) != null;
  }

  @Nullable
  private Navigatable getNavigatable(@NotNull CodeLocation location) {
    if (location.isNativeCode()) {
      return getNativeNavigatable(location);
    }

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

  /**
   * Tries to find the method's full name in the project and return a {@link Navigatable} to its location in case of success.
   * Returns null if the method is not found.
   * TODO(b/67844304): The current approach uses IntelliJ's Find Usages utility functions to find matching methods.
   *                   As a result, we won't have the most accurate results. In order to achieve best accuracy, we
   *                   need to use the search utilities of cidr-lang module.
   */
  private Navigatable getNativeNavigatable(@NotNull CodeLocation location) {
    // Create the model used to search for the method.
    String fullMethodName = String.format("%s::%s", location.getClassName(), location.getMethodName());
    FindModel findModel = new FindModel();
    findModel.setStringToFind(fullMethodName);
    findModel.setCaseSensitive(true);
    findModel.setSearchContext(FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS);

    // Workaround to set the navigatable from findUsages callback.
    Navigatable[] navigatable = new Navigatable[1];
    // Search for the method and assign its location to navigatable[0] in case of success.
    Processor<UsageInfo> consumer = (usageInfo) -> {
      VirtualFile file = usageInfo.getVirtualFile();
      if (file == null) {
        // Method not found within the project
        return false;
      }
      navigatable[0] = new OpenFileDescriptor(myProject, file, usageInfo.getNavigationOffset());
      return true;
    };
    FindUsagesProcessPresentation ignored = new FindUsagesProcessPresentation(new UsageViewPresentation());

    // Try to find and return the method in the project
    FindInProjectUtil.findUsages(findModel, myProject, consumer, ignored);
    return navigatable[0];
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
